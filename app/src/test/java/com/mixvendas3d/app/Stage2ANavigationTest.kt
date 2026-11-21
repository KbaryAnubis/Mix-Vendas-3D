package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage2ANavigationTest {

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
    fun `bottom navigation switches across all 5 tabs and keeps vendas centered without special highlight when unselected`() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("castle_wallpaper_image").assertExists()
        composeRule.onNodeWithTag("screen_inicio").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_inicio").assertIsSelected()
        composeRule.onNodeWithTag("nav_tab_vender").assertIsNotSelected()
        composeRule.onNodeWithTag("nav_tab_vendas").assertIsNotSelected()
        composeRule.onNodeWithTag("nav_tab_fechamento").assertIsNotSelected()
        composeRule.onNodeWithTag("nav_tab_mais").assertIsNotSelected()

        val venderBounds = composeRule.onNodeWithTag("nav_tab_vender").fetchSemanticsNode().boundsInRoot
        val vendasBounds = composeRule.onNodeWithTag("nav_tab_vendas").fetchSemanticsNode().boundsInRoot
        val fechamentoBounds = composeRule.onNodeWithTag("nav_tab_fechamento").fetchSemanticsNode().boundsInRoot
        assertTrue(venderBounds.right <= vendasBounds.left + 8f)
        assertTrue(vendasBounds.right <= fechamentoBounds.left + 8f)

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_vender").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_vender").assertIsSelected()

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_vendas").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_vendas").assertIsSelected()

        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_fechamento").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_fechamento").assertIsSelected()

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_mais").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_mais").assertIsSelected()
    }

    @Test
    fun `inicio hides visao geral and app name, shows 4 real kpis, two people initially, expands with ver mais, and opens nova venda`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val peopleIds = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Pessoa 1")
            val p2 = app.catalogRepository.createPerson("Pessoa 2")
            val p3 = app.catalogRepository.createPerson("Pessoa 3")
            val p4 = app.catalogRepository.createPerson("Pessoa 4")
            val prod = app.catalogRepository.createProduct(
                name = "Produto Teste Início",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(1_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(1_000L)),
                    ProductParticipantDraft(p3.id, MoneyCents(1_000L)),
                    ProductParticipantDraft(p4.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            listOf(p1.id, p2.id, p3.id, p4.id)
        }

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag("screen_inicio")
                    .performScrollToNode(hasTestTag("home_person_card_${peopleIds[0]}"))
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("home_header_title"))
        composeRule.onNodeWithTag("home_header_title").assertIsDisplayed()
        composeRule.onAllNodesWithText("Visão geral").assertCountEquals(0)
        composeRule.onAllNodesWithText("MIX VENDAS 3D").assertCountEquals(0)
        composeRule.onAllNodesWithText("Resumo do período", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("V19", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("home_new_sale_button").assertIsDisplayed()

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("home_metric_total_sold"))
        composeRule.onNodeWithTag("home_metric_total_sold").assertExists()
        composeRule.onNodeWithTag("home_metric_fees").assertExists()

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("home_metric_total_net"))
        composeRule.onNodeWithTag("home_metric_total_net").assertExists()
        composeRule.onNodeWithTag("home_metric_sales_count").assertExists()

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("home_person_card_${peopleIds[0]}"))
        composeRule.onNodeWithTag("home_people_sales_card").assertExists()
        composeRule.onNodeWithTag("home_person_card_${peopleIds[0]}").assertExists()
        composeRule.onNodeWithTag("home_person_card_${peopleIds[1]}").assertExists()
        composeRule.onAllNodesWithTag("home_person_card_${peopleIds[2]}").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_person_card_${peopleIds[3]}").assertCountEquals(0)

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("toggle_more_people_button"))
        composeRule.onNodeWithTag("toggle_more_people_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("home_person_card_${peopleIds[2]}"))
        composeRule.onNodeWithTag("home_person_card_${peopleIds[2]}").assertExists()
        composeRule.onNodeWithTag("home_person_card_${peopleIds[3]}").assertExists()

        composeRule.onNodeWithTag("screen_inicio")
            .performScrollToNode(hasTestTag("home_new_sale_button"))
        composeRule.onNodeWithTag("home_new_sale_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_vender").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_tab_vender").assertIsSelected()
    }

    @Test
    fun `vender supports searchable multi product picker with real Room products, machine fields, active save button, and omits history or preview`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (createdProductId, createdMachineId) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Ana")
            val p2 = app.catalogRepository.createPerson("Bruno")
            val prodId = app.catalogRepository.createProduct(
                name = "Miniatura Dragão",
                price = MoneyCents(2_200L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(1_700L)),
                    ProductParticipantDraft(p2.id, MoneyCents(500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            ).id
            val machineId = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "Ton Pro Real",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 280,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 250),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 110, 3 to 160),
                ),
            ).id
            prodId to machineId
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()

        // Garante ausência de histórico e prévia financeira na tela Vender
        composeRule.onAllNodesWithTag("sale_history_card_demo-1").assertCountEquals(0)
        composeRule.onAllNodesWithText("Divisão automática por produto").assertCountEquals(0)

        composeRule.onNodeWithTag("open_product_picker_button")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_dialog").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("picker_checkbox_$createdProductId")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("product_picker_search").performTextInput("Miniatura")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_checkbox_$createdProductId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_inc_$createdProductId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_qty_$createdProductId", useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pay_method_credit")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("machine_extras_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("machine_selector_dropdown").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_picker_dialog").assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 6_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("machine_picker_row_$createdMachineId")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("machine_picker_row_$createdMachineId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("brand_selector_dropdown").assertExists()
        composeRule.onNodeWithTag("installments_selector_dropdown").assertExists()

        composeRule.onNodeWithTag("save_sale_button")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun `vendas displays totals for all people, auto filters, detailed cards, and pending edit delete`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (peopleIds, sale1Id, sale2Id) = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Pessoa 1")
            val p2 = app.catalogRepository.createPerson("Pessoa 2")
            val p3 = app.catalogRepository.createPerson("Pessoa 3")
            val p4 = app.catalogRepository.createPerson("Pessoa 4")

            val prodSuporte = app.catalogRepository.createProduct(
                name = "Suporte Headset 3D",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(2_500L)),
                    ProductParticipantDraft(p2.id, MoneyCents(1_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodOrganizador = app.catalogRepository.createProduct(
                name = "Organizador Cabos",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p3.id, MoneyCents(1_800L)),
                    ProductParticipantDraft(p4.id, MoneyCents(1_200L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val saved1 = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodSuporte, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            val saved2 = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodOrganizador, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                ),
            )
            Triple(listOf(p1.id, p2.id, p3.id, p4.id), saved1.id, saved2.id)
        }

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("sales_person_net_${peopleIds[0]}")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("sales_top_summary_card").assertExists()
        peopleIds.forEach { pid ->
            composeRule.onNodeWithTag("sales_person_net_$pid").assertExists()
        }

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_$sale1Id"))
        composeRule.onNodeWithTag("sale_history_card_$sale1Id").assertExists()

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_$sale2Id"))
        composeRule.onNodeWithTag("sale_history_card_$sale2Id").assertExists()

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sales_filters_card"))
        composeRule.onNodeWithTag("sales_search_input")
            .performScrollTo()
            .performTextInput("Suporte")
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag("sale_history_card_$sale2Id").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithTag("screen_vendas")
            .performScrollToNode(hasTestTag("sale_history_card_$sale1Id"))
        composeRule.onNodeWithTag("sale_history_card_$sale1Id").assertExists()

        composeRule.onNodeWithTag("sale_edit_$sale1Id")
            .performScrollTo()
            .assertIsEnabled()
        composeRule.onNodeWithTag("sale_delete_$sale1Id")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun `fechamento shows summary, individual breakdown, payments, machine details, and pending csv button`() {
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("hero_summary_card").assertExists()
        composeRule.onNodeWithTag("closing_individual_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("closing_payments_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("closing_machines_card").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("export_csv_button")
            .performScrollTo()
            .assertIsEnabled()
    }

    @Test
    fun `mais lists five V2 cards in exact order without daily sales or room status and navigates across subscreens with back button`() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mais_header_title").assertIsDisplayed()
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("mais_item_daily_sales").assertCountEquals(0)
        composeRule.onAllNodesWithTag("mais_room_status").assertCountEquals(0)
        composeRule.onAllNodesWithTag("mais_item_vendas").assertCountEquals(0)
        composeRule.onAllNodesWithTag("mais_item_fechamento").assertCountEquals(0)
        composeRule.onAllNodesWithText("Vendas por Dia", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("SISTEMA E CADASTROS", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("MIX VENDAS 3D", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("100% offline", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Configurações", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onAllNodesWithText("Cadastrar e gerenciar produtos").assertCountEquals(1)
        composeRule.onAllNodesWithText("Cadastrar os participantes das vendas").assertCountEquals(1)
        composeRule.onAllNodesWithText("Gerenciar bandeiras e tarifas").assertCountEquals(1)
        composeRule.onAllNodesWithText("Backup JSON, restauração e CSV").assertCountEquals(1)
        composeRule.onAllNodesWithText("Revisar dados internos das vendas").assertCountEquals(1)

        val productsTop = composeRule.onNodeWithTag("mais_item_types_products")
            .fetchSemanticsNode().positionInRoot.y
        val namesTop = composeRule.onNodeWithTag("mais_item_names")
            .fetchSemanticsNode().positionInRoot.y
        val machinesTop = composeRule.onNodeWithTag("mais_item_machines")
            .fetchSemanticsNode().positionInRoot.y
        val importExportTop = composeRule.onNodeWithTag("mais_item_import_export")
            .fetchSemanticsNode().positionInRoot.y
        val updateSystemTop = composeRule.onNodeWithTag("mais_item_update_system")
            .fetchSemanticsNode().positionInRoot.y

        assertTrue("Expected Produtos ($productsTop) before Nomes ($namesTop)", productsTop < namesTop)
        assertTrue("Expected Nomes ($namesTop) before Maquininhas ($machinesTop)", namesTop < machinesTop)
        assertTrue("Expected Maquininhas ($machinesTop) before Importar/exportar ($importExportTop)", machinesTop < importExportTop)
        assertTrue("Expected Importar/exportar ($importExportTop) before Atualizar sistema ($updateSystemTop)", importExportTop < updateSystemTop)

        val destinationAssertions = listOf(
            "mais_item_types_products" to "screen_products",
            "mais_item_names" to "screen_names",
            "mais_item_machines" to "screen_machines",
            "mais_item_import_export" to "screen_mais_detail_import_export",
            "mais_item_update_system" to "screen_mais_detail_update_system",
        )

        destinationAssertions.forEach { (cardTag, screenTag) ->
            composeRule.onNodeWithTag(cardTag)
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(screenTag).assertIsDisplayed()
            composeRule.onNodeWithTag("topbar_back_button")
                .assertIsDisplayed()
                .performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("screen_mais").assertIsDisplayed()
            composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)
        }
    }

    @Test
    fun `mais shows Produtos label and navigates to restored internal update system screen without apk update text`() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("mais_item_types_products")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Tipos / produtos", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Tipos e produtos", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag("mais_item_types_products").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_products").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tipos / produtos", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("Tipos e produtos", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_mais").assertIsDisplayed()

        composeRule.onAllNodesWithText("próximas etapas", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag("mais_item_update_system")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_mais_detail_update_system").assertIsDisplayed()
        composeRule.onNodeWithTag("update_system_header_card").assertExists()
        composeRule.onNodeWithTag("update_system_analyze_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithTag("update_system_backup_button").assertIsDisplayed().assertIsEnabled()
        composeRule.onAllNodesWithText("Etapa 7", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("próximas etapas", substring = true, ignoreCase = true)
            .assertCountEquals(0)
        composeRule.onAllNodesWithText("baixar APK", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        composeRule.onNodeWithTag("topbar_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_mais").assertIsDisplayed()
    }
}
