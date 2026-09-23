$ProgressPreference = 'SilentlyContinue'
$r = Invoke-WebRequest -Uri 'http://localhost:8081/api/items' -UseBasicParsing
$json = $r.Content | ConvertFrom-Json
$grass = $json.items | Where-Object { $_.key_name -eq 'GRASS' }
$fill = $grass.forms | Where-Object { $_.kind -eq 'fill' -and $_.variant -eq 0 }
Write-Output ('fill0 sha=' + $fill.sha)
Write-Output ('fill0 dim=' + $fill.w + ' x ' + $fill.h)
$img = Invoke-WebRequest -Uri ('http://localhost:8081/api/items/image/' + $fill.sha) -UseBasicParsing
$bytes = $img.Content
Write-Output ('image bytes=' + $bytes.Length + ' type=' + $img.Headers['Content-Type'] + ' cache=' + $img.Headers['Cache-Control'])
