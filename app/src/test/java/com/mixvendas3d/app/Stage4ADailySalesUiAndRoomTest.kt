package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class Stage4ADailySalesUiAndRoomTest {

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

    private fun openDailySalesFromMais() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithTag("screen_mais_detail_daily_sales").fetchSemanticsNodes().isEmpty()) {
            shellHarness.navigateToLegacyDailySalesRoute()
            waitUiUntil {
                composeRule.onAllNodesWithTag("screen_mais_detail_daily_sales")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun scrollDailySalesToTag(tag: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_mais_detail_daily_sales")
                    .performScrollToNode(hasTestTag(tag))
                true
            }.getOrDefault(false)
        }
    }

    private suspend fun insertRealSaleAt(
        app: MixVendasApplication,
        epochMs: Long,
        product: Product,
        quantity: Int = 1,
        finalAmountOverride: MoneyCents? = null,
        paymentMethod: SalePaymentMethod = SalePaymentMethod.PIX,
        machineId: String? = null,
        note: String = "",
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
                items = listOf(SaleItemInput(product, quantity)),
                finalAmountOverride = finalAmountOverride,
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = paymentMethod,
                machine = machine,
                cardBrand = if (paymentMethod == SalePaymentMethod.CREDIT) CardBrand.VISA else null,
                installments = if (paymentMethod == SalePaymentMethod.CREDIT) 1 else null,
                note = note,
            ),
        )
        return repo.saveSale(calc).id
    }

    @Test
    fun `01 empty database displays official header, description, zero totals, and no demo entries`() {
        openDailySalesFromMais()

        composeRule.onNodeWithTag("topbar_title").assertTextContains("Vendas por Dia")
        composeRule.onNodeWithTag("daily_sales_description")
            .assertTextContains("Todos os dias que tiveram vendas, separados por mês. Toque em um dia para abrir suas vendas.")

        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 0,00", substring = true)
            .assertTextContains("0 vendas", substring = true)
            .assertTextContains("0 dias", substring = true)

        scrollDailySalesToTag("daily_sales_empty_state")
        composeRule.onNodeWithTag("daily_sales_empty_state").assertIsDisplayed()
        composeRule.onNodeWithText("Não há dias com vendas para apresentar.").assertIsDisplayed()
        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
    }

    @Test
    fun `02 real room grouping orders months and days descending, uses discounted finalAmount before fees, and drills down into vendas clearing old filters`() {
        savedTimeZone = TimeZone.getDefault()
        val spTz = TimeZone.getTimeZone("America/Sao_Paulo")
        TimeZone.setDefault(spTz)
        val zone = spTz.toZoneId()

        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val setup = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")

            val prod = app.catalogRepository.createProduct(
                name = "Vaso Prisma",
                price = MoneyCents(10_000L), // R$ 100,00
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val mach = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500, // 5,00%
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 500),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Dia 16/11/2026:
            // Venda A às 10:00 local: 1x R$ 100,00 no Pix (sem taxa) -> final R$ 100,00
            val nov16A = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 1,
                paymentMethod = SalePaymentMethod.PIX,
                note = "Manhã Pix",
            )
            // Venda B às 22:30 local (01:30 UTC do dia 17!): 2x (subtotal 200,00) com desconto para R$ 150,00 no Crédito 5% (taxa 7,50)
            // Deve agrupar em 16/11 (não 17/11!) e somar R$ 150,00 (não R$ 200,00 nem R$ 142,50)
            val nov16B = insertRealSaleAt(
                app = app,
                epochMs = ZonedDateTime.of(2026, 11, 17, 1, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(),
                product = prod,
                quantity = 2,
                finalAmountOverride = MoneyCents(15_000L),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = mach.id,
                note = "Noite Crédito",
            )
            // Dia 14/11/2026: R$ 80,00 em Dinheiro
            val nov14 = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2026, 11, 14, 15, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 1,
                finalAmountOverride = MoneyCents(8_000L),
                paymentMethod = SalePaymentMethod.CASH,
                note = "Tarde Dinheiro",
            )
            // Dia 30/10/2026: R$ 120,00
            val oct30 = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2026, 10, 30, 11, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 2,
                finalAmountOverride = MoneyCents(12_000L),
                paymentMethod = SalePaymentMethod.PIX,
            )
            // Ano bissexto 29/02/2024: R$ 70,00
            val feb29 = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2024, 2, 29, 18, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 1,
                finalAmountOverride = MoneyCents(7_000L),
                paymentMethod = SalePaymentMethod.DEBIT,
                machineId = mach.id,
            )

            // Altera a taxa da maquininha DEPOIS da venda para provar que histórico NÃO recalcula
            app.machineRepository.updateMachine(
                mach.id,
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 900,
                    creditEnabled = true,
                    creditDefaultRateBps = 1200,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 1200),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            listOf(nov16A, nov16B, nov14, oct30, feb29)
        }
        val nov16A = setup[0]
        val nov16B = setup[1]
        val nov14 = setup[2]
        val oct30 = setup[3]
        val feb29 = setup[4]

        // Pré-aplica filtros restritivos na aba VENDAS (busca por "Inexistente" e método Dinheiro)
        // para comprovar que tocar em um dia limpa filtros anteriores automaticamente
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sales_search_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextInput("Inexistente")
        composeRule.onNodeWithTag("sales_method_filter_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Dinheiro").performClick()
        composeRule.waitForIdle()

        // Abre Mais -> Vendas por Dia
        openDailySalesFromMais()

        // Aguarda emissão do Room no topo: 100 + 150 + 80 + 120 + 70 = R$ 520,00 | 5 vendas | 4 dias
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 520,00", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 520,00", substring = true)
            .assertTextContains("5 vendas", substring = true)
            .assertTextContains("4 dias", substring = true)

        // Verifica cards mensais na ordem decrescente e ano bissexto
        scrollDailySalesToTag("daily_month_card_2024-02")
        composeRule.onNode(
            hasTestTag("daily_month_card_2024-02") and
                hasAnyDescendant(hasText("FEVEREIRO 2024", substring = true)) and
                hasAnyDescendant(hasText("R$ 70,00", substring = true)),
        ).assertExists()
        composeRule.onNodeWithTag("daily_day_row_2024-02-29")
            .performScrollTo()
            .assertTextContains("29/02", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 70,00", substring = true)

        scrollDailySalesToTag("daily_month_card_2026-10")
        composeRule.onNode(
            hasTestTag("daily_month_card_2026-10") and
                hasAnyDescendant(hasText("OUTUBRO 2026", substring = true)) and
                hasAnyDescendant(hasText("R$ 120,00", substring = true)),
        ).assertExists()

        // Volta para NOVEMBRO 2026 e verifica dias decrescentes (16/11 antes de 14/11, sem 17/11)
        scrollDailySalesToTag("daily_month_card_2026-11")
        composeRule.onNode(
            hasTestTag("daily_month_card_2026-11") and
                hasAnyDescendant(hasText("NOVEMBRO 2026", substring = true)) and
                hasAnyDescendant(hasText("R$ 330,00", substring = true)),
        ).assertExists()
        composeRule.onAllNodesWithTag("daily_day_row_2026-11-17").assertCountEquals(0)
        composeRule.onNodeWithTag("daily_day_row_2026-11-16")
            .performScrollTo()
            .assertTextContains("16/11", substring = true)
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 250,00", substring = true)
        composeRule.onNodeWithTag("daily_day_row_2026-11-14")
            .performScrollTo()
            .assertTextContains("14/11", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 80,00", substring = true)

        val nov16Bounds = composeRule.onNodeWithTag("daily_day_row_2026-11-16").fetchSemanticsNode().boundsInRoot
        val nov14Bounds = composeRule.onNodeWithTag("daily_day_row_2026-11-14").fetchSemanticsNode().boundsInRoot
        assertTrue(nov16Bounds.top < nov14Bounds.top)

        // Toca no dia 16/11/2026 -> abre VENDAS filtrada exatamente para 16/11/2026
        composeRule.onNodeWithTag("daily_day_row_2026-11-16").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vendas_selected_day_banner").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("nav_tab_vendas").assertIsSelected()
        composeRule.onNode(
            hasTestTag("vendas_selected_day_banner") and
                hasAnyDescendant(hasText("Dia selecionado: 16/11/2026", substring = true)),
        ).assertExists()

        // Limpou busca e forma de pagamento anteriores -> exibe Pix e Crédito de 16/11, oculta 14/11, 30/10 e 29/02
        composeRule.onNode(
            hasTestTag("sales_top_summary_card") and
                hasAnyDescendant(hasText("2 vendas", substring = true)) and
                hasAnyDescendant(hasText("R$ 250,00", substring = true)) and
                hasAnyDescendant(hasText("R$ 242,50", substring = true)),
        ).assertExists()

        composeRule.onAllNodesWithTag("sale_history_card_$nov14").assertCountEquals(0)
        composeRule.onAllNodesWithTag("sale_history_card_$oct30").assertCountEquals(0)
        composeRule.onAllNodesWithTag("sale_history_card_$feb29").assertCountEquals(0)

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_$nov16A"))
        composeRule.onNodeWithTag("sale_history_card_$nov16A").assertExists()
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_$nov16B"))
        composeRule.onNodeWithTag("sale_history_card_$nov16B").assertExists()

        // Limpa filtro do dia -> todas as 5 vendas voltam
        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("vendas_selected_day_banner"))
        composeRule.onNodeWithTag("vendas_clear_day_filter_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vendas_selected_day_banner").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNode(
            hasTestTag("sales_top_summary_card") and
                hasAnyDescendant(hasText("5 vendas", substring = true)) and
                hasAnyDescendant(hasText("R$ 520,00", substring = true)),
        ).assertExists()
    }

    @Test
    fun `03 live updates on create, edit, and delete last sale of a day update daily and monthly cards immediately`() {
        val zone = ZoneId.systemDefault()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (prodId, nov16A, nov16B, oct15Only) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Marina")
            val prod = app.catalogRepository.createProduct(
                name = "Troféu 3D",
                price = MoneyCents(5_000L), // R$ 50,00
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            // Dia 16/11 com 2 vendas (R$ 50 + R$ 50 = R$ 100)
            val s1 = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 1,
                note = "Venda 1 dia 16",
            )
            val s2 = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2026, 11, 16, 15, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 1,
                note = "Venda 2 dia 16",
            )
            // Dia 15/10 com 1 venda (R$ 50)
            val s3 = insertRealSaleAt(
                app = app,
                epochMs = LocalDateTime.of(2026, 10, 15, 12, 0).atZone(zone).toInstant().toEpochMilli(),
                product = prod,
                quantity = 1,
                note = "Única de outubro",
            )
            listOf(prod.id, s1, s2, s3)
        }

        // Abre Vendas por Dia -> confirma estado inicial (Nov R$ 100, Out R$ 50, Total R$ 150)
        openDailySalesFromMais()
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 150,00", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 150,00", substring = true)
            .assertTextContains("3 vendas", substring = true)
            .assertTextContains("2 dias", substring = true)

        // 1) Abrir dia 16/11 em VENDAS e editar s1 de 1x (R$ 50) para 2x (R$ 100)
        scrollDailySalesToTag("daily_day_row_2026-11-16")
        composeRule.onNodeWithTag("daily_day_row_2026-11-16").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$nov16A"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_$nov16A").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vender").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("selected_inc_$prodId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Volta a Mais -> Vendas por Dia -> 16/11 agora soma R$ 150,00 e Total Geral R$ 200,00
        openDailySalesFromMais()
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 200,00", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollDailySalesToTag("daily_day_row_2026-11-16")
        composeRule.onNodeWithTag("daily_day_row_2026-11-16")
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 150,00", substring = true)

        // 2) Excluir s2 (uma das duas vendas de 16/11) pela aba VENDAS -> contagem cai para 1 e valor para R$ 100,00
        composeRule.onNodeWithTag("daily_day_row_2026-11-16").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$nov16B"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_delete_$nov16B").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_confirm_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_history_card_$nov16B").fetchSemanticsNodes().isEmpty()
        }

        openDailySalesFromMais()
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 150,00", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        scrollDailySalesToTag("daily_day_row_2026-11-16")
        composeRule.onNodeWithTag("daily_day_row_2026-11-16")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 100,00", substring = true)

        // 3) Excluir a última venda de outubro (s3) -> remove dia 15/10 e o agrupamento mensal de OUTUBRO 2026
        runBlocking(Dispatchers.IO) {
            app.saleRepository.deleteSale(oct15Only)
        }
        waitUiUntil {
            composeRule.onAllNodesWithTag("daily_month_card_2026-10")
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithTag("daily_month_card_2026-10").assertCountEquals(0)
        composeRule.onAllNodesWithTag("daily_day_row_2026-10-15").assertCountEquals(0)
        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 100,00", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("1 dia", substring = true)

        // 4) Registrar nova venda em Vender -> Vendas por Dia inclui o dia de hoje automaticamente
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("picker_checkbox_$prodId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("picker_checkbox_$prodId").performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val today = LocalDate.now(zone)
        openDailySalesFromMais()
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 150,00", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 150,00", substring = true)
            .assertTextContains("2 vendas", substring = true)
        scrollDailySalesToTag("daily_day_row_$today")
        composeRule.onNodeWithTag("daily_day_row_$today").assertExists()
    }

    @Test
    fun `04 daily sales grouping persists identically after closing and reopening Room database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "daily.db"
        context.deleteDatabase(dbName)

        val zone = ZoneId.of("America/Sao_Paulo")
        val day1 = LocalDate.of(2026, 9, 5)
        val day2 = LocalDate.of(2026, 8, 20)

        val firstDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(firstDb.personDao(), firstDb.productDao())
            val person = catalogRepo.createPerson("Helena")
            val prod = catalogRepo.createProduct(
                name = "Luminária Lua",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(6_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            RoomSaleRepository(
                database = firstDb,
                saleDao = firstDb.saleDao(),
                clockMillis = { day1.atTime(14, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        finalAmountOverride = MoneyCents(5_500L),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            RoomSaleRepository(
                database = firstDb,
                saleDao = firstDb.saleDao(),
                clockMillis = { day2.atTime(18, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                ),
            )
        } finally {
            firstDb.close()
        }

        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val reopenedSaleRepo = RoomSaleRepository(reopenedDb, reopenedDb.saleDao())
            val sales = reopenedSaleRepo.observeSales().first()
            val report = DailySalesAggregator.aggregate(sales, zone)
            assertEquals(17_500L, report.totalSold.rawCents)
            assertEquals(2, report.totalSalesCount)
            assertEquals(2, report.totalDaysCount)
            assertEquals(listOf("SETEMBRO 2026", "AGOSTO 2026"), report.months.map { it.title })
            assertEquals(5_500L, report.months[0].totalSold.rawCents)
            assertEquals(12_000L, report.months[1].totalSold.rawCents)
        } finally {
            reopenedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
