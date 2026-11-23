package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import java.time.LocalDate
import java.time.LocalDateTime
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
import org.junit.Assert.assertFalse
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
class Stage4B2ClosingRealDataTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var savedTimeZone: TimeZone? = null

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
                note = note,
            ),
        )
        return repo.saveSale(calc).id
    }

    private fun openFechamento() {
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_fechamento").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `01 empty fechamento displays zeroed totals, empty messages, disabled csv, and no preview fixtures`() {
        openFechamento()

        composeRule.onAllNodesWithTag("preview_demo_banner").assertCountEquals(0)
        composeRule.onAllNodesWithText("prévia", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Pessoa 1 (Prévia)", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("0 vendas", substring = true)
            .assertTextContains("Subtotal original", substring = true)
            .assertTextContains("Descontos concedidos", substring = true)
            .assertTextContains("R$ 0,00", substring = true)

        composeRule.onAllNodesWithTag("split_balance_banner").assertCountEquals(0)

        composeRule.onNodeWithTag("closing_individual_empty_message")
            .performScrollTo()
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)

        composeRule.onNodeWithTag("closing_payments_empty_message")
            .performScrollTo()
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)
        composeRule.onAllNodesWithTag("closing_payment_row_cash").assertCountEquals(0)
        composeRule.onAllNodesWithTag("closing_payment_row_pix").assertCountEquals(0)
        composeRule.onAllNodesWithTag("closing_payment_row_debit").assertCountEquals(0)
        composeRule.onAllNodesWithTag("closing_payment_row_credit").assertCountEquals(0)

        composeRule.onNodeWithTag("closing_machines_empty_message")
            .performScrollTo()
            .assertTextContains("Nenhuma venda registrada ainda.", substring = true)

        composeRule.onNodeWithTag("export_csv_button")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun `02 cash, pix, debit and credit sales show real payment methods, zero fee for cash and pix, and exact machine details`() {
        val zone = ZoneId.systemDefault()
        val baseMs = LocalDate.now(zone).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, brunoId, machStoneId, machTonId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val prod = app.catalogRepository.createProduct(
                name = "Suporte Dual",
                price = MoneyCents(10_000L), // R$ 100,00
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val stone = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Smart",
                    debitRateBps = 200, // 2,00%
                    creditEnabled = true,
                    creditDefaultRateBps = 400, // 4,00%
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(
                        CardBrand.VISA to 400,
                        CardBrand.MASTERCARD to 450, // 4,50%
                    ),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100, 3 to 150), // 3x Mastercard = 6,00%
                ),
            )
            val ton = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Ultra",
                    debitRateBps = 150, // 1,50%
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // 1) Dinheiro: 1x (R$ 100,00) -> taxa 0,00, líq 100,00
            insertRealSaleAt(app, baseMs, listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH)
            // 2) Pix: 1x (R$ 100,00) -> taxa 0,00, líq 100,00
            insertRealSaleAt(app, baseMs + 1_000L, listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.PIX)
            // 3) Débito Stone Smart: 1x (R$ 100,00) @ 2,00% -> taxa 2,00, líq 98,00
            insertRealSaleAt(
                app,
                baseMs + 2_000L,
                listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machineId = stone.id,
            )
            // 4) Crédito Ton Ultra com tarifa manual substitutiva 5,00% (NÃO soma 3,50% + 5,00%!) -> taxa 5,00, líq 95,00
            insertRealSaleAt(
                app,
                baseMs + 3_000L,
                listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = ton.id,
                cardBrand = CardBrand.VISA,
                installments = 1,
                manualRateOverrideBps = 500,
            )
            // 5) Crédito Stone Smart Mastercard 3x (4,50% + 1,50% = 6,00%) -> taxa 6,00, líq 94,00
            insertRealSaleAt(
                app,
                baseMs + 4_000L,
                listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = stone.id,
                cardBrand = CardBrand.MASTERCARD,
                installments = 3,
            )
            // 6) Crédito Stone Smart crédito padrão / Visa 1x (4,00%) -> taxa 4,00, líq 96,00
            // Stone Smart Crédito agora possui taxas diferentes (6,00% e 4,00%)!
            insertRealSaleAt(
                app,
                baseMs + 5_000L,
                listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = stone.id,
                cardBrand = CardBrand.VISA,
                installments = 1,
            )

            // Altera catálogo DEPOIS das vendas: não pode recalcular nem mudar nome histórico da maquininha
            app.machineRepository.updateMachine(
                machineId = stone.id,
                draft = MachineInputDraft(
                    name = "Stone Alterada Depois",
                    debitRateBps = 990,
                    creditEnabled = true,
                    creditDefaultRateBps = 1990,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            listOf(ana.id, bruno.id, stone.id, ton.id)
        }

        openFechamento()

        // Totais gerais: 6 vendas | Bruto R$ 600,00 | Tarifas R$ 17,00 (0+0+2+5+6+4) | Líquido R$ 583,00
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 600,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("6 vendas", substring = true)
            .assertTextContains("R$ 600,00", substring = true)
            .assertTextContains("− R$ 17,00", substring = true)
            .assertTextContains("R$ 583,00", substring = true)

        // Formas de pagamento
        composeRule.onNodeWithTag("closing_payment_row_cash")
            .performScrollTo()
            .assertTextContains("Dinheiro", substring = true)
            .assertTextContains("Sem tarifa de maquininha", substring = true)
            .assertTextContains("R$ 100,00", substring = true)
            .assertTextContains("Líq. R$ 100,00", substring = true)

        composeRule.onNodeWithTag("closing_payment_row_pix")
            .performScrollTo()
            .assertTextContains("Pix", substring = true)
            .assertTextContains("Sem tarifa de maquininha", substring = true)
            .assertTextContains("R$ 100,00", substring = true)
            .assertTextContains("Líq. R$ 100,00", substring = true)

        composeRule.onNodeWithTag("closing_payment_row_debit")
            .performScrollTo()
            .assertTextContains("Débito", substring = true)
            .assertTextContains("Taxas R$ 2,00", substring = true)
            .assertTextContains("R$ 100,00", substring = true)
            .assertTextContains("Líq. R$ 98,00", substring = true)

        composeRule.onNodeWithTag("closing_payment_row_credit")
            .performScrollTo()
            .assertTextContains("Crédito", substring = true)
            .assertTextContains("3 vendas", substring = true)
            .assertTextContains("Taxas R$ 15,00", substring = true)
            .assertTextContains("R$ 300,00", substring = true)
            .assertTextContains("Líq. R$ 285,00", substring = true)

        // Maquininhas: preserva nomes históricos, sem Dinheiro/Pix, tarifa manual sem somar automática,
        // e Stone Crédito com múltiplas taxas NÃO atribui porcentagem única incorreta (ex.: 5,00%)!
        composeRule.onNodeWithTag("closing_machine_row_${machStoneId}|Stone Smart|DEBIT")
            .performScrollTo()
            .assertTextContains("Stone Smart", substring = true)
            .assertTextContains("Débito", substring = true)
            .assertTextContains("Movimentado R$ 100,00", substring = true)
            .assertTextContains("Tarifas − R$ 2,00", substring = true)
            .assertTextContains("Líquido R$ 98,00", substring = true)
            .assertTextContains("2,00%", substring = true)

        composeRule.onNodeWithTag("closing_machine_row_${machStoneId}|Stone Smart|CREDIT")
            .performScrollTo()
            .assertTextContains("Stone Smart", substring = true)
            .assertTextContains("Crédito", substring = true)
            .assertTextContains("2 vendas", substring = true)
            .assertTextContains("Movimentado R$ 200,00", substring = true)
            .assertTextContains("Tarifas − R$ 10,00", substring = true)
            .assertTextContains("Líquido R$ 190,00", substring = true)
            .assertTextContains("Múltiplas taxas efetivas aplicadas — valores somados", substring = true)

        composeRule.onNodeWithTag("closing_machine_row_${machTonId}|Ton Ultra|CREDIT")
            .performScrollTo()
            .assertTextContains("Ton Ultra", substring = true)
            .assertTextContains("Crédito", substring = true)
            .assertTextContains("Movimentado R$ 100,00", substring = true)
            .assertTextContains("Tarifas − R$ 5,00", substring = true)
            .assertTextContains("Líquido R$ 95,00", substring = true)
            .assertTextContains("5,00% (tarifa manual)", substring = true)

        composeRule.onAllNodesWithText("Stone Alterada Depois", substring = true).assertCountEquals(0)
        // Soma individual exata: Ana 60% de 583 = 349,80 | Bruno 40% de 583 = 233,20
        composeRule.onNodeWithTag("closing_individual_row_$anaId")
            .performScrollTo()
            .assertTextContains("R$ 349,80", substring = true)
        composeRule.onNodeWithTag("closing_individual_row_$brunoId")
            .performScrollTo()
            .assertTextContains("R$ 233,20", substring = true)
    }

    @Test
    fun `03 multiple products, opposite splits, 4 people unlimited view, item and proportional discounts, all fee modes and historical rename or delete preservation`() {
        val zone = ZoneId.systemDefault()
        val nowMs = LocalDate.now(zone).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (p1Id, p2Id, p3Id, p4Id) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Pessoa 1")
            val p2 = app.catalogRepository.createPerson("Pessoa 2")
            val p3 = app.catalogRepository.createPerson("Pessoa 3")
            val p4 = app.catalogRepository.createPerson("Pessoa 4")

            // Exemplo oficial com divisões opostas + tarifa INDIVIDUAL no Produto A e EQUAL no Produto B:
            // Prod A (R$ 22,00): P1 = 17,00 | P2 = 5,00 | Tarifa INDIVIDUAL em P1
            val prodA = app.catalogRepository.createProduct(
                name = "Produto A",
                price = MoneyCents(2_200L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(1_700L)),
                    ProductParticipantDraft(p2.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = p1.id,
            )
            // Prod B (R$ 18,00): P1 = 3,00 | P2 = 15,00 (oposta!) | Tarifa EQUAL
            val prodB = app.catalogRepository.createProduct(
                name = "Produto B",
                price = MoneyCents(1_800L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(300L)),
                    ProductParticipantDraft(p2.id, MoneyCents(1_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            // Prod C (R$ 50,00): P3 = 30,00 | P4 = 20,00 | Tarifa PROPORTIONAL
            val prodC = app.catalogRepository.createProduct(
                name = "Produto C",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p3.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(p4.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val mach = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "PagSeguro Pro",
                    debitRateBps = 200, // 2%
                    creditEnabled = true,
                    creditDefaultRateBps = 1000, // 10% para contas exatas
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1: 1x Prod A (22,00) + 1x Prod B (18,00) no Pix (sem desconto, sem taxa):
            // P1 bruto = 17 + 3 = 20,00 | P2 bruto = 5 + 15 = 20,00
            insertRealSaleAt(
                app = app,
                epochMs = nowMs,
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                paymentMethod = SalePaymentMethod.PIX,
            )

            // Venda 2: 2x Prod A (44,00) + 1x Prod B (18,00) -> subtotal original 62,00
            // Desconto específico de 2,00 em Prod B -> Prod A 44,00 (P1 34,00, P2 10,00), Prod B 16,00 -> final 60,00
            // Crédito 10%:
            // Prod A 44,00 (P1 34,00, P2 10,00), taxa 4,40 (100% P1) -> P1 líq 29,60, P2 líq 10,00
            // Prod B 16,00 (desconto R$ 2,00 dividido igualmente -> -1,00 cada -> P1 2,00, P2 14,00),
            // taxa 1,60 (EQUAL -> P1 0,80, P2 0,80) -> P1 líq 1,20, P2 líq 13,20
            insertRealSaleAt(
                app = app,
                epochMs = nowMs + 1_000L,
                items = listOf(SaleItemInput(prodA, 2), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(6_000L),
                discountMode = SaleDiscountInput.SpecificProduct(prodB.id),
                paymentMethod = SalePaymentMethod.CREDIT,
                machineId = mach.id,
                cardBrand = CardBrand.VISA,
                installments = 1,
            )

            // Venda 3: 2x Prod C (subtotal 100,00) com desconto de 20,00 dividido igualmente (-10,00 cada) no Débito 2%
            // P3 bruto 50,00, P4 bruto 30,00 | taxa 1,60 (P3 1,00, P4 0,60) | P3 líq 49,00, P4 líq 29,40
            insertRealSaleAt(
                app = app,
                epochMs = nowMs + 2_000L,
                items = listOf(SaleItemInput(prodC, 2)),
                finalAmountOverride = MoneyCents(8_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.DEBIT,
                machineId = mach.id,
            )

            // Renomeia Pessoa 1, desvincula Pessoa 4 do Produto C e exclui Pessoa 4 do catálogo -> Fechamento preserva snapshots!
            app.catalogRepository.renamePerson(p1.id, "Pessoa 1 Renomeada")
            app.catalogRepository.updateProduct(
                productId = prodC.id,
                name = "Produto C",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p3.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.catalogRepository.deletePerson(p4.id)

            listOf(p1.id, p2.id, p3.id, p4.id)
        }

        openFechamento()

        // Totais exatos:
        // Subtotal original = 40,00 + 62,00 + 100,00 = R$ 202,00
        // Descontos = 0,00 + 2,00 + 20,00 = R$ 22,00
        // Vendido = R$ 180,00 | Tarifas = 0 + 6,00 + 1,60 = R$ 7,60 | Líquido = R$ 172,40
        // P1: bruto 20,00 + (34,00 + 2,00) = 56,00 | taxa 4,40 + 0,80 = 5,20 | líq 50,80
        // P2: bruto 20,00 + (10,00 + 14,00) = 44,00 | taxa 0,80 | líq 43,20
        // P3: bruto 50,00 | taxa 1,00 | líq 49,00
        // P4: bruto 30,00 | taxa 0,60 | líq 29,40
        // Soma líquidos: 50,80 + 43,20 + 49,00 + 29,40 = 172,40!
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 180,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("3 vendas", substring = true)
            .assertTextContains("R$ 202,00", substring = true)
            .assertTextContains("− R$ 22,00", substring = true)
            .assertTextContains("R$ 180,00", substring = true)
            .assertTextContains("− R$ 7,60", substring = true)
            .assertTextContains("R$ 172,40", substring = true)

        // TODAS as 4 pessoas exibidas nos cards (sem limitar a 2) e sem botão "Ver mais"
        composeRule.onNodeWithTag("closing_person_card_$p1Id").performScrollTo().assertExists()
        composeRule.onNodeWithTag("closing_person_card_$p2Id").performScrollTo().assertExists()
        composeRule.onNodeWithTag("closing_person_card_$p3Id").performScrollTo().assertExists()
        composeRule.onNodeWithTag("closing_person_card_$p4Id").performScrollTo().assertExists()
        composeRule.onAllNodesWithTag("toggle_more_people_button").assertCountEquals(0)

        // Snapshots históricos preservam nomes originais "Pessoa 1" e "Pessoa 4" mesmo após rename/delete
        composeRule.onNodeWithTag("closing_individual_row_$p1Id")
            .performScrollTo()
            .assertTextContains("Pessoa 1", substring = true)
            .assertTextContains("R$ 56,00", substring = true)
            .assertTextContains("− R$ 5,20", substring = true)
            .assertTextContains("R$ 50,80", substring = true)

        composeRule.onNodeWithTag("closing_individual_row_$p2Id")
            .performScrollTo()
            .assertTextContains("Pessoa 2", substring = true)
            .assertTextContains("R$ 44,00", substring = true)
            .assertTextContains("− R$ 0,80", substring = true)
            .assertTextContains("R$ 43,20", substring = true)

        composeRule.onNodeWithTag("closing_individual_row_$p3Id")
            .performScrollTo()
            .assertTextContains("Pessoa 3", substring = true)
            .assertTextContains("R$ 50,00", substring = true)
            .assertTextContains("− R$ 1,00", substring = true)
            .assertTextContains("R$ 49,00", substring = true)

        composeRule.onNodeWithTag("closing_individual_row_$p4Id")
            .performScrollTo()
            .assertTextContains("Pessoa 4", substring = true)
            .assertTextContains("R$ 30,00", substring = true)
            .assertTextContains("− R$ 0,60", substring = true)
            .assertTextContains("R$ 29,40", substring = true)

        composeRule.onAllNodesWithTag("split_balance_banner").assertCountEquals(0)
        // Conservação dos centavos confirmada nos cards individuais (50,80 + 43,20 + 49,00 + 29,40 = 172,40)
        composeRule.onNodeWithTag("closing_person_card_$p1Id").performScrollTo().assertTextContains("R$ 50,80", substring = true)
        composeRule.onNodeWithTag("closing_person_card_$p2Id").performScrollTo().assertTextContains("R$ 43,20", substring = true)
        composeRule.onNodeWithTag("closing_person_card_$p3Id").performScrollTo().assertTextContains("R$ 49,00", substring = true)
        composeRule.onNodeWithTag("closing_person_card_$p4Id").performScrollTo().assertTextContains("R$ 29,40", substring = true)
    }

    @Test
    fun `04 period filters use local timezone and remain strictly isolated between fechamento, inicio and vendas`() {
        savedTimeZone = TimeZone.getDefault()
        val spTz = TimeZone.getTimeZone("America/Sao_Paulo")
        TimeZone.setDefault(spTz)
        val zone = spTz.toZoneId()
        val today = LocalDate.now(zone)

        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Filtro Item",
                price = MoneyCents(5_000L), // R$ 50,00
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            // Venda 1: 22:30 local de hoje (01:30 UTC do dia seguinte!) -> R$ 50,00
            val todayNightUtc = ZonedDateTime.of(
                today.plusDays(1),
                java.time.LocalTime.of(1, 30),
                ZoneOffset.UTC,
            ).toInstant().toEpochMilli()
            insertRealSaleAt(app, todayNightUtc, listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.PIX)

            // Venda 2: 3 dias atrás -> R$ 100,00
            insertRealSaleAt(
                app,
                today.minusDays(3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                listOf(SaleItemInput(prod, 2)),
                paymentMethod = SalePaymentMethod.CASH,
            )

            // Venda 3: 45 dias atrás -> R$ 150,00
            insertRealSaleAt(
                app,
                today.minusDays(45).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
                listOf(SaleItemInput(prod, 3)),
                paymentMethod = SalePaymentMethod.PIX,
            )
        }

        // Início exibe total acumulado sem filtro próprio (R$ 300,00); define filtro em VENDAS para "Hoje" (R$ 50,00)
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 300,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 300,00", substring = true)

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_today").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("sales_top_summary_card")
                    .performScrollTo()
                    .assert(hasAnyDescendant(hasText("R$ 50,00", substring = true)))
                true
            }.getOrDefault(false)
        }

        // Abre Fechamento: deve estar em "Todos" (R$ 300,00, 3 vendas)
        openFechamento()
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 300,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("period_dropdown_button").assertTextContains("Todos", substring = true)
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("3 vendas", substring = true)
            .assertTextContains("R$ 300,00", substring = true)

        // Filtra Fechamento para "7 dias" -> Venda 1 + Venda 2 = R$ 150,00 (2 vendas)
        composeRule.onNodeWithTag("period_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_week").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("2 vendas", substring = true)
                    .assertTextContains("R$ 150,00", substring = true)
                true
            }.getOrDefault(false)
        }

        // Filtra Fechamento para Datas personalizadas via calendário interativo em DD/MM/AAAA (45 dias atrás -> R$ 150,00, 1 venda)
        val oldDate = today.minusDays(45)
        composeRule.onNodeWithTag("period_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_custom").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("period_from_input").fetchSemanticsNodes().isNotEmpty()
        }
        PeriodCalendarTestHelper.pickDate(composeRule, "period_from_input", oldDate)
        PeriodCalendarTestHelper.pickDate(composeRule, "period_to_input", oldDate)
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("1 venda", substring = true)
                    .assertTextContains("R$ 150,00", substring = true)
                true
            }.getOrDefault(false)
        }

        // Verifica que Início manteve R$ 300,00 e VENDAS continuou em "Hoje" (R$ 50,00)
        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_inicio").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("home_metric_total_sold").assertTextContains("R$ 300,00", substring = true)

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vendas").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().assertTextContains("Hoje", substring = true)
        composeRule.onNodeWithTag("sales_top_summary_card")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("1 venda", substring = true)))
            .assert(hasAnyDescendant(hasText("R$ 50,00", substring = true)))
    }

    @Test
    fun `05 creating, editing and deleting sales updates fechamento automatically without restarting app`() {
        val zone = ZoneId.systemDefault()
        val todayMs = LocalDate.now(zone).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        val (anaId, prodId, existingSaleId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Boneco Reativo",
                price = MoneyCents(10_000L), // R$ 100,00
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val s1 = insertRealSaleAt(app, todayMs, listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH)
            Triple(ana.id, prod.id, s1)
        }

        // 1) Abre Fechamento: 1 venda R$ 100,00
        openFechamento()
        waitUiUntil {
            composeRule.onAllNodesWithText("R$ 100,00", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("hero_summary_card")
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 100,00", substring = true)

        // 2) Registra nova venda via Vender (R$ 100,00 Pix) -> Fechamento sobe para 2 vendas / R$ 200,00
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vender").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("picker_checkbox_$prodId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("picker_checkbox_$prodId").performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        openFechamento()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("2 vendas", substring = true)
                    .assertTextContains("R$ 200,00", substring = true)
                true
            }.getOrDefault(false)
        }

        // 3) Edita venda existente via VENDAS (1x -> 2x, total R$ 300,00)
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$existingSaleId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_$existingSaleId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_vender").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("selected_inc_$prodId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        openFechamento()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("2 vendas", substring = true)
                    .assertTextContains("R$ 300,00", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("closing_individual_row_$anaId")
            .performScrollTo()
            .assertTextContains("R$ 300,00", substring = true)

        // 4) Exclui venda existente via VENDAS -> resta 1 venda / R$ 100,00
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$existingSaleId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_delete_$existingSaleId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("delete_sale_confirm_button").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("delete_sale_confirm_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("sale_history_card_$existingSaleId").fetchSemanticsNodes().isEmpty()
        }

        openFechamento()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("hero_summary_card")
                    .assertTextContains("1 venda", substring = true)
                    .assertTextContains("R$ 100,00", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("closing_individual_row_$anaId")
            .performScrollTo()
            .assertTextContains("R$ 100,00", substring = true)
    }

    @Test
    fun `06 viewmodel closing enforces exact cent invariants, machine vs payment parity, no preview data, and filter isolation`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val dbName = "close.db"
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
                name = "Peça Exata",
                price = MoneyCents(9_999L), // R$ 99,99
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
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.ELO to 425),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 110),
                ),
            )

            // Banco vazio: ViewModel Fechamento inicia zerado e sem PreviewFixtures
            val saleRepo = RoomSaleRepository(db, db.saleDao())
            val vm = MixVendasShellViewModel(prefRepo, catalogRepo, machineRepo, saleRepo)
            val collectJob = CoroutineScope(Dispatchers.Main).launch {
                vm.uiState.collect {}
            }
            delay(50)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val emptyState = vm.uiState.value
            assertEquals(0, emptyState.closingFilteredRealSalesCount)
            assertEquals(0L, emptyState.closingRealOriginalSubtotal.rawCents)
            assertEquals(0L, emptyState.closingRealTotalDiscount.rawCents)
            assertEquals(0L, emptyState.closingRealTotalGross.rawCents)
            assertEquals(0L, emptyState.closingRealTotalFee.rawCents)
            assertEquals(0L, emptyState.closingRealTotalNet.rawCents)
            assertTrue(emptyState.closingRealPersonSummaries.isEmpty())
            assertTrue(emptyState.closingRealMachineBreakdowns.isEmpty())

            // Grava 4 vendas variadas: Dinheiro, Pix, Crédito Elo 2x, Crédito manual
            RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = { today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                ),
            )
            RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = { today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        finalAmountOverride = MoneyCents(18_500L),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.ELO,
                        installments = 2,
                    ),
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
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 1,
                        manualRateOverrideBps = 275,
                    ),
                ),
            )
            RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = { today.minusDays(20).atTime(14, 0).atZone(zone).toInstant().toEpochMilli() },
            ).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )

            var waited = 0
            while (vm.uiState.value.closingRealTotalSalesCount < 4 && waited < 60) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                delay(25)
                waited++
            }

            val allState = vm.uiState.value
            assertEquals(4, allState.closingFilteredRealSalesCount)
            assertEquals(
                allState.closingRealTotalGross.rawCents,
                allState.closingRealOriginalSubtotal.rawCents - allState.closingRealTotalDiscount.rawCents,
            )
            assertEquals(
                allState.closingRealTotalNet.rawCents,
                allState.closingRealTotalGross.rawCents - allState.closingRealTotalFee.rawCents,
            )
            assertEquals(
                allState.closingRealTotalGross.rawCents,
                allState.closingRealPersonSummaries.sumOf { it.grossAmount.rawCents },
            )
            assertEquals(
                allState.closingRealTotalFee.rawCents,
                allState.closingRealPersonSummaries.sumOf { it.feeAmount.rawCents },
            )
            assertEquals(
                allState.closingRealTotalNet.rawCents,
                allState.closingRealPersonSummaries.sumOf { it.netAmount.rawCents },
            )

            // Formas de pagamento fecham 100% com totais
            assertEquals(4, allState.closingRealPaymentBreakdowns.sumOf { it.count })
            assertEquals(allState.closingRealTotalGross.rawCents, allState.closingRealPaymentBreakdowns.sumOf { it.gross.rawCents })
            assertEquals(allState.closingRealTotalFee.rawCents, allState.closingRealPaymentBreakdowns.sumOf { it.fee.rawCents })
            assertEquals(allState.closingRealTotalNet.rawCents, allState.closingRealPaymentBreakdowns.sumOf { it.net.rawCents })

            // Dinheiro e Pix têm taxa zero
            val cashRow = allState.closingRealPaymentBreakdowns.first { it.method == SalePaymentMethod.CASH }
            val pixRow = allState.closingRealPaymentBreakdowns.first { it.method == SalePaymentMethod.PIX }
            assertEquals(0L, cashRow.fee.rawCents)
            assertEquals(0L, pixRow.fee.rawCents)
            assertEquals(cashRow.gross, cashRow.net)
            assertEquals(pixRow.gross, pixRow.net)

            // Maquininha fecham 100% com Débito + Crédito e nunca atribuem taxa única quando há taxas diferentes (5,35% vs 2,75%)
            val cardRows = allState.closingRealPaymentBreakdowns.filter { it.method.usesMachine }
            assertEquals(cardRows.sumOf { it.count }, allState.closingRealMachineBreakdowns.sumOf { it.count })
            assertEquals(cardRows.sumOf { it.gross.rawCents }, allState.closingRealMachineBreakdowns.sumOf { it.gross.rawCents })
            assertEquals(cardRows.sumOf { it.fee.rawCents }, allState.closingRealMachineBreakdowns.sumOf { it.fee.rawCents })
            assertEquals(cardRows.sumOf { it.net.rawCents }, allState.closingRealMachineBreakdowns.sumOf { it.net.rawCents })
            assertEquals(1, allState.closingRealMachineBreakdowns.size)
            val creditMach = allState.closingRealMachineBreakdowns.single()
            assertEquals(listOf(275, 535), creditMach.distinctEffectiveRatesBps)
            assertTrue(creditMach.hasManualOverride)
            assertNull(creditMach.singleRateSummaryLabel)

            // Nenhuma maquininha de Dinheiro/Pix
            assertFalse(allState.closingRealMachineBreakdowns.any { !it.paymentMethod.usesMachine })

            // Isolamento de filtros entre Fechamento, Início e VENDAS
            vm.onHomePeriodFilterSelected(PreviewPeriodFilter.MONTH)
            vm.onPeriodFilterSelected(PreviewPeriodFilter.WEEK)
            vm.onClosingPeriodFilterSelected(PreviewPeriodFilter.TODAY)
            delay(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            val filteredState = vm.uiState.value
            assertEquals(PreviewPeriodFilter.TODAY, filteredState.closingPeriodFilter)
            assertEquals(3, filteredState.closingFilteredRealSalesCount)
            assertEquals(PreviewPeriodFilter.MONTH, filteredState.homePeriodFilter)
            assertEquals(PreviewPeriodFilter.WEEK, filteredState.periodFilter)

            collectJob.cancel()
        } finally {
            db.close()
            assertTrue(context.deleteDatabase(dbName))
        }
    }
}
