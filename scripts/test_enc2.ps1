$base = "http://3.145.6.22:8000"
$body = '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}'
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
$h = @{ Authorization = "Bearer $($login.dados.access_token)" }
$encId = "86eeb2fd-a48c-4bc6-97b4-c3fe5f241f94"
try {
    $r = Invoke-WebRequest -Uri "$base/api/v1/checklists/$encId/submeter" -Method PATCH -Headers $h -UseBasicParsing
    Write-Host "OK" $r.StatusCode $r.Content
} catch [System.Net.WebException] {
    $resp = $_.Exception.Response
    if ($resp) {
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        Write-Host "HTTP" ([int]$resp.StatusCode) $reader.ReadToEnd()
    } else { Write-Host $_.Exception.Message }
}
