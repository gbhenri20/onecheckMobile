$base = "http://3.145.6.22:8000"
$body = '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}'
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
$h = @{ Authorization = "Bearer $($login.dados.access_token)" }
$encId = "86eeb2fd-a48c-4bc6-97b4-c3fe5f241f94"
$det = Invoke-RestMethod -Uri "$base/api/v1/checklists/$encId" -Headers $h
Write-Host "encerramento itens:" $det.dados.itens.Count "status:" $det.dados.status
$imovel = "00000002-0000-4000-8000-000000000001"
$com = (Invoke-RestMethod -Uri "$base/api/v1/imoveis/$imovel/comodos" -Headers $h).dados
Write-Host "comodos:" $com.Count
try {
    $r = Invoke-WebRequest -Uri "$base/api/v1/checklists/$encId/submeter" -Method PATCH -Headers $h -UseBasicParsing
    Write-Host "submeter enc HTTP" $r.StatusCode $r.Content
} catch {
    Write-Host "submeter enc:" $_.ErrorDetails.Message
}
