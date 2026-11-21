package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageV29CategoriesTest {

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
            condition()
        }
    }

    private fun scrollToPickerNode(testTag: String) {
        waitUiUntil {
            runCatching {
                composeRule.onNodeWithTag("product_picker_list")
                    .performScrollToNode(hasTestTag(testTag))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `01 creation and renaming of main categories and subcategories`() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val repo = app.catalogRepository

        // 1. Criar Categoria Principal
        val decoracao = repo.createProductCategory("Decoração", parentId = null)
        assertEquals("Decoração", decoracao.name)
        assertNull(decoracao.parentId)

        // 2. Renomear Categoria Principal
        val decoracaoRenomeada = repo.renameProductCategory(decoracao.id, "Decoração & Arte")
        assertEquals("Decoração & Arte", decoracaoRenomeada.name)

        // 3. Criar Subcategoria
        val vasos = repo.createProductCategory("Vasos", parentId = decoracao.id)
        assertEquals("Vasos", vasos.name)
        assertEquals(decoracao.id, vasos.parentId)

        // 4. Renomear Subcategoria
        val vasosRenomeados = repo.renameProductCategory(vasos.id, "Vasos Decorativos")
        assertEquals("Vasos Decorativos", vasosRenomeados.name)
        assertEquals(decoracao.id, vasosRenomeados.parentId)
    }

    @Test
    fun `02 validation of two-level hierarchy limit and wrong parent category linking`() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val repo = app.catalogRepository

        val catPrincipal = repo.createProductCategory("Eletrônicos")
        val subCat = repo.createProductCategory("Smartphones", parentId = catPrincipal.id)

        // Tentar criar 3º nível deve lançar exceção
        try {
            repo.createProductCategory("Acessórios Celular", parentId = subCat.id)
            fail("Deveria bloquear criação de 3º nível de subcategoria.")
        } catch (ex: CatalogValidationException) {
            assertTrue(ex.message!!.contains("Subcategorias não podem ter um terceiro nível"))
        }

        // Tentar vincular produto a subcategoria pertencente a outra categoria pai
        val outraCat = repo.createProductCategory("Vestuário")
        val pessoa = repo.createPerson("Ana")

        try {
            repo.createProduct(
                name = "Camiseta",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(pessoa.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = outraCat.id,
                subcategoryId = subCat.id, // Pertence a 'Eletrônicos', não 'Vestuário'
            )
            fail("Deveria bloquear vinculação de subcategoria a categoria pai incompatível.")
        } catch (ex: CatalogValidationException) {
            assertTrue(ex.message!!.contains("não pertence à categoria principal selecionada"))
        }
    }

    @Test
    fun `03 safe deletion of subcategory preserves products in main category`() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val repo = app.catalogRepository

        val cat = repo.createProductCategory("Cozinha")
        val sub = repo.createProductCategory("Utensílios", parentId = cat.id)
        val pessoa = repo.createPerson("Bruno")

        val produto = repo.createProduct(
            name = "Espátula Silicone",
            price = MoneyCents(2_500L),
            active = true,
            participants = listOf(ProductParticipantDraft(pessoa.id, MoneyCents(2_500L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
            categoryId = cat.id,
            subcategoryId = sub.id,
        )

        assertEquals(cat.id, produto.categoryId)
        assertEquals(sub.id, produto.subcategoryId)

        // Excluir subcategoria
        repo.deleteProductCategory(sub.id)

        val prods = repo.observeAllProducts().first()
        val prodAtualizado = prods.first { it.id == produto.id }

        // Produto continua na Categoria Principal, mas sem subcategoria
        assertEquals(cat.id, prodAtualizado.categoryId)
        assertNull(prodAtualizado.subcategoryId)
    }

    @Test
    fun `04 safe deletion of main category moves products to uncategorized`() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val repo = app.catalogRepository

        val cat = repo.createProductCategory("Jardim")
        val sub = repo.createProductCategory("Vasos", parentId = cat.id)
        val pessoa = repo.createPerson("Carlos")

        val produto = repo.createProduct(
            name = "Vaso Cerâmica",
            price = MoneyCents(3_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(pessoa.id, MoneyCents(3_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
            categoryId = cat.id,
            subcategoryId = sub.id,
        )

        // Excluir Categoria Principal
        repo.deleteProductCategory(cat.id)

        val prods = repo.observeAllProducts().first()
        val prodAtualizado = prods.first { it.id == produto.id }

        // Produto passa a ficar "Sem categoria"
        assertNull(prodAtualizado.categoryId)
        assertNull(prodAtualizado.subcategoryId)
    }

    @Test
    fun `05 json backup export and restore preserves categories and handles old backups without categories`() = runBlocking(Dispatchers.IO) {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val db = app.database
        val repo = app.catalogRepository

        val cat = repo.createProductCategory("Móveis")
        val sub = repo.createProductCategory("Cadeiras", parentId = cat.id)
        val pessoa = repo.createPerson("Daniel")

        val produto = repo.createProduct(
            name = "Cadeira Gamer",
            price = MoneyCents(80_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(pessoa.id, MoneyCents(80_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
            categoryId = cat.id,
            subcategoryId = sub.id,
        )

        // Exportar backup JSON
        val exportResult = JsonBackupExporter.export(db)
        assertTrue(exportResult.jsonContent.contains("productCategories"))
        assertTrue(exportResult.jsonContent.contains("Móveis"))
        assertTrue(exportResult.jsonContent.contains("Cadeiras"))

        // Leitura e validação do backup exportado
        val readResult = JsonBackupReader.readAndValidate(exportResult.jsonContent)
        assertTrue(readResult.isValid)
        assertNotNull(readResult.validatedData)
        val catsBackup = readResult.validatedData!!.productCategories
        assertEquals(2, catsBackup.size)

        val prodBackup = readResult.validatedData!!.products.first { it.id == produto.id }
        assertEquals(cat.id, prodBackup.categoryId)
        assertEquals(sub.id, prodBackup.subcategoryId)

        // Testar leitura de backup antigo (JSON sem a coleção productCategories)
        val oldJson = """
        {
          "format": "MIX_VENDAS_3D_BACKUP",
          "backupSchemaVersion": 1,
          "applicationId": "com.mixvendas3d.app",
          "exportedAtEpochMs": 1700000000000,
          "roomSchemaVersion": 5,
          "collections": {
            "preferences": [],
            "people": [{"id":"p1","name":"Eduardo","normalizedName":"eduardo","colorIndex":0,"createdAtEpochMs":1700000000000,"updatedAtEpochMs":1700000000000}],
            "products": [{"id":"prod1","name":"Produto Antigo","priceCents":1000,"active":true,"feeResponsibilityMode":"PROPORTIONAL","feeOwnerPersonId":null,"createdAtEpochMs":1700000000000,"updatedAtEpochMs":1700000000000}],
            "productParticipants": [{"productId":"prod1","personId":"p1","amountCents":1000,"sortOrder":0}],
            "machines": [],
            "machineBrandRates": [],
            "machineInstallmentRates": [],
            "sales": [],
            "saleItems": [],
            "saleItemParticipants": [],
            "salePersonTotals": []
          },
          "counts": {
            "preferences": 0,
            "people": 1,
            "products": 1,
            "productParticipants": 1,
            "machines": 0,
            "machineBrandRates": 0,
            "machineInstallmentRates": 0,
            "sales": 0,
            "saleItems": 0,
            "saleItemParticipants": 0,
            "salePersonTotals": 0
          }
        }
        """.trimIndent()

        val oldReadResult = JsonBackupReader.readAndValidate(oldJson)
        assertTrue(oldReadResult.isValid)
        assertNotNull(oldReadResult.validatedData)
        assertTrue(oldReadResult.validatedData!!.productCategories.isEmpty())
        assertNull(oldReadResult.validatedData!!.products.first().categoryId)
    }

    @Test
    fun `06 hierarchical accordion selection in Nova Venda product picker`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val repo = app.catalogRepository

        val (catId, subId, prodId) = runBlocking(Dispatchers.IO) {
            val cat = repo.createProductCategory("Impressão 3D")
            val sub = repo.createProductCategory("Filamentos", parentId = cat.id)
            val pessoa = repo.createPerson("Fernanda")
            val prod = repo.createProduct(
                name = "Filamento PLA 1kg",
                price = MoneyCents(12_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(pessoa.id, MoneyCents(12_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cat.id,
                subcategoryId = sub.id,
            )
            Triple(cat.id, sub.id, prod.id)
        }

        waitUiUntil {
            runBlocking(Dispatchers.IO) {
                val prods = repo.observeActiveProducts().first()
                val cats = repo.observeProductCategories().first()
                prods.any { it.id == prodId && it.subcategoryId == subId } && cats.any { it.id == subId }
            }
        }
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        // Abrir Nova Venda e abrir o seletor de produtos
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("picker_category_header_$catId").fetchSemanticsNodes().isNotEmpty()
        }

        // 1. Inicialmente a categoria começa recolhida
        scrollToPickerNode("picker_category_header_$catId")
        composeRule.onNodeWithTag("picker_category_header_$catId").assertIsDisplayed()
        composeRule.onAllNodesWithTag("picker_row_$prodId").assertCountEquals(0)

        // 2. Tocar na categoria expande e exibe a subcategoria
        composeRule.onNodeWithTag("picker_category_header_$catId").performClick()
        scrollToPickerNode("picker_subcategory_header_$subId")
        composeRule.onNodeWithTag("picker_subcategory_header_$subId").assertIsDisplayed()
        composeRule.onAllNodesWithTag("picker_row_$prodId").assertCountEquals(0)

        // 3. Tocar na subcategoria expande e exibe o produto
        composeRule.onNodeWithTag("picker_subcategory_header_$subId").performClick()
        scrollToPickerNode("picker_row_$prodId")
        composeRule.onNodeWithTag("picker_row_$prodId").assertIsDisplayed()

        // 4. Selecionar o produto e fechar picker
        composeRule.onNodeWithTag("picker_checkbox_$prodId").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_picker_close_x_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("product_picker_dialog").fetchSemanticsNodes().isEmpty()
        }
    }
}
