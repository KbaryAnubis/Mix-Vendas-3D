package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
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
class StageV25B2MachinePickerTest {

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

    private fun openVenderWithMethod(methodTag: String) {
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(methodTag).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun openMachinePicker() {
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToMachineRow(machineId: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("machine_picker_list")
                    .performScrollToNode(hasTestTag("machine_picker_row_$machineId"))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `01 open close headers and distinct empty states match V2-5B2`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        // 1) Banco sem nenhuma maquininha -> estado vazio de cadastro no Débito
        openVenderWithMethod("pay_method_debit")
        openMachinePicker()
        composeRule.onNodeWithTag("machine_picker_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Selecionar maquininha").assertIsDisplayed()
        composeRule.onNodeWithText("Escolha a maquininha para receber no débito.").assertIsDisplayed()
        composeRule.onNodeWithTag("machine_picker_empty_state")
            .assertTextContains("Você ainda não possui maquininhas cadastradas. Cadastre uma em Mais → Maquininhas.")

        // Fecha pelo botão X
        composeRule.onNodeWithTag("close_machine_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }

        // 2) Cadastra apenas uma maquininha somente débito -> no Crédito mostra vazio específico de crédito
        runBlocking(Dispatchers.IO) {
            app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Somente Débito",
                    debitRateBps = 149,
                    creditEnabled = false,
                    creditDefaultRateBps = 0,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
        }

        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        composeRule.waitForIdle()
        openMachinePicker()
        composeRule.onNodeWithText("Selecionar maquininha").assertIsDisplayed()
        composeRule.onNodeWithText("Escolha uma maquininha habilitada para crédito.").assertIsDisplayed()
        composeRule.onNodeWithTag("machine_picker_empty_state")
            .assertTextContains("Nenhuma maquininha disponível para crédito. Ative o crédito em Mais → Maquininhas.")

        composeRule.onNodeWithTag("close_machine_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `02 search filters with case and accent compatibility preserves selected machine and clears cleanly`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (cartaoUnicoId, infiniteId) = runBlocking(Dispatchers.IO) {
            val m1 = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Cartão Único Débito",
                    debitRateBps = 155,
                    creditEnabled = true,
                    creditDefaultRateBps = 310,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val m2 = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "InfinitePay Smart",
                    debitRateBps = 175,
                    creditEnabled = true,
                    creditDefaultRateBps = 290,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 270),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 110),
                ),
            )
            m1.id to m2.id
        }

        openVenderWithMethod("pay_method_credit")
        openMachinePicker()
        scrollToMachineRow(cartaoUnicoId)
        composeRule.onNodeWithTag("machine_picker_row_$cartaoUnicoId").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("machine_selector_dropdown").assertTextContains("Cartão Único Débito")

        // Reabre e pesquisa sem acento e em minúsculas por "infinite" -> selecionada fica oculta, mas continua selecionada no formulário
        openMachinePicker()
        composeRule.onNodeWithTag("machine_picker_row_$cartaoUnicoId").assertIsSelected()
        composeRule.onNodeWithTag("machine_picker_search").performTextInput("infinite")
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_row_$cartaoUnicoId").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_row_$infiniteId").assertIsDisplayed()
        composeRule.onNodeWithTag("machine_selector_dropdown").assertTextContains("Cartão Único Débito")

        // Pesquisa sem correspondência exibe estado vazio de pesquisa
        composeRule.onNodeWithTag("machine_picker_clear_search").performClick()
        composeRule.onNodeWithTag("machine_picker_search").performTextInput("Máquina Inexistente")
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_empty_state").assertTextContains("Nenhuma maquininha encontrada.")

        // Pesquisa sem acentos ("cartao unico") encontra "Cartão Único Débito" e mantém destaque da seleção
        composeRule.onNodeWithTag("machine_picker_clear_search").performClick()
        composeRule.onNodeWithTag("machine_picker_search").performTextInput("cartao unico")
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_row_$cartaoUnicoId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_row_$cartaoUnicoId").assertIsSelected()
        composeRule.onNodeWithTag("machine_picker_selected_icon_$cartaoUnicoId", useUnmergedTree = true).assertIsDisplayed()

        // Limpa pesquisa e fecha -> seleção anterior continua intacta
        composeRule.onNodeWithTag("machine_picker_clear_search").performClick()
        composeRule.onNodeWithTag("close_machine_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("machine_selector_dropdown").assertTextContains("Cartão Único Débito")
    }

    @Test
    fun `03 eligibility filters debit vs credit rates and selection closes automatically updating controls`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (debitOnlyId, fullCreditId) = runBlocking(Dispatchers.IO) {
            val debitOnly = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Terminal Débito Exclusivo",
                    debitRateBps = 139,
                    creditEnabled = false,
                    creditDefaultRateBps = 0,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val fullCredit = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Mega Crédito",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 345,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 315),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 120),
                ),
            )
            debitOnly.id to fullCredit.id
        }

        // No Débito: ambas aparecem com taxa de débito real
        openVenderWithMethod("pay_method_debit")
        openMachinePicker()
        scrollToMachineRow(debitOnlyId)
        composeRule.onNodeWithTag("machine_picker_row_$debitOnlyId")
            .assertTextContains("Débito: 1,39%", substring = true)
        scrollToMachineRow(fullCreditId)
        composeRule.onNodeWithTag("machine_picker_row_$fullCreditId")
            .assertTextContains("Débito: 1,99%", substring = true)

        composeRule.onNodeWithTag("close_machine_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }

        // No Crédito: apenas creditEnabled = true aparece, exibindo taxa padrão + recursos de crédito
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        composeRule.waitForIdle()
        openMachinePicker()
        composeRule.onAllNodesWithTag("machine_picker_row_$debitOnlyId").assertCountEquals(0)
        scrollToMachineRow(fullCreditId)
        composeRule.onNodeWithTag("machine_picker_row_$fullCreditId")
            .assertTextContains("Crédito padrão: 3,45%", substring = true)
            .assertTextContains("Taxas por bandeira", substring = true)
            .assertTextContains("Parcelamento", substring = true)

        // Tocar na maquininha seleciona e fecha automaticamente, exibindo bandeira e parcelas
        composeRule.onNodeWithTag("machine_picker_row_$fullCreditId").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("machine_selector_dropdown").assertTextContains("Ton Mega Crédito")
        composeRule.onNodeWithTag("brand_selector_dropdown").assertExists()
        composeRule.onNodeWithTag("installments_selector_dropdown").assertExists()
    }

    @Test
    fun `04 historical machine during sale edit shows preserved badge and keeps snapshot rates`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (saleId, histMachineId, currentMachineId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prod = app.catalogRepository.createProduct(
                name = "Luminária 3D",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val histMachine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Histórica",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 290,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 260),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 140),
                ),
            )
            val currentMachine = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Cielo Atual",
                    debitRateBps = 170,
                    creditEnabled = true,
                    creditDefaultRateBps = 410,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val savedSale = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = histMachine,
                        cardBrand = CardBrand.VISA,
                        installments = 2,
                    ),
                ),
            )
            // Exclui a maquininha histórica do catálogo após salvar a venda
            app.machineRepository.deleteMachine(histMachine.id)
            Triple(savedSale.id, histMachine.id, currentMachine.id)
        }

        // Abre a venda em edição na aba VENDAS
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("screen_vendas")
                    .performScrollToNode(hasTestTag("sale_history_card_$saleId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("sale_edit_$saleId").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("vender_editing_banner").fetchSemanticsNodes().isNotEmpty()
        }

        openMachinePicker()
        scrollToMachineRow(histMachineId)
        composeRule.onNodeWithTag("machine_picker_row_$histMachineId").assertIsSelected()
        composeRule.onNodeWithTag("machine_picker_historical_badge_$histMachineId")
            .assertIsDisplayed()
            .assertTextContains("Da venda — histórico preservado")

        // Tocar na própria maquininha histórica fecha automaticamente e preserva a taxa snapshot (4,00%)
        composeRule.onNodeWithTag("machine_picker_row_$histMachineId").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("machine_selector_dropdown").assertTextContains("Stone Histórica")
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("4,00%", substring = true)

        // Trocar para a maquininha atual do catálogo fecha automaticamente e recalcula com 4,10%
        openMachinePicker()
        scrollToMachineRow(currentMachineId)
        composeRule.onNodeWithTag("machine_picker_row_$currentMachineId").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("machine_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("machine_selector_dropdown").assertTextContains("Cielo Atual")
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
            .assertTextContains("4,10%", substring = true)
    }
}
