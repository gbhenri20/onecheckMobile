# Testes — OneCheck Mobile

Este documento descreve os tipos de teste do projeto, como executá-los e o que cada camada cobre.

## Tipos de teste

| Tipo | Pasta | O que valida | Quando roda |
|------|--------|--------------|-------------|
| **Unitário (JVM)** | `app/src/test/` | Lógica pura: rascunho, mapeamentos API, parser de erros | PC, sem emulador |
| **Integração (JVM)** | `app/src/test/` | Repositório + `MockWebServer` (login/MFA simulado) | PC, com Robolectric |
| **Instrumentado** | `app/src/androidTest/` | Android real: SharedPreferences, Bitmap, Context | Emulador ou celular |
| **Aceitação (UI)** | `app/src/androidTest/` | Espresso: telas e fluxo visível ao usuário | Emulador ou celular |

## Executar localmente

### Testes unitários (recomendado no CI e no dia a dia)

```bash
./gradlew testDebugUnitTest
```

Relatório HTML:

`app/build/reports/tests/testDebugUnitTest/index.html`

### Testes instrumentados (emulador ou USB)

Conecte um dispositivo ou inicie um emulador, depois:

```bash
./gradlew connectedDebugAndroidTest
```

Relatório:

`app/build/reports/androidTests/connected/debug/index.html`

### Todos os testes unitários + build

```bash
./gradlew testDebugUnitTest assembleDebug
```

## O que está coberto hoje

### Unitários

- `ChecklistDraftTest` — merge local/API, troca de IDs, fotos, próximo cômodo
- `ApiMappersTest` — status aceito/submetido, condições, tipos de vistoria
- `ApiResponseParserTest` — envelope `{ sucesso, dados }`, erros JSON
- `ApiOneCheckRepositoryIntegrationTest` — login com/sem MFA, erro 401

### Instrumentados

- `AgendaListBuilderInstrumentedTest` — seções Pendentes / Enviados
- `SubmissionLogStoreInstrumentedTest` — log local de envios
- `ImageCompressorInstrumentedTest` — redução de tamanho de foto
- `UserFacingErrorsInstrumentedTest` — mensagens amigáveis

### Aceitação (UI)

- `LoginActivityAcceptanceTest` — campos e botão de entrada visíveis

## CI no GitHub

O workflow `.github/workflows/android-tests.yml` roda automaticamente:

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`

Testes instrumentados ficam para execução local (exigem emulador).

## Próximos testes sugeridos

- Espresso: fluxo Agenda → Checklist → Revisão (com API mockada)
- Testes E2E contra ambiente de staging (opcional)
- Testes de performance de upload de foto
- Snapshot/visual dos badges de status na agenda

## Convenções

- Factories em `testutil/TestFixtures.kt`
- Nome de teste: `acao_condicao_resultadoEsperado`
- Preferir testes de **comportamento real** (merge, login, seções) em vez de asserts triviais
