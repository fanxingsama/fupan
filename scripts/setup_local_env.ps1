$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $projectRoot "backend"
$frontendDir = Join-Path $projectRoot "frontend"
$toolsDir = Join-Path $projectRoot "tools"
$mavenVersion = "3.9.11"
$mavenZip = Join-Path $toolsDir "apache-maven-$mavenVersion-bin.zip"
$mavenHome = Join-Path $toolsDir "apache-maven-$mavenVersion"
$pythonExe = "python"
$npmCmd = "npm.cmd"
$userNodeRoot = Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA "Microsoft\WinGet\Packages\OpenJS.NodeJS.LTS_Microsoft.Winget.Source_8wekyb3d8bbwe") -Directory -Filter "node-v*-win-x64" -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1

if ($userNodeRoot -and (Test-Path (Join-Path $userNodeRoot.FullName "npm.cmd"))) {
    $npmCmd = Join-Path $userNodeRoot.FullName "npm.cmd"
    $env:Path = "$($userNodeRoot.FullName);$env:Path"
} elseif (-not (Get-Command $npmCmd -ErrorAction SilentlyContinue)) {
    $defaultNpm = "C:\Program Files\nodejs\npm.cmd"
    if (Test-Path $defaultNpm) {
        $npmCmd = $defaultNpm
        $env:Path = "C:\Program Files\nodejs;$env:Path"
    } else {
        throw "npm.cmd not found. Please install Node.js LTS first."
    }
} else {
    $nodeDir = Split-Path -Parent (Get-Command $npmCmd).Source
    $env:Path = "$nodeDir;$env:Path"
}

New-Item -ItemType Directory -Force $toolsDir | Out-Null

if (-not (Test-Path $mavenHome)) {
    $url = "https://archive.apache.org/dist/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $mavenZip
    Expand-Archive -LiteralPath $mavenZip -DestinationPath $toolsDir -Force
}

Push-Location $backendDir
try {
    if (-not (Test-Path ".venv\Scripts\python.exe")) {
        & $pythonExe -m venv .venv
    }
    & ".\.venv\Scripts\python.exe" -m pip install --upgrade pip
    & ".\.venv\Scripts\python.exe" -m pip install akshare tushare
} finally {
    Pop-Location
}

Push-Location $frontendDir
try {
    & $npmCmd install
} finally {
    Pop-Location
}

Write-Host "Local environment setup finished."
