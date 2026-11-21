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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.JsonBackupResult
import com.mixvendas3d.app.domain.export.JsonBackupValidationException
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.BackupExportPhase
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
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
import org.junit.Assert.assertArrayEquals
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
class Stage6D1JsonBackupExportUiAndSafTest {

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

    private fun newTempJsonFile(name: String): File {
        val file = File(composeRule.activity.cacheDir, name)
        file.delete()
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
                    key = "backup_test_pref",
                    value = "v19_roxo",
                    updatedAtEpochMs = 1_763_390_000_000L,
                ),
            )
            val ana = app.catalogRepository.createPerson("Ana Backup")
            val bruno = app.catalogRepository.createPerson("Bruno Backup")
            val prod = app.catalogRepository.createProduct(
                name = "Dragão 3D Edição Especial",
                price = MoneyCents(12_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(5_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Backup",
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
                clockMillis = { 1_763_391_234_567L },
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    finalAmountOverride = MoneyCents(23_000L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = "Venda preservada no backup",
                ),
            )
            fixedSaleRepo.saveSale(calc).id
        }
    }

    @Test
    fun `01 import export screen displays official title, description, and export button`() {
        openImportExportScreen()

        composeRule.onNodeWithTag("backup_restore_card").assertIsDisplayed()
        composeRule.onNodeWithTag("backup_restore_title")
            .assertIsDisplayed()
            .assertTextContains("Backup e restauração")
        composeRule.onNodeWithTag("backup_export_description")
            .assertIsDisplayed()
            .assertTextContains(
                "Gere um arquivo JSON com os dados atuais do aplicativo.",
            )
        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTextContains("Exportar backup JSON")

        // Garante que nenhuma ação de restauração é exibida antes da análise/revisão
        composeRule.onAllNodesWithTag("restore_backup_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("Confirmar restauração", substring = true).assertCountEquals(0)
    }

    @Test
    fun `02 exporting json backup creates valid SAF document, preserves exact UTF-8 and Longs, and keeps Room untouched`() {
        val saleId = seedFullDatabase()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val countsBefore = captureRoomCounts(app)
        val salesBefore = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId) }
        assertNotNull(salesBefore)

        openImportExportScreen()

        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil {
            shadowActivity.peekNextStartedActivityForResult() != null
        }

        val started = shadowActivity.nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("application/json", started.intent.type)
        val suggestedName = started.intent.getStringExtra(Intent.EXTRA_TITLE)
        assertNotNull(suggestedName)
        assertTrue(
            "Nome sugerido inesperado: $suggestedName",
            Regex("^mix_vendas_3d_backup_\\d{8}_\\d{6}\\.json$").matches(suggestedName!!),
        )

        val outFile = newTempJsonFile(suggestedName)
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(outFile)),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)

        assertTrue(outFile.exists())
        val writtenBytes = outFile.readBytes()
        assertTrue(writtenBytes.isNotEmpty())
        // Não deve adicionar BOM nem alterar codificação UTF-8 pura
        assertEquals('{'.code.toByte(), writtenBytes[0])

        val readResult = JsonBackupReader.readAndValidate(writtenBytes)
        assertTrue("Backup JSON exportado deve ser válido: ${readResult.errors}", readResult.isValid)
        val doc = readResult.validatedData!!
        assertEquals("MIX_VENDAS_3D_BACKUP", doc.format)
        assertEquals("com.mixvendas3d.app", doc.applicationId)
        assertEquals(7, doc.roomSchemaVersion)
        assertEquals(1, doc.preferences.size)
        assertEquals(2, doc.people.size)
        assertEquals(1, doc.products.size)
        assertEquals(2, doc.productParticipants.size)
        assertEquals(1, doc.machines.size)
        assertEquals(1, doc.machineBrandRates.size)
        assertEquals(2, doc.machineInstallmentRates.size)
        assertEquals(1, doc.sales.size)
        assertEquals(1, doc.saleItems.size)
        assertEquals(2, doc.saleItemParticipants.size)
        assertEquals(2, doc.salePersonTotals.size)

        // Preservação exata de Longs no texto JSON e nos DTOs lidos
        val rawJson = String(writtenBytes, Charsets.UTF_8)
        val rootJson = JSONObject(rawJson)
        val collectionsJson = rootJson.getJSONObject("collections")
        val firstSaleJson = collectionsJson.getJSONArray("sales").getJSONObject(0)
        assertEquals("1763391234567", firstSaleJson.getString("soldAtEpochMs"))
        assertEquals("23000", firstSaleJson.getString("finalAmountCents"))
        assertEquals(1_763_391_234_567L, doc.sales.first().soldAtEpochMs)
        assertEquals(23_000L, doc.sales.first().finalAmountCents)
        assertEquals(salesBefore!!.calculation.netTotal.rawCents, doc.sales.first().netTotalCents)

        // Nenhuma escrita no Room
        val countsAfter = captureRoomCounts(app)
        val salesAfter = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId) }
        assertEquals(countsBefore, countsAfter)
        assertEquals(salesBefore, salesAfter)
    }

    @Test
    fun `03 canceling SAF shows cancellation message, writes nothing, avoids false success, and allows retry`() {
        seedFullDatabase()
        openImportExportScreen()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }

        val started = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(started.intent, Activity.RESULT_CANCELED, null)

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Exportação cancelada.", substring = true)
        composeRule.onAllNodesWithText("Backup JSON salvo com sucesso.", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("export_json_backup_button").assertIsEnabled()

        // O banner de cancelamento muda a posição do botão.
        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val retryStarted = shadowActivity.nextStartedActivityForResult
        val retryFile = newTempJsonFile("retry_after_cancel.json")
        shadowActivity.receiveResult(retryStarted.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(retryFile)))

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("backup_export_feedback_banner")
                    .assertTextContains("Backup JSON salvo com sucesso.", substring = true)
                true
            }.getOrDefault(false)
        }
        assertTrue(retryFile.exists() && retryFile.length() > 0L)
    }

    @Test
    fun `04 openOutputStream failure shows error, avoids false success, and allows retry`() {
        seedFullDatabase()
        openImportExportScreen()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }

        // Enquanto o seletor está aberto, botão fica desabilitado e não dispara novo SAF
        composeRule.onNodeWithTag("export_json_backup_button").assertIsNotEnabled()
        val reqFail = shadowActivity.nextStartedActivityForResult
        assertNull(shadowActivity.peekNextStartedActivityForResult())

        // URI sem ContentProvider -> falha ao abrir OutputStream
        shadowActivity.receiveResult(
            reqFail.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.parse("content://com.mixvendas3d.invalid.provider/fail_backup.json")),
        )

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Não foi possível salvar o backup.", substring = true)
        composeRule.onAllNodesWithText("Backup JSON salvo com sucesso.", substring = true).assertCountEquals(0)

        // Permite tentar novamente após erro
        composeRule.onNodeWithTag("export_json_backup_button")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val reqRetry = shadowActivity.nextStartedActivityForResult
        val retryFile = newTempJsonFile("retry_after_open_fail.json")
        shadowActivity.receiveResult(reqRetry.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(retryFile)))

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("backup_export_feedback_banner")
                    .assertTextContains("Backup JSON salvo com sucesso.", substring = true)
                true
            }.getOrDefault(false)
        }
        assertTrue(JsonBackupReader.readAndValidate(retryFile.readBytes()).isValid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `05 write failure during OutputStream closes stream, reports error without false success, and allows retry`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val expectedBytes = "{\"test\":true}".toByteArray(Charsets.UTF_8)
            val result = JsonBackupResult(
                jsonContent = "{\"test\":true}",
                utf8Bytes = expectedBytes,
                suggestedFileName = "mix_vendas_3d_backup_20261117_083000.json",
                totalRecordsCount = 3,
                collectionsCounts = mapOf("preferences" to 3),
            )
            val vm = ImportExportViewModel(
                generateBackup = { result },
                ioDispatcher = testDispatcher,
            )

            vm.requestExportBackupJson()
            advanceUntilIdle()
            assertEquals(BackupExportPhase.AWAITING_DESTINATION, vm.uiState.value.exportPhase)

            var streamClosed = false
            val failingStream = object : OutputStream() {
                override fun write(b: Int) {
                    throw IOException("Disco cheio simulado")
                }

                override fun close() {
                    streamClosed = true
                    super.close()
                }
            }

            vm.onDocumentDestinationSelected(Uri.parse("content://test/fail.json")) { _, bytes ->
                failingStream.use { out ->
                    out.write(bytes)
                }
            }
            advanceUntilIdle()

            assertTrue("OutputStream deve ser fechado mesmo com erro de escrita", streamClosed)
            assertEquals(BackupExportPhase.ERROR, vm.uiState.value.exportPhase)
            assertFalse(vm.uiState.value.isBusy)

            // Retry após erro de escrita deve funcionar
            vm.requestExportBackupJson()
            advanceUntilIdle()
            val outBuffer = ByteArrayOutputStream()
            vm.onDocumentDestinationSelected(Uri.parse("content://test/ok.json")) { _, bytes ->
                outBuffer.use { it.write(bytes) }
            }
            advanceUntilIdle()

            assertEquals(BackupExportPhase.SUCCESS, vm.uiState.value.exportPhase)
            assertArrayEquals(expectedBytes, outBuffer.toByteArray())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `06 preparing and writing states block concurrent exports and never report success before write completes`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val generateGate = CompletableDeferred<JsonBackupResult>()
            val writeGate = CompletableDeferred<Unit>()
            val generateCalls = AtomicInteger(0)
            val writeCalls = AtomicInteger(0)
            val sampleBytes = "{\"ok\":1}".toByteArray(Charsets.UTF_8)

            val vm = ImportExportViewModel(
                generateBackup = {
                    generateCalls.incrementAndGet()
                    generateGate.await()
                },
                ioDispatcher = testDispatcher,
            )

            // 1. Dispara exportação -> PREPARING e bloqueia chamadas concorrentes
            vm.requestExportBackupJson()
            vm.requestExportBackupJson()
            testScheduler.runCurrent()

            assertEquals(BackupExportPhase.PREPARING, vm.uiState.value.exportPhase)
            assertTrue(vm.uiState.value.isPreparing)
            assertTrue(vm.uiState.value.isBusy)
            assertEquals(1, generateCalls.get())

            // 2. Libera geração -> AWAITING_DESTINATION, ainda ocupado contra novo clique
            generateGate.complete(
                JsonBackupResult(
                    jsonContent = "{\"ok\":1}",
                    utf8Bytes = sampleBytes,
                    suggestedFileName = "mix_vendas_3d_backup_20261117_090000.json",
                    totalRecordsCount = 1,
                    collectionsCounts = emptyMap(),
                ),
            )
            advanceUntilIdle()

            assertEquals(BackupExportPhase.AWAITING_DESTINATION, vm.uiState.value.exportPhase)
            assertNotNull(vm.uiState.value.pendingDocumentRequest)
            vm.requestExportBackupJson()
            assertEquals(1, generateCalls.get())

            // 3. Entrega destino -> WRITING antes da conclusão da escrita (sem sucesso falso)
            vm.onDocumentDestinationSelected(Uri.parse("content://test/out.json")) { _, _ ->
                writeCalls.incrementAndGet()
                writeGate.await()
            }
            testScheduler.runCurrent()

            assertEquals(BackupExportPhase.WRITING, vm.uiState.value.exportPhase)
            assertTrue(vm.uiState.value.isWriting)
            assertTrue(vm.uiState.value.isBusy)
            // Tentativa concorrente durante escrita é ignorada
            vm.onDocumentDestinationSelected(Uri.parse("content://test/out2.json")) { _, _ ->
                writeCalls.incrementAndGet()
            }
            vm.requestExportBackupJson()
            assertEquals(1, writeCalls.get())
            assertEquals(1, generateCalls.get())

            // 4. Só após concluir escrita vira SUCCESS
            writeGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(BackupExportPhase.SUCCESS, vm.uiState.value.exportPhase)
            assertFalse(vm.uiState.value.isBusy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `07 missing pending backup after process death never writes bytes or reports false success`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            // Novo ViewModel recriado após morte de processo (sem backup pendente em memória)
            val freshVm = ImportExportViewModel(
                generateBackup = {
                    error("Não deve gerar backup automaticamente após morte de processo")
                },
                ioDispatcher = testDispatcher,
            )

            var writeInvoked = false
            freshVm.onDocumentDestinationSelected(Uri.parse("content://test/stale.json")) { _, _ ->
                writeInvoked = true
            }
            advanceUntilIdle()

            assertFalse(writeInvoked)
            assertEquals(BackupExportPhase.ERROR, freshVm.uiState.value.exportPhase)
            assertNull(freshVm.uiState.value.pendingDocumentRequest)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    @Config(sdk = [26])
    fun `08 createDocument contract creates compatible SAF intent on Android minSdk 26`() {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val contract = ActivityResultContracts.CreateDocument("application/json")
        val intent = contract.createIntent(context, "mix_vendas_3d_backup_20261117_093000.json")
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("application/json", intent.type)
        assertEquals(
            "mix_vendas_3d_backup_20261117_093000.json",
            intent.getStringExtra(Intent.EXTRA_TITLE),
        )
    }

    @Test
    fun `09 activity recreation while SAF is open avoids duplicate launches and writes exact backup`() {
        seedFullDatabase()
        openImportExportScreen()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val req1 = shadowActivity.nextStartedActivityForResult
        assertNull(shadowActivity.peekNextStartedActivityForResult())

        // Recriação de configuração da Activity com SAF aberto
        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_import_export").fetchSemanticsNodes().isNotEmpty()
        }
        val recreatedShadow = Shadows.shadowOf(composeRule.activity)
        assertNull("Não deve relançar o seletor SAF após recriação", recreatedShadow.peekNextStartedActivityForResult())

        val outFile = newTempJsonFile("recreated_backup.json")
        recreatedShadow.receiveResult(req1.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)
        assertTrue(JsonBackupReader.readAndValidate(outFile.readBytes()).isValid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `10 validation exception during export reports error without pending SAF request`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val vm = ImportExportViewModel(
                generateBackup = {
                    throw JsonBackupValidationException("sale-1", "Snapshot inválido")
                },
                ioDispatcher = testDispatcher,
            )

            vm.requestExportBackupJson()
            advanceUntilIdle()

            assertEquals(BackupExportPhase.ERROR, vm.uiState.value.exportPhase)
            assertNull(vm.uiState.value.pendingDocumentRequest)
            assertFalse(vm.uiState.value.isBusy)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `11 exact high-precision Long values above 2^53-1 survive export and file write unchanged`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val highPrecisionTimestamp = 9_007_199_254_740_993L
        runBlocking(Dispatchers.IO) {
            app.database.preferenceDao().upsert(
                PreferenceEntity(
                    key = "high_precision_long",
                    value = "exact",
                    updatedAtEpochMs = highPrecisionTimestamp,
                ),
            )
        }

        openImportExportScreen()
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        val outFile = newTempJsonFile("high_precision.json")
        shadowActivity.receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)

        val writtenBytes = outFile.readBytes()
        val readBack = JsonBackupReader.readAndValidate(writtenBytes)
        assertTrue(readBack.isValid)
        val pref = readBack.validatedData!!.preferences.first { it.key == "high_precision_long" }
        assertEquals(highPrecisionTimestamp, pref.updatedAtEpochMs)
    }
}
