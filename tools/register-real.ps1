# Register using the REAL email verification code (ASCII only)
$ErrorActionPreference = 'Stop'
$body = @{ email = '2017934282@qq.com'; code = '158778'; password = 'cavedream123'; nickname = 'Zhe' } | ConvertTo-Json -Compress
Write-Host ('body: ' + $body)
try {
    $resp = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/auth/register' `
        -ContentType 'application/json' -Body $body
    $resp | ConvertTo-Json
} catch {
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    Write-Host ('server said: ' + $reader.ReadToEnd())
}
