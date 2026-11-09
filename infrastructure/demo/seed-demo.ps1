<# Local-only seed. Requires healthy Compose databases and a running backend. #>
[CmdletBinding()]
param([string]$BaseUrl = 'http://localhost:8080/api/v1')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$apiUri = [uri]$BaseUrl
if ($apiUri.Host -notin @('localhost', '127.0.0.1', '[::1]') -or $apiUri.Scheme -ne 'http') {
    throw 'Demo credentials are local-only. Use an http://localhost API URL.'
}
Push-Location $repoRoot
try {
    $health = Invoke-RestMethod "$BaseUrl/public/health" -TimeoutSec 10
    if ($health.status -ne 'UP') { throw 'Start the backend and complete Flyway migrations first.' }
    # Use the Compose database name and container environment without printing credentials.
    $dbConfig = docker compose config --format json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read Compose configuration.' }
    $databaseName = $dbConfig.services.'sqlserver-init'.environment.MSSQL_DATABASE
    if ($databaseName -notmatch '^[A-Za-z0-9_]+$') { throw 'Invalid local database name.' }
    docker compose cp infrastructure/demo/bootstrap-admin.sql sqlserver:/tmp/nexora-demo-admin.sql
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy demo bootstrap to SQL Server.' }
    $sqlCommand = '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -b -d ' + $databaseName + ' -f 65001 -i /tmp/nexora-demo-admin.sql'
    docker compose exec -T sqlserver bash -c $sqlCommand
    if ($LASTEXITCODE -ne 0) { throw 'Demo Admin bootstrap failed.' }
    node infrastructure/demo/seed-demo.mjs --base-url $BaseUrl
    if ($LASTEXITCODE -ne 0) { throw 'Demo seed incomplete. Fix the reported error and rerun to resume.' }
} finally {
    Pop-Location
}
