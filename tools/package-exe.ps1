# CaveDream packaging: fat jar -> jlink runtime image -> jpackage app-image (bundled JRE, no install)
# App name built from unicode code points to keep this file pure-ASCII (PS 5.1 encoding safe).
# Usage: powershell -ExecutionPolicy Bypass -File tools\package-exe.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$appName = [string]::Join('', [char]0x68A6, [char]0x8FF9, [char]0x884C, [char]0x8005)  # Meng-Ji-Xing-Zhe
$jdk = $env:JAVA_HOME

Write-Host '==> 1/4 gradle fat jar'
& "$root\gradlew.bat" :desktop:fatJar --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'fatJar build failed' }
& "$root\gradlew.bat" :server:installDist --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'server installDist build failed' }

$jarDir = "$root\build\exe-staging"
$distDir = "$root\build\dist"
$runtimeDir = "$root\build\runtime-image"
New-Item -ItemType Directory -Force -Path $jarDir | Out-Null
Copy-Item "$root\desktop\build\libs\cavedream-1.0-SNAPSHOT.jar" $jarDir -Force

Write-Host '==> 2/4 jlink runtime image'
if (Test-Path $runtimeDir) { Remove-Item $runtimeDir -Recurse -Force }
& "$jdk\bin\jlink.exe" --no-header-files --no-man-pages --strip-debug `
    --add-modules java.base,java.desktop,java.logging,java.management,java.net.http,java.prefs,java.sql,java.xml,java.naming,jdk.unsupported,jdk.crypto.ec `
    --output $runtimeDir
if ($LASTEXITCODE -ne 0) { throw 'jlink failed' }

Write-Host '==> 3/4 jpackage app-image'
$appPath = Join-Path $distDir $appName
if (Test-Path $appPath) { Remove-Item $appPath -Recurse -Force }
# No --icon: jpackage's ResourceEditor step (writing icon/version into the exe) can fail with
# system error 110 under AV/file-lock, corrupting the launcher (Failed to launch JVM). Re-add icon later when clean.
& "$jdk\bin\jpackage.exe" `
    --type app-image `
    --name $appName `
    --input $jarDir `
    --main-jar 'cavedream-1.0-SNAPSHOT.jar' `
    --main-class 'com.cavedream.desktop.Main' `
    --runtime-image $runtimeDir `
    --dest $distDir `
    --java-options '-Xmx1g' `
    --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }

Write-Host '==> 3.5 bundle server + auto-start launcher'
$serverLibSrc = Join-Path $root 'server\build\install\server\lib'
$serverLibDst = Join-Path $appPath 'server\lib'
if (Test-Path $serverLibDst) { Remove-Item $serverLibDst -Recurse -Force }
New-Item -ItemType Directory -Force -Path $serverLibDst | Out-Null
Copy-Item (Join-Path $serverLibSrc '*') $serverLibDst -Recurse -Force
$allLauncher = Join-Path $appPath 'start-all.ps1'
Copy-Item (Join-Path $root 'tools\start-all-template.ps1') $allLauncher -Force
Write-Host ('wrote auto-start launcher: ' + $allLauncher)

Write-Host '==> 4/4 emit bat launcher + verify via bundled JRE'
$javaOk = Test-Path (Join-Path $appPath 'runtime\bin\java.exe')
Write-Host ('runtime\bin\java.exe exists: ' + $javaOk)
if (-not $javaOk) { throw 'runtime missing in app-image' }

# Reliable launcher: bundled javaw (the jpackage .exe may be blocked by AV on some machines)
$bat = Join-Path $appPath 'start-game.bat'
$batLines = @(
    '@echo off',
    'rem CaveDream launcher - bundled JRE, avoids jpackage exe being blocked by AV',
    'cd /d "%~dp0"',
    'start "" "runtime\bin\javaw.exe" -Xmx1g -Dfile.encoding=UTF-8 -cp "app\cavedream-1.0-SNAPSHOT.jar" com.cavedream.desktop.Main'
)
[System.IO.File]::WriteAllLines($bat, $batLines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ('wrote launcher: ' + $bat)

$exePath = Join-Path $appPath ($appName + '.exe')
Start-Process -FilePath $bat -WorkingDirectory $appPath | Out-Null
Start-Sleep -Seconds 14
$games = Get-CimInstance Win32_Process | Where-Object { $_.Name -match 'javaw?\.exe' -and $_.CommandLine -match 'cavedream.desktop.Main' }
if ($games) {
    Write-Host ('LAUNCH OK via start-game.bat - game JVM running, count=' + ($games | Measure-Object).Count)
} else {
    Write-Host 'FAILED - no game JVM even via bat'
}
$games | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Get-Process | Where-Object { try { $_.Path -and $_.Path.StartsWith($appPath) } catch { $false } } | Stop-Process -Force -ErrorAction SilentlyContinue
if (-not $games) { throw 'launcher failed' }
Write-Host '==> DONE'
Write-Host ('double-click to play: ' + $bat)
Get-Item $exePath | Select-Object FullName, Length
