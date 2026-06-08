$base = "http://3.145.6.22:8000"
$passwords = @("Senha@123", "Admin@1234", "Vistoriador@123", "Carlos@1234")
foreach ($senha in $passwords) {
    $body = @{ email = "carlos.vistoriador@onecheck.com.br"; senha = $senha } | ConvertTo-Json -Compress
    try {
        $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
        if ($r.sucesso -and $r.dados.access_token) {
            Write-Host "LOGIN OK com senha: $senha"
            $token = $r.dados.access_token
            $h = @{ Authorization = "Bearer $token" }
            $cid = "00000005-0000-4000-8000-000000000001"
            $cl = (Invoke-RestMethod -Uri "$base/api/v1/contratos/$cid/checklists" -Headers $h).dados
            Write-Host "Checklists visiveis: $($cl.Count)"
            $cl | ForEach-Object { Write-Host "  tipo=$($_.tipo) id=$($_.id)" }
            try {
                Invoke-RestMethod -Uri "$base/api/v1/checklists/00000006-0000-4000-8000-000000000001" -Headers $h | Out-Null
                Write-Host "GET checklist inicial: OK"
            } catch { Write-Host "GET checklist inicial: $($_.Exception.Message)" }
            exit 0
        }
    } catch { }
}
Write-Host "Nenhuma senha funcionou para vistoriador"
