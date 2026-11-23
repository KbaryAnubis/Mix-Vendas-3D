package com.mixvendas3d.app

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.ProductCategoryEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.DecisionValidationIssueType
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupErrorCode
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.JsonBackupValidationException
import com.mixvendas3d.app.domain.export.MergeConflictKind
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergePlanAction
import com.mixvendas3d.app.domain.export.RelationalRiskType
import com.mixvendas3d.app.ui.screens.BackupAnalysisPhase
import com.mixvendas3d.app.ui.screens.BackupComparisonOutcome
import com.mixvendas3d.app.ui.screens.BackupConflictFormatter
import com.mixvendas3d.app.ui.screens.ImportExportViewModel
import java.io.ByteArrayInputStream
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuditImpedimentsJsonBackupCategoriesTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun newInMemoryDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private suspend fun seedBasePerson(
        db: AppDatabase,
        id: String = "person-1",
        name: String = "Ana",
        norm: String = "ana",
    ) {
        db.personDao().insert(
            PersonEntity(
                id = id,
                name = name,
                normalizedName = norm,
                colorIndex = 0,
                createdAtEpochMs = 1_730_000_000_000L,
                updatedAtEpochMs = 1_730_000_000_000L,
            ),
        )
    }

    private suspend fun seedCategory(
        db: AppDatabase,
        id: String,
        name: String,
        norm: String,
        parentId: String? = null,
        createdAt: Long = 1_730_000_010_000L,
        updatedAt: Long = createdAt,
    ) {
        val inserted = db.productDao().insertCategoryIfUnique(
            ProductCategoryEntity(
                id = id,
                name = name,
                normalizedName = norm,
                parentId = parentId,
                createdAtEpochMs = createdAt,
                updatedAtEpochMs = updatedAt,
            ),
        )
        assertTrue("Falha ao inserir categoria de teste $id", inserted)
    }

    private suspend fun seedProduct(
        db: AppDatabase,
        id: String,
        name: String,
        priceCents: Long,
        personId: String,
        categoryId: String? = null,
        subcategoryId: String? = null,
        updatedAt: Long = 1_730_000_020_000L,
    ) {
        db.productDao().insertProduct(
            ProductEntity(
                id = id,
                name = name,
                priceCents = priceCents,
                active = true,
                feeResponsibilityMode = "PROPORTIONAL",
                feeOwnerPersonId = null,
                categoryId = categoryId,
                subcategoryId = subcategoryId,
                createdAtEpochMs = 1_730_000_020_000L,
                updatedAtEpochMs = updatedAt,
            ),
        )
        db.productDao().insertParticipants(
            listOf(
                ProductParticipantEntity(
                    productId = id,
                    personId = personId,
                    amountCents = priceCents,
                    sortOrder = 0,
                ),
            ),
        )
    }

    @Test
    fun test01_exportAndRestoreNewProductsWithCategoriesAndSubcategoriesPreservesLinks() = runBlocking<Unit>(Dispatchers.IO) {
        val sourceDb = newInMemoryDb()
        val targetDb = newInMemoryDb()
        try {
            seedBasePerson(sourceDb)
            seedCategory(sourceDb, id = "cat-decor", name = "Decoração", norm = "decoracao")
            seedCategory(sourceDb, id = "sub-vasos", name = "Vasos", norm = "vasos", parentId = "cat-decor")
            seedProduct(
                db = sourceDb,
                id = "prod-vaso",
                name = "Vaso Geométrico",
                priceCents = 4_500L,
                personId = "person-1",
                categoryId = "cat-decor",
                subcategoryId = "sub-vasos",
            )

            val exported = JsonBackupExporter.export(sourceDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.errors.joinToString { it.message }, readResult.isValid)
            assertEquals(2, readResult.validatedData!!.productCategories.size)

            val plan = JsonBackupMergePlanner.plan(readResult, targetDb)
            assertEquals(12, plan.collectionPlans.size)
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PRODUCT_CATEGORIES).actionForRecord("cat-decor"))
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PRODUCT_CATEGORIES).actionForRecord("sub-vasos"))
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PRODUCTS).actionForRecord("prod-vaso"))

            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, targetDb, emptyMap())
            assertTrue(validation.isReadyForNextPhase)

            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, targetDb)
            assertTrue(preview.isSafeToApply)
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.PRODUCT_CATEGORIES])
            assertEquals(2, preview.mergedCollections.productCategories.size)
            val previewProd = preview.mergedCollections.products.single()
            assertEquals("cat-decor", previewProd.categoryId)
            assertEquals("sub-vasos", previewProd.subcategoryId)

            val execResult = JsonBackupMergeExecutor.execute(
                readResult = readResult,
                plan = plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = validation,
                previewResult = preview,
                executionContract = preview.executionContract,
                userConfirmed = true,
                database = targetDb,
            )
            assertTrue(execResult.failureMessage ?: "Execução deveria ter sucesso", execResult.success)
            assertFalse(execResult.rolledBack)
            assertEquals(2, execResult.finalTableCounts[BackupCollectionType.PRODUCT_CATEGORIES])

            val persistedCategories = targetDb.productDao().getAllCategories().associateBy { it.id }
            assertEquals(2, persistedCategories.size)
            assertNull(persistedCategories["cat-decor"]?.parentId)
            assertEquals("cat-decor", persistedCategories["sub-vasos"]?.parentId)

            val persistedProduct = targetDb.productDao().getAllProducts().single()
            assertEquals("cat-decor", persistedProduct.categoryId)
            assertEquals("sub-vasos", persistedProduct.subcategoryId)
        } finally {
            sourceDb.close()
            targetDb.close()
        }
    }

    @Test
    fun test02_mergeUpdateExistingProductPreservesCategoryAndSubcategoryOnApplyAndKeepLocal() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localApplyDb = newInMemoryDb()
        val localKeepDb = newInMemoryDb()
        try {
            // Backup: prod-1 aponta para cat-b / sub-b; além de cat-a existente
            listOf(backupDb, localApplyDb, localKeepDb).forEach { db ->
                seedBasePerson(db)
                seedCategory(db, id = "cat-a", name = "Decoração", norm = "decoracao")
                seedCategory(db, id = "sub-a", name = "Vasos", norm = "vasos", parentId = "cat-a")
            }
            seedCategory(backupDb, id = "cat-b", name = "Utilidades", norm = "utilidades")
            seedCategory(backupDb, id = "sub-b", name = "Suportes", norm = "suportes", parentId = "cat-b")
            seedProduct(
                db = backupDb,
                id = "prod-1",
                name = "Suporte Mesa",
                priceCents = 3_000L,
                personId = "person-1",
                categoryId = "cat-b",
                subcategoryId = "sub-b",
                updatedAt = 1_730_000_090_000L,
            )

            // Bancos locais: mesmo prod-1 apontando para cat-a / sub-a
            listOf(localApplyDb, localKeepDb).forEach { db ->
                seedProduct(
                    db = db,
                    id = "prod-1",
                    name = "Suporte Mesa",
                    priceCents = 3_000L,
                    personId = "person-1",
                    categoryId = "cat-a",
                    subcategoryId = "sub-a",
                    updatedAt = 1_730_000_050_000L,
                )
            }

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_200_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.isValid)

            // Caminho 1: USE_BACKUP -> deve gravar cat-b/sub-b e atualizar categoryId/subcategoryId do produto
            val planApply = JsonBackupMergePlanner.plan(readResult, localApplyDb)
            val prodConflict = planApply.conflictItems.single { it.collection == BackupCollectionType.PRODUCTS }
            assertTrue("categoryId" in prodConflict.divergentFields)
            assertTrue("subcategoryId" in prodConflict.divergentFields)

            val decisionsApply = mapOf("products:prod-1" to MergeConflictResolution.USE_BACKUP)
            val valApply = JsonBackupMergeDecisionValidator.validate(planApply, readResult, localApplyDb, decisionsApply)
            assertTrue(valApply.isReadyForNextPhase)
            val prevApply = JsonBackupMergePreviewBuilder.buildPreview(readResult, planApply, decisionsApply, valApply, localApplyDb)
            assertTrue(prevApply.isSafeToApply)
            assertEquals("cat-b", prevApply.mergedCollections.products.single().categoryId)
            assertEquals("sub-b", prevApply.mergedCollections.products.single().subcategoryId)

            val execApply = JsonBackupMergeExecutor.execute(
                readResult, planApply, decisionsApply, valApply, prevApply, prevApply.executionContract, true, localApplyDb,
            )
            assertTrue(execApply.failureMessage ?: "", execApply.success)
            val updatedProd = localApplyDb.productDao().getAllProducts().single()
            assertEquals("cat-b", updatedProd.categoryId)
            assertEquals("sub-b", updatedProd.subcategoryId)
            assertEquals(4, localApplyDb.productDao().getAllCategories().size)

            // Caminho 2: KEEP_LOCAL -> deve preservar cat-a/sub-a no produto local e adicionar novas categorias do backup sem apagar locais
            val planKeep = JsonBackupMergePlanner.plan(readResult, localKeepDb)
            val decisionsKeep = mapOf("products:prod-1" to MergeConflictResolution.KEEP_LOCAL)
            val valKeep = JsonBackupMergeDecisionValidator.validate(planKeep, readResult, localKeepDb, decisionsKeep)
            assertTrue(valKeep.isReadyForNextPhase)
            val prevKeep = JsonBackupMergePreviewBuilder.buildPreview(readResult, planKeep, decisionsKeep, valKeep, localKeepDb)
            assertTrue(prevKeep.isSafeToApply)
            assertEquals("cat-a", prevKeep.mergedCollections.products.single().categoryId)
            assertEquals("sub-a", prevKeep.mergedCollections.products.single().subcategoryId)

            val execKeep = JsonBackupMergeExecutor.execute(
                readResult, planKeep, decisionsKeep, valKeep, prevKeep, prevKeep.executionContract, true, localKeepDb,
            )
            assertTrue(execKeep.failureMessage ?: "", execKeep.success)
            val keptProd = localKeepDb.productDao().getAllProducts().single()
            assertEquals("cat-a", keptProd.categoryId)
            assertEquals("sub-a", keptProd.subcategoryId)
            assertEquals(4, localKeepDb.productDao().getAllCategories().size)
        } finally {
            backupDb.close()
            localApplyDb.close()
            localKeepDb.close()
        }
    }

    @Test
    fun test03_subcategoriesOrderedBeforeParentInJsonStillRestoreParentFirst() = runBlocking<Unit>(Dispatchers.IO) {
        val sourceDb = newInMemoryDb()
        val targetDb = newInMemoryDb()
        try {
            seedBasePerson(sourceDb)
            // ID da subcategoria ("a-sub") alfabeticamente ANTES da categoria principal ("z-parent")
            seedCategory(sourceDb, id = "z-parent", name = "Miniaturas", norm = "miniaturas")
            seedCategory(sourceDb, id = "a-sub", name = "Dragões", norm = "dragoes", parentId = "z-parent")
            seedProduct(
                db = sourceDb,
                id = "prod-dragao",
                name = "Dragão Articulado",
                priceCents = 8_000L,
                personId = "person-1",
                categoryId = "z-parent",
                subcategoryId = "a-sub",
            )

            val exported = JsonBackupExporter.export(sourceDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            // Inverte propositalmente a ordem no JSON para listar subcategoria ANTES da categoria principal
            val root = JSONObject(exported.jsonContent)
            val cols = root.getJSONObject("collections")
            val cats = cols.getJSONArray("productCategories")
            val reversedCats = JSONArray()
            for (i in cats.length() - 1 downTo 0) {
                reversedCats.put(cats.getJSONObject(i))
            }
            // Confirma que a subcategoria está no índice 0
            assertEquals("a-sub", reversedCats.getJSONObject(0).getString("id"))
            cols.put("productCategories", reversedCats)

            val readResult = JsonBackupReader.readAndValidate(root.toString())
            assertTrue(readResult.errors.joinToString { it.message }, readResult.isValid)

            val plan = JsonBackupMergePlanner.plan(readResult, targetDb)
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, targetDb, emptyMap())
            assertTrue(validation.isReadyForNextPhase)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, targetDb)
            assertTrue(preview.isSafeToApply)

            val execResult = JsonBackupMergeExecutor.execute(
                readResult, plan, emptyMap(), validation, preview, preview.executionContract, true, targetDb,
            )
            assertTrue(execResult.failureMessage ?: "", execResult.success)
            val catsInDb = targetDb.productDao().getAllCategories().associateBy { it.id }
            assertEquals(2, catsInDb.size)
            assertNull(catsInDb["z-parent"]?.parentId)
            assertEquals("z-parent", catsInDb["a-sub"]?.parentId)
            assertEquals("a-sub", targetDb.productDao().getAllProducts().single().subcategoryId)
        } finally {
            sourceDb.close()
            targetDb.close()
        }
    }

    @Test
    fun test04_categoryNormalizedNameCollisionBlocksUseBackupAndPreservesLocalWithoutAutoRemap() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            seedBasePerson(backupDb)
            seedBasePerson(localDb)
            // Mesma normalizedName ("decoracao"), IDs distintos
            seedCategory(localDb, id = "cat-local", name = "Decoração", norm = "decoracao")
            seedCategory(backupDb, id = "cat-backup", name = "Decoração", norm = "decoracao")
            seedProduct(
                db = backupDb,
                id = "prod-bkp",
                name = "Vaso Novo",
                priceCents = 2_000L,
                personId = "person-1",
                categoryId = "cat-backup",
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_400_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.isValid)

            val comparison = JsonBackupComparator.compare(readResult, localDb)
            assertTrue(
                comparison.relationalRisks.any {
                    it.type == RelationalRiskType.UNIQUE_NAME_COLLISION &&
                        it.collection == BackupCollectionType.PRODUCT_CATEGORIES
                },
            )

            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val catConflict = plan.conflictItems.single { it.collection == BackupCollectionType.PRODUCT_CATEGORIES }
            assertEquals("cat-backup", catConflict.recordId)
            assertEquals(MergeConflictKind.IDENTITY_NAME_COLLISION, catConflict.kind)
            assertEquals("cat-local", catConflict.collidingLocalRecordId)
            // Produto dependente da categoria em conflito fica PENDING_DEPENDENCY
            assertEquals(
                MergePlanAction.PENDING_DEPENDENCY,
                plan.planFor(BackupCollectionType.PRODUCTS).actionForRecord("prod-bkp"),
            )

            // Tentar USE_BACKUP na colisão de identidade deve ser BLOQUEADO (sem sobrescrever ou silenciar erro)
            val useBackupValidation = JsonBackupMergeDecisionValidator.validate(
                plan,
                readResult,
                localDb,
                mapOf("productCategories:cat-backup" to MergeConflictResolution.USE_BACKUP),
            )
            assertFalse(useBackupValidation.isReadyForNextPhase)
            assertTrue(
                useBackupValidation.blockedOperations.any {
                    it.type == DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED &&
                        it.collection == BackupCollectionType.PRODUCT_CATEGORIES
                },
            )
            // E o produto que dependia de cat-backup também fica bloqueado por dependência não resolvida
            assertTrue(
                useBackupValidation.blockedOperations.any {
                    it.type == DecisionValidationIssueType.UNRESOLVED_DEPENDENCY &&
                        it.collection == BackupCollectionType.PRODUCTS &&
                        it.recordId == "prod-bkp"
                },
            )

            // Escolher KEEP_LOCAL na colisão preserva cat-local sem remapeamento automático;
            // como prod-bkp referencia cat-backup (não inserida), o validador bloqueia prod-bkp em vez de gravar FK inválida!
            val keepLocalValidation = JsonBackupMergeDecisionValidator.validate(
                plan,
                readResult,
                localDb,
                mapOf("productCategories:cat-backup" to MergeConflictResolution.KEEP_LOCAL),
            )
            assertFalse(keepLocalValidation.isReadyForNextPhase)
            assertTrue(
                keepLocalValidation.blockedOperations.any {
                    it.type == DecisionValidationIssueType.UNRESOLVED_DEPENDENCY &&
                        it.recordId == "prod-bkp"
                },
            )
            // Banco local permanece intocado
            assertEquals(listOf("cat-local"), localDb.productDao().getAllCategories().map { it.id })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test05_invalidOrOrphanCategoryAndSubcategoryReferencesAreRejectedByReaderAndValidator() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            seedBasePerson(db)
            seedCategory(db, id = "cat-1", name = "Decoração", norm = "decoracao")
            seedCategory(db, id = "cat-2", name = "Chaveiros", norm = "chaveiros")
            seedCategory(db, id = "sub-2", name = "Geek", norm = "geek", parentId = "cat-2")
            seedProduct(
                db = db,
                id = "prod-1",
                name = "Peça Teste",
                priceCents = 1_500L,
                personId = "person-1",
                categoryId = "cat-2",
                subcategoryId = "sub-2",
            )
            val baseJson = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_500_000L).jsonContent

            // Caso A: produto referencia categoryId inexistente
            val missingCatJson = JSONObject(baseJson).apply {
                val p = getJSONObject("collections").getJSONArray("products").getJSONObject(0)
                p.put("categoryId", "cat-inexistente")
                p.put("subcategoryId", JSONObject.NULL)
            }
            val missingCatRead = JsonBackupReader.readAndValidate(missingCatJson.toString())
            assertFalse(missingCatRead.isValid)
            assertTrue(missingCatRead.errors.any { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR })

            // Caso B: produto define subcategoryId pertencente a outra categoria (cat-1 != parentId cat-2)
            val wrongParentJson = JSONObject(baseJson).apply {
                val p = getJSONObject("collections").getJSONArray("products").getJSONObject(0)
                p.put("categoryId", "cat-1")
                p.put("subcategoryId", "sub-2")
            }
            val wrongParentRead = JsonBackupReader.readAndValidate(wrongParentJson.toString())
            assertFalse(wrongParentRead.isValid)
            assertTrue(wrongParentRead.errors.any { it.code == JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR })

            // Caso C: subcategoria com parentId inexistente
            val orphanSubJson = JSONObject(baseJson).apply {
                val cats = getJSONObject("collections").getJSONArray("productCategories")
                for (i in 0 until cats.length()) {
                    val obj = cats.getJSONObject(i)
                    if (obj.getString("id") == "sub-2") {
                        obj.put("parentId", "pai-inexistente")
                    }
                }
            }
            val orphanSubRead = JsonBackupReader.readAndValidate(orphanSubJson.toString())
            assertFalse(orphanSubRead.isValid)

            // Caso D: contagem divergente de productCategories
            val badCountJson = JSONObject(baseJson).apply {
                getJSONObject("counts").put("productCategories", 99)
            }
            val badCountRead = JsonBackupReader.readAndValidate(badCountJson.toString())
            assertFalse(badCountRead.isValid)
            assertTrue(badCountRead.errors.any { it.code == JsonBackupErrorCode.COUNT_MISMATCH })

            // Caso E: validateSnapshot direto rejeita subcategoryId sem categoryId
            val snapshot = JsonBackupExporter.captureConsistentSnapshot(db)
            val invalidSnapshot = snapshot.copy(
                products = snapshot.products.map { it.copy(categoryId = null, subcategoryId = "sub-2") },
            )
            try {
                JsonBackupExporter.validateSnapshot(invalidSnapshot)
                fail("Deveria rejeitar produto com subcategoryId sem categoryId")
            } catch (expected: JsonBackupValidationException) {
                assertTrue(
                    "Mensagem inesperada: ${expected.message}",
                    expected.message.orEmpty().contains("subcategoria", ignoreCase = true),
                )
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun test06_fingerprintsAndBackupSignatureDetectCategoryAndProductCategoryMutations() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            seedBasePerson(db)
            seedCategory(db, id = "cat-1", name = "Decoração", norm = "decoracao")
            seedCategory(db, id = "cat-2", name = "Colecionáveis", norm = "colecionaveis")
            seedProduct(
                db = db,
                id = "prod-1",
                name = "Busto 3D",
                priceCents = 5_000L,
                personId = "person-1",
                categoryId = "cat-1",
            )

            val snapBase = JsonBackupExporter.captureConsistentSnapshot(db)
            val fpBase = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snapBase)

            // Alterar apenas categoryId do produto deve mudar o fingerprint
            val snapChangedProdCat = snapBase.copy(
                products = snapBase.products.map { it.copy(categoryId = "cat-2") },
            )
            val fpChangedProdCat = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snapChangedProdCat)
            assertNotEquals(fpBase, fpChangedProdCat)

            // Alterar apenas o nome de uma categoria deve mudar o fingerprint
            val snapChangedCat = snapBase.copy(
                productCategories = snapBase.productCategories.map {
                    if (it.id == "cat-1") it.copy(name = "Decoração Premium") else it
                },
            )
            val fpChangedCat = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(snapChangedCat)
            assertNotEquals(fpBase, fpChangedCat)

            // BackupIdentitySignature também deve detectar alteração em productCategories e product.categoryId
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_600_000L)
            val readBase = JsonBackupReader.readAndValidate(exported.jsonContent).validatedData!!
            val sigBase = JsonBackupMergePreviewBuilder.computeBackupIdentitySignature(readBase)

            val alteredJson = JSONObject(exported.jsonContent).apply {
                getJSONObject("collections").getJSONArray("products").getJSONObject(0).put("categoryId", "cat-2")
            }.toString()
            val readAltered = JsonBackupReader.readAndValidate(alteredJson).validatedData!!
            val sigAltered = JsonBackupMergePreviewBuilder.computeBackupIdentitySignature(readAltered)
            assertNotEquals(sigBase.backupPayloadFingerprint, sigAltered.backupPayloadFingerprint)
        } finally {
            db.close()
        }
    }

    @Test
    fun test07_backwardCompatibilityWithLegacyBackupWithoutProductCategories() = runBlocking<Unit>(Dispatchers.IO) {
        val sourceDb = newInMemoryDb()
        val targetDb = newInMemoryDb()
        try {
            seedBasePerson(sourceDb)
            seedProduct(
                db = sourceDb,
                id = "prod-legacy",
                name = "Produto Antigo",
                priceCents = 2_500L,
                personId = "person-1",
                categoryId = null,
                subcategoryId = null,
            )

            // Simula backup antigo (sem productCategories em counts/collections e sem categoryId/subcategoryId em products)
            val exported = JsonBackupExporter.export(sourceDb, ZoneId.of("UTC"), 1_730_000_700_000L)
            val legacyJson = JSONObject(exported.jsonContent).apply {
                getJSONObject("counts").remove("productCategories")
                val cols = getJSONObject("collections")
                cols.remove("productCategories")
                val prod = cols.getJSONArray("products").getJSONObject(0)
                prod.remove("categoryId")
                prod.remove("subcategoryId")
            }.toString()

            val readResult = JsonBackupReader.readAndValidate(legacyJson)
            assertTrue(readResult.errors.joinToString { it.message }, readResult.isValid)
            assertTrue(readResult.validatedData!!.productCategories.isEmpty())
            assertNull(readResult.validatedData!!.products.single().categoryId)

            val plan = JsonBackupMergePlanner.plan(readResult, targetDb)
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, targetDb, emptyMap())
            assertTrue(validation.isReadyForNextPhase)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, targetDb)
            assertTrue(preview.isSafeToApply)

            val execResult = JsonBackupMergeExecutor.execute(
                readResult, plan, emptyMap(), validation, preview, preview.executionContract, true, targetDb,
            )
            assertTrue(execResult.failureMessage ?: "", execResult.success)
            assertEquals(1, targetDb.productDao().getAllProducts().size)
            assertEquals(0, targetDb.productDao().getAllCategories().size)
        } finally {
            sourceDb.close()
            targetDb.close()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun test08_conflictResolutionAsyncRevalidationSetsBusyStateAndAppliesAfterCompletion() {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        val (exported, localSnapshot) = try {
            runBlocking(Dispatchers.IO) {
                seedBasePerson(backupDb, id = "p1", name = "Ana Backup", norm = "ana backup")
                seedBasePerson(localDb, id = "p1", name = "Ana Local", norm = "ana local")
                val exp = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_800_000L)
                val snap = JsonBackupExporter.captureConsistentSnapshot(localDb)
                exp to snap
            }
        } finally {
            backupDb.close()
            localDb.close()
        }

        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(testDispatcher)
            try {
                val validationGate = CompletableDeferred<Unit>()
                var validationInvocations = 0

                val vm = ImportExportViewModel(
                    generateBackup = { exported },
                    compareWithDatabase = { readRes ->
                        val comp = JsonBackupComparator.compareSnapshots(readRes.validatedData!!, localSnapshot)
                        val plan = JsonBackupMergePlanner.planFromSnapshots(readRes.validatedData!!, localSnapshot, comp)
                        val initVal = JsonBackupMergeDecisionValidator.validateFromSnapshot(plan, readRes.validatedData!!, localSnapshot, emptyMap())
                        BackupComparisonOutcome(
                            comparison = comp,
                            localStateFingerprint = plan.preconditions.expectedLocalStateFingerprint,
                            mergePlan = plan,
                            initialValidation = initVal,
                            unitDetailsByKey = BackupConflictFormatter.buildUnitVisualDetails(readRes.validatedData!!, localSnapshot, plan),
                        )
                    },
                    validateMergeDecisions = { plan, validatedBackup, decisions ->
                        validationInvocations += 1
                        validationGate.await()
                        JsonBackupMergeDecisionValidator.validateFromSnapshot(plan, validatedBackup, localSnapshot, decisions)
                    },
                    ioDispatcher = testDispatcher,
                )

                vm.requestSelectBackupJson()
                vm.onImportFileSelected(Uri.parse("content://mixvendas3d/test.json")) {
                    exported.jsonContent.toByteArray(Charsets.UTF_8)
                }
                advanceUntilIdle()

                assertEquals(BackupAnalysisPhase.ANALYSIS_READY, vm.uiState.value.analysisPhase)
                val initialUnit = vm.uiState.value.conflictSection!!.units.single()
                assertNull(initialUnit.selectedResolution)

                // Dispara seleção assíncrona: enquanto validationGate não conclui, isValidatingDecisions deve ficar true
                vm.onSelectConflictResolution(initialUnit.unitKey, MergeConflictResolution.USE_BACKUP)
                testScheduler.runCurrent()

                assertTrue("isValidatingDecisions deve estar true durante validação assíncrona", vm.uiState.value.isValidatingDecisions)
                assertTrue("isBusy deve bloquear novas interações durante validação", vm.uiState.value.isBusy)

                // Clique concorrente enquanto valida deve ser ignorado com segurança
                vm.onSelectConflictResolution(initialUnit.unitKey, MergeConflictResolution.KEEP_LOCAL)
                testScheduler.runCurrent()
                assertEquals(1, validationInvocations)

                // Libera validação e aguarda conclusão efetiva
                validationGate.complete(Unit)
                advanceUntilIdle()

                assertFalse(vm.uiState.value.isValidatingDecisions)
                assertFalse(vm.uiState.value.isBusy)
                val updatedUnit = vm.uiState.value.conflictSection!!.units.single()
                assertEquals(MergeConflictResolution.USE_BACKUP, updatedUnit.selectedResolution)
                assertTrue(vm.uiState.value.conflictSection!!.isReadyForNextPhase)
            } finally {
                Dispatchers.resetMain()
            }
        }
    }
}
