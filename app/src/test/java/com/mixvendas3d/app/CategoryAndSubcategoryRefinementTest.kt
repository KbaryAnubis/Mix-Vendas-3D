package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class CategoryAndSubcategoryRefinementTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun app(): MixVendasApplication = ApplicationProvider.getApplicationContext()

    @Before
    fun setUpCleanDb() {
        runBlocking(Dispatchers.IO) {
            app().database.clearAllTables()
        }
    }

    @After
    fun tearDownCleanDb() {
        runBlocking(Dispatchers.IO) {
            app().database.clearAllTables()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun navigateToProducts() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("mais_item_types_products")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")
    }

    @Test
    fun categoryManagementButtonsHaveNoPlusSymbolAndKeepCreateEditHierarchyWorking() {
        val application = app()
        val seededMain = runBlocking(Dispatchers.IO) {
            application.catalogRepository.createProductCategory("DECORAÇÃO 3D", null)
        }

        navigateToProducts()
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")

        // 1. Nenhum botão exibe o símbolo "+"
        composeRule.onAllNodesWithText("+ Nova categoria", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("+ Subcategoria", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("new_category_button").assert(hasText("Nova categoria"))

        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("add_subcategory_button_${seededMain.id}"))
        composeRule.onNodeWithTag("add_subcategory_button_${seededMain.id}")
            .assertIsDisplayed()
            .assert(hasText("Subcategoria"))

        // 2. Criar nova categoria principal continua funcionando
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("new_category_button"))
        composeRule.onNodeWithTag("new_category_button").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextInput("miniaturas")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        val createdMain = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeProductCategories().first()
                .first { it.parentId == null && it.name == "MINIATURAS" }
        }

        // 3. Criar subcategoria dentro da categoria criada
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("add_subcategory_button_${createdMain.id}"))
        composeRule.onNodeWithTag("add_subcategory_button_${createdMain.id}").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextInput("pokémon")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        val createdSub = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeProductCategories().first()
                .first { it.parentId == createdMain.id && it.name == "POKÉMON" }
        }
        assertEquals(createdMain.id, createdSub.parentId)

        // 4. Editar categoria principal e editar subcategoria preservando hierarquia
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("edit_category_${createdMain.id}"))
        composeRule.onNodeWithTag("edit_category_${createdMain.id}").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextReplacement("miniaturas 3d")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("edit_category_${createdSub.id}"))
        composeRule.onNodeWithTag("edit_category_${createdSub.id}").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextReplacement("pokémon kanto")
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        val finalCategories = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeProductCategories().first()
        }
        val editedMain = finalCategories.first { it.id == createdMain.id }
        val editedSub = finalCategories.first { it.id == createdSub.id }
        assertEquals("MINIATURAS 3D", editedMain.name)
        assertEquals(null, editedMain.parentId)
        assertEquals("POKÉMON KANTO", editedSub.name)
        assertEquals(createdMain.id, editedSub.parentId)
    }

    @Test
    fun subcategoryFieldStaysPermanentlyVisibleUpdatesOnCategorySwitchAndSavesProductHierarchy() {
        val application = app()
        val seeded = runBlocking(Dispatchers.IO) {
            val person = application.catalogRepository.createPerson("ANA")
            val catA = application.catalogRepository.createProductCategory("COLEÇÃO A", null)
            val subA1 = application.catalogRepository.createProductCategory("DRAGÕES", catA.id)
            val catB = application.catalogRepository.createProductCategory("COLEÇÃO B", null)
            val subB1 = application.catalogRepository.createProductCategory("VASOS", catB.id)
            val catEmpty = application.catalogRepository.createProductCategory("COLEÇÃO VAZIA", null)
            application.catalogRepository.createProduct(
                name = "DRAGÃO REAL",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = catA.id,
                subcategoryId = subA1.id,
            )
            SeededHierarchy(person.id, catA.id, subA1.id, catB.id, subB1.id, catEmpty.id)
        }

        navigateToProducts()

        // Na tela principal de Produtos, ambos os filtros (Categoria e Subcategoria) estão visíveis
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_category_filter"))
        composeRule.onNodeWithTag("products_category_filter").assertIsDisplayed()
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_subcategory_filter"))
        composeRule.onNodeWithTag("products_subcategory_filter")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(hasText("Selecione uma categoria primeiro"))

        // Selecionar Categoria A habilita o filtro de Subcategoria com DRAGÕES
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_category_filter"))
        composeRule.onNodeWithTag("products_category_filter").performClick()
        waitForTag("products_category_filter_option_${seeded.catAId}")
        composeRule.onNodeWithTag("products_category_filter_option_${seeded.catAId}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("products_subcategory_filter"))
        composeRule.onNodeWithTag("products_subcategory_filter")
            .assertIsDisplayed()
            .assertIsEnabled()

        // Abre Novo produto -> Categoria principal e Subcategoria (opcional) visíveis desde o início
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("new_product_button"))
        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")

        composeRule.onNodeWithTag("product_category_dropdown").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(hasText("Selecione uma categoria primeiro"))

        // Seleciona Categoria A -> Subcategoria habilita e exibe DRAGÕES (não VASOS)
        composeRule.onNodeWithTag("product_category_dropdown").performScrollTo().performClick()
        waitForTag("product_category_dropdown_option_${seeded.catAId}")
        composeRule.onNodeWithTag("product_category_dropdown_option_${seeded.catAId}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        waitForTag("product_subcategory_dropdown_option_${seeded.subA1Id}")
        composeRule.onAllNodesWithTag("product_subcategory_dropdown_option_${seeded.subB1Id}")
            .assertCountEquals(0)
        composeRule.onNodeWithTag("product_subcategory_dropdown_option_${seeded.subA1Id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_subcategory_dropdown").assert(hasText("DRAGÕES"))

        // Troca para Categoria Vazia -> Subcategoria continua visível e indica sem subcategorias
        composeRule.onNodeWithTag("product_category_dropdown").performScrollTo().performClick()
        waitForTag("product_category_dropdown_option_${seeded.catEmptyId}")
        composeRule.onNodeWithTag("product_category_dropdown_option_${seeded.catEmptyId}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(hasText("Sem subcategorias cadastradas"))

        // Troca para Categoria B -> atualiza opções para VASOS e limpa DRAGÕES
        composeRule.onNodeWithTag("product_category_dropdown").performScrollTo().performClick()
        waitForTag("product_category_dropdown_option_${seeded.catBId}")
        composeRule.onNodeWithTag("product_category_dropdown_option_${seeded.catBId}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsEnabled()
            .assert(hasText("Nenhuma"))
            .performClick()
        waitForTag("product_subcategory_dropdown_option_${seeded.subB1Id}")
        composeRule.onAllNodesWithTag("product_subcategory_dropdown_option_${seeded.subA1Id}")
            .assertCountEquals(0)
        composeRule.onNodeWithTag("product_subcategory_dropdown_option_${seeded.subB1Id}").performClick()
        composeRule.waitForIdle()

        // Salva produto e valida hierarquia salva no Room
        composeRule.onNodeWithTag("product_name_input").performScrollTo().performTextInput("vaso geométrico")
        composeRule.onNodeWithTag("product_price_input").performScrollTo().performTextInput("6500")
        composeRule.onNodeWithTag("product_participant_check_${seeded.personId}").performScrollTo().performClick()
        composeRule.onNodeWithTag("product_participant_amount_${seeded.personId}").performScrollTo().performTextInput("6500")
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        waitForTag("products_feedback_banner")

        val savedProduct = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeAllProducts().first()
                .first { it.name == "VASO GEOMÉTRICO" }
        }
        assertEquals(seeded.catBId, savedProduct.categoryId)
        assertEquals(seeded.subB1Id, savedProduct.subcategoryId)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w840dp-h1280dp-xhdpi")
    fun categoryAndSubcategoryFieldsRemainVisibleAndCleanOnLargeScreen() {
        val application = app()
        val (catId, subId) = runBlocking(Dispatchers.IO) {
            val cat = application.catalogRepository.createProductCategory("DECORAÇÃO", null)
            val sub = application.catalogRepository.createProductCategory("LUMINÁRIAS", cat.id)
            cat.id to sub.id
        }

        navigateToProducts()
        composeRule.onNodeWithTag("products_category_filter").assertIsDisplayed()
        composeRule.onNodeWithTag("products_subcategory_filter")
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")
        composeRule.onNodeWithTag("product_category_dropdown").assertIsDisplayed()
        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .assertIsDisplayed()
            .assertIsNotEnabled()

        composeRule.onNodeWithTag("product_category_dropdown").performClick()
        waitForTag("product_category_dropdown_option_$catId")
        composeRule.onNodeWithTag("product_category_dropdown_option_$catId").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_subcategory_dropdown")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        waitForTag("product_subcategory_dropdown_option_$subId")
        composeRule.onNodeWithTag("product_subcategory_dropdown_option_$subId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_subcategory_dropdown").assert(hasText("LUMINÁRIAS"))

        // Voltar e validar ausência de "+" em Categorias também em tela grande
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("screen_products")
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")
        composeRule.onAllNodesWithText("+ Nova categoria", substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("+ Subcategoria", substring = true).assertCountEquals(0)
        composeRule.onNodeWithTag("new_category_button").assertIsDisplayed().assert(hasText("Nova categoria"))
        assertTrue(
            composeRule.onAllNodesWithTag("add_subcategory_button_$catId").fetchSemanticsNodes().isNotEmpty(),
        )
    }

    private data class SeededHierarchy(
        val personId: String,
        val catAId: String,
        val subA1Id: String,
        val catBId: String,
        val subB1Id: String,
        val catEmptyId: String,
    )
}
