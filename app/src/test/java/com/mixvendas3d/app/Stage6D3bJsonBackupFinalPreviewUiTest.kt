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
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewResult
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictResolution
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
import com.mixvendas3d.app.ui.screens.BackupPreviewFormatter
import com.mixvendas3d.app.ui.screens.BackupPreviewPhase
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import java.io.File
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
class Stage6D3bJsonBackupFinalPreviewUiTest {

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

    private fun waitUiUntil(timeoutMillis: Long = 6_000L, condition: () -> Boolean) {
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
                composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("backup_preview_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private data class SeededCatalogIds(
        val anaId: String,
        val brunoId: String,
        val productId: String,
        val machineId: String,
        val saleId: String,
    )

    private fun seedBaseCatalogAndSale(
        saleFinalCents: Long = 19_000L,
        saleClockMs: Long = 1_763_450_000_000L,
        note: String = "Venda Base",
    ): SeededCatalogIds {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        return runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana Base")
            val bruno = app.catalogRepository.createPerson("Bruno Base")
            val prod = app.catalogRepository.createProduct(
                name = "Suporte Dual 3D",
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
                    name = "Stone Preview",
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
                clockMillis = { saleClockMs },
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
            val savedSale = fixedRepo.saveSale(calc)
            SeededCatalogIds(
                anaId = ana.id,
                brunoId = bruno.id,
                productId = prod.id,
                machineId = machine.id,
                saleId = savedSale.id,
            )
        }
    }

    @Test
    fun `01 identical backup opens REVISAR RESTAURACAO with zero changes, zero Room writes, and no execute button`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale(saleFinalCents = 19_000L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        val backupFile = newTempFile("identical_preview.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()

        composeRule.onNodeWithTag("preview_restore_title")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("REVISAR RESTAURAÇÃO")
        composeRule.onNodeWithTag("preview_add_people_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_add_products_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_add_machines_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_add_sales_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_ignored_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_no_changes_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preview_fin_total_delta_final").performScrollTo().assertTextContains("R$ 0,00")
        composeRule.onNodeWithTag("preview_fin_total_delta_fee").performScrollTo().assertTextContains("R$ 0,00")
        composeRule.onNodeWithTag("preview_fin_total_delta_net").performScrollTo().assertTextContains("R$ 0,00")
        composeRule.onNodeWithTag("preview_not_executed_badge")
            .performScrollTo()
            .assertTextContains("Restauração ainda não executada.", substring = true)
        composeRule.onNodeWithTag("preview_review_before_confirm_note")
            .performScrollTo()
            .assertTextContains("Revise as alterações antes de confirmar a restauração.", substring = true)

        // Nenhum diálogo de confirmação automático ou execução ao abrir prévia
        composeRule.onAllNodesWithTag("confirm_restore_dialog_title").assertCountEquals(0)
        composeRule.onNodeWithTag("restore_backup_button").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("back_to_conflicts_button").performScrollTo().assertIsEnabled()

        // Room inalterado
        val salesAfter = runBlocking(Dispatchers.IO) { app.database.saleDao().getAllSales() }
        assertEquals(1, salesAfter.size)
        assertEquals(ids.saleId, salesAfter.first().id)
    }

    @Test
    fun `02 pending conflict and pending dependency block advancing to preview`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupPerson = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Carla Pendente") }
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.createProduct(
                name = "Produto Dependente",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(backupPerson.id, MoneyCents(4_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Gera colisão de normalizedName com outro ID -> conflito pendente + dependência pendente no produto
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.catalogRepository.createPerson("Carla Pendente")
        }

        val backupFile = newTempFile("blocked_preview.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflicts_pending_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("conflicts_pending_deps_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)

        // Mesmo tentando USE_BACKUP (que gera colisão bloqueada), o avanço continua bloqueado
        composeRule.onNodeWithTag("conflict_choice_people:${backupPerson.id}_USE_BACKUP")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_unit_issue_people:${backupPerson.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("review_restore_button").performScrollTo().assertIsNotEnabled()
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)
    }

    @Test
    fun `03 new people, products, machines, and sales with discounts and fees render exact ADD groups, financial summary, and final counts`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val backupIds = seedBaseCatalogAndSale(saleFinalCents = 19_000L, note = "Venda Backup Desconto")
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Banco local possui somente 1 pessoa e 1 venda locais distintas (que devem ser preservadas)
        val localCalc = runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            val localAna = app.catalogRepository.createPerson("Lucas Local")
            val localProd = app.catalogRepository.createProduct(
                name = "Caixa Local",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(localAna.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val fixedRepo = RoomSaleRepository(
                database = app.database,
                saleDao = app.database.saleDao(),
                clockMillis = { 1_763_460_000_000L },
            )
            val c = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(localProd, 1)),
                    finalAmountOverride = MoneyCents(10_000L),
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "Venda Somente Local",
                ),
            )
            fixedRepo.saveSale(c)
            c
        }

        // Calcula valores esperados diretamente da venda exportada (sem recálculo)
        val backupValidated = JsonBackupReader.readAndValidate(exported.utf8Bytes).validatedData!!
        val addedSaleDto = backupValidated.sales.single { it.id == backupIds.saleId }
        assertEquals(1_000L, addedSaleDto.discountTotalCents) // 200,00 -> 190,00 = desconto R$ 10,00
        assertTrue(addedSaleDto.feeTotalCents > 0L)

        val expectedLocalFinal = localCalc.finalAmount.rawCents // 10_000L
        val expectedFinalFinal = expectedLocalFinal + addedSaleDto.finalAmountCents // 29_000L
        val expectedFinalFee = localCalc.feeTotal.rawCents + addedSaleDto.feeTotalCents
        val expectedFinalNet = localCalc.netTotal.rawCents + addedSaleDto.netTotalCents

        val backupFile = newTempFile("additive_full_preview.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()

        // Contagens adicionadas e finais exatas
        composeRule.onNodeWithTag("preview_add_people_count").performScrollTo().assertTextContains("2")
        composeRule.onNodeWithTag("preview_add_products_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("preview_add_machines_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("preview_add_sales_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_final_tables_breakdown")
            .performScrollTo()
            .assertTextContains("3 pessoa(s)", substring = true)
            .assertTextContains("2 produto(s)", substring = true)
            .assertTextContains("1 maquininha(s)", substring = true)
            .assertTextContains("2 venda(s)", substring = true)

        // Preservação somente local: pessoa(1) + produto(1) + part(1) + venda(1) + item(1) + sip(1) + totalPessoa(1) = 7
        composeRule.onNodeWithTag("preview_local_only_preserved_count")
            .performScrollTo()
            .assertTextContains("7")

        // Grupos detalhados de alterações
        composeRule.onNodeWithTag("preview_group_PEOPLE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preview_group_PRODUCT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preview_group_MACHINE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preview_group_SALE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("preview_entry_ADD_${backupIds.anaId}")
            .performScrollTo()
            .assertTextContains("INCLUIR (ADD)", substring = true)
        composeRule.onNodeWithTag("preview_entry_ADD_${backupIds.machineId}")
            .performScrollTo()
            .assertTextContains("1,99%", substring = true)
        composeRule.onNodeWithTag("preview_entry_ADD_${backupIds.saleId}")
            .performScrollTo()
            .assertTextContains("Desconto aplicado: R$ 10,00", substring = true)

        // Resumo financeiro previsto exato em BRL
        composeRule.onNodeWithTag("preview_fin_local_total")
            .performScrollTo()
            .assertTextContains("R$ 100,00")
        composeRule.onNodeWithTag("preview_fin_final_total")
            .performScrollTo()
            .assertTextContains("R$ 290,00")
        composeRule.onNodeWithTag("preview_fin_final_fee")
            .performScrollTo()
            .assertTextContains(BackupPreviewFormatter.formatMoney(expectedFinalFee))
        composeRule.onNodeWithTag("preview_fin_final_net")
            .performScrollTo()
            .assertTextContains(BackupPreviewFormatter.formatMoney(expectedFinalNet))
        composeRule.onNodeWithTag("preview_fin_added_impact")
            .performScrollTo()
            .assertTextContains("+R$ 190,00", substring = true)
        composeRule.onNodeWithTag("preview_fin_total_delta_final")
            .performScrollTo()
            .assertTextContains("+R$ 190,00")

        // Proteção das vendas e histórico
        composeRule.onNodeWithTag("preview_sales_protection_card")
            .performScrollTo()
            .assertTextContains("Vendas somente locais serão preservadas.", substring = true)
            .assertTextContains("Snapshots históricos serão preservados", substring = true)

        // Nenhuma gravação no Room ocorreu durante a prévia
        val peopleAfter = runBlocking(Dispatchers.IO) { app.database.personDao().getAllPeople() }
        val salesAfter = runBlocking(Dispatchers.IO) { app.database.saleDao().getAllSales() }
        assertEquals(1, peopleAfter.size)
        assertEquals(1, salesAfter.size)
    }

    @Test
    fun `04 USE_BACKUP product and machine replacements clearly show REPLACE atomic notes without mixing local children`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale(saleFinalCents = 20_000L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Altera produto (preço/participantes) e maquininha (taxas) localmente -> ambos entram em conflito
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.updateProduct(
                productId = ids.productId,
                name = "Suporte Dual Local",
                price = MoneyCents(12_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ids.anaId, MoneyCents(12_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.machineRepository.updateMachine(
                machineId = ids.machineId,
                draft = MachineInputDraft(
                    name = "Stone Preview",
                    debitRateBps = 250,
                    creditEnabled = true,
                    creditDefaultRateBps = 499,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 480),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 200),
                ),
            )
        }

        val backupFile = newTempFile("replace_prod_machine.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_choice_PRODUCT:${ids.productId}_USE_BACKUP")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("conflict_choice_MACHINE:${ids.machineId}_USE_BACKUP")
            .performScrollTo()
            .performClick()

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        clickReviewRestoreAndWaitPreview()

        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("2")
        composeRule.onNodeWithTag("preview_add_products_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_add_machines_count").performScrollTo().assertTextContains("0")

        // Verifica que substituições NÃO aparecem como inclusão e informam conjunto único
        composeRule.onNodeWithTag("preview_entry_REPLACE_${ids.productId}")
            .performScrollTo()
            .assertTextContains("SUBSTITUIR (REPLACE)", substring = true)
            .assertTextContains("participante(s) serão substituídos conjuntamente", substring = true)
        composeRule.onNodeWithTag("preview_entry_REPLACE_${ids.machineId}")
            .performScrollTo()
            .assertTextContains("SUBSTITUIR (REPLACE)", substring = true)
            .assertTextContains("taxas relacionadas", substring = true)
            .assertTextContains("substituídas conjuntamente", substring = true)

        // Participantes locais do Room permanecem intocados (nenhuma escrita)
        val prodAfter = runBlocking(Dispatchers.IO) {
            app.database.productDao().getWithParticipantsById(ids.productId)
        }!!
        assertEquals(12_000L, prodAfter.product.priceCents)
        assertEquals(1, prodAfter.participants.size)
    }

    @Test
    fun `05 replacing higher local sale with lower backup sale shows atomic REPLACE notice and negative financial deltas`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        // Backup tem venda de R$ 150,00 (com desconto)
        val ids = seedBaseCatalogAndSale(saleFinalCents = 15_000L, note = "Backup 150")
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        // Local é editado para R$ 200,00 (maior que o backup!) -> substituição trará delta NEGATIVO
        val localEditedCalc = runBlocking(Dispatchers.IO) {
            val currentSale = app.saleRepository.getSaleById(ids.saleId)!!
            val updated = SaleFinancialEngine.calculate(
                currentSale.toEditableCalculationInput(
                    overrideFinalAmount = MoneyCents(20_000L),
                    overrideNote = "Local 200",
                ),
            )
            app.saleRepository.updateSale(ids.saleId, updated)
            updated
        }

        val backupDto = JsonBackupReader.readAndValidate(exported.utf8Bytes).validatedData!!
            .sales.single { it.id == ids.saleId }
        val expectedFinalDeltaCents = backupDto.finalAmountCents - localEditedCalc.finalAmount.rawCents // -5_000L
        val expectedNetDeltaCents = backupDto.netTotalCents - localEditedCalc.netTotal.rawCents
        assertTrue(expectedFinalDeltaCents == -5_000L)
        assertTrue(expectedNetDeltaCents < 0L)

        val backupFile = newTempFile("replace_sale_negative_delta.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        composeRule.onNodeWithTag("conflict_choice_SALE:${ids.saleId}_USE_BACKUP")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        clickReviewRestoreAndWaitPreview()

        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("preview_entry_REPLACE_${ids.saleId}")
            .performScrollTo()
            .assertTextContains("SUBSTITUIR (REPLACE)", substring = true)
            .assertTextContains("itens", substring = true)
            .assertTextContains("participantes", substring = true)
            .assertTextContains("totais históricos", substring = true)
            .assertTextContains("substituídos conjuntamente", substring = true)

        // Diferença negativa com sinal "-" explícito em BRL
        composeRule.onNodeWithTag("preview_fin_local_total").performScrollTo().assertTextContains("R$ 200,00")
        composeRule.onNodeWithTag("preview_fin_final_total").performScrollTo().assertTextContains("R$ 150,00")
        composeRule.onNodeWithTag("preview_fin_replaced_impact")
            .performScrollTo()
            .assertTextContains("-R$ 50,00", substring = true)
        composeRule.onNodeWithTag("preview_fin_total_delta_final")
            .performScrollTo()
            .assertTextContains("-R$ 50,00")
        composeRule.onNodeWithTag("preview_fin_total_delta_net")
            .performScrollTo()
            .assertTextContains(BackupPreviewFormatter.formatSignedMoney(expectedNetDeltaCents))

        // Banco local permanece com R$ 200,00
        val dbSaleAfter = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(ids.saleId) }!!
        assertEquals(20_000L, dbSaleAfter.calculation.finalAmount.rawCents)
    }

    @Test
    fun `06 KEEP_LOCAL and IGNORE_IMPORT keep local data unchanged and record zero REPLACE entries`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val people = runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Pessoa Keep") to
                app.catalogRepository.createPerson("Pessoa Ignore")
        }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        runBlocking(Dispatchers.IO) {
            app.catalogRepository.renamePerson(people.first.id, "Pessoa Keep Local")
            app.catalogRepository.renamePerson(people.second.id, "Pessoa Ignore Local")
        }

        val backupFile = newTempFile("keep_and_ignore_preview.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        val firstChoiceTag = "conflict_choice_people:${people.first.id}_KEEP_LOCAL"
        val secondChoiceTag = "conflict_choice_people:${people.second.id}_IGNORE_IMPORT"

        composeRule.onNodeWithTag(firstChoiceTag)
            .performScrollTo()
            .performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag(firstChoiceTag).assert(isSelected())
                composeRule.onNodeWithTag(secondChoiceTag).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(secondChoiceTag)
            .performScrollTo()
            .performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        clickReviewRestoreAndWaitPreview()

        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_kept_count").performScrollTo().assertTextContains("2")
        composeRule.onNodeWithTag("preview_ignored_count").performScrollTo().assertTextContains("2")
        composeRule.onNodeWithTag("preview_final_total_count").performScrollTo().assertTextContains("2")
        composeRule.onNodeWithTag("preview_no_changes_banner").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `07 back to conflicts preserves decisions and changing decision invalidates old preview and updates new one`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val ids = seedBaseCatalogAndSale(saleFinalCents = 16_000L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        runBlocking(Dispatchers.IO) {
            val current = app.saleRepository.getSaleById(ids.saleId)!!
            app.saleRepository.updateSale(
                ids.saleId,
                SaleFinancialEngine.calculate(
                    current.toEditableCalculationInput(overrideFinalAmount = MoneyCents(20_000L)),
                ),
            )
        }

        val backupFile = newTempFile("back_and_change_decision.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)

        val keepChoiceTag = "conflict_choice_SALE:${ids.saleId}_KEEP_LOCAL"
        val backupChoiceTag = "conflict_choice_SALE:${ids.saleId}_USE_BACKUP"

        // 1) Decide KEEP_LOCAL e gera prévia
        composeRule.onNodeWithTag(keepChoiceTag).performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        clickReviewRestoreAndWaitPreview()
        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("0")
        composeRule.onNodeWithTag("preview_fin_total_delta_final").performScrollTo().assertTextContains("R$ 0,00")

        // 2) Volta aos conflitos -> preserva decisão KEEP_LOCAL
        composeRule.onNodeWithTag("back_to_conflicts_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("conflict_resolution_section").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)
        composeRule.onNodeWithTag(keepChoiceTag).performScrollTo().assert(isSelected())

        // 3) Altera decisão para USE_BACKUP -> exige nova revisão e gera prévia atualizada (-R$ 40,00)
        composeRule.onNodeWithTag(backupChoiceTag).performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag(backupChoiceTag).assert(isSelected())
                composeRule.onNodeWithTag("review_restore_button").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)

        clickReviewRestoreAndWaitPreview()
        composeRule.onNodeWithTag("preview_replace_count").performScrollTo().assertTextContains("1")
        composeRule.onNodeWithTag("preview_fin_total_delta_final").performScrollTo().assertTextContains("-R$ 40,00")
    }

    @Test
    fun `08 local Room change while on preview invalidates preview and demands fresh analysis`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedBaseCatalogAndSale(saleFinalCents = 18_000L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        val backupFile = newTempFile("room_mutation_invalidation.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()

        composeRule.onNodeWithTag("preview_restore_section").assertIsDisplayed()

        // Modifica Room localmente enquanto a prévia estava visível
        runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Pessoa Inserida Durante Revisão")
        }

        // Ao tocar em Voltar aos conflitos, consistência local é verificada e invalida plano/prévia
        composeRule.onNodeWithTag("back_to_conflicts_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_analysis_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_analysis_feedback_banner")
            .performScrollTo()
            .assertTextContains("O banco de dados local foi alterado", substring = true)
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("conflict_resolution_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("selected_backup_card").assertCountEquals(0)
    }

    @Test
    fun `09 selecting a new backup file discards prior preview completely`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val pBase = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Base Comum") }
        val file1Bytes = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database).utf8Bytes }
        runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Nova No Arquivo 2") }
        val file2Bytes = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database).utf8Bytes }

        // Deixa Room igual ao arquivo 1 para que arquivo 1 tenha 0 novas pessoas e arquivo 2 tenha +1
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
            app.database.personDao().insert(
                com.mixvendas3d.app.data.local.entity.PersonEntity(
                    id = pBase.id,
                    name = "Base Comum",
                    normalizedName = "base comum",
                    colorIndex = pBase.colorIndex,
                    createdAtEpochMs = pBase.createdAtEpochMs,
                    updatedAtEpochMs = pBase.updatedAtEpochMs,
                ),
            )
        }

        val file1 = newTempFile("prev_file_1.json", file1Bytes)
        val file2 = newTempFile("prev_file_2.json", file2Bytes)

        openImportExportScreen()
        selectBackupFile(file1)
        clickReviewRestoreAndWaitPreview()
        composeRule.onNodeWithTag("preview_add_people_count").performScrollTo().assertTextContains("0")

        // Troca de arquivo enquanto está na prévia -> prévia antiga é eliminada imediatamente
        selectBackupFile(file2)
        composeRule.onAllNodesWithTag("preview_restore_section").assertCountEquals(0)
        composeRule.onNodeWithTag("conflict_resolution_section").performScrollTo().assertIsDisplayed()

        // Nova prévia reflete exclusivamente o arquivo 2 (+1 pessoa)
        clickReviewRestoreAndWaitPreview()
        composeRule.onNodeWithTag("preview_add_people_count").performScrollTo().assertTextContains("1")
    }

    @Test
    fun `10 unsafe preview isSafeToApply false sets INVALID phase and never shows preview as ready`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedBaseCatalogAndSale(saleFinalCents = 19_000L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        val readResult = JsonBackupReader.readAndValidate(exported.utf8Bytes)
        val validated = readResult.validatedData!!

        val vm = ImportExportViewModel(
            generateBackup = { JsonBackupExporter.export(app.database) },
            compareWithDatabase = { _ ->
                val snap = JsonBackupExporter.captureConsistentSnapshot(app.database)
                val comp = JsonBackupComparator.compareSnapshots(validated, snap)
                val fp = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snap)
                val plan = JsonBackupMergePlanner.planFromSnapshots(validated, snap, comp)
                val initial = JsonBackupMergeDecisionValidator.validateFromSnapshot(plan, validated, snap)
                val details = BackupConflictFormatter.buildUnitVisualDetails(validated, snap, plan)
                BackupComparisonOutcome(comp, fp, plan, initial, details)
            },
            validateMergeDecisions = { plan, backup, decisions ->
                JsonBackupMergeDecisionValidator.validate(plan, backup, app.database, decisions)
            },
            buildMergePreview = { backup, plan, _, _ ->
                // Simula retorno de prévia insegura (isSafeToApply = false) mesmo com banco consistente
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
                    issues = listOf(
                        PreviewValidationIssue(
                            type = PreviewIssueType.FINANCIAL_VALIDATION_ERROR,
                            message = "Erro financeiro simulado na prévia final.",
                        ),
                    ),
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
        )

        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/unsafe_preview.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }

        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.INVALID }

        assertEquals(BackupPreviewPhase.INVALID, vm.uiState.value.previewPhase)
        assertNull(vm.uiState.value.previewUi)
        assertNull(vm.currentPreviewResultForTesting())
        assertFalse(vm.uiState.value.isReviewReadyConfirmed)
        assertTrue(vm.uiState.value.previewErrorMessages.any { it.contains("Erro financeiro simulado") })
    }

    @Test
    fun `11 buildMergePreview exception sets VALIDATION_ERROR and changing decision clears previous preview cleanly`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val p1 = runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Pessoa Erro") }
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }
        runBlocking(Dispatchers.IO) { app.catalogRepository.renamePerson(p1.id, "Pessoa Erro Local") }

        var shouldThrow = true
        val vm = ImportExportViewModel(
            generateBackup = { JsonBackupExporter.export(app.database) },
            compareWithDatabase = { rr ->
                val validated = rr.validatedData!!
                val snap = JsonBackupExporter.captureConsistentSnapshot(app.database)
                val comp = JsonBackupComparator.compareSnapshots(validated, snap)
                val fp = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snap)
                val plan = JsonBackupMergePlanner.planFromSnapshots(validated, snap, comp)
                val initial = JsonBackupMergeDecisionValidator.validateFromSnapshot(plan, validated, snap)
                val details = BackupConflictFormatter.buildUnitVisualDetails(validated, snap, plan)
                BackupComparisonOutcome(comp, fp, plan, initial, details)
            },
            validateMergeDecisions = { plan, backup, decisions ->
                JsonBackupMergeDecisionValidator.validate(plan, backup, app.database, decisions)
            },
            buildMergePreview = { backup, plan, decisions, validation ->
                if (shouldThrow) {
                    throw IllegalStateException("Falha simulada ao montar prévia.")
                }
                JsonBackupMergePreviewBuilder.buildPreview(backup, plan, decisions, validation, app.database)
            },
        )

        vm.requestSelectBackupJson()
        vm.onImportFileSelected(Uri.parse("content://test/throw_preview.json")) { exported.utf8Bytes }
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY }

        vm.onSelectConflictResolution("people:${p1.id}", MergeConflictResolution.KEEP_LOCAL)
        waitUiUntil { vm.uiState.value.conflictSection?.isReadyForNextPhase == true }

        // Falha -> VALIDATION_ERROR
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.VALIDATION_ERROR }
        assertNull(vm.uiState.value.previewUi)
        assertNull(vm.currentPreviewResultForTesting())
        assertTrue(vm.uiState.value.previewErrorMessages.any { it.contains("Falha simulada") })

        // Sucesso após resolver falha -> AVAILABLE
        shouldThrow = false
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        assertNotNull(vm.uiState.value.previewUi)
        assertNotNull(vm.currentPreviewResultForTesting())

        // Alterar decisão invalida prévia anterior
        vm.onSelectConflictResolution("people:${p1.id}", MergeConflictResolution.USE_BACKUP)
        waitUiUntil { !vm.uiState.value.isValidatingDecisions }
        assertEquals(BackupPreviewPhase.IDLE, vm.uiState.value.previewPhase)
        assertNull(vm.uiState.value.previewUi)
        assertNull(vm.currentPreviewResultForTesting())
        assertFalse(vm.uiState.value.isReviewReadyConfirmed)

        // Alterar banco local + verifyLocalDatabaseConsistency -> LOCAL_STATE_CHANGED
        vm.onPrepareReviewRestore()
        waitUiUntil { vm.uiState.value.previewPhase == BackupPreviewPhase.AVAILABLE }
        runBlocking(Dispatchers.IO) { app.catalogRepository.createPerson("Nova Pessoa Local") }
        vm.verifyLocalDatabaseConsistency()
        waitUiUntil { vm.uiState.value.analysisPhase == BackupAnalysisPhase.ERROR }
        assertEquals(BackupAnalysisErrorType.LOCAL_STATE_CHANGED, vm.uiState.value.analysisError?.type)
        assertEquals(BackupPreviewPhase.LOCAL_STATE_CHANGED, vm.uiState.value.previewPhase)
        assertNull(vm.uiState.value.previewUi)
        assertNull(vm.currentPreviewResultForTesting())
    }

    @Test
    fun `12 activity recreation keeps preview visible while JSON export continues working and fresh ViewModel resets to IDLE`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        seedBaseCatalogAndSale(saleFinalCents = 17_500L)
        val exported = runBlocking(Dispatchers.IO) { JsonBackupExporter.export(app.database) }

        val backupFile = newTempFile("recreate_preview.json", exported.utf8Bytes)
        openImportExportScreen()
        selectBackupFile(backupFile)
        clickReviewRestoreAndWaitPreview()

        composeRule.onNodeWithTag("preview_restore_title").performScrollTo().assertIsDisplayed()

        // Recriação de Activity mantém estado em memória do ViewModel
        composeRule.activityRule.scenario.recreate()
        waitUiUntil {
            composeRule.onAllNodesWithTag("preview_restore_section").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("preview_fin_final_total").performScrollTo().assertTextContains("R$ 175,00")

        // Exportação JSON existente continua operando normalmente
        composeRule.onNodeWithTag("export_json_backup_button").performScrollTo().performClick()
        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val exportReq = shadowActivity.nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, exportReq.intent.action)
        val outFile = newTempFile("export_after_preview.json", ByteArray(0))
        shadowActivity.receiveResult(exportReq.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(outFile)))
        waitUiUntil {
            composeRule.onAllNodesWithTag("backup_export_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("backup_export_feedback_banner")
            .assertTextContains("Backup JSON salvo com sucesso.", substring = true)

        // Novo ViewModel após encerramento de processo volta limpo em IDLE
        val freshVm = ImportExportViewModel(generateBackup = { JsonBackupExporter.export(app.database) })
        assertEquals(BackupPreviewPhase.IDLE, freshVm.uiState.value.previewPhase)
        assertNull(freshVm.uiState.value.previewUi)
        assertNull(freshVm.currentPreviewResultForTesting())
        assertFalse(freshVm.uiState.value.isReviewReadyConfirmed)
    }
}
