$ErrorActionPreference = 'SilentlyContinue'
$root = $PSScriptRoot
$java = "$env:JAVA_HOME\bin\java.exe"
if (-not (Test-Path $java)) { $java = 'D:\Dev\environments\JAVA\bin\java.exe' }
$up = Test-NetConnection localhost -Port 8081 -InformationLevel Quiet -WarningAction SilentlyContinue
if (-not $up) {
    Start-Process -FilePath $java -ArgumentList @('-Dfile.encoding=UTF-8', '-cp', "$root\server\lib\*", 'com.cavedream.server.CaveDreamServerApplication') -WindowStyle Hidden -RedirectStandardOutput "$root\server-run.log" -RedirectStandardError "$root\server-err.log"
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 1
        if (Test-NetConnection localhost -Port 8081 -InformationLevel Quiet -WarningAction SilentlyContinue) { break }
    }
}
Start-Process -FilePath "$root\runtime\bin\javaw.exe" -ArgumentList @('-Xmx1g', '-Dfile.encoding=UTF-8', '-cp', "$root\app\cavedream-1.0-SNAPSHOT.jar", 'com.cavedream.desktop.Main') -WorkingDirectory $root
