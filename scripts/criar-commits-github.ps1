# OneCheck Mobile — historico de commits com sentido (do zero ao app completo)
# Uso: .\scripts\criar-commits-github.ps1
# Opcional: .\scripts\criar-commits-github.ps1 -Force   (sem perguntar)

param([switch]$Force)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $ProjectRoot

function Find-Git {
    $fromPath = $null
    $cmd = Get-Command git -ErrorAction SilentlyContinue
    if ($cmd) { $fromPath = $cmd.Source }
    $candidates = @($fromPath, "$env:ProgramFiles\Git\cmd\git.exe") | Where-Object { $_ -and (Test-Path $_) }
    if (-not $candidates) { throw "Git nao encontrado." }
    return ($candidates | Select-Object -First 1)
}

$gitExe = Find-Git
function Invoke-Git { & $gitExe @args }

function Add-Paths([string[]]$Paths) {
    foreach ($path in $Paths) {
        if (Test-Path $path) { Invoke-Git add -- "$path" }
    }
}

function New-Commit([string]$Subject, [string]$Body, [string[]]$Paths) {
    Write-Host ">> $Subject" -ForegroundColor Yellow
    Add-Paths $Paths
    if (-not (Invoke-Git diff --cached --name-only)) {
        Write-Host "   (vazio, pulando)" -ForegroundColor DarkGray
        return
    }
    if ($Body) {
        Invoke-Git commit -m $Subject -m $Body
    } else {
        Invoke-Git commit -m $Subject
    }
}

Write-Host "Git: $gitExe" -ForegroundColor Cyan

if (-not (Test-Path ".git")) {
    Invoke-Git init
    Invoke-Git branch -M main
}

try {
    if ([int](Invoke-Git rev-list --count HEAD 2>$null) -gt 0 -and -not $Force) {
        $r = Read-Host "Repo ja tem commits. Continuar com arquivos novos? (s/N)"
        if ($r -notmatch '^[sS]') { exit 0 }
    }
} catch { }

# Ordem = como o app foi construido na pratica
$steps = @(
    @{
        Subject = "chore: inicializar projeto Android OneCheck"
        Body    = "Estrutura Gradle, wrapper e gitignore para o app de vistorias."
        Paths   = @(
            ".gitignore", "settings.gradle.kts", "build.gradle.kts", "gradle.properties",
            "gradle", "gradlew", "gradlew.bat", "app/build.gradle.kts", "app/.gitignore",
            "app/proguard-rules.pro"
        )
    }
    @{
        Subject = "feat(ui): tema escuro, cores e textos base"
        Body    = "Identidade visual e strings usadas em todas as telas."
        Paths   = @(
            "app/src/main/res/values/themes.xml"
            "app/src/main/res/values/colors.xml"
            "app/src/main/res/values/dimens.xml"
            "app/src/main/res/values/strings.xml"
            "app/src/main/res/values/plurals.xml"
            "app/src/main/res/color/box_stroke_color.xml"
            "app/src/main/res/drawable/bg_screen.xml"
            "app/src/main/res/drawable/bg_card.xml"
            "app/src/main/res/drawable/btn_save.xml"
        )
    }
    @{
        Subject = "feat(api): integrar backend OneCheck com Retrofit"
        Body    = "Cliente HTTP, DTOs, refresh de token, mapeamento e repositorio da API."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/data/api"
            "app/src/main/res/xml/network_security_config.xml"
        )
    }
    @{
        Subject = "feat: modelos de vistoria, checklist e contrato do repositorio"
        Body    = "Entidades de dominio e interface OneCheckRepository."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/model"
            "app/src/main/java/com/example/onecheck/data/OneCheckRepository.kt"
            "app/src/main/java/com/example/onecheck/data/LoadedInspection.kt"
        )
    }
    @{
        Subject = "feat(data): sessao do usuario e persistencia local"
        Body    = "TokenStore via sessao, log de envios e rascunho salvo no aparelho."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/data/OneCheckSession.kt"
            "app/src/main/java/com/example/onecheck/data/SubmissionLogStore.kt"
            "app/src/main/java/com/example/onecheck/data/DraftPersistStore.kt"
        )
    }
    @{
        Subject = "feat(ui): erros amigaveis, toasts e compressao de fotos"
        Body    = "Mensagens para o vistoriador e reducao de tamanho antes do upload."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/ui"
            "app/src/main/java/com/example/onecheck/util"
        )
    }
    @{
        Subject = "feat(auth): login do vistoriador e verificacao MFA"
        Body    = "Entrada no app com e-mail/senha e codigo do autenticador."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/LoginActivity.kt"
            "app/src/main/java/com/example/onecheck/MfaActivity.kt"
            "app/src/main/res/layout/activity_login.xml"
            "app/src/main/res/layout/activity_mfa.xml"
            "app/src/main/res/drawable/ic_lock.xml"
            "app/src/main/res/drawable/ic_email.xml"
        )
    }
    @{
        Subject = "feat(agenda): listar vistorias agendadas do vistoriador"
        Body    = "Tela principal com pull-to-refresh e abertura do checklist."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/AgendaActivity.kt"
            "app/src/main/res/layout/activity_agenda.xml"
            "app/src/main/res/layout/item_vistoria.xml"
            "app/src/main/res/menu/menu_agenda.xml"
            "app/src/main/res/drawable/bg_badge_inicial.xml"
            "app/src/main/res/drawable/bg_badge_final.xml"
        )
    }
    @{
        Subject = "feat(checklist): preencher itens por comodo e anexar fotos"
        Body    = "Estado do item, observacao, camera/galeria e upload para a API."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/RoomChecklistActivity.kt"
            "app/src/main/res/layout/activity_room_checklist.xml"
            "app/src/main/res/layout/item_checklist_row.xml"
            "app/src/main/res/menu/menu_checklist.xml"
            "app/src/main/res/xml/file_paths.xml"
            "app/src/main/res/drawable/ic_arrow_forward.xml"
        )
    }
    @{
        Subject = "feat(review): revisar comodos e enviar checklist"
        Body    = "Resumo por comodo, sincronizacao com servidor e submissao final."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/ReviewActivity.kt"
            "app/src/main/res/layout/activity_review.xml"
            "app/src/main/res/layout/item_comodo_resumo.xml"
        )
    }
    @{
        Subject = "feat(agenda): separar pendentes/enviados, cores de status e log"
        Body    = "Secoes na agenda, verde para aceitas, laranja para aguardando aceite, historico de envios."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/AgendaListAdapter.kt"
            "app/src/main/java/com/example/onecheck/SubmissionLogActivity.kt"
            "app/src/main/res/layout/item_agenda_section_header.xml"
            "app/src/main/res/layout/activity_submission_log.xml"
            "app/src/main/res/layout/item_submission_log.xml"
            "app/src/main/res/menu/menu_submission_log.xml"
            "app/src/main/res/drawable/bg_badge_aceita.xml"
            "app/src/main/res/drawable/bg_badge_aguardando.xml"
        )
    }
    @{
        Subject = "chore: application, manifesto, icones e testes"
        Body    = "OneCheckApp, AndroidManifest, icones launcher e testes padrao."
        Paths   = @(
            "app/src/main/java/com/example/onecheck/OneCheckApp.kt"
            "app/src/main/AndroidManifest.xml"
            "app/src/main/res/mipmap-anydpi-v26"
            "app/src/main/res/drawable/ic_launcher_foreground.xml"
            "app/src/main/res/drawable/ic_launcher_background.xml"
            "app/src/main/res/xml/data_extraction_rules.xml"
            "app/src/main/res/xml/backup_rules.xml"
            "app/src/androidTest"
            "app/src/test"
        )
    }
)

foreach ($step in $steps) {
    New-Commit $step.Subject $step.Body $step.Paths
}

if (Invoke-Git status --porcelain) {
    New-Commit "docs: script para gerar historico de commits no GitHub" @(
        "Scripts auxiliares para versionar o projeto em etapas."
    ) @("scripts")
}

Write-Host ""
Write-Host "Historico criado:" -ForegroundColor Green
Invoke-Git log --oneline
