$ErrorActionPreference = 'Continue'
$app = Get-ChildItem 'D:\Dev\projects\CaveDream\build\dist' -Directory | Select-Object -First 1
Write-Output ('APPDIR: ' + $app.FullName)
Write-Output '--- top level ---'
Get-ChildItem $app.FullName | Select-Object Name, Length | Format-Table -AutoSize | Out-String | Write-Output
$appDir = Join-Path $app.FullName 'app'
Write-Output '--- app/ contents ---'
Get-ChildItem $appDir | Select-Object Name, Length | Format-Table -AutoSize | Out-String | Write-Output
Write-Output '--- *.cfg ---'
Get-ChildItem $appDir -Filter *.cfg | ForEach-Object { Write-Output ('### ' + $_.Name); Get-Content $_.FullName | ForEach-Object { Write-Output $_ } }
Write-Output '--- run jar with bundled runtime ---'
$jar = Get-ChildItem $appDir -Filter *.jar | Select-Object -First 1
$java = Join-Path $app.FullName 'runtime\bin\java.exe'
$psi = Start-Process -FilePath $java -ArgumentList @('-cp', $jar.FullName, 'com.cavedream.desktop.Main') -RedirectStandardError 'D:\Dev\projects\CaveDream\build\jar-err.txt' -RedirectStandardOutput 'D:\Dev\projects\CaveDream\build\jar-out.txt' -PassThru -NoNewWindow
Start-Sleep -Seconds 8
if (-not $psi.HasExited) { Write-Output 'STILL RUNNING (jar launches fine via runtime)'; Stop-Process -Id $psi.Id -Force } else { Write-Output ('EXITED code=' + $psi.ExitCode) }
Write-Output '--- stderr head ---'
Get-Content 'D:\Dev\projects\CaveDream\build\jar-err.txt' -TotalCount 25 | ForEach-Object { Write-Output $_ }
