package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.data.repository.SaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
class Stage3B2B1SaleEditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanUpDatabase() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun scrollToSaleCardInVendas(saleId: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$saleId"))
                true
            }.getOrDefault(false)
        }
    }

    private fun openSaleEditFromVendas(
        saleId: String,
        navigateToVendasTab: Boolean = true,
        verifyDeleteDisabled: Boolean = false,
    ) {
        if (navigateToVendasTab) {
            composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        }
        scrollToSaleCardInVendas(saleId)
        if (verifyDeleteDisabled) {
            composeRule.onNodeWithTag("sale_delete_$saleId")
                .performScrollTo()
                .assertIsEnabled()
        }
        composeRule.onNodeWithTag("sale_edit_$saleId")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pickProductFromDialog(productId: String) {
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("product_picker_list")
                    .performScrollToNode(hasTestTag("picker_row_$productId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("picker_checkbox_$productId").performScrollTo().performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("selected_inc_$productId").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectMachineFromDialog(machineId: String) {
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("machine_picker_list")
                    .performScrollToNode(hasTestTag("machine_picker_row_$machineId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("machine_picker_row_$machineId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun assertMathematicalInvariants(sale: PersistedSale) {
        val calc = sale.calculation
        assertEquals(
            calc.finalAmount.rawCents,
            calc.originalSubtotal.rawCents - calc.discountTotal.rawCents,
        )
        assertEquals(
            calc.finalAmount.rawCents,
            calc.items.sumOf { it.finalAmount.rawCents },
        )
        assertEquals(
            calc.feeTotal.rawCents,
            calc.items.sumOf { it.feeAmount.rawCents },
        )
        assertEquals(
            calc.netTotal.rawCents,
            calc.personTotals.sumOf { it.netAmount.rawCents },
        )
        assertEquals(
            calc.netTotal.rawCents,
            calc.finalAmount.rawCents - calc.feeTotal.rawCents,
        )
        calc.items.forEach { item ->
            assertEquals(
                item.finalAmount.rawCents,
                item.participantSplits.sumOf { it.grossAmount.rawCents },
            )
            assertEquals(
                item.feeAmount.rawCents,
                item.participantSplits.sumOf { it.feeAmount.rawCents },
            )
            assertEquals(
                item.netAmount.rawCents,
                item.participantSplits.sumOf { it.netAmount.rawCents },
            )
        }
    }

    @Test
    fun `01 open edit from vendas loads all fields, edits note preserving snapshots and date, and exits cleanly`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (saleId, prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val prodA = app.catalogRepository.createProduct(
                name = "Dragão Dourado",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Base Rochosa",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = bruno.id,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Ultra",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 280,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.ELO to 340),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 110, 3 to 160),
                ),
            )

            val saved = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(
                            SaleItemInput(prodA, 2),
                            SaleItemInput(prodB, 1),
                        ),
                        finalAmountOverride = MoneyCents(14_000L), // 160,00 - 20,00 desconto
                        discountMode = SaleDiscountInput.SpecificProduct(prodA.id),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.ELO,
                        installments = 3,
                        manualRateOverrideBps = 450,
                        note = "Entrega sexta",
                    ),
                ),
            )
            Triple(saved.id, prodA.id, prodB.id)
        }

        val originalSale = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }

        // Vai para VENDAS e aciona Editar (enquanto Excluir segue pendente/desabilitado)
        openSaleEditFromVendas(saleId, navigateToVendasTab = true, verifyDeleteDisabled = true)
        composeRule.onNodeWithTag("vender_editing_banner")
            .assertTextContains("Editando venda", substring = true)
        composeRule.onNodeWithTag("selected_inc_$prodAId").performScrollTo().assertExists()
        composeRule.onNodeWithTag("selected_inc_$prodBId").performScrollTo().assertExists()
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo()
            .assertTextContains("140,00")
        composeRule.onNodeWithTag("discount_products_box").performScrollTo().assertExists()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodAId").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("discount_product_checkbox_$prodBId").performScrollTo().assertIsOff()
        composeRule.onNodeWithTag("sale_note_input").performScrollTo()
            .assertTextContains("Entrega sexta")
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo()
            .assertTextContains("Ton Ultra")
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo()
            .assertTextContains("Elo")
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo()
            .assertTextContains("3x")
        composeRule.onNodeWithTag("manual_rate_input").performScrollTo()
            .assertTextContains("4,50")
        composeRule.onNodeWithTag("save_sale_button").performScrollTo()
            .assertTextContains("Salvar alterações")

        // Edita somente a observação
        Thread.sleep(5)
        composeRule.onNodeWithTag("sale_note_input").performScrollTo()
            .performTextReplacement("Entrega sábado à tarde")
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Alterações salvas com sucesso", substring = true)

        // Sai do modo edição e restaura "Registrar venda"
        composeRule.onAllNodesWithTag("vender_editing_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("save_sale_button").performScrollTo()
            .assertTextContains("Registrar venda")

        // Confirma no Room: sem duplicação, mesmo ID e soldAtEpochMs, snapshots financeiros idênticos
        val allSales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        assertEquals(1, allSales.size)
        val updatedSale = allSales.single()
        assertEquals(originalSale.id, updatedSale.id)
        assertEquals(originalSale.soldAtEpochMs, updatedSale.soldAtEpochMs)
        assertTrue(updatedSale.updatedAtEpochMs >= originalSale.updatedAtEpochMs)
        assertEquals("Entrega sábado à tarde", updatedSale.calculation.note)
        assertEquals(originalSale.calculation.originalSubtotal, updatedSale.calculation.originalSubtotal)
        assertEquals(originalSale.calculation.discountTotal, updatedSale.calculation.discountTotal)
        assertEquals(originalSale.calculation.finalAmount, updatedSale.calculation.finalAmount)
        assertEquals(originalSale.calculation.feeTotal, updatedSale.calculation.feeTotal)
        assertEquals(originalSale.calculation.netTotal, updatedSale.calculation.netTotal)
        assertEquals(originalSale.calculation.effectiveFeeRateBps, updatedSale.calculation.effectiveFeeRateBps)
        assertEquals(originalSale.calculation.machineSnapshot, updatedSale.calculation.machineSnapshot)
        assertEquals(originalSale.calculation.personTotals, updatedSale.calculation.personTotals)
        assertMathematicalInvariants(updatedSale)
    }

    @Test
    fun `02 edit quantities, add and remove products, change payment and discount, and re-edit without double discounting`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (saleId, prodAId, prodBId, prodCId, machineId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")

            val prodA = app.catalogRepository.createProduct(
                name = "Vaso Prisma",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Cachepot Lua",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodC = app.catalogRepository.createProduct(
                name = "Suporte Cubo",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(5_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = bruno.id,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Smart",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250, CardBrand.MASTERCARD to 320),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100),
                ),
            )
            val saved = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                        finalAmountOverride = MoneyCents(8_000L), // subtotal 100 - desconto 20 (proporcional)
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Pix inicial",
                    ),
                ),
            )
            listOf(saved.id, prodA.id, prodB.id, prodC.id, machine.id)
        }

        // Passo A: Abre edição (1x Vaso 60 + 1x Cachepot 40, desconto 20 proporcional, Pix)
        openSaleEditFromVendas(saleId)

        // Aumenta Vaso Prisma para 2x (novo subtotal 120 + 40 = 160; desconto 20 preservado -> final 140)
        composeRule.onNodeWithTag("selected_inc_$prodAId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_recalculated_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo()
            .assertTextContains("140,00")

        // Remove Cachepot Lua (prodB) e adiciona Suporte Cubo (prodC, R$ 50,00) -> subtotal 120 + 50 = 170; desconto 20 preservado -> final 150
        composeRule.onNodeWithTag("selected_remove_$prodBId").performScrollTo().performClick()
        pickProductFromDialog(prodCId)
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo()
            .assertTextContains("150,00")

        // Muda desconto para específico em Vaso Prisma e altera valor final para 140,00 (desconto R$ 30 só em Vaso)
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("140,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_product_row_$prodCId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_product_row_$prodCId").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Muda pagamento de Pix -> Crédito na Stone Smart, Mastercard (3,20%) + 2x (+1,00%) = 4,20%
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        selectMachineFromDialog(machineId)
        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Mastercard (3,20%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Mastercard (3,20%)").performClick()
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("2x (+1,00%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("2x (+1,00%)").performClick()

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val afterFirstEdit = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertMathematicalInvariants(afterFirstEdit)
        assertEquals(17_000L, afterFirstEdit.calculation.originalSubtotal.rawCents)
        assertEquals(3_000L, afterFirstEdit.calculation.discountTotal.rawCents)
        assertEquals(14_000L, afterFirstEdit.calculation.finalAmount.rawCents)
        assertEquals(420, afterFirstEdit.calculation.effectiveFeeRateBps)
        // Taxas: Vaso (90,00 * 4,20% = 3,78 = 378) + Suporte (50,00 * 4,20% = 2,10 = 210) => total 588 cents
        assertEquals(588L, afterFirstEdit.calculation.feeTotal.rawCents)
        assertEquals(13_412L, afterFirstEdit.calculation.netTotal.rawCents)
        assertTrue(afterFirstEdit.calculation.discountConfig is SaleDiscountConfigSnapshot.SpecificProduct)
        assertEquals(setOf(prodAId, prodCId), afterFirstEdit.calculation.items.map { it.productId }.toSet())

        // Passo B: RE-EDITAR a venda já editada: preço unitário original do Vaso deve ser 60,00 (sem desconto em duplicidade!)
        openSaleEditFromVendas(saleId)

        // Subtotal original precisa continuar sendo R$ 170,00 e valor final R$ 140,00 (nunca R$ 140 - R$ 30)
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().assertTextContains("140,00")
        composeRule.onNodeWithText("R$ 170,00").assertExists()

        // Muda pagamento de Crédito -> Dinheiro e altera valor final para 160,00 (desconto 10 proporcional)
        composeRule.onNodeWithTag("pay_method_cash").performScrollTo().performClick()
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("160,00")
        composeRule.onNodeWithTag("discount_select_all_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val afterSecondEdit = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertMathematicalInvariants(afterSecondEdit)
        assertEquals(SalePaymentMethod.CASH, afterSecondEdit.calculation.paymentMethod)
        assertNull(afterSecondEdit.calculation.machineSnapshot)
        assertEquals(0, afterSecondEdit.calculation.effectiveFeeRateBps)
        assertEquals(0L, afterSecondEdit.calculation.feeTotal.rawCents)
        assertEquals(17_000L, afterSecondEdit.calculation.originalSubtotal.rawCents)
        assertEquals(1_000L, afterSecondEdit.calculation.discountTotal.rawCents)
        assertEquals(16_000L, afterSecondEdit.calculation.finalAmount.rawCents)
        assertEquals(16_000L, afterSecondEdit.calculation.netTotal.rawCents)
    }

    @Test
    fun `03 removing specific discount target or shrinking subtotal below old discount blocks save cleanly`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (saleId, prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Lia")
            val prodA = app.catalogRepository.createProduct(
                name = "Capa Celular",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Estatueta M",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(6_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val saved = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                        finalAmountOverride = MoneyCents(5_000L), // desconto 50,00 específico em Estatueta (60,00)
                        discountMode = SaleDiscountInput.SpecificProduct(prodB.id),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            Triple(saved.id, prodA.id, prodB.id)
        }

        openSaleEditFromVendas(saleId)

        // Remove Estatueta (R$ 60): restante Capa (R$ 40) < desconto anterior (R$ 50) E alvo específico sumiu!
        composeRule.onNodeWithTag("selected_remove_$prodBId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_recalculated_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_target_missing_warning").performScrollTo().assertExists()

        // Tentar salvar deve falhar e não modificar o Room
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_editing_banner").assertExists()

        // Ajustar valor final para 35,00 (desconto 5,00), mas SEM escolher novo alvo de desconto ainda deve bloquear
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("35,00")
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("removido", substring = true).fetchSemanticsNodes().size >= 2
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("removido", substring = true)

        // Agora usuário escolhe explicitamente Capa Celular na caixa de produtos do desconto e salva com sucesso
        composeRule.onNodeWithTag("discount_product_row_$prodAId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithText("Alterações salvas com sucesso", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Alterações salvas com sucesso", substring = true)

        val updated = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(3_500L, updated.calculation.finalAmount.rawCents)
        assertEquals(listOf(prodAId), updated.calculation.items.map { it.productId })
        assertMathematicalInvariants(updated)
    }

    @Test
    fun `04 historical product and machine snapshots stay intact after catalog deactivation rename price change credit disable or deletion`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val setupIds = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")

            val deletedProd = app.catalogRepository.createProduct(
                name = "Peça Edição Limitada",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val changedProd = app.catalogRepository.createProduct(
                name = "Suporte Antigo",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val newProd = app.catalogRepository.createProduct(
                name = "Produto Novo Atual",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(bruno.id, MoneyCents(2_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val histMachine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Maquininha Antiga",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 150),
                ),
            )
            val newMachine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Maquininha Nova",
                    debitRateBps = 150,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Grava venda original: Peça Limitada (50,00) + Suporte Antigo (40,00) no Crédito Visa 2x = 4,00%
            val saved = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(deletedProd, 1), SaleItemInput(changedProd, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = histMachine,
                        cardBrand = CardBrand.VISA,
                        installments = 2,
                    ),
                ),
            )

            // Após a venda: exclui Peça Limitada direto na tabela Room, renomeia/desativa/altera preço do Suporte Antigo,
            // desativa crédito e depois exclui a Maquininha Antiga!
            app.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM products WHERE id = ?",
                arrayOf(deletedProd.id),
            )
            app.catalogRepository.updateProduct(
                productId = changedProd.id,
                name = "Suporte Renomeado",
                price = MoneyCents(9_900L),
                active = false,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_900L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.machineRepository.updateMachine(
                histMachine.id,
                MachineInputDraft(
                    name = "Maquininha Alterada",
                    debitRateBps = 250,
                    creditEnabled = false,
                    creditDefaultRateBps = 700,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            app.database.machineDao().deleteById(histMachine.id)

            listOf(saved.id, deletedProd.id, changedProd.id, newProd.id, histMachine.id, newMachine.id)
        }
        val saleId = setupIds[0]
        val deletedProdId = setupIds[1]
        val changedProdId = setupIds[2]
        val newProdId = setupIds[3]
        val histMachineId = setupIds[4]
        val newMachineId = setupIds[5]

        // Abre edição: nada deve quebrar; produtos e maquininha originais vêm do snapshot!
        openSaleEditFromVendas(saleId)

        composeRule.onNodeWithText("1× Peça Edição Limitada", substring = true).performScrollTo().assertExists()
        composeRule.onNodeWithText("1× Suporte Antigo", substring = true).performScrollTo().assertExists()
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo()
            .assertTextContains("Maquininha Antiga")
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("4,00%", substring = true)

        // Remove produto excluído, aumenta Suporte Antigo para 2x (deve manter preço 40,00 -> 80,00),
        // e adiciona Produto Novo Atual (20,00 do cadastro atual -> subtotal 100,00).
        // Mantém a maquininha histórica (4,00%) primeiro e salva!
        composeRule.onNodeWithTag("selected_remove_$deletedProdId").performScrollTo().performClick()
        composeRule.onNodeWithTag("selected_inc_$changedProdId").performScrollTo().performClick()
        pickProductFromDialog(newProdId)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Alterações salvas com sucesso", substring = true)

        val editedWithHistoricalMachine = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertMathematicalInvariants(editedWithHistoricalMachine)
        assertEquals(10_000L, editedWithHistoricalMachine.calculation.finalAmount.rawCents)
        assertEquals(400, editedWithHistoricalMachine.calculation.effectiveFeeRateBps)
        assertEquals(400L, editedWithHistoricalMachine.calculation.feeTotal.rawCents)
        assertEquals("Maquininha Antiga", editedWithHistoricalMachine.calculation.machineSnapshot?.machineName)
        assertFalse(editedWithHistoricalMachine.calculation.items.any { it.productId == deletedProdId })
        assertEquals(
            4_000L,
            editedWithHistoricalMachine.calculation.items.first { it.productId == changedProdId }.unitPriceOriginal.rawCents,
        )
        assertEquals(
            2_000L,
            editedWithHistoricalMachine.calculation.items.first { it.productId == newProdId }.unitPriceOriginal.rawCents,
        )

        // Agora reabre edição e troca explicitamente para Maquininha Nova (5,00% padrão, sem bandeiras/parcelas)
        openSaleEditFromVendas(saleId)
        selectMachineFromDialog(newMachineId)
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("5,00%", substring = true)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        val swappedMachineSale = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(newMachineId, swappedMachineSale.calculation.machineSnapshot?.machineId)
        assertEquals("Maquininha Nova", swappedMachineSale.calculation.machineSnapshot?.machineName)
        assertEquals(500, swappedMachineSale.calculation.effectiveFeeRateBps)
        assertEquals(500L, swappedMachineSale.calculation.feeTotal.rawCents)
        assertEquals(9_500L, swappedMachineSale.calculation.netTotal.rawCents)
        assertMathematicalInvariants(swappedMachineSale)
        assertNotNull(histMachineId)
    }

    @Test
    fun `05 cancel button and back press with dirty edits confirm discard without mutating room or contaminating next sale`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val saleId = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Marina")
            val prod = app.catalogRepository.createProduct(
                name = "Chaveiro Estrela",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Nota original",
                    ),
                ),
            ).id
        }

        // 1) Abrir edição, alterar observação, clicar Voltar -> abre confirmação -> "Continuar editando" mantém alterações
        openSaleEditFromVendas(saleId)

        composeRule.onNodeWithTag("sale_note_input").performScrollTo()
            .performTextReplacement("Alteração não salva")
        composeRule.onNodeWithTag("topbar_back_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("discard_edit_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discard_edit_keep_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("discard_edit_dialog").assertCountEquals(0)
        composeRule.onNodeWithTag("sale_note_input").performScrollTo()
            .assertTextContains("Alteração não salva")

        // 2) Voltar novamente e confirmar descarte -> volta para VENDAS, Room intocado e Vender restaurado
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("discard_edit_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discard_edit_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(
            "Nota original",
            runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!!.calculation.note },
        )

        // 3) Reabrir edição, editar observação e clicar "Cancelar edição" -> restaura formulário normal de Nova venda
        openSaleEditFromVendas(saleId, navigateToVendasTab = false)
        composeRule.onNodeWithTag("sale_note_input").performScrollTo()
            .performTextReplacement("Cancelada via botão")
        composeRule.onNodeWithTag("cancel_edit_button").performScrollTo().performClick()

        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("vender_editing_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("save_sale_button").performScrollTo()
            .assertTextContains("Registrar venda")
        composeRule.onNodeWithTag("sale_note_input").performScrollTo()
            .assertTextContains("")
        assertEquals(
            "Nota original",
            runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!!.calculation.note },
        )
    }

    @Test
    fun `06 update failure preserves edit mode, duplicate update clicks persist once, and edited sale survives db reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "edit.db"
        context.deleteDatabase(dbName)

        var savedSaleId = ""
        var olderSaleId = ""
        var prodId = ""
        var originalSoldAt = 0L

        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val prefRepo = RoomPreferenceRepository(firstDb.preferenceDao())
            val catalogRepo = RoomCatalogRepository(firstDb.personDao(), firstDb.productDao())
            val machineRepo = RoomMachineRepository(firstDb.machineDao())
            var clock = 1_700_000_000_000L
            val realSaleRepo = RoomSaleRepository(firstDb, firstDb.saleDao(), clockMillis = { clock })

            val p1 = catalogRepo.createPerson("Carla")
            val prod = catalogRepo.createProduct(
                name = "Luminária Saturno",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(4_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            prodId = prod.id

            clock = 1_700_000_000_000L
            val olderSale = realSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Antiga",
                    ),
                ),
            )
            olderSaleId = olderSale.id

            clock = 1_700_000_100_000L
            val targetSale = realSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Alvo",
                    ),
                ),
            )
            savedSaleId = targetSale.id
            originalSoldAt = targetSale.soldAtEpochMs

            val shouldFail = AtomicBoolean(true)
            val gateActive = AtomicBoolean(false)
            val updateCount = AtomicInteger(0)
            val gateDeferred = CompletableDeferred<Unit>()

            val controllableRepo = object : SaleRepository by realSaleRepo {
                override suspend fun updateSale(saleId: String, result: SaleCalculationResult): PersistedSale {
                    updateCount.incrementAndGet()
                    if (shouldFail.get()) {
                        throw IllegalStateException("Falha simulada ao atualizar venda no Room")
                    }
                    if (gateActive.get()) {
                        gateDeferred.await()
                    }
                    clock = 1_700_000_999_000L
                    return realSaleRepo.updateSale(saleId, result)
                }
            }

            val vm = MixVendasShellViewModel(
                preferenceRepository = prefRepo,
                catalogRepository = catalogRepo,
                machineRepository = machineRepo,
                saleRepository = controllableRepo,
            )
            val collectJob = CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }

            var loaded = false
            vm.startEditingSale(savedSaleId) { loaded = true }
            var waited = 0
            while (!loaded && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(loaded)

            vm.incrementProductQuantity(prodId)
            vm.onNoteChanged("Atualizada pós falha")

            // 1) Erro simulado no update: mantém modo edição e valores editados
            vm.saveSale()
            waited = 0
            while (vm.uiState.value.venderForm.feedbackMessage == null && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            val failedForm = vm.uiState.value.venderForm
            assertTrue(failedForm.isEditing)
            assertEquals(savedSaleId, failedForm.editingSaleId)
            assertTrue(failedForm.feedbackIsError)
            assertEquals(mapOf(prodId to 2), failedForm.selectedQuantities)
            assertEquals("Atualizada pós falha", failedForm.note)
            assertFalse(failedForm.isSaving)

            // 2) Segunda tentativa com cliques repetidos: trava executa apenas 1 update
            shouldFail.set(false)
            gateActive.set(true)
            updateCount.set(0)

            vm.saveSale()
            vm.saveSale()
            vm.saveSale()
            delay(50)
            gateDeferred.complete(Unit)

            waited = 0
            while (vm.uiState.value.venderForm.isEditing && waited < 80) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }

            assertEquals(1, updateCount.get())
            assertFalse(vm.uiState.value.venderForm.feedbackIsError)
            assertFalse(vm.uiState.value.venderForm.isEditing)
            assertEquals(2, realSaleRepo.observeSales().first().size)
            collectJob.cancel()
        } finally {
            firstDb.close()
        }

        // Reabre o banco para garantir persistência e preservação de ordem/datas
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val saleRepo = RoomSaleRepository(reopenedDb, reopenedDb.saleDao())
            val allSales = saleRepo.observeSales().first()
            assertEquals(2, allSales.size)
            assertEquals(savedSaleId, allSales[0].id)
            assertEquals(olderSaleId, allSales[1].id)

            val reloaded = saleRepo.getSaleById(savedSaleId)!!
            assertEquals(originalSoldAt, reloaded.soldAtEpochMs)
            assertEquals(1_700_000_999_000L, reloaded.updatedAtEpochMs)
            assertEquals(9_000L, reloaded.calculation.finalAmount.rawCents)
            assertEquals("Atualizada pós falha", reloaded.calculation.note)
            assertMathematicalInvariants(reloaded)
        } finally {
            reopenedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `07 editing historical 18x sale after machine limit lowered to 12x preserves snapshot until explicit machine switch`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (saleId, prodId, machineId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Helena")
            val prod = app.catalogRepository.createProduct(
                name = "Castelo 3D Gigante",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "InfinitePay 18x",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 300),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 12 to 450, 18 to 720),
                    maxInstallments = 18,
                ),
            )
            val saved = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 18,
                        note = "Venda histórica 18x",
                    ),
                ),
            )

            // Reduz limite da maquininha para 12x após a venda ter sido gravada
            app.machineRepository.updateMachine(
                machine.id,
                MachineInputDraft(
                    name = "InfinitePay 18x",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 300),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 12 to 450, 18 to 720),
                    maxInstallments = 12,
                ),
            )
            Triple(saved.id, prod.id, machine.id)
        }

        // Venda histórica no Room não pode ser alterada pela mudança da maquininha
        val beforeEdit = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(18, beforeEdit.calculation.machineSnapshot?.appliedInstallments)
        assertEquals(1_020, beforeEdit.calculation.effectiveFeeRateBps)

        // 1) Abrir edição preserva 18x e taxa original (10,20%) mesmo com novo limite de 12x no cadastro
        openSaleEditFromVendas(saleId)
        composeRule.onNodeWithTag("vender_historical_machine_notice").performScrollTo().assertExists()
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo()
            .assertTextContains("18x")
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("10,20%", substring = true)

        // Altera quantidade (1 -> 2) mantendo snapshot histórico da maquininha -> 18x e 10,20% preservados
        composeRule.onNodeWithTag("selected_inc_$prodId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val afterSnapshotEdit = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(20_000L, afterSnapshotEdit.calculation.finalAmount.rawCents)
        assertEquals(18, afterSnapshotEdit.calculation.machineSnapshot?.appliedInstallments)
        assertEquals(1_020, afterSnapshotEdit.calculation.effectiveFeeRateBps)
        assertEquals(2_040L, afterSnapshotEdit.calculation.feeTotal.rawCents)
        assertMathematicalInvariants(afterSnapshotEdit)

        // 2) Reabrir edição e selecionar voluntariamente a maquininha atual (limite 12x) -> ajusta 18x -> 1x e aplica 3,00%
        openSaleEditFromVendas(saleId)
        selectMachineFromDialog(machineId)
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo()
            .assertTextContains("1x")
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("3,00%", substring = true)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val afterCurrentMachineApply = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(1, afterCurrentMachineApply.calculation.machineSnapshot?.appliedInstallments)
        assertEquals(300, afterCurrentMachineApply.calculation.effectiveFeeRateBps)
        assertEquals(600L, afterCurrentMachineApply.calculation.feeTotal.rawCents)
        assertEquals(19_400L, afterCurrentMachineApply.calculation.netTotal.rawCents)
        assertMathematicalInvariants(afterCurrentMachineApply)
    }
}
