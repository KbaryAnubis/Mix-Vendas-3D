package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
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
class StageV23ProductsUiTest {

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
    fun `products V2-3 screen masks centavos, validates reais-only split, hides fee owner when proportional, and supports cancel edit`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (anaId, carlosId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val carlos = app.catalogRepository.createPerson("Carlos")
            ana.id to carlos.id
        }

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_products").assertIsDisplayed()
        composeRule.onNodeWithTag("topbar_title").assertIsDisplayed()
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)
        composeRule.onAllNodesWithText("CATÁLOGO NO ROOM", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("V19", substring = true).assertCountEquals(0)

        composeRule.onNodeWithTag("new_product_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_editor_dialog").assertIsDisplayed()

        // Modo padrão PROPORTIONAL não exibe campo de pessoa responsável pela tarifa
        composeRule.onAllNodesWithTag("fee_owner_$anaId").assertCountEquals(0)
        composeRule.onNodeWithTag("fee_mode_equal").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("fee_owner_$anaId").assertCountEquals(0)

        composeRule.onNodeWithTag("product_name_input").performScrollTo().performTextInput("Pastel de queijo")

        // Digitação bruta em centavos -> 2200 => 22,00
        composeRule.onNodeWithTag("product_price_input").performScrollTo().performTextInput("2200")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_price_input").assert(hasText("22,00"))

        composeRule.onNodeWithTag("product_participant_check_$anaId").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_$anaId").performScrollTo().performTextInput("1500")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_$anaId").assert(hasText("15,00"))

        composeRule.onNodeWithTag("product_participant_check_$carlosId").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Valor incorreto (600 => 6,00) gera soma 21,00 != 22,00 e bloqueia salvamento
        composeRule.onNodeWithTag("product_participant_amount_$carlosId").performScrollTo().performTextInput("600")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("product_form_error").fetchSemanticsNodes().isNotEmpty()
        }

        // Corrige para 700 => 7,00 (15,00 + 7,00 = 22,00) e seleciona modo Pessoa específica
        composeRule.onNodeWithTag("product_participant_amount_$carlosId").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("product_participant_amount_$carlosId").performTextInput("700")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_$carlosId").assert(hasText("7,00"))
        composeRule.onNodeWithTag("fee_mode_individual").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_fee_owner_dropdown").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("fee_owner_$anaId")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("fee_owner_$anaId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_fee_owner_dropdown").assert(hasText("Ana", substring = true))

        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("products_feedback_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val savedProduct = runBlocking(Dispatchers.IO) {
            app.catalogRepository.observeAllProducts().first().first { it.name == "Pastel de queijo" }
        }
        assertEquals(2_200L, savedProduct.price.rawCents)
        assertEquals(1_500L, savedProduct.participants.first { it.personId == anaId }.amount.rawCents)
        assertEquals(700L, savedProduct.participants.first { it.personId == carlosId }.amount.rawCents)
        assertEquals(FeeResponsibilityMode.INDIVIDUAL, savedProduct.feeResponsibilityMode)
        assertEquals(anaId, savedProduct.feeOwnerPersonId)
        assertEquals(true, savedProduct.active)

        // A listagem indica o status, mas não oferece uma ação interativa para alterá-lo.
        composeRule.onNodeWithTag("product_card_${savedProduct.id}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("product_status_${savedProduct.id}")
            .performScrollTo()
            .assert(hasText("Ativo", substring = true))
        composeRule.onAllNodesWithTag("toggle_product_active_${savedProduct.id}").assertCountEquals(0)

        // O status é alterado dentro do formulário e salvo junto com o produto.
        composeRule.onNodeWithTag("edit_product_${savedProduct.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_editor_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Produto ativo para novas vendas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("product_active_switch").assertIsOn()
        composeRule.onNodeWithTag("product_active_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("product_editor_dialog").fetchSemanticsNodes().isEmpty() &&
                runBlocking(Dispatchers.IO) {
                    app.catalogRepository.observeAllProducts().first().singleOrNull()?.active == false &&
                        app.catalogRepository.observeActiveProducts().first().isEmpty()
                }
        }
        composeRule.onNodeWithTag("product_status_${savedProduct.id}")
            .performScrollTo()
            .assert(hasText("Inativo", substring = true))

        // Reabre a edição para confirmar persistência do status desligado; depois cancela.
        composeRule.onNodeWithTag("edit_product_${savedProduct.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_active_switch").assertIsOff()
        composeRule.onNodeWithTag("cancel_product_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("product_editor_dialog").assertCountEquals(0)
        composeRule.onNodeWithTag("product_card_${savedProduct.id}").assertExists()

        // Reativa no editor e confirma que volta ao catálogo disponível para vendas
        composeRule.onNodeWithTag("edit_product_${savedProduct.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("product_active_switch").performScrollTo().performClick()
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                app.catalogRepository.observeActiveProducts().first().any { it.id == savedProduct.id }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("product_editor_dialog").fetchSemanticsNodes().isEmpty()
        }

        // CANCELAR preserva; EXCLUIR remove de fato um produto sem vendas vinculadas
        composeRule.onNodeWithTag("delete_product_${savedProduct.id}").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("confirm_delete_product").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("cancel_delete_product").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_card_${savedProduct.id}").assertExists()

        composeRule.onNodeWithTag("delete_product_${savedProduct.id}").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("confirm_delete_product").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("confirm_delete_product").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                app.catalogRepository.observeAllProducts().first().none { it.id == savedProduct.id }
            }
        }
    }
}
