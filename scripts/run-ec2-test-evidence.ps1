param(
    [string]$ConfigPath = 'scripts\test-evidence.ec2.local.json'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$workspace = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $workspace '.env'
$resolvedConfigPath = if ([IO.Path]::IsPathRooted($ConfigPath)) {
    [IO.Path]::GetFullPath($ConfigPath)
} else {
    [IO.Path]::GetFullPath((Join-Path $workspace $ConfigPath))
}

function Write-Utf8Json {
    param([string]$Path, $Value)
    $json = $Value | ConvertTo-Json -Depth 8
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

function Read-LocalSetting {
    param([string]$Name, [string]$Default = '')
    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if ($processValue) {
        return $processValue.Trim()
    }
    if (Test-Path -LiteralPath $envFile) {
        $line = Get-Content -LiteralPath $envFile | Where-Object {
            $_ -match "^$([regex]::Escape($Name))="
        } | Select-Object -Last 1
        if ($line) {
            return (($line -split '=', 2)[1]).Trim().Trim('"').Trim("'")
        }
    }
    return $Default
}

function Initialize-Config {
    $hostValue = Read-LocalSetting 'EC2_HOST'
    $keyValue = Read-LocalSetting 'EC2_SSH_KEY_PATH'
    $userValue = Read-LocalSetting 'EC2_SSH_USER' 'ubuntu'
    if (-not $hostValue) {
        $hostValue = Read-Host 'EC2 public IP or SSH host'
    }
    $sshAgentReady = $false
    if (Get-Command ssh-add -ErrorAction SilentlyContinue) {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & ssh-add -L *> $null
        $sshAgentReady = $LASTEXITCODE -eq 0
        $ErrorActionPreference = $previousPreference
    }
    if (-not $keyValue -and -not $sshAgentReady) {
        $keyValue = Read-Host 'SSH private key path'
    }
    $config = [ordered]@{
        baseUrl = 'https://app.pay-flow.cloud'
        ec2Host = $hostValue
        sshUser = $userValue
        sshKeyPath = $keyValue
        mySqlContainer = 'payflow-mysql'
        mode = 'concurrent'
        vus = 1000
        rate = 420
        duration = '2m'
        amount = 1000
        senderCount = 50
        receiverCount = 50
        skipJUnit = $false
    }
    Write-Utf8Json -Path $resolvedConfigPath -Value $config
    Write-Host "Saved EC2 evidence config: $resolvedConfigPath"
    return [pscustomobject]$config
}

function Test-DockerEngine {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & docker info *> $null
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    return $exitCode -eq 0
}

if (Test-Path -LiteralPath $resolvedConfigPath) {
    $config = Get-Content -LiteralPath $resolvedConfigPath -Raw | ConvertFrom-Json
} else {
    $config = Initialize-Config
}

# Explicit environment/.env values override the cached local config.
$envHost = Read-LocalSetting 'EC2_HOST'
$envUser = Read-LocalSetting 'EC2_SSH_USER'
$envKeyPath = Read-LocalSetting 'EC2_SSH_KEY_PATH'
if ($envHost) { $config.ec2Host = $envHost }
if ($envUser) { $config.sshUser = $envUser }
if ($envKeyPath) { $config.sshKeyPath = $envKeyPath }

if (-not $config.ec2Host -or $config.ec2Host -eq 'EC2_PUBLIC_IP_OR_HOST') {
    throw "Set ec2Host in $resolvedConfigPath"
}
if ($config.sshKeyPath -and -not (Test-Path -LiteralPath $config.sshKeyPath)) {
    throw "SSH key does not exist: $($config.sshKeyPath)"
}
if ($config.sshKeyPath -and $env:OS -eq 'Windows_NT') {
    $currentPrincipal = "$($env:USERDOMAIN)\$($env:USERNAME):(R)"
    & icacls.exe ([string]$config.sshKeyPath) '/inheritance:r' *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not disable inherited permissions on SSH key: $($config.sshKeyPath)"
    }
    & icacls.exe ([string]$config.sshKeyPath) '/grant:r' $currentPrincipal *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not restrict SSH key permissions to the current Windows user: $($config.sshKeyPath)"
    }
}

$sshBaseArgs = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new')
if ($config.sshKeyPath) {
    $sshBaseArgs += @('-i', [string]$config.sshKeyPath)
}
$sshTarget = "$($config.sshUser)@$($config.ec2Host)"

$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$sshCheckOutput = & ssh @sshBaseArgs $sshTarget 'docker ps --format {{.Names}}' 2>&1
$sshExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousPreference
if ($sshExitCode -ne 0) {
    throw "SSH or remote Docker check failed: $sshTarget. $($sshCheckOutput -join ' ')"
}

$requiredKafkaTopics = @('transfer.completed', 'transfer.failed')
$kafkaContainer = ([string]$config.mySqlContainer) -replace '-mysql$', '-kafka'
if ($kafkaContainer -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'Derived Kafka container name contains unsupported characters.'
}
$previousPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$topicOutput = & ssh @sshBaseArgs $sshTarget "docker exec $kafkaContainer /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list" 2>&1
$topicExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousPreference
if ($topicExitCode -ne 0) {
    throw "Kafka topic readiness check failed on EC2: $($topicOutput -join ' ')"
}
$availableTopics = @($topicOutput | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
$missingTopics = @($requiredKafkaTopics | Where-Object { $_ -notin $availableTopics })
if ($missingTopics.Count -gt 0) {
    throw "Required Kafka topics are missing on EC2: $($missingTopics -join ', '). Deploy kafka-init before running evidence tests."
}

if (-not (Get-Command k6 -ErrorAction SilentlyContinue) -and -not (Test-DockerEngine)) {
    $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (Test-Path -LiteralPath $dockerDesktop) {
        Write-Host 'Starting Docker Desktop for the k6 container...'
        Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
        $ready = $false
        for ($attempt = 1; $attempt -le 30; $attempt++) {
            Start-Sleep -Seconds 2
            if (Test-DockerEngine) {
                $ready = $true
                break
            }
        }
        if (-not $ready) {
            throw 'Docker Desktop did not become ready within 60 seconds. Start it manually or install k6 locally.'
        }
    } else {
        throw 'Install k6 locally or start Docker Desktop.'
    }
}

$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portProbe.Start()
$tunnelPort = ([Net.IPEndPoint]$portProbe.LocalEndpoint).Port
$portProbe.Stop()
$tunnelArgs = @($sshBaseArgs) + @(
    '-N', '-L', "127.0.0.1:${tunnelPort}:127.0.0.1:8080", $sshTarget
)
$tunnelArgumentString = ($tunnelArgs | ForEach-Object {
    if ($_ -match '\s') { '"' + $_.Replace('"', '\"') + '"' } else { $_ }
}) -join ' '
$tunnel = Start-Process -FilePath 'ssh.exe' -ArgumentList $tunnelArgumentString -PassThru -WindowStyle Hidden

try {
    $tunnelReady = $false
    for ($attempt = 1; $attempt -le 15; $attempt++) {
        Start-Sleep -Seconds 1
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:$tunnelPort/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') {
                $tunnelReady = $true
                break
            }
        } catch {
            # The SSH tunnel may still be starting.
        }
    }
    if (-not $tunnelReady) {
        throw 'The SSH tunnel to the EC2 API Gateway did not become ready.'
    }

    $runArgs = @{
        Mode = [string]$config.mode
        BaseUrl = [string]$config.baseUrl
        PrepareBaseUrl = "http://127.0.0.1:$tunnelPort"
        Vus = [int]$config.vus
        Rate = [int]$config.rate
        Duration = [string]$config.duration
        Amount = [int]$config.amount
        SenderCount = [int]$config.senderCount
        ReceiverCount = $(if ($config.PSObject.Properties.Name -contains 'receiverCount') {
            [int]$config.receiverCount
        } else {
            0
        })
        SqlExecution = 'SshDocker'
        SshHost = [string]$config.ec2Host
        SshUser = [string]$config.sshUser
        SshKeyPath = [string]$config.sshKeyPath
        MySqlContainer = [string]$config.mySqlContainer
        AutoPrepareUsers = $true
        UseSshInternalFunding = $true
        SkipJUnit = [bool]$config.skipJUnit
    }
    & (Join-Path $PSScriptRoot 'run-test-evidence.ps1') @runArgs
    exit $LASTEXITCODE
} finally {
    if ($tunnel -and -not $tunnel.HasExited) {
        Stop-Process -Id $tunnel.Id -Force
    }
}
