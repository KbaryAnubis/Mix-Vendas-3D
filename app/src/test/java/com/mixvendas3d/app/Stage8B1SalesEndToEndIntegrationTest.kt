package com.mixvendas3d.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage8B1SalesEndToEndIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var shellHarness: MixVendasShellTestHarness
    private var savedTimeZone: TimeZone? = null

    @Before
    fun setUp() {
        shellHarness = MixVendasShellTestHarness(composeRule)
        shellHarness.mountAppShell()
    }

    @After
    fun tearDown() {
        savedTimeZone?.let { TimeZone.setDefault(it) }
        savedTimeZone = null
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

    private fun scrollNodeToTag(containerTag: String, targetTag: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag(containerTag)
                    .performScrollToNode(hasTestTag(targetTag))
                composeRule.onNodeWithTag(targetTag, useUnmergedTree = true).performScrollTo()
                true
            }.getOrDefault(false)
        }
    }

    private fun openTab(tabTag: String, screenTag: String) {
        composeRule.onNodeWithTag(tabTag).performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag(screenTag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pickProductsInVender(productIds: List<String>) {
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        productIds.forEach { productId ->
            waitUiUntil {
                runCatching {
                    composeRule.onNodeWithTag("product_picker_list")
                        .performScrollToNode(hasTestTag("picker_row_$productId"))
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag("picker_checkbox_$productId").performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun selectMachineInVender(machineId: String) {
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
        note: String = "",
    ): PersistedSale {
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
                note = note,
            ),
        )
        return repo.saveSale(calc)
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
            calc.finalAmount.rawCents - calc.feeTotal.rawCents,
        )
        assertEquals(
            calc.netTotal.rawCents,
            calc.personTotals.sumOf { it.netAmount.rawCents },
        )
        assertEquals(
            calc.finalAmount.rawCents,
            calc.personTotals.sumOf { it.grossAmount.rawCents },
        )
        assertEquals(
            calc.feeTotal.rawCents,
            calc.personTotals.sumOf { it.feeAmount.rawCents },
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

    private fun querySingleInt(app: MixVendasApplication, sql: String): Int {
        return app.database.openHelper.writableDatabase.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    @Test
    fun `01 real multi-product sale registered via vender UI persists in room, appears in vendas, and updates inicio and fechamento with zero cent divergence`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, carlaId, prodAId, prodBId, machId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val carla = app.catalogRepository.createPerson("Carla")

            // Produto A (R$ 60,00): Ana 40,00 + Bruno 20,00 | Tarifa INDIVIDUAL na Ana
            val prodA = app.catalogRepository.createProduct(
                name = "Dragão Dourado",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            // Produto B (R$ 40,00): Bruno 20,00 + Carla 20,00 | Tarifa EQUAL
            val prodB = app.catalogRepository.createProduct(
                name = "Vaso Prisma",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(carla.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Smart",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 400,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(
                        CardBrand.VISA to 400,
                        CardBrand.MASTERCARD to 450,
                    ),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100, 3 to 150),
                ),
            )
            SixTuple(ana.id, bruno.id, carla.id, prodA.id, prodB.id, machine.id)
        }

        // 1) Fluxo completo na aba VENDER
        openTab("nav_tab_vender", "screen_vender")
        pickProductsInVender(listOf(prodAId, prodBId))

        // 2x Dragão (120,00) + 1x Vaso (40,00) = Subtotal 160,00
        composeRule.onNodeWithTag("selected_inc_$prodAId").performScrollTo().performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("sale_final_value_input").assertTextContains("160,00")
                true
            }.getOrDefault(false)
        }

        // Desconto específico de R$ 20,00 somente no Vaso -> Valor final R$ 140,00
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("140,00")
        waitUiUntil {
            composeRule.onAllNodesWithTag("discount_products_box").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("discount_product_row_$prodAId").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextReplacement("Pedido Loja 3D")

        // Crédito Mastercard 3x (4,50% + 1,50% = 6,00%)
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_selector_dropdown").fetchSemanticsNodes().isNotEmpty()
        }
        selectMachineInVender(machId)

        composeRule.onNodeWithTag("brand_selector_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Mastercard", substring = true).onLast().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("installments_selector_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("3x (+", substring = true).onLast().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Venda registrada com sucesso", substring = true)
            .assertTextContains("R$ 140,00", substring = true)

        // 2) Conferência direta no Room sem divergência de centavos
        val persistedSales = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        assertEquals(1, persistedSales.size)
        val saved = persistedSales.single()
        assertMathematicalInvariants(saved)

        // Totais esperados:
        // Dragão 120,00 (Ana 80,00, Bruno 40,00), taxa 6% = 7,20 (100% Ana) -> Ana líq 72,80 | Bruno líq 40,00
        // Vaso 20,00 (Bruno 10,00, Carla 10,00), taxa 6% = 1,20 (EQUAL: Bruno 0,60, Carla 0,60) -> Bruno líq 9,40 | Carla líq 9,40
        // Consolidado: Bruto 140,00 | Taxa 8,40 | Líq 131,60 | Ana 72,80 | Bruno 49,40 | Carla 9,40
        assertEquals(16_000L, saved.calculation.originalSubtotal.rawCents)
        assertEquals(2_000L, saved.calculation.discountTotal.rawCents)
        assertEquals(14_000L, saved.calculation.finalAmount.rawCents)
        assertEquals(840L, saved.calculation.feeTotal.rawCents)
        assertEquals(13_160L, saved.calculation.netTotal.rawCents)
        assertEquals(600, saved.calculation.effectiveFeeRateBps)
        assertEquals("Pedido Loja 3D", saved.calculation.note)
        assertEquals(CardBrand.MASTERCARD, saved.calculation.machineSnapshot?.appliedBrand)
        assertEquals(3, saved.calculation.machineSnapshot?.appliedInstallments)
        val totalsByPerson = saved.calculation.personTotals.associateBy { it.personId }
        assertEquals(7_280L, totalsByPerson.getValue(anaId).netAmount.rawCents)
        assertEquals(4_940L, totalsByPerson.getValue(brunoId).netAmount.rawCents)
        assertEquals(940L, totalsByPerson.getValue(carlaId).netAmount.rawCents)

        // 3) Integração com histórico VENDAS (sem PreviewFixtures, com detalhes expandidos)
        openTab("nav_tab_vendas", "screen_vendas")
        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("sales_top_summary_card")
            .assert(hasAnyDescendant(hasText("1 venda", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 140,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Descontos: R$ 20,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Taxas: R$ 8,40", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 131,60", substring = true)))

        scrollNodeToTag("screen_vendas", "sale_history_card_${saved.id}")
        composeRule.onNodeWithTag("sale_history_card_${saved.id}")
            .assert(hasAnyDescendant(hasText("2× Dragão Dourado + 1× Vaso Prisma", substring = true)))
            .assert(hasAnyDescendant(hasText(CurrencyFormatter.formatEpochMillis(saved.soldAtEpochMs), substring = true)))
            .assert(hasAnyDescendant(hasText("Stone Smart · Mastercard · 3x", substring = true)))
            .assert(hasAnyDescendant(hasText("6,00%", substring = true)))
            .assert(hasAnyDescendant(hasText("Pedido Loja 3D", substring = true)))

        composeRule.onNodeWithTag("sale_details_toggle_${saved.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_item_detail_${saved.id}_$prodAId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_item_detail_${saved.id}_$prodAId")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("R$ 120,00", substring = true)))
            .assert(hasAnyDescendant(hasText("− R$ 7,20", substring = true)))
        composeRule.onNodeWithTag("sale_item_detail_${saved.id}_$prodBId")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("R$ 20,00", substring = true)))
            .assert(hasAnyDescendant(hasText("− R$ 1,20", substring = true)))

        // 4) Integração com INÍCIO
        openTab("nav_tab_inicio", "screen_inicio")
        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 140,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees").assertTextContains("R$ 8,40", substring = true)
        scrollNodeToTag("screen_inicio", "home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 131,60", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("1", substring = true)

        scrollNodeToTag("screen_inicio", "home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 72,80", substring = true)
        composeRule.onNodeWithTag("home_person_card_$brunoId")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 49,40", substring = true)
        scrollNodeToTag("screen_inicio", "toggle_more_people_button")
        composeRule.onNodeWithTag("toggle_more_people_button").performClick()
        scrollNodeToTag("screen_inicio", "home_person_card_$carlaId")
        composeRule.onNodeWithTag("home_person_card_$carlaId")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 9,40", substring = true)

        // 5) Integração com FECHAMENTO
        openTab("nav_tab_fechamento", "screen_fechamento")
        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 160,00", substring = true)
            .assertTextContains("− R$ 20,00", substring = true)
            .assertTextContains("R$ 140,00", substring = true)
            .assertTextContains("− R$ 8,40", substring = true)
            .assertTextContains("R$ 131,60", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$anaId")
            .performScrollTo()
            .assertTextContains("R$ 72,80", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$brunoId")
            .performScrollTo()
            .assertTextContains("R$ 49,40", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$carlaId")
            .performScrollTo()
            .assertTextContains("R$ 9,40", substring = true)
        composeRule.onNodeWithTag("closing_payment_row_credit")
            .performScrollTo()
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 140,00", substring = true)
            .assertTextContains("Líq. R$ 131,60", substring = true)
        composeRule.onNodeWithTag("closing_machine_row_$machId|Stone Smart|CREDIT")
            .performScrollTo()
            .assertTextContains("Stone Smart", substring = true)
            .assertTextContains("6,00%", substring = true)
            .assertTextContains("Líquido R$ 131,60", substring = true)
        composeRule.onAllNodesWithTag("split_balance_banner").assertCountEquals(0)
    }

    @Test
    fun `02 editing a sale via UI preserves ID and original date, avoids duplication, leaves other sales intact, and updates vendas inicio and fechamento`() {
        val zone = ZoneId.systemDefault()
        val yesterdayMs = LocalDate.now(zone).minusDays(1).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val todayMorningMs = LocalDate.now(zone).atTime(9, 15).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, prodAId, prodBId, machId, preservedSale, targetSale) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")

            val prodA = app.catalogRepository.createProduct(
                name = "Caneca 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Chaveiro Luxo",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(bruno.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 200, // 2,00%
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda preservada: 1x Chaveiro (R$ 50,00) em Dinheiro
            val kept = insertRealSaleAt(
                app = app,
                epochMs = yesterdayMs,
                items = listOf(SaleItemInput(prodB, 1)),
                paymentMethod = SalePaymentMethod.CASH,
                note = "Venda antiga preservada",
            )
            // Venda alvo: 1x Caneca (R$ 100,00) no Pix
            val target = insertRealSaleAt(
                app = app,
                epochMs = todayMorningMs,
                items = listOf(SaleItemInput(prodA, 1)),
                paymentMethod = SalePaymentMethod.PIX,
                note = "Venda alvo inicial",
            )
            SevenTuple(ana.id, bruno.id, prodA.id, prodB.id, machine.id, kept, target)
        }

        openTab("nav_tab_vendas", "screen_vendas")
        scrollNodeToTag("screen_vendas", "sale_history_card_${targetSale.id}")
        composeRule.onNodeWithTag("sale_edit_${targetSale.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Edita quantidade (1x -> 2x Caneca = 200), adiciona 1x Chaveiro (50) => Subtotal 250,00
        composeRule.onNodeWithTag("selected_inc_$prodAId").performScrollTo().performClick()
        pickProductsInVender(listOf(prodBId))

        // Desconto proporcional para R$ 220,00 (Caneca 176,00 [Ana], Chaveiro 44,00 [Bruno])
        // Muda pagamento para Débito Stone Pro 2,00% -> Taxa R$ 4,40 (Ana 3,52, Bruno 0,88), Líq R$ 215,60
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextReplacement("220,00")
        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextReplacement("Editada fim a fim")
        composeRule.onNodeWithTag("pay_method_debit").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_selector_dropdown").fetchSemanticsNodes().isNotEmpty()
        }
        selectMachineInVender(machId)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_feedback_banner")
            .assertTextContains("Alterações salvas com sucesso", substring = true)

        // Room: nenhuma duplicação, mesmo ID e mesma data original, outra venda intocada
        runBlocking(Dispatchers.IO) {
            val allSales = app.saleRepository.observeSales().first()
            assertEquals(2, allSales.size)
            assertEquals(2, app.database.saleDao().countSales())

            val edited = app.saleRepository.getSaleById(targetSale.id)
            assertNotNull(edited)
            assertEquals(targetSale.id, edited!!.id)
            assertEquals(targetSale.soldAtEpochMs, edited.soldAtEpochMs)
            assertTrue(edited.updatedAtEpochMs >= edited.soldAtEpochMs)
            assertMathematicalInvariants(edited)

            assertEquals(25_000L, edited.calculation.originalSubtotal.rawCents)
            assertEquals(3_000L, edited.calculation.discountTotal.rawCents)
            assertEquals(22_000L, edited.calculation.finalAmount.rawCents)
            assertEquals(440L, edited.calculation.feeTotal.rawCents)
            assertEquals(21_560L, edited.calculation.netTotal.rawCents)
            assertEquals(SalePaymentMethod.DEBIT, edited.calculation.paymentMethod)
            assertEquals("Editada fim a fim", edited.calculation.note)
            val perPerson = edited.calculation.personTotals.associateBy { it.personId }
            assertEquals(17_248L, perPerson.getValue(anaId).netAmount.rawCents)
            assertEquals(4_312L, perPerson.getValue(brunoId).netAmount.rawCents)

            // Demais vendas preservadas sem recálculo
            val stillKept = app.saleRepository.getSaleById(preservedSale.id)
            assertEquals(preservedSale, stillKept)
        }

        // Totais acumulados esperados: Preserved (50,00) + Edited (220,00) = 270,00 bruto, 4,40 taxa, 265,60 líq
        // Ana = 172,48 | Bruno = 50,00 + 43,12 = 93,12
        openTab("nav_tab_vendas", "screen_vendas")
        scrollNodeToTag("screen_vendas", "sales_top_summary_card")
        composeRule.onNodeWithTag("sales_top_summary_card")
            .assert(hasAnyDescendant(hasText("2 vendas", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 270,00", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 265,60", substring = true)))

        openTab("nav_tab_inicio", "screen_inicio")
        scrollNodeToTag("screen_inicio", "home_metric_total_sold")
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 270,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees").assertTextContains("R$ 4,40", substring = true)
        scrollNodeToTag("screen_inicio", "home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 265,60", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("2", substring = true)
        scrollNodeToTag("screen_inicio", "home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 172,48", substring = true)
        composeRule.onNodeWithTag("home_person_card_$brunoId")
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 93,12", substring = true)

        openTab("nav_tab_fechamento", "screen_fechamento")
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 300,00", substring = true)
            .assertTextContains("− R$ 30,00", substring = true)
            .assertTextContains("R$ 270,00", substring = true)
            .assertTextContains("− R$ 4,40", substring = true)
            .assertTextContains("R$ 265,60", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$anaId")
            .performScrollTo()
            .assertTextContains("R$ 172,48", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$brunoId")
            .performScrollTo()
            .assertTextContains("R$ 93,12", substring = true)
        composeRule.onNodeWithTag("closing_payment_row_debit")
            .performScrollTo()
            .assertTextContains("R$ 220,00", substring = true)
            .assertTextContains("Líq. R$ 215,60", substring = true)
    }

    @Test
    fun `03 deleting a sale via UI removes dependent rows with zero orphans, preserves catalog and other sales, and updates inicio and fechamento`() {
        val zone = ZoneId.systemDefault()
        val nowMs = LocalDate.now(zone).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, saleToDelete, preservedSale) = runBlocking(Dispatchers.IO) {
            app.preferenceRepository.savePreference("stage8b1_keep", "true")
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")

            val prodA = app.catalogRepository.createProduct(
                name = "Luminária Apagar",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Suporte Manter",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(bruno.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Básica",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 400,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            val deleteTarget = insertRealSaleAt(
                app = app,
                epochMs = nowMs + 1_000L,
                items = listOf(SaleItemInput(prodA, 2)), // 160,00 Crédito 4% -> taxa 6,40, líq 153,60
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = machine.id,
                note = "Remover via UI",
            )
            val preserved = insertRealSaleAt(
                app = app,
                epochMs = nowMs,
                items = listOf(SaleItemInput(prodB, 1)), // 50,00 Pix -> líq 50,00
                paymentMethod = SalePaymentMethod.PIX,
                note = "Manter intacta",
            )
            Quadruple(ana.id, bruno.id, deleteTarget, preserved)
        }

        openTab("nav_tab_vendas", "screen_vendas")
        scrollNodeToTag("screen_vendas", "sale_history_card_${saleToDelete.id}")
        composeRule.onNodeWithTag("sale_delete_${saleToDelete.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_dialog").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("sale_history_card_${saleToDelete.id}").fetchSemanticsNodes().isEmpty()
        }
        scrollNodeToTag("screen_vendas", "vendas_feedback_banner")
        composeRule.onNodeWithTag("vendas_feedback_banner")
            .assertTextContains("Venda excluída com sucesso", substring = true)

        // Room: dependentes removidos sem órfãos, outra venda e cadastros preservados
        runBlocking(Dispatchers.IO) {
            assertNull(app.saleRepository.getSaleById(saleToDelete.id))
            assertEquals(preservedSale, app.saleRepository.getSaleById(preservedSale.id))

            assertEquals(1, app.database.saleDao().countSales())
            assertEquals(1, app.database.saleDao().countSaleItems())
            assertEquals(1, app.database.saleDao().countSaleItemParticipants())
            assertEquals(1, app.database.saleDao().countSalePersonTotals())

            assertEquals(
                0,
                querySingleInt(
                    app,
                    "SELECT COUNT(*) FROM sale_items si LEFT JOIN sales s ON si.sale_id = s.id WHERE s.id IS NULL",
                ),
            )
            assertEquals(
                0,
                querySingleInt(
                    app,
                    "SELECT COUNT(*) FROM sale_item_participants sip LEFT JOIN sale_items si ON sip.sale_item_id = si.id WHERE si.id IS NULL",
                ),
            )
            assertEquals(
                0,
                querySingleInt(
                    app,
                    "SELECT COUNT(*) FROM sale_person_totals spt LEFT JOIN sales s ON spt.sale_id = s.id WHERE s.id IS NULL",
                ),
            )

            // Cadastros não são excluídos em cascata
            assertEquals(2, app.catalogRepository.observePeople().first().size)
            assertEquals(2, app.catalogRepository.observeAllProducts().first().size)
            assertEquals(1, app.machineRepository.observeMachines().first().size)
            assertEquals("true", app.preferenceRepository.getPreference("stage8b1_keep")?.value)
        }

        // VENDAS, INÍCIO e FECHAMENTO atualizados apenas com preservedSale (R$ 50,00 Pix)
        scrollNodeToTag("screen_vendas", "sales_top_summary_card")
        composeRule.onNodeWithTag("sales_top_summary_card")
            .assert(hasAnyDescendant(hasText("1 venda", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 50,00", substring = true)))

        openTab("nav_tab_inicio", "screen_inicio")
        scrollNodeToTag("screen_inicio", "home_metric_total_sold")
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 50,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees").assertTextContains("R$ 0,00", substring = true)
        scrollNodeToTag("screen_inicio", "home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 50,00", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("1", substring = true)
        scrollNodeToTag("screen_inicio", "home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("0 vendas", substring = true)
            .assertTextContains("R$ 0,00", substring = true)
        composeRule.onNodeWithTag("home_person_card_$brunoId")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 50,00", substring = true)

        openTab("nav_tab_fechamento", "screen_fechamento")
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 50,00", substring = true)
            .assertTextContains("− R$ 0,00", substring = true)
        composeRule.onAllNodesWithTag("closing_individual_row_$anaId").assertCountEquals(0)
        composeRule.onNodeWithTag("closing_individual_row_$brunoId")
            .performScrollTo()
            .assertTextContains("R$ 50,00", substring = true)
        composeRule.onNodeWithTag("closing_payment_row_credit")
            .performScrollTo()
            .assertTextContains("0 vendas", substring = true)
            .assertTextContains("R$ 0,00", substring = true)
        composeRule.onNodeWithTag("closing_payment_row_pix")
            .performScrollTo()
            .assertTextContains("R$ 50,00", substring = true)
    }

    @Test
    fun `04 daily and monthly grouping handles local midnight boundary, period filters, and day drilldown consistently across screens`() {
        savedTimeZone = TimeZone.getDefault()
        val spTz = TimeZone.getTimeZone("America/Sao_Paulo")
        TimeZone.setDefault(spTz)
        val zone = spTz.toZoneId()
        val today = LocalDate.now(zone)
        val midWeekDate = if (today.dayOfMonth > 4) today.minusDays(4) else today.minusDays(1)
        val previousMonthDate = today.minusMonths(1).withDayOfMonth(15)

        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (todayMorningSale, todayNightSale, midWeekSale, previousMonthSale) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Miniatura Fuso",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(2_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "PagSeguro Smart",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500, // 5,00%
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // 1) Hoje 09:30 local -> R$ 80,00 Pix
            val s1 = insertRealSaleAt(
                app = app,
                epochMs = today.atTime(9, 30).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 4)),
                paymentMethod = SalePaymentMethod.PIX,
                note = "Hoje manhã",
            )
            // 2) Hoje 22:45 local (01:45 UTC do dia seguinte!) -> R$ 120,00 Crédito 5% (taxa 6,00, líq 114,00)
            val s2 = insertRealSaleAt(
                app = app,
                epochMs = ZonedDateTime.of(
                    today.plusDays(1),
                    LocalTime.of(1, 45),
                    ZoneOffset.UTC,
                ).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 6)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = machine.id,
                note = "Hoje noite virada UTC",
            )
            // 3) Mesmo mês dentro de 7 dias -> R$ 60,00 Dinheiro
            val s3 = insertRealSaleAt(
                app = app,
                epochMs = midWeekDate.atTime(15, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 3)),
                paymentMethod = SalePaymentMethod.CASH,
                note = "Mesmo mês semana",
            )
            // 4) Mês anterior -> R$ 100,00 Pix
            val s4 = insertRealSaleAt(
                app = app,
                epochMs = previousMonthDate.atTime(11, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 5)),
                paymentMethod = SalePaymentMethod.PIX,
                note = "Mês anterior",
            )
            listOf(s1, s2, s3, s4)
        }

        val allPersisted = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first() }
        val expectedReport = DailySalesAggregator.aggregate(allPersisted, zone)
        assertEquals(4, expectedReport.totalSalesCount)
        assertEquals(3, expectedReport.totalDaysCount)
        assertEquals(36_000L, expectedReport.totalSold.rawCents)
        assertEquals(2, expectedReport.months.size)

        // Navega via MAIS -> rota legada Vendas por Dia controlada pelo fixture de teste
        openTab("nav_tab_mais", "screen_mais")
        shellHarness.navigateToLegacyDailySalesRoute()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_daily_sales").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 360,00", substring = true)
            .assertTextContains("4 vendas", substring = true)
            .assertTextContains("3 dias", substring = true)

        val currentYm = YearMonth.from(today)
        val prevYm = YearMonth.from(previousMonthDate)
        scrollNodeToTag("screen_mais_detail_daily_sales", "daily_month_card_$currentYm")
        composeRule.onNode(
            hasTestTag("daily_month_card_$currentYm") and
                hasAnyDescendant(hasText(DailySalesAggregator.formatMonthHeader(currentYm), substring = true)) and
                hasAnyDescendant(hasText("R$ 260,00", substring = true)),
        ).assertExists()

        // Garante que a venda de 22:45 local foi agrupada em TODAY e NÃO em today.plusDays(1)
        composeRule.onAllNodesWithTag("daily_day_row_${today.plusDays(1)}").assertCountEquals(0)
        composeRule.onNodeWithTag("daily_day_row_$today")
            .performScrollTo()
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 200,00", substring = true)

        scrollNodeToTag("screen_mais_detail_daily_sales", "daily_month_card_$prevYm")
        composeRule.onNode(
            hasTestTag("daily_month_card_$prevYm") and
                hasAnyDescendant(hasText(DailySalesAggregator.formatMonthHeader(prevYm), substring = true)) and
                hasAnyDescendant(hasText("R$ 100,00", substring = true)),
        ).assertExists()

        // Toca no dia de hoje -> abre VENDAS filtrada exatamente para o dia
        scrollNodeToTag("screen_mais_detail_daily_sales", "daily_day_row_$today")
        composeRule.onNodeWithTag("daily_day_row_$today").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vendas_selected_day_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("nav_tab_vendas").assertIsSelected()
        composeRule.onNodeWithTag("sales_top_summary_card")
            .assert(hasAnyDescendant(hasText("2 vendas", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 200,00", substring = true)))
            .assert(hasAnyDescendant(hasText("Taxas: R$ 6,00", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 194,00", substring = true)))

        scrollNodeToTag("screen_vendas", "sale_history_card_${todayMorningSale.id}")
        scrollNodeToTag("screen_vendas", "sale_history_card_${todayNightSale.id}")
        composeRule.onAllNodesWithTag("sale_history_card_${midWeekSale.id}").assertCountEquals(0)
        composeRule.onAllNodesWithTag("sale_history_card_${previousMonthSale.id}").assertCountEquals(0)

        // Limpa seleção de dia e valida filtro de período no Início e Fechamento
        scrollNodeToTag("screen_vendas", "vendas_selected_day_banner")
        composeRule.onNodeWithTag("vendas_clear_day_filter_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vendas_selected_day_banner").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("sales_top_summary_card")
            .assert(hasAnyDescendant(hasText("4 vendas", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 360,00", substring = true)))

        openTab("nav_tab_inicio", "screen_inicio")
        scrollNodeToTag("screen_inicio", "home_metric_total_sold")
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("home_metric_total_sold")
                    .assertTextContains("R$ 360,00", substring = true)
                true
            }.getOrDefault(false)
        }
        scrollNodeToTag("screen_inicio", "home_metric_sales_count")
        composeRule.onNodeWithTag("home_metric_sales_count")
            .assertTextContains("4", substring = true)

        openTab("nav_tab_fechamento", "screen_fechamento")
        // Fechamento continua independente em "Todos" (R$ 360,00), depois muda para "7 dias" (R$ 260,00)
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("4 vendas", substring = true)
            .assertTextContains("R$ 360,00", substring = true)
        composeRule.onNodeWithTag("period_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_week").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("3 vendas", substring = true)
                    .assertTextContains("R$ 260,00", substring = true)
                    .assertTextContains("R$ 254,00", substring = true)
                true
            }.getOrDefault(false)
        }
    }

    @Test
    fun `05 multi-person financial splits across all fee responsibility modes preserve exact cent conservation end-to-end`() {
        val zone = ZoneId.systemDefault()
        val nowMs = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (aliceId, betoId, caioId, dudaId, sale1, sale2) = runBlocking(Dispatchers.IO) {
            val alice = app.catalogRepository.createPerson("Alice")
            val beto = app.catalogRepository.createPerson("Beto")
            val caio = app.catalogRepository.createPerson("Caio")
            val duda = app.catalogRepository.createPerson("Duda")

            // Prod EQUAL: 99,99 dividido igualmente 33,33 x 3 (Alice, Beto, Caio)
            val prodEq = app.catalogRepository.createProduct(
                name = "Peça Tríade",
                price = MoneyCents(9_999L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(alice.id, MoneyCents(3_333L)),
                    ProductParticipantDraft(beto.id, MoneyCents(3_333L)),
                    ProductParticipantDraft(caio.id, MoneyCents(3_333L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            // Prod INDIVIDUAL: 77,77 (Alice 50,00 + Duda 27,77), tarifa 100% na Duda
            val prodInd = app.catalogRepository.createProduct(
                name = "Peça Duda",
                price = MoneyCents(7_777L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(alice.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(duda.id, MoneyCents(2_777L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = duda.id,
            )
            // Prod PROPORTIONAL: 55,55 (Beto 30,00 + Caio 25,55)
            val prodProp = app.catalogRepository.createProduct(
                name = "Peça Proporcional",
                price = MoneyCents(5_555L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(beto.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(caio.id, MoneyCents(2_555L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "InfinitePay Precisa",
                    debitRateBps = 183, // 1,83%
                    creditEnabled = true,
                    creditDefaultRateBps = 437, // 4,37%
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            val s1 = insertRealSaleAt(
                app = app,
                epochMs = nowMs,
                items = listOf(SaleItemInput(prodEq, 1), SaleItemInput(prodInd, 1)),
                finalAmountOverride = MoneyCents(16_543L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = machine.id,
            )
            val s2 = insertRealSaleAt(
                app = app,
                epochMs = nowMs + 1_000L,
                items = listOf(SaleItemInput(prodProp, 2)),
                finalAmountOverride = MoneyCents(10_111L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.DEBIT,
                machineId = machine.id,
            )
            SixTuple(alice.id, beto.id, caio.id, duda.id, s1, s2)
        }

        assertMathematicalInvariants(sale1)
        assertMathematicalInvariants(sale2)

        val expectedGrossCents = sale1.calculation.finalAmount.rawCents + sale2.calculation.finalAmount.rawCents
        val expectedFeeCents = sale1.calculation.feeTotal.rawCents + sale2.calculation.feeTotal.rawCents
        val expectedNetCents = sale1.calculation.netTotal.rawCents + sale2.calculation.netTotal.rawCents
        assertEquals(expectedNetCents, expectedGrossCents - expectedFeeCents)

        val netByPerson = listOf(sale1, sale2)
            .flatMap { it.calculation.personTotals }
            .groupBy { it.personId }
            .mapValues { (_, totals) -> totals.sumOf { it.netAmount.rawCents } }
        assertEquals(expectedNetCents, netByPerson.values.sum())

        val formattedGross = CurrencyFormatter.formatBrl(MoneyCents(expectedGrossCents))
        val formattedNet = CurrencyFormatter.formatBrl(MoneyCents(expectedNetCents))

        // VENDAS reflete exatos centavos por pessoa
        openTab("nav_tab_vendas", "screen_vendas")
        composeRule.onNodeWithTag("sales_top_summary_card")
            .assert(hasAnyDescendant(hasText(formattedGross, substring = true)))
            .assert(hasAnyDescendant(hasText(formattedNet, substring = true)))
        listOf(aliceId, betoId, caioId, dudaId).forEach { pid ->
            composeRule.onNodeWithTag("sales_person_net_$pid")
                .performScrollTo()
                .assertTextContains(CurrencyFormatter.formatBrl(MoneyCents(netByPerson.getValue(pid))), substring = true)
        }

        // INÍCIO reflete todas as 4 pessoas após "Ver mais"
        openTab("nav_tab_inicio", "screen_inicio")
        scrollNodeToTag("screen_inicio", "toggle_more_people_button")
        composeRule.onNodeWithTag("toggle_more_people_button").performClick()
        listOf(aliceId, betoId, caioId, dudaId).forEach { pid ->
            scrollNodeToTag("screen_inicio", "home_person_card_$pid")
            composeRule.onNodeWithTag("home_person_card_$pid")
                .assertTextContains(CurrencyFormatter.formatBrl(MoneyCents(netByPerson.getValue(pid))), substring = true)
        }

        // FECHAMENTO reflete mesmos centavos para as 4 pessoas e soma individual == total líquido
        openTab("nav_tab_fechamento", "screen_fechamento")
        listOf(aliceId, betoId, caioId, dudaId).forEach { pid ->
            composeRule.onNodeWithTag("closing_individual_row_$pid")
                .performScrollTo()
                .assertTextContains(CurrencyFormatter.formatBrl(MoneyCents(netByPerson.getValue(pid))), substring = true)
        }
        composeRule.onAllNodesWithTag("split_balance_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("hero_summary_card")
            .performScrollTo()
            .assertTextContains(formattedNet, substring = true)
    }

    @Test
    fun `06 post-sale catalog edits never recalculate historical sales even when editing sale note via UI, and sibling sales remain untouched`() {
        val zone = ZoneId.systemDefault()
        val yesterdayMs = LocalDate.now(zone).minusDays(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val todayMs = LocalDate.now(zone).atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, machId, historicalSale, latestSale) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana Original")
            val prod = app.catalogRepository.createProduct(
                name = "Peça Histórica",
                price = MoneyCents(10_000L), // R$ 100,00
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Maquininha Antiga",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 300, // 3,00%
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1 (histórica): R$ 100,00 Crédito 3,00% -> Taxa R$ 3,00, Líq R$ 97,00
            val sHist = insertRealSaleAt(
                app = app,
                epochMs = yesterdayMs,
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = machine.id,
                note = "Nota inicial",
            )
            // Venda 2 (irmã): R$ 50,00 no Pix (desconto R$ 50,00) -> Líq R$ 50,00
            val sLatest = insertRealSaleAt(
                app = app,
                epochMs = todayMs,
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(5_000L),
                paymentMethod = SalePaymentMethod.PIX,
                note = "Venda irmã intocada",
            )

            // Muta catálogo DEPOIS das duas vendas
            app.catalogRepository.renamePerson(ana.id, "Ana Atualizada")
            app.catalogRepository.updateProduct(
                productId = prod.id,
                name = "Peça Nova Dobrada",
                price = MoneyCents(20_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(20_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.machineRepository.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Maquininha Nova",
                    debitRateBps = 500,
                    creditEnabled = true,
                    creditDefaultRateBps = 900,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            Quadruple(ana.id, machine.id, sHist, sLatest)
        }

        // Edita SOMENTE a observação da venda histórica pela UI -> snapshots originais não podem mudar!
        openTab("nav_tab_vendas", "screen_vendas")
        scrollNodeToTag("screen_vendas", "sale_history_card_${historicalSale.id}")
        composeRule.onNodeWithTag("sale_edit_${historicalSale.id}").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextReplacement("Observação atualizada")
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Room: snapshot histórico 100% preservado (mudou apenas note e updatedAtEpochMs) e venda irmã intocada
        runBlocking(Dispatchers.IO) {
            val reloadedHist = app.saleRepository.getSaleById(historicalSale.id)!!
            assertEquals(historicalSale.soldAtEpochMs, reloadedHist.soldAtEpochMs)
            assertEquals(
                historicalSale.calculation.copy(note = "Observação atualizada"),
                reloadedHist.calculation,
            )
            assertEquals("Peça Histórica", reloadedHist.calculation.items.single().productName)
            assertEquals("Maquininha Antiga", reloadedHist.calculation.machineSnapshot?.machineName)
            assertEquals(300, reloadedHist.calculation.effectiveFeeRateBps)
            assertEquals(300L, reloadedHist.calculation.feeTotal.rawCents)
            assertEquals(9_700L, reloadedHist.calculation.netTotal.rawCents)

            val reloadedLatest = app.saleRepository.getSaleById(latestSale.id)
            assertEquals(latestSale, reloadedLatest)
        }

        // FECHAMENTO preserva nome histórico da maquininha e da pessoa, além de totais exatos (150,00 - 3,00 = 147,00)
        openTab("nav_tab_fechamento", "screen_fechamento")
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 150,00", substring = true)
            .assertTextContains("− R$ 3,00", substring = true)
            .assertTextContains("R$ 147,00", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$anaId")
            .performScrollTo()
            .assertTextContains("Ana Original", substring = true)
            .assertTextContains("R$ 147,00", substring = true)
        composeRule.onNodeWithTag("closing_machine_row_$machId|Maquininha Antiga|CREDIT")
            .performScrollTo()
            .assertTextContains("Maquininha Antiga", substring = true)
            .assertTextContains("3,00%", substring = true)
            .assertTextContains("Líquido R$ 97,00", substring = true)
        composeRule.onAllNodesWithText("Maquininha Nova", substring = true).assertCountEquals(0)
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

    private data class SixTuple<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
    )

    private data class SevenTuple<A, B, C, D, E, F, G>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G,
    )
}
