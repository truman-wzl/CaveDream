# Re-test the packaged exe launch (ASCII only); list matching processes by id/name
$ErrorActionPreference = 'Continue'
$appDir = Get-ChildItem 'D:\Dev\projects\CaveDream\build\dist' -Directory | Select-Object -First 1
$exe = Get-ChildItem $appDir.FullName -Filter *.exe | Select-Object -First 1
Write-Host ('exe: ' + $exe.FullName)
Start-Process -FilePath $exe.FullName
Start-Sleep -Seconds 12
$all = Get-Process | Where-Object { $_.Path -like ($appDir.FullName + '*') }
if ($all) {
    $all | ForEach-Object { Write-Host ('ALIVE: pid=' + $_.Id + ' name=[' + $_.ProcessName + '] title=[' + $_.MainWindowTitle + ']') }
    $all | Stop-Process -Force
    Write-Host 'closed'
} else {
    Write-Host 'NOT FOUND - exe really failed'
}
