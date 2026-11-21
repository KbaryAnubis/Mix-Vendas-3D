package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
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
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
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
class StageV25B1ProductPickerTest {

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

    private fun openProductPickerOnVender() {
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_product_picker_button")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollToPickerProduct(productId: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("product_picker_list")
                    .performScrollToNode(hasTestTag("picker_row_$productId"))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `01 open close empty catalog and header match V2-5B1`() {
        openProductPickerOnVender()

        composeRule.onNodeWithTag("product_picker_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Selecionar produtos").assertIsDisplayed()
        composeRule.onNodeWithText("Escolha os produtos e ajuste as quantidades.").assertIsDisplayed()
        composeRule.onNodeWithTag("picker_empty_state")
            .assertTextContains("Nenhum produto disponível. Cadastre produtos em Mais → Produtos.")
        composeRule.onNodeWithTag("product_picker_counts").assertTextContains("0 produto(s) · 0 unidade(s)")
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 0,00")
        composeRule.onNodeWithTag("close_product_picker_button").assertIsEnabled()

        // Fechar pelo botão X superior direito
        composeRule.onNodeWithTag("product_picker_close_x_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isEmpty()
        }

        // Reabrir e fechar pelo botão Concluir seleção
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `02 search filters case-insensitively preserves hidden selections and clears cleanly`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (dragaoId, torreId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val bruno = app.catalogRepository.createPerson("Bruno")
            val p1 = app.catalogRepository.createProduct(
                name = "Dragão Articulação 3D",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(1_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val p2 = app.catalogRepository.createProduct(
                name = "Torre Castelo",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(bruno.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            p1.id to p2.id
        }

        openProductPickerOnVender()
        scrollToPickerProduct(dragaoId)

        // Seleciona Dragão (1x = R$ 45,00) e incrementa para 2x (R$ 90,00)
        composeRule.onNodeWithTag("picker_checkbox_$dragaoId").performClick()
        composeRule.onNodeWithTag("picker_inc_$dragaoId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 90,00")

        // Pesquisa em minúsculas por "torre" -> Dragão fica oculto, mas sua seleção e subtotal permanecem
        composeRule.onNodeWithTag("product_picker_search").performTextInput("torre")
        waitUiUntil {
            composeRule.onAllNodesWithTag("picker_row_$dragaoId").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("picker_row_$torreId").assertIsDisplayed()
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 90,00")

        // Seleciona Torre (1x = R$ 30,00) durante o filtro -> subtotal passa para R$ 120,00
        composeRule.onNodeWithTag("picker_checkbox_$torreId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_counts").assertTextContains("2 produto(s) · 3 unidade(s)")
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 120,00")

        // Pesquisa sem resultados exibe estado vazio de pesquisa
        composeRule.onNodeWithTag("product_picker_clear_search").performClick()
        composeRule.onNodeWithTag("product_picker_search").performTextInput("Inexistente XYZ")
        waitUiUntil {
            composeRule.onAllNodesWithTag("picker_empty_state").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("picker_empty_state").assertTextContains("Nenhum produto encontrado.")

        // Limpa pesquisa -> ambos os produtos voltam visíveis com suas quantidades preservadas
        composeRule.onNodeWithTag("product_picker_clear_search").performClick()
        scrollToPickerProduct(dragaoId)
        composeRule.onNodeWithTag("picker_qty_$dragaoId", useUnmergedTree = true).assertTextEquals("2")
        scrollToPickerProduct(torreId)
        composeRule.onNodeWithTag("picker_qty_$torreId", useUnmergedTree = true).assertTextEquals("1")
        composeRule.onAllNodesWithText("%", substring = true).assertCountEquals(0)
    }

    @Test
    fun `03 independent quantity increment decrement removal and form sync`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val lia = app.catalogRepository.createPerson("Lia")
            val p1 = app.catalogRepository.createProduct(
                name = "Suporte Controle",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(lia.id, MoneyCents(2_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val p2 = app.catalogRepository.createProduct(
                name = "Chaveiro Lua",
                price = MoneyCents(1_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(lia.id, MoneyCents(1_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            p1.id to p2.id
        }

        openProductPickerOnVender()
        scrollToPickerProduct(prodAId)

        // Não selecionado mostra quantidade 0 e botão diminuir desabilitado sem selecionar a linha
        composeRule.onNodeWithTag("picker_qty_$prodAId", useUnmergedTree = true).assertTextEquals("0")
        composeRule.onNodeWithTag("picker_dec_$prodAId").assertIsNotEnabled()

        // Incremento direto pelo botão + seleciona com 1 e incrementa independentemente
        composeRule.onNodeWithTag("picker_inc_$prodAId").performClick()
        composeRule.onNodeWithTag("picker_inc_$prodAId").performClick()
        scrollToPickerProduct(prodBId)
        composeRule.onNodeWithTag("picker_checkbox_$prodBId").performClick()
        composeRule.waitForIdle()

        scrollToPickerProduct(prodAId)
        composeRule.onNodeWithTag("picker_qty_$prodAId", useUnmergedTree = true).assertTextEquals("2")
        composeRule.onNodeWithTag("picker_dec_$prodAId").assertIsEnabled()
        scrollToPickerProduct(prodBId)
        composeRule.onNodeWithTag("picker_qty_$prodBId", useUnmergedTree = true).assertTextEquals("1")
        composeRule.onNodeWithTag("product_picker_counts").assertTextContains("2 produto(s) · 3 unidade(s)")
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 55,00")

        // Decrementa prodA de 2 -> 1 e desmarca prodB pelo botão - (1 -> 0 / removido)
        scrollToPickerProduct(prodAId)
        composeRule.onNodeWithTag("picker_dec_$prodAId").performClick()
        scrollToPickerProduct(prodBId)
        composeRule.onNodeWithTag("picker_dec_$prodBId").performClick()
        composeRule.waitForIdle()

        scrollToPickerProduct(prodAId)
        composeRule.onNodeWithTag("picker_qty_$prodAId", useUnmergedTree = true).assertTextEquals("1")
        scrollToPickerProduct(prodBId)
        composeRule.onNodeWithTag("picker_qty_$prodBId", useUnmergedTree = true).assertTextEquals("0")
        composeRule.onNodeWithTag("product_picker_counts").assertTextContains("1 produto(s) · 1 unidade(s)")
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 20,00")

        // Conclui seleção e verifica atualização no formulário principal de Nova venda
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("selected_inc_$prodAId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("vender_selected_summary_bar").performScrollTo()
            .assertTextContains("R$ 20,00", substring = true)
    }

    @Test
    fun `04 historical products during edit stay identified with preserved prices`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (saleId, histProdId, activeProdId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val histProd = app.catalogRepository.createProduct(
                name = "Edição Especial Antiga",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val activeProd = app.catalogRepository.createProduct(
                name = "Produto Atual Catálogo",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val savedSale = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(histProd, 2)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            // Altera preço e desativa produto histórico no catálogo após a venda
            app.catalogRepository.updateProduct(
                productId = histProd.id,
                name = "Produto Renomeado",
                price = MoneyCents(9_900L),
                active = false,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(9_900L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            Triple(savedSale.id, histProd.id, activeProd.id)
        }

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

        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty()
        }

        scrollToPickerProduct(histProdId)
        composeRule.onNodeWithTag("picker_row_$histProdId").assertIsDisplayed()
        composeRule.onNodeWithTag("picker_historical_badge_$histProdId")
            .assertTextContains("Da venda (histórico preservado)")
        composeRule.onNodeWithTag("picker_qty_$histProdId", useUnmergedTree = true).assertTextEquals("2")
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 100,00")

        // Adiciona produto atual do catálogo junto ao histórico sem recalcular o preço histórico (100 + 25 = 125)
        scrollToPickerProduct(activeProdId)
        composeRule.onNodeWithTag("picker_checkbox_$activeProdId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_counts").assertTextContains("2 produto(s) · 3 unidade(s)")
        composeRule.onNodeWithTag("product_picker_subtotal").assertTextContains("R$ 125,00")
    }
}
