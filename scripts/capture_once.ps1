$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$envFile = Join-Path $backendDir ".env"
$pythonExe = Join-Path $backendDir ".venv\Scripts\python.exe"
$collector = Join-Path $backendDir "scripts\collect_wencai.py"
$dataDir = Join-Path $backendDir "data"

if (-not (Test-Path $pythonExe)) {
    throw "Python virtual environment not found. Run scripts\setup_local_env.ps1 first."
}

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $parts = $_.Split('=', 2)
        [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
    }
}

if (-not $env:WENCAI_COOKIE) {
    throw "WENCAI_COOKIE is empty. Fill backend\.env first."
}

New-Item -ItemType Directory -Force $dataDir | Out-Null
$tradeDate = Get-Date -Format "yyyy-MM-dd"
$targetFile = Join-Path $dataDir "$tradeDate.json"

& $pythonExe $collector --date $tradeDate --cookie $env:WENCAI_COOKIE --sleep 1.2 | Out-File -LiteralPath $targetFile -Encoding utf8
Write-Host "Capture complete: $targetFile"
