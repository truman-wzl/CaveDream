$ErrorActionPreference = 'Stop'
# Desktop shortcut that launches the game directly via the bundled javaw.exe (a real exe target,
# so Windows allows "Pin to taskbar"; no console flash). App dir resolved at runtime (pure-ASCII file).
$root = Split-Path -Parent $PSScriptRoot
$app = Get-ChildItem (Join-Path $root 'build\dist') -Directory | Select-Object -First 1
if (-not $app) { throw 'no app-image under build\dist; run package-exe.ps1 first' }
$exe = Get-ChildItem -LiteralPath $app.FullName -File | Where-Object { $_.Extension -eq '.exe' } | Select-Object -First 1
$javaw = Join-Path $app.FullName 'runtime\bin\javaw.exe'
$jar = Get-ChildItem (Join-Path $app.FullName 'app') -Filter '*.jar' | Select-Object -First 1
$ico = Join-Path $root 'desktop\pkg\cavedream.ico'

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop 'CaveDream.lnk'

$ws = New-Object -ComObject WScript.Shell
$sc = $ws.CreateShortcut($lnk)
$allPs = Join-Path $app.FullName 'start-all.ps1'
$psExe = Join-Path $env:Windir 'System32\WindowsPowerShell\v1.0\powershell.exe'
if ((Test-Path $allPs) -and (Test-Path $psExe)) {
    # Preferred: auto-start launcher (boots backend if needed, waits for port, then the game)
    $sc.TargetPath = $psExe
    $sc.Arguments = '-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "' + $allPs + '"'
    $sc.WorkingDirectory = $app.FullName
} elseif ($exe) {
    # jpackage launcher exe (no auto-start backend)
    $sc.TargetPath = $exe.FullName
    $sc.WorkingDirectory = $app.FullName
} else {
    # Fallback: bundled javaw when exe unavailable
    $sc.TargetPath = $javaw
    $sc.Arguments = '-Xmx1g -Dfile.encoding=UTF-8 -cp "' + $jar.FullName + '" com.cavedream.desktop.Main'
    $sc.WorkingDirectory = $app.FullName
}
if (Test-Path $ico) { $sc.IconLocation = "$ico,0" }
$sc.Description = 'CaveDream'
$sc.Save()
Write-Output ('shortcut created -> ' + $sc.TargetPath)
