$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$envFile = Join-Path $backendDir ".env"
$venvDir = Join-Path $backendDir ".venv"
$pythonExe = Join-Path $venvDir "Scripts\python.exe"
$litellmExe = Join-Path $venvDir "Scripts\litellm.exe"
$configFile = Join-Path $backendDir "litellm\config.yaml"

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $parts = $_.Split('=', 2)
        Set-Item -Path ("Env:" + $parts[0].Trim()) -Value $parts[1].Trim()
    }
}

if (-not (Test-Path $configFile)) {
    throw "LiteLLM config not found: $configFile"
}

$healthUrl = ($env:AI_BASE_URL -replace '/v1/?$', '') + "/v1/models"
if ($healthUrl -eq "/v1/models") {
    $healthUrl = "http://127.0.0.1:4000/v1/models"
}

try {
    $headers = @{}
    if ($env:AI_API_KEY) {
        $headers["Authorization"] = "Bearer $($env:AI_API_KEY)"
    }
    Invoke-WebRequest -Uri $healthUrl -Headers $headers -UseBasicParsing -TimeoutSec 2 | Out-Null
    Write-Host "LiteLLM is already running: $healthUrl"
    exit 0
} catch {
}

if (-not (Test-Path $litellmExe)) {
    if (-not (Test-Path $pythonExe)) {
        throw "Python virtual environment not found. Please run scripts\\setup_local_env.ps1 first."
    }
    & $pythonExe -m pip install "litellm[proxy]"
}

if (-not (Test-Path $litellmExe)) {
    throw "LiteLLM executable not found after install: $litellmExe"
}

Push-Location $backendDir
try {
    & $litellmExe --config $configFile
} finally {
    Pop-Location
}
