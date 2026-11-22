package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
class Wave2RegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdDbNames = mutableListOf<String>()

    @After
    fun cleanUp() {
        val app = context as MixVendasApplication
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
        createdDbNames.forEach { dbName ->
            runCatching { context.deleteDatabase(dbName) }
        }
        createdDbNames.clear()
    }

    private fun waitForTag(testTag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(testTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForTagGone(testTag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(testTag).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `01 navigation from Mais to all sub-destinations and back uses single topbar cleanly`() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("screen_mais")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)

        val targets = listOf(
            "mais_item_types_products" to "screen_products",
            "mais_item_names" to "screen_names",
            "mais_item_machines" to "screen_machines",
            "mais_item_import_export" to "screen_mais_detail_import_export",
            "mais_item_update_system" to "screen_mais_detail_update_system",
        )

        targets.forEach { (itemTag, screenTag) ->
            composeRule.onNodeWithTag(itemTag).performScrollTo().performClick()
            waitForTag(screenTag)
            composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)
            composeRule.onNodeWithTag("topbar_back_button").performClick()
            waitForTag("screen_mais")
            composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(0)
        }
    }

    @Test
    fun `02 products views never duplicate topbar_back_button, follow exact back hierarchy, and reset transient editors on tab exit`() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("screen_mais")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        // 1. Produtos -> Categorias -> Nova categoria -> Categorias -> Produtos -> Mais
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        composeRule.onNodeWithTag("new_category_button").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        // Voltar: Nova categoria -> Categorias
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("category_management_dialog")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        // Voltar: Categorias -> Produtos
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("screen_products")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        // 2. Produtos -> Novo produto -> Voltar -> Produtos
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("new_product_button"))
        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("screen_products")
        composeRule.onAllNodesWithTag("topbar_back_button").assertCountEquals(1)

        // 3. Abrir editor, sair da rota Produtos pela barra inferior e voltar -> lista limpa (sem editor obsoleto)
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("new_product_button"))
        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")

        composeRule.onNodeWithTag("nav_tab_inicio").performClick()
        waitForTag("screen_inicio")

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("screen_mais")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")
        composeRule.onAllNodesWithTag("product_editor_dialog").assertCountEquals(0)
        composeRule.onAllNodesWithTag("category_management_dialog").assertCountEquals(0)
        composeRule.onAllNodesWithTag("create_edit_category_dialog").assertCountEquals(0)
    }

    @Test
    fun `03 category creation, duplicate error preservation, edit hierarchy lock, and deleted filter recovery work cleanly`() {
        val app = context as MixVendasApplication
        val (catId, subId) = runBlocking(Dispatchers.IO) {
            val main = app.catalogRepository.createProductCategory("Impressão 3D")
            val sub = app.catalogRepository.createProductCategory("Miniaturas", parentId = main.id)
            main.id to sub.id
        }

        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("screen_mais")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")

        // Seleciona filtro pela categoria principal
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_category_filter"))
        composeRule.onNodeWithTag("products_category_filter").performClick()
        waitForTag("products_category_filter_option_$catId")
        composeRule.onNodeWithTag("products_category_filter_option_$catId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("products_category_filter")
            .assert(hasText("Impressão 3D", substring = true))

        // Abre gerenciamento de categorias
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")

        // 1. Tenta criar categoria duplicada -> mantém editor aberto, preserva texto digitado e mostra erro
        composeRule.onNodeWithTag("new_category_button").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextInput("Impressão 3D")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_form_error")
        composeRule.onNodeWithTag("create_edit_category_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("category_name_input").assert(hasText("Impressão 3D"))

        // 2. Corrige nome e cria nova categoria principal com sucesso -> fecha editor e exibe sucesso
        composeRule.onNodeWithTag("category_name_input").performScrollTo().performTextReplacement("Decoração")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")
        waitForTag("categories_feedback_banner")

        // 3. Editar subcategoria: tipo e categoria pai desabilitados + aviso de hierarquia + preserva parentId
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("edit_category_$subId"))
        composeRule.onNodeWithTag("edit_category_$subId").performScrollTo().performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("radio_category_main").assertIsNotEnabled()
        composeRule.onNodeWithTag("radio_category_sub").assertIsNotEnabled()
        composeRule.onNodeWithTag("category_edit_hierarchy_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("category_name_input").performScrollTo().performTextReplacement("Miniaturas RPG")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        val renamedSub = runBlocking(Dispatchers.IO) {
            app.catalogRepository.observeProductCategories().first().first { it.id == subId }
        }
        assertEquals("Miniaturas RPG", renamedSub.name)
        assertEquals(catId, renamedSub.parentId)

        // 4. Exclui a categoria principal que estava selecionada no filtro -> volta para Produtos com filtro resetado
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("delete_category_$catId"))
        composeRule.onNodeWithTag("delete_category_$catId").performScrollTo().performClick()
        waitForTag("confirm_delete_category_button")
        composeRule.onNodeWithTag("confirm_delete_category_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                app.catalogRepository.observeProductCategories().first().none { it.id == catId }
            }
        }
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("screen_products")
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_category_filter"))
        composeRule.onNodeWithTag("products_category_filter")
            .assert(hasText("Todas as categorias", substring = true))
    }

    @Test
    fun `04 vender allows clearing final value temporarily, blocks saving blank, and recalculates discount and quantity cleanly`() {
        val app = context as MixVendasApplication
        val (prodAId, prodBId) = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val prodA = app.catalogRepository.createProduct(
                name = "Chaveiro Dragão",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodB = app.catalogRepository.createProduct(
                name = "Suporte Controle",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(6_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            prodA.id to prodB.id
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitForTag("screen_vender")

        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitForTag("product_picker_dialog")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag("product_picker_list")
                    .performScrollToNode(hasTestTag("picker_row_$prodAId"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("picker_checkbox_$prodAId").performClick()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_$prodBId"))
        composeRule.onNodeWithTag("picker_checkbox_$prodBId").performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitForTagGone("product_picker_dialog")

        // Valor inicial sincronizado = 100,00
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().assert(hasText("100,00"))

        // 1. Apagar totalmente o valor final deve permanecer em branco (não restaura 100,00 sozinho)
        composeRule.onNodeWithTag("sale_final_value_input").performTextClearance()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sale_final_value_input").assert(hasText(""))

        // 2. Tentar registrar venda com valor final vazio deve bloquear e não salvar no Room
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        val countWhileBlank = runBlocking(Dispatchers.IO) {
            app.saleRepository.observeSales().first().size
        }
        assertEquals(0, countWhileBlank)

        // 3. Digitar novo valor com desconto (90,00) calcula desconto de R$ 10,00 e permite escolher alvo do desconto
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().performTextInput("90,00")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("discount_target_dropdown").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("discount_target_dropdown").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Aplicar somente em Suporte Controle").performClick()
        composeRule.waitForIdle()

        // 4. Alterar quantidade de um produto mantém recálculo coerente (preserva desconto de R$ 10,00)
        composeRule.onNodeWithTag("selected_inc_$prodAId").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Novo subtotal = 2*40 + 60 = 140,00; desconto de R$ 10,00 mantido => valor final = 130,00
        composeRule.onNodeWithTag("sale_final_value_input").performScrollTo().assert(hasText("130,00"))

        // 5. Registrar venda com sucesso
        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                app.saleRepository.observeSales().first().size == 1
            }
        }
        val saved = runBlocking(Dispatchers.IO) { app.saleRepository.observeSales().first().single() }
        assertEquals(14_000L, saved.calculation.originalSubtotal.rawCents)
        assertEquals(13_000L, saved.calculation.finalAmount.rawCents)
        assertEquals(1_000L, saved.calculation.discountTotal.rawCents)
    }

    @Test
    fun `05 product picker displays orphaned subcategory product under main category without duplication or DB mutation`() {
        val app = context as MixVendasApplication
        val seeded = runBlocking(Dispatchers.IO) {
            val ana = app.catalogRepository.createPerson("Ana")
            val mainA = app.catalogRepository.createProductCategory("Colecionáveis")
            val mainB = app.catalogRepository.createProductCategory("Acessórios")
            val subA = app.catalogRepository.createProductCategory("Action Figures", parentId = mainA.id)
            val subB = app.catalogRepository.createProductCategory("Chaveiros", parentId = mainB.id)

            val uncat = app.catalogRepository.createProduct(
                name = "Item Sem Categoria",
                price = MoneyCents(1_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(1_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val direct = app.catalogRepository.createProduct(
                name = "Busto Direto",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = mainA.id,
            )
            val validSub = app.catalogRepository.createProduct(
                name = "Guerreiro Subcategoria",
                price = MoneyCents(7_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(7_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = mainA.id,
                subcategoryId = subA.id,
            )

            // Produto com categoryId válida (mainA) mas subcategoryId inconsistente (pertence a mainB)
            val orphanId = "prod-orphan-sub-1"
            app.database.productDao().upsertProductWithParticipants(
                product = ProductEntity(
                    id = orphanId,
                    name = "Estátua Sub Órfã",
                    priceCents = 8_000L,
                    active = true,
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL.name,
                    feeOwnerPersonId = null,
                    categoryId = mainA.id,
                    subcategoryId = subB.id,
                    createdAtEpochMs = 1_765_000_000_000L,
                    updatedAtEpochMs = 1_765_000_000_000L,
                ),
                participants = listOf(
                    ProductParticipantEntity(
                        productId = orphanId,
                        personId = ana.id,
                        amountCents = 8_000L,
                        sortOrder = 0,
                    ),
                ),
                isNew = true,
            )
            listOf(mainA.id, subA.id, uncat.id, direct.id, validSub.id, orphanId, subB.id)
        }
        val mainAId = seeded[0]
        val subAId = seeded[1]
        val uncatId = seeded[2]
        val directId = seeded[3]
        val validSubId = seeded[4]
        val orphanId = seeded[5]
        val subBId = seeded[6]

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitForTag("screen_vender")
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitForTag("product_picker_dialog")
        composeRule.onNodeWithTag("product_picker_close_x_button").assertIsDisplayed()
        composeRule.onNodeWithTag("close_product_picker_button").assertIsDisplayed()

        // Expande "Sem categoria" -> encontra item sem categoria
        waitForTag("picker_category_header_uncategorized")
        composeRule.onNodeWithTag("picker_category_header_uncategorized").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_$uncatId"))
        composeRule.onAllNodesWithTag("picker_row_$uncatId").assertCountEquals(1)

        // Expande categoria principal Colecionáveis -> vê item direto E item órfão sem duplicação
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_category_header_$mainAId"))
        composeRule.onNodeWithTag("picker_category_header_$mainAId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_$directId"))
        composeRule.onAllNodesWithTag("picker_row_$directId").assertCountEquals(1)
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_$orphanId"))
        composeRule.onAllNodesWithTag("picker_row_$orphanId").assertCountEquals(1)

        // Expande subcategoria válida -> encontra produto da subcategoria
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_subcategory_header_$subAId"))
        composeRule.onNodeWithTag("picker_subcategory_header_$subAId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_$validSubId"))
        composeRule.onAllNodesWithTag("picker_row_$validSubId").assertCountEquals(1)

        // Busca normal encontra produto órfão sem duplicação
        composeRule.onNodeWithTag("product_picker_search").performTextInput("Órfã")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("picker_row_$orphanId").assertCountEquals(1)

        // Busca sem resultados exibe estado vazio
        composeRule.onNodeWithTag("product_picker_search").performTextReplacement("XYZ_INEXISTENTE_999")
        waitForTag("picker_empty_state")

        // Fecha diálogo pelo X
        composeRule.onNodeWithTag("product_picker_close_x_button").performClick()
        waitForTagGone("product_picker_dialog")

        // Confirma que o banco NÃO foi mutado silenciosamente
        val dbOrphan = runBlocking(Dispatchers.IO) {
            app.database.productDao().getWithParticipantsById(orphanId)
        }
        assertNotNull(dbOrphan)
        assertEquals(mainAId, dbOrphan!!.product.categoryId)
        assertEquals(subBId, dbOrphan.product.subcategoryId)
    }

    @Test
    fun `06 standard and custom brand debit and credit rates save, reload, calculate, display on card, and round-trip via JSON backup`() {
        val app = context as MixVendasApplication
        val ana = runBlocking(Dispatchers.IO) {
            app.catalogRepository.createPerson("Ana")
        }
        val product = runBlocking(Dispatchers.IO) {
            app.catalogRepository.createProduct(
                name = "Luminária 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
        }

        // Abre Maquininhas -> Novo editor
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("screen_mais")
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        waitForTag("screen_machines")
        composeRule.onNodeWithTag("new_machine_button").performClick()
        waitForTag("machine_editor_dialog")

        composeRule.onNodeWithTag("machine_name_input").performTextInput("Infinite Dual")
        composeRule.onNodeWithTag("machine_debit_rate_input").performTextInput("1,90")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_credit_default_rate_input"))
        composeRule.onNodeWithTag("machine_credit_default_rate_input").performTextInput("3,50")

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()

        // Visa: Débito 1,40% + Crédito 2,80%
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("standard_brand_debit_rate_VISA"))
        composeRule.onNodeWithTag("standard_brand_debit_rate_VISA").performTextInput("1,40")
        composeRule.onNodeWithTag("standard_brand_credit_rate_VISA").performTextInput("2,80")

        // Mastercard: somente Débito 1,55% (Crédito vazio -> usa padrão 3,50%)
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("standard_brand_debit_rate_MASTERCARD"))
        composeRule.onNodeWithTag("standard_brand_debit_rate_MASTERCARD").performTextInput("1,55")

        // Elo: somente Crédito 3,15% (Débito vazio -> usa padrão 1,90%)
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("standard_brand_credit_rate_ELO"))
        composeRule.onNodeWithTag("standard_brand_credit_rate_ELO").performTextInput("3,15")

        // Bandeira personalizada com Débito + Crédito
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_name_input"))
        composeRule.onNodeWithTag("custom_brand_name_input").performTextInput("Banese VIP")
        composeRule.onNodeWithTag("new_custom_brand_debit_rate_input").performTextInput("1,75")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performTextInput("4,10")
        composeRule.onNodeWithTag("add_custom_brand_button").performClick()
        composeRule.waitForIdle()

        // Salva maquininha
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()
        waitForTagGone("machine_editor_dialog")

        val savedMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.name == "Infinite Dual" }
        }
        val baneseKey = CustomBrandKeyCodec.encodeKey("Banese VIP")
        assertEquals(140, savedMachine.brandDebitRatesBps[CardBrand.VISA])
        assertEquals(280, savedMachine.brandRatesBps[CardBrand.VISA])
        assertEquals(155, savedMachine.brandDebitRatesBps[CardBrand.MASTERCARD])
        assertNull(savedMachine.brandRatesBps[CardBrand.MASTERCARD])
        assertNull(savedMachine.brandDebitRatesBps[CardBrand.ELO])
        assertEquals(315, savedMachine.brandRatesBps[CardBrand.ELO])
        assertNull(savedMachine.brandDebitRatesBps[CardBrand.HIPERCARD])
        assertNull(savedMachine.brandRatesBps[CardBrand.HIPERCARD])
        assertEquals(175, savedMachine.findCustomBrand(baneseKey)?.debitRateBps)
        assertEquals(410, savedMachine.findCustomBrand(baneseKey)?.creditRateBps)

        // Motor financeiro aplica taxas específicas e fallbacks corretamente
        val visaDebitCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = savedMachine,
                cardBrand = CardBrand.VISA,
            ),
        )
        assertEquals(140, visaDebitCalc.effectiveFeeRateBps)

        val masterCreditFallback = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = savedMachine,
                cardBrand = CardBrand.MASTERCARD,
            ),
        )
        assertEquals(350, masterCreditFallback.effectiveFeeRateBps)

        val eloDebitFallback = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = savedMachine,
                cardBrand = CardBrand.ELO,
            ),
        )
        assertEquals(190, eloDebitFallback.effectiveFeeRateBps)

        // Card expandido exibe Bandeiras (Débito) e Bandeiras (Crédito) separadamente
        composeRule.onNodeWithTag("toggle_machine_rates_${savedMachine.id}").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_card_debit_brands_${savedMachine.id}")
            .performScrollTo()
            .assert(hasText("Visa 1,40%", substring = true))
            .assert(hasText("Mastercard 1,55%", substring = true))
            .assert(hasText("Elo (padrão)", substring = true))
        composeRule.onNodeWithTag("machine_card_credit_brands_${savedMachine.id}")
            .performScrollTo()
            .assert(hasText("Visa 2,80%", substring = true))
            .assert(hasText("Mastercard (padrão)", substring = true))
            .assert(hasText("Elo 3,15%", substring = true))

        // Desativa crédito no card -> Bandeiras (Débito) continua visível ao expandir
        composeRule.onNodeWithTag("machine_credit_switch_${savedMachine.id}").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                !app.machineRepository.observeMachines().first().first { it.id == savedMachine.id }.creditEnabled
            }
        }
        composeRule.onNodeWithTag("machine_card_debit_brands_${savedMachine.id}")
            .performScrollTo()
            .assert(hasText("Visa 1,40%", substring = true))

        // Reativa crédito, reabre edição, altera apenas Débito do Visa e verifica preservação de Crédito do Visa e demais taxas
        composeRule.onNodeWithTag("machine_credit_switch_${savedMachine.id}").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) {
                app.machineRepository.observeMachines().first().first { it.id == savedMachine.id }.creditEnabled
            }
        }
        composeRule.onNodeWithTag("edit_machine_${savedMachine.id}").performScrollTo().performClick()
        waitForTag("machine_editor_dialog")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("standard_brand_debit_rate_VISA"))
        composeRule.onNodeWithTag("standard_brand_debit_rate_VISA").assert(hasText("1,40"))
        composeRule.onNodeWithTag("standard_brand_credit_rate_VISA").assert(hasText("2,80"))
        composeRule.onNodeWithTag("standard_brand_debit_rate_VISA").performTextReplacement("1,25")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()
        waitForTagGone("machine_editor_dialog")

        val editedMachine = runBlocking(Dispatchers.IO) {
            app.machineRepository.observeMachines().first().first { it.id == savedMachine.id }
        }
        assertEquals(125, editedMachine.brandDebitRatesBps[CardBrand.VISA])
        assertEquals(280, editedMachine.brandRatesBps[CardBrand.VISA])
        assertEquals(155, editedMachine.brandDebitRatesBps[CardBrand.MASTERCARD])
        assertEquals(315, editedMachine.brandRatesBps[CardBrand.ELO])

        // Persistência real em banco em arquivo (fechar/reabrir) + Round-trip completo de Backup JSON
        runBlocking(Dispatchers.IO) {
            val dbName = shortTestDatabaseName("wave2_std_brands")
            createdDbNames.add(dbName)
            context.deleteDatabase(dbName)
            val fileDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val repo = RoomMachineRepository(fileDb.machineDao())
            val created = repo.createMachine(
                MachineInputDraft(
                    name = "Stone File",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 320,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 280, CardBrand.ELO to 315),
                    brandDebitRatesBps = mapOf(CardBrand.VISA to 125, CardBrand.MASTERCARD to 155),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                    customBrandRates = listOf(
                        CustomCardBrandInput(name = "Banese VIP", creditRateBps = 410, debitRateBps = 175),
                    ),
                ),
            )
            fileDb.close()

            val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val restoreDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            try {
                val reopenedMachine = RoomMachineRepository(reopenedDb.machineDao())
                    .observeMachines().first().first { it.id == created.id }
                assertEquals(125, reopenedMachine.brandDebitRatesBps[CardBrand.VISA])
                assertEquals(280, reopenedMachine.brandRatesBps[CardBrand.VISA])
                assertEquals(155, reopenedMachine.brandDebitRatesBps[CardBrand.MASTERCARD])
                assertNull(reopenedMachine.brandRatesBps[CardBrand.MASTERCARD])
                assertEquals(315, reopenedMachine.brandRatesBps[CardBrand.ELO])
                assertNull(reopenedMachine.brandDebitRatesBps[CardBrand.ELO])

                // Exporta e restaura via JSON Backup (incluindo Mastercard débito-only com rateBps == null)
                val exported = JsonBackupExporter.export(reopenedDb, ZoneId.of("UTC"), 1_766_000_000_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                assertTrue("Backup JSON deveria ser válido: ${readResult.errors}", readResult.isValid)
                val plan = JsonBackupMergePlanner.plan(readResult, restoreDb)
                val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, restoreDb)
                assertTrue(validation.isReadyForNextPhase)
                val preview = JsonBackupMergePreviewBuilder.buildPreview(
                    readResult,
                    plan,
                    emptyMap(),
                    validation,
                    restoreDb,
                )
                val exec = JsonBackupMergeExecutor.execute(
                    readResult = readResult,
                    plan = plan,
                    decisionsByConflictKey = emptyMap(),
                    decisionValidation = validation,
                    previewResult = preview,
                    executionContract = preview.executionContract,
                    userConfirmed = true,
                    database = restoreDb,
                )
                assertTrue(exec.success)

                val restored = RoomMachineRepository(restoreDb.machineDao())
                    .observeMachines().first().single()
                assertEquals(125, restored.brandDebitRatesBps[CardBrand.VISA])
                assertEquals(280, restored.brandRatesBps[CardBrand.VISA])
                assertEquals(155, restored.brandDebitRatesBps[CardBrand.MASTERCARD])
                assertNull(restored.brandRatesBps[CardBrand.MASTERCARD])
                assertEquals(315, restored.brandRatesBps[CardBrand.ELO])
                assertEquals(175, restored.findCustomBrand(baneseKey)?.debitRateBps)
                assertEquals(410, restored.findCustomBrand(baneseKey)?.creditRateBps)
            } finally {
                reopenedDb.close()
                restoreDb.close()
            }
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
    fun `07 compact 360dp screen renders standard and custom brands cleanly with 48dp remove touch target`() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("screen_mais")
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        waitForTag("screen_machines")
        composeRule.onNodeWithTag("new_machine_button").performClick()
        waitForTag("machine_editor_dialog")

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()

        // Bandeira padrão com nome longo renderiza ambos os campos limpos em 360dp
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("standard_brand_row_AMEX"))
        composeRule.onNodeWithTag("standard_brand_debit_rate_AMEX").assertIsDisplayed()
        composeRule.onNodeWithTag("standard_brand_credit_rate_AMEX").assertIsDisplayed()

        // Adiciona bandeira personalizada e valida alvo de toque >= 48dp no botão de remover
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_name_input"))
        composeRule.onNodeWithTag("custom_brand_name_input").performTextInput("Alelo Refeição Compacta")
        composeRule.onNodeWithTag("new_custom_brand_debit_rate_input").performTextInput("1,80")
        composeRule.onNodeWithTag("new_custom_brand_credit_rate_input").performTextInput("3,90")
        composeRule.onNodeWithTag("add_custom_brand_button").performClick()
        composeRule.waitForIdle()

        val aleloKey = CustomBrandKeyCodec.encodeKey("Alelo Refeição Compacta")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("remove_custom_brand_$aleloKey"))
        composeRule.onNodeWithTag("remove_custom_brand_$aleloKey")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("custom_brand_debit_rate_$aleloKey").assertIsDisplayed()
        composeRule.onNodeWithTag("custom_brand_credit_rate_$aleloKey").assertIsDisplayed()
    }
}
