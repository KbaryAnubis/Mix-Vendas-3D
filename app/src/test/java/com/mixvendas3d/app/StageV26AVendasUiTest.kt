package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV26AVendasUiTest {

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

    private fun openVendasTab() {
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun scrollVendasToTag(tag: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag(tag))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `01 clean VENDAS header and empty states omit legacy topbar, demo sales, and redundant green sum`() {
        openVendasTab()

        composeRule.onNodeWithTag("vendas_header_title")
            .assertIsDisplayed()
            .assertTextContains("VENDAS")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("HISTÓRICO DETALHADO", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("MIX VENDAS 3D", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Soma das pessoas", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag("sales_filters_card").assertIsDisplayed()
        composeRule.onNodeWithTag("period_dropdown_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sales_search_input")
            .assertTextContains("Buscar por produto, observação...")
        composeRule.onNodeWithTag("sales_method_filter_dropdown")
            .assertTextContains("Todas as formas")

        scrollVendasToTag("vendas_empty_state")
        composeRule.onNodeWithTag("vendas_empty_state").assertIsDisplayed()
        composeRule.onNodeWithTag("vendas_empty_sell_cta").assertIsEnabled()
        composeRule.onAllNodesWithTag("sale_history_card_demo-1").assertCountEquals(0)
    }

    @Test
    fun `02 functional period, search, and payment filters work together and show filter empty state`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val zone = ZoneId.systemDefault()
        val todayMs = LocalDate.now(zone).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val fortyDaysAgoMs = LocalDate.now(zone).minusDays(40).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

        val (pixSaleId, creditSaleId, oldCashSaleId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Marina")
            val prod = app.catalogRepository.createProduct(
                name = "Luminária Dragão",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            var clock = todayMs
            val clockedSaleRepo = com.mixvendas3d.app.data.repository.RoomSaleRepository(
                app.database,
                app.database.saleDao(),
                clockMillis = { clock },
            )
            val s1 = clockedSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Entrega rápida",
                    ),
                ),
            )
            clock = todayMs + 60_000L
            val s2 = clockedSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        note = "Balcão",
                    ),
                ),
            )
            clock = fortyDaysAgoMs
            val s3 = clockedSaleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                        note = "Feira antiga",
                    ),
                ),
            )
            Triple(s1.id, s2.id, s3.id)
        }

        openVendasTab()
        waitUiUntil {
            composeRule.onAllNodesWithText("3 vendas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Filtro 1: Hoje -> oculta venda antiga
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("period_option_today").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("2 vendas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("sale_history_card_$oldCashSaleId").assertCountEquals(0)

        // Filtro 3: Forma de pagamento Crédito -> exibe somente s2
        composeRule.onNodeWithTag("sales_method_filter_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Crédito").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Crédito").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("1 venda", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        scrollVendasToTag("sale_history_card_$creditSaleId")
        composeRule.onNodeWithTag("sale_history_card_$creditSaleId").assertExists()
        composeRule.onAllNodesWithTag("sale_history_card_$pixSaleId").assertCountEquals(0)

        // Filtro 2: Pesquisa sem resultado exibe vendas_filter_empty_state
        scrollVendasToTag("sales_filters_card")
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextInput("Inexistente XYZ")
        scrollVendasToTag("vendas_filter_empty_state")
        composeRule.onNodeWithTag("vendas_filter_empty_state").assertIsDisplayed()
    }

    @Test
    fun `03 sale card renders real snapshots, expandable product and participant splits, no proportional bar or green sum`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (saleId, prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Pessoa 1")
            val p2 = app.catalogRepository.createPerson("Pessoa 2")
            val prodA = app.catalogRepository.createProduct(
                name = "Produto Alfa",
                price = MoneyCents(2_200L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(1_700L)),
                    ProductParticipantDraft(p2.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Produto Beta",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(400L)),
                    ProductParticipantDraft(p2.id, MoneyCents(1_600L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Subtotal 42,00, desconto específico em Produto Alfa de 2,00 -> Final 40,00 no Pix
            val saved = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                        finalAmountOverride = MoneyCents(4_000L),
                        discountMode = SaleDiscountInput.SpecificProduct(prodA.id),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Entrega combinada",
                    ),
                ),
            )
            // Renomeia e altera preço no catálogo para provar preservação do snapshot histórico
            app.catalogRepository.updateProduct(
                productId = prodA.id,
                name = "Produto Alfa Alterado",
                price = MoneyCents(9_900L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(9_900L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            Triple(saved.id, prodA.id, prodB.id)
        }
        val persisted = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }

        openVendasTab()
        scrollVendasToTag("sale_history_card_$saleId")

        // Sem soma verde redundante nem porcentagens
        composeRule.onAllNodesWithText("Soma das pessoas", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("sale_history_card_$saleId")
            .assert(hasAnyDescendant(hasText(CurrencyFormatter.formatEpochMillis(persisted.soldAtEpochMs), substring = true)))
            .assert(hasAnyDescendant(hasText("2 produtos", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 40,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Subtotal dos produtos", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 42,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Total cobrado", substring = true)))
            .assert(hasAnyDescendant(hasText("Pix", substring = true)))
            .assert(hasAnyDescendant(hasText("Líquido total de cada pessoa", substring = true)))
            .assert(hasAnyDescendant(hasText("Líquido individual", substring = true)))
            .assert(hasAnyDescendant(hasText("Entrega combinada", substring = true)))

        // Expande detalhamento por produto e verifica snapshots originais preservados
        composeRule.onNodeWithTag("sale_details_toggle_$saleId")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_item_detail_${saleId}_$prodAId")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_item_detail_${saleId}_$prodAId")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("1× Produto Alfa", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 20,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Valor original: R$ 22,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Desconto − R$ 2,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Pessoa 1", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 15,45", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 4,55", substring = true)))

        composeRule.onNodeWithTag("sale_item_detail_${saleId}_$prodBId")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("1× Produto Beta", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 20,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Valor original: R$ 20,00", substring = true)))

        // Não pode mostrar nome alterado do catálogo após a venda
        composeRule.onAllNodesWithText("Produto Alfa Alterado", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun `04 edit and delete buttons work with confirmation dialog preserved`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val saleId = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Carlos")
            val prod = app.catalogRepository.createProduct(
                name = "Suporte 3D",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "Venda teste ação",
                    ),
                ),
            ).id
        }

        openVendasTab()
        scrollVendasToTag("sale_history_card_$saleId")

        // Editar leva para tela Vender em modo de edição
        composeRule.onNodeWithTag("sale_edit_$saleId")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cancel_edit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Volta para VENDAS e valida diálogo de exclusão
        openVendasTab()
        scrollVendasToTag("sale_delete_$saleId")
        composeRule.onNodeWithTag("sale_delete_$saleId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_sale_cancel_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("sale_history_card_$saleId").assertExists()

        composeRule.onNodeWithTag("sale_delete_$saleId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vendas_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vendas_empty_state").assertIsDisplayed()
    }
}
