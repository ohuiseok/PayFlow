param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9._-]+$')]
    [string]$RunId,

    [Parameter(Mandatory = $true)]
    [ValidateSet('Before', 'After')]
    [string]$Phase,

    [Parameter(Mandatory = $true)]
    [string]$ResultDir,

    [ValidateSet('concurrent', 'throughput', 'hot-wallet', 'idempotency')]
    [string]$Mode = 'concurrent',

    [ValidateSet('LocalDocker', 'SshDocker')]
    [string]$SqlExecution = 'LocalDocker',

    [string]$SshHost,
    [string]$SshUser = 'ubuntu',
    [string]$SshKeyPath,
    [string]$MySqlContainer,

    [int]$WaitSeconds = 0
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$workspace = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $workspace '.env'

function Read-DotEnv {
    param([string]$Name, [string]$Default)

    $processValue = [Environment]::GetEnvironmentVariable($Name)
    if ($processValue) {
        return $processValue
    }
    if (Test-Path -LiteralPath $envFile) {
        $line = Get-Content -LiteralPath $envFile | Where-Object {
            $_ -match "^$([regex]::Escape($Name))="
        } | Select-Object -Last 1
        if ($line) {
            return ($line -split '=', 2)[1].Trim()
        }
    }
    return $Default
}

function Assert-Identifier {
    param([string]$Value, [string]$Name)
    if ($Value -notmatch '^[A-Za-z0-9_]+$') {
        throw "$Name contains unsupported characters"
    }
}

$projectName = Read-DotEnv 'PROJECT_NAME' 'payflow'
$mysqlUser = Read-DotEnv 'MYSQL_USER' 'payflow'
$mysqlPassword = Read-DotEnv 'MYSQL_PASSWORD' 'payflow'
$transferDb = Read-DotEnv 'TRANSFER_DB_NAME' 'payflow_transfer'
$walletDb = Read-DotEnv 'WALLET_DB_NAME' 'payflow_wallet'
$ledgerDb = Read-DotEnv 'LEDGER_DB_NAME' 'payflow_ledger'
$mysqlContainer = if ($MySqlContainer) { $MySqlContainer } else { "$projectName-mysql" }

Assert-Identifier $transferDb 'TRANSFER_DB_NAME'
Assert-Identifier $walletDb 'WALLET_DB_NAME'
Assert-Identifier $ledgerDb 'LEDGER_DB_NAME'

New-Item -ItemType Directory -Path $ResultDir -Force | Out-Null
$logPath = Join-Path $ResultDir "sql-$($Phase.ToLowerInvariant()).log"

function Invoke-MySql {
    param([string]$Query, [switch]$Table)

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    if ($SqlExecution -eq 'SshDocker') {
        if (-not $SshHost) {
            throw 'SshHost is required for SshDocker SQL execution.'
        }
        if ($mysqlContainer -notmatch '^[A-Za-z0-9_.-]+$') {
            throw 'MySqlContainer contains unsupported characters.'
        }
        $encodedQuery = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Query))
        $formatArgs = if ($Table) { '--table' } else { '--batch --raw --skip-column-names' }
        $remoteCommand = "echo '$encodedQuery' | base64 -d | docker exec -i $mysqlContainer sh -lc 'MYSQL_PWD=`"`$MYSQL_PASSWORD`" mysql --default-character-set=utf8mb4 -u`"`$MYSQL_USER`" $formatArgs'"
        $sshArgs = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=accept-new')
        if ($SshKeyPath) {
            $sshArgs += @('-i', $SshKeyPath)
        }
        $sshArgs += @("$SshUser@$SshHost", $remoteCommand)
        $output = & ssh @sshArgs 2>&1
        $queryExitCode = $LASTEXITCODE
    } else {
        $dockerArgs = @(
            'exec', '--env', "MYSQL_PWD=$mysqlPassword", $mysqlContainer,
            'mysql', '--default-character-set=utf8mb4', '-u', $mysqlUser
        )
        if ($Table) {
            $dockerArgs += '--table'
        } else {
            $dockerArgs += @('--batch', '--raw', '--skip-column-names')
        }
        $dockerArgs += @('-e', $Query)
        $output = & docker @dockerArgs 2>&1
        $queryExitCode = $LASTEXITCODE
    }
    $ErrorActionPreference = $previousErrorActionPreference
    if ($queryExitCode -ne 0) {
        throw "MySQL query failed: $($output -join [Environment]::NewLine)"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Add-QueryEvidence {
    param([string]$Title, [string]$Query)
    Add-Content -LiteralPath $logPath -Encoding utf8 -Value "`n### $Title"
    Add-Content -LiteralPath $logPath -Encoding utf8 -Value (Invoke-MySql -Query $Query -Table)
}

if ($WaitSeconds -gt 0) {
    Start-Sleep -Seconds $WaitSeconds
}

$balanceRaw = Invoke-MySql "SELECT COALESCE(SUM(balance), 0), COUNT(*) FROM $walletDb.wallets;"
$balanceParts = $balanceRaw -split "`t"
$snapshot = [ordered]@{
    runId = $RunId
    phase = $Phase
    capturedAt = (Get-Date).ToString('o')
    totalWalletBalance = [decimal]$balanceParts[0]
    walletCount = [int]$balanceParts[1]
}

if ($Phase -eq 'Before') {
    $snapshot | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ResultDir 'sql-before.json') -Encoding utf8
    @(
        "PayFlow SQL evidence - BEFORE",
        "runId: $RunId",
        "capturedAt: $($snapshot.capturedAt)",
        "walletCount: $($snapshot.walletCount)",
        "totalWalletBalance: $($snapshot.totalWalletBalance)"
    ) | Set-Content -LiteralPath $logPath -Encoding utf8
    Write-Host "SQL baseline saved: $logPath"
    exit 0
}

$baselinePath = Join-Path $ResultDir 'sql-before.json'
if (-not (Test-Path -LiteralPath $baselinePath)) {
    throw "SQL baseline does not exist: $baselinePath"
}
$baseline = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
$keyPattern = "k6:${RunId}:%"
$keyCollate = "COLLATE utf8mb4_unicode_ci"

@(
    "PayFlow SQL evidence - AFTER",
    "runId: $RunId",
    "capturedAt: $($snapshot.capturedAt)",
    "idempotencyKeyPattern: $keyPattern"
) | Set-Content -LiteralPath $logPath -Encoding utf8

$transferCount = [int](Invoke-MySql "SELECT COUNT(*) FROM $transferDb.transfers WHERE idempotency_key LIKE '$keyPattern' $keyCollate;")
$duplicateIdempotency = [int](Invoke-MySql "SELECT COUNT(*) FROM (SELECT idempotency_key FROM $transferDb.transfers WHERE idempotency_key LIKE '$keyPattern' $keyCollate GROUP BY idempotency_key HAVING COUNT(*) > 1) duplicated;")
$duplicateWalletTransactions = [int](Invoke-MySql "SELECT COUNT(*) FROM (SELECT wt.wallet_id, wt.transaction_type, wt.reference_type, wt.reference_id FROM $walletDb.wallet_transactions wt JOIN $transferDb.transfers t ON wt.reference_id = CAST(t.id AS CHAR) WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate AND wt.reference_type = 'TRANSFER' GROUP BY wt.wallet_id, wt.transaction_type, wt.reference_type, wt.reference_id HAVING COUNT(*) > 1) duplicated;")
$succeededTransactionAnomalies = [int](Invoke-MySql "SELECT COUNT(*) FROM (SELECT t.id, SUM(CASE WHEN wt.transaction_type = 'WITHDRAW' THEN 1 ELSE 0 END) withdrawals, SUM(CASE WHEN wt.transaction_type = 'DEPOSIT' THEN 1 ELSE 0 END) deposits FROM $transferDb.transfers t LEFT JOIN $walletDb.wallet_transactions wt ON wt.reference_id = CAST(t.id AS CHAR) AND wt.reference_type = 'TRANSFER' WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate AND t.status = 'SUCCEEDED' GROUP BY t.id HAVING withdrawals <> 1 OR deposits <> 1) anomalies;")
$missingOutbox = [int](Invoke-MySql "SELECT COUNT(*) FROM $transferDb.transfers t LEFT JOIN $transferDb.outbox_events o ON o.event_key = CAST(t.id AS CHAR) WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate AND o.id IS NULL;")
$unpublishedOutbox = [int](Invoke-MySql "SELECT COUNT(*) FROM $transferDb.transfers t JOIN $transferDb.outbox_events o ON o.event_key = CAST(t.id AS CHAR) WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate AND o.status <> 'PUBLISHED';")
$missingSucceededLedger = [int](Invoke-MySql "SELECT COUNT(*) FROM $transferDb.transfers t LEFT JOIN $ledgerDb.ledger_entries le ON le.transfer_id = t.id WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate AND t.status = 'SUCCEEDED' AND le.id IS NULL;")
$duplicateLedger = [int](Invoke-MySql "SELECT COUNT(*) FROM (SELECT le.transfer_id FROM $ledgerDb.ledger_entries le JOIN $transferDb.transfers t ON t.id = le.transfer_id WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate GROUP BY le.transfer_id HAVING COUNT(*) > 1) duplicated;")
$negativeBalances = [int](Invoke-MySql "SELECT COUNT(*) FROM $walletDb.wallets WHERE balance < 0;")
$compensationRequired = [int](Invoke-MySql "SELECT COUNT(*) FROM $transferDb.transfers WHERE idempotency_key LIKE '$keyPattern' $keyCollate AND status = 'COMPENSATION_REQUIRED';")
$balanceDelta = [decimal]$snapshot.totalWalletBalance - [decimal]$baseline.totalWalletBalance
$expectedTransferCountPassed = if ($Mode -eq 'idempotency') { $transferCount -eq 1 } else { $transferCount -gt 0 }

Add-QueryEvidence 'Transfer status summary' "SELECT status, COUNT(*) AS count, COALESCE(SUM(amount), 0) AS amount_sum FROM $transferDb.transfers WHERE idempotency_key LIKE '$keyPattern' $keyCollate GROUP BY status ORDER BY status;"
Add-QueryEvidence 'Outbox status summary' "SELECT o.status, COUNT(*) AS count FROM $transferDb.transfers t JOIN $transferDb.outbox_events o ON o.event_key = CAST(t.id AS CHAR) WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate GROUP BY o.status ORDER BY o.status;"
Add-QueryEvidence 'Succeeded transfer wallet transaction coverage' "SELECT t.id AS transfer_id, t.amount, SUM(CASE WHEN wt.transaction_type = 'WITHDRAW' THEN 1 ELSE 0 END) AS withdrawals, SUM(CASE WHEN wt.transaction_type = 'DEPOSIT' THEN 1 ELSE 0 END) AS deposits FROM $transferDb.transfers t LEFT JOIN $walletDb.wallet_transactions wt ON wt.reference_id = CAST(t.id AS CHAR) AND wt.reference_type = 'TRANSFER' WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate AND t.status = 'SUCCEEDED' GROUP BY t.id, t.amount ORDER BY t.id;"
Add-QueryEvidence 'Ledger coverage' "SELECT t.status AS transfer_status, COUNT(*) AS transfer_count, SUM(CASE WHEN le.id IS NOT NULL THEN 1 ELSE 0 END) AS ledger_count FROM $transferDb.transfers t LEFT JOIN $ledgerDb.ledger_entries le ON le.transfer_id = t.id WHERE t.idempotency_key LIKE '$keyPattern' $keyCollate GROUP BY t.status ORDER BY t.status;"

$passed = $expectedTransferCountPassed -and
    $duplicateIdempotency -eq 0 -and
    $duplicateWalletTransactions -eq 0 -and
    $succeededTransactionAnomalies -eq 0 -and
    $missingOutbox -eq 0 -and
    $unpublishedOutbox -eq 0 -and
    $missingSucceededLedger -eq 0 -and
    $duplicateLedger -eq 0 -and
    $negativeBalances -eq 0 -and
    $compensationRequired -eq 0 -and
    $balanceDelta -eq 0

$summary = [ordered]@{
    runId = $RunId
    passed = $passed
    mode = $Mode
    transferCount = $transferCount
    expectedTransferCountPassed = $expectedTransferCountPassed
    totalWalletBalanceBefore = [decimal]$baseline.totalWalletBalance
    totalWalletBalanceAfter = $snapshot.totalWalletBalance
    totalWalletBalanceDelta = $balanceDelta
    duplicateIdempotencyKeys = $duplicateIdempotency
    duplicateWalletTransactions = $duplicateWalletTransactions
    succeededTransactionAnomalies = $succeededTransactionAnomalies
    missingOutboxEvents = $missingOutbox
    unpublishedOutboxEvents = $unpublishedOutbox
    missingSucceededLedgerEntries = $missingSucceededLedger
    duplicateLedgerEntries = $duplicateLedger
    negativeWalletBalances = $negativeBalances
    compensationRequired = $compensationRequired
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ResultDir 'sql-summary.json') -Encoding utf8

Add-Content -LiteralPath $logPath -Encoding utf8 -Value "`n### Automated verdict"
Add-Content -LiteralPath $logPath -Encoding utf8 -Value ($summary | ConvertTo-Json)
Write-Host "SQL verification $($(if ($passed) { 'PASSED' } else { 'FAILED' })): $logPath"

if (-not $passed) {
    exit 1
}
