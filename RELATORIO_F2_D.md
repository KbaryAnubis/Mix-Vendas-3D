# MIX VENDAS 3D — Relatório F2-D

## Falhas originais

A execução original registrou 402 testes, 365 aprovações, 37 falhas e zero ignorados. O inventário abaixo foi extraído dos XMLs do Gradle antes das novas execuções.

## SQLite/Room — `SQLITE_CANTOPEN` (código 14) (33)

- **Stage2B2UnitAndDbTest** — migration 2 to 3 preserves app_preferences, people, products, and participants while adding machines. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeExecuteForString(Native Method)`
- **Stage3B2B1SaleEditTest** — 06 update failure preserves edit mode, duplicate update clicks persist once, and edited sale survives db reopen. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeExecuteForString(Native Method)`
- **Stage4B2ClosingRealDataTest** — 06 viewmodel closing enforces exact cent invariants, machine vs payment parity, no preview data, and filter isolation. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeExecuteForString(Native Method)`
- **Stage7B3PersonDeletionSafetyTest** — concurrent deletion vs product linking and double deletion remain atomic and consistent. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeExecuteForString(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 05 referential integrity foreign keys and cascade deletes hold after close and reopen with zero orphans. Caminho do banco com 282 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 01 new file-backed database starts completely empty and stays empty across close and reopen. Caminho do banco com 277 caracteres. `at com.mixvendas3d.app.Stage8AOfflinePersistenceIntegrationTest$01 new file-backed database starts completely empty and stays empty across close and reopen$1.invokeSuspend(Stage8AOfflinePersistenceIntegrationTest.kt:165)` → `at com.mixvendas3d.app.Stage8AOfflinePersistenceIntegrationTest.01 new file-backed database starts completely empty and stays empty across close and reopen(Stage8AOfflinePersistenceIntegrationTest.kt:159)` → `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 03 multi-product financial sales with discounts rates brands installments and splits persist identically across close and reopen. Caminho do banco com 277 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 02 people products fee modes machines and preferences persist identically after close and reopen. Caminho do banco com 279 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 07 separate file-backed test databases remain strictly isolated across close and reopen. Caminho do banco com 282 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 04 post-sale catalog edits and person deletion never alter historical sales across close and reopen. Caminho do banco com 285 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8AOfflinePersistenceIntegrationTest** — 06 reporting aggregations period queries and viewmodel home and closing pipelines remain identical after reopen. Caminho do banco com 282 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 08 real transactional rollback after partial writes preserves all 11 tables and existing records intact. Caminho do banco com 283 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 03 complete json backup export and reader validation across all 11 collections. Caminho do banco com 279 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 01 csv export summary and detailed validate formula injection protection and exact formatting. Caminho do banco com 283 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 06 post restoration reports csv export and new json backup consistency. Caminho do banco com 269 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 04 cross database backup restore from bank a to bank b through real pipeline. Caminho do banco com 279 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 05 conflict resolution with KEEP_LOCAL, USE_BACKUP, IGNORE_IMPORT and atomic sales preservation. Caminho do banco com 280 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 02 csv export updates correctly after editing sale preserving ID and totals. Caminho do banco com 276 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **Stage8B2CsvBackupRestoreIntegrationTest** — 07 transaction failure and rollback preserves bank b untouched. Caminho do banco com 261 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **StageF2aCustomBrandsBaseTest** — 03 persists two custom brands alongside all 13 standard brands across db reopen without converting custom to OTHER. Caminho do banco com 282 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **StageF2bCustomBrandsIntegrationTest** — 06 system update evaluates, updates modified custom brand rate and blocks deleted custom brand without fallback. Caminho do banco com 293 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **StageV24b2aMachineMaxInstallmentsAndMigrationTest** — 02 explicit maxInstallments persists across close and reopen, survives partial edits, and clears only explicitly. Caminho do banco com 285 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **StageV24b2aMachineMaxInstallmentsAndMigrationTest** — 05 real migration v4 to v5 preserves all 11 tables and sets max_installments to null on legacy machines. Caminho do banco com 288 caracteres. `at com.mixvendas3d.app.StageV24b2aMachineMaxInstallmentsAndMigrationTest$05 real migration v4 to v5 preserves all 11 tables and sets max_installments to null on legacy machines$1.invokeSuspend(StageV24b2aMachineMaxInstallmentsAndMigrationTest.kt:496)` → `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 09 updated history and undo batch survive Room close and reopen. Caminho do banco com 265 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 11 missing entities brand installment negative net conflicts and undo rollback work with Compose conflict UI. Caminho do banco com 290 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 02 analyze never mutates Room tables before explicit confirmation. Caminho do banco com 265 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 07 stale fingerprint rejects apply and transactional failure rolls back completely. Caminho do banco com 284 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 05 changing product split or fee responsibility recalculates safely while preserving historical charged price. Caminho do banco com 295 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 08 undo restores exact snapshot leaves newer sales intact and blocks blind undo if edited afterwards. Caminho do banco com 291 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 06 conflicts block unsafe updates and explicit partial opt-in preserves conflicted sales untouched. Caminho do banco com 292 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 03 renaming person updates sales reports csv and json backup while keeping date qty and charged price. Caminho do banco com 289 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 01 empty database and synchronized history report zero updates and zero conflicts. Caminho do banco com 269 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`
- **SystemUpdateIntegrationTest** — 04 changing machine rate recalculates automatic card sales in cents and preserves manual override. Caminho do banco com 288 caracteres. `at org.robolectric.nativeruntime.SQLiteConnectionNatives.nativeOpen(Native Method)`

## Compose — `ComposeTimeoutException` após 5.000 ms (3)

- **Stage1RobolectricTest** — startup diagnostic screen displays wallpaper ready title and saves preference. `at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$AndroidComposeUiTestImpl.waitUntil(ComposeUiTest.android.kt:441)` → `at androidx.compose.ui.test.junit4.AndroidComposeTestRule.waitUntil(AndroidComposeTestRule.android.kt:306)` → `at com.mixvendas3d.app.Stage1RobolectricTest.startup diagnostic screen displays wallpaper ready title and saves preference(Stage1RobolectricTest.kt:143)`
- **Stage6D1JsonBackupExportUiAndSafTest** — 03 canceling SAF shows cancellation message, writes nothing, avoids false success, and allows retry. `at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$AndroidComposeUiTestImpl.waitUntil(ComposeUiTest.android.kt:441)` → `at androidx.compose.ui.test.junit4.AndroidComposeTestRule.waitUntil(AndroidComposeTestRule.android.kt:306)` → `at com.mixvendas3d.app.Stage6D1JsonBackupExportUiAndSafTest.waitUiUntil(Stage6D1JsonBackupExportUiAndSafTest.kt:95)`
- **Stage6D1JsonBackupExportUiAndSafTest** — 04 openOutputStream failure shows error, avoids false success, and allows retry. `at androidx.compose.ui.test.AndroidComposeUiTestEnvironment$AndroidComposeUiTestImpl.waitUntil(ComposeUiTest.android.kt:441)` → `at androidx.compose.ui.test.junit4.AndroidComposeTestRule.waitUntil(AndroidComposeTestRule.android.kt:306)` → `at com.mixvendas3d.app.Stage6D1JsonBackupExportUiAndSafTest.waitUiUntil(Stage6D1JsonBackupExportUiAndSafTest.kt:95)`

## Maquininhas — `AssertionError` ao localizar `machine_brand_rate_input_VISA` (1)

- **StageV24aMachinesUiTest** — machines V2-4A opens listing first, collapses sections without data loss, preserves disabled credit rates, and guards back discard. `at androidx.compose.ui.test.ActionsKt.performScrollToNode(Actions.kt:264)` → `at com.mixvendas3d.app.StageV24aMachinesUiTest.machines V2-4A opens listing first, collapses sections without data loss, preserves disabled credit rates, and guards back discard(StageV24aMachinesUiTest.kt:169)`

## Diagnóstico e correções

- **SQLite/Room e F2-B.** O SQLite nativo do Robolectric retornava `SQLITE_CANTOPEN` ao criar ou abrir bancos em disco. Nos 29 casos com caminho completo no XML, os nomes das classes e dos métodos, o identificador do sandbox e os nomes dos bancos somavam de 261 a 295 caracteres. Os demais casos chegavam a `nativeExecuteForString` durante a configuração do banco. A troca exclusiva dos nomes dos arquivos eliminou os 33 erros de abertura na execução dirigida. Na primeira suíte completa, outra fixture de reabertura ainda usava um nome longo e apresentou o mesmo erro. Foram abreviados também os nomes fixos remanescentes. `TestDatabaseNames.kt` centraliza os nomes curtos e mantém a distinção entre bancos no mesmo teste por meio de uma sequência atômica. O SQLite nativo, a persistência em arquivo, a reabertura, as transações e as migrações reais continuam em uso.
- **Versão do esquema no teste de persistência.** Após abrir o banco com nome curto, `Stage8AOfflinePersistenceIntegrationTest` passou a registrar `expected:<4> but was:<5>`. `AppDatabase` já declara a versão 5 e a migração 4→5 tem cobertura própria. O teste agora verifica a versão vigente tanto na criação quanto na reabertura. As verificações de banco vazio, chaves estrangeiras e dados persistidos continuam presentes.
- **Compose, nova tentativa de backup.** Os dois timeouts ocorreram aguardando o segundo lançamento do SAF, nas linhas 332 e 377 da versão original. O banner de cancelamento ou erro aparece antes do botão e muda sua posição. Os testes agora rolam até a posição atual, verificam a visibilidade e clicam novamente. As verificações do lançamento SAF e do arquivo produzido foram mantidas.
- **Compose, diagnóstico inicial.** A condição original mesclava rolagem, asserção e captura genérica de exceções em uma espera de cinco segundos. O teste agora aciona o botão pela ação semântica e aguarda o valor persistido com uma consulta de semântica sem rolagem no loop. A falha reapareceu na primeira execução completa, mesmo com a espera revisada. O banco em memória desse teste passou a usar um executor de consultas síncrono para eliminar a corrida entre tarefas do Room e o laço que conduz o Compose. A asserção final sobre o texto persistido continua explícita.
- **Maquininhas.** A busca pelo campo VISA falhava logo após o segundo clique na opção de crédito, feito sem reposicionar o controle após remover as seções do formulário. O teste agora rola até a opção, confirma sua visibilidade e verifica que o crédito ficou ativo antes de localizar os campos. Foram preservadas as asserções sobre os valores VISA e de parcelamento e o descarte de alterações.

O projeto usa JUnit 4, Robolectric 4.16.1 e as APIs de teste do Compose. A infraestrutura alterada se limita a nomes de bancos de teste e ao executor da fixture do diagnóstico inicial. Nenhuma configuração global do Windows, dependência, versão do SDK, código de produção, configuração de build ou recurso visual foi alterado.

## Arquivos corrigidos

Todos os arquivos Kotlin abaixo ficam em `app/src/test/java/com/mixvendas3d/app/`.

- **Nomes de bancos em disco** — `TestDatabaseNames.kt`, `Stage1RobolectricTest.kt`, `Stage2B1UnitAndDbTest.kt`, `Stage2B2UnitAndDbTest.kt`, `Stage3B1SalePersistenceTest.kt`, `Stage3B2ASalesFlowTest.kt`, `Stage3B2B1SaleEditTest.kt`, `Stage3B2B2SaleDeleteTest.kt`, `Stage4ADailySalesUiAndRoomTest.kt`, `Stage4B1HomeRealDataTest.kt`, `Stage4B2ClosingRealDataTest.kt`, `Stage7B3PersonDeletionSafetyTest.kt`, `Stage8AOfflinePersistenceIntegrationTest.kt`, `Stage8B2CsvBackupRestoreIntegrationTest.kt`, `StageF2aCustomBrandsBaseTest.kt`, `StageF2bCustomBrandsIntegrationTest.kt`, `StageV24b2aMachineMaxInstallmentsAndMigrationTest.kt` e `SystemUpdateIntegrationTest.kt`.
- **Expectativa de versão do esquema** — `Stage8AOfflinePersistenceIntegrationTest.kt`.
- **Interações e sincronização Compose** — `Stage1RobolectricTest.kt`, `Stage6D1JsonBackupExportUiAndSafTest.kt` e `StageV24aMachinesUiTest.kt`.

## Execuções de validação

| Execução | Aprovados | Reprovados | Ignorados |
|---|---:|---:|---:|
| Original | 365 | 37 | 0 |
| Dez classes afetadas pelo SQLite | 71 | 1 | 0 |
| Persistência, diagnóstico, backup e maquininhas após os primeiros ajustes | 24 | 0 | 0 |
| Primeira suíte completa com recompilação forçada | 400 | 2 | 0 |
| Diagnóstico e bancos Room após os ajustes adicionais | 12 | 0 | 0 |
| Suíte final com recompilação forçada | 402 | 0 | 0 |

A suíte final foi executada com `./gradlew.bat :app:testDebugUnitTest --rerun-tasks --console=plain`. O Gradle retornou código 0 em 6 min 58 s, com 33 tarefas executadas. O HTML confirmou 100% de aprovação. [Resultado da suíte](<C:/Users/olive/Downloads/Studio/Mix Vendas 3D/app/build/reports/tests/testDebugUnitTest/index.html>)

A execução independente de `./gradlew.bat :app:assembleDebug --console=plain` retornou código 0 em 9 s. [APK Debug](<C:/Users/olive/Downloads/Studio/Mix Vendas 3D/app/build/outputs/apk/debug/app-debug.apk>)

## Verificação específica da F2-B

Os seis testes de `StageF2bCustomBrandsIntegrationTest` passaram na execução dirigida e na suíte final. Eles cobrem validação e codificação de bandeiras, cadastro e edição em Room, cálculo financeiro, venda e histórico, CSV e backup JSON, além da atualização do sistema. O teste 06, originalmente bloqueado pelo SQLite, executou com o banco em disco e verificou tanto a aplicação da taxa alterada quanto o bloqueio da bandeira excluída. [XML da F2-B](<C:/Users/olive/Downloads/Studio/Mix Vendas 3D/app/build/test-results/testDebugUnitTest/TEST-com.mixvendas3d.app.StageF2bCustomBrandsIntegrationTest.xml>)

A suíte solicitada ficou sem falhas ou testes ignorados.

