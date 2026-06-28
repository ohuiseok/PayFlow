param(
    [Parameter(Mandatory = $true)]
    [string]$ResultDir
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$workspace = Split-Path -Parent $PSScriptRoot
$resolvedResultDir = if ([IO.Path]::IsPathRooted($ResultDir)) {
    [IO.Path]::GetFullPath($ResultDir)
} else {
    [IO.Path]::GetFullPath((Join-Path $workspace $ResultDir))
}
$junitResultDir = Join-Path $resolvedResultDir 'junit'
New-Item -ItemType Directory -Path $junitResultDir -Force | Out-Null

$modules = @('transfer-service', 'ledger-service')
$moduleExitCodes = [ordered]@{}

foreach ($module in $modules) {
    $moduleDir = Join-Path $workspace $module
    $logPath = Join-Path $junitResultDir "$module.log"
    Push-Location $moduleDir
    try {
        # Windows PowerShell 5 treats any native stderr line as NativeCommandError
        # when ErrorActionPreference is Stop. The JVM can emit harmless warnings there.
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        # Redirect stderr inside cmd.exe so Windows PowerShell does not wrap JVM
        # warnings as NativeCommandError records in the evidence log.
        & cmd.exe /d /c '.\gradlew.bat test --rerun-tasks --console=plain 2>&1' | Tee-Object -FilePath $logPath
        $moduleExitCodes[$module] = $LASTEXITCODE
        $ErrorActionPreference = $previousErrorActionPreference
    } finally {
        $ErrorActionPreference = 'Stop'
        Pop-Location
    }

    $htmlSource = Join-Path $moduleDir 'build\reports\tests\test'
    if (Test-Path -LiteralPath $htmlSource) {
        Copy-Item -LiteralPath $htmlSource -Destination (Join-Path $junitResultDir "$module-html") -Recurse -Force
    }
    $xmlSource = Join-Path $moduleDir 'build\test-results\test'
    if (Test-Path -LiteralPath $xmlSource) {
        Copy-Item -LiteralPath $xmlSource -Destination (Join-Path $junitResultDir "$module-xml") -Recurse -Force
    }
}

$totals = [ordered]@{ tests = 0; failures = 0; errors = 0; skipped = 0; timeSeconds = 0.0 }
Get-ChildItem -LiteralPath $junitResultDir -Recurse -Filter 'TEST-*.xml' | ForEach-Object {
    [xml]$document = Get-Content -LiteralPath $_.FullName -Raw
    $suite = $document.testsuite
    $totals.tests += [int]$suite.tests
    $totals.failures += [int]$suite.failures
    $totals.errors += [int]$suite.errors
    $totals.skipped += [int]$suite.skipped
    $totals.timeSeconds += [double]$suite.time
}

$allProcessesPassed = @($moduleExitCodes.Values | Where-Object { $_ -ne 0 }).Count -eq 0
$passed = $allProcessesPassed -and $totals.failures -eq 0 -and $totals.errors -eq 0 -and $totals.tests -gt 0
$summary = [ordered]@{
    passed = $passed
    moduleExitCodes = $moduleExitCodes
    tests = $totals.tests
    failures = $totals.failures
    errors = $totals.errors
    skipped = $totals.skipped
    timeSeconds = [math]::Round($totals.timeSeconds, 3)
}
$summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $junitResultDir 'junit-summary.json') -Encoding utf8
$summary | ConvertTo-Json -Depth 4 | Tee-Object -FilePath (Join-Path $junitResultDir 'junit-summary.log')

if (-not $passed) {
    exit 1
}
