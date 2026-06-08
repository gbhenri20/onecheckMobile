# Popula / complementa o banco One Check via API (admin).
# Idempotente. Uso:
#   powershell -ExecutionPolicy Bypass -File scripts/populate_database.ps1

$ErrorActionPreference = "Continue"
$base = "http://3.145.6.22:8000"

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg) { Write-Host "  OK: $msg" -ForegroundColor Green }
function Write-Skip($msg) { Write-Host "  SKIP: $msg" -ForegroundColor DarkYellow }
function Write-Fail($msg) { Write-Host "  ERRO: $msg" -ForegroundColor Red }

function Login-Admin {
    $body = '{"email":"admin@onecheck.com.br","senha":"Admin@1234"}'
    $r = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    if (-not $r.sucesso) { throw "Login admin falhou" }
    return @{ Authorization = "Bearer $($r.dados.access_token)" }
}

function Api-Post($path, $obj, $headers) {
    $json = $obj | ConvertTo-Json -Compress -Depth 6
    try {
        return Invoke-RestMethod -Uri "$base$path" -Method Post -Body $json -ContentType "application/json" -Headers $headers
    } catch {
        $detail = $_.ErrorDetails.Message
        if (-not $detail) { $detail = $_.Exception.Message }
        throw "POST $path -> $detail"
    }
}

function Api-PostSafe($path, $obj, $headers) {
    try {
        $r = Api-Post $path $obj $headers
        return @{ ok = $true; data = $r.dados }
    } catch {
        return @{ ok = $false; err = $_.Exception.Message }
    }
}

function Api-Get($path, $headers) {
    $r = Invoke-RestMethod -Uri "$base$path" -Headers $headers
    if ($r.sucesso -eq $false) { throw "GET $path falhou: $($r.erro)" }
    return $r.dados
}

function Find-UserId($headers, $email) {
    $users = Api-Get "/api/v1/usuarios?por_pagina=100" $headers
    ($users | Where-Object { $_.email -eq $email } | Select-Object -First 1).id
}

function Ensure-User($headers, $nome, $email, $senha, $role) {
    $id = Find-UserId $headers $email
    if ($id) {
        Write-Skip "usuario $email id=$id"
        return $id
    }
    $r = Api-Post "/api/v1/usuarios" @{ nome = $nome; email = $email; senha = $senha; role = $role } $headers
    Write-Ok "usuario $email id=$($r.dados.id)"
    return $r.dados.id
}

function Ensure-Imovel($headers, $tipo, $tamanho, $garagem, $vagas, $endereco) {
    $imoveis = Api-Get "/api/v1/imoveis?por_pagina=100" $headers
    $found = $imoveis | Where-Object { $_.tipo -eq $tipo -and $_.tamanho -eq $tamanho } | Select-Object -First 1
    if ($found) {
        Write-Skip "imovel $tipo $tamanho id=$($found.id)"
        return $found.id
    }
    $r = Api-Post "/api/v1/imoveis" @{
        tipo = $tipo
        tamanho = $tamanho
        garagem = $garagem
        garagem_vagas = $vagas
        endereco = $endereco
    } $headers
    Write-Ok "imovel $tipo id=$($r.dados.id)"
    return $r.dados.id
}

function Ensure-Comodo($headers, $imovelId, $tipo, $descricao) {
    $comodos = Api-Get "/api/v1/imoveis/$imovelId/comodos" $headers
    $found = $comodos | Where-Object { $_.tipo -eq $tipo } | Select-Object -First 1
    if ($found) { return $found.id }
    $r = Api-Post "/api/v1/imoveis/$imovelId/comodos" @{ tipo = $tipo; descricao = $descricao } $headers
    Write-Ok "comodo $tipo"
    return $r.dados.id
}

function Ensure-Contrato($headers, $imovelId, $locatarioId, $inicio, $fim) {
    $contratos = Api-Get "/api/v1/contratos?por_pagina=100&status=ativo" $headers
    $found = $contratos | Where-Object { $_.imovel_id -eq $imovelId -and $_.locatario_id -eq $locatarioId } | Select-Object -First 1
    if ($found) {
        Write-Skip "contrato id=$($found.id)"
        return $found.id
    }
    $r = Api-Post "/api/v1/contratos" @{
        imovel_id = $imovelId
        locatario_id = $locatarioId
        data_inicio = $inicio
        data_fim = $fim
    } $headers
    Write-Ok "contrato id=$($r.dados.id)"
    return $r.dados.id
}

function Ensure-Agendamento($headers, $contratoId, $tipo, $data, $obs) {
    $ags = Api-Get "/api/v1/contratos/$contratoId/agendamentos" $headers
    $found = $ags | Where-Object { $_.tipo -eq $tipo } | Select-Object -First 1
    if ($found) {
        Write-Skip "agendamento $tipo"
        return $found.id
    }
    $r = Api-PostSafe "/api/v1/contratos/$contratoId/agendamentos" @{
        tipo = $tipo
        data_agendada = $data
        observacao = $obs
    } $headers
    if ($r.ok) {
        Write-Ok "agendamento $tipo"
        return $r.data.id
    }
    Write-Fail "agendamento $tipo : $($r.err)"
    return $null
}

function Ensure-Checklist($headers, $contratoId, $vistoriadorId, $tipo) {
    $cls = Api-Get "/api/v1/contratos/$contratoId/checklists" $headers
    $found = $cls | Where-Object { $_.tipo -eq $tipo } | Select-Object -First 1
    if ($found) {
        Write-Skip "checklist $tipo id=$($found.id)"
        return $found.id
    }
    $r = Api-PostSafe "/api/v1/contratos/$contratoId/checklists" @{
        vistoriador_id = $vistoriadorId
        tipo = $tipo
    } $headers
    if ($r.ok) {
        Write-Ok "checklist $tipo id=$($r.data.id)"
        return $r.data.id
    }
    Write-Fail "checklist $tipo : $($r.err)"
    return $null
}

function Populate-ContratoCompleto($h, $vh, $contratoId, $imovelId, $vistId) {
    Write-Step "Contrato $contratoId"
    Ensure-Agendamento $h $contratoId "inicial" "2026-08-10 09:00:00" "Vistoria inicial" | Out-Null
    Ensure-Agendamento $h $contratoId "encerramento" "2026-12-10 14:00:00" "Vistoria encerramento" | Out-Null
    $chkIni = Ensure-Checklist $h $contratoId $vistId "inicial"
    $chkEnc = Ensure-Checklist $h $contratoId $vistId "encerramento"

    if (-not $chkEnc) { return }

    $comodos = Api-Get "/api/v1/imoveis/$imovelId/comodos" $h
    $itensV = Api-Get "/api/v1/itens-vistoria" $vh
    if ($comodos.Count -eq 0 -or $itensV.Count -eq 0) { return }

    $iv = $itensV[0].id
    $estados = @("otimo", "bom", "regular", "bom")
    $n = [Math]::Min(4, $comodos.Count)
    for ($i = 0; $i -lt $n; $i++) {
        $c = $comodos[$i]
        $est = $estados[$i % $estados.Length]
        $det = Api-Get "/api/v1/checklists/$chkEnc" $vh
        $exists = $det.itens | Where-Object { $_.comodo_id -eq $c.id } | Select-Object -First 1
        if ($exists) { continue }
        $res = Api-PostSafe "/api/v1/checklists/$chkEnc/itens" @{
            comodo_id = $c.id
            item_vistoria_id = $iv
            estado = $est
            observacao = "Seed automatico - $($c.tipo)"
        } $vh
        if ($res.ok) { Write-Ok "item $($c.tipo) em encerramento" }
        else { Write-Fail "item $($c.tipo): $($res.err)" }
    }
}

# --- MAIN ---
Write-Step "Login admin"
$h = Login-Admin

Write-Step "Usuarios (cria se nao existir)"
$vistId = Ensure-User $h "Carlos Vistoriador" "carlos.vistoriador@onecheck.com.br" "Vistoria@123" "vistoriador"
$locAna = Ensure-User $h "Ana Locataria" "ana.locataria@onecheck.com.br" "Locatario@123" "locatario"
$locPedro = Ensure-User $h "Pedro Locatario" "pedro.locatario@onecheck.com.br" "Locatario@123" "locatario"

Write-Step "Imoveis e comodos"
$imovelPrincipal = Ensure-Imovel $h "Apartamento" "92m2" $true 1 @{
    rua = "Rua Augusta"; numero = "500"; cidade = "Sao Paulo"; estado = "SP"; cep = "01305-000"
}
$imovelDemo = Ensure-Imovel $h "Apartamento" "85m2" $true 1 @{
    rua = "Rua das Palmeiras"; numero = "250"; cidade = "Sao Paulo"; estado = "SP"; cep = "01310-100"
}
$imovelCasa = Ensure-Imovel $h "Casa" "120m2" $true 2 @{
    rua = "Av. Brasil"; numero = "1500"; cidade = "Campinas"; estado = "SP"; cep = "13025-240"
}

foreach ($c in @("Sala de Estar", "Cozinha", "Quarto Principal", "Banheiro Social", "Varanda", "Area de Servico", "Suite", "Escritorio")) {
    Ensure-Comodo $h $imovelPrincipal $c "Comodo $c" | Out-Null
}
foreach ($c in @("Sala", "Cozinha", "Quarto", "Banheiro")) {
    Ensure-Comodo $h $imovelDemo $c "Comodo $c" | Out-Null
}
foreach ($c in @("Sala", "Cozinha", "Quartos", "Banheiro", "Garagem")) {
    Ensure-Comodo $h $imovelCasa $c "Comodo $c" | Out-Null
}

Write-Step "Contratos"
$contratoAna = Ensure-Contrato $h $imovelPrincipal $locAna "2026-01-01" "2027-12-31"
$contratoDemo = Ensure-Contrato $h $imovelDemo $locPedro "2026-03-01" "2027-03-01"
$contratoCasa = Ensure-Contrato $h $imovelCasa $locPedro "2026-04-01" "2028-04-01"

Write-Step "Login Carlos"
$vLogin = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"carlos.vistoriador@onecheck.com.br","senha":"Vistoria@123"}' -ContentType "application/json"
$vh = @{ Authorization = "Bearer $($vLogin.dados.access_token)" }

Populate-ContratoCompleto $h $vh $contratoAna $imovelPrincipal $vistId
Populate-ContratoCompleto $h $vh $contratoDemo $imovelDemo $vistId
Populate-ContratoCompleto $h $vh $contratoCasa $imovelCasa $vistId

Write-Step "Problema exemplo (Ana)"
try {
    $locLogin = Invoke-RestMethod -Uri "$base/api/v1/auth/login" -Method Post -Body '{"email":"ana.locataria@onecheck.com.br","senha":"Locatario@123"}' -ContentType "application/json"
    $lh = @{ Authorization = "Bearer $($locLogin.dados.access_token)" }
    $comodos = Api-Get "/api/v1/imoveis/$imovelPrincipal/comodos" $h
    if ($comodos.Count -gt 0) {
        Api-PostSafe "/api/v1/contratos/$contratoAna/problemas" @{
            comodo_id = $comodos[0].id
            titulo = "Gotejamento na torneira"
            descricao = "Problema registrado no seed da API"
        } $lh | Out-Null
        Write-Ok "problema no contrato Ana"
    }
} catch { Write-Skip "problema: $_" }

Write-Step "RESUMO - credenciais para teste"
Write-Host @"

Admin:     admin@onecheck.com.br / Admin@1234
Vistoriador: carlos.vistoriador@onecheck.com.br / Vistoria@123  (id $vistId)
Locataria: ana.locataria@onecheck.com.br / Locatario@123
Locataria: pedro.locatario@onecheck.com.br / Locatario@123

Contrato principal (Ana): $contratoAna
Imovel principal: $imovelPrincipal
Contrato demo: $contratoDemo
Contrato casa: $contratoCasa

No app mobile: login Carlos -> Atualizar agenda -> 3 contratos com vistorias inicial e encerramento.

"@
