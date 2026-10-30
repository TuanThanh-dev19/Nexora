<#
.SYNOPSIS
    Runs the Ticket API regression test suite using Newman.

.DESCRIPTION
    Automates execution of Postman regression test collection against
    a live CampusFix / Nexora backend environment.

.PARAMETER EnvironmentFile
    Path to the Postman environment file. Defaults to local.postman_environment.json.

.PARAMETER CollectionFile
    Path to the Postman collection file. Defaults to ticket-regression.postman_collection.json.

.PARAMETER BaseUrl
    Base API URL to probe for health before running tests.

.PARAMETER SetupData
    Switch to re-run database test data preparation before executing tests.

.PARAMETER ReportsDir
    Directory where HTML test reports will be saved.

.PARAMETER NoHtmlReport
    Switch to disable HTML report generation and use CLI output only.

.EXAMPLE
    .\run-ticket-regression.ps1
    .\run-ticket-regression.ps1 -SetupData
    .\run-ticket-regression.ps1 -NoHtmlReport
#>
[CmdletBinding()]
param(
    [string]$EnvironmentFile,
    [string]$CollectionFile,
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [switch]$SetupData,
    [string]$ReportsDir,
    [switch]$NoHtmlReport
)

$ErrorActionPreference = "Stop"

if (-not $EnvironmentFile) { $EnvironmentFile = Join-Path $PSScriptRoot "local.postman_environment.json" }
if (-not $CollectionFile) { $CollectionFile = Join-Path $PSScriptRoot "ticket-regression.postman_collection.json" }
if (-not $ReportsDir) { $ReportsDir = Join-Path $PSScriptRoot "reports" }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   CampusFix / Nexora Ticket API Regression Test Runner   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Check Node.js and Newman prerequisites
Write-Host "[1/5] Checking prerequisites..." -ForegroundColor Yellow

$nodeCmd = Get-Command "node" -ErrorAction SilentlyContinue
if (-not $nodeCmd) {
    Write-Error "Node.js is not found on PATH. Please install Node.js."
    exit 1
}
$nodeVersion = & node -v
Write-Host "  * Node.js version: $nodeVersion" -ForegroundColor Gray

$newmanCmd = Get-Command "newman" -ErrorAction SilentlyContinue
if (-not $newmanCmd) {
    Write-Error "Newman is not found on PATH. Run 'npm install -g newman newman-reporter-htmlextra' to install."
    exit 1
}
$newmanVersion = & newman -v
Write-Host "  * Newman version: $newmanVersion" -ForegroundColor Gray

# 2. Check Backend Health
Write-Host "[2/5] Checking backend health at $BaseUrl/public/health..." -ForegroundColor Yellow
$healthUrl = "$BaseUrl/public/health"
try {
    $healthResponse = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 5 -ErrorAction Stop
    if ($healthResponse.status -eq "UP") {
        Write-Host "  * Backend is healthy (status: UP)" -ForegroundColor Green
    } else {
        Write-Host "  * Backend returned status: $($healthResponse.status)" -ForegroundColor Yellow
    }
} catch {
    Write-Error "Backend is not reachable at $healthUrl. Please ensure the backend is running before executing tests. Details: $_"
    exit 1
}

# 3. Environment and Test Data Setup
Write-Host "[3/5] Checking test environment and fixtures..." -ForegroundColor Yellow

if (-not (Test-Path $EnvironmentFile) -or $SetupData) {
    Write-Host "  * Running test data setup script..." -ForegroundColor Yellow
    $setupScript = "$PSScriptRoot/setup-test-data.ps1"
    if (Test-Path $setupScript) {
        & $setupScript
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Test data setup failed with exit code $LASTEXITCODE."
            exit $LASTEXITCODE
        }
    } else {
        Write-Error "Environment file '$EnvironmentFile' not found and setup script '$setupScript' does not exist."
        exit 1
    }
}

if (-not (Test-Path $CollectionFile)) {
    Write-Error "Postman collection file '$CollectionFile' not found."
    exit 1
}

# 4. Prepare Reports Directory
Write-Host "[4/5] Preparing test report output..." -ForegroundColor Yellow
if (-not (Test-Path $ReportsDir)) {
    New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
}
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportFile = "$ReportsDir/ticket-regression-report-$timestamp.html"

# 5. Execute Newman
Write-Host "[5/5] Running Postman regression test collection..." -ForegroundColor Yellow
$workingDir = Split-Path $CollectionFile -Parent

$reporters = if ($NoHtmlReport) { "cli" } else { "cli,htmlextra" }
$newmanArgs = @(
    "run", $CollectionFile,
    "-e", $EnvironmentFile,
    "--working-dir", $workingDir,
    "--reporters", $reporters
)

if (-not $NoHtmlReport) {
    $newmanArgs += @(
        "--reporter-htmlextra-export", $reportFile,
        "--reporter-htmlextra-title", "Ticket API Regression Test Report",
        "--reporter-htmlextra-browserTitle", "CampusFix Ticket Regression"
    )
}

Write-Host "Executing: newman $($newmanArgs -join ' ')" -ForegroundColor Gray
Write-Host "Working directory: $workingDir" -ForegroundColor Gray
Write-Host "----------------------------------------------------------" -ForegroundColor Gray

& newman @newmanArgs
$newmanExitCode = $LASTEXITCODE

Write-Host "----------------------------------------------------------" -ForegroundColor Gray
if ($newmanExitCode -eq 0) {
    Write-Host "SUCCESS: All regression test assertions passed!" -ForegroundColor Green
    if (-not $NoHtmlReport -and (Test-Path $reportFile)) {
        Write-Host "HTML report saved to: $reportFile" -ForegroundColor Cyan
    }
} else {
    Write-Host "FAILURE: Regression test suite failed with exit code $newmanExitCode." -ForegroundColor Red
    if (-not $NoHtmlReport -and (Test-Path $reportFile)) {
        Write-Host "HTML failure report saved to: $reportFile" -ForegroundColor Red
    }
}

exit $newmanExitCode
