$ErrorActionPreference = 'SilentlyContinue'
# Kill any running CaveDream game JVM (by cmdline) and any process under build\dist (jar lock release)
Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match 'javaw?\.exe' -and $_.CommandLine -match 'cavedream'
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Get-Process | Where-Object {
    try { $_.Path -and $_.Path.StartsWith('D:\Dev\projects\CaveDream\build\dist') } catch { $false }
} | ForEach-Object { Stop-Process -Id $_.Id -Force }
Start-Sleep -Seconds 3
Write-Output 'kill-game done'
