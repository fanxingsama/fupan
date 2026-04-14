$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$pythonExe = Join-Path $backendDir ".venv\Scripts\python.exe"
$collector = Join-Path $backendDir "scripts\collect_akshare.py"
$dataDir = Join-Path $backendDir "data"

if (-not (Test-Path $pythonExe)) {
    throw "Python virtual environment not found. Run scripts\setup_local_env.ps1 first."
}

New-Item -ItemType Directory -Force $dataDir | Out-Null
$tradeDate = Get-Date -Format "yyyy-MM-dd"
$targetFile = Join-Path $dataDir "$tradeDate.json"

& $pythonExe $collector --date $tradeDate --sleep 1.2 | Out-File -LiteralPath $targetFile -Encoding utf8
Write-Host "Capture complete: $targetFile"
