$base = "http://3.145.6.22:8000"
$v = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}' -ContentType "application/json"
Write-Host "carlos id:" $v.dados.usuario.id
$h = @{ Authorization = "Bearer $($v.dados.access_token)" }
$c = (Invoke-RestMethod -Uri "$base/api/v1/contratos?status=ativo&por_pagina=50" -Headers $h).dados
Write-Host "contratos:" $c.Count
$c | ForEach-Object {
    $ags = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$($_.id)/agendamentos" -Headers $h).dados
    $cls = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$($_.id)/checklists" -Headers $h).dados
    Write-Host "contrato $($_.id) ags=$($ags.Count) cls=$($cls.Count)"
}
