$base = "http://3.145.6.22:8000"
$h = @{ Authorization = "Bearer $((Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"admin@onecheck.com.br","senha":"Admin@1234"}' -ContentType "application/json").dados.access_token)" }

Write-Host "USUARIOS:"
(Invoke-RestMethod -Uri "$base/api/v1/usuarios?por_pagina=50" -Headers $h).dados | ForEach-Object { Write-Host "  $($_.email) $($_.id) $($_.role)" }

Write-Host "`nIMOVEIS:"
(Invoke-RestMethod -Uri "$base/api/v1/imoveis?por_pagina=50" -Headers $h).dados | ForEach-Object { Write-Host "  $($_.id) $($_.tipo) $($_.tamanho)" }

Write-Host "`nCONTRATOS ativos:"
(Invoke-RestMethod -Uri "$base/api/v1/contratos?status=ativo&por_pagina=50" -Headers $h).dados | ForEach-Object { Write-Host "  $($_.id) imovel=$($_.imovel_id) loc=$($_.locatario_id)" }

$cid = "00000005-0000-4000-8000-000000000001"
Write-Host "`nAGENDAMENTOS contrato $cid"
try {
    (Invoke-RestMethod -Uri "$base/api/v1/contratos/$cid/agendamentos" -Headers $h).dados | ForEach-Object { Write-Host "  $($_.id) $($_.tipo) $($_.data_agendada)" }
} catch { Write-Host $_.ErrorDetails.Message }

Write-Host "`nTEST POST agendamento:"
$body = '{"tipo":"inicial","data_agendada":"2026-07-01 10:00:00","observacao":"teste"}'
try {
    Invoke-RestMethod -Uri "$base/api/v1/contratos/db8bbae6-d3d3-4f9c-af76-20902eb76784/agendamentos" -Method Post -Body $body -ContentType "application/json" -Headers $h
} catch { Write-Host $_.ErrorDetails.Message }
