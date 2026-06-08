$base = "http://3.145.6.22:8000"
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}' -ContentType "application/json"
$token = $login.dados.access_token
$encId = "86eeb2fd-a48c-4bc6-97b4-c3fe5f241f94"
$itemId = "a43d2d71-833d-40de-a373-f22b3687a732"
$pngPath = Join-Path $env:TEMP "onecheck_test.png"
[Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==") | Set-Content -Path $pngPath -Encoding Byte

$uri = "$base/api/v1/checklists/$encId/itens/$itemId/fotos"
$boundary = [System.Guid]::NewGuid().ToString()
$fileBytes = [System.IO.File]::ReadAllBytes($pngPath)
$enc = [System.Text.Encoding]::GetEncoding("iso-8859-1")
$LF = "`r`n"
$bodyLines = @(
    "--$boundary",
    "Content-Disposition: form-data; name=`"foto`"; filename=`"test.png`"",
    "Content-Type: image/png",
    "",
    $enc.GetString($fileBytes),
    "--$boundary--",
    ""
) -join $LF
$bodyBytes = $enc.GetBytes($bodyLines)

try {
    $r = Invoke-WebRequest -Uri $uri -Method Post -Headers @{ Authorization = "Bearer $token" } -ContentType "multipart/form-data; boundary=$boundary" -Body $bodyBytes -UseBasicParsing
    Write-Host "HTTP $($r.StatusCode)"
    Write-Host $r.Content
} catch {
    Write-Host "HTTP $($_.Exception.Response.StatusCode.value__)"
    Write-Host $_.ErrorDetails.Message
}
