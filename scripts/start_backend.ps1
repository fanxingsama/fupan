$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$toolsDir = Join-Path $projectRoot "tools"
$mavenHome = Get-ChildItem -Path $toolsDir -Directory -Filter "apache-maven-*" | Select-Object -First 1
$jdkHome = Get-ChildItem -Path "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" | Sort-Object Name -Descending | Select-Object -First 1
$envFile = Join-Path $backendDir ".env"

if (-not $mavenHome) {
    throw "Maven not found. Run scripts\setup_local_env.ps1 first."
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

$env:Path = "$($mavenHome.FullName)\bin;$env:Path"

Push-Location $backendDir
try {
    & "$($mavenHome.FullName)\bin\mvn.cmd" spring-boot:run
} finally {
    Pop-Location
}
