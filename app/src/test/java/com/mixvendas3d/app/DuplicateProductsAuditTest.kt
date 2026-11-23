package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.ProductDuplicateDetector
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.ui.screens.SystemUpdateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Cobertura integral dos cenários da Parte 2 (Seção 13):
 * Ferramenta de auditoria de produtos duplicados dentro de "Atualizar sistema",
 * incluindo exclusão manual segura, proteção de vendas vinculadas e teste de escala.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class DuplicateProductsAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun app(): MixVendasApplication = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpCleanDb() {
        runBlocking(Dispatchers.IO) {
            app().database.clearAllTables()
        }
    }

    @After
    fun tearDownCleanDb() {
        runBlocking(Dispatchers.IO) {
            app().database.clearAllTables()
        }
    }

    private fun newSystemUpdateViewModel(): SystemUpdateViewModel {
        val application = app()
        return SystemUpdateViewModel(
            repository = application.systemUpdateRepository,
            generateBackup = { JsonBackupExporter.export(application.database) },
            catalogRepository = application.catalogRepository,
            ioDispatcher = Dispatchers.IO,
            computationDispatcher = Dispatchers.Default,
        )
    }

    private fun awaitCondition(timeoutMs: Long = 8_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condição não satisfeita dentro de ${timeoutMs}ms.", predicate())
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForTagGone(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun scenario01_emptyCatalogOrNoDuplicates_returnsCleanReport() {
        val vm = newSystemUpdateViewModel()
        vm.analyzeDuplicateProducts()

        awaitCondition { vm.uiState.value.duplicateAudit.hasCompletedAnalysis }
        val report = vm.uiState.value.duplicateAudit.report!!
        assertEquals(0, report.totalProductsAnalyzed)
        assertTrue(report.exactDuplicateGroups.isEmpty())
        assertEquals(0, report.exactDuplicateRecordsCount)
        assertTrue(report.similarProductGroups.isEmpty())
        assertFalse(report.hasFindings)
    }

    @Test
    fun scenario02_03_09_exactDuplicatesTwoCopiesThreeCopiesAndReorderedParticipants() {
        val repo = app().catalogRepository
        val vm = newSystemUpdateViewModel()
        runBlocking(Dispatchers.IO) {
            val ana = repo.createPerson("Ana")
            val bruno = repo.createPerson("Bruno")
            val cat = repo.createProductCategory("MINIATURAS", null)

            // Grupo com 2 cópias exatas (ordem de participantes invertida + acento/espaço diferente)
            repo.createProduct(
                name = "DRAGÃO 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(6_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cat.id,
            )
            repo.createProduct(
                name = "dragao   3d",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
                categoryId = cat.id,
            )

            // Grupo com 3 cópias exatas
            repeat(3) {
                repo.createProduct(
                    name = "CHAVEIRO ESTRELA",
                    price = MoneyCents(2_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(2_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
            }
        }

        vm.analyzeDuplicateProducts()
        awaitCondition { vm.uiState.value.duplicateAudit.hasCompletedAnalysis }
        val report = vm.uiState.value.duplicateAudit.report!!

        assertEquals(5, report.totalProductsAnalyzed)
        assertEquals(2, report.exactDuplicateGroups.size)
        assertEquals(5, report.exactDuplicateRecordsCount)
        assertTrue(report.similarProductGroups.isEmpty())

        val starGroup = report.exactDuplicateGroups.first { it.displayName.contains("ESTRELA") }
        val dragonGroup = report.exactDuplicateGroups.first { it.displayName.contains("DRAG") }
        assertEquals(3, starGroup.copyCount)
        assertEquals(2, dragonGroup.copyCount)
    }

    @Test
    fun scenario04_05_06_07_08_similarByCategoryOrSplit_ignoresDiffPrice_includesInactive() {
        val repo = app().catalogRepository
        val vm = newSystemUpdateViewModel()
        runBlocking(Dispatchers.IO) {
            val ana = repo.createPerson("Ana")
            val bruno = repo.createPerson("Bruno")
            val catA = repo.createProductCategory("CAT A", null)
            val catB = repo.createProductCategory("CAT B", null)

            // Semelhantes por categoria diferente (um deles INATIVO!)
            repo.createProduct(
                name = "VASO MODERNO",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = catA.id,
            )
            repo.createProduct(
                name = "VASO MODERNO",
                price = MoneyCents(8_000L),
                active = false,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = catB.id,
            )

            // Semelhantes por divisão diferente
            repo.createProduct(
                name = "SUPORTE FONE",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(5_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            repo.createProduct(
                name = "SUPORTE FONE",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(7_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            // Mesmo nome, PREÇO DIFERENTE -> NÃO é duplicado nem semelhante
            repo.createProduct(
                name = "PORTA CANETA",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            repo.createProduct(
                name = "PORTA CANETA",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(4_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            // Produto único em outra categoria
            repo.createProduct(
                name = "ITEM EXCLUSIVO",
                price = MoneyCents(12_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(bruno.id, MoneyCents(12_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = catA.id,
            )
        }

        vm.analyzeDuplicateProducts()
        awaitCondition { vm.uiState.value.duplicateAudit.hasCompletedAnalysis }
        val report = vm.uiState.value.duplicateAudit.report!!

        assertEquals(7, report.totalProductsAnalyzed)
        assertTrue(report.exactDuplicateGroups.isEmpty())
        assertEquals(2, report.similarProductGroups.size)

        val vasoGroup = report.similarProductGroups.first { it.displayName == "VASO MODERNO" }
        assertTrue(vasoGroup.entries.any { !it.product.active })
        assertTrue(vasoGroup.differenceSummaries.any { it.contains("Categoria diferente") })

        val suporteGroup = report.similarProductGroups.first { it.displayName == "SUPORTE FONE" }
        assertTrue(suporteGroup.differenceSummaries.any { it.contains("Valores individuais") })

        // PORTA CANETA (preço diferente) e ITEM EXCLUSIVO não aparecem nos achados
        assertFalse(report.similarProductGroups.any { it.displayName == "PORTA CANETA" })
        assertFalse(report.similarProductGroups.any { it.displayName == "ITEM EXCLUSIVO" })
    }

    @Test
    fun scenario10_11_12_14_manualDeleteCancelConfirmRecalculateAndReadOnlyRepeatedRuns() {
        val repo = app().catalogRepository
        val vm = newSystemUpdateViewModel()
        val products = runBlocking(Dispatchers.IO) {
            val ana = repo.createPerson("Ana")
            val p1 = repo.createProduct(
                name = "BONECO TESTE",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val p2 = repo.createProduct(
                name = "BONECO TESTE",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val p3 = repo.createProduct(
                name = "BONECO TESTE",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            listOf(p1, p2, p3)
        }

        // Executa repetidamente — estritamente somente leitura (Cenário 14)
        vm.analyzeDuplicateProducts()
        awaitCondition { vm.uiState.value.duplicateAudit.hasCompletedAnalysis }
        vm.analyzeDuplicateProducts()
        awaitCondition { !vm.uiState.value.duplicateAudit.isAnalyzing }
        assertEquals(3, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })

        // Seleciona o segundo registro e CANCELA (Cenário 10 e 11)
        val entryToDelete = vm.uiState.value.duplicateAudit.report!!
            .exactDuplicateGroups.first().entries.first { it.product.id == products[1].id }
        vm.requestDeleteAuditedProduct(entryToDelete)
        assertEquals(products[1].id, vm.uiState.value.duplicateAudit.pendingDeleteTarget?.product?.id)
        vm.dismissDeleteAuditedProduct()
        assertNull(vm.uiState.value.duplicateAudit.pendingDeleteTarget)
        assertEquals(3, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })

        // Confirma exclusão apenas do registro escolhido (3 -> 2 cópias) (Cenário 12)
        vm.requestDeleteAuditedProduct(entryToDelete)
        vm.confirmDeleteAuditedProduct()
        awaitCondition { !vm.uiState.value.duplicateAudit.isDeleting && vm.uiState.value.duplicateAudit.pendingDeleteTarget == null }

        val remainingIdsAfterFirstDelete = runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().map { it.id }.toSet() }
        assertEquals(setOf(products[0].id, products[2].id), remainingIdsAfterFirstDelete)
        assertEquals(1, vm.uiState.value.duplicateAudit.report!!.exactDuplicateGroups.size)
        assertEquals(2, vm.uiState.value.duplicateAudit.report!!.exactDuplicateGroups.first().copyCount)

        // Exclui mais uma cópia (2 -> 1, grupo de duplicados exatos desaparece!)
        val secondEntryToDelete = vm.uiState.value.duplicateAudit.report!!
            .exactDuplicateGroups.first().entries.first { it.product.id == products[2].id }
        vm.requestDeleteAuditedProduct(secondEntryToDelete)
        vm.confirmDeleteAuditedProduct()
        awaitCondition { !vm.uiState.value.duplicateAudit.isDeleting && vm.uiState.value.duplicateAudit.pendingDeleteTarget == null }

        assertEquals(listOf(products[0].id), runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().map { it.id } })
        assertTrue(vm.uiState.value.duplicateAudit.report!!.exactDuplicateGroups.isEmpty())
        assertEquals(0, vm.uiState.value.duplicateAudit.report!!.exactDuplicateRecordsCount)
    }

    @Test
    fun scenario13_linkedSalesProductBlockedAndSalesHistoryPreserved() {
        val repo = app().catalogRepository
        val saleRepo = app().saleRepository
        val vm = newSystemUpdateViewModel()

        val (linkedProduct, unlinkedProduct, savedSaleId) = runBlocking(Dispatchers.IO) {
            val ana = repo.createPerson("Ana")
            val p1 = repo.createProduct(
                name = "MINIATURA RARIDADE",
                price = MoneyCents(15_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(15_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val p2 = repo.createProduct(
                name = "MINIATURA RARIDADE",
                price = MoneyCents(15_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(15_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val calc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product = p1, quantity = 1)),
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "Venda histórica protegida",
                ),
            )
            val savedSale = saleRepo.saveSale(calc)
            Triple(p1, p2, savedSale.id)
        }

        vm.analyzeDuplicateProducts()
        awaitCondition { vm.uiState.value.duplicateAudit.hasCompletedAnalysis }

        val entries = vm.uiState.value.duplicateAudit.report!!.exactDuplicateGroups.first().entries
        val linkedEntry = entries.first { it.product.id == linkedProduct.id }
        val unlinkedEntry = entries.first { it.product.id == unlinkedProduct.id }

        assertEquals(1, linkedEntry.linkedSalesCount)
        assertFalse(linkedEntry.canDelete)
        assertEquals(0, unlinkedEntry.linkedSalesCount)
        assertTrue(unlinkedEntry.canDelete)

        // Tentar excluir o produto vinculado é bloqueado sem abrir confirmação
        vm.requestDeleteAuditedProduct(linkedEntry)
        assertNull(vm.uiState.value.duplicateAudit.pendingDeleteTarget)
        assertTrue(vm.uiState.value.duplicateAudit.feedbackIsError)

        // Excluir a cópia sem vendas funciona e preserva o produto vinculado + histórico da venda
        vm.requestDeleteAuditedProduct(unlinkedEntry)
        vm.confirmDeleteAuditedProduct()
        awaitCondition { !vm.uiState.value.duplicateAudit.isDeleting && vm.uiState.value.duplicateAudit.pendingDeleteTarget == null }

        val remainingProducts = runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot() }
        assertEquals(listOf(linkedProduct.id), remainingProducts.map { it.id })

        val preservedSale = runBlocking(Dispatchers.IO) { saleRepo.getSaleById(savedSaleId) }
        assertNotNull(preservedSale)
        assertEquals(15_000L, preservedSale!!.calculation.finalAmount.rawCents)
    }

    @Test
    fun scenario15_catalogChangedWhileResultsOpen_flagsStalenessCleanly() {
        val repo = app().catalogRepository
        val vm = newSystemUpdateViewModel()
        val ana = runBlocking(Dispatchers.IO) { repo.createPerson("Ana") }

        vm.analyzeDuplicateProducts()
        awaitCondition { vm.uiState.value.duplicateAudit.hasCompletedAnalysis }
        assertFalse(vm.uiState.value.duplicateAudit.catalogChangedSinceAnalysis)

        // Insere novo produto enquanto janela está aberta
        runBlocking(Dispatchers.IO) {
            repo.createProduct(
                name = "PRODUTO NOVO",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }

        awaitCondition { vm.uiState.value.duplicateAudit.catalogChangedSinceAnalysis }
        assertTrue(vm.uiState.value.duplicateAudit.catalogChangedSinceAnalysis)
    }

    @Test
    fun scenario16_thousandsOfProductsPerformanceAndLinearComplexity() {
        val totalCount = 3_000
        val products = ArrayList<Product>(totalCount)
        for (i in 0 until 2_800) {
            products.add(
                Product(
                    id = "unique-$i",
                    name = "PRODUTO UNICO $i",
                    price = MoneyCents(1_000L + (i * 10L)),
                    active = i % 5 != 0,
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                    participants = listOf(
                        ProductParticipantSplit("p1", "ANA", 0, MoneyCents(1_000L + (i * 10L))),
                    ),
                    createdAtEpochMs = 1_700_000_000_000L + i,
                    updatedAtEpochMs = 1_700_000_000_000L + i,
                ),
            )
        }
        // 50 grupos de 2 duplicados exatos = 100 produtos
        for (g in 0 until 50) {
            repeat(2) { copy ->
                products.add(
                    Product(
                        id = "exact-$g-$copy",
                        name = if (copy == 0) "EXATO $g" else "  exato   $g ",
                        price = MoneyCents(5_000L),
                        active = copy == 0,
                        feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                        feeOwnerPersonId = null,
                        participants = listOf(
                            ProductParticipantSplit("p1", "ANA", 0, MoneyCents(2_000L)),
                            ProductParticipantSplit("p2", "BRUNO", 1, MoneyCents(3_000L)),
                        ),
                        createdAtEpochMs = 1_700_000_100_000L + g * 10L + copy,
                        updatedAtEpochMs = 1_700_000_100_000L + g * 10L + copy,
                    ),
                )
            }
        }
        // 50 grupos de 2 produtos semelhantes = 100 produtos
        for (s in 0 until 50) {
            products.add(
                Product(
                    id = "sim-$s-a",
                    name = "SEMELHANTE $s",
                    price = MoneyCents(6_000L),
                    active = true,
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                    participants = listOf(ProductParticipantSplit("p1", "ANA", 0, MoneyCents(6_000L))),
                    createdAtEpochMs = 1_700_000_200_000L + s * 2L,
                    updatedAtEpochMs = 1_700_000_200_000L + s * 2L,
                ),
            )
            products.add(
                Product(
                    id = "sim-$s-b",
                    name = "SEMELHANTE $s",
                    price = MoneyCents(6_000L),
                    active = true,
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                    participants = listOf(ProductParticipantSplit("p2", "BRUNO", 1, MoneyCents(6_000L))),
                    createdAtEpochMs = 1_700_000_200_000L + s * 2L + 1,
                    updatedAtEpochMs = 1_700_000_200_000L + s * 2L + 1,
                ),
            )
        }

        val startMs = System.currentTimeMillis()
        val report = ProductDuplicateDetector.auditCatalog(
            products = products,
            categoriesById = emptyMap(),
            salesCountByProductId = emptyMap(),
        )
        val elapsedMs = System.currentTimeMillis() - startMs

        assertEquals(totalCount, report.totalProductsAnalyzed)
        assertEquals(50, report.exactDuplicateGroups.size)
        assertEquals(100, report.exactDuplicateRecordsCount)
        assertEquals(50, report.similarProductGroups.size)
        assertTrue("Auditoria de 3.000 produtos demorou ${elapsedMs}ms (esperado < 2000ms).", elapsedMs < 2_000L)
    }

    @Test
    fun uiEndToEnd_updateSystemScreenDuplicateAuditAndManualSafeDelete() {
        val application = app()
        val (linkedProd, freeProd) = runBlocking(Dispatchers.IO) {
            val ana = application.catalogRepository.createPerson("Ana")
            val p1 = application.catalogRepository.createProduct(
                name = "DRAGÃO DUPLO",
                price = MoneyCents(9_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(9_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val p2 = application.catalogRepository.createProduct(
                name = "DRAGÃO DUPLO",
                price = MoneyCents(9_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(9_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val saleCalc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product = p1, quantity = 1)),
                    paymentMethod = SalePaymentMethod.PIX,
                ),
            )
            application.saleRepository.saveSale(saleCalc)
            p1 to p2
        }

        // Navega para Mais -> Atualizar sistema
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("mais_item_update_system")
        composeRule.onNodeWithTag("mais_item_update_system").performScrollTo().performClick()
        waitForTag("screen_mais_detail_update_system")

        // Verifica que recursos existentes de Atualizar Sistema continuam presentes
        composeRule.onNodeWithTag("update_system_analyze_button").assertIsDisplayed()

        // Rola e clica em VERIFICAR PRODUTOS DUPLICADOS
        composeRule.onNodeWithTag("verify_duplicate_products_button").performScrollTo().performClick()

        // Abre janela de resultados com o grupo exato
        waitForTag("duplicate_audit_results_dialog")
        waitForTag("duplicate_audit_exact_section_header")

        // Verifica produto vinculado com botão desabilitado e produto livre com botão habilitado
        composeRule.onNodeWithTag("duplicate_audit_results_list")
            .performScrollToNode(hasTestTag("audit_delete_product_button_${linkedProd.id}"))
        composeRule.onNodeWithTag("audit_delete_product_button_${linkedProd.id}").assertIsNotEnabled()

        composeRule.onNodeWithTag("duplicate_audit_results_list")
            .performScrollToNode(hasTestTag("audit_delete_product_button_${freeProd.id}"))
        composeRule.onNodeWithTag("audit_delete_product_button_${freeProd.id}")
            .assertIsEnabled()
            .performClick()

        // Diálogo de confirmação individual de exclusão
        waitForTag("confirm_delete_audited_product_dialog")
        composeRule.onNodeWithText("Confirmar exclusão do produto").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_audited_product_button").performClick()

        waitForTagGone("confirm_delete_audited_product_dialog")
        // Após excluir a cópia livre, grupo desaparece e estado vazio aparece
        waitForTag("duplicate_audit_empty_state")

        val remaining = runBlocking(Dispatchers.IO) { application.catalogRepository.getAllProductsSnapshot() }
        assertEquals(listOf(linkedProd.id), remaining.map { it.id })
        val salesCount = runBlocking(Dispatchers.IO) { application.saleRepository.observeSales().first().size }
        assertEquals(1, salesCount)
    }
}
