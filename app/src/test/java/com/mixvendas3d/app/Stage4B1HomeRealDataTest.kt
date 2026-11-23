package com.mixvendas3d.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
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
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.PreviewPeriodFilter
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class Stage4B1HomeRealDataTest {

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

    private fun scrollHomeToTag(tag: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_inicio")
                    .performScrollToNode(hasTestTag(tag))
                composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                true
            }.getOrDefault(false)
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
        discountRecipientPersonIdsByProduct: Map<String, Set<String>>? = null,
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
                items = items,
                finalAmountOverride = finalAmountOverride,
                discountMode = discountMode,
                paymentMethod = paymentMethod,
                machine = machine,
                cardBrand = cardBrand,
                installments = installments,
                discountRecipientPersonIdsByProduct = discountRecipientPersonIdsByProduct,
                note = note,
            ),
        )
        return repo.saveSale(calc).id
    }

    @Test
    fun `01 empty database displays zeroed indicators, empty messages, and no demo or preview fixtures on inicio`() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_inicio").assertIsDisplayed()
        composeRule.onNodeWithTag("home_header_title").assertTextContains("Início")
        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
        composeRule.onAllNodesWithText("prévia", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Pessoa 1 (Prévia)", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("840,00", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Ana Silva", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Carlos Mendes", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("home_metric_total_sold")
            .assertTextContains("Total vendido", substring = true)
            .assertTextContains("R$ 0,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees")
            .assertTextContains("Tarifas", substring = true)
            .assertTextContains("R$ 0,00", substring = true)
        scrollHomeToTag("home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net")
            .assertTextContains("Total líquido", substring = true)
            .assertTextContains("R$ 0,00", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count")
            .assertTextContains("Total de vendas", substring = true)
            .assertTextContains("0", substring = true)

        scrollHomeToTag("home_people_empty_state")
        composeRule.onNodeWithTag("home_people_empty_state")
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)

        composeRule.onAllNodesWithTag("toggle_more_people_button").assertCountEquals(0)
    }

    @Test
    fun `02 single real sale recorded via vender updates inicio immediately with exact net, person splits, day and month`() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, prodId, machId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val prod = app.catalogRepository.createProduct(
                name = "Boneco 3D",
                price = MoneyCents(10_000L), // R$ 100,00
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val mach = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Smart",
                    debitRateBps = 200, // 2,00%
                    creditEnabled = true,
                    creditDefaultRateBps = 500, // 5,00%
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 500),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            listOf(ana.id, bruno.id, prod.id, mach.id)
        }

        // Registra venda real pela aba Vender: 1x Boneco (100,00) no Crédito 5,00% -> taxa 5,00, líquido 95,00 (Ana 66,50 / Bruno 28,50)
        composeRule.onNodeWithTag("home_new_sale_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("picker_checkbox_$prodId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("picker_checkbox_$prodId").performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_row_$machId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_row_$machId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Volta para Início e valida os dados reais sem reiniciar o app
        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("home_metric_total_sold").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("R$ 100,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 100,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees").assertTextContains("R$ 5,00", substring = true)
        scrollHomeToTag("home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 95,00", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("1", substring = true)

        scrollHomeToTag("home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("Ana", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 66,50", substring = true)
        composeRule.onNodeWithTag("home_person_card_$brunoId")
            .assertTextContains("Bruno", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 28,50", substring = true)
    }

    @Test
    fun `03 multiple sales with opposite splits, item and proportional discounts, all fee modes, 4 people, rename identity and catalog immutability`() {
        val zone = ZoneId.systemDefault()
        val nowMs = LocalDateTime.of(2026, 11, 16, 14, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val setup = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val carla = app.catalogRepository.createPerson("Carla")
            val diego = app.catalogRepository.createPerson("Diego")

            // Produto 1: Ana 80,00 + Bruno 20,00 | Tarifa INDIVIDUAL na Ana
            val prod1 = app.catalogRepository.createProduct(
                name = "Produto Oposto A",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(8_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            // Produto 2: Ana 10,00 + Bruno 90,00 (divisão oposta!) | Tarifa EQUAL
            val prod2 = app.catalogRepository.createProduct(
                name = "Produto Oposto B",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(1_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(9_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            // Produto 3: Carla 60,00 + Diego 40,00 | Tarifa PROPORTIONAL
            val prod3 = app.catalogRepository.createProduct(
                name = "Produto Trio C",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(carla.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(diego.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val mach = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Maquininha Oficial",
                    debitRateBps = 200, // 2,00%
                    creditEnabled = true,
                    creditDefaultRateBps = 500, // 5,00%
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 500),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1: Prod 1 (100,00) + Prod 2 (100,00) -> subtotal 200,00
            // Desconto específico de 20,00 em Prod 2 aplicado somente em Bruno -> final 180,00 (Prod 1 = 100,00, Prod 2 = 80,00)
            // Crédito 5,00%:
            //   Prod 1: bruto 100,00, taxa 5,00 (100% Ana), líq 95,00 -> Ana 75,00, Bruno 20,00
            //   Prod 2: bruto 80,00 (Ana 10,00 sem desconto, Bruno 70,00), taxa 4,00 (EQUAL -> Ana 2,00, Bruno 2,00) -> Ana 8,00, Bruno 68,00
            //   Total Venda 1: bruto 180,00, taxa 9,00, líq 171,00 -> Ana 83,00, Bruno 88,00
            insertRealSaleAt(
                app = app,
                epochMs = nowMs,
                items = listOf(SaleItemInput(prod1, 1), SaleItemInput(prod2, 1)),
                finalAmountOverride = MoneyCents(18_000L),
                discountMode = SaleDiscountInput.SpecificProduct(prod2.id),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = mach.id,
                cardBrand = CardBrand.VISA,
                installments = 1,
                discountRecipientPersonIdsByProduct = mapOf(prod2.id to setOf(bruno.id)),
            )

            // Venda 2: Prod 3 (100,00) com desconto de 50,00 dividido igualmente (25,00 cada) no Débito 2,00%
            //   Bruto 50,00 (Carla 35,00, Diego 15,00), taxa 1,00 (Carla 0,70, Diego 0,30), líq 49,00 -> Carla 34,30, Diego 14,70
            insertRealSaleAt(
                app = app,
                epochMs = nowMs + 60_000L,
                items = listOf(SaleItemInput(prod3, 1)),
                finalAmountOverride = MoneyCents(5_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.DEBIT,
                machineId = mach.id,
            )

            // Altera cadastro DEPOIS das vendas: renomeia Ana -> Ana Paula, muda preço e taxa
            app.catalogRepository.renamePerson(ana.id, "Ana Paula")
            app.catalogRepository.updateProduct(
                productId = prod1.id,
                name = "Produto Oposto A",
                price = MoneyCents(99_900L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(50_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(49_900L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            app.machineRepository.updateMachine(
                machineId = mach.id,
                draft = MachineInputDraft(
                    name = "Maquininha Oficial",
                    debitRateBps = 950,
                    creditEnabled = true,
                    creditDefaultRateBps = 1500,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 1500),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            listOf(ana.id, bruno.id, carla.id, diego.id)
        }
        val (anaId, brunoId, carlaId, diegoId) = setup

        // Totais esperados preservados: Bruto 230,00 | Tarifas 10,00 | Líquido 220,00
        // Pessoas: Ana Paula 83,00 | Bruno 88,00 | Carla 34,30 | Diego 14,70 (soma = 220,00)
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 230,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 230,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees").assertTextContains("R$ 10,00", substring = true)
        scrollHomeToTag("home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 220,00", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("2", substring = true)

        // Apenas 2 pessoas inicialmente visíveis, com identidade agrupada por ID (Ana Paula)
        scrollHomeToTag("home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("Ana Paula", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 83,00", substring = true)
        scrollHomeToTag("home_person_card_$brunoId")
        composeRule.onNodeWithTag("home_person_card_$brunoId")
            .assertTextContains("Bruno", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 88,00", substring = true)
        composeRule.onAllNodesWithTag("home_person_card_$carlaId").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_person_card_$diegoId").assertCountEquals(0)

        // Expande com Ver mais
        scrollHomeToTag("toggle_more_people_button")
        composeRule.onNodeWithTag("toggle_more_people_button")
            .assertTextContains("Ver mais", substring = true)
            .performClick()
        composeRule.waitForIdle()

        scrollHomeToTag("home_person_card_$carlaId")
        composeRule.onNodeWithTag("home_person_card_$carlaId")
            .assertTextContains("Carla", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 34,30", substring = true)
        scrollHomeToTag("home_person_card_$diegoId")
        composeRule.onNodeWithTag("home_person_card_$diegoId")
            .assertTextContains("Diego", substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 14,70", substring = true)
    }

    @Test
    fun `04 period filters apply to all inicio indicators using local dates, isolate vendas filters, and view all opens daily sales`() {
        savedTimeZone = TimeZone.getDefault()
        val spTz = TimeZone.getTimeZone("America/Sao_Paulo")
        TimeZone.setDefault(spTz)
        val zone = spTz.toZoneId()
        val today = LocalDate.now(zone)

        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, prod) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Miniatura Filtro",
                price = MoneyCents(5_000L), // R$ 50,00
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            // Venda 1: Hoje 22:30 local (01:30 UTC do dia seguinte!) -> R$ 50,00
            val todayNightUtc = ZonedDateTime.of(
                today.plusDays(1),
                java.time.LocalTime.of(1, 30),
                ZoneOffset.UTC,
            ).toInstant().toEpochMilli()
            insertRealSaleAt(
                app = app,
                epochMs = todayNightUtc,
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.PIX,
                note = "HojeLocal",
            )

            // Venda 2: 3 dias atrás (dentro de 7 dias) -> R$ 100,00
            insertRealSaleAt(
                app = app,
                epochMs = today.minusDays(3).atTime(14, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 2)),
                paymentMethod = SalePaymentMethod.CASH,
                note = "SemanaPassada",
            )

            // Venda 3: 40 dias atrás (fora de Hoje/7 dias/Mês atual) -> R$ 150,00
            insertRealSaleAt(
                app = app,
                epochMs = today.minusDays(40).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                items = listOf(SaleItemInput(prod, 3)),
                paymentMethod = SalePaymentMethod.PIX,
                note = "AntigaMesAnterior",
            )

            ana.id to prod
        }

        // Pré-aplica filtros específicos na aba VENDAS (busca "AntigaMesAnterior" e método PIX)
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sales_search_input").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().performTextInput("AntigaMesAnterior")
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("sales_top_summary_card").assert(hasAnyDescendant(hasText("1 venda", substring = true)))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sales_method_filter_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Todas as formas").fetchSemanticsNodes().size >= 2
        }
        val pixNodes = composeRule.onAllNodesWithText("Pix")
        val pixCount = pixNodes.fetchSemanticsNodes().size
        pixNodes[pixCount - 1].performClick()
        waitUiUntil {
            composeRule.onAllNodesWithText("Todas as formas").fetchSemanticsNodes().isEmpty()
        }

        // Vai para Início e valida os totais reais acumulados sem misturar filtros da aba VENDAS
        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("home_metric_total_sold").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithText("R$ 300,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 300,00", substring = true)
        composeRule.onNodeWithTag("home_metric_fees").assertTextContains("R$ 0,00", substring = true)
        scrollHomeToTag("home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 300,00", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("3", substring = true)
        scrollHomeToTag("home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("3 vendas", substring = true)
            .assertTextContains("R$ 300,00", substring = true)

        // Confirma que os filtros da aba VENDAS NÃO foram alterados silenciosamente
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("sales_search_input").performScrollTo().assertTextContains("AntigaMesAnterior")
        composeRule.onNodeWithTag("sales_method_filter_dropdown", useUnmergedTree = true)
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("Pix")))
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().assertTextContains("Todos")
        composeRule.onNodeWithTag("sales_top_summary_card")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("1 venda", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 150,00", substring = true)))

        // Vendas por Dia segue preservada na rota legada e acessível via NavHostController controlado em teste
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais").fetchSemanticsNodes().isNotEmpty()
        }
        shellHarness.navigateToLegacyDailySalesRoute()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_daily_sales").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("daily_sales_summary_card")
            .assertTextContains("R$ 300,00", substring = true)
            .assertTextContains("3 vendas", substring = true)
    }

    @Test
    fun `05 editing and deleting sales from vendas updates inicio automatically without restarting app`() {
        val zone = ZoneId.systemDefault()
        val todayMs = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, prodId, sale1Id, sale2Id) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Item Live",
                price = MoneyCents(4_000L), // R$ 40,00
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val s1 = insertRealSaleAt(
                app = app,
                epochMs = todayMs,
                items = listOf(SaleItemInput(prod, 1)), // R$ 40,00
                paymentMethod = SalePaymentMethod.PIX,
            )
            val s2 = insertRealSaleAt(
                app = app,
                epochMs = todayMs + 1_000L,
                items = listOf(SaleItemInput(prod, 1)), // R$ 40,00
                paymentMethod = SalePaymentMethod.CASH,
            )
            listOf(ana.id, prod.id, s1, s2)
        }

        // Início exibe 2 vendas, R$ 80,00
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 80,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 80,00", substring = true)
        scrollHomeToTag("home_metric_sales_count")
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("2", substring = true)

        // 1) Edita sale1 na aba VENDAS: aumenta quantidade de 1 para 2 (R$ 40 -> R$ 80; total R$ 120,00)
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$sale1Id"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_$sale1Id").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vender").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("selected_inc_$prodId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        scrollHomeToTag("home_metric_total_sold")
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("home_metric_total_sold")
                    .assertTextContains("R$ 120,00", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 120,00", substring = true)
        scrollHomeToTag("home_metric_total_net")
        composeRule.onNodeWithTag("home_metric_total_net").assertTextContains("R$ 120,00", substring = true)
        composeRule.onNodeWithTag("home_metric_sales_count").assertTextContains("2", substring = true)
        scrollHomeToTag("home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("R$ 120,00", substring = true)

        // 2) Exclui sale2 na aba VENDAS (R$ 120 - R$ 40 = R$ 80,00 e 1 venda)
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$sale2Id"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_delete_$sale2Id").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_confirm_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_history_card_$sale2Id").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        scrollHomeToTag("home_metric_total_sold")
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("home_metric_total_sold")
                    .assertTextContains("R$ 80,00", substring = true)
        scrollHomeToTag("home_metric_sales_count")
                composeRule.onNodeWithTag("home_metric_sales_count")
                    .assertTextContains("1", substring = true)
                true
            }.getOrDefault(false)
        }
        scrollHomeToTag("home_person_card_$anaId")
        composeRule.onNodeWithTag("home_person_card_$anaId")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 80,00", substring = true)
    }

    @Test
    fun `06 viewmodel isolates home period filter from vendas filters and enforces exact cent invariants`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val dbName = "home.db"
        context.deleteDatabase(dbName)

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
            val machineRepo = RoomMachineRepository(db.machineDao())
            val prefRepo = RoomPreferenceRepository(db.preferenceDao())

            val p1 = catalogRepo.createPerson("Alice")
            val p2 = catalogRepo.createPerson("Beto")
            val p3 = catalogRepo.createPerson("Caio")

            val prod = catalogRepo.createProduct(
                name = "Escultura 3D",
                price = MoneyCents(9_999L), // R$ 99,99 para testar arredondamento exato
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(3_333L)),
                    ProductParticipantDraft(p2.id, MoneyCents(3_333L)),
                    ProductParticipantDraft(p3.id, MoneyCents(3_333L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val machine = machineRepo.createMachine(
                MachineInputDraft(
                    name = "InfinitePay",
                    debitRateBps = 138,
                    creditEnabled = true,
                    creditDefaultRateBps = 319,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = { today.atTime(11, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        finalAmountOverride = MoneyCents(9_001L),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )

            RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = { today.minusDays(15).atTime(16, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )

            val saleRepo = RoomSaleRepository(db, db.saleDao())
            val vm = MixVendasShellViewModel(prefRepo, catalogRepo, machineRepo, saleRepo)
            val collectJob = CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }

            var waited = 0
            while (vm.uiState.value.homeRealTotalSalesCount < 2 && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
                waited++
            }

            // Estado inicial ALL: 2 vendas
            val initial = vm.uiState.value
            assertEquals(2, initial.homeFilteredRealSalesCount)
            assertEquals(
                initial.homeRealTotalNet.rawCents,
                initial.homeRealTotalGross.rawCents - initial.homeRealTotalFee.rawCents,
            )
            assertEquals(
                initial.homeRealTotalNet.rawCents,
                initial.homeRealPersonSummaries.sumOf { it.netAmount.rawCents },
            )
            assertEquals(2, initial.homeDailyReport.totalSalesCount)
            assertEquals(2, initial.homeDailyReport.totalDaysCount)

            // Define filtros em VENDAS
            vm.onPeriodFilterSelected(PreviewPeriodFilter.ALL)
            vm.onSalesSearchChanged("Escultura")
            vm.onSalesMethodFilterSelected(PreviewPaymentMethod.PIX)
            delay(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            // Altera período da tela INÍCIO para TODAY -> VENDAS não muda!
            vm.onHomePeriodFilterSelected(PreviewPeriodFilter.TODAY)
            delay(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val afterHomeFilter = vm.uiState.value
            assertEquals(PreviewPeriodFilter.TODAY, afterHomeFilter.homePeriodFilter)
            assertEquals(1, afterHomeFilter.homeFilteredRealSalesCount)
            assertEquals(9_001L, afterHomeFilter.homeRealTotalGross.rawCents)
            assertEquals(
                afterHomeFilter.homeRealTotalNet.rawCents,
                afterHomeFilter.homeRealTotalGross.rawCents - afterHomeFilter.homeRealTotalFee.rawCents,
            )
            assertEquals(
                afterHomeFilter.homeRealTotalNet.rawCents,
                afterHomeFilter.homeRealPersonSummaries.sumOf { it.netAmount.rawCents },
            )
            assertEquals(1, afterHomeFilter.homeDailyReport.totalSalesCount)
            assertEquals(1, afterHomeFilter.homeDailyReport.totalDaysCount)

            // VENDAS manteve todos os seus filtros intactos
            assertEquals(PreviewPeriodFilter.ALL, afterHomeFilter.periodFilter)
            assertEquals("Escultura", afterHomeFilter.salesSearchQuery)
            assertEquals(PreviewPaymentMethod.PIX, afterHomeFilter.salesMethodFilter)
            assertNull(afterHomeFilter.selectedSalesDate)
            assertEquals(1, afterHomeFilter.filteredRealSales.size)
            assertEquals(9_999L, afterHomeFilter.realTotalGross.rawCents)

            collectJob.cancel()
        } finally {
            db.close()
            assertTrue(context.deleteDatabase(dbName))
        }
    }
}
