$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$frontendDir = Join-Path $projectRoot "frontend"
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

Push-Location $frontendDir
try {
    & $npmCmd run dev
} finally {
    Pop-Location
}
