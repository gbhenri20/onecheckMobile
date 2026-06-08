$base = "http://3.145.6.22:8000"
$v = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}' -ContentType "application/json"
$h = @{ Authorization = "Bearer $($v.dados.access_token)" }

$contratos = (Invoke-RestMethod -Uri "$base/api/v1/contratos?status=ativo&por_pagina=20" -Headers $h).dados
foreach ($c in $contratos) {
    $cls = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$($c.id)/checklists" -Headers $h).dados
    foreach ($cl in $cls) {
        Write-Host "`n--- checklist $($cl.id) tipo=$($cl.tipo) status=$($cl.status) ---"
        $det = (Invoke-RestMethod -Uri "$base/api/v1/checklists/$($cl.id)" -Headers $h).dados
        Write-Host "itens no servidor: $($det.itens.Count)"
        try {
            $r = Invoke-WebRequest -Uri "$base/api/v1/checklists/$($cl.id)/submeter" -Method PATCH -Headers $h -UseBasicParsing
            Write-Host "SUBMETER HTTP $($r.StatusCode) $($r.Content.Substring(0, [Math]::Min(300, $r.Content.Length)))"
        } catch {
            Write-Host "SUBMETER ERRO HTTP $($_.Exception.Response.StatusCode.value__)"
            Write-Host $_.ErrorDetails.Message
        }
    }
}
