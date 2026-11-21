package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage2B2UiIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun cleanUpDatabase() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    @Test
    fun `machines crud ui, toggle preservation, vender method filtering, and pre-existing catalog preservation work end to end`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()

        // Pré-popula pessoas e produto no Room para comprovar preservação durante Etapa 2B-2
        val existingProductId = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Alice")
            val p2 = app.catalogRepository.createPerson("Rafael")
            app.catalogRepository.createProduct(
                name = "Vaso Geométrico 3D",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            ).id
        }

        // Navega para Mais -> Maquininhas
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_machines").assertIsDisplayed()
        composeRule.onNodeWithTag("machines_empty_state").assertExists()

        // 1) Cria maquininha completa (Débito 1,90%, Crédito padrão 2,80%, Visa 2,50%, 3x +1,60%)
        composeRule.onNodeWithTag("new_machine_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("machine_name_input").performTextInput("Ton Pro")
        composeRule.onNodeWithTag("machine_debit_rate_input").performTextInput("1,90")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_credit_default_rate_input"))
        composeRule.onNodeWithTag("machine_credit_default_rate_input").performTextInput("2,80")

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_name_input"))
        composeRule.onNodeWithTag("custom_brand_name_input").performTextInput("Visa Custom")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performTextInput("2,50")
        composeRule.onNodeWithTag("add_custom_brand_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_switch"))
        composeRule.onNodeWithTag("editor_installments_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_installment_input_3"))
        composeRule.onNodeWithTag("machine_installment_input_3").performTextInput("1,60")

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("machines_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val tonMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.name == "Ton Pro" }
        }
        assertEquals(190, tonMachine.debitRateBps)
        assertEquals(280, tonMachine.creditDefaultRateBps)
        assertEquals(250, tonMachine.customBrandRates.firstOrNull { it.name == "Visa Custom" }?.creditRateBps)
        assertEquals(160, tonMachine.installmentAddonsBps[3])

        // 2) Cria maquininha SOMENTE DÉBITO (crédito desativado)
        composeRule.onNodeWithTag("new_machine_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_name_input").performTextInput("Cartão Débito Local")
        composeRule.onNodeWithTag("machine_debit_rate_input").performTextInput("1,45")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_credit_enabled_switch"))
        composeRule.onNodeWithTag("editor_credit_enabled_switch").performClick()
        composeRule.waitForIdle()
        // Ao desativar crédito, campo de taxa padrão de crédito é ocultado
        composeRule.onAllNodesWithTag("machine_credit_default_rate_input").assertCountEquals(0)
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodesWithTag("machines_feedback_banner")
                    .fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("machine_credit_switch_${tonMachine.id}")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val debitOnlyMachine = runBlocking(Dispatchers.IO) {
            val all = app.machineRepository.observeMachines().first()
            assertEquals(2, all.size)
            all.first { it.name == "Cartão Débito Local" }
        }

        // 3) Desativa e reativa crédito na "Ton Pro" pelo card e verifica preservação
        composeRule.onNodeWithTag("machine_credit_switch_${tonMachine.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                !app.machineRepository.observeMachines().first().first { it.id == tonMachine.id }.creditEnabled
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_credit_switch_${tonMachine.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                val restored = app.machineRepository.observeMachines().first().first { it.id == tonMachine.id }
                restored.creditEnabled && restored.customBrandRates.firstOrNull { it.name == "Visa Custom" }?.creditRateBps == 250 && restored.installmentAddonsBps[3] == 160
            }
        }

        // 4) Navega para a tela Vender e valida todas as regras de forma de pagamento
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()

        // Pix oculta maquininha e tarifas
        composeRule.onNodeWithTag("pay_method_pix").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("machine_extras_section").assertCountEquals(0)

        // Dinheiro oculta maquininha e tarifas
        composeRule.onNodeWithTag("pay_method_cash").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("machine_extras_section").assertCountEquals(0)

        // Débito mostra todas as maquininhas (incluindo "Cartão Débito Local") e seleciona via busca
        composeRule.onNodeWithTag("pay_method_debit").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_picker_search").performTextInput("Débito Local")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_picker_row_${debitOnlyMachine.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Cartão Débito Local").assertExists()

        // Ao mudar para Crédito, a maquininha somente débito incompatível é limpa e NÃO aparece na lista
        composeRule.onNodeWithTag("pay_method_credit").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("brand_selector_dropdown").assertCountEquals(0)
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("machine_picker_row_${debitOnlyMachine.id}").assertCountEquals(0)
        composeRule.onNodeWithTag("machine_picker_row_${tonMachine.id}").performClick()
        composeRule.waitForIdle()

        // Agora Crédito com "Ton Pro" exibe bandeiras e parcelamento
        composeRule.onNodeWithTag("brand_selector_dropdown").assertExists()
        composeRule.onNodeWithTag("installments_selector_dropdown").assertExists()
        composeRule.onNodeWithTag("vender_rate_summary").assertExists()

        // Selecionar parcela 3x e conferir taxa automática 3x (padrão 2,80% + 1,60% = 4,40%)
        composeRule.onNodeWithTag("installments_selector_dropdown")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("3x (+1,60%)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4,40%", substring = true).assertExists()

        // Testar tarifa manual substituindo automática e validando limite 100%
        composeRule.onNodeWithTag("manual_fee_override_row").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("manual_rate_input").performScrollTo().performTextInput("101,00")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vender_manual_rate_error", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("manual_rate_input").performTextReplacement("3,50")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("vender_rate_summary").performScrollTo()
        composeRule.onNodeWithText("Tarifa aplicada nesta venda (manual): 3,50%", substring = true).assertExists()

        // Confirma preservação integral de pessoas e produtos já cadastrados no Room
        runBlocking(Dispatchers.IO) {
            assertEquals(2, app.catalogRepository.observePeople().first().size)
            val products = app.catalogRepository.observeAllProducts().first()
            assertEquals(1, products.size)
            assertEquals(existingProductId, products.first().id)
            assertTrue(products.first().active)
        }
    }
}
