# Fast dev test: run the appearance/anim/weapon unit tests only (skip the full suite) to keep iteration quick.
# Usage: powershell -ExecutionPolicy Bypass -File tools\dev-test.ps1
# Final gate is still: gradlew build (full tests) before packaging.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

& .\gradlew.bat :core:test --tests '*DreamerRigTest*' --tests '*AppearanceTest*' --tests '*WeaponRasterTest*' --tests '*PaintedLookTest*' --console=plain
if ($LASTEXITCODE -eq 0) {
    Write-Host '>>> TESTS OK'
} else {
    Write-Host '>>> TESTS FAIL'
}
