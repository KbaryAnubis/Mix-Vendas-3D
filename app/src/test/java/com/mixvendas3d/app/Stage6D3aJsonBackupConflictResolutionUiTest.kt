package com.mixvendas3d.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.DecisionValidationIssueType
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictResolution
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
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import java.io.File
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
class Stage6D3aJsonBackupConflictResolutionUiTest {

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

    private fun selectBackupFile(file: File) {
        composeRule.onNodeWithTag("select_json_backup_button")
            .performScrollTo()
            .performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(
            started.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(file)),
        )
        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_resolution_section").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertNotSelected(): SemanticsMatcher = SemanticsMatcher("not selected") { node ->
        node.config.getOrElse(SemanticsProperties.Selected) { false } == false
    }

    private data class SeededScenarioIds(
        val personAnaId: String,
        val personBrunoId: String,
        val productId: String,
        val machineId: String,
        val saleId: String,
    )

    private fun seedBaseCatalogAndSale(): SeededScenarioIds {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        return runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana Backup")
            val bruno = app.catalogRepository.createPerson("Bruno Backup")
            val prod = app.catalogRepository.createProduct(
                name = "Dragão Articulado",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Principal",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 450,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 420),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 150),
                ),
            )
            val fixedRepo = RoomSaleRepository(
                database = app.database,
                saleDao = app.database.saleDao(),
                clockMillis = { 1_763_450_000_000L },
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    finalAmountOverride = MoneyCents(19_000L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = "Venda Backup",
                ),
            )
            val savedSale = fixedRepo.saveSale(calc)
            SeededScenarioIds(
                personAnaId = ana.id,
                personBrunoId = bruno.id,
                productId = prod.id,
                machineId = machine.id,
                saleId = savedSale.id,
            )
        }
    }

    @Test
    fun `01 conflict-free backup displays empty conflicts state and enables review restore directly without Room writes`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val backupFile = newTempFile("no_conflicts.json", exported.utf8Bytes)

        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_resolution_title")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Resolver conflitos")
        composeRule.onNodeWithTag("conflict_resolution_description")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Escolha quais informações deseja manter antes de restaurar o backup.")
        composeRule.onNodeWithTag("conflicts_empty_banner")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("conflicts_pending_count").assertTextContains("0")
        composeRule.onNodeWithTag("conflicts_blocked_count").assertTextContains("0")

        composeRule.onNodeWithTag("review_restore_button")
            .performScrollTo()
            .assertIsEnabled()
            .assertTextContains("Revisar restauração")
            .performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("review_ready_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("review_ready_banner")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Pronto para revisão da restauração")

        // Nenhuma escrita no Room e nenhum botão final falso
        val peopleAfter = runBlocking(Dispatchers.IO) { app.database.personDao().getAllPeople() }
        val salesAfter = runBlocking(Dispatchers.IO) { app.database.saleDao().getAllSales() }
        assertEquals(2, peopleAfter.size)
        assertEquals(1, salesAfter.size)
        assertEquals(ids.saleId, salesAfter.first().id)
        composeRule.onAllNodesWithTag("confirm_restore_dialog_title").assertCountEquals(0)
    }

    @Test
    fun `02 person conflict displays local vs backup details, zero auto-selection, and supports KEEP_LOCAL`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ana = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Ana Original") }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        runBlocking(Dispatchers.IO) { app.catalogRepository.renamePerson(ana.id, "Ana Editada Local") }

        val unitKey = "people:${ana.id}"
        val backupFile = newTempFile("person_conflict.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_unit_card_$unitKey").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("conflict_unit_local_$unitKey").assertTextContains("Ana Editada Local", substring = true)
        composeRule.onNodeWithTag("conflict_unit_backup_$unitKey").assertTextContains("Ana Original", substring = true)
        composeRule.onNodeWithTag("conflict_unit_diff_$unitKey").assertTextContains("Nome", substring = true)

        // Nenhuma opção pré-selecionada
        val keepBtn = composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL")
        val backupBtn = composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
        val ignoreBtn = composeRule.onNodeWithTag("conflict_choice_${unitKey}_IGNORE_IMPORT")
        keepBtn.performScrollTo().assert(assertNotSelected())
        backupBtn.performScrollTo().assert(assertNotSelected())
        ignoreBtn.performScrollTo().assert(assertNotSelected())

        composeRule.onNodeWithTag("conflicts_resolved_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()

        // Escolhe MANTER DO CELULAR
        keepBtn.performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("conflicts_pending_count").assertTextContains("0")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL").assert(isSelected())
        composeRule.onNodeWithTag("conflicts_resolved_count").assertTextContains("1")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsEnabled().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("review_ready_banner").fetchSemanticsNodes().isNotEmpty()
        }
        // Banco local permanece inalterado
        val personInDb = runBlocking(Dispatchers.IO) { app.database.personDao().getById(ana.id) }!!
        assertEquals("Ana Editada Local", personInDb.name)
    }

    @Test
    fun `03 product atomic conflict groups participants, shows BRL financial values, and applies USE_BACKUP coherently to all keys`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Modifica preço e divisão do produto localmente -> conflita cabeçalho + ambos participantes
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.updateProduct(
                productId = ids.productId,
                name = "Dragão Articulado Local",
                price = MoneyCents(15_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ids.personAnaId, MoneyCents(9_000L)),
                    ProductParticipantDraft(ids.personBrunoId, MoneyCents(6_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }

        val unitKey = "PRODUCT:${ids.productId}"
        val backupFile = newTempFile("product_conflict.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        // Apenas 1 cartão de unidade atômica para produto + participantes
        composeRule.onAllNodesWithTag("conflict_unit_card_$unitKey").assertCountEquals(1)
        composeRule.onNodeWithTag("conflict_unit_financial_warning_$unitKey")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("conflict_unit_local_$unitKey")
            .performScrollTo()
            .assertTextContains("R$ 150,00", substring = true)
            .assertTextContains("R$ 90,00", substring = true)
            .assertTextContains("R$ 60,00", substring = true)
        composeRule.onNodeWithTag("conflict_unit_backup_$unitKey")
            .performScrollTo()
            .assertTextContains("R$ 100,00", substring = true)
            .assertTextContains("R$ 60,00", substring = true)
            .assertTextContains("R$ 40,00", substring = true)

        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("1")

        // Escolhe USAR DO BACKUP -> aplica coerentemente em produto e participantes e habilita revisão
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
            .performScrollTo()
            .performClick()

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP").assert(isSelected())
        composeRule.onNodeWithTag("conflicts_resolved_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("conflicts_blocked_count").performScrollTo().assertTextContains("0")

        // Verifica que Room NÃO foi alterado (nenhuma restauração nesta etapa)
        val localProdAfter = runBlocking(Dispatchers.IO) { app.database.productDao().getWithParticipantsById(ids.productId) }!!
        assertEquals(15_000L, localProdAfter.product.priceCents)
    }

    @Test
    fun `04 machine atomic conflict displays rate percentages, financial warning, and resolves atomically via IGNORE_IMPORT`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Altera taxas da maquininha localmente (débito, crédito, bandeira VISA, parcelamento 2x)
        runBlocking(Dispatchers.IO) {
            app.machineRepository.updateMachine(
                machineId = ids.machineId,
                draft = MachineInputDraft(
                    name = "Stone Principal",
                    debitRateBps = 249,
                    creditEnabled = true,
                    creditDefaultRateBps = 520,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 490),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 210),
                ),
            )
        }

        val unitKey = "MACHINE:${ids.machineId}"
        val backupFile = newTempFile("machine_conflict.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onAllNodesWithTag("conflict_unit_card_$unitKey").assertCountEquals(1)
        composeRule.onNodeWithTag("conflict_unit_financial_warning_$unitKey")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("conflict_unit_local_$unitKey")
            .performScrollTo()
            .assertTextContains("2,49%", substring = true)
            .assertTextContains("5,20%", substring = true)
        composeRule.onNodeWithTag("conflict_unit_backup_$unitKey")
            .performScrollTo()
            .assertTextContains("1,99%", substring = true)
            .assertTextContains("4,50%", substring = true)

        composeRule.onNodeWithTag("conflict_choice_${unitKey}_IGNORE_IMPORT")
            .performScrollTo()
            .performClick()

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_IGNORE_IMPORT").assert(isSelected())
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("review_ready_banner").fetchSemanticsNodes().isNotEmpty()
        }
        val machineAfter = runBlocking(Dispatchers.IO) { app.database.machineDao().getWithRatesById(ids.machineId) }!!
        assertEquals(249, machineAfter.machine.debitRateBps)
    }

    @Test
    fun `05 sale atomic conflict shows exact historical BRL values (sale, discount, fee, net, split) and allows resolution without recalculating`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Edita a venda localmente (valor final diferente, preservando mesmo saleId)
        runBlocking(Dispatchers.IO) {
            val currentSale = app.saleRepository.getSaleById(ids.saleId)!!
            val updatedCalc = SaleFinancialEngine.calculate(
                currentSale.toEditableCalculationInput(
                    overrideFinalAmount = MoneyCents(17_500L),
                    overrideNote = "Venda Editada Local",
                ),
            )
            app.saleRepository.updateSale(ids.saleId, updatedCalc)
        }

        val unitKey = "SALE:${ids.saleId}"
        val backupFile = newTempFile("sale_conflict.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        // Unidade atômica de venda (cabeçalho + itens + participantes + totais por pessoa)
        composeRule.onAllNodesWithTag("conflict_unit_card_$unitKey").assertCountEquals(1)
        composeRule.onNodeWithTag("conflict_unit_financial_warning_$unitKey")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("conflict_unit_local_$unitKey")
            .performScrollTo()
            .assertTextContains("R$ 175,00", substring = true)
            .assertTextContains("Desconto:", substring = true)
            .assertTextContains("Tarifa:", substring = true)
            .assertTextContains("Líquido:", substring = true)
        composeRule.onNodeWithTag("conflict_unit_backup_$unitKey")
            .performScrollTo()
            .assertTextContains("R$ 190,00", substring = true)
            .assertTextContains("R$ 10,00", substring = true)

        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        val localSaleAfter = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(ids.saleId) }!!
        assertEquals(17_500L, localSaleAfter.calculation.finalAmount.rawCents)
    }

    @Test
    fun `06 normalizedName collision blocks USE_BACKUP without auto-changing decision, exposes unresolved dependencies, and keeps review disabled`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupPerson = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Carla Unica") }
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.createProduct(
                name = "Produto da Carla",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(backupPerson.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Recria no Room com ID diferente mas mesmo normalizedName ("Carla Unica")
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.catalogRepository.createPerson("Carla Unica")
        }

        val unitKey = "people:${backupPerson.id}"
        val backupFile = newTempFile("collision_with_dep.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        // Antes de decidir: 1 conflito pendente + 1+ dependência(s) pendente(s) do produto novo
        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()

        // Tentar USAR DO BACKUP -> bloqueado por colisão de normalizedName; escolha NÃO deve ser alterada automaticamente
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
            .performScrollTo()
            .performClick()

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
                    .assertIsEnabled()
                    .assert(isSelected())
                composeRule.onNodeWithTag("conflict_unit_issue_$unitKey")
                    .assertTextContains("colisão de normalizedName", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
            .assertIsEnabled()
            .assert(isSelected())
        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()

        // Trocar para KEEP_LOCAL também é bloqueado por dependência impossível (produto do backup depende do ID da pessoa pulada)
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL")
            .performScrollTo()
            .performClick()

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL")
                    .assertIsEnabled()
                    .assert(isSelected())
                composeRule.onNodeWithTag("conflict_validation_issues_banner")
                    .assertTextContains("sem remapeamento", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL")
            .assertIsEnabled()
            .assert(isSelected())
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `07 normalizedName collision without dependent backup records resolves cleanly with KEEP_LOCAL after USE_BACKUP block`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupPerson = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Daniela Colisao") }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.catalogRepository.createPerson("Daniela Colisao")
        }

        val unitKey = "people:${backupPerson.id}"
        val backupFile = newTempFile("collision_standalone.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        // USE_BACKUP falha por colisão
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_unit_issue_$unitKey").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()

        // KEEP_LOCAL resolve sem bloqueio
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflicts_blocked_count").performScrollTo().assertTextContains("0")
    }

    @Test
    fun `08 incompatible unit decision (header USE_BACKUP vs child KEEP_LOCAL) is blocked by validator and disables review button`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale()
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        runBlocking(Dispatchers.IO) {
            app.catalogRepository.updateProduct(
                productId = ids.productId,
                name = "Dragão Divergente",
                price = MoneyCents(14_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ids.personAnaId, MoneyCents(8_400L)),
                    ProductParticipantDraft(ids.personBrunoId, MoneyCents(5_600L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }

        val vm = ImportExportViewModel.provideFactory(app.database).create(ImportExportViewModel::class.java)
        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/prod.json")) { exported.utf8Bytes }
        waitUiUntil {
            vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY &&
                vm.uiState.value.conflictSection != null
        }

        // Aplica decisão mista incompatível diretamente no contrato
        vm.applyRawConflictDecisionsForTesting(
            mapOf(
                "products:${ids.productId}" to MergeConflictResolution.USE_BACKUP,
                "productParticipants:${ids.productId}:${ids.personAnaId}" to MergeConflictResolution.KEEP_LOCAL,
                "productParticipants:${ids.productId}:${ids.personBrunoId}" to MergeConflictResolution.USE_BACKUP,
            ),
        )
        waitUiUntil {
            !vm.uiState.value.isValidatingDecisions &&
                (vm.currentValidationResultForTesting()?.incompatibleDecisionsCount ?: 0) > 0
        }

        val validation = vm.currentValidationResultForTesting()!!
        assertFalse(validation.isReadyForNextPhase)
        assertTrue(validation.incompatibleDecisionsCount > 0)
        assertTrue(
            validation.incompatibleDecisions.any {
                it.type == DecisionValidationIssueType.INCOMPATIBLE_UNIT_DECISION
            },
        )
        assertFalse(vm.uiState.value.conflictSection!!.isReadyForNextPhase)

        // Já a seleção atômica via ViewModel aplica a todas as chaves da unidade coerentemente
        vm.onSelectConflictResolution("PRODUCT:${ids.productId}", MergeConflictResolution.USE_BACKUP)
        waitUiUntil {
            !vm.uiState.value.isValidatingDecisions &&
                vm.currentValidationResultForTesting()?.isReadyForNextPhase == true
        }
        val afterAtomic = vm.currentValidationResultForTesting()!!
        assertTrue(afterAtomic.isReadyForNextPhase)
        assertEquals(0, afterAtomic.incompatibleDecisionsCount)
        val decisions = vm.currentExplicitDecisionsForTesting()
        assertEquals(MergeConflictResolution.USE_BACKUP, decisions["products:${ids.productId}"])
        assertEquals(
            MergeConflictResolution.USE_BACKUP,
            decisions["productParticipants:${ids.productId}:${ids.personAnaId}"],
        )
        assertEquals(
            MergeConflictResolution.USE_BACKUP,
            decisions["productParticipants:${ids.productId}:${ids.personBrunoId}"],
        )
    }

    @Test
    fun `09 multiple conflicts update pending count incrementally and replacing backup file discards previous decisions`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val people = runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Pessoa Um") to
                app.catalogRepository.createPerson("Pessoa Dois")
        }
        val exported1 = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Altera ambas no Room
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.renamePerson(people.first.id, "Pessoa Um Local")
            app.catalogRepository.renamePerson(people.second.id, "Pessoa Dois Local")
        }
        val exported2 = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Cria arquivo2 com nome de Pessoa Um alterado diferente do local -> apenas 1 conflito em arquivo2
        val json2 = JSONObject(exported2.jsonContent)
        val peopleArr = json2.getJSONObject("collections").getJSONArray("people")
        for (i in 0 until peopleArr.length()) {
            val obj = peopleArr.getJSONObject(i)
            if (obj.getString("id") == people.first.id) {
                obj.put("name", "Pessoa Um Arquivo 2")
            }
        }
        val file1 = newTempFile("multi_conflict_1.json", exported1.utf8Bytes)
        val file2 = newTempFile("single_conflict_2.json", json2.toString().toByteArray(Charsets.UTF_8))

        val unit1 = "people:${people.first.id}"
        val unit2 = "people:${people.second.id}"

        openImportExportScreen()
        selectBackupFile(file1)

        composeRule.onNodeWithTag("conflicts_resolved_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("2")

        // Resolve 1o conflito -> pendentes cai para 1
        composeRule.onNodeWithTag("conflict_choice_${unit1}_KEEP_LOCAL").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("conflicts_pending_count").assertTextContains("1")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflicts_resolved_count").assertTextContains("1")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()

        // Seleciona segundo arquivo -> limpa plano e decisões anteriores!
        selectBackupFile(file2)
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("conflict_unit_backup_$unit1")
                    .assertTextContains("Pessoa Um Arquivo 2", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("conflict_choice_${unit1}_KEEP_LOCAL").assert(assertNotSelected())
        composeRule.onNodeWithTag("conflicts_resolved_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("1")
        composeRule.onAllNodesWithTag("conflict_unit_card_$unit2").assertCountEquals(0)
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `10 changing local Room database after analysis invalidates stale plan and decisions and requires re-analysis`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val p1 = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Pessoa Base") }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        runBlocking(Dispatchers.IO) { app.catalogRepository.renamePerson(p1.id, "Pessoa Local") }

        val unitKey = "people:${p1.id}"
        val backupFile = newTempFile("stale_check.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        // Resolve conflito e prepara revisão
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_KEEP_LOCAL").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        // Banco local muda depois da comparação!
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Pessoa Criada Depois")
        }

        // Ao tentar avançar ("Revisar restauração"), fingerprint local diverge -> invalida tudo e exige nova análise
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_analysis_feedback_banner")
            .performScrollTo()
            .assertTextContains("O banco de dados local foi alterado", substring = true)
        composeRule.onAllNodesWithTag("conflict_resolution_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("selected_backup_card").assertCountEquals(0)
        composeRule.onAllNodesWithTag("review_ready_banner").assertCountEquals(0)
    }

    @Test
    fun `11 activity recreation preserves chosen conflict decisions in memory while JSON export keeps working`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val p1 = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Pessoa Recreate") }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        runBlocking(Dispatchers.IO) { app.catalogRepository.renamePerson(p1.id, "Pessoa Recreate Local") }

        val unitKey = "people:${p1.id}"
        val backupFile = newTempFile("recreate_decisions.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        // Recriação normal de configuração da Activity preserva decisões e plano
        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_resolution_section").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("conflict_choice_${unitKey}_USE_BACKUP")
            .performScrollTo()
            .assert(isSelected())
        composeRule.onNodeWithTag("conflicts_resolved_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsEnabled()

        // Exportação JSON existente continua operando normalmente
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()
        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val exportReq = shadowActivity.nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, exportReq.intent.action)
        val outFile = newTempFile("export_still_works.json", ByteArray(0))
        shadowActivity.receiveResult(exportReq.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))

        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)
        assertTrue(JsonBackupReader.readAndValidate(outFile.readBytes()).isValid)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `12 fresh ViewModel after process death never restores old conflict decisions and keeps state IDLE`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val freshVm = ImportExportViewModel(
                generateBackup = { error("não usado") },
                ioDispatcher = testDispatcher,
            )
            assertEquals(BackupAnalysisPhase.IDLE, freshVm.uiState.value.analysisPhase)
            assertNull(freshVm.uiState.value.conflictSection)
            assertNull(freshVm.uiState.value.analysisSummary)
            assertFalse(freshVm.uiState.value.isReviewReadyConfirmed)
            assertTrue(freshVm.currentExplicitDecisionsForTesting().isEmpty())

            // Tentar confirmar revisão sem sessão ativa não tem efeito
            freshVm.onPrepareReviewRestore()
            advanceUntilIdle()
            assertFalse(freshVm.uiState.value.isReviewReadyConfirmed)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
