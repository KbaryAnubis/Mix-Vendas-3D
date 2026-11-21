package com.mixvendas3d.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.BackupComparisonResult
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.JsonBackupResult
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
import com.mixvendas3d.app.ui.screens.BackupExportPhase
import com.mixvendas3d.app.ui.screens.BackupFileTooLargeException
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import com.mixvendas3d.app.ui.screens.readBoundedBackupBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
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
class Stage6D2JsonBackupAnalysisUiAndSafTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun newTempFile(name: String, bytes: ByteArray): File {
        val file = File(composeRule.activity.cacheDir, name)
        file.delete()
        file.writeBytes(bytes)
        tempFiles.add(file)
        return file
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
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

    private data class RoomSnapshotCounts(
        val preferences: Int,
        val people: Int,
        val products: Int,
        val productParticipants: Int,
        val machines: Int,
        val machineBrandRates: Int,
        val machineInstallmentRates: Int,
        val sales: Int,
        val saleItems: Int,
        val saleItemParticipants: Int,
        val salePersonTotals: Int,
    )

    private fun captureRoomCounts(app: MixVendasApplication): RoomSnapshotCounts = runBlocking(Dispatchers.IO) {
        val db = app.database
        RoomSnapshotCounts(
            preferences = db.preferenceDao().getAllPreferences().size,
            people = db.personDao().getAllPeople().size,
            products = db.productDao().getAllProducts().size,
            productParticipants = db.productDao().getAllProductParticipants().size,
            machines = db.machineDao().getAllMachines().size,
            machineBrandRates = db.machineDao().getAllBrandRates().size,
            machineInstallmentRates = db.machineDao().getAllInstallmentRates().size,
            sales = db.saleDao().getAllSales().size,
            saleItems = db.saleDao().getAllSaleItems().size,
            saleItemParticipants = db.saleDao().getAllSaleItemParticipants().size,
            salePersonTotals = db.saleDao().getAllSalePersonTotals().size,
        )
    }

    private fun seedFullDatabase(): String {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        return runBlocking(Dispatchers.IO) {
            app.database.preferenceDao().upsert(
                PreferenceEntity(
                    key = "backup_analysis_pref",
                    value = "v19_roxo",
                    updatedAtEpochMs = 1_763_400_000_000L,
                ),
            )
            val ana = app.catalogRepository.createPerson("Ana Análise")
            val bruno = app.catalogRepository.createPerson("Bruno Análise")
            val prod = app.catalogRepository.createProduct(
                name = "Vaso 3D Geométrico",
                price = MoneyCents(12_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_200L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_800L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Análise",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 450,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 420),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 150),
                ),
            )
            val fixedSaleRepo = RoomSaleRepository(
                database = app.database,
                saleDao = app.database.saleDao(),
                clockMillis = { 1_763_401_234_567L },
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    finalAmountOverride = MoneyCents(22_000L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = "Venda para análise 6D-2",
                ),
            )
            fixedSaleRepo.saveSale(calc).id
        }
    }

    private fun triggerSelectAndDeliverFile(file: File) {
        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started.intent.action)
        val mimeTypes = started.intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty()
        assertTrue("EXTRA_MIME_TYPES deve conter application/json: $mimeTypes", "application/json" in mimeTypes)

        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(file)),
        )
    }

    private fun triggerSelectAndDeliverUri(uri: Uri) {
        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(uri),
        )
    }

    @Test
    fun `01 import export screen preserves export section and displays select backup json button and description`() {
        openImportExportScreen()

        composeRule.onNodeWithTag("backup_restore_title")
            .assertIsDisplayed()
            .assertTextContains("Backup e restauração")
        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("Exportar backup JSON")

        composeRule.onNodeWithTag("backup_select_description")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Selecione um arquivo JSON para analisar os dados antes de restaurar.")
        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("Selecionar backup JSON")

        composeRule.onAllNodesWithTag("restore_backup_button").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [26])
    fun `02 openDocument contract creates compatible SAF intent on Android minSdk 26`() {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val contract = ActivityResultContracts.OpenDocument()
        val intent = contract.createIntent(
            context,
            arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"),
        )
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("*/*", intent.type)
        val extraMimes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList().orEmpty()
        assertTrue("application/json" in extraMimes)
    }

    @Test
    fun `03 selecting valid backup vs empty Room shows backup summary and all new records without modifying Room`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedFullDatabase()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val expectedTotal = exported.totalRecordsCount
        runBlocking(Dispatchers.IO) { app.database.clearAllTables() }

        val countsBefore = captureRoomCounts(app)
        assertEquals(0, countsBefore.people)
        assertEquals(0, countsBefore.sales)

        val backupFile = newTempFile("valid_full_backup.json", exported.utf8Bytes)
        openImportExportScreen()
        triggerSelectAndDeliverFile(backupFile)

        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("selected_backup_card").assertExists()
        composeRule.onNodeWithTag("selected_backup_card_title").assertTextContains("Backup selecionado")
        composeRule.onNodeWithTag("backup_summary_format_version")
            .assertTextContains("v1 · MIX_VENDAS_3D_BACKUP (Room v7)")
        composeRule.onNodeWithTag("backup_summary_people_count").assertTextContains("2")
        composeRule.onNodeWithTag("backup_summary_products_count").assertTextContains("1")
        composeRule.onNodeWithTag("backup_summary_machines_count").assertTextContains("1")
        composeRule.onNodeWithTag("backup_summary_sales_count").assertTextContains("1")
        composeRule.onNodeWithTag("backup_summary_sale_items_count").assertTextContains("1")

        composeRule.onNodeWithTag("comparison_result_card_title").assertTextContains("Resultado da comparação")
        composeRule.onNodeWithTag("comparison_count_new").assertTextContains(expectedTotal.toString())
        composeRule.onNodeWithTag("comparison_count_identical").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_conflicting").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_local_only").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_relational_risks").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_status_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Análise concluída. Nenhum conflito encontrado.")
        composeRule.onNodeWithTag("comparison_readonly_note").assertExists()

        // Não deve haver botão de restauração nesta etapa
        composeRule.onAllNodesWithTag("restore_backup_button").assertCountEquals(0)

        // Room permanece 100% intacto (vazio)
        assertEquals(countsBefore, captureRoomCounts(app))
    }

    @Test
    fun `04 selecting valid empty backup displays zero counts and no conflicts`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val emptyExport = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val backupFile = newTempFile("empty_valid_backup.json", emptyExport.utf8Bytes)

        openImportExportScreen()
        triggerSelectAndDeliverFile(backupFile)

        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("backup_summary_people_count").assertTextContains("0")
        composeRule.onNodeWithTag("backup_summary_sales_count").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_new").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_identical").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_conflicting").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_local_only").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_status_banner")
            .performScrollTo()
            .assertTextContains("Análise concluída. Nenhum conflito encontrado.")
    }

    @Test
    fun `05 selecting backup identical to local Room reports identical records and zero conflicts`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedFullDatabase()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val countsBefore = captureRoomCounts(app)
        val backupFile = newTempFile("identical_backup.json", exported.utf8Bytes)

        openImportExportScreen()
        triggerSelectAndDeliverFile(backupFile)

        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("comparison_count_new").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_identical")
            .assertTextContains(exported.totalRecordsCount.toString())
        composeRule.onNodeWithTag("comparison_count_conflicting").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_local_only").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_relational_risks").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_status_banner")
            .performScrollTo()
            .assertTextContains("Análise concluída. Nenhum conflito encontrado.")
        assertEquals(countsBefore, captureRoomCounts(app))
    }

    @Test
    fun `06 selecting backup with new, identical, conflicting, and local-only records displays exact summary and conflict warning`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val people = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Pessoa Idêntica")
            val p2 = app.catalogRepository.createPerson("Pessoa Conflito Original")
            val p3 = app.catalogRepository.createPerson("Pessoa Nova Backup")
            Triple(p1, p2, p3)
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Altera o banco local após o backup:
        // - p1 permanece idêntico
        // - p2 ganha nome diferente -> conflitante
        // - p3 é removida -> passa a existir somente no backup (novo)
        // - p4 é criada -> existe somente no celular (localOnly)
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.renamePerson(people.second.id, "Pessoa Conflito Local")
            app.database.personDao().deleteById(people.third.id)
            app.catalogRepository.createPerson("Pessoa Somente Local")
        }
        val countsBefore = captureRoomCounts(app)
        val backupFile = newTempFile("mixed_diff_backup.json", exported.utf8Bytes)

        openImportExportScreen()
        triggerSelectAndDeliverFile(backupFile)

        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("comparison_count_new").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_count_identical").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_count_conflicting").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_count_local_only").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_status_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Existem diferenças que precisarão ser resolvidas antes da restauração.")
        assertEquals(countsBefore, captureRoomCounts(app))
    }

    @Test
    fun `07 selecting backup with unique name collision reports relational risk and resolution warning`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Ana Colisão")
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Limpa e recria com mesmo nome (novo ID gerado) -> colisão de normalizedName
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.catalogRepository.createPerson("Ana Colisão")
        }
        val countsBefore = captureRoomCounts(app)
        val backupFile = newTempFile("name_collision_backup.json", exported.utf8Bytes)

        openImportExportScreen()
        triggerSelectAndDeliverFile(backupFile)

        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("comparison_count_new").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_count_conflicting").assertTextContains("0")
        composeRule.onNodeWithTag("comparison_count_local_only").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_count_relational_risks").assertTextContains("1")
        composeRule.onNodeWithTag("comparison_status_banner")
            .performScrollTo()
            .assertTextContains("Existem diferenças que precisarão ser resolvidas antes da restauração.")
        assertEquals(countsBefore, captureRoomCounts(app))
    }

    @Test
    fun `08 invalid, incompatible, inconsistent, and unreadable inputs display friendly errors, clear prior summary, and recover on retry`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedFullDatabase()
        val validExport = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val validFile = newTempFile("base_valid.json", validExport.utf8Bytes)

        openImportExportScreen()

        // 1. Primeiro carrega um backup válido para exibir os cards
        triggerSelectAndDeliverFile(validFile)
        waitUiUntil {
            composeRule.onAllNodesWithTag("selected_backup_card").fetchSemanticsNodes().isNotEmpty()
        }

        // 2. JSON inválido / corrompido -> limpa cards anteriores e exibe mensagem amigável
        val invalidFile = newTempFile("broken.json", "{invalid-json-content".toByteArray(Charsets.UTF_8))
        triggerSelectAndDeliverFile(invalidFile)
        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_analysis_feedback_banner")
            .assertTextContains("Arquivo de backup inválido.", substring = true)
        composeRule.onAllNodesWithTag("selected_backup_card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("comparison_result_card").assertCountEquals(0)

        // 3. Versão incompatível
        val incompatibleJson = JSONObject(validExport.jsonContent)
            .put("backupSchemaVersion", 99)
            .toString()
            .toByteArray(Charsets.UTF_8)
        val versionFile = newTempFile("incompatible_version.json", incompatibleJson)
        triggerSelectAndDeliverFile(versionFile)
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("backup_analysis_feedback_banner")
                    .assertTextContains("Versão de backup incompatível.", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onAllNodesWithTag("comparison_result_card").assertCountEquals(0)

        // 4. Backup com contagem/registros inconsistentes
        val rootInconsistent = JSONObject(validExport.jsonContent)
        rootInconsistent.getJSONObject("counts").put("people", 999)
        val inconsistentFile = newTempFile(
            "inconsistent_counts.json",
            rootInconsistent.toString().toByteArray(Charsets.UTF_8),
        )
        triggerSelectAndDeliverFile(inconsistentFile)
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("backup_analysis_feedback_banner")
                    .assertTextContains("Backup contém registros inconsistentes.", substring = true)
                true
            }.getOrDefault(false)
        }

        // 5. Falha ao abrir InputStream
        triggerSelectAndDeliverUri(Uri.parse("content://com.mixvendas3d.invalid.provider/missing.json"))
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("backup_analysis_feedback_banner")
                    .assertTextContains("Não foi possível ler o arquivo selecionado.", substring = true)
                true
            }.getOrDefault(false)
        }

        // 6. Novo arquivo válido após erros limpa o erro e volta a exibir os cards
        triggerSelectAndDeliverFile(validFile)
        waitUiUntil {
            composeRule.onAllNodesWithTag("selected_backup_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("comparison_result_card").assertExists()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `09 oversized stream above 16 MiB fails safely with FILE_TOO_LARGE and never runs comparator`() = runTest {
        val maxBytes = JsonBackupReader.DEFAULT_MAX_BACKUP_BYTES
        val oversizedStream = object : InputStream() {
            private var remaining = maxBytes + 64
            override fun read(): Int {
                return if (remaining > 0) {
                    remaining--
                    'a'.code
                } else {
                    -1
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (remaining <= 0) return -1
                val chunk = minOf(len, remaining)
                b.fill('a'.code.toByte(), off, off + chunk)
                remaining -= chunk
                return chunk
            }
        }

        var caughtTooLarge = false
        try {
            readBoundedBackupBytes(oversizedStream, maxBytes)
        } catch (_: BackupFileTooLargeException) {
            caughtTooLarge = true
        }
        assertTrue("Fluxo acima de 16 MiB deve lançar BackupFileTooLargeException", caughtTooLarge)

        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val comparatorCalls = AtomicInteger(0)
            val vm = ImportExportViewModel(
                generateBackup = { error("não usado") },
                compareWithDatabase = {
                    comparatorCalls.incrementAndGet()
                    error("Comparador não pode rodar para arquivo gigante")
                },
                ioDispatcher = testDispatcher,
            )

            vm.requestSelectBackupJson()
            vm.onImportFileSelected(Uri.parse("content://test/huge.json")) {
                readBoundedBackupBytes(
                    ByteArrayInputStream(ByteArray(maxBytes + 1) { ' '.code.toByte() }),
                    maxBytes,
                )
            }
            advanceUntilIdle()

            assertEquals(BackupAnalysisPhase.ERROR, vm.uiState.value.analysisPhase)
            assertEquals(BackupAnalysisErrorType.FILE_TOO_LARGE, vm.uiState.value.analysisError?.type)
            assertNull(vm.uiState.value.analysisSummary)
            assertEquals(0, comparatorCalls.get())
            assertFalse(vm.uiState.value.isBusy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `10 canceling OpenDocument shows cancellation banner, keeps Room untouched, and allows selecting again`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedFullDatabase()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val countsBefore = captureRoomCounts(app)
        val validFile = newTempFile("after_cancel.json", exported.utf8Bytes)

        openImportExportScreen()
        composeRule.onNodeWithTag("select_json_backup_button").performScrollTo().performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(started.intent, Activity.RESULT_CANCELED, null)

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_analysis_feedback_banner")
            .assertTextContains("Seleção de backup cancelada.", substring = true)
        composeRule.onAllNodesWithTag("selected_backup_card").assertCountEquals(0)
        composeRule.onNodeWithTag("select_json_backup_button").assertIsEnabled()

        // Nova seleção após cancelamento funciona normalmente
        triggerSelectAndDeliverFile(validFile)
        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(countsBefore, captureRoomCounts(app))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `11 viewModel enforces exact analysis phases, blocks concurrent export or import, and preserves export flow`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
            val emptyExport = JsonBackupExporter.export(app.database)
            val realReadResult = JsonBackupReader.readAndValidate(emptyExport.utf8Bytes)
            assertTrue(realReadResult.isValid)
            val dummyComparison = BackupComparisonResult(
                backupFormat = realReadResult.format!!,
                backupSchemaVersion = realReadResult.backupSchemaVersion!!,
                applicationId = realReadResult.applicationId!!,
                roomSchemaVersion = realReadResult.roomSchemaVersion!!,
                exportedAtEpochMs = realReadResult.exportedAtEpochMs!!,
                collectionSummaries = emptyList(),
                relationalRisks = emptyList(),
            )

            val readGate = CompletableDeferred<ByteArray>()
            val validateGate = CompletableDeferred<Unit>()
            val compareGate = CompletableDeferred<BackupComparisonOutcome>()
            val exportCalls = AtomicInteger(0)

            val vm = ImportExportViewModel(
                generateBackup = {
                    exportCalls.incrementAndGet()
                    JsonBackupResult(
                        jsonContent = emptyExport.jsonContent,
                        utf8Bytes = emptyExport.utf8Bytes,
                        suggestedFileName = emptyExport.suggestedFileName,
                        totalRecordsCount = emptyExport.totalRecordsCount,
                        collectionsCounts = emptyExport.collectionsCounts,
                    )
                },
                validateBackupBytes = { bytes ->
                    check(validateGate.isCompleted)
                    JsonBackupReader.readAndValidate(bytes)
                },
                compareWithDatabase = {
                    compareGate.await()
                },
                ioDispatcher = testDispatcher,
            )

            // 1. SELECTING_FILE bloqueia ações concorrentes
            vm.requestSelectBackupJson()
            assertEquals(BackupAnalysisPhase.SELECTING_FILE, vm.uiState.value.analysisPhase)
            assertTrue(vm.uiState.value.isBusy)
            vm.requestExportBackupJson()
            assertEquals(0, exportCalls.get())

            // 2. READING
            vm.onImportFileSelected(Uri.parse("content://test/ok.json")) {
                readGate.await()
            }
            testScheduler.runCurrent()
            assertEquals(BackupAnalysisPhase.READING, vm.uiState.value.analysisPhase)
            assertTrue(vm.uiState.value.isAnalysisInProgress)
            assertTrue(vm.uiState.value.isBusy)
            vm.requestExportBackupJson()
            assertEquals(0, exportCalls.get())

            // 3. Libera leitura -> VALIDATING -> COMPARING
            validateGate.complete(Unit)
            readGate.complete(emptyExport.utf8Bytes)
            testScheduler.runCurrent()
            assertEquals(BackupAnalysisPhase.COMPARING, vm.uiState.value.analysisPhase)
            assertTrue(vm.uiState.value.isBusy)

            // 4. Conclui comparação -> ANALYSIS_READY
            compareGate.complete(
                BackupComparisonOutcome(
                    comparison = dummyComparison,
                    localStateFingerprint = "fp-123",
                ),
            )
            advanceUntilIdle()
            assertEquals(BackupAnalysisPhase.ANALYSIS_READY, vm.uiState.value.analysisPhase)
            assertNotNull(vm.uiState.value.analysisSummary)
            assertFalse(vm.uiState.value.isBusy)

            // 5. Exportação continua funcionando independentemente após análise pronta
            vm.requestExportBackupJson()
            advanceUntilIdle()
            assertEquals(BackupExportPhase.AWAITING_DESTINATION, vm.uiState.value.exportPhase)
            val writtenBuffer = ByteArrayOutputStream()
            vm.onDocumentDestinationSelected(Uri.parse("content://test/export_after.json")) { _, bytes ->
                writtenBuffer.write(bytes)
            }
            advanceUntilIdle()
            assertEquals(BackupExportPhase.SUCCESS, vm.uiState.value.exportPhase)
            assertEquals(BackupAnalysisPhase.ANALYSIS_READY, vm.uiState.value.analysisPhase)
            assertNotNull(vm.uiState.value.analysisSummary)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `12 activity recreation keeps analysis summary and avoids duplicate OpenDocument launches while fresh VM rejects stale result`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedFullDatabase()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val backupFile = newTempFile("recreate_analysis.json", exported.utf8Bytes)

        openImportExportScreen()
        composeRule.onNodeWithTag("select_json_backup_button").performScrollTo().performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        composeRule.onNodeWithTag("select_json_backup_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("export_json_backup_button").assertIsNotEnabled()
        val req1 = shadowActivity.nextStartedActivityForResult
        assertNull(shadowActivity.peekNextStartedActivityForResult())

        // Recriação enquanto SAF está aberto não duplica lançamento
        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_import_export").fetchSemanticsNodes().isNotEmpty()
        }
        val recreatedShadow = Shadows.shadowOf(composeRule.activity)
        assertNull("Não deve relançar OpenDocument após recriação", recreatedShadow.peekNextStartedActivityForResult())

        recreatedShadow.receiveResult(req1.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(backupFile)))
        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }

        // Nova recriação com ANALYSIS_READY mantém os cards visíveis
        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("comparison_result_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("selected_backup_card").assertExists()

        // ViewModel novo (pós morte de processo) não aceita resultado órfão sem solicitação pendente
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                var readInvoked = false
                val freshVm = ImportExportViewModel(
                    generateBackup = { error("não usado") },
                    compareWithDatabase = {
                        BackupComparisonOutcome(
                            comparison = JsonBackupComparator.compare(it, app.database),
                            localStateFingerprint = "unused",
                        )
                    },
                    ioDispatcher = testDispatcher,
                )
                freshVm.onImportFileSelected(Uri.parse("content://test/orphan.json")) {
                    readInvoked = true
                    exported.utf8Bytes
                }
                advanceUntilIdle()
                assertFalse(readInvoked)
                assertEquals(BackupAnalysisPhase.ERROR, freshVm.uiState.value.analysisPhase)
                assertNull(freshVm.uiState.value.analysisSummary)
            } finally {
                Dispatchers.resetMain()
            }
        }
    }
}
