$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendScript = Join-Path $projectRoot "scripts\start_backend.ps1"
$frontendScript = Join-Path $projectRoot "scripts\start_frontend.ps1"
$backendUrl = "http://localhost:8080/api/recaps"
$frontendUrl = $null

Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $backendScript
Start-Sleep -Seconds 2
Start-Process powershell -ArgumentList "-NoExit", "-ExecutionPolicy", "Bypass", "-File", $frontendScript

$frontendReady = $false
for ($i = 0; $i -lt 60 -and -not $frontendReady; $i++) {
    foreach ($port in 5173..5180) {
        $candidateUrl = "http://localhost:$port"
        try {
            Invoke-WebRequest -Uri $candidateUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
            $frontendReady = $true
            $frontendUrl = $candidateUrl
            break
        } catch {
        }
    }
    if (-not $frontendReady) {
        Start-Sleep -Seconds 2
    }
}

if (-not $frontendReady) {
    Write-Host "Frontend did not become reachable in time. Opening the URL anyway."
    $frontendUrl = "http://localhost:5173"
}

Start-Process $frontendUrl

try {
    Invoke-WebRequest -Uri $backendUrl -UseBasicParsing -TimeoutSec 2 | Out-Null
    Write-Host "Backend is reachable: $backendUrl"
} catch {
    Write-Host "Backend is still starting. The page may show API errors briefly."
}
