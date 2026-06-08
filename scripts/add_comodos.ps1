$base = "http://3.145.6.22:8000"
$imovelId = "00000002-0000-4000-8000-000000000001"

$loginJson = '{"email":"admin@onecheck.com.br","senha":"Admin@1234"}'
$login = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $loginJson -ContentType "application/json"
$h = @{ Authorization = "Bearer $($login.dados.access_token)" }

$existentes = (Invoke-RestMethod -Uri "$base/api/v1/imoveis/$imovelId/comodos" -Headers $h).dados
Write-Host "Comodos atuais: $($existentes.Count)"
$existentes | ForEach-Object { Write-Host "  - $($_.tipo)" }

$novos = @(
    @{ tipo = "Varanda"; descricao = "Varanda com vista" },
    @{ tipo = "Area de servico"; descricao = "Lavanderia" },
    @{ tipo = "Suite"; descricao = "Suite principal" }
)

foreach ($c in $novos) {
    $jaExiste = $existentes | Where-Object { $_.tipo -eq $c.tipo }
    if ($jaExiste) {
        Write-Host "Ja existe: $($c.tipo)"
        continue
    }
    $body = $c | ConvertTo-Json -Compress
    try {
        $r = Invoke-RestMethod -Uri "$base/api/v1/imoveis/$imovelId/comodos" -Method Post -Body $body -ContentType "application/json" -Headers $h
        Write-Host "Criado: $($c.tipo) id=$($r.dados.id)"
    } catch {
        Write-Host "Erro $($c.tipo): $($_.ErrorDetails.Message)"
    }
}

$final = (Invoke-RestMethod -Uri "$base/api/v1/imoveis/$imovelId/comodos" -Headers $h).dados
Write-Host "`nTotal comodos: $($final.Count)"
