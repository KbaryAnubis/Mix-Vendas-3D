package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage2B1UiIntegrationTest {

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
    fun `creating people, creating products, renaming person, and selecting in vender works end to end`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        composeRule.waitForIdle()

        // 1. Abrir Mais -> Nomes e cadastrar 3 pessoas (sem limite de 2)
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_names").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_names").assertIsDisplayed()

        composeRule.onNodeWithTag("person_name_input").performTextInput("Lucas")
        composeRule.onNodeWithTag("save_person_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("\"LUCAS\" adicionada com sucesso.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("person_name_input").performTextInput("Beatriz")
        composeRule.onNodeWithTag("save_person_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("\"BEATRIZ\" adicionada com sucesso.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("person_name_input").performTextInput("Terceiro")
        composeRule.onNodeWithTag("save_person_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("\"TERCEIRO\" adicionada com sucesso.")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Tenta duplicado com espaço e maiúsculas -> deve bloquear e exibir erro
        composeRule.onNodeWithTag("person_name_input").performTextInput("  LUCAS ")
        composeRule.onNodeWithTag("save_person_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Já existe uma pessoa cadastrada com o nome \"LUCAS\".")
                .fetchSemanticsNodes().isNotEmpty()
        }

        val people = runBlocking(Dispatchers.IO) { app.catalogRepository.observePeople().first() }
        assertEquals(3, people.size)
        val lucas = people.first { it.name == "LUCAS" }
        val beatriz = people.first { it.name == "BEATRIZ" }

        // 2. Voltar para Mais -> Tipos / produtos e cadastrar Produto A (Exemplo 1: R$ 22,00 = 17,00 + 5,00)
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_products").assertIsDisplayed()

        composeRule.onNodeWithTag("new_product_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_editor_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("product_name_input").performTextInput("Produto Exemplo A")
        composeRule.onNodeWithTag("product_price_input").performTextInput("22,00")

        composeRule.onNodeWithTag("product_participant_check_${lucas.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_${lucas.id}")
            .performScrollTo()
            .performTextInput("17,00")

        composeRule.onNodeWithTag("product_participant_check_${beatriz.id}")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_${beatriz.id}")
            .performScrollTo()
            .performTextReplacement("5,00")

        composeRule.onNodeWithTag("fee_mode_individual")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_fee_owner_dropdown")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("fee_owner_${lucas.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("fee_owner_${lucas.id}")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_fee_owner_dropdown")
            .assert(hasText("LUCAS", substring = true))

        composeRule.onNodeWithTag("save_product_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("product_editor_dialog")
                .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_feedback_banner"))
        composeRule.onNodeWithTag("products_feedback_banner").assertIsDisplayed()

        // 3. Voltar em Nomes, tentar excluir Lucas (vinculado -> bloqueado) e renomear para "Lucas Atualizado"
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_names").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("delete_person_${lucas.id}"))
        composeRule.onNodeWithTag("delete_person_${lucas.id}")
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText(
                "Não é possível excluir \"LUCAS\" porque ela está vinculada a produto(s) existente(s).",
            ).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("edit_person_${lucas.id}"))
        composeRule.onNodeWithTag("edit_person_${lucas.id}")
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("person_name_input"))
        composeRule.onNodeWithTag("person_name_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("person_name_input").performTextInput("Lucas Atualizado")
        composeRule.onNodeWithTag("save_person_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("Nome atualizado para \"LUCAS ATUALIZADO\".")
                .fetchSemanticsNodes().isNotEmpty()
        }

        // 4. Ir para a tela Vender -> confirmar produto real do Room com nome atualizado e sem produtos demo
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithText("PRODUTO EXEMPLO A").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("PRODUTO EXEMPLO A").assertIsDisplayed()
        composeRule.onAllNodesWithText("Impressão 3D Miniatura").assertCountEquals(0)

        val products = runBlocking(Dispatchers.IO) { app.catalogRepository.observeActiveProducts().first() }
        assertEquals(1, products.size)
        val prodId = products.first().id
        assertEquals("LUCAS ATUALIZADO", products.first().participants.first().personName)

        composeRule.onNodeWithTag("picker_checkbox_$prodId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1× PRODUTO EXEMPLO A").assertExists()
    }

    @Test
    fun `names V2 layout displays clean header, three sections, empty state, cancel edit, and delete confirmation`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_names").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_names").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_title").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cadastro de nomes").assertCountEquals(1)
        composeRule.onAllNodesWithText("PERSONALIZAÇÃO", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("MIX VENDAS 3D", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onAllNodesWithText("Cadastrar participante").assertCountEquals(1)
        composeRule.onAllNodesWithText("Nome da pessoa").assertCountEquals(1)
        composeRule.onAllNodesWithText("Ex.: João, Maria, Equipe 1").assertCountEquals(1)
        composeRule.onAllNodesWithText("Adicionar pessoa").assertCountEquals(1)
        composeRule.onAllNodesWithText("Sobre os participantes").assertCountEquals(1)
        composeRule.onAllNodesWithText(
            "Cadastre as pessoas que participam das vendas para distribuir os valores e acompanhar o líquido de cada uma.",
        ).assertCountEquals(1)
        composeRule.onAllNodesWithText("Nomes cadastrados").assertCountEquals(1)

        composeRule.onNodeWithTag("names_empty_state").performScrollTo().assertIsDisplayed()

        // Cria participante e valida linha + edição cancelável + exclusão confirmada
        composeRule.onNodeWithTag("person_name_input").performScrollTo().performTextInput("Equipe 1")
        composeRule.onNodeWithTag("save_person_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("names_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val createdId = runBlocking(Dispatchers.IO) {
            app.catalogRepository.observePeople().first().single().id
        }

        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("person_row_$createdId"))
        composeRule.onNodeWithTag("person_row_$createdId").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("edit_person_$createdId"))
        composeRule.onNodeWithTag("edit_person_$createdId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("cancel_person_edit_button"))
        composeRule.onAllNodesWithText("Salvar nome").assertCountEquals(1)
        composeRule.onNodeWithTag("cancel_person_edit_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Adicionar pessoa").assertCountEquals(1)

        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("delete_person_$createdId"))
        composeRule.onNodeWithTag("delete_person_$createdId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cancel_delete_person_button").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_delete_person_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("person_row_$createdId").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("names_empty_state"))
        composeRule.onNodeWithTag("names_empty_state").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_mais").assertIsDisplayed()
    }
}
