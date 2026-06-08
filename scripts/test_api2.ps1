$base = "http://3.145.6.22:8000"
$loginJson = '{"email":"admin@onecheck.com.br","senha":"Admin@1234"}'
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $loginJson -ContentType "application/json"
$token = $login.dados.access_token
$headers = @{ Authorization = "Bearer $token" }

$contratos = (Invoke-RestMethod -Uri "$base/api/v1/contratos?status=ativo&por_pagina=20" -Headers $headers).dados
foreach ($c in $contratos) {
    $cid = $c.id
    Write-Host "`n======== CONTRATO $cid imovel=$($c.imovel_id) ========"
    $ag = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$cid/agendamentos" -Headers $headers).dados
    Write-Host "Agendamentos: $($ag.Count)"
    $ag | ForEach-Object { Write-Host "  - id=$($_.id) tipo=$($_.tipo) data=$($_.data_agendada)" }

    $cl = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$cid/checklists" -Headers $headers).dados
    Write-Host "Checklists: $($cl.Count)"
    $cl | ForEach-Object { Write-Host "  - id=$($_.id) tipo=$($_.tipo) vistoriador=$($_.vistoriador_id) status=$($_.status)" }

    foreach ($chk in $cl) {
        $chkId = $chk.id
        Write-Host "`n  GET checklist $chkId ..."
        try {
            $detail = Invoke-RestMethod -Uri "$base/api/v1/checklists/$chkId" -Headers $headers
            $itens = $detail.dados.itens
            Write-Host "  itens: $($itens.Count)"
            if ($itens -and $itens.Count -gt 0) {
                $item0 = $itens[0]
                Write-Host "  primeiro item: id=$($item0.id) comodo=$($item0.comodo_id)"
            }
        } catch {
            Write-Host "  ERRO: $($_.Exception.Message)"
            if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
        }
    }
}

Write-Host "`n=== USUARIOS vistoriador ==="
try {
    $users = Invoke-RestMethod -Uri "$base/api/v1/usuarios?role=vistoriador&por_pagina=10" -Headers $headers
    $users.dados | ForEach-Object { Write-Host "  $($_.email) id=$($_.id)" }
} catch { Write-Host $_.Exception.Message }
