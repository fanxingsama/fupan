$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendScript = Join-Path $projectRoot "scripts\start_backend.ps1"
$frontendScript = Join-Path $projectRoot "scripts\start_frontend.ps1"
$frontendUrl = "http://localhost:5173"
$backendUrl = "http://localhost:8080/api/recaps"

Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $backendScript
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $frontendScript

$frontendReady = $false
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-WebRequest -Uri $frontendUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
        $frontendReady = $true
        break
    } catch {
        Start-Sleep -Seconds 2
    }
}

if (-not $frontendReady) {
    Write-Host "Frontend did not become reachable in time. Opening the URL anyway."
}

Start-Process $frontendUrl

try {
    Invoke-WebRequest -Uri $backendUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
    Write-Host "Backend is reachable: $backendUrl"
} catch {
    Write-Host "Backend is still starting. The page may show API errors briefly."
}
