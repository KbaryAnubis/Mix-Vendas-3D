package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
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
import androidx.compose.ui.test.performTextInput
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
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
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
class Stage3B2ASalesFlowTest {

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

    private fun selectProductsInVender(productIdsAndExtraIncrements: List<Pair<String, Int>>) {
        composeRule.onNodeWithTag("open_product_picker_button")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        productIdsAndExtraIncrements.forEach { (productId, extraIncrements) ->
            waitUiUntil {
                runCatching {
                    composeRule.onNodeWithTag("product_picker_list")
                        .performScrollToNode(hasTestTag("picker_row_$productId"))
                    true
                }.getOrDefault(false)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("picker_checkbox_$productId").performClick()
            waitUiUntil {
                composeRule.onAllNodesWithTag("picker_inc_$productId")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            repeat(extraIncrements) {
                composeRule.onNodeWithTag("product_picker_list")
                    .performScrollToNode(hasTestTag("picker_row_$productId"))
                composeRule.onNodeWithTag("picker_inc_$productId").performClick()
                composeRule.waitForIdle()
            }
        }
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        if (productIdsAndExtraIncrements.isNotEmpty()) {
            val firstId = productIdsAndExtraIncrements.first().first
            waitUiUntil {
                composeRule.onAllNodesWithTag("selected_inc_$firstId")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.waitForIdle()
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
    }

    @Test
    fun `01 empty vendas shows CTA without demo data, and cash plus pix sales save without machine fees`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        // 1) VENDAS deve iniciar 100% real (vazia) e sem nenhum item de PreviewFixtures
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_vendas").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("vendas_empty_state"))
        composeRule.onNodeWithTag("vendas_empty_state").assertExists()
        composeRule.onNodeWithTag("vendas_empty_sell_cta").assertExists()
        composeRule.onAllNodesWithTag("sale_history_card_demo-1").assertCountEquals(0)
        composeRule.onAllNodesWithText("Exemplo", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("PRÉVIA", substring = true).assertCountEquals(0)

        // Cadastra produto real no Room
        val prodId = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Marina")
            val p2 = app.catalogRepository.createPerson("Lucas")
            app.catalogRepository.createProduct(
                name = "Chaveiro Dragão",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(1_500L)),
                    ProductParticipantDraft(p2.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            ).id
        }

        // CTA leva direto para a aba Vender
        composeRule.onNodeWithTag("vendas_empty_sell_cta").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_vender").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_vender").assertIsSelected()

        // 2) Venda em Dinheiro (1x Chaveiro = R$ 25,00)
        selectProductsInVender(listOf(prodId to 0))
        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextInput("Balcão")
        composeRule.onNodeWithTag("pay_method_cash").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Venda registrada com sucesso", substring = true)

        // Formulário deve limpar após sucesso
        composeRule.onNodeWithTag("sale_note_input").assertTextContains("")

        // 3) Venda em Pix (2x Chaveiro = R$ 50,00)
        selectProductsInVender(listOf(prodId to 1))
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            runBlocking(Dispatchers.IO) {
                app.saleRepository.observeSales().first().size == 2
            }
        }

        val savedSales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        assertEquals(2, savedSales.size)
        savedSales.forEach { sale ->
            assertMathematicalInvariants(sale)
            assertEquals(0L, sale.calculation.feeTotal.rawCents)
            assertEquals(0, sale.calculation.effectiveFeeRateBps)
            assertNull(sale.calculation.machineSnapshot)
        }
        val cashSale = savedSales.first { it.calculation.paymentMethod == SalePaymentMethod.CASH }
        val pixSale = savedSales.first { it.calculation.paymentMethod == SalePaymentMethod.PIX }
        assertEquals(2_500L, cashSale.calculation.netTotal.rawCents)
        assertEquals(5_000L, pixSale.calculation.netTotal.rawCents)

        // 4) Consulta na aba VENDAS: não exibe taxa de maquininha em Dinheiro/Pix e mostra botões pendentes desabilitados
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_top_summary_card"))
        waitUiUntil {
            composeRule.onAllNodesWithText("2 vendas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Taxa da maquininha", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Tarifa cobrada", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${cashSale.id}"))
        composeRule.onNodeWithTag("sale_details_toggle_${cashSale.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sale_item_detail_${cashSale.id}_$prodId").assertExists()
        composeRule.onNodeWithTag("sale_edit_${cashSale.id}").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("sale_delete_${cashSale.id}").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${pixSale.id}"))
        composeRule.onNodeWithTag("sale_history_card_${pixSale.id}").assertExists()
    }

    @Test
    fun `02 multi product sale with opposite participant splits and 3 plus people renders exact cents in room and vendas`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, carlaId, prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val carla = app.catalogRepository.createPerson("Carla")

            // Produto A (R$ 40,00): Ana > Bruno
            val prodA = app.catalogRepository.createProduct(
                name = "Torre Castelo 3D",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Produto B (R$ 30,00): Bruno > Ana e Carla
            val prodB = app.catalogRepository.createProduct(
                name = "Base Modular 3D",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(ana.id, MoneyCents(500L)),
                    ProductParticipantDraft(carla.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            listOf(ana.id, bruno.id, carla.id, prodA.id, prodB.id)
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()

        // 2x Torre Castelo (80,00) + 1x Base Modular (30,00) = 110,00
        selectProductsInVender(listOf(prodAId to 1, prodBId to 0))
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertMathematicalInvariants(saved)
        assertEquals(11_000L, saved.calculation.originalSubtotal.rawCents)
        assertEquals(11_000L, saved.calculation.finalAmount.rawCents)
        assertEquals(11_000L, saved.calculation.netTotal.rawCents)

        val byPerson = saved.calculation.personTotals.associateBy { it.personId }
        assertEquals(6_500L, byPerson.getValue(anaId).netAmount.rawCents)
        assertEquals(4_000L, byPerson.getValue(brunoId).netAmount.rawCents)
        assertEquals(500L, byPerson.getValue(carlaId).netAmount.rawCents)

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sales_person_net_$anaId").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("sales_person_net_$anaId").assertTextContains("R$ 65,00", substring = true)
        composeRule.onNodeWithTag("sales_person_net_$brunoId").assertTextContains("R$ 40,00", substring = true)
        composeRule.onNodeWithTag("sales_person_net_$carlaId").assertTextContains("R$ 5,00", substring = true)

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${saved.id}"))
        composeRule.onNodeWithTag("sale_details_toggle_${saved.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sale_item_detail_${saved.id}_$prodAId").assertExists()
        composeRule.onNodeWithTag("sale_item_detail_${saved.id}_$prodBId").assertExists()
    }

    @Test
    fun `03 debit, credit with brand and installment addon, and manual rate replacement compute exact rates and fees`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (prodId, tonId, debitOnlyId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Daniel")
            val prod = app.catalogRepository.createProduct(
                name = "Luminária Lua",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = p1.id,
            )
            val ton = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Pro",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 280,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250, CardBrand.ELO to 340),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 110, 3 to 160),
                ),
            )
            val debitOnly = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Só Débito",
                    debitRateBps = 140,
                    creditEnabled = false,
                    creditDefaultRateBps = 0,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            Triple(prod.id, ton.id, debitOnly.id)
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()

        // A) Débito na Ton Pro (1,90%)
        selectProductsInVender(listOf(prodId to 0))
        composeRule.onNodeWithTag("pay_method_debit").performScrollTo().performClick()
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_row_$tonId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_row_$tonId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        // B) Crédito na Ton Pro com bandeira Elo (3,40%) + 3x (+1,60%) = 5,00%
        selectProductsInVender(listOf(prodId to 0))
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        // Maquininha sem crédito ativado NÃO pode aparecer para seleção em Crédito
        composeRule.onAllNodesWithTag("machine_picker_row_$debitOnlyId").assertCountEquals(0)
        composeRule.onNodeWithTag("machine_picker_row_$tonId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Elo (3,40%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Elo (3,40%)").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("installments_selector_dropdown").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("3x (+1,60%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("3x (+1,60%)").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithText("5,00%", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("5,00%", substring = true)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 2 }
        }

        // C) Crédito na Ton Pro com tarifa manual substituindo integralmente a taxa (2,10%)
        selectProductsInVender(listOf(prodId to 0))
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_selector_dropdown").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_row_$tonId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_row_$tonId").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("manual_fee_override_checkbox").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("manual_fee_override_checkbox").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("manual_rate_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("manual_rate_input").performScrollTo().performTextInput("2,10")
        waitUiUntil {
            composeRule.onAllNodesWithText("Tarifa aplicada nesta venda (manual): 2,10%", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("Tarifa aplicada nesta venda (manual): 2,10%", substring = true)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty() &&
                runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 3 }
        }

        val allSales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        allSales.forEach(::assertMathematicalInvariants)

        val debitSale = allSales.first { it.calculation.paymentMethod == SalePaymentMethod.DEBIT }
        assertEquals(190, debitSale.calculation.effectiveFeeRateBps)
        assertEquals(190L, debitSale.calculation.feeTotal.rawCents)
        assertEquals(9_810L, debitSale.calculation.netTotal.rawCents)

        val autoCreditSale = allSales.first {
            it.calculation.paymentMethod == SalePaymentMethod.CREDIT &&
                it.calculation.machineSnapshot?.manualRateOverrideBps == null
        }
        assertEquals(CardBrand.ELO, autoCreditSale.calculation.machineSnapshot?.appliedBrand)
        assertEquals(3, autoCreditSale.calculation.machineSnapshot?.appliedInstallments)
        assertEquals(500, autoCreditSale.calculation.effectiveFeeRateBps)
        assertEquals(500L, autoCreditSale.calculation.feeTotal.rawCents)
        assertEquals(9_500L, autoCreditSale.calculation.netTotal.rawCents)

        val manualCreditSale = allSales.first {
            it.calculation.paymentMethod == SalePaymentMethod.CREDIT &&
                it.calculation.machineSnapshot?.manualRateOverrideBps != null
        }
        assertEquals(210, manualCreditSale.calculation.machineSnapshot?.manualRateOverrideBps)
        assertEquals(210, manualCreditSale.calculation.effectiveFeeRateBps)
        assertEquals(210L, manualCreditSale.calculation.feeTotal.rawCents)
        assertEquals(9_790L, manualCreditSale.calculation.netTotal.rawCents)

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("3 vendas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${manualCreditSale.id}"))
        composeRule.onNodeWithText("Taxa da maquininha (manual)", substring = true).assertExists()
    }

    @Test
    fun `04 proportional discount and specific product discount work from dropdown showing only selected products`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Lia")
            val prodA = app.catalogRepository.createProduct(
                name = "Produto Alfa",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(6_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Produto Beta",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.catalogRepository.createProduct(
                name = "Produto Fora",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(2_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            prodA.id to prodB.id
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()

        // 1) Desconto proporcional (R$ 100,00 -> R$ 80,00 => -12,00 em Alfa, -8,00 em Beta)
        selectProductsInVender(listOf(prodAId to 0, prodBId to 0))
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("80,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_target_dropdown").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("discount_target_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Aplicar somente em Produto Beta").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Aplicar somente em Produto Fora").assertCountEquals(0)
        composeRule.onAllNodesWithText("Distribuir proporcionalmente entre os produtos")
            .onLast()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        // 2) Desconto específico em Produto Beta (R$ 100,00 -> R$ 85,00 => -15,00 só em Beta)
        selectProductsInVender(listOf(prodAId to 0, prodBId to 0))
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("85,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_target_dropdown").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_target_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Aplicar somente em Produto Beta").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Aplicar somente em Produto Beta").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pay_method_cash").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 2 }
        }

        val sales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        sales.forEach(::assertMathematicalInvariants)

        val propSale = sales.first { it.calculation.discountConfig is SaleDiscountConfigSnapshot.Proportional }
        assertEquals(2_000L, propSale.calculation.discountTotal.rawCents)
        assertEquals(4_800L, propSale.calculation.items.first { it.productId == prodAId }.finalAmount.rawCents)
        assertEquals(3_200L, propSale.calculation.items.first { it.productId == prodBId }.finalAmount.rawCents)

        val specificSale = sales.first { it.calculation.discountConfig is SaleDiscountConfigSnapshot.SpecificProduct }
        assertEquals(1_500L, specificSale.calculation.discountTotal.rawCents)
        assertEquals(6_000L, specificSale.calculation.items.first { it.productId == prodAId }.finalAmount.rawCents)
        assertEquals(2_500L, specificSale.calculation.items.first { it.productId == prodBId }.finalAmount.rawCents)

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("2 vendas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_${specificSale.id}"))
        composeRule.onNodeWithText("Desconto (Em Produto Beta)", substring = true).assertExists()
    }

    @Test
    fun `05 invalid inputs, zero or over-subtotal discounts, excessive specific discount, and incompatible machines block save and preserve form`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Pedro")
            val prodA = app.catalogRepository.createProduct(
                name = "Caixa Alta",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(6_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Tampa Simples",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Somente Débito",
                    debitRateBps = 150,
                    creditEnabled = false,
                    creditDefaultRateBps = 0,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            prodA.id to prodB.id
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()

        // A) Tentar salvar sem nenhum produto selecionado
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Selecione pelo menos um produto", substring = true)

        // B) Selecionar produtos, preencher observação e tentar desconto inválido (> subtotal)
        selectProductsInVender(listOf(prodAId to 0, prodBId to 0))
        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextInput("Obs preservada")
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("120,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_final_amount_error", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        // Formulário deve continuar preenchido
        composeRule.onNodeWithTag("sale_note_input").assertTextContains("Obs preservada")
        composeRule.onNodeWithTag("sale_final_value_input").assertTextContains("120,00")

        // C) Valor final 0,00 bloqueado e preservado
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("0,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_final_amount_error", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_note_input").assertTextContains("Obs preservada")

        // D) Desconto específico em Tampa Simples (R$ 40,00) maior que o valor do item (desconto de R$ 50,00 -> final R$ 50,00)
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("50,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_target_dropdown").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_target_dropdown").performScrollTo().performClick()
        composeRule.onNodeWithText("Aplicar somente em Tampa Simples").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_note_input").assertTextContains("Obs preservada")

        // E) Crédito sem nenhuma maquininha habilitada para crédito
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("100,00")
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_no_eligible_machines").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Nenhuma venda gravada após todas as tentativas inválidas
        val persistedCount = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size }
        assertEquals(0, persistedCount)
    }

    @Test
    fun `06 repository failure preserves form for retry and duplicate save clicks persist only once`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefRepo = RoomPreferenceRepository(db.preferenceDao())
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val machineRepo = RoomMachineRepository(db.machineDao())
            val realSaleRepo = RoomSaleRepository(db, db.saleDao())

            val p1 = catalogRepo.createPerson("Joana")
            val prod = catalogRepo.createProduct(
                name = "Vaso Espiral",
                price = MoneyCents(3_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(3_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val shouldFail = AtomicBoolean(true)
            val writeAttemptGate = AtomicBoolean(false)
            val saveCallCount = AtomicInteger(0)
            val gateDeferred = CompletableDeferred<Unit>()

            val controllableRepo = object : SaleRepository by realSaleRepo {
                override suspend fun saveSale(result: SaleCalculationResult): PersistedSale {
                    saveCallCount.incrementAndGet()
                    if (shouldFail.get()) {
                        throw IllegalStateException("Falha simulada de persistência no Room")
                    }
                    if (writeAttemptGate.get()) {
                        gateDeferred.await()
                    }
                    return realSaleRepo.saveSale(result)
                }
            }

            val vm = MixVendasShellViewModel(
                preferenceRepository = prefRepo,
                catalogRepository = catalogRepo,
                machineRepository = machineRepo,
                saleRepository = controllableRepo,
            )
            val collectJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }

            // Aguarda catálogo chegar ao ViewModel
            var waited = 0
            while (vm.uiState.value.activeCatalogProducts.isEmpty() && waited < 40) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
                waited++
            }
            assertEquals(1, vm.uiState.value.activeCatalogProducts.size)

            vm.toggleProductSelection(prod.id)
            vm.onNoteChanged("Cliente VIP")
            vm.onPaymentMethodSelected(PreviewPaymentMethod.CASH)

            // 1) Tentativa com falha: preserva todos os campos digitados e exibe erro
            vm.saveSale()
            waited = 0
            while (vm.uiState.value.venderForm.feedbackMessage == null && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
                waited++
            }
            val failedForm = vm.uiState.value.venderForm
            assertTrue(failedForm.feedbackIsError)
            assertTrue(failedForm.feedbackMessage?.contains("Falha simulada") == true)
            assertEquals(mapOf(prod.id to 1), failedForm.selectedQuantities)
            assertEquals("Cliente VIP", failedForm.note)
            assertFalse(failedForm.isSaving)
            assertEquals(0, realSaleRepo.observeSales().first().size)

            // 2) Segunda tentativa com trava de concorrência ativa: 3 cliques simultâneos gravam apenas 1 venda
            shouldFail.set(false)
            writeAttemptGate.set(true)
            saveCallCount.set(0)

            vm.saveSale()
            vm.saveSale()
            vm.saveSale()
            delay(60)
            gateDeferred.complete(Unit)

            waited = 0
            while (vm.uiState.value.venderForm.feedbackMessage == null && waited < 80) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
                waited++
            }

            assertEquals(1, saveCallCount.get())
            assertFalse(vm.uiState.value.venderForm.feedbackIsError)
            assertEquals(1, realSaleRepo.observeSales().first().size)
            assertTrue(vm.uiState.value.venderForm.selectedQuantities.isEmpty())
            assertEquals("", vm.uiState.value.venderForm.note)
            collectJob.cancel()
        } finally {
            db.close()
        }
    }

    @Test
    fun `07 saved sales survive database reopen and vendas filters filter without altering persisted records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "reopen.db"
        context.deleteDatabase(dbName)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayMs = today.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val fortyDaysAgoMs = today.minusDays(40).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        var anaId = ""
        var brunoId = ""
        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(firstDb.personDao(), firstDb.productDao())
            val machineRepo = RoomMachineRepository(firstDb.machineDao())
            var clock = todayMs
            val saleRepo = RoomSaleRepository(firstDb, firstDb.saleDao(), clockMillis = { clock })

            val ana = catalogRepo.createPerson("Ana")
            val bruno = catalogRepo.createPerson("Bruno")
            anaId = ana.id
            brunoId = bruno.id

            val prod = catalogRepo.createProduct(
                name = "Suporte Controle",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machineRepo.createMachine(
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1 (Hoje, Pix)
            clock = todayMs
            saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Entrega centro",
                    ),
                ),
            )
            // Venda 2 (Hoje, Crédito)
            clock = todayMs + 1_000L
            saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        note = "Parcela única",
                    ),
                ),
            )
            // Venda 3 (40 dias atrás, Dinheiro)
            clock = fortyDaysAgoMs
            saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Feira antiga",
                    ),
                ),
            )
        } finally {
            firstDb.close()
        }

        // Reabre o mesmo banco em uma nova instância (simula reabrir o aplicativo)
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val vm = MixVendasShellViewModel(
                preferenceRepository = RoomPreferenceRepository(reopenedDb.preferenceDao()),
                catalogRepository = RoomCatalogRepository(reopenedDb.personDao(), reopenedDb.productDao()),
                machineRepository = RoomMachineRepository(reopenedDb.machineDao()),
                saleRepository = RoomSaleRepository(reopenedDb, reopenedDb.saleDao()),
            )
            val collectJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }

            var waited = 0
            while (vm.uiState.value.realSalesCount < 3 && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
                waited++
            }
            assertEquals(3, vm.uiState.value.realSalesCount)
            assertEquals(3, vm.uiState.value.filteredRealSales.size)
            vm.uiState.value.filteredRealSales.forEach(::assertMathematicalInvariants)

            // Filtro de período TODAY mostra 2 vendas de hoje; Room mantém 3
            vm.onPeriodFilterSelected(PreviewPeriodFilter.TODAY)
            delay(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(3, vm.uiState.value.realSalesCount)
            assertEquals(2, vm.uiState.value.filteredRealSales.size)

            // Filtro de forma de pagamento CREDIT
            vm.onSalesMethodFilterSelected(PreviewPaymentMethod.CREDIT)
            delay(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, vm.uiState.value.filteredRealSales.size)
            assertEquals(300L, vm.uiState.value.realTotalFee.rawCents)
            assertEquals(9_700L, vm.uiState.value.realTotalNet.rawCents)

            // Soma das pessoas = Total líquido
            assertEquals(
                vm.uiState.value.realTotalNet.rawCents,
                vm.uiState.value.realPersonSummaries.sumOf { it.netAmount.rawCents },
            )
            assertNotNull(vm.uiState.value.realPersonSummaries.firstOrNull { it.personId == anaId })
            assertNotNull(vm.uiState.value.realPersonSummaries.firstOrNull { it.personId == brunoId })

            // Volta para ALL e busca textual por "Feira antiga"
            vm.onSalesMethodFilterSelected(null)
            vm.onPeriodFilterSelected(PreviewPeriodFilter.ALL)
            vm.onSalesSearchChanged("Feira antiga")
            delay(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, vm.uiState.value.filteredRealSales.size)
            assertEquals(SalePaymentMethod.CASH, vm.uiState.value.filteredRealSales.single().calculation.paymentMethod)
            assertEquals(3, vm.uiState.value.realSalesCount)

            collectJob.cancel()
        } finally {
            reopenedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `08 vender enforces machine maxInstallments, coerces on machine switch, keeps 1x when disabled, and blocks invalid tiers`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (prodId, legacyId, custom18Id, noInstallmentsId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Marina")
            val prod = app.catalogRepository.createProduct(
                name = "Luminária Dragão F1",
                price = MoneyCents(20_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(20_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val legacy = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Legado",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 5 to 200, 12 to 450, 18 to 720),
                    maxInstallments = null,
                ),
            )
            val custom18 = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "PagBank 18x",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 320,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(5 to 210, 7 to 300, 13 to 550, 18 to 720),
                    maxInstallments = 18,
                ),
            )
            val noInstallments = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Crédito 1x",
                    debitRateBps = 170,
                    creditEnabled = true,
                    creditDefaultRateBps = 290,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    maxInstallments = 18,
                ),
            )
            listOf(prod.id, legacy.id, custom18.id, noInstallments.id)
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        selectProductsInVender(listOf(prodId to 0))
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()

        fun selectMachineFromPicker(machineId: String) {
            composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
            waitUiUntil {
                composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isNotEmpty()
            }
            waitUiUntil {
                runCatching {
                    composeRule.onNodeWithTag("machine_picker_list")
                        .performScrollToNode(hasTestTag("machine_picker_row_$machineId"))
                    true
                }.getOrDefault(false)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("machine_picker_row_$machineId").performClick()
            waitUiUntil {
                composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
            }
        }

        // 1) Maquininha legada (null): só 1, 2, 3, 4, 6, 12x; nunca 5x, 18x ou 24x
        selectMachineFromPicker(legacyId)
        waitUiUntil {
            composeRule.onAllNodesWithTag("installments_selector_dropdown").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("12x (+4,50%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("6x (+0,00%)").assertCountEquals(1)
        composeRule.onAllNodesWithText("5x (+", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("18x (+", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("24x (+", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("12x (+4,50%)").performClick()
        composeRule.waitForIdle()

        // 2) Troca para maquininha 18x: libera todas de 1x..18x (incluindo 5x, 7x, 13x, 18x), sem 19x..24x
        selectMachineFromPicker(custom18Id)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("5x (+2,10%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("13x (+5,50%)").performScrollTo().assertExists()
        composeRule.onNodeWithText("18x (+7,20%)").performScrollTo().assertExists()
        composeRule.onAllNodesWithText("19x (+", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("24x (+", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("18x (+7,20%)").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("10,40%", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo()
            .assertTextContains("18x")

        // 3) Troca de maquininha no meio da venda (18x -> Legada) deve ajustar automaticamente para opção válida (1x)
        selectMachineFromPicker(legacyId)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo()
            .assertTextContains("1x")
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("3,00%", substring = true)

        // 4) Maquininha com parcelamento desativado permanece em 1x (sem seletor de parcelas)
        selectMachineFromPicker(noInstallmentsId)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("installments_selector_dropdown").assertCountEquals(0)
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("2,90%", substring = true)

        // 5) Seleciona PagBank 18x em 13x (+5,50% => 8,70%) e salva venda com sucesso
        selectMachineFromPicker(custom18Id)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("13x (+5,50%)").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("13x (+5,50%)").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("8,70%", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().size == 1 }
        }

        val savedSale = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertMathematicalInvariants(savedSale)
        assertEquals(13, savedSale.calculation.machineSnapshot?.appliedInstallments)
        assertEquals(870, savedSale.calculation.effectiveFeeRateBps)
        assertEquals(1_740L, savedSale.calculation.feeTotal.rawCents)

        // 6) Validação direta no ViewModel bloqueia parcela não permitida pela maquininha
        runBlocking {
            val vm = MixVendasShellViewModel(
                preferenceRepository = app.preferenceRepository,
                catalogRepository = app.catalogRepository,
                machineRepository = app.machineRepository,
                saleRepository = app.saleRepository,
            )
            val collectJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }
            var waited = 0
            while ((vm.uiState.value.machines.size < 3 || vm.uiState.value.activeCatalogProducts.isEmpty()) && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertEquals(3, vm.uiState.value.machines.size)
            vm.toggleProductSelection(prodId)
            vm.onPaymentMethodSelected(PreviewPaymentMethod.CREDIT)
            vm.onMachineSelected(legacyId)
            vm.onInstallmentsSelected(18)

            var uiWaited = 0
            while (!vm.uiState.value.venderForm.feedbackIsError && uiWaited < 30) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                uiWaited++
            }
            assertTrue(vm.uiState.value.venderForm.feedbackIsError)
            assertEquals(1, vm.uiState.value.venderForm.selectedInstallments)
            assertTrue(vm.uiState.value.venderForm.feedbackMessage?.contains("não permite parcelamento em 18x") == true)
            collectJob.cancel()
        }
    }

    @Test
    fun `inactive products are excluded from new sales while saved sale history remains editable`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val preferenceRepo = RoomPreferenceRepository(db.preferenceDao())
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val machineRepo = RoomMachineRepository(db.machineDao())
            val saleRepo = RoomSaleRepository(db, db.saleDao())
            val person = catalogRepo.createPerson("Pessoa Produto Inativo")
            val product = catalogRepo.createProduct(
                name = "Produto Histórico",
                price = MoneyCents(1_200L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(1_200L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val vm = MixVendasShellViewModel(
                preferenceRepository = preferenceRepo,
                catalogRepository = catalogRepo,
                machineRepository = machineRepo,
                saleRepository = saleRepo,
            )
            val collectJob = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }

            var waited = 0
            while (vm.uiState.value.activeCatalogProducts.none { it.id == product.id } && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertEquals(listOf(product.id), vm.uiState.value.activeCatalogProducts.map { it.id })

            vm.toggleProductSelection(product.id)
            vm.onPaymentMethodSelected(PreviewPaymentMethod.CASH)
            vm.saveSale()
            waited = 0
            while (saleRepo.observeSales().first().isEmpty() && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            val savedSale = saleRepo.observeSales().first().single()
            assertEquals(product.id, savedSale.calculation.items.single().productId)

            catalogRepo.setProductActive(product.id, active = false)
            waited = 0
            while (vm.uiState.value.activeCatalogProducts.any { it.id == product.id } && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(vm.uiState.value.activeCatalogProducts.none { it.id == product.id })

            // Mesmo se uma seleção antiga/inválida tentar incluir o ID, uma nova venda é rejeitada.
            vm.toggleProductSelection(product.id)
            vm.saveSale()
            waited = 0
            while (vm.uiState.value.venderForm.feedbackMessage == null && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(vm.uiState.value.venderForm.feedbackIsError)
            assertTrue(vm.uiState.value.venderForm.selectedQuantities.isEmpty())
            assertTrue(vm.uiState.value.venderForm.feedbackMessage.orEmpty().contains("Selecione pelo menos um produto"))
            assertEquals(1, saleRepo.observeSales().first().size)

            val historyItem = saleRepo.getSaleById(savedSale.id)?.calculation?.items?.single()
            assertEquals(product.id, historyItem?.productId)
            assertEquals("Produto Histórico", historyItem?.productName)

            val editingLoaded = CompletableDeferred<Unit>()
            vm.startEditingSale(savedSale.id) { editingLoaded.complete(Unit) }
            waited = 0
            while (!editingLoaded.isCompleted && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(20)
                waited++
            }
            assertTrue(editingLoaded.isCompleted)
            val editForm = vm.uiState.value.venderForm
            assertTrue(editForm.historicalProductsById.containsKey(product.id))
            assertTrue(
                MixVendasShellViewModel.pickerCatalogProducts(editForm, vm.uiState.value.activeCatalogProducts)
                    .any { it.id == product.id },
            )
            collectJob.cancel()
        } finally {
            db.close()
        }
    }
}