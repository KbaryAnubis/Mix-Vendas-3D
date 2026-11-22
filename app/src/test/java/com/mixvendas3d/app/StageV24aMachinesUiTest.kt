package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV24aMachinesUiTest {

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
    fun `machines V2-4A opens listing first, collapses sections without data loss, preserves disabled credit rates, and guards back discard`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val existingMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 180,
                    creditEnabled = true,
                    creditDefaultRateBps = 320,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(
                        CardBrand.VISA to 310,
                        CardBrand.MASTERCARD to 330,
                    ),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(
                        2 to 90,
                        3 to 120,
                    ),
                ),
            )
        }

        // 1. Entrada inicial na listagem, sem formulário aberto e cabeçalho V2 limpo
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_machines").assertIsDisplayed()
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)
        composeRule.onAllNodesWithTag("machine_editor_dialog").assertCountEquals(0)
        composeRule.onAllNodesWithText("CARTÕES E TARIFAS NO ROOM", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("V19", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("new_machine_button").assertIsDisplayed()
        composeRule.onNodeWithTag("machines_search_input").assertExists()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag("machines_list_lazy")
                    .performScrollToNode(hasTestTag("machine_card_${existingMachine.id}"))
                true
            }.getOrDefault(false)
        }

        // 2. Abertura do formulário vazio pela ação Cadastrar + recolher/expandir e preservação de flags
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("new_machine_button"))
        composeRule.onNodeWithTag("new_machine_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("machine_name_input").assert(hasText(""))

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_name_input"))
        composeRule.onNodeWithTag("custom_brand_name_input").performTextInput("Visa Custom")
        composeRule.onNodeWithTag("new_custom_brand_debit_rate_input").performTextInput("1,50")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performTextInput("2,95")
        composeRule.onNodeWithTag("add_custom_brand_button").performClick()
        composeRule.waitForIdle()

        val visaCustomKey = CustomBrandKeyCodec.encodeKey("VISA CUSTOM")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_credit_rate_$visaCustomKey"))
        composeRule.onNodeWithTag("custom_brand_credit_rate_$visaCustomKey").assert(hasText("2,95"))

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_switch"))
        composeRule.onNodeWithTag("editor_installments_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_installment_input_3"))
        composeRule.onNodeWithTag("machine_installment_input_3").performTextInput("1,40")
        composeRule.waitForIdle()

        // 3. Recolher e reexpandir seções sem apagar valores nem desativar recursos
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("toggle_brand_section_collapse"))
        composeRule.onNodeWithTag("toggle_brand_section_collapse").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("custom_brand_credit_rate_$visaCustomKey").assertCountEquals(0)
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").assertIsOn()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("toggle_brand_section_collapse"))
        composeRule.onNodeWithTag("toggle_brand_section_collapse").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_credit_rate_$visaCustomKey"))
        composeRule.onNodeWithTag("custom_brand_credit_rate_$visaCustomKey").assert(hasText("2,95"))

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("toggle_installments_section_collapse"))
        composeRule.onNodeWithTag("toggle_installments_section_collapse").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("machine_installment_input_3").assertCountEquals(0)
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_switch"))
        composeRule.onNodeWithTag("editor_installments_switch").assertIsOn()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("toggle_installments_section_collapse"))
        composeRule.onNodeWithTag("toggle_installments_section_collapse").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_installment_input_3"))
        composeRule.onNodeWithTag("machine_installment_input_3").assert(hasText("1,40"))

        // 4. Desativar e reativar flags de crédito/bandeira/parcelamento preserva todas as taxas digitadas
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_credit_enabled_switch"))
        composeRule.onNodeWithTag("editor_credit_enabled_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_credit_enabled_switch").assertIsOff()
        composeRule.onAllNodesWithTag("machine_credit_default_rate_input").assertCountEquals(0)
        composeRule.onAllNodesWithTag("editor_brand_rates_section").assertCountEquals(1)
        composeRule.onAllNodesWithTag("editor_installments_section").assertCountEquals(0)

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_credit_enabled_switch"))
        composeRule.onNodeWithTag("editor_credit_enabled_switch")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("editor_credit_enabled_switch").assertIsOn()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_credit_rate_$visaCustomKey"))
        composeRule.onNodeWithTag("custom_brand_credit_rate_$visaCustomKey").assert(hasText("2,95"))
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_installment_input_3"))
        composeRule.onNodeWithTag("machine_installment_input_3").assert(hasText("1,40"))

        // 5. Voltar pelo topbar com alterações pendentes não descarta silenciosamente -> confirma retorno à listagem
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_discard_machine_button").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("machine_editor_dialog").assertCountEquals(0)
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("machine_card_${existingMachine.id}"))
        composeRule.onNodeWithTag("machine_card_${existingMachine.id}").assertExists()

        // 6. Edição carregando dados existentes + cancelamento mantendo listagem e banco intactos
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("edit_machine_${existingMachine.id}"))
        composeRule.onNodeWithTag("edit_machine_${existingMachine.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("machine_name_input").assert(hasText("STONE PRO"))
        composeRule.onNodeWithTag("machine_name_input").performTextReplacement("Stone Alterada")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("cancel_machine_editor_button"))
        composeRule.onNodeWithTag("cancel_machine_editor_button").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag("machine_editor_dialog").assertCountEquals(0)
        val storedAfterCancel = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.id == existingMachine.id }
        }
        assertEquals("Stone Pro", storedAfterCancel.name)
        assertEquals(310, storedAfterCancel.brandRatesBps[CardBrand.VISA])
        assertEquals(120, storedAfterCancel.installmentAddonsBps[3])

        // 7. Na listagem, voltar retorna corretamente para Mais opções
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_mais").assertIsDisplayed()
    }

    @Test
    fun `machines F1 configures custom maxInstallments, displays configured limit on card, reverts to legacy, and preserves higher tier rates`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val legacyMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Legado SumUp",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 310,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(12 to 450),
                    maxInstallments = null,
                ),
            )
        }

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag("machines_list_lazy")
                    .performScrollToNode(hasTestTag("machine_card_${legacyMachine.id}"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("machine_card_${legacyMachine.id}")
            .assert(hasAnyDescendant(hasText("Parcelamento padrão (1, 2, 3, 4, 6 e 12x)", substring = true)))

        // 1) Cadastra nova maquininha com máximo 18x e preenche taxa para 13x e 20x
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("new_machine_button"))
        composeRule.onNodeWithTag("new_machine_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_name_input").performTextInput("PagBank F1")
        composeRule.onNodeWithTag("machine_debit_rate_input").performTextInput("1,89")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_credit_default_rate_input"))
        composeRule.onNodeWithTag("machine_credit_default_rate_input").performTextInput("3,49")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_switch"))
        composeRule.onNodeWithTag("editor_installments_switch").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_section"))
        composeRule.onNodeWithTag("machine_max_installments_option_18")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_max_installments_summary")
            .performScrollTo()
            .assertTextContains("Até 18x", substring = true)

        composeRule.onNodeWithTag("machine_installment_input_13")
            .performScrollTo()
            .performTextInput("5,20")
        composeRule.onNodeWithTag("machine_installment_input_20")
            .performScrollTo()
            .performTextInput("8,40")

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isEmpty()
        }

        val created18 = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.name == "PAGBANK F1" }
        }
        assertEquals(18, created18.maxInstallments)
        assertEquals(520, created18.installmentAddonsBps[13])
        assertEquals(840, created18.installmentAddonsBps[20])
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("machine_card_${created18.id}"))
        composeRule.onNodeWithTag("machine_card_${created18.id}")
            .assert(hasAnyDescendant(hasText("Parcelamento ativo (até 18x)", substring = true)))

        // 2) Edita para máximo 12x -> taxas de 13x e 20x devem permanecer salvas
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("edit_machine_${created18.id}"))
        composeRule.onNodeWithTag("edit_machine_${created18.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_section"))
        composeRule.onNodeWithTag("machine_max_installments_option_12")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isEmpty()
        }

        val updated12 = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.id == created18.id }
        }
        assertEquals(12, updated12.maxInstallments)
        assertEquals(520, updated12.installmentAddonsBps[13])
        assertEquals(840, updated12.installmentAddonsBps[20])
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("machine_card_${created18.id}"))
        composeRule.onNodeWithTag("machine_card_${created18.id}")
            .performScrollTo()
            .assert(hasAnyDescendant(hasText("Parcelamento ativo (até 12x)", substring = true)))

        // 3) Reabre edição, seleciona modo legado explícito, confirma dirty check ao voltar e depois salva
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("edit_machine_${created18.id}"))
        composeRule.onNodeWithTag("edit_machine_${created18.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_installments_section"))
        composeRule.onNodeWithTag("machine_max_installments_option_legacy")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_discard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("cancel_discard_machine_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("machine_editor_dialog").fetchSemanticsNodes().isEmpty()
        }

        val revertedLegacy = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.id == created18.id }
        }
        assertNull(revertedLegacy.maxInstallments)
        assertEquals(520, revertedLegacy.installmentAddonsBps[13])
        assertEquals(840, revertedLegacy.installmentAddonsBps[20])
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("machine_card_${created18.id}"))
        composeRule.onNodeWithTag("machine_card_${created18.id}")
            .assert(hasAnyDescendant(hasText("Parcelamento padrão (1, 2, 3, 4, 6 e 12x)", substring = true)))
    }
}
