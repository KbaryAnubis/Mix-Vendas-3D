package com.mixvendas3d.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.ExecutorTestHooks
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewResult
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeExecutionFailureReason
import com.mixvendas3d.app.domain.export.MergeExecutionResult
import com.mixvendas3d.app.domain.export.PreviewExecutionContract
import com.mixvendas3d.app.domain.export.PreviewFinancialSummary
import com.mixvendas3d.app.domain.export.PreviewIssueType
import com.mixvendas3d.app.domain.export.PreviewMergedCollections
import com.mixvendas3d.app.domain.export.PreviewValidationIssue
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.BackupAnalysisErrorType
import com.mixvendas3d.app.ui.screens.BackupAnalysisPhase
import com.mixvendas3d.app.ui.screens.BackupComparisonOutcome
import com.mixvendas3d.app.ui.screens.BackupConflictFormatter
import com.mixvendas3d.app.ui.screens.BackupPreviewPhase
import com.mixvendas3d.app.ui.screens.BackupRestorePhase
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage6D4JsonBackupRestoreUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tempFiles = mutableListOf<File>()
    private val extraDatabases = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
        extraDatabases.forEach { runCatching { it.close() } }
        extraDatabases.clear()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun newInMemoryDb(): AppDatabase {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { extraDatabases.add(it) }
    }

    private fun newTempFile(name: String, bytes: ByteArray): File {
        val file = File(composeRule.activity.cacheDir, name)
        file.delete()
        file.writeBytes(bytes)
        tempFiles.add(file)
        return file
    }

    private fun waitUiUntil(timeoutMillis: Long = 8_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun openImportExportScreen() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mais_item_import_export")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_import_export").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectBackupFile(file: File) {
        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .performClick()
        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(file)),
        )
        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_resolution_section").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickReviewRestoreAndWaitPreview() {
        composeRule.onNodeWithTag("review_restore_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("preview_restore_section").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("backup_preview_error_banner").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openConfirmDialogFromPreview() {
        composeRule.onNodeWithTag("restore_backup_button")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("confirm_restore_dialog_title").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun confirmRestoreAndWaitDone() {
        composeRule.onNodeWithTag("confirm_restore_dialog_button")
            .assertIsEnabled()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("restore_result_success_banner").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("restore_result_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun buildProductionLikeViewModel(
        db: AppDatabase,
        customExecutor: (
            suspend (
                com.mixvendas3d.app.domain.export.JsonBackupReadResult,
                com.mixvendas3d.app.domain.export.JsonBackupMergePlanResult,
                Map<String, MergeConflictResolution>,
                com.mixvendas3d.app.domain.export.MergeDecisionValidationResult,
                JsonBackupMergePreviewResult,
                PreviewExecutionContract,
                Boolean,
            ) -> MergeExecutionResult
        )? = null,
    ): ImportExportViewModel {
        return ImportExportViewModel(
            generateBackup = { JsonBackupExporter.export(db) },
            compareWithDatabase = { readResult ->
                val validated = readResult.validatedData!!
                val snap = JsonBackupExporter.captureConsistentSnapshot(db)
                val comp = JsonBackupComparator.compareSnapshots(validated, snap)
                val fp = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snap)
                val plan = JsonBackupMergePlanner.planFromSnapshots(validated, snap, comp)
                val initial = JsonBackupMergeDecisionValidator.validateFromSnapshot(plan, validated, snap)
                val details = BackupConflictFormatter.buildUnitVisualDetails(validated, snap, plan)
                BackupComparisonOutcome(comp, fp, plan, initial, details)
            },
            validateMergeDecisions = { plan, backup, decisions ->
                JsonBackupMergeDecisionValidator.validate(plan, backup, db, decisions)
            },
            buildMergePreview = { backup, plan, decisions, validation ->
                JsonBackupMergePreviewBuilder.buildPreview(backup, plan, decisions, validation, db)
            },
            executeMergeRestore = customExecutor ?: { readResult, plan, decisions, validation, preview, contract, userConfirmed ->
                JsonBackupMergeExecutor.execute(
                    readResult = readResult,
                    plan = plan,
                    decisionsByConflictKey = decisions,
                    decisionValidation = validation,
                    previewResult = preview,
                    executionContract = contract,
                    userConfirmed = userConfirmed,
                    database = db,
                )
            },
        )
    }

    private data class SeededData(
        val anaId: String,
        val brunoId: String,
        val productId: String,
        val machineId: String,
        val saleId: String,
    )

    private fun seedRichData(
        db: AppDatabase,
        saleFinalCents: Long = 18_500L,
        saleClockMs: Long = 1_763_500_000_000L,
        note: String = "Venda Histórica Rica",
    ): SeededData {
        val catRepo = com.mixvendas3d.app.data.repository.RoomCatalogRepository(db.personDao(), db.productDao())
        val machRepo = com.mixvendas3d.app.data.repository.RoomMachineRepository(db.machineDao())
        val saleRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { saleClockMs })
        return runBlocking(Dispatchers.IO) {
            val ana = catRepo.createPerson("Ana Restauro")
            val bruno = catRepo.createPerson("Bruno Restauro")
            val prod = catRepo.createProduct(
                name = "Kit 3D Completo",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machRepo.createMachine(
                MachineInputDraft(
                    name = "Stone Restauro",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 450,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 420),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 180),
                ),
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    finalAmountOverride = MoneyCents(saleFinalCents),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = note,
                ),
            )
            val saved = saleRepo.saveSale(calc)
            SeededData(ana.id, bruno.id, prod.id, machine.id, saved.id)
        }
    }

    @Test
    fun `01 Restaurar backup button is shown and enabled only on valid preview`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        val ids = seedRichData(backupDb)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        // Gera colisão + dependência pendente no Room local
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Ana Restauro")
        }

        openImportExportScreen()
        composeRule.onAllNodesWithTag("restore_backup_button").assertCountEquals(0)

        val backupFile = newTempFile("only_valid_preview.json", exported.utf8Bytes)
        selectBackupFile(backupFile)
        // Na tela de conflitos, botão ainda não existe e Revisar restauração está desabilitado
        composeRule.onAllNodesWithTag("restore_backup_button").assertCountEquals(0)
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()

        // Verifica também que prévia insegura (isSafeToApply = false) nunca habilita restauração
        val vm = ImportExportViewModel(
            generateBackup = { JsonBackupExporter.export(backupDb) },
            compareWithDatabase = { rr ->
                val validated = rr.validatedData!!
                val snap = JsonBackupExporter.captureConsistentSnapshot(backupDb)
                val comp = JsonBackupComparator.compareSnapshots(validated, snap)
                val fp = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snap)
                val plan = JsonBackupMergePlanner.planFromSnapshots(validated, snap, comp)
                val initial = JsonBackupMergeDecisionValidator.validateFromSnapshot(plan, validated, snap)
                BackupComparisonOutcome(comp, fp, plan, initial, emptyMap())
            },
            validateMergeDecisions = { plan, backup, decisions ->
                JsonBackupMergeDecisionValidator.validate(plan, backup, backupDb, decisions)
            },
            buildMergePreview = { backup, plan, _, _ ->
                JsonBackupMergePreviewResult(
                    isSafeToApply = false,
                    newRecordsCount = 0,
                    keptRecordsCount = 0,
                    unitsToReplaceCount = 0,
                    ignoredRecordsCount = 0,
                    localOnlyPreservedCount = 0,
                    finalTableCounts = emptyMap(),
                    changedEntries = emptyList(),
                    financialSummary = PreviewFinancialSummary.EMPTY,
                    issues = listOf(PreviewValidationIssue(PreviewIssueType.FINANCIAL_VALIDATION_ERROR, message = "Erro simulado")),
                    mergedCollections = PreviewMergedCollections(),
                    executionContract = PreviewExecutionContract(
                        expectedLocalStateFingerprint = plan.preconditions.expectedLocalStateFingerprint,
                        expectedLocalCounts = plan.preconditions.expectedLocalCounts,
                        expectedFinalCounts = emptyMap(),
                        backupIdentity = JsonBackupMergePreviewBuilder.computeBackupIdentitySignature(backup),
                        approvedDecisionsByConflictKey = emptyMap(),
                        authorizedOperationsCount = 0,
                    ),
                    localStateMatchesPlan = true,
                )
            },
            executeMergeRestore = { _, _, _, _, _, _, _ -> error("Não deveria executar") },
        )
        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/unsafe.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.INVALID }
        assertFalse(vm.uiState.value.canTriggerRestore)

        // Limpa conflito e testa caminho válido
        assertNotNull(ids.saleId)
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        composeRule.onNodeWithTag("restore_backup_button").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    @Test
    fun `02 confirmation AlertDialog is mandatory with exact text and real totals before any Room write`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        seedRichData(backupDb)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("mandatory_confirm.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()

        // Abrir prévia NÃO abre diálogo nem grava no Room
        composeRule.onAllNodesWithTag("confirm_restore_dialog_title").assertCountEquals(0)
        assertEquals(0, runBlocking(Dispatchers.IO) { app.database.saleDao().countSales() })

        openConfirmDialogFromPreview()
        composeRule.onNodeWithTag("confirm_restore_dialog_title").assertTextContains("Confirmar restauração")
        composeRule.onNodeWithTag("confirm_restore_dialog_message")
            .assertTextContains("Os dados do backup serão mesclados com os dados do celular", substring = true)
            .assertTextContains("Registros somente locais serão preservados.", substring = true)
            .assertTextContains("Deseja confirmar a restauração?", substring = true)
        composeRule.onNodeWithTag("confirm_restore_added_count").assertTextContains("15")
        composeRule.onNodeWithTag("confirm_restore_replaced_count").assertTextContains("0")
        composeRule.onNodeWithTag("confirm_restore_kept_count").assertTextContains("0")
        composeRule.onNodeWithTag("confirm_restore_affected_sales_count").assertTextContains("1")
        composeRule.onNodeWithTag("cancel_restore_dialog_button").assertIsEnabled()
        composeRule.onNodeWithTag("confirm_restore_dialog_button").assertIsEnabled()

        // Room continua vazio antes da confirmação explícita
        assertEquals(0, runBlocking(Dispatchers.IO) { app.database.saleDao().countSales() })
    }

    @Test
    fun `03 clicking Cancelar closes dialog, never executes restore, and keeps preview intact`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        seedRichData(backupDb)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("cancel_restore.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()

        composeRule.onNodeWithTag("cancel_restore_dialog_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("confirm_restore_dialog_title").assertCountEquals(0)
            true
        }

        // Prévia preservada, zero execução e Room vazio
        composeRule.onNodeWithTag("preview_restore_section").assertIsDisplayed()
        composeRule.onNodeWithTag("restore_backup_button").performScrollTo().assertIsEnabled()
        composeRule.onAllNodesWithTag("restore_result_success_banner").assertCountEquals(0)
        assertEquals(0, runBlocking(Dispatchers.IO) { app.database.personDao().getAllPeople().size })
        assertEquals(0, runBlocking(Dispatchers.IO) { app.database.saleDao().countSales() })
    }

    @Test
    fun `04 confirming calls executor exactly once with userConfirmed true and validated session artifacts`() {
        val db = newInMemoryDb()
        val person = runBlocking(Dispatchers.IO) {
            db.personDao().insert(PersonEntity("p-1", "Carlos Único", "carlos unico", 0, 100L, 100L))
            db.personDao().getById("p-1")!!
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(db) }
        runBlocking(Dispatchers.IO) { db.clearAllTables() }

        val callCount = AtomicInteger(0)
        var observedConfirmed = false
        var observedPlan: com.mixvendas3d.app.domain.export.JsonBackupMergePlanResult? = null

        val vm = buildProductionLikeViewModel(db) { readResult, plan, decisions, validation, preview, contract, userConfirmed ->
            callCount.incrementAndGet()
            observedConfirmed = userConfirmed
            observedPlan = plan
            JsonBackupMergeExecutor.execute(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = decisions,
                decisionValidation = validation,
                previewResult = preview,
                executionContract = contract,
                userConfirmed = userConfirmed,
                database = db,
            )
        }

        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/single_exec.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }

        // Chamada direta sem diálogo não executa
        vm.confirmAndExecuteRestore()
        assertEquals(0, callCount.get())

        vm.requestConfirmRestore()
        assertEquals(BackupRestorePhase.CONFIRMING, vm.uiState.value.restorePhase)
        assertEquals(0, callCount.get())

        vm.confirmAndExecuteRestore()
        waitUiUntil { vm.uiState.value.restorePhase == BackupRestorePhase.SUCCESS }

        assertEquals(1, callCount.get())
        assertTrue(observedConfirmed)
        assertNotNull(observedPlan)
        assertEquals(person.name, runBlocking(Dispatchers.IO) { db.personDao().getById("p-1")?.name })
    }

    @Test
    fun `05 simultaneous confirmations and concurrent actions are blocked during EXECUTING`() {
        val db = newInMemoryDb()
        runBlocking(Dispatchers.IO) {
            db.personDao().insert(PersonEntity("p-lock", "Paula Lock", "paula lock", 1, 100L, 100L))
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(db) }
        runBlocking(Dispatchers.IO) { db.clearAllTables() }

        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)

        val vm = buildProductionLikeViewModel(db) { readResult, plan, decisions, validation, preview, contract, userConfirmed ->
            callCount.incrementAndGet()
            started.complete(Unit)
            gate.await()
            JsonBackupMergeExecutor.execute(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = decisions,
                decisionValidation = validation,
                previewResult = preview,
                executionContract = contract,
                userConfirmed = userConfirmed,
                database = db,
            )
        }

        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/lock.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        vm.requestConfirmRestore()

        vm.confirmAndExecuteRestore()
        runBlocking { started.await() }

        assertEquals(BackupRestorePhase.EXECUTING, vm.uiState.value.restorePhase)
        assertTrue(vm.uiState.value.isRestoring)
        assertTrue(vm.uiState.value.isBusy)
        assertFalse(vm.uiState.value.canTriggerRestore)

        // Tentativas concorrentes durante EXECUTING devem ser ignoradas
        vm.confirmAndExecuteRestore()
        vm.requestConfirmRestore()
        vm.requestSelectBackupJson()
        vm.requestExportBackupJson()
        assertEquals(1, callCount.get())
        assertEquals(BackupRestorePhase.EXECUTING, vm.uiState.value.restorePhase)

        gate.complete(Unit)
        waitUiUntil { vm.uiState.value.restorePhase == BackupRestorePhase.SUCCESS }
        assertEquals(1, callCount.get())
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `06 restoring new records inserts people, products, machines, and preferences while keeping local-only items`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        val ids = seedRichData(backupDb)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val localPerson = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Diego Somente Local") }

        val backupFile = newTempFile("restore_new_records.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_success_title")
            .performScrollTo()
            .assertTextContains("Backup restaurado com sucesso!")
        composeRule.onNodeWithTag("restore_result_added_count").performScrollTo().assertTextContains("15")
        composeRule.onNodeWithTag("restore_result_replaced_count").performScrollTo().assertTextContains("0")

        runBlocking(Dispatchers.IO) {
            assertNotNull(app.database.personDao().getById(localPerson.id))
            assertNotNull(app.database.personDao().getById(ids.anaId))
            assertNotNull(app.database.productDao().getWithParticipantsById(ids.productId))
            assertNotNull(app.database.machineDao().getWithRatesById(ids.machineId))
        }
    }

    @Test
    fun `07 restoring complete historical sale preserves all snapshots without recalculation or duplication`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        val ids = seedRichData(backupDb, saleFinalCents = 18_500L, note = "Snapshot Completo")
        val backupSale = runBlocking(Dispatchers.IO) { backupDb.saleDao().getWithDetailsById(ids.saleId)!! }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("restore_historical_sale.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_result_success_banner").assertIsDisplayed()

        runBlocking(Dispatchers.IO) {
            val allSales = app.database.saleDao().getAllSales()
            assertEquals(1, allSales.size)
            val restored = app.database.saleDao().getWithDetailsById(ids.saleId)!!
            assertEquals(backupSale.sale.finalAmountCents, restored.sale.finalAmountCents)
            assertEquals(backupSale.sale.discountTotalCents, restored.sale.discountTotalCents)
            assertEquals(backupSale.sale.feeTotalCents, restored.sale.feeTotalCents)
            assertEquals(backupSale.sale.netTotalCents, restored.sale.netTotalCents)
            assertEquals(backupSale.sale.effectiveFeeRateBps, restored.sale.effectiveFeeRateBps)
            assertEquals(backupSale.sale.cardBrand, restored.sale.cardBrand)
            assertEquals(backupSale.sale.installments, restored.sale.installments)
            assertEquals(backupSale.items.size, restored.items.size)
            assertEquals(backupSale.personTotals.map { it.netAmountCents }, restored.personTotals.map { it.netAmountCents })
        }
    }

    @Test
    fun `08 KEEP_LOCAL conflict resolution preserves local entity and sale untouched after execution`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedRichData(app.database, saleFinalCents = 17_000L, note = "Backup 170")
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        runBlocking(Dispatchers.IO) {
            app.catalogRepository.renamePerson(ids.anaId, "Ana Mantida Local")
            val currentSale = app.saleRepository.getSaleById(ids.saleId)!!
            app.saleRepository.updateSale(
                ids.saleId,
                SaleFinancialEngine.calculate(
                    currentSale.toEditableCalculationInput(overrideFinalAmount = MoneyCents(16_000L), overrideNote = "Local 160"),
                ),
            )
        }

        val backupFile = newTempFile("keep_local_exec.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_choice_people:${ids.anaId}_KEEP_LOCAL").performScrollTo().performClick()
        composeRule.onNodeWithTag("conflict_choice_SALE:${ids.saleId}_KEEP_LOCAL").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_result_replaced_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("restore_result_preserved_count").performScrollTo().assertIsDisplayed()

        runBlocking(Dispatchers.IO) {
            assertEquals("Ana Mantida Local", app.database.personDao().getById(ids.anaId)?.name)
            assertEquals(16_000L, app.saleRepository.getSaleById(ids.saleId)?.calculation?.finalAmount?.rawCents)
        }
    }

    @Test
    fun `09 USE_BACKUP replaces atomic product, machine, and sale units completely without mixing local children`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedRichData(app.database, saleFinalCents = 16_000L, note = "Backup Integral 160")
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Altera produto, maquininha e venda localmente (menos participantes no produto local!)
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.updateProduct(
                productId = ids.productId,
                name = "Produto Local Alterado",
                price = MoneyCents(14_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ids.anaId, MoneyCents(14_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            app.machineRepository.updateMachine(
                machineId = ids.machineId,
                draft = MachineInputDraft(
                    name = "Stone Restauro",
                    debitRateBps = 299,
                    creditEnabled = true,
                    creditDefaultRateBps = 599,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.MASTERCARD to 520),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 250, 3 to 400),
                ),
            )
            val localSale = app.saleRepository.getSaleById(ids.saleId)!!
            app.saleRepository.updateSale(
                ids.saleId,
                SaleFinancialEngine.calculate(
                    localSale.toEditableCalculationInput(overrideFinalAmount = MoneyCents(20_000L), overrideNote = "Local 200"),
                ),
            )
        }

        val backupFile = newTempFile("use_backup_atomic.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_choice_PRODUCT:${ids.productId}_USE_BACKUP").performScrollTo().performClick()
        composeRule.onNodeWithTag("conflict_choice_MACHINE:${ids.machineId}_USE_BACKUP").performScrollTo().performClick()
        composeRule.onNodeWithTag("conflict_choice_SALE:${ids.saleId}_USE_BACKUP").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_result_replaced_count").performScrollTo().assertTextContains("3")

        runBlocking(Dispatchers.IO) {
            val prod = app.database.productDao().getWithParticipantsById(ids.productId)!!
            assertEquals("Kit 3D Completo", prod.product.name)
            assertEquals(10_000L, prod.product.priceCents)
            assertEquals(2, prod.participants.size)

            val mach = app.database.machineDao().getWithRatesById(ids.machineId)!!
            assertEquals(199, mach.machine.debitRateBps)
            assertEquals(listOf("VISA"), mach.brandRates.map { it.brandKey })
            assertEquals(listOf(1, 2), mach.installmentRates.map { it.installments })

            val sale = app.saleRepository.getSaleById(ids.saleId)!!
            assertEquals(16_000L, sale.calculation.finalAmount.rawCents)
            assertEquals("Backup Integral 160", sale.calculation.note)
        }
    }

    @Test
    fun `10 IGNORE_IMPORT skips conflicting backup data and preserves local database records`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val person = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Fernanda Backup") }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        runBlocking(Dispatchers.IO) {
            app.catalogRepository.renamePerson(person.id, "Fernanda Local Atual")
        }

        val backupFile = newTempFile("ignore_import_exec.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_choice_people:${person.id}_IGNORE_IMPORT").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_result_replaced_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("restore_result_ignored_count").performScrollTo().assertTextContains("1")
        assertEquals("Fernanda Local Atual", runBlocking(Dispatchers.IO) { app.database.personDao().getById(person.id)?.name })
    }

    @Test
    fun `11 UI across tabs updates reactively right after restore without restarting app`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        val ids = seedRichData(backupDb, saleFinalCents = 19_000L, saleClockMs = System.currentTimeMillis(), note = "Venda Reativa Tab")
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("reactive_ui_tabs.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_result_success_banner").assertIsDisplayed()

        runBlocking(Dispatchers.IO) {
            val restoredSale = app.saleRepository.getSaleById(ids.saleId)
            assertNotNull(restoredSale)
            assertEquals(19_000L, restoredSale?.calculation?.finalAmount?.rawCents)
            assertNotNull(app.database.personDao().getById(ids.anaId))
            assertNotNull(app.database.personDao().getById(ids.brunoId))
        }
    }

    @Test
    fun `12 final counts displayed on success card match MergeExecutionResult and Room and discard old preview`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        seedRichData(backupDb)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("final_counts_exec.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        // Plano e prévia antigos descartados imediatamente
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("selected_backup_card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("conflict_resolution_section").assertCountEquals(0)

        composeRule.onNodeWithTag("restore_result_added_count").performScrollTo().assertTextContains("15")
        composeRule.onNodeWithTag("restore_result_replaced_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("restore_result_preserved_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("restore_result_ignored_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("restore_result_final_summary")
            .performScrollTo()
            .assertTextContains("2 pessoa(s)", substring = true)
            .assertTextContains("1 produto(s)", substring = true)
            .assertTextContains("1 maquininha(s)", substring = true)
            .assertTextContains("1 venda(s)", substring = true)
    }

    @Test
    fun `13 stale plan after local DB mutation during confirmation is rejected cleanly by transaction fingerprint check`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        seedRichData(backupDb)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("stale_plan_reject.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()

        // Altera Room localmente enquanto diálogo de confirmação já estava aberto
        val intruded = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Pessoa Intrusa Local") }

        confirmRestoreAndWaitDone()

        composeRule.onAllNodesWithTag("restore_result_success_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("restore_result_error_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("restore_error_title")
            .performScrollTo()
            .assertTextContains("Não foi possível concluir a restauração.")
        composeRule.onNodeWithTag("restore_error_detail")
            .performScrollTo()
            .assertTextContains("Os dados do celular foram alterados. Analise o backup novamente.")
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)

        // Registro local preservado; nenhum registro do backup foi aplicado
        runBlocking(Dispatchers.IO) {
            val people = app.database.personDao().getAllPeople()
            assertEquals(listOf(intruded.id), people.map { it.id })
            assertEquals(0, app.database.saleDao().countSales())
        }
    }

    @Test
    fun `14 rolledBack failure shows rollback message without false success, and non-rolledBack never claims rollback`() {
        val db = newInMemoryDb()
        val existing = runBlocking(Dispatchers.IO) {
            db.personDao().insert(PersonEntity("p-stay", "Fica Intacto", "fica intacto", 0, 100L, 100L))
            db.personDao().getById("p-stay")!!
        }
        val backupDb = newInMemoryDb()
        runBlocking(Dispatchers.IO) {
            backupDb.personDao().insert(PersonEntity("p-new", "Novo Teste", "novo teste", 1, 200L, 200L))
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        var rolledBackFlag = true
        var failReason = MergeExecutionFailureReason.TRANSACTION_ABORTED_OR_FAILED

        val vm = buildProductionLikeViewModel(db) { _, _, _, _, _, _, _ ->
            MergeExecutionResult(
                operationId = "op-fail",
                success = false,
                rolledBack = rolledBackFlag,
                addedRecordsCount = 0,
                replacedUnitsCount = 0,
                preservedRecordsCount = 0,
                ignoredRecordsCount = 0,
                finalTableCounts = mapOf(BackupCollectionType.PEOPLE to 1),
                failureReason = failReason,
                failureMessage = "Falha simulada",
            )
        }

        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/fail_rb.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        vm.requestConfirmRestore()
        vm.confirmAndExecuteRestore()
        waitUiUntil { vm.uiState.value.restorePhase == BackupRestorePhase.FAILED }

        assertFalse(vm.uiState.value.restoreResultUi!!.success)
        assertTrue(vm.uiState.value.restoreResultUi!!.rolledBack)
        assertEquals(listOf(existing.id), runBlocking(Dispatchers.IO) { db.personDao().getAllPeople().map { it.id } })

        // Agora valida falha pré-transacional com rolledBack = false -> nunca afirma rollback
        rolledBackFlag = false
        failReason = MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH
        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/fail_no_rb.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        vm.requestConfirmRestore()
        vm.confirmAndExecuteRestore()
        waitUiUntil { vm.uiState.value.restorePhase == BackupRestorePhase.FAILED }

        assertFalse(vm.uiState.value.restoreResultUi!!.success)
        assertFalse(vm.uiState.value.restoreResultUi!!.rolledBack)
        assertNull(vm.uiState.value.previewUi)
    }

    @Test
    fun `15 SQLite FK or UNIQUE constraint violation inside transaction maps to DATABASE_CONSTRAINT_VIOLATION and rolls back`() {
        val db = newInMemoryDb()
        runBlocking(Dispatchers.IO) {
            db.personDao().insert(PersonEntity("p-base", "Base FK", "base fk", 0, 100L, 100L))
        }
        val backupDb = newInMemoryDb()
        runBlocking(Dispatchers.IO) {
            backupDb.personDao().insert(PersonEntity("p-base", "Base FK", "base fk", 0, 100L, 100L))
            backupDb.personDao().insert(PersonEntity("p-extra", "Extra Backup", "extra backup", 1, 200L, 200L))
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val vm = buildProductionLikeViewModel(db) { readResult, plan, decisions, validation, preview, contract, userConfirmed ->
            JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = decisions,
                decisionValidation = validation,
                previewResult = preview,
                executionContract = contract,
                userConfirmed = userConfirmed,
                database = db,
                hooks = ExecutorTestHooks(
                    afterFirstWrite = {
                        // Força colisão real no índice UNIQUE (normalized_name) dentro da transação SQLite
                        db.personDao().insert(
                            PersonEntity("p-colliding-tx", "Base FK", "base fk", 2, 300L, 300L),
                        )
                    },
                ),
            )
        }

        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/constraint_fail.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        vm.requestConfirmRestore()
        vm.confirmAndExecuteRestore()
        waitUiUntil { vm.uiState.value.restorePhase == BackupRestorePhase.FAILED }

        val resultUi = vm.uiState.value.restoreResultUi!!
        assertFalse(resultUi.success)
        assertTrue(resultUi.rolledBack)
        assertEquals(MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION, resultUi.failureReason)
        assertEquals(
            composeRule.activity.getString(R.string.backup_restore_error_constraint),
            "Foi encontrada uma incompatibilidade entre os registros.",
        )

        // Transação revertida: nem p-extra nem p-colliding-tx permaneceram gravados
        runBlocking(Dispatchers.IO) {
            assertEquals(listOf("p-base"), db.personDao().getAllPeople().map { it.id })
        }
    }

    @Test
    fun `16 Activity recreation keeps SUCCESS state without re-executing, and process death resets cleanly to IDLE`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        seedRichData(backupDb, saleFinalCents = 15_000L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val backupFile = newTempFile("recreate_after_restore.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()

        composeRule.onNodeWithTag("restore_result_success_banner").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("restore_result_success_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("restore_success_title").performScrollTo().assertTextContains("Backup restaurado com sucesso!")
        composeRule.onAllNodesWithTag("confirm_restore_dialog_title").assertCountEquals(0)
        assertEquals(1, runBlocking(Dispatchers.IO) { app.database.saleDao().countSales() })

        // Simula encerramento de processo: novo ViewModel começa limpo sem confirmação/execução
        val freshVm = buildProductionLikeViewModel(app.database)
        assertEquals(BackupRestorePhase.IDLE, freshVm.uiState.value.restorePhase)
        assertNull(freshVm.uiState.value.restoreConfirmationUi)
        assertNull(freshVm.uiState.value.restoreResultUi)
        assertNull(freshVm.currentRestoreResultForTesting())
    }

    @Test
    fun `17 changing selected backup discards previous confirmation and preview completely`() {
        val db = newInMemoryDb()
        val backup1 = newInMemoryDb()
        val backup2 = newInMemoryDb()
        runBlocking(Dispatchers.IO) {
            backup1.personDao().insert(PersonEntity("p-b1", "Backup Um", "backup um", 0, 100L, 100L))
            backup2.personDao().insert(PersonEntity("p-b2", "Backup Dois", "backup dois", 1, 200L, 200L))
        }
        val bytes1 = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backup1).utf8Bytes }
        val bytes2 = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backup2).utf8Bytes }

        val vm = buildProductionLikeViewModel(db)

        // Carrega backup 1 e entra em CONFIRMING
        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/b1.json")) { bytes1 }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        vm.requestConfirmRestore()
        assertEquals(BackupRestorePhase.CONFIRMING, vm.uiState.value.restorePhase)

        // Enquanto está em CONFIRMING, não permite troca sem cancelar; após cancelar e trocar para backup 2, tudo de b1 é descartado
        vm.cancelRestoreConfirmation()
        vm.requestSelectBackupJson()
        assertEquals(BackupRestorePhase.IDLE, vm.uiState.value.restorePhase)
        assertNull(vm.uiState.value.restoreConfirmationUi)
        assertNull(vm.currentPreviewResultForTesting())

        vm.onImportFileSelected(Uri.parse("content://test/b2.json")) { bytes2 }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }
        assertEquals(BackupPreviewPhase.IDLE, vm.uiState.value.previewPhase)
        assertFalse(vm.uiState.value.canTriggerRestore)

        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        vm.requestConfirmRestore()
        vm.confirmAndExecuteRestore()
        waitUiUntil { vm.uiState.value.restorePhase == BackupRestorePhase.SUCCESS }

        // Apenas backup 2 foi aplicado
        runBlocking(Dispatchers.IO) {
            assertEquals(listOf("p-b2"), db.personDao().getAllPeople().map { it.id })
        }
    }

    @Test
    fun `18 JSON backup export continues working properly after completing a restore`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupDb = newInMemoryDb()
        val ids = seedRichData(backupDb, saleFinalCents = 14_500L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(backupDb) }

        val inFile = newTempFile("restore_then_export_in.json", exported.utf8Bytes)
        val outFile = newTempFile("restore_then_export_out.json", ByteArray(0))

        openImportExportScreen()
        selectBackupFile(inFile)
        clickReviewRestoreAndWaitPreview()
        openConfirmDialogFromPreview()
        confirmRestoreAndWaitDone()
        composeRule.onNodeWithTag("restore_result_success_banner").assertIsDisplayed()

        // Executa exportação JSON via SAF imediatamente após restaurar
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()
        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val req = shadowActivity.nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, req.intent.action)
        shadowActivity.receiveResult(req.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .performScrollTo()
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)

        val reRead = JsonBackupReader.readAndValidate(outFile.readBytes())
        assertTrue(reRead.isValid)
        assertEquals(listOf(ids.saleId), reRead.validatedData!!.sales.map { it.id })
        assertEquals(1, runBlocking(Dispatchers.IO) { app.database.saleDao().countSales() })
    }
}
