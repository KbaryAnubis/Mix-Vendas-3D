package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.CustomCardBrandInput
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.ProductPriceSplitCalculator
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SaleItemParticipantSnapshot
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.ui.components.mapSelectionToUppercase
import com.mixvendas3d.app.ui.screens.CatalogUiState
import com.mixvendas3d.app.ui.screens.CatalogViewModel
import com.mixvendas3d.app.ui.screens.MachinesViewModel
import com.mixvendas3d.app.ui.screens.ProductsSectionUiState
import com.mixvendas3d.app.util.TextNormalization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class EightGroupsAdjustmentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun app(): MixVendasApplication = ApplicationProvider.getApplicationContext()

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTagGone(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
    }

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

    private fun navigateToProducts() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("mais_item_types_products")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")
    }

    private fun navigateToNames() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("mais_item_names")
        composeRule.onNodeWithTag("mais_item_names").performScrollTo().performClick()
        waitForTag("screen_names")
    }

    private fun navigateToMachines() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("mais_item_machines")
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        waitForTag("screen_machines")
    }

    // -------------------------------------------------------------------------
    // GRUPO 1 (ETAPA 2) + GRUPO 4 (ETAPA 5):
    // Rolagem completa com 40 subcategorias + ícones oficiais de editar/excluir
    // -------------------------------------------------------------------------

    @Test
    fun group1And4_manySubcategoriesAreFullyScrollableAndUseOfficialActionIconsOnCompactScreen() {
        val application = app()
        val (mainId, lastSubId) = runBlocking(Dispatchers.IO) {
            val main = application.catalogRepository.createProductCategory("COLEÇÃO 3D", null)
            var lastId = ""
            for (i in 1..40) {
                val padded = i.toString().padStart(2, '0')
                val sub = application.catalogRepository.createProductCategory("SUBCATEGORIA $padded", main.id)
                lastId = sub.id
            }
            main.id to lastId
        }

        navigateToProducts()
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")

        // Verifica que os ícones de ação da categoria principal NÃO usam emojis quebrados e possuem >= 48dp
        composeRule.onAllNodesWithText("✏️").assertCountEquals(0)
        composeRule.onAllNodesWithText("🗑️").assertCountEquals(0)

        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("edit_category_$mainId"))
        composeRule.onNodeWithTag("edit_category_$mainId")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("delete_category_$mainId")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("icon_edit_category_$mainId", useUnmergedTree = true)
            .assert(hasContentDescription("Editar COLEÇÃO 3D"))
        composeRule.onNodeWithTag("icon_delete_category_$mainId", useUnmergedTree = true)
            .assert(hasContentDescription("Excluir COLEÇÃO 3D"))

        // Rola até a 40ª subcategoria (última da lista) e valida visibilidade + ícones + botão "+ Subcategoria"
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("subcategory_item_$lastSubId"))
        composeRule.onNodeWithTag("subcategory_item_$lastSubId").assertIsDisplayed()
        composeRule.onNodeWithTag("edit_category_$lastSubId")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("delete_category_$lastSubId")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        // Rola até o botão "+ Subcategoria" logo abaixo da 40ª subcategoria
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("add_subcategory_button_$mainId"))
        composeRule.onNodeWithTag("add_subcategory_button_$mainId").assertIsDisplayed()

        // Clicar em Editar na última subcategoria abre o editor com o nome correto
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("edit_category_$lastSubId"))
        composeRule.onNodeWithTag("edit_category_$lastSubId").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").assert(hasText("SUBCATEGORIA 40"))
    }

    // -------------------------------------------------------------------------
    // GRUPO 2 (ETAPA 3):
    // MAIÚSCULAS automáticas em todos os nomes de cadastro (e NÃO em observação)
    // -------------------------------------------------------------------------

    @Test
    fun group2_uppercaseNormalizationPreservesAccentsCedillaNumbersSymbolsAndCursor() {
        assertEquals(
            "AÇÃO & DECORAÇÃO 3D — Nº 42 (Ç/Ã/É/Ó)",
            TextNormalization.toRegistrationUppercase("ação & decoração 3d — nº 42 (ç/ã/é/ó)"),
        )
        assertEquals(
            "acao & decoracao 3d",
            TextNormalization.normalizeForSearch("  AÇÃO & Decoração 3D  "),
        )
        // Preserva posição do cursor no meio da string
        val mapped = mapSelectionToUppercase(
            originalText = "vaso de aço",
            upperText = "VASO DE AÇO",
            selection = TextRange(5, 5),
        )
        assertEquals(TextRange(5, 5), mapped)
    }

    @Test
    fun group2_registrationFieldsConvertToUppercaseInRealTimeAndPersistWhileSaleNoteStaysMixedCase() {
        val application = app()

        // 1. Pessoa (criação e edição)
        navigateToNames()
        composeRule.onNodeWithTag("person_name_input").performTextInput("joão ação 12")
        composeRule.onNodeWithTag("person_name_input").assert(hasText("JOÃO AÇÃO 12"))
        composeRule.onNodeWithTag("save_person_button").performClick()
        waitForTag("names_feedback_banner")

        val savedPerson = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observePeople().first().first()
        }
        assertEquals("JOÃO AÇÃO 12", savedPerson.name)

        // 2. Categoria e Subcategoria
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("mais_item_types_products")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")

        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")

        composeRule.onNodeWithTag("new_category_button").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextInput("decoração 3d")
        composeRule.onNodeWithTag("category_name_input").assert(hasText("DECORAÇÃO 3D"))
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        val savedMainCat = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeProductCategories().first().first { it.parentId == null }
        }
        assertEquals("DECORAÇÃO 3D", savedMainCat.name)

        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("add_subcategory_button_${savedMainCat.id}"))
        composeRule.onNodeWithTag("add_subcategory_button_${savedMainCat.id}").performClick()
        waitForTag("create_edit_category_dialog")
        composeRule.onNodeWithTag("category_name_input").performTextInput("peças Pokémon #1")
        composeRule.onNodeWithTag("category_name_input").assert(hasText("PEÇAS POKÉMON #1"))
        composeRule.onNodeWithTag("save_category_button").performScrollTo().performClick()
        waitForTag("category_management_dialog")

        val savedSubCat = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeProductCategories().first().first { it.parentId == savedMainCat.id }
        }
        assertEquals("PEÇAS POKÉMON #1", savedSubCat.name)

        // 3. Produto
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("screen_products")
        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")
        composeRule.onNodeWithTag("product_name_input").performTextInput("dragão articulado ç-3d")
        composeRule.onNodeWithTag("product_name_input").assert(hasText("DRAGÃO ARTICULADO Ç-3D"))
        composeRule.onNodeWithTag("product_price_input").performTextInput("5000")
        composeRule.onNodeWithTag("product_participant_check_${savedPerson.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("product_participant_amount_${savedPerson.id}").performScrollTo().performTextInput("5000")
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        waitForTag("products_feedback_banner")

        val savedProduct = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeAllProducts().first().first()
        }
        assertEquals("DRAGÃO ARTICULADO Ç-3D", savedProduct.name)

        // 4. Maquininha + Bandeira personalizada
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("mais_item_machines")
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        waitForTag("screen_machines")
        composeRule.onNodeWithTag("new_machine_button").performClick()
        waitForTag("machine_editor_dialog")
        composeRule.onNodeWithTag("machine_name_input").performTextInput("stone loja #2")
        composeRule.onNodeWithTag("machine_name_input").assert(hasText("STONE LOJA #2"))
        composeRule.onNodeWithTag("machine_debit_rate_input").performTextInput("1,50")
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("machine_credit_default_rate_input"))
        composeRule.onNodeWithTag("machine_credit_default_rate_input").performTextInput("3,20")

        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("editor_brand_rates_switch"))
        composeRule.onNodeWithTag("editor_brand_rates_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("custom_brand_name_input"))
        composeRule.onNodeWithTag("custom_brand_name_input").performTextInput("crédz coração")
        composeRule.onNodeWithTag("custom_brand_name_input").assert(hasText("CRÉDZ CORAÇÃO"))
        composeRule.onNodeWithTag("new_custom_brand_debit_rate_input").performTextInput("1,90")
        composeRule.onNodeWithTag("add_custom_brand_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("machine_editor_scroll")
            .performScrollToNode(hasTestTag("save_machine_button"))
        composeRule.onNodeWithTag("save_machine_button").performClick()
        waitForTag("machines_feedback_banner")

        val savedMachine = runBlocking(Dispatchers.IO) {
            application.machineRepository.observeMachines().first().first()
        }
        assertEquals("STONE LOJA #2", savedMachine.name)
        assertEquals("CRÉDZ CORAÇÃO", savedMachine.customBrandRates.first().name)

        // 5. Observação da venda NÃO deve ser forçada para maiúsculas
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitForTag("screen_vender")
        composeRule.onNodeWithTag("sale_note_input").performScrollTo().performTextInput("Cliente pediu embalagem especial")
        composeRule.onNodeWithTag("sale_note_input").assert(hasText("Cliente pediu embalagem especial"))
    }

    // -------------------------------------------------------------------------
    // GRUPO 3 (ETAPA 4.1 e 4.2):
    // Categoria/Subcategoria não reabre foco do preço + Divisão bidirecional 2 pessoas
    // -------------------------------------------------------------------------

    @Test
    fun group3_bidirectionalTwoPersonSplitCalculatorCoversAllCasesWithoutLoops() {
        val order = listOf("p1", "p2")

        // 1. P1 preenchida -> calcula e escreve P2 diretamente
        val r1 = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "100,00",
            selectedParticipantOrder = order,
            currentInputs = mapOf("p1" to "", "p2" to ""),
            editedPersonId = "p1",
            rawInput = "3000",
        )
        assertEquals("30,00", r1.updatedInputs["p1"])
        assertEquals("70,00", r1.updatedInputs["p2"])

        // 2. P2 preenchida primeiro -> calcula e escreve P1 diretamente
        val r2 = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "100,00",
            selectedParticipantOrder = order,
            currentInputs = mapOf("p1" to "", "p2" to ""),
            editedPersonId = "p2",
            rawInput = "6000",
        )
        assertEquals("40,00", r2.updatedInputs["p1"])
        assertEquals("60,00", r2.updatedInputs["p2"])

        // 3. Edição manual posterior em P1 recalcula P2, e edição em P2 recalcula P1
        val r3 = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "100,00",
            selectedParticipantOrder = order,
            currentInputs = r2.updatedInputs,
            editedPersonId = "p1",
            rawInput = "2550",
        )
        assertEquals("25,50", r3.updatedInputs["p1"])
        assertEquals("74,50", r3.updatedInputs["p2"])

        val r4 = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "100,00",
            selectedParticipantOrder = order,
            currentInputs = r3.updatedInputs,
            editedPersonId = "p2",
            rawInput = "8025",
        )
        assertEquals("19,75", r4.updatedInputs["p1"])
        assertEquals("80,25", r4.updatedInputs["p2"])

        // 4. Valor igual ao total -> complemento exatamente "0,00"
        val rFull = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "100,00",
            selectedParticipantOrder = order,
            currentInputs = r4.updatedInputs,
            editedPersonId = "p1",
            rawInput = "10000",
        )
        assertEquals("100,00", rFull.updatedInputs["p1"])
        assertEquals("0,00", rFull.updatedInputs["p2"])

        // 5. Valor maior que total -> não gera negativo e não corrompe P2
        val rExcess = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "100,00",
            selectedParticipantOrder = order,
            currentInputs = mapOf("p1" to "30,00", "p2" to "70,00"),
            editedPersonId = "p1",
            rawInput = "12000",
        )
        assertEquals("120,00", rExcess.updatedInputs["p1"])
        assertEquals("70,00", rExcess.updatedInputs["p2"])

        // 6. Preço total alterado depois -> recalcula complemento preservando âncora
        val afterPriceChange = ProductPriceSplitCalculator.onTotalPriceChanged(
            newPriceInput = "150,00",
            selectedParticipantOrder = order,
            currentInputs = mapOf("p1" to "30,00", "p2" to "70,00"),
            lastEditedParticipantId = "p1",
        )
        assertEquals("30,00", afterPriceChange["p1"])
        assertEquals("120,00", afterPriceChange["p2"])

        // 7. Preço total zerado/vazio -> não gera negativos nem crash
        val rZeroPrice = ProductPriceSplitCalculator.onParticipantAmountEdited(
            priceInput = "",
            selectedParticipantOrder = order,
            currentInputs = mapOf("p1" to "", "p2" to ""),
            editedPersonId = "p1",
            rawInput = "1500",
        )
        assertEquals("15,00", rZeroPrice.updatedInputs["p1"])
        assertEquals("", rZeroPrice.updatedInputs["p2"])
    }

    @Test
    fun group3_productEditorCategorySelectionDoesNotRefocusPriceAndBidirectionalSplitPersistsInRoom() {
        val application = app()
        val (p1, p2, mainCat, subCat) = runBlocking(Dispatchers.IO) {
            val person1 = application.catalogRepository.createPerson("ANA")
            val person2 = application.catalogRepository.createPerson("BRUNO")
            val cat = application.catalogRepository.createProductCategory("MINIATURAS", null)
            val sub = application.catalogRepository.createProductCategory("DRAGÕES", cat.id)
            Quad(person1, person2, cat, sub)
        }

        navigateToProducts()
        composeRule.onNodeWithTag("new_product_button").performClick()
        waitForTag("product_editor_dialog")

        composeRule.onNodeWithTag("product_name_input").performTextInput("dragão real")
        composeRule.onNodeWithTag("product_price_input").performTextInput("10000")
        composeRule.onNodeWithTag("product_price_input").assert(hasText("100,00"))

        // Seleciona Categoria e Subcategoria: preço NÃO deve reabrir foco e deve manter 100,00 intacto
        composeRule.onNodeWithTag("product_category_dropdown").performClick()
        waitForTag("product_category_dropdown_option_${mainCat.id}")
        composeRule.onNodeWithTag("product_category_dropdown_option_${mainCat.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_price_input").assertIsNotFocused()
        composeRule.onNodeWithTag("product_price_input").assert(hasText("100,00"))

        composeRule.onNodeWithTag("product_subcategory_dropdown").performClick()
        waitForTag("product_subcategory_dropdown_option_${subCat.id}")
        composeRule.onNodeWithTag("product_subcategory_dropdown_option_${subCat.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_price_input").assertIsNotFocused()
        composeRule.onNodeWithTag("product_price_input").assert(hasText("100,00"))

        // Seleciona 2 participantes e testa divisão bidirecional real na UI
        composeRule.onNodeWithTag("product_participant_check_${p1.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("product_participant_check_${p2.id}").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Preenche Pessoa 1 com 30,00 -> Pessoa 2 recebe 70,00 diretamente no campo
        composeRule.onNodeWithTag("product_participant_amount_${p1.id}").performScrollTo().performTextInput("3000")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_${p1.id}").assert(hasText("30,00"))
        composeRule.onNodeWithTag("product_participant_amount_${p2.id}").assert(hasText("70,00"))

        // Edita Pessoa 2 para 60,00 -> Pessoa 1 recalcula para 40,00 diretamente no campo
        composeRule.onNodeWithTag("product_participant_amount_${p2.id}").performScrollTo().performTextReplacement("6000")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_participant_amount_${p2.id}").assert(hasText("60,00"))
        composeRule.onNodeWithTag("product_participant_amount_${p1.id}").assert(hasText("40,00"))

        // Trocar categoria novamente preserva preço e valores individuais preenchidos
        composeRule.onNodeWithTag("product_category_dropdown").performScrollTo().performClick()
        waitForTag("product_category_dropdown_option_${mainCat.id}")
        composeRule.onNodeWithTag("product_category_dropdown_option_${mainCat.id}").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_price_input").assert(hasText("100,00"))
        composeRule.onNodeWithTag("product_participant_amount_${p1.id}").assert(hasText("40,00"))
        composeRule.onNodeWithTag("product_participant_amount_${p2.id}").assert(hasText("60,00"))

        // Salva no Room e confirma centavos exatos
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        waitForTag("products_feedback_banner")

        val saved = runBlocking(Dispatchers.IO) {
            application.catalogRepository.observeAllProducts().first().first()
        }
        assertEquals("DRAGÃO REAL", saved.name)
        assertEquals(10_000L, saved.price.rawCents)
        assertEquals(4_000L, saved.participants.first { it.personId == p1.id }.amount.rawCents)
        assertEquals(6_000L, saved.participants.first { it.personId == p2.id }.amount.rawCents)
    }

    // -------------------------------------------------------------------------
    // GRUPO 5 (ETAPA 6):
    // Pesquisa de produtos oculta subcategorias vazias e restaura ao limpar
    // -------------------------------------------------------------------------

    @Test
    fun group5_productPickerSearchShowsOnlyMatchingSubcategoriesSupportsAccentsAndRestoresOnClear() {
        val application = app()
        val ids = runBlocking(Dispatchers.IO) {
            val person = application.catalogRepository.createPerson("CARLA")
            val main = application.catalogRepository.createProductCategory("COLEÇÃO 3D", null)
            val subPokemon = application.catalogRepository.createProductCategory("POKÉMON", main.id)
            val subDragoes = application.catalogRepository.createProductCategory("DRAGÕES", main.id)
            val subVazia1 = application.catalogRepository.createProductCategory("ANIMAIS", main.id)
            val subVazia2 = application.catalogRepository.createProductCategory("VEÍCULOS", main.id)

            val prodPika = application.catalogRepository.createProduct(
                name = "PIKACHU DOURADO",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(4_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = main.id,
                subcategoryId = subPokemon.id,
            )
            val prodDragao = application.catalogRepository.createProduct(
                name = "DRAGÃO ÁRTICO",
                price = MoneyCents(9_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(9_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = main.id,
                subcategoryId = subDragoes.id,
            )
            application.catalogRepository.createProduct(
                name = "GATO SENTADO",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = main.id,
                subcategoryId = subVazia1.id,
            )
            PickerIds(main.id, subPokemon.id, subDragoes.id, subVazia1.id, subVazia2.id, prodPika.id, prodDragao.id)
        }

        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitForTag("screen_vender")
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitForTag("product_picker_dialog")

        // Pesquisa por "pikachu" (minúscula) -> exibe categoria principal + apenas POKÉMON + produto
        composeRule.onNodeWithTag("product_picker_search").performTextInput("pikachu")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("picker_category_header_${ids.mainId}").assertIsDisplayed()
        composeRule.onNodeWithTag("picker_subcategory_header_${ids.subPokemonId}").assertIsDisplayed()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_${ids.prodPikaId}"))
        composeRule.onNodeWithTag("picker_row_${ids.prodPikaId}").assertIsDisplayed()

        // Subcategorias sem resultados NÃO aparecem
        composeRule.onAllNodesWithTag("picker_subcategory_header_${ids.subDragoesId}").assertCountEquals(0)
        composeRule.onAllNodesWithTag("picker_subcategory_header_${ids.subVazia1Id}").assertCountEquals(0)
        composeRule.onAllNodesWithTag("picker_subcategory_header_${ids.subVazia2Id}").assertCountEquals(0)

        // Pesquisa sem acento ("dragao") encontra "DRAGÃO ÁRTICO" e oculta demais subcategorias
        composeRule.onNodeWithTag("product_picker_search").performTextReplacement("dragao")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_subcategory_header_${ids.subDragoesId}"))
        composeRule.onNodeWithTag("picker_subcategory_header_${ids.subDragoesId}").assertIsDisplayed()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_row_${ids.prodDragaoId}"))
        composeRule.onNodeWithTag("picker_row_${ids.prodDragaoId}").assertIsDisplayed()
        composeRule.onAllNodesWithTag("picker_subcategory_header_${ids.subPokemonId}").assertCountEquals(0)
        composeRule.onAllNodesWithTag("picker_subcategory_header_${ids.subVazia1Id}").assertCountEquals(0)

        // Limpa pesquisa -> restaura visualização completa (ao expandir categoria principal, todas as subcategorias aparecem)
        composeRule.onNodeWithTag("product_picker_clear_search").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_list")
            .performScrollToNode(hasTestTag("picker_category_header_${ids.mainId}"))
        composeRule.onNodeWithTag("picker_category_header_${ids.mainId}").performClick()
        composeRule.waitForIdle()
        listOf(ids.subPokemonId, ids.subDragoesId, ids.subVazia1Id, ids.subVazia2Id).forEach { subId ->
            composeRule.onNodeWithTag("product_picker_list")
                .performScrollToNode(hasTestTag("picker_subcategory_header_$subId"))
            composeRule.onNodeWithTag("picker_subcategory_header_$subId").assertIsDisplayed()
        }
    }

    // -------------------------------------------------------------------------
    // GRUPO 6 (ETAPA 7):
    // Proteção contra exclusão acidental em Produtos, Categorias, Pessoas e Maquininhas
    // -------------------------------------------------------------------------

    @Test
    fun group6_accidentalDeletionRequiresExplicitConfirmationAcrossAllFourEntities() {
        val application = app()
        val (personId, productId, catId, machineId) = runBlocking(Dispatchers.IO) {
            val pKeep = application.catalogRepository.createPerson("KEEP")
            val pDel = application.catalogRepository.createPerson("REMOVER PESSOA")
            val cat = application.catalogRepository.createProductCategory("REMOVER CAT", null)
            val prod = application.catalogRepository.createProduct(
                name = "REMOVER PRODUTO",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(pKeep.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cat.id,
                subcategoryId = null,
            )
            val machine = application.machineRepository.createMachine(
                MachineInputDraft(
                    name = "REMOVER MAQ",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            Quad(pDel.id, prod.id, cat.id, machine.id)
        }

        // 1. Produto: cancelar mantém, confirmar exclui
        navigateToProducts()
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("delete_product_$productId"))
        composeRule.onNodeWithTag("delete_product_$productId").performClick()
        waitForTag("delete_product_dialog")
        composeRule.onNodeWithTag("cancel_delete_product").performClick()
        waitForTagGone("delete_product_dialog")
        assertTrue(runBlocking(Dispatchers.IO) { application.catalogRepository.observeAllProducts().first().any { it.id == productId } })

        composeRule.onNodeWithTag("delete_product_$productId").performClick()
        waitForTag("confirm_delete_product")
        composeRule.onNodeWithTag("confirm_delete_product").performClick()
        composeRule.waitUntil(5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) { application.catalogRepository.observeAllProducts().first().none { it.id == productId } }
        }

        // 2. Categoria: cancelar mantém, confirmar exclui
        composeRule.onNodeWithTag("screen_products")
            .performScrollToNode(hasTestTag("manage_categories_button"))
        composeRule.onNodeWithTag("manage_categories_button").performClick()
        waitForTag("category_management_dialog")
        composeRule.onNodeWithTag("category_management_scroll")
            .performScrollToNode(hasTestTag("delete_category_$catId"))
        composeRule.onNodeWithTag("delete_category_$catId").performClick()
        waitForTag("confirm_delete_category_dialog")
        composeRule.onNodeWithTag("cancel_delete_category_button").performClick()
        waitForTagGone("confirm_delete_category_dialog")
        assertTrue(runBlocking(Dispatchers.IO) { application.catalogRepository.observeProductCategories().first().any { it.id == catId } })

        composeRule.onNodeWithTag("delete_category_$catId").performClick()
        waitForTag("confirm_delete_category_button")
        composeRule.onNodeWithTag("confirm_delete_category_button").performClick()
        composeRule.waitUntil(5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) { application.catalogRepository.observeProductCategories().first().none { it.id == catId } }
        }

        // 3. Pessoa: cancelar mantém, confirmar exclui
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("screen_products")
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("mais_item_names")
        composeRule.onNodeWithTag("mais_item_names").performScrollTo().performClick()
        waitForTag("screen_names")
        composeRule.onNodeWithTag("screen_names")
            .performScrollToNode(hasTestTag("delete_person_$personId"))
        composeRule.onNodeWithTag("delete_person_$personId").performClick()
        waitForTag("delete_person_dialog")
        composeRule.onNodeWithTag("cancel_delete_person_button").performClick()
        waitForTagGone("delete_person_dialog")
        assertTrue(runBlocking(Dispatchers.IO) { application.catalogRepository.observePeople().first().any { it.id == personId } })

        composeRule.onNodeWithTag("delete_person_$personId").performClick()
        waitForTag("confirm_delete_person_button")
        composeRule.onNodeWithTag("confirm_delete_person_button").performClick()
        composeRule.waitUntil(5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) { application.catalogRepository.observePeople().first().none { it.id == personId } }
        }

        // 4. Maquininha: cancelar mantém, confirmar exclui
        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("mais_item_machines")
        composeRule.onNodeWithTag("mais_item_machines").performScrollTo().performClick()
        waitForTag("screen_machines")
        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("delete_machine_$machineId"))
        composeRule.onNodeWithTag("delete_machine_$machineId").performClick()
        waitForTag("delete_machine_dialog")
        composeRule.onNodeWithTag("cancel_delete_machine_button").performClick()
        waitForTagGone("delete_machine_dialog")
        assertTrue(runBlocking(Dispatchers.IO) { application.machineRepository.observeMachines().first().any { it.id == machineId } })

        composeRule.onNodeWithTag("machines_list_lazy")
            .performScrollToNode(hasTestTag("delete_machine_$machineId"))
        composeRule.onNodeWithTag("delete_machine_$machineId").performClick()
        waitForTag("confirm_delete_machine_button")
        composeRule.onNodeWithTag("confirm_delete_machine_button").performClick()
        composeRule.waitUntil(5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runBlocking(Dispatchers.IO) { application.machineRepository.observeMachines().first().none { it.id == machineId } }
        }
    }

    // -------------------------------------------------------------------------
    // GRUPO 7 (ETAPA 8):
    // Desempenho com grande volume (2.000 produtos e 2.000 vendas)
    // -------------------------------------------------------------------------

    @Test
    fun group7_highVolumeCatalogAndSalesFilteringAndAggregationRemainFastAndAccurate() {
        val products = (1..2_000).map { idx ->
            Product(
                id = "prod_$idx",
                name = if (idx == 1_999) "DRAGÃO ESPECIAL ÚNICO" else "PRODUTO 3D NUMERO $idx",
                price = MoneyCents(5_000L),
                active = idx % 5 != 0,
                participants = listOf(
                    ProductParticipantSplit("p1", "ANA", 0, MoneyCents(2_500L)),
                    ProductParticipantSplit("p2", "BRUNO", 1, MoneyCents(2_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                createdAtEpochMs = idx.toLong(),
                updatedAtEpochMs = idx.toLong(),
                categoryId = "cat_${idx % 20}",
                subcategoryId = "sub_${idx % 60}",
            )
        }

        val startFilter = System.nanoTime()
        val filtered = CatalogUiState.computeFilteredProducts(
            ProductsSectionUiState(
                products = products,
                searchQuery = "dragao especial",
                showOnlyActive = true,
            ),
        )
        val filterElapsedMs = (System.nanoTime() - startFilter) / 1_000_000L
        assertEquals(1, filtered.size)
        assertEquals("prod_1999", filtered.first().id)
        assertTrue("Filtro de 2.000 produtos demorou ${filterElapsedMs}ms", filterElapsedMs < 1_000L)

        val zone = ZoneId.of("America/Sao_Paulo")
        val baseDate = LocalDate.of(2026, 5, 1)
        val sales = (1..2_000).map { idx ->
            val date = baseDate.plusDays((idx % 60).toLong())
            val epochMs = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            PersistedSale(
                id = "sale_$idx",
                soldAtEpochMs = epochMs,
                updatedAtEpochMs = epochMs,
                calculation = SaleCalculationResult(
                    items = listOf(
                        SaleItemCalculationResult(
                            productId = "prod_1",
                            productName = "PRODUTO 1",
                            quantity = 1,
                            unitPriceOriginal = MoneyCents(5_000L),
                            originalSubtotal = MoneyCents(5_000L),
                            discountAmount = MoneyCents(0L),
                            finalAmount = MoneyCents(5_000L),
                            appliedFeeRateBps = 0,
                            feeAmount = MoneyCents(0L),
                            netAmount = MoneyCents(5_000L),
                            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                            feeOwnerPersonId = null,
                            feeOwnerPersonName = null,
                            participantSplits = listOf(
                                SaleItemParticipantSnapshot(
                                    personId = "p1",
                                    personName = "ANA",
                                    personColorIndex = 0,
                                    originalUnitAmount = MoneyCents(5_000L),
                                    originalSubtotal = MoneyCents(5_000L),
                                    discountAmount = MoneyCents(0L),
                                    grossAmount = MoneyCents(5_000L),
                                    feeAmount = MoneyCents(0L),
                                    netAmount = MoneyCents(5_000L),
                                ),
                            ),
                        ),
                    ),
                    personTotals = listOf(
                        SalePersonTotalSnapshot("p1", "ANA", 0, MoneyCents(5_000L), MoneyCents(0L), MoneyCents(5_000L)),
                    ),
                    originalSubtotal = MoneyCents(5_000L),
                    discountTotal = MoneyCents(0L),
                    finalAmount = MoneyCents(5_000L),
                    feeTotal = MoneyCents(0L),
                    netTotal = MoneyCents(5_000L),
                    paymentMethod = SalePaymentMethod.PIX,
                    machineSnapshot = null,
                    effectiveFeeRateBps = 0,
                    discountConfig = SaleDiscountConfigSnapshot.Proportional,
                    note = "",
                ),
            )
        }

        val startAgg = System.nanoTime()
        val report = DailySalesAggregator.aggregate(sales, zone)
        val aggElapsedMs = (System.nanoTime() - startAgg) / 1_000_000L
        assertEquals(2_000, report.totalSalesCount)
        assertEquals(2_000L * 5_000L, report.totalSold.rawCents)
        assertTrue("Agregação de 2.000 vendas demorou ${aggElapsedMs}ms", aggElapsedMs < 1_500L)
    }

    // -------------------------------------------------------------------------
    // GRUPO 8 (ETAPA 9):
    // Integridade financeira histórica após alterar produto, alterar taxas e excluir maquininha
    // -------------------------------------------------------------------------

    @Test
    fun group8_historicalSalesRemainCompletelyImmutableAfterCatalogAndMachineMutationsAndDeletion() = runBlocking(Dispatchers.IO) {
        val application = app()
        val p1 = application.catalogRepository.createPerson("ANA")
        val p2 = application.catalogRepository.createPerson("BRUNO")
        val product = application.catalogRepository.createProduct(
            name = "ESTÁTUA 3D",
            price = MoneyCents(10_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(3_000L)),
                ProductParticipantDraft(p2.id, MoneyCents(7_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val customBrandName = "BANESE VIP"
        val customBrandKey = CustomBrandKeyCodec.encodeKey(customBrandName)
        val machine = application.machineRepository.createMachine(
            MachineInputDraft(
                name = "STONE ANTIGA",
                debitRateBps = 190,
                creditEnabled = true,
                creditDefaultRateBps = 350,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 320),
                brandDebitRatesBps = mapOf(CardBrand.VISA to 140),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(3 to 200),
                customBrandRates = listOf(
                    CustomCardBrandInput(name = customBrandName, creditRateBps = 410, debitRateBps = 175),
                ),
            ),
        )

        val saleDate = LocalDate.of(2026, 4, 15)
        val zone = ZoneId.of("America/Sao_Paulo")

        // Registra venda no Crédito 3x com bandeira personalizada + desconto R$ 10,00
        val creditCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product = product, quantity = 1)),
                finalAmountOverride = MoneyCents(9_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = null,
                cardBrandKey = customBrandKey,
                installments = 3,
                note = "Venda histórica crédito",
            ),
        )
        val savedCredit = application.saleRepository.saveSale(creditCalc, saleDate = saleDate, zoneId = zone)

        // Registra venda no Débito com bandeira VISA (taxa 1,40%)
        val debitCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product = product, quantity = 1)),
                finalAmountOverride = MoneyCents(10_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine,
                cardBrand = CardBrand.VISA,
                cardBrandKey = CardBrand.VISA.storageKey,
                note = "Venda histórica débito",
            ),
        )
        val savedDebit = application.saleRepository.saveSale(debitCalc, saleDate = saleDate, zoneId = zone)

        // Mutações posteriores drásticas no catálogo e maquininha:
        // 1) Altera preço do produto (R$ 100 -> R$ 500), nome, divisão (50/50) e regra de tarifa (INDIVIDUAL)
        application.catalogRepository.updateProduct(
            productId = product.id,
            name = "ESTÁTUA NOVA CARA",
            price = MoneyCents(50_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(25_000L)),
                ProductParticipantDraft(p2.id, MoneyCents(25_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = p1.id,
        )
        // 2) Altera taxas da maquininha para valores completamente diferentes
        application.machineRepository.updateMachine(
            machineId = machine.id,
            draft = MachineInputDraft(
                name = "STONE TAXA ALTA",
                debitRateBps = 999,
                creditEnabled = true,
                creditDefaultRateBps = 1500,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 1200),
                brandDebitRatesBps = mapOf(CardBrand.VISA to 800),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(3 to 900),
                customBrandRates = listOf(
                    CustomCardBrandInput(name = customBrandName, creditRateBps = 1800, debitRateBps = 950),
                ),
            ),
        )
        // 3) Verifica bloqueio pelo repositório e remove direto no DAO para validar imutabilidade do snapshot
        val blockedEx = runCatching {
            application.machineRepository.deleteMachine(machine.id)
        }.exceptionOrNull()
        assertTrue(blockedEx is com.mixvendas3d.app.domain.calculation.MachineValidationException)
        assertTrue(blockedEx?.message.orEmpty().contains("2 vendas"))
        application.database.machineDao().deleteById(machine.id)

        // Valida que AMBAS as vendas históricas permanecem 100% idênticas no Room
        val reloaded = application.saleRepository.observeSales().first().associateBy { it.id }
        val reloadedCredit = reloaded.getValue(savedCredit.id)
        val reloadedDebit = reloaded.getValue(savedDebit.id)

        assertEquals(savedCredit.calculation, reloadedCredit.calculation)
        assertEquals(savedCredit.soldAtEpochMs, reloadedCredit.soldAtEpochMs)
        assertEquals(610, reloadedCredit.calculation.effectiveFeeRateBps) // 4,10% + 2,00%
        assertEquals("ESTÁTUA 3D", reloadedCredit.calculation.items.first().productName)
        assertEquals(10_000L, reloadedCredit.calculation.items.first().unitPriceOriginal.rawCents)
        assertEquals("STONE ANTIGA", reloadedCredit.calculation.machineSnapshot?.machineName)

        assertEquals(savedDebit.calculation, reloadedDebit.calculation)
        assertEquals(140, reloadedDebit.calculation.effectiveFeeRateBps)
        assertEquals(140L, reloadedDebit.calculation.feeTotal.rawCents)
        assertEquals(9_860L, reloadedDebit.calculation.netTotal.rawCents)

        // Valida integridade no Backup JSON e na Exportação CSV
        val backup = JsonBackupExporter.export(application.database)
        assertTrue(backup.jsonContent.contains("STONE ANTIGA"))
        assertTrue(backup.jsonContent.contains("ESTÁTUA 3D"))
        assertFalse(backup.jsonContent.isBlank())

        val csv = CsvSaleExporter.export(reloaded.values)
        assertEquals(2, csv.exportedSalesCount)
        assertTrue(csv.summaryCsvContent.contains("STONE ANTIGA"))
        assertTrue(csv.detailedCsvContent.contains("ESTÁTUA 3D"))
        assertTrue(csv.detailedCsvContent.contains("1,40%"))
        assertTrue(csv.detailedCsvContent.contains("6,10%"))
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
    private data class PickerIds(
        val mainId: String,
        val subPokemonId: String,
        val subDragoesId: String,
        val subVazia1Id: String,
        val subVazia2Id: String,
        val prodPikaId: String,
        val prodDragaoId: String,
    )
}
