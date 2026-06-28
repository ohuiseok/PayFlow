param(
    [Parameter(Mandatory = $true)]
    [string]$TestUsersFile,

    [Parameter(Mandatory = $true)]
    [long]$FundingPerSender,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$RunId,

    [Parameter(Mandatory = $true)]
    [string]$SshHost,

    [string]$SshUser = 'ubuntu',
    [string]$SshKeyPath,
    [string]$WalletContainer = 'payflow-wallet-service',
    [string]$ResultDir
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ($FundingPerSender -le 0) {
    throw 'FundingPerSender must be greater than zero.'
}
if ($WalletContainer -notmatch '^[A-Za-z0-9_.-]+$') {
    throw 'WalletContainer contains unsupported characters.'
}

$testData = Get-Content -LiteralPath $TestUsersFile -Raw | ConvertFrom-Json
$users = @($testData.users)
if ($users.Count -eq 0) {
    throw 'No test senders were found.'
}

$sshArgsBase = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new')
if ($SshKeyPath) {
    $sshArgsBase += @('-i', $SshKeyPath)
}
$sshTarget = "$SshUser@$SshHost"

function Invoke-RemoteWalletShell {
    param([string]$Script)
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Script))
    $remoteCommand = "echo '$encoded' | base64 -d | docker exec -i $WalletContainer sh"
    $sshArgs = @($sshArgsBase) + @($sshTarget, $remoteCommand)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = & ssh @sshArgs 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($exitCode -ne 0) {
        throw "Remote wallet request failed: $($output -join ' ')"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

$seeded = @()
foreach ($user in $users) {
    $userId = [long]$user.senderUserId
    if ($userId -le 0) {
        throw 'Invalid senderUserId in test user data.'
    }

    $walletLookupScript = @"
wget -qO- \
  --header='X-Internal-Request: true' \
  --header="X-Internal-Secret: `$INTERNAL_SECRET" \
  http://127.0.0.1:8082/wallets/users/$userId
"@
    $walletJson = Invoke-RemoteWalletShell $walletLookupScript
    try {
        $wallet = $walletJson | ConvertFrom-Json
    } catch {
        throw "Wallet lookup returned invalid JSON for user $userId."
    }
    if (-not $wallet.walletId) {
        throw "Wallet was not found for synthetic user $userId."
    }

    $remaining = $FundingPerSender
    $chunkIndex = 0
    $lastBalance = [decimal]$wallet.balance
    while ($remaining -gt 0) {
        $chunk = [long][math]::Min($remaining, 10000000)
        $referenceId = "evidence-$RunId-$userId-$chunkIndex"
        if ($referenceId.Length -gt 100) {
            throw 'Generated evidence referenceId exceeds 100 characters.'
        }
        $payload = [ordered]@{
            amount = $chunk
            referenceType = 'MANUAL_CHARGE'
            referenceId = $referenceId
        } | ConvertTo-Json -Compress
        $depositScript = @"
wget -qO- \
  --header='Content-Type: application/json' \
  --header='X-Internal-Request: true' \
  --header="X-Internal-Secret: `$INTERNAL_SECRET" \
  --post-data='$payload' \
  http://127.0.0.1:8082/wallets/$($wallet.walletId)/deposit
"@
        $depositJson = Invoke-RemoteWalletShell $depositScript
        try {
            $deposit = $depositJson | ConvertFrom-Json
        } catch {
            throw "Wallet deposit returned invalid JSON for user $userId."
        }
        $lastBalance = [decimal]$deposit.balance
        $remaining -= $chunk
        $chunkIndex++
    }

    $seeded += [ordered]@{
        userId = $userId
        walletId = [long]$wallet.walletId
        credited = $FundingPerSender
        balanceAfter = $lastBalance
    }
    Write-Host "Seeded synthetic user $userId with $FundingPerSender credits."
}

$summary = [ordered]@{
    runId = $RunId
    method = 'EC2_INTERNAL_WALLET_API'
    referenceType = 'MANUAL_CHARGE'
    senderCount = $users.Count
    fundingPerSender = $FundingPerSender
    totalCredited = [long]$FundingPerSender * $users.Count
    wallets = $seeded
}

if ($ResultDir) {
    New-Item -ItemType Directory -Path $ResultDir -Force | Out-Null
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $ResultDir 'wallet-seed-summary.json') -Encoding utf8
}
$summary | ConvertTo-Json -Depth 8
