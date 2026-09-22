# CaveDream backend smoke test: send-code -> register(code) -> login -> upload -> list -> download -> reset -> relogin
# Uses dev master code 888888 (app.auth.dev-master-code=true) so it passes without real SMTP config.
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8081'
$mail = 'dreamer+' + (Get-Random -Maximum 99999) + '@qq.com'

Write-Host '== 1. send-code (mail not configured -> 503 with hint, or 200 if auth code set) =='
try {
    Invoke-RestMethod -Method Post -Uri "$base/api/auth/send-code" -ContentType 'application/json' `
        -Body ('{"email":"' + $mail + '"}') | ConvertTo-Json
} catch {
    Write-Host ('expected hint: ' + $_.Exception.Message)
}

Write-Host '== 2. register with dev master code 888888 =='
Invoke-RestMethod -Method Post -Uri "$base/api/auth/register" -ContentType 'application/json' `
    -Body ('{"email":"' + $mail + '","code":"888888","password":"123456","nickname":"MailWalker"}') | ConvertTo-Json

Write-Host '== 3. login with email =='
$login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' `
    -Body ('{"account":"' + $mail + '","password":"123456"}')
$login | ConvertTo-Json
$token = $login.token

Write-Host '== 4. upload save slot 2 =='
$body = '{"saveName":"mail dream","seed":987654321,"gameVersion":"v0.33","worldStateJson":"{\"storylineProgress\":1,\"dreamSeaUnlocked\":false}"}'
Invoke-RestMethod -Method Put -Uri "$base/api/saves/2" -Headers @{ 'X-Token' = $token } `
    -ContentType 'application/json' -Body $body | ConvertTo-Json -Depth 5

Write-Host '== 5. list saves =='
Invoke-RestMethod -Uri "$base/api/saves" -Headers @{ 'X-Token' = $token } | ConvertTo-Json -Depth 5

Write-Host '== 6. reset password with master code =='
Invoke-RestMethod -Method Post -Uri "$base/api/auth/reset" -ContentType 'application/json' `
    -Body ('{"email":"' + $mail + '","code":"888888","newPassword":"654321"}') | ConvertTo-Json

Write-Host '== 7. relogin with new password =='
$relogin = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' `
    -Body ('{"account":"' + $mail + '","password":"654321"}')
Write-Host ('re-login ok, nickname = ' + $relogin.nickname)

Write-Host '== ALL DONE =='
