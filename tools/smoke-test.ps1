# CaveDream 后端 API 冒烟测试：注册 → 登录 → 上传云存档 → 列表 → 下载
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'

Write-Host '== 1. register (409=已注册，视为通过) =='
try {
    Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType 'application/json' `
        -Body '{"username":"dreamer01","password":"123456","nickname":"Sleepwalker"}' | ConvertTo-Json
} catch {
    Write-Host ('skip: ' + $_.Exception.Message)
}

Write-Host '== 2. login =='
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' `
    -Body '{"username":"dreamer01","password":"123456"}'
$login | ConvertTo-Json
$token = $login.token

Write-Host '== 3. upload save slot 1 =='
$body = '{"saveName":"first dream","seed":20260922,"gameVersion":"v0.25","worldStateJson":"{\"storylineProgress\":2,\"dreamSeaUnlocked\":false,\"clearedLayerIds\":[\"L1_SHALLOW\",\"L2_MAZE\"]}"}'
Invoke-RestMethod -Method Put -Uri "$base/api/saves/1" -Headers @{ 'X-Token' = $token } `
    -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 5

Write-Host '== 4. list saves =='
Invoke-RestMethod -Uri "$base/api/saves" -Headers @{ 'X-Token' = $token } | ConvertTo-Json -Depth 5

Write-Host '== 5. download slot 1 =='
Invoke-RestMethod -Uri "$base/api/saves/1" -Headers @{ 'X-Token' = $token } | ConvertTo-Json -Depth 5

Write-Host '== ALL DONE =='
