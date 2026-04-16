$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$toolsDir = Join-Path $projectRoot "tools"
$setupScript = Join-Path $PSScriptRoot "setup_local_env.ps1"
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
    if (-not (Test-Path $setupScript)) {
        throw "Maven not found, and setup_local_env.ps1 is missing."
    }

    Write-Host "Maven not found. Running scripts\setup_local_env.ps1 to bootstrap local tools..."
    powershell -ExecutionPolicy Bypass -File $setupScript

    if (Test-Path $toolsDir) {
        $mavenHome = Get-ChildItem -Path $toolsDir -Directory -Filter "apache-maven-*" | Select-Object -First 1
        if ($mavenHome) {
            $mavenCmd = Join-Path $mavenHome.FullName "bin\mvn.cmd"
            $env:Path = "$($mavenHome.FullName)\bin;$env:Path"
        }
    }
}

if (-not $mavenCmd) {
    throw "Maven bootstrap failed. Please run scripts\setup_local_env.ps1 manually and check for errors."
}

if ($jdkHome) {
    $env:JAVA_HOME = $jdkHome.FullName
    $env:Path = "$($jdkHome.FullName)\bin;$env:Path"
} elseif (Get-Command "java" -ErrorAction SilentlyContinue) {
    $javaCmd = (Get-Command "java").Source
    $javaBinDir = Split-Path -Parent $javaCmd
    $resolvedJavaBinDir = try { (Resolve-Path $javaBinDir).Path } catch { $javaBinDir }
    $possibleJavaHome = Split-Path -Parent $resolvedJavaBinDir
    if (Test-Path (Join-Path $possibleJavaHome "bin\java.exe")) {
        $env:JAVA_HOME = $possibleJavaHome
        $env:Path = "$($possibleJavaHome)\bin;$env:Path"
    }
}

if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
        $parts = $_.Split('=', 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim()
        Set-Item -Path "Env:$name" -Value $value
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
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
