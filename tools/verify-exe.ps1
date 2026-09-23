$ErrorActionPreference = 'Continue'
$app = Get-ChildItem 'D:\Dev\projects\CaveDream\build\dist' -Directory | Select-Object -First 1
$exe = Get-ChildItem $app.FullName -Filter *.exe | Select-Object -First 1
Write-Output ('EXE: ' + $exe.FullName)
Start-Process -FilePath $exe.FullName
Start-Sleep -Seconds 12
$games = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'javaw?\.exe' -and $_.CommandLine -match 'cavedream.desktop.Main' }
if ($games) {
    Write-Output ('REAL GAME JVM RUNNING, count=' + ($games | Measure-Object).Count)
    $games | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
} else {
    Write-Output 'NO GAME JVM -> launcher failed (Failed to launch JVM)'
}
Get-Process | Where-Object { try { $_.Path -and $_.Path.StartsWith($app.FullName) } catch { $false } } | Stop-Process -Force -ErrorAction SilentlyContinue
