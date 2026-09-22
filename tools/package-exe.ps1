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

Write-Host '==> 4/4 verify runtime bundled + launch test'
$javaOk = Test-Path (Join-Path $appPath 'runtime\bin\java.exe')
Write-Host ('runtime\bin\java.exe exists: ' + $javaOk)
if (-not $javaOk) { throw 'runtime missing in app-image' }

$exePath = Join-Path $appPath ($appName + '.exe')
$proc = Start-Process -FilePath $exePath -PassThru
Start-Sleep -Seconds 15
$children = Get-Process | Where-Object { $_.ProcessName -notmatch '^csrss|^svchost' -and $_.Path -and $_.Path.StartsWith($appPath) } | Measure-Object
if (-not $proc.HasExited -or $children.Count -gt 0) {
    Write-Host ('LAUNCH OK (launcher pid=' + $proc.Id + ', app processes=' + $children.Count + ')')
    Get-Process | Where-Object { $_.Path -and $_.Path.StartsWith($appPath) } | Stop-Process -Force -ErrorAction SilentlyContinue
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue }
} elseif ($proc.ExitCode -eq 0) {
    Write-Host 'launcher exited 0 (hand-off) - treat as OK'  # jpackage launcher may detach
} else {
    Write-Host ('exe exited early with code ' + $proc.ExitCode)
    throw 'exe failed to launch'
}
Write-Host '==> DONE'
Get-Item $exePath | Select-Object FullName, Length
