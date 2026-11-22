package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.FinancialOverflowException
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.CustomCardBrandRate
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.components.personAccentColor
import com.mixvendas3d.app.ui.screens.MachinesViewModel
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import com.mixvendas3d.app.ui.theme.V19PersonPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Wave1RegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanUpDatabase() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun waitForTag(testTag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(testTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `01 all five ProductsDropdownField menus open without infinite height crash and select options`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (catId, subId, personId) = runBlocking(Dispatchers.IO) {
            val cat = app.catalogRepository.createProductCategory("Impressão 3D")
            val sub = app.catalogRepository.createProductCategory("Miniaturas", parentId = cat.id)
            val person = app.catalogRepository.createPerson("Marina")
            app.catalogRepository.createProduct(
                name = "Dragão 3D",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cat.id,
                subcategoryId = sub.id,
            )
            Triple(cat.id, sub.id, person.id)
        }

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")

        // 1. Filtro de categoria de Produtos
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_category_filter"))
        composeRule.onNodeWithTag("products_category_filter").performClick()
        waitForTag("products_category_filter_option_$catId")
        composeRule.onNodeWithTag("products_category_filter_option_$catId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("products_category_filter")
            .assert(hasText("Impressão 3D", substring = true))

        // Abrir editor de produto
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("new_product_button"))
        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")

        // 2. Seleção de categoria principal
        composeRule.onNodeWithTag("product_category_dropdown").performScrollTo().performClick()
        waitForTag("product_category_dropdown_option_$catId")
        composeRule.onNodeWithTag("product_category_dropdown_option_$catId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_category_dropdown")
            .assert(hasText("Impressão 3D", substring = true))

        // 3. Seleção de subcategoria
        composeRule.onNodeWithTag("product_subcategory_dropdown").performScrollTo().performClick()
        waitForTag("product_subcategory_dropdown_option_$subId")
        composeRule.onNodeWithTag("product_subcategory_dropdown_option_$subId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .assert(hasText("Miniaturas", substring = true))

        // 4. Seleção do responsável pela tarifa
        composeRule.onNodeWithTag("product_participant_check_$personId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("fee_mode_individual").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_fee_owner_dropdown").performScrollTo().performClick()
        waitForTag("fee_owner_$personId")
        composeRule.onNodeWithTag("fee_owner_$personId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_fee_owner_dropdown")
            .assert(hasText("Marina", substring = true))

        // Fechar editor de produto e abrir Gerenciar Categorias -> Nova Subcategoria
        composeRule.onNodeWithTag("cancel_product_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("new_category_button")
        composeRule.onNodeWithTag("new_category_button").performClick()
        waitForTag("radio_category_sub")
        composeRule.onNodeWithTag("radio_category_sub").performClick()
        waitForTag("category_parent_dropdown")

        // 5. Seleção da categoria pai
        composeRule.onNodeWithTag("category_parent_dropdown").performScrollTo().performClick()
        waitForTag("category_parent_dropdown_option_$catId")
        composeRule.onNodeWithTag("category_parent_dropdown_option_$catId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("category_parent_dropdown")
            .assert(hasText("Impressão 3D", substring = true))
    }

    @Test
    fun `02 MachinesViewModel preserves custom brand debit and null credit rates on edit, cancel, and dirty check`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val repo = app.machineRepository
        val aleloKey = CustomBrandKeyCodec.encodeKey("Alelo")
        val vrKey = CustomBrandKeyCodec.encodeKey("VR")

        val created = runBlocking(Dispatchers.IO) {
            repo.createMachine(
                MachineInputDraft(
                    name = "Stone Loja",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 320,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 290),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    customBrandRates = listOf(
                        CustomCardBrandInput(
                            name = "Alelo",
                            creditRateBps = 250,
                            debitRateBps = 120,
                            key = aleloKey,
                        ),
                        CustomCardBrandInput(
                            name = "VR",
                            creditRateBps = null,
                            debitRateBps = 95,
                            key = vrKey,
                        ),
                    ),
                ),
            )
        }

        val viewModel = MachinesViewModel(repo)
        val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        collectorScope.launch {
            viewModel.uiState.collect {}
        }
        try {
            viewModel.openEditMachineEditor(created)

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                viewModel.uiState.value.editor.isOpen &&
                    viewModel.uiState.value.editor.customBrandDrafts.size == 2
            }
            val loadedDrafts = viewModel.uiState.value.editor.customBrandDrafts
            val aleloDraft = loadedDrafts.first { it.persistedKey == aleloKey }
            val vrDraft = loadedDrafts.first { it.persistedKey == vrKey }
            assertEquals("2,50", aleloDraft.creditRateInput)
            assertEquals("1,20", aleloDraft.debitRateInput)
            assertEquals("", vrDraft.creditRateInput)
            assertEquals("0,95", vrDraft.debitRateInput)

            // Alterar apenas newCustomBrandDebitRateInput deve ser detectado em hasUnsavedEditorChanges
            viewModel.onNewCustomBrandDebitRateChanged("1,10")
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                viewModel.uiState.value.editor.newCustomBrandDebitRateInput == "1,10"
            }
            viewModel.handleEditorBackRequested()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                viewModel.uiState.value.editor.showDiscardConfirmation
            }
            assertTrue(viewModel.uiState.value.editor.showDiscardConfirmation)
            viewModel.confirmDiscardEditorChanges()
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !viewModel.uiState.value.editor.isOpen
            }
            assertFalse(viewModel.uiState.value.editor.isOpen)

            // Reabrir, alterar apenas o nome da maquininha e salvar -> todas as taxas personalizadas intactas
            viewModel.openEditMachineEditor(created)
            composeRule.waitUntil(timeoutMillis = 5_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                viewModel.uiState.value.editor.isOpen &&
                    viewModel.uiState.value.editor.customBrandDrafts.size == 2
            }
            viewModel.onEditorNameChanged("Stone Loja Principal")
            viewModel.saveMachine()

            composeRule.waitUntil(timeoutMillis = 5_000L) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !viewModel.uiState.value.editor.isOpen
            }
            assertFalse(viewModel.uiState.value.editor.isOpen)

            val persisted = runBlocking(Dispatchers.IO) {
                repo.observeMachines().first().first { it.id == created.id }
            }
            assertEquals("STONE LOJA PRINCIPAL", persisted.name)
            val savedAlelo = persisted.findCustomBrand(aleloKey)!!
            assertEquals(250, savedAlelo.creditRateBps)
            assertEquals(120, savedAlelo.debitRateBps)
            val savedVr = persisted.findCustomBrand(vrKey)!!
            assertNull(savedVr.creditRateBps)
            assertEquals(95, savedVr.debitRateBps)
        } finally {
            collectorScope.cancel()
        }
    }

    @Test
    fun `03 SaleFinancialEngine and persistence support debit standard, debit custom, debit fallback, credit, and legacy debit`() =
        runBlocking(Dispatchers.IO) {
            val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
            val saleRepo = app.saleRepository
            val customKey = CustomBrandKeyCodec.encodeKey("Alelo")
            val customNoDebitKey = CustomBrandKeyCodec.encodeKey("Hiper Especial")

            val product = Product(
                id = "prod_1",
                name = "Vaso 3D",
                price = MoneyCents(10_000L),
                active = true,
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                participants = listOf(
                    ProductParticipantSplit("p1", "Ana", 0, MoneyCents(10_000L)),
                ),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
            )

            val machine = Machine(
                id = "m_1",
                name = "Maquininha Completa",
                debitRateBps = 180,
                creditEnabled = true,
                creditDefaultRateBps = 350,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 310),
                brandDebitRatesBps = mapOf(CardBrand.ELO to 145),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to 100),
                createdAtEpochMs = 1L,
                updatedAtEpochMs = 1L,
                customBrandRates = listOf(
                    CustomCardBrandRate(
                        key = customKey,
                        name = "Alelo",
                        creditRateBps = 260,
                        debitRateBps = 115,
                    ),
                    CustomCardBrandRate(
                        key = customNoDebitKey,
                        name = "Hiper Especial",
                        creditRateBps = 280,
                        debitRateBps = null,
                    ),
                ),
            )

            // 1. Débito com bandeira padrão (ELO com taxa específica de débito = 1,45%)
            val debitStandard = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                    cardBrand = CardBrand.ELO,
                    cardBrandKey = CardBrand.ELO.storageKey,
                ),
            )
            assertEquals(145, debitStandard.effectiveFeeRateBps)
            assertEquals(145L, debitStandard.feeTotal.rawCents)
            assertEquals(CardBrand.ELO, debitStandard.machineSnapshot?.appliedBrand)
            assertEquals(CardBrand.ELO.storageKey, debitStandard.machineSnapshot?.resolvedAppliedBrandKey)
            assertNull(debitStandard.machineSnapshot?.appliedInstallments)
            SalePersistenceValidator.validateForPersistence(debitStandard)
            val savedDebitStandard = saleRepo.saveSale(debitStandard)
            assertEquals(CardBrand.ELO, savedDebitStandard.calculation.machineSnapshot?.appliedBrand)
            assertEquals(145, savedDebitStandard.calculation.effectiveFeeRateBps)

            // 2. Débito com bandeira personalizada (Alelo com taxa específica de débito = 1,15%)
            val debitCustom = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                    cardBrand = null,
                    cardBrandKey = customKey,
                ),
            )
            assertEquals(115, debitCustom.effectiveFeeRateBps)
            assertEquals(115L, debitCustom.feeTotal.rawCents)
            assertNull(debitCustom.machineSnapshot?.appliedBrand)
            assertEquals(customKey, debitCustom.machineSnapshot?.resolvedAppliedBrandKey)
            SalePersistenceValidator.validateForPersistence(debitCustom)
            val savedDebitCustom = saleRepo.saveSale(debitCustom)
            val reloadedCustom = saleRepo.getSaleById(savedDebitCustom.id)!!
            assertEquals(customKey, reloadedCustom.calculation.machineSnapshot?.resolvedAppliedBrandKey)
            assertEquals(115, reloadedCustom.calculation.effectiveFeeRateBps)
            // Edição a partir do snapshot preserva taxa de débito da bandeira personalizada
            val editedCustom = SaleFinancialEngine.calculate(reloadedCustom.toEditableCalculationInput())
            assertEquals(115, editedCustom.effectiveFeeRateBps)
            assertEquals(customKey, editedCustom.machineSnapshot?.resolvedAppliedBrandKey)

            // 3. Débito sem taxa específica (VISA e Hiper Especial -> fallback para 1,80%)
            val debitFallbackStandard = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    cardBrandKey = CardBrand.VISA.storageKey,
                ),
            )
            assertEquals(180, debitFallbackStandard.effectiveFeeRateBps)
            assertEquals(CardBrand.VISA, debitFallbackStandard.machineSnapshot?.appliedBrand)
            SalePersistenceValidator.validateForPersistence(debitFallbackStandard)

            val debitFallbackCustom = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                    cardBrand = null,
                    cardBrandKey = customNoDebitKey,
                ),
            )
            assertEquals(180, debitFallbackCustom.effectiveFeeRateBps)
            assertEquals(customNoDebitKey, debitFallbackCustom.machineSnapshot?.resolvedAppliedBrandKey)
            SalePersistenceValidator.validateForPersistence(debitFallbackCustom)

            // 4. Compatibilidade histórica: venda em Débito sem bandeira (null) em maquininha com brandRatesEnabled
            val legacyDebit = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                    cardBrand = null,
                    cardBrandKey = null,
                ),
            )
            assertEquals(180, legacyDebit.effectiveFeeRateBps)
            assertNull(legacyDebit.machineSnapshot?.resolvedAppliedBrandKey)
            SalePersistenceValidator.validateForPersistence(legacyDebit)

            // 5. Crédito com bandeira padrão e personalizada continua íntegro
            val creditStandard = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    cardBrandKey = CardBrand.VISA.storageKey,
                    installments = 2,
                ),
            )
            assertEquals(410, creditStandard.effectiveFeeRateBps) // 310 + 100
            assertEquals(CardBrand.VISA, creditStandard.machineSnapshot?.appliedBrand)
            SalePersistenceValidator.validateForPersistence(creditStandard)

            val creditCustom = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(product, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = null,
                    cardBrandKey = customKey,
                    installments = 1,
                ),
            )
            assertEquals(260, creditCustom.effectiveFeeRateBps)
            assertEquals(customKey, creditCustom.machineSnapshot?.resolvedAppliedBrandKey)
            SalePersistenceValidator.validateForPersistence(creditCustom)
        }

    @Test
    fun `04 Vender UI debit custom brand matches displayed rate, applied rate, and persisted Room snapshot`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val aleloKey = CustomBrandKeyCodec.encodeKey("Alelo")
        val (productId, machineId) = runBlocking(Dispatchers.IO) {
            val person = app.catalogRepository.createPerson("Lucas")
            val product = app.catalogRepository.createProduct(
                name = "Suporte 3D",
                price = MoneyCents(10_000L), // R$ 100,00
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Rede Débito",
                    debitRateBps = 180, // 1,80% padrão
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 300),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    customBrandRates = listOf(
                        CustomCardBrandInput(
                            name = "Alelo",
                            creditRateBps = 250,
                            debitRateBps = 120, // 1,20% no débito
                            key = aleloKey,
                        ),
                    ),
                ),
            )
            product.id to machine.id
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitForTag("open_product_picker_button")

        // Seleciona produto
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitForTag("picker_checkbox_$productId")
        composeRule.onNodeWithTag("picker_checkbox_$productId").performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        composeRule.waitForIdle()

        // Seleciona pagamento Débito e maquininha
        composeRule.onNodeWithTag("pay_method_debit").performScrollTo().performClick()
        waitForTag("machine_selector_dropdown")
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitForTag("machine_picker_row_$machineId")
        composeRule.onNodeWithTag("machine_picker_row_$machineId").performClick()
        waitForTag("brand_selector_dropdown")

        // Seleciona bandeira personalizada "Alelo" no Débito
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(hasText("Alelo (1,20%)", substring = false))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Alelo (1,20%)", substring = false).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodes(
                hasTestTag("brand_selector_dropdown") and hasText("Alelo", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        // Bandeira não é resetada para VISA e taxa exibida é 1,20%
        composeRule.onNodeWithTag("brand_selector_dropdown")
            .assert(hasText("Alelo", substring = true))
        composeRule.onNodeWithTag("vender_rate_summary")
            .performScrollTo()
            .assert(hasText("1,20%", substring = true))

        // Salva venda e confere que a taxa aplicada e salva no Room é exatamente 120 bps (R$ 1,20)
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                app.saleRepository.observeSales().first().isNotEmpty()
            }
        }

        val savedSale = runBlocking(Dispatchers.IO) {
            app.saleRepository.observeSales().first().single()
        }
        assertEquals(SalePaymentMethod.DEBIT, savedSale.calculation.paymentMethod)
        assertEquals(aleloKey, savedSale.calculation.machineSnapshot?.resolvedAppliedBrandKey)
        assertEquals(120, savedSale.calculation.effectiveFeeRateBps)
        assertEquals(120L, savedSale.calculation.feeTotal.rawCents)
        assertEquals(9_880L, savedSale.calculation.netTotal.rawCents)
    }

    @Test
    fun `05 personAccentColor handles negative, zero, positive, and overflow indices safely`() {
        val paletteSize = V19PersonPalette.size
        assertTrue(paletteSize > 0)

        // Zero e positivos mantêm o comportamento original
        assertEquals(V19PersonPalette[0], personAccentColor(0))
        for (i in 0 until paletteSize) {
            assertEquals(V19PersonPalette[i], personAccentColor(i))
            assertEquals(V19PersonPalette[i], personAccentColor(i + paletteSize * 3))
        }

        // Negativos (incluindo Int.MIN_VALUE) nunca lançam IndexOutOfBoundsException
        assertEquals(V19PersonPalette[paletteSize - 1], personAccentColor(-1))
        assertEquals(V19PersonPalette[Math.floorMod(-7, paletteSize)], personAccentColor(-7))
        assertEquals(V19PersonPalette[Math.floorMod(-100, paletteSize)], personAccentColor(-100))
        assertEquals(
            V19PersonPalette[Math.floorMod(Int.MIN_VALUE, paletteSize)],
            personAccentColor(Int.MIN_VALUE),
        )
    }

    @Test
    fun `06 monetary safe multiplications preserve normal values and prevent silent Long overflow`() {
        // Normal
        assertEquals(15_000L, MonetaryRounding.safeMultiply(5_000L, 3))
        assertEquals(15_000L, MonetaryRounding.safeMultiplyOrNull(5_000L, 3))
        assertEquals(0L, MonetaryRounding.safeMultiplyOrNull(5_000L, 0))
        assertEquals(10_000L, MonetaryRounding.safeAddOrNull(4_000L, 6_000L))

        // Overflow em safeMultiply lança FinancialOverflowException e safeMultiplyOrNull retorna null
        val hugePrice = (Long.MAX_VALUE / 2L) + 100L
        try {
            MonetaryRounding.safeMultiply(hugePrice, 3)
            fail("Deveria lançar FinancialOverflowException em overflow de multiplicação.")
        } catch (_: FinancialOverflowException) {
            // Esperado
        }
        assertNull(MonetaryRounding.safeMultiplyOrNull(hugePrice, 3))
        assertNull(MonetaryRounding.safeAddOrNull(Long.MAX_VALUE, 1L))

        // computeSelectedSubtotalCents normal vs overflow
        val normalProduct = Product(
            id = "p_norm",
            name = "Normal",
            price = MoneyCents(2_500L),
            active = true,
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
            participants = listOf(ProductParticipantSplit("u1", "Ana", 0, MoneyCents(2_500L))),
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
        val hugeProduct = normalProduct.copy(
            id = "p_huge",
            name = "Extremo",
            price = MoneyCents(hugePrice),
            participants = listOf(ProductParticipantSplit("u1", "Ana", 0, MoneyCents(hugePrice))),
        )

        assertEquals(
            7_500L,
            MixVendasShellViewModel.computeSelectedSubtotalCents(
                selectedQuantities = mapOf("p_norm" to 3),
                productMap = mapOf("p_norm" to normalProduct),
            ),
        )
        assertNull(
            MixVendasShellViewModel.computeSelectedSubtotalCentsOrNull(
                selectedQuantities = mapOf("p_huge" to 3),
                productMap = mapOf("p_huge" to hugeProduct),
            ),
        )
        // Nunca faz wrap para negativo na visualização
        assertEquals(
            Long.MAX_VALUE,
            MixVendasShellViewModel.computeSelectedSubtotalCents(
                selectedQuantities = mapOf("p_huge" to 3),
                productMap = mapOf("p_huge" to hugeProduct),
            ),
        )

        // Motor financeiro continua bloqueando overflow ao calcular venda
        try {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(hugeProduct, 3)),
                    paymentMethod = SalePaymentMethod.PIX,
                ),
            )
            fail("SaleFinancialEngine deveria rejeitar overflow com SaleCalculationException.")
        } catch (_: SaleCalculationException) {
            // Esperado
        }
    }
}
