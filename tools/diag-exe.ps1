# ASCII-only diagnostic for the packaged app-image JVM launch
$ErrorActionPreference = 'Continue'
$appDir = Get-ChildItem 'D:\Dev\projects\CaveDream\build\dist' -Directory | Select-Object -First 1
Write-Host ('app dir: ' + $appDir.FullName)

Write-Host '--- app.cfg ---'
Get-Content (Join-Path $appDir.FullName 'app\app.cfg')

Write-Host '--- app dir contents ---'
Get-ChildItem (Join-Path $appDir.FullName 'app') | Select-Object Name, Length

Write-Host '--- bundled java version ---'
& (Join-Path $appDir.FullName 'runtime\bin\java.exe') -version 2>&1 | ForEach-Object { Write-Host $_ }

Write-Host '--- run main directly, capture error ---'
$jar = Get-ChildItem (Join-Path $appDir.FullName 'app') -Filter *.jar | Select-Object -First 1
$psi = Start-Process -FilePath (Join-Path $appDir.FullName 'runtime\bin\java.exe') `
    -ArgumentList @('-cp', $jar.FullName, 'com.cavedream.desktop.Main') `
    -RedirectStandardError 'D:\Dev\projects\CaveDream\build\diag-err.txt' `
    -RedirectStandardOutput 'D:\Dev\projects\CaveDream\build\diag-out.txt' `
    -PassThru -NoNewWindow
Start-Sleep -Seconds 12
if (-not $psi.HasExited) { Write-Host 'STILL RUNNING (ok)'; Stop-Process -Id $psi.Id -Force } else { Write-Host ('EXITED code=' + $psi.ExitCode) }
Write-Host '--- stderr ---'
Get-Content 'D:\Dev\projects\CaveDream\build\diag-err.txt' | Select-Object -First 30
Write-Host '--- stdout ---'
Get-Content 'D:\Dev\projects\CaveDream\build\diag-out.txt' | Select-Object -First 10
