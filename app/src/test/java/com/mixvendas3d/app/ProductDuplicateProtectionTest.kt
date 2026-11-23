package com.mixvendas3d.app

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.domain.calculation.ParticipantDiffType
import com.mixvendas3d.app.domain.calculation.ProductDuplicateCandidate
import com.mixvendas3d.app.domain.calculation.ProductDuplicateCheckResult
import com.mixvendas3d.app.domain.calculation.ProductDuplicateDetector
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.ui.screens.CatalogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Cobertura integral dos cenários da Parte 1 (Seção 12):
 * Detecção inteligente de duplicidade durante cadastro e edição de produtos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xxhdpi")
class ProductDuplicateProtectionTest {

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

    private fun withCollectedCatalogViewModel(block: (CatalogViewModel) -> Unit) {
        val vm = CatalogViewModel(app().catalogRepository)
        val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val job: Job = collectorScope.launch {
            vm.uiState.collect { }
        }
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            block(vm)
        } finally {
            job.cancel()
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(15)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Condição não satisfeita dentro de ${timeoutMs}ms.", predicate())
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForTagGone(tag: String, timeoutMillis: Long = 8_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun scenario01_sameNameDifferentPrice_savesWithoutAlert() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val ana = runBlocking(Dispatchers.IO) {
            val person = repo.createPerson("Ana")
            repo.createProduct(
                name = "DRAGÃO 3D",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            person
        }

        vm.openNewProductEditor()
        awaitCondition { vm.uiState.value.products.editor.isOpen }
        vm.onProductNameChanged("Dragao 3D")
        vm.onProductPriceChanged("7500")
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(ana.id, "7500")
        vm.saveProduct()

        awaitCondition { !vm.uiState.value.products.editor.isOpen }
        assertNull(vm.uiState.value.products.editor.pendingDuplicateConfirmation)
        assertNull(vm.uiState.value.products.editor.errorMessage)

        val savedProducts = runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot() }
        assertEquals(2, savedProducts.size)
    }

    @Test
    fun scenario02_sameNameSamePriceDifferentCategory_showsConfirmationWithDiff() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, catA, catB) = runBlocking(Dispatchers.IO) {
            val person = repo.createPerson("Ana")
            val cA = repo.createProductCategory("ANIMAIS", null)
            val cB = repo.createProductCategory("DECORAÇÃO", null)
            repo.createProduct(
                name = "DRAGÃO 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cA.id,
            )
            Triple(person, cA, cB)
        }

        vm.openNewProductEditor()
        vm.onProductNameChanged("DRAGÃO 3D")
        vm.onProductPriceChanged("10000")
        vm.onProductCategoryChanged(catB.id)
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(ana.id, "10000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        val pending = vm.uiState.value.products.editor.pendingDuplicateConfirmation!!
        assertEquals(1, pending.result.matches.size)
        val diff = pending.result.matches.first().fieldDifferences
        assertTrue(diff.any { it.fieldLabel == "Categoria" && it.existingValue == catA.name && it.candidateValue == catB.name })

        // Não salvou antes da confirmação
        assertEquals(1, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })
    }

    @Test
    fun scenario03_sameNameSamePriceDifferentSubcategory_showsConfirmationWithDiff() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, catA, sub1, sub2) = runBlocking(Dispatchers.IO) {
            val person = repo.createPerson("Ana")
            val cA = repo.createProductCategory("CHAVEIROS", null)
            val s1 = repo.createProductCategory("PEQUENO", cA.id)
            val s2 = repo.createProductCategory("GRANDE", cA.id)
            repo.createProduct(
                name = "CHAVEIRO GATO",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(person.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cA.id,
                subcategoryId = s1.id,
            )
            listOf(person, cA, s1, s2)
        }
        val person = ana as com.mixvendas3d.app.domain.models.Person
        val category = catA as com.mixvendas3d.app.domain.models.ProductCategory
        val subOld = sub1 as com.mixvendas3d.app.domain.models.ProductCategory
        val subNew = sub2 as com.mixvendas3d.app.domain.models.ProductCategory

        vm.openNewProductEditor()
        vm.onProductNameChanged("CHAVEIRO GATO")
        vm.onProductPriceChanged("2500")
        vm.onProductCategoryChanged(category.id)
        vm.onProductSubcategoryChanged(subNew.id)
        vm.toggleProductParticipant(person.id)
        vm.onParticipantAmountChanged(person.id, "2500")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        val diff = vm.uiState.value.products.editor.pendingDuplicateConfirmation!!.result.matches.first().fieldDifferences
        assertTrue(diff.any { it.fieldLabel == "Subcategoria" && it.existingValue == subOld.name && it.candidateValue == subNew.name })
    }

    @Test
    fun scenario04_sameNameSamePriceDifferentParticipants_showsAddedAndRemovedPeople() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, bruno) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val p2 = repo.createPerson("Bruno")
            repo.createProduct(
                name = "VASO GEOMÉTRICO",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(6_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            p1 to p2
        }

        vm.openNewProductEditor()
        vm.onProductNameChanged("VASO GEOMÉTRICO")
        vm.onProductPriceChanged("6000")
        vm.toggleProductParticipant(bruno.id)
        vm.onParticipantAmountChanged(bruno.id, "6000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        val participantDiffs = vm.uiState.value.products.editor.pendingDuplicateConfirmation!!
            .result.matches.first().participantDiffs
        assertTrue(participantDiffs.any { it.personId == ana.id && it.diffType == ParticipantDiffType.REMOVED })
        assertTrue(participantDiffs.any { it.personId == bruno.id && it.diffType == ParticipantDiffType.ADDED })
    }

    @Test
    fun scenario05_sameParticipantsDifferentIndividualAmounts_showsAmountChangedDiff() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, bruno) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val p2 = repo.createPerson("Bruno")
            repo.createProduct(
                name = "LUMINÁRIA LUA",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(7_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            p1 to p2
        }

        vm.openNewProductEditor()
        vm.onProductNameChanged("LUMINÁRIA LUA")
        vm.onProductPriceChanged("10000")
        vm.toggleProductParticipant(ana.id)
        vm.toggleProductParticipant(bruno.id)
        vm.onParticipantAmountChanged(ana.id, "4000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        val pDiffs = vm.uiState.value.products.editor.pendingDuplicateConfirmation!!
            .result.matches.first().participantDiffs
        val anaDiff = pDiffs.first { it.personId == ana.id }
        val brunoDiff = pDiffs.first { it.personId == bruno.id }
        assertEquals(ParticipantDiffType.AMOUNT_CHANGED, anaDiff.diffType)
        assertEquals(3_000L, anaDiff.existingAmountCents)
        assertEquals(4_000L, anaDiff.candidateAmountCents)
        assertEquals(ParticipantDiffType.AMOUNT_CHANGED, brunoDiff.diffType)
        assertEquals(7_000L, brunoDiff.existingAmountCents)
        assertEquals(6_000L, brunoDiff.candidateAmountCents)
    }

    @Test
    fun scenario06_07_08_exactDuplicateNormalizationAndParticipantOrder_blocksDefinitively() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, bruno, cat) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val p2 = repo.createPerson("Bruno")
            val c = repo.createProductCategory("COLEÇÃO", null)
            repo.createProduct(
                name = "CORAÇÃO 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(7_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = c.id,
            )
            Triple(p1, p2, c)
        }

        // Cenário 6 + 7 + 8: nome com acentos ausentes, espaços extras e minúsculas + participantes em ordem invertida (Bruno primeiro, Ana depois)
        vm.openNewProductEditor()
        vm.onProductNameChanged("   coracao    3d  ")
        vm.onProductPriceChanged("10000")
        vm.onProductCategoryChanged(cat.id)
        vm.toggleProductParticipant(bruno.id)
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(bruno.id, "7000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.errorMessage != null }
        assertEquals(
            ProductDuplicateCheckResult.EXACT_DUPLICATE_ERROR_MESSAGE,
            vm.uiState.value.products.editor.errorMessage,
        )
        assertNull(vm.uiState.value.products.editor.pendingDuplicateConfirmation)
        assertTrue(vm.uiState.value.products.editor.isOpen)
        assertEquals(1, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })
    }

    @Test
    fun scenario09_cancelConfirmationAlert_preservesAllEditorFieldsAndDoesNotSave() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, cat1, cat2) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val c1 = repo.createProductCategory("CAT A", null)
            val c2 = repo.createProductCategory("CAT B", null)
            repo.createProduct(
                name = "MINIATURA ROBÔ",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(4_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = c1.id,
            )
            Triple(p1, c1, c2)
        }

        vm.openNewProductEditor()
        vm.onProductNameChanged("MINIATURA ROBÔ")
        vm.onProductPriceChanged("4500")
        vm.onProductCategoryChanged(cat2.id)
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(ana.id, "4500")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        vm.dismissDuplicateConfirmation()

        val editor = vm.uiState.value.products.editor
        assertNull(editor.pendingDuplicateConfirmation)
        assertTrue(editor.isOpen)
        assertEquals("MINIATURA ROBÔ", editor.nameInput)
        assertEquals("45,00", editor.priceInput)
        assertEquals(cat2.id, editor.categoryId)
        assertEquals(listOf(ana.id), editor.selectedParticipantOrder)
        assertEquals("45,00", editor.participantAmountInputs[ana.id])
        assertEquals(1, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })
    }

    @Test
    fun scenario10_confirmSaveAnyway_persistsOnceAndKeepsFutureProtectionActive() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, cat1, cat2) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val c1 = repo.createProductCategory("CAT 1", null)
            val c2 = repo.createProductCategory("CAT 2", null)
            repo.createProduct(
                name = "SUPORTE CONTROLE",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(8_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = c1.id,
            )
            Triple(p1, c1, c2)
        }

        vm.openNewProductEditor()
        vm.onProductNameChanged("SUPORTE CONTROLE")
        vm.onProductPriceChanged("8000")
        vm.onProductCategoryChanged(cat2.id)
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(ana.id, "8000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        vm.confirmSaveDespiteSimilarProducts()

        awaitCondition { !vm.uiState.value.products.editor.isOpen }
        assertEquals(2, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })

        // Tentar cadastrar novamente exatamente o mesmo da CAT 2 deve bloquear como duplicado exato
        vm.openNewProductEditor()
        vm.onProductNameChanged("SUPORTE CONTROLE")
        vm.onProductPriceChanged("8000")
        vm.onProductCategoryChanged(cat2.id)
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(ana.id, "8000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.errorMessage != null }
        assertEquals(
            ProductDuplicateCheckResult.EXACT_DUPLICATE_ERROR_MESSAGE,
            vm.uiState.value.products.editor.errorMessage,
        )
        assertEquals(2, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })
    }

    @Test
    fun scenario11_multipleExistingSimilarProducts_allMatchesDisplayedInAlert() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, bruno, cat1, cat2, cat3) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val p2 = repo.createPerson("Bruno")
            val c1 = repo.createProductCategory("LINHA A", null)
            val c2 = repo.createProductCategory("LINHA B", null)
            val c3 = repo.createProductCategory("LINHA C", null)
            repo.createProduct(
                name = "COPO 3D",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = c1.id,
            )
            repo.createProduct(
                name = "COPO 3D",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p2.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = c2.id,
            )
            listOf(p1, p2, c1, c2, c3)
        }
        val personAna = ana as com.mixvendas3d.app.domain.models.Person
        val category3 = cat3 as com.mixvendas3d.app.domain.models.ProductCategory

        vm.openNewProductEditor()
        vm.onProductNameChanged("COPO 3D")
        vm.onProductPriceChanged("5000")
        vm.onProductCategoryChanged(category3.id)
        vm.toggleProductParticipant(personAna.id)
        vm.onParticipantAmountChanged(personAna.id, "5000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        val matches = vm.uiState.value.products.editor.pendingDuplicateConfirmation!!.result.matches
        assertEquals(2, matches.size)
    }

    @Test
    fun scenario12_13_14_editingProduct_ignoresSelfBlocksExactAndConfirmsSimilarOnlyUpdatingEditedId() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val seeded = runBlocking(Dispatchers.IO) {
            val ana = repo.createPerson("Ana")
            val cat1 = repo.createProductCategory("CAT A", null)
            val cat2 = repo.createProductCategory("CAT B", null)
            val prod1 = repo.createProduct(
                name = "PEÇA BASE",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cat1.id,
            )
            val prod2 = repo.createProduct(
                name = "OUTRA PEÇA",
                price = MoneyCents(7_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(7_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
                categoryId = cat2.id,
            )
            listOf(ana, cat1, cat2, prod1, prod2)
        }
        val anaPerson = seeded[0] as com.mixvendas3d.app.domain.models.Person
        val cat1 = seeded[1] as com.mixvendas3d.app.domain.models.ProductCategory
        val cat2 = seeded[2] as com.mixvendas3d.app.domain.models.ProductCategory
        val prod1 = seeded[3] as com.mixvendas3d.app.domain.models.Product
        val prod2 = seeded[4] as com.mixvendas3d.app.domain.models.Product

        // Cenário 12: editar prod1 mantendo mesmos 4 critérios (apenas alterando ativo) não conflita consigo mesmo
        vm.openEditProductEditor(prod1)
        awaitCondition { vm.uiState.value.products.editor.isOpen }
        vm.onProductActiveChanged(false)
        vm.saveProduct()
        awaitCondition { !vm.uiState.value.products.editor.isOpen }
        val reloadedProd1 = runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().first { it.id == prod1.id } }
        assertFalse(reloadedProd1.active)

        // Cenário 13: editar prod2 para ficar idêntico a prod1 (mesmo nome, preço, categoria, participantes) bloqueia mesmo com prod1 inativo!
        vm.openEditProductEditor(prod2)
        awaitCondition { vm.uiState.value.products.editor.isOpen }
        vm.onProductNameChanged("PEÇA BASE")
        vm.onProductPriceChanged("5000")
        vm.onParticipantAmountChanged(anaPerson.id, "5000")
        vm.onProductCategoryChanged(cat1.id)
        vm.saveProduct()
        awaitCondition { vm.uiState.value.products.editor.errorMessage != null }
        assertEquals(
            ProductDuplicateCheckResult.EXACT_DUPLICATE_ERROR_MESSAGE,
            vm.uiState.value.products.editor.errorMessage,
        )

        // Cenário 14: editar prod2 com mesmo nome e preço de prod1, mas categoria cat2 -> pede confirmação e atualiza SOMENTE prod2
        vm.onProductCategoryChanged(cat2.id)
        vm.saveProduct()
        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }
        assertTrue(vm.uiState.value.products.editor.pendingDuplicateConfirmation!!.isEditing)
        vm.confirmSaveDespiteSimilarProducts()

        awaitCondition { !vm.uiState.value.products.editor.isOpen }
        val allAfter = runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot() }
        assertEquals(2, allAfter.size)
        val finalProd1 = allAfter.first { it.id == prod1.id }
        val finalProd2 = allAfter.first { it.id == prod2.id }
        assertEquals(cat1.id, finalProd1.categoryId)
        assertFalse(finalProd1.active)
        assertEquals("PEÇA BASE", finalProd2.name)
        assertEquals(5_000L, finalProd2.price.rawCents)
        assertEquals(cat2.id, finalProd2.categoryId)
    }

    @Test
    fun scenario15_revalidationBeforeConfirmedSaveAndIgnoreNonCriteriaFields() = withCollectedCatalogViewModel { vm ->
        val repo = app().catalogRepository
        val (ana, cat1, cat2) = runBlocking(Dispatchers.IO) {
            val p1 = repo.createPerson("Ana")
            val c1 = repo.createProductCategory("ORIG", null)
            val c2 = repo.createProductCategory("NOVA", null)
            repo.createProduct(
                name = "TROFÉU 3D",
                price = MoneyCents(9_000L),
                active = false, // Inativo e com taxa EQUAL — não impede detecção!
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(9_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
                categoryId = c1.id,
            )
            Triple(p1, c1, c2)
        }

        // Abre alerta de confirmação para cat2
        vm.openNewProductEditor()
        vm.onProductNameChanged("TROFÉU 3D")
        vm.onProductPriceChanged("9000")
        vm.onProductCategoryChanged(cat2.id)
        vm.toggleProductParticipant(ana.id)
        vm.onParticipantAmountChanged(ana.id, "9000")
        vm.saveProduct()

        awaitCondition { vm.uiState.value.products.editor.pendingDuplicateConfirmation != null }

        // Simula alteração concorrente no catálogo enquanto o alerta estava aberto:
        // alguém inseriu exatamente "TROFÉU 3D" R$ 90,00 na cat2
        runBlocking(Dispatchers.IO) {
            repo.createProduct(
                name = "TROFÉU 3D",
                price = MoneyCents(9_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(9_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
                categoryId = cat2.id,
            )
        }

        // Ao confirmar, revalidação obrigatória detecta duplicidade exata e bloqueia!
        vm.confirmSaveDespiteSimilarProducts()
        awaitCondition { vm.uiState.value.products.editor.errorMessage != null }
        assertEquals(
            ProductDuplicateCheckResult.EXACT_DUPLICATE_ERROR_MESSAGE,
            vm.uiState.value.products.editor.errorMessage,
        )
        assertNull(vm.uiState.value.products.editor.pendingDuplicateConfirmation)
        assertEquals(2, runBlocking(Dispatchers.IO) { repo.getAllProductsSnapshot().size })
    }

    @Test
    fun uiFlow_similarDuplicateDialogDisplaysDiffAndSupportsCancelThenConfirm() {
        val application = app()
        val seeded = runBlocking(Dispatchers.IO) {
            val ana = application.catalogRepository.createPerson("Ana")
            val bruno = application.catalogRepository.createPerson("Bruno")
            application.catalogRepository.createProduct(
                name = "ESTÁTUA HERO",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            ana to bruno
        }
        val bruno = seeded.second

        // Navega para Produtos
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitForTag("mais_item_types_products")
        composeRule.onNodeWithTag("mais_item_types_products").performScrollTo().performClick()
        waitForTag("screen_products")

        // Abre Novo Produto
        composeRule.onNodeWithTag("new_product_button").performScrollTo().performClick()
        waitForTag("product_name_input")

        composeRule.onNodeWithTag("product_name_input").performTextReplacement("ESTÁTUA HERO")
        composeRule.onNodeWithTag("product_price_input").performTextReplacement("10000")
        composeRule.onNodeWithTag("product_participant_check_${bruno.id}").performScrollTo().performClick()
        composeRule.onNodeWithTag("product_participant_amount_${bruno.id}").performScrollTo().performTextReplacement("10000")
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()

        // Diálogo de confirmação deve abrir com título exato
        waitForTag("product_duplicate_confirmation_dialog")
        composeRule.onNodeWithText("Produto com mesmo nome e valor encontrado.").assertIsDisplayed()

        // Cancelar fecha o diálogo e preserva campos
        composeRule.onNodeWithTag("product_duplicate_cancel_button").performClick()
        waitForTagGone("product_duplicate_confirmation_dialog")
        assertEquals(1, runBlocking(Dispatchers.IO) { application.catalogRepository.getAllProductsSnapshot().size })

        // Tenta salvar novamente e confirma "Cadastrar mesmo assim"
        composeRule.onNodeWithTag("save_product_button").performScrollTo().performClick()
        waitForTag("product_duplicate_confirmation_dialog")
        composeRule.onNodeWithText("Cadastrar mesmo assim").assertIsDisplayed()
        composeRule.onNodeWithTag("product_duplicate_confirm_button").performClick()

        waitForTagGone("product_duplicate_confirmation_dialog")
        waitForTag("screen_products")
        assertEquals(2, runBlocking(Dispatchers.IO) { application.catalogRepository.getAllProductsSnapshot().size })
    }

    @Test
    fun pureDetector_ignoresActiveAndFeeModeAndValidates4CriteriaOnly() {
        val candidate = ProductDuplicateCandidate(
            displayName = "  Boneco   3D  ",
            priceCents = 5_000L,
            categoryId = "cat-1",
            subcategoryId = "sub-1",
            participantsByPersonId = mapOf("p2" to 2_000L, "p1" to 3_000L),
            participantNamesById = mapOf("p1" to "ANA", "p2" to "BRUNO"),
        )
        val existing = com.mixvendas3d.app.domain.models.Product(
            id = "existing-1",
            name = "BONECO 3D",
            price = MoneyCents(5_000L),
            active = false,
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = "p1",
            participants = listOf(
                com.mixvendas3d.app.domain.models.ProductParticipantSplit("p1", "ANA", 0, MoneyCents(3_000L)),
                com.mixvendas3d.app.domain.models.ProductParticipantSplit("p2", "BRUNO", 1, MoneyCents(2_000L)),
            ),
            createdAtEpochMs = 100L,
            updatedAtEpochMs = 200L,
            categoryId = "cat-1",
            subcategoryId = "sub-1",
        )
        val result = ProductDuplicateDetector.evaluateCandidate(
            candidate = candidate,
            existingProducts = listOf(existing),
            categoriesById = emptyMap(),
        )
        assertTrue(result is ProductDuplicateCheckResult.ExactDuplicateBlocked)
        assertNotNull((result as ProductDuplicateCheckResult.ExactDuplicateBlocked).matches.firstOrNull())
    }
}
