param(
  [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

function New-JsonHeaders {
  param([string]$Token, [string]$IdempotencyKey)

  $headers = @{ "Content-Type" = "application/json" }
  if ($Token) {
    $headers.Authorization = "Bearer $Token"
  }
  if ($IdempotencyKey) {
    $headers["Idempotency-Key"] = $IdempotencyKey
  }
  return $headers
}

function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [object]$Body,
    [string]$Token,
    [string]$IdempotencyKey
  )

  $headers = New-JsonHeaders -Token $Token -IdempotencyKey $IdempotencyKey
  $uri = "$BaseUrl$Path"
  $json = $null
  if ($null -ne $Body) {
    $json = $Body | ConvertTo-Json -Depth 10
  }

  Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $json
}

function Write-Step {
  param([string]$Message)
  Write-Host "[api-smoke] $Message"
}

$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = "password12"
$parentPhone = "0107$($suffix.ToString().Substring($suffix.ToString().Length - 7))"
$childPhone = "0108$($suffix.ToString().Substring($suffix.ToString().Length - 7))"

Write-Step "sign up parent and child"
$parent = Invoke-Api -Method Post -Path "/api/users" -Body @{
  phoneNumber = $parentPhone
  password = $password
  name = "Smoke Parent"
  role = "PARENT"
}
$child = Invoke-Api -Method Post -Path "/api/users" -Body @{
  phoneNumber = $childPhone
  password = $password
  name = "Smoke Child"
  role = "CHILD"
}

Write-Step "login parent and child"
$parentLogin = Invoke-Api -Method Post -Path "/api/users/login" -Body @{
  phoneNumber = $parentPhone
  password = $password
}
$childLogin = Invoke-Api -Method Post -Path "/api/users/login" -Body @{
  phoneNumber = $childPhone
  password = $password
}
$parentToken = $parentLogin.accessToken
$childToken = $childLogin.accessToken

Write-Step "register parent bank account and charge wallet"
$bankAccount = Invoke-Api -Method Post -Path "/api/bank/accounts" -Token $parentToken -Body @{
  bankCode = "004"
  bankName = "MOCK_BANK"
  accountNumber = "123456789012"
  accountHolderName = "Smoke Parent"
}
$charge = Invoke-Api -Method Post -Path "/api/bank/deposits" -Token $parentToken -IdempotencyKey "smoke-charge-$suffix" -Body @{
  bankAccountId = $bankAccount.bankAccountId
  amount = 50000
}
if ($charge.status -ne "SUCCEEDED" -and $charge.status -ne "COMPLETED") {
  throw "Expected charge to succeed, got $($charge.status)"
}

Write-Step "create family link"
$familyLink = Invoke-Api -Method Post -Path "/api/families/links" -Token $parentToken -Body @{
  childUserId = $child.userId
}
$children = Invoke-Api -Method Get -Path "/api/families/children" -Token $parentToken
$parents = Invoke-Api -Method Get -Path "/api/families/parents" -Token $childToken
if (@($children).Count -lt 1 -or @($parents).Count -lt 1) {
  throw "Expected family link to be visible to parent and child"
}

Write-Step "create and submit mission"
$mission = Invoke-Api -Method Post -Path "/api/missions" -Token $parentToken -Body @{
  childUserId = $child.userId
  title = "Smoke mission"
  description = "API smoke mission"
  rewardAmount = 3000
}
$submitted = Invoke-Api -Method Patch -Path "/api/missions/$($mission.missionId)/submit" -Token $childToken -Body @{
  submissionNote = "Done"
}
if ($submitted.status -ne "SUBMITTED") {
  throw "Expected SUBMITTED, got $($submitted.status)"
}

Write-Step "approve and pay mission"
$approved = Invoke-Api -Method Patch -Path "/api/missions/$($mission.missionId)/approve" -Token $parentToken
if ($approved.status -ne "APPROVED") {
  throw "Expected APPROVED, got $($approved.status)"
}
$paid = Invoke-Api -Method Post -Path "/api/missions/$($mission.missionId)/pay" -Token $parentToken
if ($paid.status -ne "PAID") {
  throw "Expected PAID, got $($paid.status)"
}

Write-Step "read summaries"
$parentSummary = Invoke-Api -Method Get -Path "/api/cashbook/parent/summary" -Token $parentToken
$childSummary = Invoke-Api -Method Get -Path "/api/cashbook/children/$($child.userId)/summary" -Token $childToken
if ($parentSummary.creditBalance -lt 1 -or $childSummary.walletBalance -lt 1) {
  throw "Expected positive wallet balances after charge/payment"
}

Write-Step "success"
[PSCustomObject]@{
  parentUserId = $parent.userId
  childUserId = $child.userId
  familyLinkId = $familyLink.familyLinkId
  missionId = $mission.missionId
  chargeId = $charge.bankingTransferId
  parentBalance = $parentSummary.creditBalance
  childBalance = $childSummary.walletBalance
} | ConvertTo-Json -Depth 5
