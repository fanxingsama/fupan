$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$toolsDir = Join-Path $projectRoot "tools"
$envFile = Join-Path $backendDir ".env"
$mavenCmd = $null
$jdkHome = $null

if (Test-Path "C:\Program Files\Eclipse Adoptium") {
    $jdkHome = Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Sort-Object Name -Descending | Select-Object -First 1
}

if (Get-Command "mvn.cmd" -ErrorAction SilentlyContinue) {
    $mavenCmd = (Get-Command "mvn.cmd").Source
} elseif (Get-Command "mvn" -ErrorAction SilentlyContinue) {
    $mavenCmd = (Get-Command "mvn").Source
} elseif (Test-Path $toolsDir) {
    $mavenHome = Get-ChildItem -Path $toolsDir -Directory -Filter "apache-maven-*" | Select-Object -First 1
    if ($mavenHome) {
        $mavenCmd = Join-Path $mavenHome.FullName "bin\mvn.cmd"
        $env:Path = "$($mavenHome.FullName)\bin;$env:Path"
    }
}

if (-not $mavenCmd) {
    throw "Maven not found. Install Maven or run scripts\setup_local_env.ps1 first."
}

if ($jdkHome) {
    $env:JAVA_HOME = $jdkHome.FullName
    $env:Path = "$($jdkHome.FullName)\bin;$env:Path"
}

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $parts = $_.Split('=', 2)
        [Environment]::SetEnvironmentVariable($parts[0], $parts[1], "Process")
    }
}

try {
    $health = Invoke-WebRequest -Uri "http://localhost:8080/api/recaps" -UseBasicParsing -TimeoutSec 2
    if ($health.StatusCode -eq 200) {
        Write-Host "Backend is already running at http://localhost:8080/api/recaps"
        exit 0
    }
} catch {
}

Push-Location $backendDir
try {
    & $mavenCmd spring-boot:run
} finally {
    Pop-Location
}
