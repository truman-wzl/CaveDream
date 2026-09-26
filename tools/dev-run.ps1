# Fast dev run: skip jpackage/shortcut, launch the desktop game directly via Gradle (seconds).
# Usage: powershell -ExecutionPolicy Bypass -File tools\dev-run.ps1
# For dev eyeball iteration only; final release still uses tools\package-exe.ps1.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# Kill any running debug instance (match cavedream.desktop.Main; do not touch packaged exe)
Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -like '*cavedream.desktop.Main*' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

Write-Host '>>> Running desktop directly (:desktop:run, no packaging)...' -ForegroundColor Green
& .\gradlew.bat :desktop:run --console=plain
