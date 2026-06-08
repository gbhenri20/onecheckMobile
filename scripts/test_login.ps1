$base = "http://3.145.6.22:8000"
try {
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}' -ContentType "application/json" -TimeoutSec 30
    Write-Host "sucesso=$($r.sucesso) mfa=$($r.dados.mfa_required)"
} catch {
    Write-Host "ERRO: $($_.Exception.Message)"
}
