$base = "http://3.145.6.22:8000"
$contratoId = "00000005-0000-4000-8000-000000000001"

function Login($email, $senha) {
    $body = @{ email = $email; senha = $senha } | ConvertTo-Json -Compress
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    if (-not $r.sucesso) { throw "Login falhou: $email" }
    return @{ Token = $r.dados.access_token; User = $r.dados.usuario; Headers = @{ Authorization = "Bearer $($r.dados.access_token)" } }
}

Write-Host "=== ADMIN ==="
$admin = Login "admin@onecheck.com.br" "Admin@1234"
Write-Host "user id: $($admin.User.id) role: $($admin.User.role)"

Write-Host "`n=== VISTORIADOR ==="
$vist = Login "carlos.vistoriador@onecheck.com.br" "Vistoria@123"
Write-Host "user id: $($vist.User.id) role: $($vist.User.role)"

Write-Host "`n--- ADMIN: checklists do contrato ---"
$clAdmin = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$contratoId/checklists" -Headers $admin.Headers).dados
$clAdmin | ForEach-Object { Write-Host "  checklist id=$($_.id) tipo=$($_.tipo) status=$($_.status) vistoriador=$($_.vistoriador_id)" }

Write-Host "`n--- ADMIN: criar checklist encerramento? ---"
$createBody = @{
    vistoriador_id = "00000001-0000-4000-8000-000000000001"
    tipo = "encerramento"
} | ConvertTo-Json -Compress
try {
    $created = Invoke-RestMethod -Uri "$base/api/v1/contratos/$contratoId/checklists" -Method Post -Body $createBody -ContentType "application/json" -Headers $admin.Headers
    Write-Host "CRIADO:" ($created | ConvertTo-Json -Compress)
} catch {
    Write-Host "ERRO criar: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
}

Write-Host "`n--- checklists apos tentativa criar ---"
$cl2 = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$contratoId/checklists" -Headers $admin.Headers).dados
$cl2 | ForEach-Object { Write-Host "  id=$($_.id) tipo=$($_.tipo) status=$($_.status)" }

Write-Host "`n--- VISTORIADOR: contratos ---"
try {
    $contratos = (Invoke-RestMethod -Uri "$base/api/v1/contratos?status=ativo" -Headers $vist.Headers).dados
    Write-Host "contratos: $($contratos.Count)"
} catch {
    Write-Host "ERRO contratos vistoriador: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
}

Write-Host "`n--- VISTORIADOR: agendamentos ---"
$ag = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$contratoId/agendamentos" -Headers $vist.Headers).dados
$ag | ForEach-Object { Write-Host "  ag id=$($_.id) tipo=$($_.tipo)" }

Write-Host "`n--- VISTORIADOR: checklists ---"
$clV = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$contratoId/checklists" -Headers $vist.Headers).dados
$clV | ForEach-Object { Write-Host "  id=$($_.id) tipo=$($_.tipo) status=$($_.status)" }

$enc = $clV | Where-Object { $_.tipo -eq "encerramento" } | Select-Object -First 1
$ini = $clV | Where-Object { $_.tipo -eq "inicial" } | Select-Object -First 1

if ($ini) {
    $chkId = $ini.id
    Write-Host "`n--- VISTORIADOR: GET checklist inicial $chkId ---"
    try {
        $det = Invoke-RestMethod -Uri "$base/api/v1/checklists/$chkId" -Headers $vist.Headers
        Write-Host "  status=$($det.dados.status) itens=$($det.dados.itens.Count)"
    } catch {
        Write-Host "  ERRO: $($_.Exception.Message)"
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    }

    Write-Host "`n--- VISTORIADOR: PATCH submeter $chkId ---"
    try {
        $sub = Invoke-WebRequest -Uri "$base/api/v1/checklists/$chkId/submeter" -Method PATCH -Headers $vist.Headers -UseBasicParsing
        Write-Host "  HTTP $($sub.StatusCode) body: $($sub.Content)"
    } catch {
        Write-Host "  ERRO submeter: $($_.Exception.Message)"
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    }
}

if ($enc) {
    Write-Host "`n--- VISTORIADOR: GET checklist encerramento $($enc.id) ---"
    try {
        Invoke-RestMethod -Uri "$base/api/v1/checklists/$($enc.id)" -Headers $vist.Headers | Out-Null
        Write-Host "  OK"
    } catch {
        Write-Host "  ERRO: $($_.Exception.Message)"
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    }
} else {
    Write-Host "`n!!! SEM CHECKLIST ENCERRAMENTO - causa da 2a vistoria nao abrir"
}
