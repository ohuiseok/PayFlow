param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$SenderPhone,
    [long]$ReceiverUserId,
    [int]$Amount = 1000,
    [string]$OutputPath = 'k6\test-users.local.json',
    [string]$StatePath = 'k6\test-accounts.local.json',
    [int]$SenderCount = 1,
    [long]$FundingPerSender = 0,
    [switch]$AutoCreate,
    [switch]$AllowMockFunding
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$workspace = Split-Path -Parent $PSScriptRoot
$baseUrlValue = $BaseUrl.TrimEnd('/')

function Resolve-WorkspacePath {
    param([string]$Path)
    if ([IO.Path]::IsPathRooted($Path)) {
        return [IO.Path]::GetFullPath($Path)
    }
    return [IO.Path]::GetFullPath((Join-Path $workspace $Path))
}

function Write-Utf8Json {
    param([string]$Path, $Value, [int]$Depth = 8)
    $parent = Split-Path -Parent $Path
    New-Item -ItemType Directory -Path $parent -Force | Out-Null
    $json = $Value | ConvertTo-Json -Depth $Depth
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [hashtable]$Headers = @{}
    )

    $parameters = @{
        Method = $Method
        Uri = "$baseUrlValue$Path"
        Headers = $Headers
        TimeoutSec = 30
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = ($Body | ConvertTo-Json -Depth 8)
    }
    try {
        $bodyValue = Invoke-RestMethod @parameters
        return [pscustomobject]@{ Status = 200; Body = $bodyValue; Error = $null }
    } catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $errorBody = if ($_.ErrorDetails) { $_.ErrorDetails.Message } else { $_.Exception.Message }
        return [pscustomobject]@{ Status = $status; Body = $null; Error = $errorBody }
    }
}

function New-TestPhone {
    return '010' + (Get-Random -Minimum 10000000 -Maximum 99999999).ToString()
}

function New-TestPassword {
    return 'Pf!' + [Guid]::NewGuid().ToString('N') + '9a'
}

function Invoke-Login {
    param($Account)
    for ($attempt = 1; $attempt -le 12; $attempt++) {
        $response = Invoke-Api -Method POST -Path '/api/users/login' -Body @{
            phoneNumber = $Account.phone
            password = $Account.password
        }
        if ($response.Status -eq 200) {
            return $response.Body
        }
        if ($response.Status -eq 401) {
            return $null
        }
        if ($response.Status -eq 429) {
            Write-Host 'Login rate limit reached; waiting 13 seconds...'
            Start-Sleep -Seconds 13
            continue
        }
        throw "Login request failed for $($Account.phone): HTTP $($response.Status) $($response.Error)"
    }
    throw "Login rate limit did not clear for $($Account.phone)."
}

function Test-Token {
    param($Account)
    if (-not $Account.token) {
        return $false
    }
    $response = Invoke-Api -Method GET -Path '/api/users/me' -Headers @{
        Authorization = "Bearer $($Account.token)"
    }
    if ($response.Status -eq 200) {
        $Account.userId = [long]$response.Body.userId
        return $true
    }
    return $false
}

function Ensure-Account {
    param($Account)
    if (Test-Token $Account) {
        Write-Host "Reusing test account: $($Account.phone)"
        return
    }

    $login = Invoke-Login $Account
    if ($null -eq $login) {
        $signup = Invoke-Api -Method POST -Path '/api/users' -Body @{
            phoneNumber = $Account.phone
            password = $Account.password
            name = $Account.name
        }
        if ($signup.Status -ne 200 -and $signup.Status -ne 201) {
            if ($signup.Status -eq 409) {
                throw "Account $($Account.phone) exists but its password does not match the local test state. Delete the local state file or use the original state."
            }
            throw "Signup failed for $($Account.phone): HTTP $($signup.Status) $($signup.Error)"
        }
        Write-Host "Created test account: $($Account.phone)"
        $login = Invoke-Login $Account
    }
    if ($null -eq $login -or -not $login.accessToken) {
        throw "Could not obtain a JWT for $($Account.phone)."
    }
    $Account.userId = [long]$login.user.userId
    $Account.token = [string]$login.accessToken
}

function Ensure-MockFunding {
    param($Account, [long]$RequiredAmount)
    if ($RequiredAmount -le 0) {
        return
    }
    if (-not $AllowMockFunding) {
        throw 'Funding was requested but AllowMockFunding was not set. Use it only when EC2 OPENBANKING_MODE=mock.'
    }

    $headers = @{ Authorization = "Bearer $($Account.token)" }
    $accountsResponse = Invoke-Api -Method GET -Path '/api/bank/accounts' -Headers $headers
    if ($accountsResponse.Status -ne 200) {
        throw "Bank account lookup failed for user $($Account.userId): HTTP $($accountsResponse.Status) $($accountsResponse.Error)"
    }
    $bankAccounts = @($accountsResponse.Body)
    if ($bankAccounts.Count -eq 0) {
        $accountNumber = ($Account.phone + $Account.userId.ToString().PadLeft(6, '0'))
        $createResponse = Invoke-Api -Method POST -Path '/api/bank/accounts' -Headers $headers -Body @{
            bankCode = '004'
            accountNumber = $accountNumber
            accountHolderName = $Account.name
        }
        if ($createResponse.Status -ne 200 -and $createResponse.Status -ne 201) {
            throw "Bank account creation failed for user $($Account.userId): HTTP $($createResponse.Status) $($createResponse.Error)"
        }
        $bankAccount = $createResponse.Body
    } else {
        $bankAccount = $bankAccounts[0]
    }

    $remaining = $RequiredAmount
    $chunkIndex = 0
    while ($remaining -gt 0) {
        $chunk = [math]::Min($remaining, 10000000)
        $fundingKey = "evidence-funding-$($Account.userId)-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())-$chunkIndex"
        $fundingHeaders = @{
            Authorization = "Bearer $($Account.token)"
            'Idempotency-Key' = $fundingKey
        }
        $deposit = Invoke-Api -Method POST -Path '/api/bank/deposits' -Headers $fundingHeaders -Body @{
            bankAccountId = [long]$bankAccount.bankAccountId
            amount = [long]$chunk
        }
        if (($deposit.Status -ne 200 -and $deposit.Status -ne 201) -or $deposit.Body.status -ne 'SUCCEEDED') {
            throw "Mock funding failed for user $($Account.userId). Verify EC2 OPENBANKING_MODE=mock. HTTP $($deposit.Status) status=$($deposit.Body.status) $($deposit.Error)"
        }
        $remaining -= $chunk
        $chunkIndex++
    }
    Write-Host "Funded user $($Account.userId) with $RequiredAmount test credits."
}

$resolvedOutputPath = Resolve-WorkspacePath $OutputPath
$resolvedStatePath = Resolve-WorkspacePath $StatePath

if ($Amount -le 0) {
    throw 'Amount must be greater than zero.'
}

if (-not $AutoCreate) {
    if (-not $SenderPhone) {
        $SenderPhone = Read-Host 'Sender phone number'
    }
    if ($ReceiverUserId -le 0) {
        $ReceiverUserId = [long](Read-Host 'Receiver userId')
    }
    $securePassword = Read-Host 'Sender password' -AsSecureString
    $credential = [pscredential]::new($SenderPhone, $securePassword)
    $account = [pscustomobject]@{
        kind = 'sender'; phone = $SenderPhone; password = $credential.GetNetworkCredential().Password
        name = 'PayFlow Evidence Sender'; userId = 0L; token = ''
    }
    Ensure-Account $account
    if ($account.userId -eq $ReceiverUserId) {
        throw 'Sender and receiver must be different users.'
    }
    $senders = @($account)
    $receiver = [pscustomobject]@{ userId = $ReceiverUserId }
} else {
    if ($SenderCount -lt 1) {
        throw 'SenderCount must be at least one.'
    }
    if (Test-Path -LiteralPath $resolvedStatePath) {
        $state = Get-Content -LiteralPath $resolvedStatePath -Raw | ConvertFrom-Json
        if ($state.baseUrl -ne $baseUrlValue) {
            throw "The saved account state belongs to $($state.baseUrl), not $baseUrlValue. Use a different StatePath or delete the old state."
        }
    } else {
        $state = [pscustomobject]@{ baseUrl = $baseUrlValue; accounts = @() }
    }

    $accounts = @($state.accounts)
    $receiver = @($accounts | Where-Object { $_.kind -eq 'receiver' }) | Select-Object -First 1
    if ($null -eq $receiver) {
        $receiver = [pscustomobject]@{
            kind = 'receiver'; phone = New-TestPhone; password = New-TestPassword
            name = 'PayFlow Evidence Receiver'; userId = 0L; token = ''
        }
        $accounts += $receiver
    }
    $senders = @($accounts | Where-Object { $_.kind -eq 'sender' })
    while ($senders.Count -lt $SenderCount) {
        $index = $senders.Count + 1
        $sender = [pscustomobject]@{
            kind = 'sender'; phone = New-TestPhone; password = New-TestPassword
            name = "PayFlow Evidence Sender $index"; userId = 0L; token = ''
        }
        $accounts += $sender
        $senders += $sender
    }
    $senders = @($senders | Select-Object -First $SenderCount)
    $state.accounts = $accounts

    Ensure-Account $receiver
    Write-Utf8Json -Path $resolvedStatePath -Value $state
    foreach ($sender in $senders) {
        Ensure-Account $sender
        Ensure-MockFunding -Account $sender -RequiredAmount $FundingPerSender
        Write-Utf8Json -Path $resolvedStatePath -Value $state
    }
}

$testData = [ordered]@{
    amount = $Amount
    users = @($senders | ForEach-Object {
        [ordered]@{
            senderUserId = [long]$_.userId
            receiverUserId = [long]$receiver.userId
            token = [string]$_.token
        }
    })
}
Write-Utf8Json -Path $resolvedOutputPath -Value $testData -Depth 5

Write-Host "Test user data created: $resolvedOutputPath"
Write-Host "senders: $($senders.Count), receiverUserId: $($receiver.userId)"
Write-Host 'JWTs and synthetic credentials were written only to ignored local files.'
