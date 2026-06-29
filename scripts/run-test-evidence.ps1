param(
    [ValidateSet('concurrent', 'throughput', 'hot-wallet', 'idempotency')]
    [string]$Mode = 'concurrent',

    [string]$RunId = (Get-Date -Format 'yyyyMMdd-HHmmss'),
    [string]$TestUsersFile = 'k6\test-users.local.json',
    [string]$ResultRoot = 'results',
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$PrepareBaseUrl,
    [string]$AccountStateIdentity,
    [int]$Vus = 1000,
    [int]$Rate = 420,
    [string]$Duration = '5m',
    [int]$Amount = 1000,
    [int]$OutboxRecoveryWaitSeconds = 180,
    [string]$K6Image = 'grafana/k6:latest',
    [int]$SenderCount = 10,
    [int]$ReceiverCount = 0,
    [long]$FundingPerSender = 0,
    [ValidateSet('LocalDocker', 'SshDocker')]
    [string]$SqlExecution = 'LocalDocker',
    [string]$SshHost,
    [string]$SshUser = 'ubuntu',
    [string]$SshKeyPath,
    [string]$MySqlContainer,
    [switch]$AutoPrepareUsers,
    [switch]$AllowMockFunding,
    [switch]$UseSshInternalFunding,
    [switch]$KafkaOutage,
    [switch]$SkipJUnit
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($RunId -notmatch '^[A-Za-z0-9._-]+$') {
    throw 'RunId may contain only letters, numbers, dot, underscore, and hyphen.'
}

$workspace = Split-Path -Parent $PSScriptRoot
function Get-ProjectName {
    if ($env:PROJECT_NAME) {
        return $env:PROJECT_NAME
    }
    $envFile = Join-Path $workspace '.env'
    if (Test-Path -LiteralPath $envFile) {
        $line = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^PROJECT_NAME=' } | Select-Object -Last 1
        if ($line) {
            return ($line -split '=', 2)[1].Trim()
        }
    }
    return 'payflow'
}

$dataPath = if ([IO.Path]::IsPathRooted($TestUsersFile)) {
    [IO.Path]::GetFullPath($TestUsersFile)
} else {
    [IO.Path]::GetFullPath((Join-Path $workspace $TestUsersFile))
}
$resultRootPath = if ([IO.Path]::IsPathRooted($ResultRoot)) {
    [IO.Path]::GetFullPath($ResultRoot)
} else {
    [IO.Path]::GetFullPath((Join-Path $workspace $ResultRoot))
}
$resultDir = Join-Path $resultRootPath $RunId

function Convert-DurationToSeconds {
    param([string]$Value)
    if ($Value -match '^(\d+)(s|m|h)$') {
        $number = [long]$Matches[1]
        switch ($Matches[2]) {
            's' { return $number }
            'm' { return $number * 60 }
            'h' { return $number * 3600 }
        }
    }
    throw "Unsupported duration '$Value'. Use values such as 30s, 5m, or 1h."
}

if ($AutoPrepareUsers) {
    $accountBaseUrl = if ($PrepareBaseUrl) { $PrepareBaseUrl } else { $BaseUrl }
    if ($Mode -eq 'hot-wallet' -or $Mode -eq 'idempotency') {
        $SenderCount = 1
        $ReceiverCount = 1
    } elseif ($ReceiverCount -le 0) {
        $ReceiverCount = $SenderCount
    }
    if ($FundingPerSender -le 0) {
        $requestCount = if ($Mode -eq 'throughput') {
            [long]$Rate * (Convert-DurationToSeconds $Duration)
        } else {
            [long]$Vus
        }
        $FundingPerSender = [long][math]::Ceiling(($requestCount * $Amount * 1.20) / $SenderCount)
    }
    $prepareArgs = @{
        BaseUrl = $accountBaseUrl
        Amount = $Amount
        OutputPath = $TestUsersFile
        StateIdentity = $AccountStateIdentity
        SenderCount = $SenderCount
        ReceiverCount = $ReceiverCount
        FundingPerSender = $(if ($UseSshInternalFunding) { 0 } else { $FundingPerSender })
        AutoCreate = $true
        AllowMockFunding = [bool]$AllowMockFunding
    }
    $accountStatePath = Join-Path $workspace 'k6\test-accounts.local.json'
    if (Test-Path -LiteralPath $accountStatePath) {
        Write-Host "Reusing test account state: $accountStatePath"
    }
    & (Join-Path $PSScriptRoot 'prepare-test-users.ps1') @prepareArgs
}

$expectedIterations = if ($Mode -eq 'throughput') {
    [long]$Rate * (Convert-DurationToSeconds $Duration)
} else {
    [long]$Vus
}
$expectedTransferCount = if ($Mode -eq 'idempotency') { 1L } else { $expectedIterations }
$expectedSucceededCount = $expectedTransferCount

if (-not (Test-Path -LiteralPath $dataPath)) {
    throw "Test user data does not exist: $dataPath. Copy k6/test-users.example.json to k6/test-users.local.json and set real JWT values."
}
if ((Get-Content -LiteralPath $dataPath -Raw) -match 'REPLACE_WITH') {
    throw 'Replace placeholder JWT values in the test user data before running the test.'
}

New-Item -ItemType Directory -Path $resultDir -Force | Out-Null
$localK6 = Get-Command k6 -ErrorAction SilentlyContinue
if ($SqlExecution -eq 'LocalDocker' -or -not $localK6) {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & docker info *> $null
    $dockerInfoExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorActionPreference
    if ($dockerInfoExitCode -ne 0) {
        throw 'Docker is required for local SQL or the k6 Docker fallback. Start Docker Desktop or install k6 locally.'
    }
}
if ($UseSshInternalFunding) {
    if ($SqlExecution -ne 'SshDocker' -or -not $SshHost) {
        throw 'UseSshInternalFunding requires SqlExecution=SshDocker and SshHost.'
    }
    & (Join-Path $PSScriptRoot 'seed-remote-wallets.ps1') `
        -TestUsersFile $dataPath `
        -FundingPerSender $FundingPerSender `
        -RunId $RunId `
        -SshHost $SshHost `
        -SshUser $SshUser `
        -SshKeyPath $SshKeyPath `
        -ResultDir $resultDir
}
$gitSha = (& git -C $workspace rev-parse HEAD 2>$null)
$metadata = [ordered]@{
    runId = $RunId
    startedAt = (Get-Date).ToString('o')
    gitSha = $gitSha
    mode = $Mode
    baseUrl = $BaseUrl
    vus = $Vus
    rate = $Rate
    duration = $Duration
    amount = $Amount
    kafkaOutage = [bool]$KafkaOutage
    sqlExecution = $SqlExecution
    senderCount = $SenderCount
    receiverCount = $ReceiverCount
    fundingPerSender = $FundingPerSender
    fundingMethod = $(if ($UseSshInternalFunding) { 'EC2_INTERNAL_WALLET_API' } else { 'BANKING_API' })
    expectedIterations = $expectedIterations
    expectedTransferCount = $expectedTransferCount
    expectedSucceededCount = $expectedSucceededCount
    testUsersFile = Split-Path -Leaf $dataPath
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $resultDir 'run-metadata.json') -Encoding utf8

Write-Host "[1/4] Capturing SQL baseline..."
$sqlArgs = @{
    RunId = $RunId
    ResultDir = $resultDir
    Mode = $Mode
    SqlExecution = $SqlExecution
    SshHost = $SshHost
    SshUser = $SshUser
    SshKeyPath = $SshKeyPath
    MySqlContainer = $MySqlContainer
    ExpectedTransferCount = $expectedTransferCount
    ExpectedSucceededCount = $expectedSucceededCount
}
& (Join-Path $PSScriptRoot 'verify-test-evidence.ps1') @sqlArgs -Phase Before
if ($LASTEXITCODE -ne 0) {
    throw 'Failed to capture SQL baseline.'
}

$projectName = Get-ProjectName
$kafkaContainer = "$projectName-kafka"
$gatewayContainer = "$projectName-api-gateway"
$kafkaStopped = $false
$k6ExitCode = 1

try {
if ($KafkaOutage) {
        if ($SqlExecution -eq 'SshDocker') {
            throw 'KafkaOutage is not yet supported through remote SSH execution.'
        }
        Write-Host "Stopping Kafka for Outbox recovery evidence..."
        & docker stop $kafkaContainer | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Could not stop $kafkaContainer"
        }
        $kafkaStopped = $true
    }

    Write-Host "[2/4] Running k6 scenario '$Mode'..."
    $k6Log = Join-Path $resultDir 'k6-console.log'
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    if ($localK6) {
        $k6Args = @(
            'run',
            '-e', "MODE=$Mode",
            '-e', "RUN_ID=$RunId",
            '-e', "BASE_URL=$BaseUrl",
            '-e', "TEST_USERS_FILE=$dataPath",
            '-e', "RESULT_DIR=$resultDir",
            '-e', "VUS=$Vus",
            '-e', "RATE=$Rate",
            '-e', "DURATION=$Duration",
            '-e', "MAX_DURATION=$Duration",
            '-e', "AMOUNT=$Amount",
            (Join-Path $workspace 'k6\transfer-evidence.js')
        )
        & $localK6.Source @k6Args 2>&1 | Tee-Object -FilePath $k6Log
        $k6ExitCode = $LASTEXITCODE
    } else {
        Write-Host "Local k6 was not found; using Docker image $K6Image"
        $workspacePrefix = $workspace.TrimEnd('\') + '\'
        if (-not $dataPath.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Docker k6 fallback requires TestUsersFile to be inside the workspace.'
        }
        if (-not $resultDir.StartsWith($workspacePrefix, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'Docker k6 fallback requires ResultRoot to be inside the workspace.'
        }
        $dataRelative = $dataPath.Substring($workspace.TrimEnd('\', '/').Length).TrimStart('\', '/').Replace('\', '/')
        $resultRelative = $resultDir.Substring($workspace.TrimEnd('\', '/').Length).TrimStart('\', '/').Replace('\', '/')
        $dockerBaseUrl = if ($BaseUrl -eq 'http://localhost:8080') { 'http://api-gateway:8080' } else { $BaseUrl }
        $dockerArgs = @(
            'run', '--rm',
            '--mount', "type=bind,source=$workspace,target=/work",
            $K6Image
        )
        if ($BaseUrl -eq 'http://localhost:8080') {
            $network = (& docker inspect --format '{{range $name, $config := .NetworkSettings.Networks}}{{$name}}{{end}}' $gatewayContainer 2>$null).Trim()
            if (-not $network) {
                throw "Could not resolve the Docker network from $gatewayContainer"
            }
            $dockerArgs = @('run', '--rm', '--network', $network, '--mount', "type=bind,source=$workspace,target=/work", $K6Image)
        }
        $dockerArgs += @(
            'run',
            '-e', "MODE=$Mode",
            '-e', "RUN_ID=$RunId",
            '-e', "BASE_URL=$dockerBaseUrl",
            '-e', "TEST_USERS_FILE=/work/$dataRelative",
            '-e', "RESULT_DIR=/work/$resultRelative",
            '-e', "VUS=$Vus",
            '-e', "RATE=$Rate",
            '-e', "DURATION=$Duration",
            '-e', "MAX_DURATION=$Duration",
            '-e', "AMOUNT=$Amount",
            '/work/k6/transfer-evidence.js'
        )
        & docker @dockerArgs 2>&1 | Tee-Object -FilePath $k6Log
        $k6ExitCode = $LASTEXITCODE
    }
    $ErrorActionPreference = $previousErrorActionPreference
} finally {
    $ErrorActionPreference = 'Stop'
    if ($kafkaStopped) {
        Write-Host 'Restarting Kafka...'
        & docker start $kafkaContainer | Out-Null
        $kafkaStopped = $false
    }
}

Write-Host "[3/4] Waiting for Outbox and ledger convergence, then verifying SQL..."
& (Join-Path $PSScriptRoot 'verify-test-evidence.ps1') @sqlArgs -Phase After -WaitSeconds $OutboxRecoveryWaitSeconds
$sqlExitCode = $LASTEXITCODE

$junitExitCode = 0
if (-not $SkipJUnit) {
    Write-Host '[4/4] Running JUnit evidence tests...'
    & (Join-Path $PSScriptRoot 'run-junit-evidence.ps1') -ResultDir $resultDir
    $junitExitCode = $LASTEXITCODE
} else {
    Write-Host '[4/4] JUnit evidence skipped.'
}

$k6SummaryPath = Join-Path $resultDir 'k6-summary.json'
$sqlSummaryPath = Join-Path $resultDir 'sql-summary.json'
$junitSummaryPath = Join-Path $resultDir 'junit\junit-summary.json'
$combined = [ordered]@{
    runId = $RunId
    completedAt = (Get-Date).ToString('o')
    passed = $k6ExitCode -eq 0 -and $sqlExitCode -eq 0 -and $junitExitCode -eq 0
    exitCodes = [ordered]@{ k6 = $k6ExitCode; sql = $sqlExitCode; junit = $junitExitCode }
    k6 = if (Test-Path -LiteralPath $k6SummaryPath) { Get-Content -LiteralPath $k6SummaryPath -Raw | ConvertFrom-Json } else { $null }
    sql = if (Test-Path -LiteralPath $sqlSummaryPath) { Get-Content -LiteralPath $sqlSummaryPath -Raw | ConvertFrom-Json } else { $null }
    junit = if (Test-Path -LiteralPath $junitSummaryPath) { Get-Content -LiteralPath $junitSummaryPath -Raw | ConvertFrom-Json } else { $null }
}
$combined | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultDir 'evidence-summary.json') -Encoding utf8
$combined | ConvertTo-Json -Depth 10 | Tee-Object -FilePath (Join-Path $resultDir 'evidence-summary.log')

Write-Host "Evidence directory: $resultDir"
if (-not $combined.passed) {
    exit 1
}
