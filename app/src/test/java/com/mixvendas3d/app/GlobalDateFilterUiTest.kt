package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Validação global dos filtros de data em todo o aplicativo:
 * - Padrão visual exclusivo `DD/MM/AAAA` (nenhum campo com `AAAA-MM-DD`).
 * - Seleção interativa via calendário (navegação de mês/dia, Confirmar, Cancelar preservando valor anterior, Limpar).
 * - Validação de intervalo invertido (De > Até).
 * - Filtragem real consistente em VENDAS, Fechamento (incluindo exportação CSV) e helpers de formatação.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalDateFilterUiTest {

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

    private suspend fun insertRealSaleAt(
        app: MixVendasApplication,
        epochMs: Long,
        items: List<SaleItemInput>,
        paymentMethod: SalePaymentMethod,
    ) {
        val repo = RoomSaleRepository(
            database = app.database,
            saleDao = app.database.saleDao(),
            clockMillis = { epochMs },
        )
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = items,
                finalAmountOverride = null,
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = paymentMethod,
            ),
        )
        repo.saveSale(calc)
    }

    @Test
    fun `01 VENDAS and FECHAMENTO use DD MM AAAA interactive calendar with confirm cancel clear and range validation`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val pastDate = today.minusDays(35)
        val midDate = today.minusDays(10)

        runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prodOld = app.catalogRepository.createProduct(
                name = "Peça Antiga",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodToday = app.catalogRepository.createProduct(
                name = "Peça Hoje",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            insertRealSaleAt(
                app = app,
                epochMs = pastDate.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prodOld, 1)),
                paymentMethod = SalePaymentMethod.PIX,
            )
            insertRealSaleAt(
                app = app,
                epochMs = today.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prodToday, 1)),
                paymentMethod = SalePaymentMethod.CASH,
            )
        }

        // 1) Aba VENDAS -> Datas personalizadas
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_custom").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("period_from_input").fetchSemanticsNodes().isNotEmpty()
        }

        // Padrão visual DD/MM/AAAA (sem nenhum texto AAAA-MM-DD)
        composeRule.onAllNodesWithText("AAAA-MM-DD", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("period_from_input").assertTextContains("DD/MM/AAAA", substring = true)
        composeRule.onNodeWithTag("period_to_input").assertTextContains("DD/MM/AAAA", substring = true)

        // Seleciona data inicial e final via calendário interativo para pastDate
        PeriodCalendarTestHelper.pickDate(composeRule, "period_from_input", pastDate)
        PeriodCalendarTestHelper.pickDate(composeRule, "period_to_input", pastDate)

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("sales_top_summary_card")
                    .assert(hasAnyDescendant(hasText("1 venda", substring = true)))
                    .assert(hasAnyDescendant(hasText("R$ 80,00", substring = true)))
                true
            }.getOrDefault(false)
        }

        // Cancelar calendário não altera a data previamente selecionada
        PeriodCalendarTestHelper.openPicker(composeRule, "period_from_input")
        composeRule.onNodeWithTag("period_date_picker_cancel").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("period_date_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("period_from_input")
            .assertTextContains(CurrencyFormatter.formatLocalDate(pastDate), substring = true)

        // Intervalo invertido (De = today > Até = pastDate) exibe aviso claro
        PeriodCalendarTestHelper.pickDate(composeRule, "period_from_input", today, currentlySelectedDate = pastDate)
        waitUiUntil {
            composeRule.onAllNodesWithTag("period_invalid_range_warning").fetchSemanticsNodes().isNotEmpty()
        }

        // Limpar data pelo botão dentro do campo e restaurar todas (R$ 130,00)
        composeRule.onNodeWithTag("period_from_clear").performClick()
        composeRule.onNodeWithTag("period_to_clear").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("sales_top_summary_card")
                    .assert(hasAnyDescendant(hasText("2 vendas", substring = true)))
                    .assert(hasAnyDescendant(hasText("R$ 130,00", substring = true)))
                true
            }.getOrDefault(false)
        }

        // 2) Aba FECHAMENTO -> mesmo componente e comportamento DD/MM/AAAA + calendário + CSV sincronizado
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_fechamento").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("period_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_custom").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("period_from_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("AAAA-MM-DD", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("period_from_input").assertTextContains("DD/MM/AAAA", substring = true)

        PeriodCalendarTestHelper.pickDate(composeRule, "period_from_input", midDate)
        PeriodCalendarTestHelper.pickDate(composeRule, "period_to_input", today)

        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("1 venda", substring = true)
                    .assertTextContains("R$ 50,00", substring = true)
                true
            }.getOrDefault(false)
        }

        // Botão Limpar dentro do próprio calendário limpa o filtro
        PeriodCalendarTestHelper.openPicker(composeRule, "period_from_input")
        composeRule.onNodeWithTag("period_date_picker_clear").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("period_date_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("period_from_input").assertTextContains("DD/MM/AAAA", substring = true)

        // Exportar CSV com intervalo personalizado em DD/MM/AAAA funciona sem erro
        composeRule.onNodeWithTag("export_csv_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("csv_export_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("csv_export_confirm_button").assertIsDisplayed()
        composeRule.onNodeWithTag("csv_export_cancel_button").performClick()
    }

    @Test
    fun `02 flexible BR date parser formats DD MM AAAA and preserves strict ISO parsing compatibility`() {
        assertEquals(LocalDate.of(2026, 3, 9), CurrencyFormatter.parseBrDate("09/03/2026"))
        assertNull(CurrencyFormatter.parseBrDate("2026-03-09"))
        assertNull(CurrencyFormatter.parseBrDate("31/02/2026"))

        assertEquals(LocalDate.of(2026, 3, 9), CurrencyFormatter.parseFlexibleFilterDate("09/03/2026"))
        assertEquals(LocalDate.of(2026, 3, 9), CurrencyFormatter.parseFlexibleFilterDate("2026-03-09"))
        assertNull(CurrencyFormatter.parseFlexibleFilterDate("99/99/2026"))

        assertEquals("09/03/2026", CurrencyFormatter.formatFilterDateDisplay("2026-03-09"))
        assertEquals("09/03/2026", CurrencyFormatter.formatFilterDateDisplay("09/03/2026"))
        assertEquals("invalido", CurrencyFormatter.formatFilterDateDisplay("invalido"))
        assertEquals("", CurrencyFormatter.formatFilterDateDisplay("   "))
    }
}
