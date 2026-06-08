$base = "http://3.145.6.22:8000"
$loginJson = '{"email":"admin@onecheck.com.br","senha":"Admin@1234"}'
Write-Host "=== LOGIN ==="
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $loginJson -ContentType "application/json"
$login | ConvertTo-Json -Depth 8

if ($login.dados.mfa_required -eq $true) {
    Write-Host "MFA required - stopping"
    exit 0
}

$token = $login.dados.access_token
if (-not $token) { Write-Host "No token"; exit 1 }
$headers = @{ Authorization = "Bearer $token" }

Write-Host "`n=== CONTRATOS ==="
$contratos = Invoke-RestMethod -Uri "$base/api/v1/contratos?status=ativo&por_pagina=20" -Headers $headers
$contratos | ConvertTo-Json -Depth 6

Write-Host "`n=== USUARIO ME ==="
try {
    $me = Invoke-RestMethod -Uri "$base/api/v1/usuarios/me" -Headers $headers
    $me | ConvertTo-Json -Depth 4
} catch { Write-Host $_.Exception.Message }

if ($contratos.dados -and $contratos.dados.Count -gt 0) {
    $cid = $contratos.dados[0].id
    Write-Host "`n=== AGENDAMENTOS contrato $cid ==="
    $ag = Invoke-RestMethod -Uri "$base/api/v1/contratos/$cid/agendamentos" -Headers $headers
    $ag | ConvertTo-Json -Depth 6

    Write-Host "`n=== CHECKLISTS contrato $cid ==="
    $cl = Invoke-RestMethod -Uri "$base/api/v1/contratos/$cid/checklists" -Headers $headers
    $cl | ConvertTo-Json -Depth 8
}
