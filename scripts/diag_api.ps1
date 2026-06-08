$base = "http://3.145.6.22:8000"
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}' -ContentType "application/json"
$h = @{ Authorization = "Bearer $($login.dados.access_token)" }

$contratos = (Invoke-RestMethod -Uri "$base/api/v1/contratos" -Headers $h).dados
foreach ($c in $contratos) {
    Write-Host "contrato $($c.id) imovel $($c.imovel_id)"
}

$encId = "86eeb2fd-a48c-4bc6-97b4-c3fe5f241f94"
$ch = (Invoke-RestMethod -Uri "$base/api/v1/checklists/$encId" -Headers $h).dados
Write-Host "encerramento itens=$($ch.itens.Count) status=$($ch.status) contrato=$($ch.contrato_id)"

$contratoId = $ch.contrato_id
$imovelId = ($contratos | Where-Object { $_.id -eq $contratoId }).imovel_id
Write-Host "imovel do contrato encerramento: $imovelId"

$com = (Invoke-RestMethod -Uri "$base/api/v1/imoveis/$imovelId/comodos" -Headers $h).dados
Write-Host "comodos no imovel correto: $($com.Count)"
$com | ForEach-Object { Write-Host "  - $($_.tipo) $($_.id)" }

# criar item e testar foto se houver item
$itensV = (Invoke-RestMethod -Uri "$base/api/v1/itens-vistoria" -Headers $h).dados
$iv = $itensV[0]
$comodo = $com[0]
if ($ch.itens.Count -eq 0) {
    $body = @{ comodo_id = $comodo.id; item_vistoria_id = $iv.id; estado = "bom"; observacao = "teste api" } | ConvertTo-Json -Compress
    $criado = Invoke-RestMethod -Uri "$base/api/v1/checklists/$encId/itens" -Method Post -Body $body -ContentType "application/json" -Headers $h
    $itemId = $criado.dados.id
    Write-Host "item criado $itemId"
} else {
    $itemId = $ch.itens[0].id
    Write-Host "usando item existente $itemId"
}

# upload foto minima
$png = [Convert]::FromBase64String("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
$tmp = [System.IO.Path]::GetTempFileName() + ".png"
[IO.File]::WriteAllBytes($tmp, $png)
try {
    $form = @{ foto = Get-Item $tmp }
    $r = Invoke-RestMethod -Uri "$base/api/v1/checklists/$encId/itens/$itemId/fotos" -Method Post -Headers $h -Form $form
    Write-Host "foto OK: $($r.dados.id)"
} catch {
    Write-Host "foto ERRO: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
}
Remove-Item $tmp -ErrorAction SilentlyContinue

try {
    $r2 = Invoke-WebRequest -Uri "$base/api/v1/checklists/$encId/submeter" -Method PATCH -Headers $h -UseBasicParsing
    Write-Host "submeter HTTP $($r2.StatusCode) $($r2.Content)"
} catch {
    Write-Host "submeter ERRO: $($_.ErrorDetails.Message)"
}
