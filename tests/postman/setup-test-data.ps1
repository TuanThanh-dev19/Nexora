<#
.SYNOPSIS
    Idempotent test data setup for Ticket API Regression Testing.
.DESCRIPTION
    Ensures required test accounts (Requester, Other Requester, Manager, Technician, Ineligible Technician)
    exist in the system with correct roles and technician profiles.
    Generates tests/postman/local.postman_environment.json with active credentials.
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$AdminEmail = "admin@example.test",
    [string]$AdminPassword = "AdminPassword123!",
    [string]$RequesterEmail = "requester@example.test",
    [string]$RequesterPassword = "RequesterPass123!",
    [string]$OtherRequesterEmail = "other_requester@example.test",
    [string]$OtherRequesterPassword = "OtherPass123!",
    [string]$ManagerEmail = "manager@example.test",
    [string]$ManagerPassword = "ManagerPass123!",
    [string]$TechnicianEmail = "technician@example.test",
    [string]$TechnicianPassword = "TechPass123!",
    [string]$IneligibleTechEmail = "ineligible_tech@example.test",
    [string]$IneligibleTechPassword = "IneligPass123!",
    [string]$OutputFile = ""
)

if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    if ([string]::IsNullOrWhiteSpace($scriptDir)) {
        $scriptDir = Join-Path (Get-Location) "tests/postman"
    }
    $OutputFile = Join-Path $scriptDir "local.postman_environment.json"
}

$ErrorActionPreference = "Stop"

Write-Host "==> Checking backend health at $BaseUrl/public/health..." -ForegroundColor Cyan
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/public/health" -Method Get -TimeoutSec 10
    if ($health.status -ne "UP") {
        throw "Backend is not UP (status: $($health.status))"
    }
    Write-Host "    Backend is healthy: $($health.service) ($($health.status))" -ForegroundColor Green
} catch {
    Write-Error "Cannot connect to backend at $BaseUrl. Please ensure the backend is running.`nError: $_"
    exit 1
}

Write-Host "==> Authenticating as Admin ($AdminEmail)..." -ForegroundColor Cyan
$adminLoginBody = @{ email = $AdminEmail; password = $AdminPassword } | ConvertTo-Json
$adminAuth = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json"
$adminToken = $adminAuth.accessToken
$adminHeaders = @{ Authorization = "Bearer $adminToken" }

# Helper to find or create user
function Ensure-UserAccount {
    param($email, $password, $fullName, $roles)

    # List all users (up to 100)
    $list = Invoke-RestMethod -Uri "$BaseUrl/admin/users?size=100" -Method Get -Headers $adminHeaders
    $user = $list.content | Where-Object { $_.email -eq $email }

    if (-not $user) {
        Write-Host "    Creating user $email ($($roles -join ', '))..."
        $createBody = @{
            email = $email
            password = $password
            fullName = $fullName
            status = "ACTIVE"
            roles = $roles
        } | ConvertTo-Json
        $user = Invoke-RestMethod -Uri "$BaseUrl/admin/users" -Method Post -Body $createBody -ContentType "application/json" -Headers $adminHeaders
    } else {
        Write-Host "    User $email already exists (id: $($user.id))."
        # Ensure status is ACTIVE
        if ($user.status -ne "ACTIVE") {
            $statusBody = @{ status = "ACTIVE" } | ConvertTo-Json
            $user = Invoke-RestMethod -Uri "$BaseUrl/admin/users/$($user.id)/status" -Method Patch -Body $statusBody -ContentType "application/json" -Headers $adminHeaders
        }
    }
    return $user
}

Write-Host "==> Ensuring test users..." -ForegroundColor Cyan
$requester = Ensure-UserAccount -email $RequesterEmail -password $RequesterPassword -fullName "Test Requester" -roles @("REQUESTER")
$otherRequester = Ensure-UserAccount -email $OtherRequesterEmail -password $OtherRequesterPassword -fullName "Other Requester" -roles @("REQUESTER")
$manager = Ensure-UserAccount -email $ManagerEmail -password $ManagerPassword -fullName "Test Manager" -roles @("MANAGER")
$technician = Ensure-UserAccount -email $TechnicianEmail -password $TechnicianPassword -fullName "Test Technician" -roles @("TECHNICIAN")
$ineligibleTech = Ensure-UserAccount -email $IneligibleTechEmail -password $IneligibleTechPassword -fullName "Ineligible Technician" -roles @("TECHNICIAN")

Write-Host "==> Configuring technician profiles..." -ForegroundColor Cyan

# 1. Eligible technician (SAFETY skill, CAMPUS-MAIN service area, available = true)
Invoke-RestMethod -Uri "$BaseUrl/technicians/$($technician.id)" -Method Put -Body (@{
    maxActiveTickets = 10
    available = $true
} | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null

Invoke-RestMethod -Uri "$BaseUrl/technicians/$($technician.id)/skills" -Method Put -Body (@{
    skills = @(
        @{ skillId = 4; proficiencyLevel = 3 }
    )
} | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null

Invoke-RestMethod -Uri "$BaseUrl/technicians/$($technician.id)/service-areas" -Method Put -Body (@{
    locationIds = @(1)
} | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Write-Host "    Configured technician $($technician.email) (Available, Skill 4, Location 1)" -ForegroundColor Green

# 2. Ineligible technician (available = false)
Invoke-RestMethod -Uri "$BaseUrl/technicians/$($ineligibleTech.id)" -Method Put -Body (@{
    maxActiveTickets = 5
    available = $false
} | ConvertTo-Json) -ContentType "application/json" -Headers $adminHeaders | Out-Null
Write-Host "    Configured ineligible technician $($ineligibleTech.email) (Blocked/Unavailable)" -ForegroundColor Green

# Generate local environment json
$envData = @{
    id = "nexora-local-active-environment"
    name = "Nexora Local Active Environment"
    values = @(
        @{ key = "baseUrl"; value = $BaseUrl; enabled = $true; type = "default" },
        @{ key = "adminEmail"; value = $AdminEmail; enabled = $true; type = "default" },
        @{ key = "adminPassword"; value = $AdminPassword; enabled = $true; type = "secret" },
        @{ key = "requesterEmail"; value = $RequesterEmail; enabled = $true; type = "default" },
        @{ key = "requesterPassword"; value = $RequesterPassword; enabled = $true; type = "secret" },
        @{ key = "otherRequesterEmail"; value = $OtherRequesterEmail; enabled = $true; type = "default" },
        @{ key = "otherRequesterPassword"; value = $OtherRequesterPassword; enabled = $true; type = "secret" },
        @{ key = "managerEmail"; value = $ManagerEmail; enabled = $true; type = "default" },
        @{ key = "managerPassword"; value = $ManagerPassword; enabled = $true; type = "secret" },
        @{ key = "technicianEmail"; value = $TechnicianEmail; enabled = $true; type = "default" },
        @{ key = "technicianPassword"; value = $TechnicianPassword; enabled = $true; type = "secret" },
        @{ key = "ineligibleTechnicianEmail"; value = $IneligibleTechEmail; enabled = $true; type = "default" },
        @{ key = "ineligibleTechnicianPassword"; value = $IneligibleTechPassword; enabled = $true; type = "secret" },
        @{ key = "categoryId"; value = "4"; enabled = $true; type = "default" },
        @{ key = "formVersionId"; value = "4"; enabled = $true; type = "default" },
        @{ key = "locationId"; value = "1"; enabled = $true; type = "default" },
        @{ key = "technicianId"; value = "$($technician.id)"; enabled = $true; type = "default" },
        @{ key = "ineligibleTechnicianId"; value = "$($ineligibleTech.id)"; enabled = $true; type = "default" }
    )
    _postman_variable_scope = "environment"
}

$envData | ConvertTo-Json -Depth 5 | Set-Content -Path $OutputFile -Encoding UTF8
Write-Host "==> Environment file generated at: $OutputFile" -ForegroundColor Green
