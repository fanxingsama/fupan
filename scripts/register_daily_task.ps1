$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$taskName = "DailyRecap-AutoCapture-1510"
$captureScript = Join-Path $projectRoot "scripts\capture_once.ps1"
$escapedCaptureScript = $captureScript.Replace('"', '""')
$command = "powershell.exe -ExecutionPolicy Bypass -File ""$escapedCaptureScript"""

schtasks /Create /TN $taskName /SC WEEKLY /D MON,TUE,WED,THU,FRI /ST 15:10 /TR $command /F
Write-Host "Scheduled task created: $taskName"
