# One-off: request a real verification code email (ASCII only)
$ErrorActionPreference = 'Stop'
$body = @{ email = '2017934282@qq.com' } | ConvertTo-Json -Compress
Write-Host ('request body: ' + $body)
$resp = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/auth/send-code' `
    -ContentType 'application/json' -Body $body
$resp | ConvertTo-Json
