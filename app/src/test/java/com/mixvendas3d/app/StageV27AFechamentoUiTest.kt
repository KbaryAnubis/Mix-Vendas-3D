package com.mixvendas3d.app

import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.repository.RoomSaleRepository
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
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV27AFechamentoUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
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

    private fun openFechamento() {
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_fechamento").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private suspend fun insertRealSaleAt(
        app: MixVendasApplication,
        epochMs: Long,
        items: List<SaleItemInput>,
        finalAmountOverride: MoneyCents? = null,
        discountMode: SaleDiscountInput = SaleDiscountInput.Proportional,
        paymentMethod: SalePaymentMethod = SalePaymentMethod.PIX,
        machineId: String? = null,
        cardBrand: CardBrand? = null,
        installments: Int? = null,
        manualRateOverrideBps: Int? = null,
    ): String {
        val machine = if (machineId != null) {
            app.machineRepository.observeMachines().first().first { it.id == machineId }
        } else {
            null
        }
        val repo = RoomSaleRepository(
            database = app.database,
            saleDao = app.database.saleDao(),
            clockMillis = { epochMs },
        )
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = items,
                finalAmountOverride = finalAmountOverride,
                discountMode = discountMode,
                paymentMethod = paymentMethod,
                machine = machine,
                cardBrand = cardBrand,
                installments = installments,
                manualRateOverrideBps = manualRateOverrideBps,
            ),
        )
        return repo.saveSale(calc).id
    }

    @Test
    fun `01 clean header without legacy top bar, empty states, and absence of print or fake routes`() {
        openFechamento()

        composeRule.onNodeWithTag("closing_header_title")
            .assertIsDisplayed()
            .assertTextContains("Fechamento")

        composeRule.onAllNodesWithTag("topbar_title").assertCountEquals(0)
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("FECHAMENTO DO PERÍODO", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("RELATÓRIO DO PERÍODO", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Conferir valores", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("MIX VENDAS 3D", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Imprimir", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Vendas do fechamento", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithTag("split_balance_banner").assertCountEquals(0)

        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("Total vendido", substring = true)
            .assertTextContains("0 vendas", substring = true)
            .assertTextContains("R$ 0,00", substring = true)

        composeRule.onNodeWithTag("closing_individual_empty_message")
            .performScrollTo()
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)

        composeRule.onNodeWithTag("closing_payments_empty_message")
            .performScrollTo()
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)

        composeRule.onNodeWithTag("closing_machines_empty_message")
            .performScrollTo()
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)

        composeRule.onNodeWithTag("export_csv_button")
            .performScrollTo()
            .assertIsEnabled()
            .assertTextContains("Exportar relatório CSV", substring = true)
    }

    @Test
    fun `02 period selector supports all options and custom dates isolated from inicio and vendas`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Produto Período",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            insertRealSaleAt(
                app = app,
                epochMs = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.PIX,
            )
            insertRealSaleAt(
                app = app,
                epochMs = today.minusDays(20).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 2)),
                paymentMethod = SalePaymentMethod.CASH,
            )
        }

        openFechamento()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card").assertTextContains("R$ 150,00", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("period_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_all").assertIsDisplayed()
        composeRule.onNodeWithTag("period_option_today").assertIsDisplayed()
        composeRule.onNodeWithTag("period_option_week").assertIsDisplayed()
        composeRule.onNodeWithTag("period_option_month").assertIsDisplayed()
        composeRule.onNodeWithTag("period_option_custom").assertIsDisplayed().performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("period_from_input").fetchSemanticsNodes().isNotEmpty()
        }
        val oldDay = today.minusDays(20).toString()
        composeRule.onNodeWithTag("period_from_input").performTextReplacement(oldDay)
        composeRule.onNodeWithTag("period_to_input").performTextReplacement(oldDay)

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("1 venda", substring = true)
                    .assertTextContains("R$ 100,00", substring = true)
                true
            }.getOrDefault(false)
        }

        // Verifica mensagem específica para período sem vendas mantendo diferenciação de banco vazio
        val emptyDay = today.minusDays(90).toString()
        composeRule.onNodeWithTag("period_from_input").performTextReplacement(emptyDay)
        composeRule.onNodeWithTag("period_to_input").performTextReplacement(emptyDay)
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("closing_individual_empty_message")
                    .performScrollTo()
                    .assertTextContains("Nenhuma venda registrada neste período.", substring = true)
                true
            }.getOrDefault(false)
        }

        // Aba Início e aba VENDAS permanecem independentes ("Todos" -> R$ 150,00)
        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_inicio").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 150,00", substring = true)

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().assertTextContains("Todos", substring = true)
    }

    @Test
    fun `03 unlimited participants grid, individual conference, real payment breakdowns, and collapsible machines`() {
        val zone = ZoneId.systemDefault()
        val nowMs = LocalDate.now(zone).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (peopleIds, machineId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Ana")
            val p2 = app.catalogRepository.createPerson("Bruno")
            val p3 = app.catalogRepository.createPerson("Carla")
            val p4 = app.catalogRepository.createPerson("Diego")

            val prodA = app.catalogRepository.createProduct(
                name = "Kit A",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(2_500L)),
                    ProductParticipantDraft(p2.id, MoneyCents(1_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Kit B",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p3.id, MoneyCents(3_500L)),
                    ProductParticipantDraft(p4.id, MoneyCents(2_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Neon",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 400,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            insertRealSaleAt(
                app = app,
                epochMs = nowMs,
                items = listOf(SaleItemInput(prodA, 1)),
                paymentMethod = SalePaymentMethod.PIX,
            )
            insertRealSaleAt(
                app = app,
                epochMs = nowMs + 1_000L,
                items = listOf(SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(5_000L),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = machine.id,
                cardBrand = CardBrand.VISA,
                installments = 1,
                manualRateOverrideBps = 1000, // 10,00% manual -> taxa R$ 5,00, líq R$ 45,00
            )
            listOf(p1.id, p2.id, p3.id, p4.id) to machine.id
        }

        openFechamento()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card").assertTextContains("R$ 90,00", substring = true)
                true
            }.getOrDefault(false)
        }

        // Resumo hero
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 100,00", substring = true) // subtotal original
            .assertTextContains("− R$ 10,00", substring = true) // descontos concedidos
            .assertTextContains("R$ 90,00", substring = true) // total vendido
            .assertTextContains("− R$ 5,00", substring = true) // tarifas
            .assertTextContains("R$ 85,00", substring = true) // líquido

        // Todas as 4 pessoas exibidas na grade, sem "Ver mais" e sem porcentagem nos valores dos participantes
        peopleIds.forEach { pid ->
            composeRule.onNodeWithTag("closing_person_card_$pid")
                .performScrollTo()
                .assertIsDisplayed()
        }
        composeRule.onAllNodesWithTag("toggle_more_people_button").assertCountEquals(0)

        // Conferência individual sem barra colorida
        composeRule.onAllNodesWithTag("split_balance_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("closing_individual_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("closing_individual_row_${peopleIds[0]}")
            .performScrollTo()
            .assertTextContains("Ana", substring = true)
            .assertTextContains("R$ 25,00", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_${peopleIds[3]}")
            .performScrollTo()
            .assertTextContains("Diego", substring = true)

        // Formas de pagamento
        composeRule.onNodeWithTag("closing_payment_row_pix")
            .performScrollTo()
            .assertTextContains("Pix", substring = true)
            .assertTextContains("R$ 40,00", substring = true)
        composeRule.onNodeWithTag("closing_payment_row_credit")
            .performScrollTo()
            .assertTextContains("Crédito", substring = true)
            .assertTextContains("R$ 50,00", substring = true)
            .assertTextContains("Líq. R$ 45,00", substring = true)

        // Maquininhas expansível: recolher e reexpandir preserva dados e tarifa manual
        composeRule.onNodeWithTag("closing_machines_card").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Ton Neon", substring = true).assertCountEquals(1)
        composeRule.onNodeWithTag("closing_machines_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Ton Neon", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("closing_machines_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Ton Neon", substring = true).assertCountEquals(1)
        composeRule.onNodeWithTag("closing_machine_row_${machineId}|Ton Neon|CREDIT")
            .performScrollTo()
            .assertTextContains("10,00% (tarifa manual)", substring = true)
            .assertTextContains("Líquido R$ 45,00", substring = true)
    }

    @Test
    fun `04 csv export button preserves dialog options and launches SAF document creation`() {
        val zone = ZoneId.systemDefault()
        val nowMs = LocalDate.now(zone).atTime(11, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana CSV")
            val prod = app.catalogRepository.createProduct(
                name = "Item CSV",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            insertRealSaleAt(
                app = app,
                epochMs = nowMs,
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CASH,
            )
        }

        openFechamento()
        composeRule.onNodeWithTag("export_csv_button")
            .performScrollTo()
            .assertIsEnabled()
            .assertTextContains("Exportar relatório CSV", substring = true)
            .performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("csv_export_option_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_option_detailed").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_option_both").assertIsDisplayed()

        composeRule.onNodeWithTag("csv_export_confirm_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val started = shadowActivity.nextStartedActivityForResult
        assertNotNull(started)
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
        assertEquals("text/csv", started.intent.type)
    }
}
