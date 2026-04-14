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

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $pythonExe
$null = $psi.ArgumentList.Add($collector)
$null = $psi.ArgumentList.Add("--date")
$null = $psi.ArgumentList.Add($tradeDate)
$null = $psi.ArgumentList.Add("--sleep")
$null = $psi.ArgumentList.Add("1.2")
$psi.WorkingDirectory = $backendDir
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
$psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
$psi.Environment["PYTHONIOENCODING"] = "utf-8"
$psi.Environment["PYTHONUTF8"] = "1"

$process = [System.Diagnostics.Process]::Start($psi)
$stdout = $process.StandardOutput.ReadToEnd()
$stderr = $process.StandardError.ReadToEnd()
$process.WaitForExit()

if ($process.ExitCode -ne 0) {
    throw "Collector failed: $stderr"
}

[System.IO.File]::WriteAllText($targetFile, $stdout, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Capture complete: $targetFile"
