package com.mixvendas3d.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MachineBrandRateDto
import com.mixvendas3d.app.domain.export.MachineDto
import com.mixvendas3d.app.domain.export.MachineInstallmentRateDto
import com.mixvendas3d.app.domain.export.MergeConflictKind
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergePlanAction
import com.mixvendas3d.app.domain.export.MergeUnitType
import com.mixvendas3d.app.domain.export.ProductDto
import com.mixvendas3d.app.domain.export.ProductParticipantDto
import com.mixvendas3d.app.domain.export.RecordComparisonStatus
import com.mixvendas3d.app.domain.export.ValidatedBackupData
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage6B3JsonBackupMergePlannerTest {

    private val app: MixVendasApplication
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
    }

    private fun newInMemoryDb(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private data class ScenarioRefs(
        val anaId: String,
        val brunoId: String,
        val productId: String,
        val machineId: String,
        val saleId: String,
    )

    private suspend fun populateStandardScenario(
        db: AppDatabase,
        idPrefix: String = "id",
        baseClock: Long = 1_730_000_000_000L,
        anaName: String = "Ana Silva",
        brunoName: String = "Bruno Costa",
        productPriceCents: Long = 10_000L,
        anaShareCents: Long = 6_000L,
        brunoShareCents: Long = 4_000L,
        machineName: String = "Stone Pro",
        debitRateBps: Int = 199,
        creditDefaultRateBps: Int = 450,
        visaRateBps: Int = 399,
        installment2AddonBps: Int = 150,
        saleFinalOverrideCents: Long = 18_000L,
    ): ScenarioRefs {
        var seq = 0
        val idGen = {
            seq += 1
            "$idPrefix-$seq"
        }
        val clock = { baseClock }

        db.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", baseClock))
        val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao(), idGen, clock)
        val machineRepo = RoomMachineRepository(
            machineDao = db.machineDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )
        val saleRepo = RoomSaleRepository(db, db.saleDao(), idGen, clock)

        val ana = catalogRepo.createPerson(anaName)
        val bruno = catalogRepo.createPerson(brunoName)

        val prod = catalogRepo.createProduct(
            name = "Dragão 3D",
            price = MoneyCents(productPriceCents),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(anaShareCents)),
                ProductParticipantDraft(bruno.id, MoneyCents(brunoShareCents)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = ana.id,
        )

        val machine = machineRepo.createMachine(
            MachineInputDraft(
                name = machineName,
                debitRateBps = debitRateBps,
                creditEnabled = true,
                creditDefaultRateBps = creditDefaultRateBps,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to visaRateBps),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to installment2AddonBps),
            ),
        )

        val sale = saleRepo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    finalAmountOverride = MoneyCents(saleFinalOverrideCents),
                    discountMode = SaleDiscountInput.SpecificProduct(prod.id),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = "Venda padrão",
                ),
            ),
        )
        return ScenarioRefs(
            anaId = ana.id,
            brunoId = bruno.id,
            productId = prod.id,
            machineId = machine.id,
            saleId = sale.id,
        )
    }

    @Test
    fun test01_emptyBackupAndEmptyDatabase() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            assertEquals(12, plan.collectionPlans.size)
            assertEquals(0, plan.totalAnalyzedRecords)
            assertEquals(0, plan.candidateAdditionsCount)
            assertEquals(0, plan.keptRecordsCount)
            assertEquals(0, plan.conflictsCount)
            assertEquals(0, plan.pendingDependenciesCount)
            assertEquals(0, plan.blockedOperationsCount)
            assertEquals(0, plan.relatedUnits.size)
            assertFalse(plan.hasAnyConflictsOrBlocks)
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
            assertTrue(plan.preconditions.requiresSingleTransaction)
            assertTrue(plan.preconditions.requiresFinalValidationBeforeCommit)
            assertTrue(plan.preconditions.requiresRollbackOnAnyFailure)
            assertEquals(64, plan.preconditions.expectedLocalStateFingerprint.length)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test02_newRecordsClassifiedAsAdd() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb)
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            assertEquals(15, plan.totalAnalyzedRecords)
            assertEquals(15, plan.candidateAdditionsCount)
            assertEquals(0, plan.keptRecordsCount)
            assertEquals(0, plan.conflictsCount)
            assertEquals(0, plan.pendingDependenciesCount)
            assertEquals(0, plan.blockedOperationsCount)
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PREFERENCES).actionForRecord("app_theme"))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test03_identicalRecordsClassifiedAsKeep() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same")
            populateStandardScenario(localDb, idPrefix = "same")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            assertEquals(15, plan.totalAnalyzedRecords)
            assertEquals(0, plan.candidateAdditionsCount)
            assertEquals(15, plan.keptRecordsCount)
            assertEquals(0, plan.conflictsCount)
            assertEquals(0, plan.pendingDependenciesCount)
            assertEquals(0, plan.blockedOperationsCount)
            assertTrue(plan.relatedUnits.all { it.action == MergePlanAction.KEEP })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test04_localOnlyRecordsPreservedAsKeep() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(localDb, idPrefix = "local")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            assertEquals(15, plan.totalAnalyzedRecords)
            assertEquals(0, plan.candidateAdditionsCount)
            assertEquals(15, plan.keptRecordsCount)
            assertEquals(15, plan.localOnlyPreservedCount)
            assertEquals(0, plan.conflictsCount)
            assertTrue(plan.relatedUnits.all { it.action == MergePlanAction.KEEP })
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test05_sameIdDifferentDataMarkedAsConflictWithoutAutoReplace() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val catalogB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_100_000L })
            val catalogL = RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-1" }, { 1_730_000_200_000L })
            catalogB.createPerson("Ana Backup")
            catalogL.createPerson("Ana Local")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            assertEquals(2, plan.conflictsCount)
            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.PREFERENCES).actionForRecord("app_theme"))
            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.PEOPLE).actionForRecord("p-1"))
            assertFalse(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
            assertEquals(listOf("preferences:app_theme", "people:p-1"), plan.preconditions.unresolvedConflictIds)
            assertTrue(plan.conflictItems.all { it.selectedResolution == null })

            val resolved = plan.withExplicitDecisions(
                mapOf(
                    "preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL,
                    "people:p-1" to MergeConflictResolution.USE_BACKUP,
                ),
            )
            assertTrue(resolved.preconditions.unresolvedConflictIds.isEmpty())
            assertTrue(resolved.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test06_sameNormalizedNameWithDistinctIdsFlagsIdentityCollision() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val catalogB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "person-backup" }, { 1_730_000_100_000L })
            val catalogL = RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "person-local" }, { 1_730_000_200_000L })
            catalogB.createPerson("Ana Silva")
            catalogL.createPerson("  ana silva  ")

            val machineRepoB = RoomMachineRepository(backupDb.machineDao(), idGenerator = { "mach-backup" }, clockMillis = { 1_730_000_100_000L })
            val machineRepoL = RoomMachineRepository(localDb.machineDao(), idGenerator = { "mach-local" }, clockMillis = { 1_730_000_200_000L })
            machineRepoB.createMachine(MachineInputDraft("Stone Pro", 199, true, 450, false, emptyMap(), false, emptyMap()))
            machineRepoL.createMachine(MachineInputDraft("  stone pro ", 210, true, 480, false, emptyMap(), false, emptyMap()))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.PEOPLE).actionForRecord("person-backup"))
            assertEquals(MergePlanAction.KEEP, plan.planFor(BackupCollectionType.PEOPLE).actionForRecord("person-local"))
            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.MACHINES).actionForRecord("mach-backup"))
            assertEquals(MergePlanAction.KEEP, plan.planFor(BackupCollectionType.MACHINES).actionForRecord("mach-local"))

            val personCollision = plan.conflictItems.first { it.collection == BackupCollectionType.PEOPLE }
            assertEquals(MergeConflictKind.IDENTITY_NAME_COLLISION, personCollision.kind)
            assertEquals("person-local", personCollision.collidingLocalRecordId)

            val machineCollision = plan.conflictItems.first { it.collection == BackupCollectionType.MACHINES }
            assertEquals(MergeConflictKind.IDENTITY_NAME_COLLISION, machineCollision.kind)
            assertEquals("mach-local", machineCollision.collidingLocalRecordId)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test07_newProductAndParticipantsPlannedAsAtomicAddUnit() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val catalogB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_100_000L })
            val p1 = catalogB.createPerson("Ana")
            val catalogB2 = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-2" }, { 1_730_000_100_000L })
            val p2 = catalogB2.createPerson("Bruno")
            val catalogProd = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-1" }, { 1_730_000_100_000L })
            catalogProd.createProduct(
                name = "Vaso 3D",
                price = MoneyCents(9_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_200_000L)
            val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PRODUCTS).actionForRecord("prod-1"))
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-1:p-1"))
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-1:p-2"))
            val unit = plan.relatedUnits.single { it.unitType == MergeUnitType.PRODUCT && it.unitId == "prod-1" }
            assertEquals(MergePlanAction.ADD, unit.action)
            assertTrue(unit.isAtomicUnit)
            assertEquals(listOf("prod-1:p-1", "prod-1:p-2"), unit.memberRecordIdsByCollection[BackupCollectionType.PRODUCT_PARTICIPANTS])
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test08_productWithConflictingSplitBlocksPartialParticipantMerge() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Backup: prod-1 com 3 participantes (6000 + 2500 + 1500 = 10000)
            // Local:  prod-1 com 2 participantes (6000 + 4000 = 10000) -> p3 é novo no backup
            val catB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
            catB.createPerson("Ana")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-2" }, { 1_730_000_000_000L }).createPerson("Bruno")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-3" }, { 1_730_000_000_000L }).createPerson("Carla")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-1" }, { 1_730_000_000_000L }).createProduct(
                name = "Luminária",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p-1", MoneyCents(6_000L)),
                    ProductParticipantDraft("p-2", MoneyCents(2_500L)),
                    ProductParticipantDraft("p-3", MoneyCents(1_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val catL = RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
            catL.createPerson("Ana")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-2" }, { 1_730_000_000_000L }).createPerson("Bruno")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "prod-1" }, { 1_730_000_000_000L }).createProduct(
                name = "Luminária",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p-1", MoneyCents(6_000L)),
                    ProductParticipantDraft("p-2", MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertEquals(MergePlanAction.PENDING_DEPENDENCY, plan.planFor(BackupCollectionType.PRODUCTS).actionForRecord("prod-1"))
            assertEquals(MergePlanAction.PENDING_DEPENDENCY, plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-1:p-1"))
            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-1:p-2"))
            assertEquals(MergePlanAction.BLOCKED, plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-1:p-3"))

            val unit = plan.relatedUnits.single { it.unitType == MergeUnitType.PRODUCT && it.unitId == "prod-1" }
            assertEquals(MergePlanAction.CONFLICT, unit.action)
            assertTrue(unit.blockReasons.isNotEmpty())
            assertFalse(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test09_machineWithConflictingRatesAndOver100PercentPartialMergeBlocked() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Backup tem crédito padrão 85% e 2x addon 10% (85 + 10 = 95% válido no backup)
            // Local tem crédito padrão 95% (e sem 2x), então adicionar apenas 2x (10%) somaria 105% -> bloqueado!
            val repoB = RoomMachineRepository(backupDb.machineDao(), idGenerator = { "m-1" }, clockMillis = { 1_730_000_000_000L })
            repoB.createMachine(
                MachineInputDraft(
                    name = "PagSeguro",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 8_500,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 8_500),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(2 to 1_000),
                ),
            )
            val repoL = RoomMachineRepository(localDb.machineDao(), idGenerator = { "m-1" }, clockMillis = { 1_730_000_000_000L })
            repoL.createMachine(
                MachineInputDraft(
                    name = "PagSeguro",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 9_500,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 9_500),
                    installmentsEnabled = true,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.MACHINES).actionForRecord("m-1"))
            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.MACHINE_BRAND_RATES).actionForRecord("m-1:VISA"))
            assertEquals(MergePlanAction.BLOCKED, plan.planFor(BackupCollectionType.MACHINE_INSTALLMENT_RATES).actionForRecord("m-1:2"))
            val unit = plan.relatedUnits.single { it.unitType == MergeUnitType.MACHINE && it.unitId == "m-1" }
            assertEquals(MergePlanAction.CONFLICT, unit.action)
            assertTrue(unit.blockReasons.any { it.contains("100,00%") })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test10_newMultiProductSalePlannedAsAtomicFourTableAddUnit() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            var seq = 0
            val idGen = { seq += 1; "mp-$seq" }
            val clock = { 1_730_000_000_000L }
            val catalog = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), idGen, clock)
            val machineRepo = RoomMachineRepository(backupDb.machineDao(), idGenerator = idGen, clockMillis = clock)
            val saleRepo = RoomSaleRepository(backupDb, backupDb.saleDao(), idGen, clock)

            val ana = catalog.createPerson("Ana")
            val bruno = catalog.createPerson("Bruno")
            val p1 = catalog.createProduct(
                "Peça A", MoneyCents(10_000L), true,
                listOf(ProductParticipantDraft(ana.id, MoneyCents(6_000L)), ProductParticipantDraft(bruno.id, MoneyCents(4_000L))),
                FeeResponsibilityMode.PROPORTIONAL, null,
            )
            val p2 = catalog.createProduct(
                "Peça B", MoneyCents(5_000L), true,
                listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                FeeResponsibilityMode.INDIVIDUAL, ana.id,
            )
            val mach = machineRepo.createMachine(
                MachineInputDraft("Stone", 199, true, 399, false, emptyMap(), false, emptyMap()),
            )
            val sale = saleRepo.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(p1, 1), SaleItemInput(p2, 2)),
                        finalAmountOverride = null,
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.PIX,
                        machine = null,
                        cardBrand = null,
                        installments = null,
                        note = "Multi-itens",
                    ),
                ),
            )
            assertEquals("Stone", mach.name)

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.SALES).actionForRecord(sale.id))
            assertEquals(2, plan.planFor(BackupCollectionType.SALE_ITEMS).addCount)
            assertEquals(3, plan.planFor(BackupCollectionType.SALE_ITEM_PARTICIPANTS).addCount)
            assertEquals(2, plan.planFor(BackupCollectionType.SALE_PERSON_TOTALS).addCount)

            val saleUnit = plan.relatedUnits.single { it.unitType == MergeUnitType.SALE && it.unitId == sale.id }
            assertEquals(MergePlanAction.ADD, saleUnit.action)
            assertEquals(2, saleUnit.memberRecordIdsByCollection[BackupCollectionType.SALE_ITEMS]?.size)
            assertEquals(3, saleUnit.memberRecordIdsByCollection[BackupCollectionType.SALE_ITEM_PARTICIPANTS]?.size)
            assertEquals(2, saleUnit.memberRecordIdsByCollection[BackupCollectionType.SALE_PERSON_TOTALS]?.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test11_identicalSalePlannedAsKeepWithoutDuplication() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refsB = populateStandardScenario(backupDb, idPrefix = "same")
            val refsL = populateStandardScenario(localDb, idPrefix = "same")
            assertEquals(refsB.saleId, refsL.saleId)

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertEquals(MergePlanAction.KEEP, plan.planFor(BackupCollectionType.SALES).actionForRecord(refsB.saleId))
            assertEquals(0, plan.planFor(BackupCollectionType.SALES).addCount)
            assertEquals(0, plan.planFor(BackupCollectionType.SALE_ITEMS).addCount)
            assertEquals(0, plan.planFor(BackupCollectionType.SALE_ITEM_PARTICIPANTS).addCount)
            assertEquals(0, plan.planFor(BackupCollectionType.SALE_PERSON_TOTALS).addCount)
            val saleUnit = plan.relatedUnits.single { it.unitType == MergeUnitType.SALE && it.unitId == refsB.saleId }
            assertEquals(MergePlanAction.KEEP, saleUnit.action)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test12_saleSameIdDivergentConflictsWithoutMixingAndDistinctIdsSameAmountDateStayAdd() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                // Mesma venda por ID com valores finais divergentes
                val refsSameB = populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
                populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

                // Adiciona outra venda válida no backup com mesmos valor/data de uma venda local, porém IDs distintos
                var seqL = 0
                var seqB = 0
                val repoL = RoomSaleRepository(localDb, localDb.saleDao(), { seqL += 1; "sale-local-extra-$seqL" }, { 1_730_000_000_000L })
                val repoB = RoomSaleRepository(backupDb, backupDb.saleDao(), { seqB += 1; "sale-backup-extra-$seqB" }, { 1_730_000_000_000L })

                val pB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "x-prod-b" }, { 1L })
                val pAnaB = backupDb.personDao().getAllPeople().first()
                val prodB = backupDb.productDao().getAllProducts().first()
                val extraProductB = pB.createProduct(
                    "Suporte", MoneyCents(5_000L), true,
                    listOf(ProductParticipantDraft(pAnaB.id, MoneyCents(5_000L))),
                    FeeResponsibilityMode.PROPORTIONAL, null,
                )
                val localAna = localDb.personDao().getAllPeople().first()
                val localProdExtra = RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "x-prod-l" }, { 1L })
                    .createProduct(
                        "Suporte Local", MoneyCents(5_000L), true,
                        listOf(ProductParticipantDraft(localAna.id, MoneyCents(5_000L))),
                        FeeResponsibilityMode.PROPORTIONAL, null,
                    )
                assertNotEquals(prodB.id, extraProductB.id)

                val extraSaleB = repoB.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(extraProductB, 1)),
                            finalAmountOverride = null,
                            discountMode = SaleDiscountInput.Proportional,
                            paymentMethod = SalePaymentMethod.CASH,
                            machine = null,
                            cardBrand = null,
                            installments = null,
                            note = "Dinheiro",
                        ),
                    ),
                )
                val extraSaleL = repoL.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(localProdExtra, 1)),
                            finalAmountOverride = null,
                            discountMode = SaleDiscountInput.Proportional,
                            paymentMethod = SalePaymentMethod.CASH,
                            machine = null,
                            cardBrand = null,
                            installments = null,
                            note = "Dinheiro",
                        ),
                    ),
                )

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
                val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

                // Venda com mesmo ID diverge -> CONFLICT
                assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.SALES).actionForRecord(refsSameB.saleId))
                val conflictUnit = plan.relatedUnits.single { it.unitType == MergeUnitType.SALE && it.unitId == refsSameB.saleId }
                assertEquals(MergePlanAction.CONFLICT, conflictUnit.action)

                // Vendas com IDs diferentes (mesmo valor e mesma data) não são deduplicadas nem confundidas
                assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.SALES).actionForRecord(extraSaleB.id))
                assertEquals(MergePlanAction.KEEP, plan.planFor(BackupCollectionType.SALES).actionForRecord(extraSaleL.id))
                assertEquals(
                    RecordComparisonStatus.LOCAL_ONLY,
                    plan.planFor(BackupCollectionType.SALES).records.first { it.recordId == extraSaleL.id }.comparisonStatus,
                )
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test13_newProductDependingOnConflictingPersonMarkedPendingDependency() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Ana tem colisão de nome entre IDs distintos ("ana-b" vs "ana-l")
            val anaB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "ana-b" }, { 1_730_000_000_000L })
                .createPerson("Ana Silva")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "ana-l" }, { 1_730_000_000_000L })
                .createPerson("Ana Silva")

            // Produto novo no backup depende dessa pessoa em conflito
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-dep" }, { 1_730_000_100_000L })
                .createProduct(
                    name = "Miniatura",
                    price = MoneyCents(4_500L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(anaB.id, MoneyCents(4_500L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_200_000L)
            val plan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertEquals(MergePlanAction.CONFLICT, plan.planFor(BackupCollectionType.PEOPLE).actionForRecord("ana-b"))
            assertEquals(MergePlanAction.PENDING_DEPENDENCY, plan.planFor(BackupCollectionType.PRODUCTS).actionForRecord("prod-dep"))
            assertEquals(
                MergePlanAction.PENDING_DEPENDENCY,
                plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-dep:ana-b"),
            )
            assertTrue(plan.pendingDependencies.any { it.recordId == "prod-dep" && it.dependsOnRecordId == "ana-b" })
            val prodUnit = plan.relatedUnits.single { it.unitType == MergeUnitType.PRODUCT && it.unitId == "prod-dep" }
            assertEquals(MergePlanAction.PENDING_DEPENDENCY, prodUnit.action)
            assertEquals(listOf("ana-b"), prodUnit.dependencyUnitOrRecordIds)
            assertFalse(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test14_inconsistentProductSplitAndInvalidMachineRatesBlockedForMerge() = runBlocking<Unit>(Dispatchers.IO) {
        val localDb = newInMemoryDb()
        try {
            val invalidValidated = ValidatedBackupData(
                format = "MIX_VENDAS_3D_BACKUP",
                backupSchemaVersion = 1,
                applicationId = "com.mixvendas3d.app",
                roomSchemaVersion = 4,
                exportedAtEpochMs = 1_730_000_000_000L,
                preferences = emptyList(),
                people = emptyList(),
                products = listOf(
                    ProductDto(
                        id = "prod-broken",
                        name = "Produto Quebrado",
                        priceCents = 10_000L,
                        active = true,
                        feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL.storageKey,
                        feeOwnerPersonId = null,
                        createdAtEpochMs = 1_730_000_000_000L,
                        updatedAtEpochMs = 1_730_000_000_000L,
                    ),
                ),
                productParticipants = listOf(
                    ProductParticipantDto(
                        productId = "prod-broken",
                        personId = "p-missing",
                        amountCents = 7_000L,
                        sortOrder = 0,
                    ),
                ),
                machines = listOf(
                    MachineDto(
                        id = "mach-broken",
                        name = "Maquininha Invalida",
                        normalizedName = "maquininha invalida",
                        debitRateBps = 199,
                        creditEnabled = true,
                        creditDefaultRateBps = 9_500,
                        brandRatesEnabled = true,
                        installmentsEnabled = true,
                        createdAtEpochMs = 1_730_000_000_000L,
                        updatedAtEpochMs = 1_730_000_000_000L,
                    ),
                ),
                machineBrandRates = listOf(
                    MachineBrandRateDto("mach-broken", CardBrand.VISA.storageKey, 9_500),
                ),
                machineInstallmentRates = listOf(
                    MachineInstallmentRateDto("mach-broken", 2, 1_000),
                ),
                sales = emptyList(),
                saleItems = emptyList(),
                saleItemParticipants = emptyList(),
                salePersonTotals = emptyList(),
                declaredCounts = emptyMap(),
            )

            val plan = JsonBackupMergePlanner.plan(invalidValidated, localDb)

            assertEquals(MergePlanAction.BLOCKED, plan.planFor(BackupCollectionType.PRODUCTS).actionForRecord("prod-broken"))
            assertEquals(
                MergePlanAction.BLOCKED,
                plan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).actionForRecord("prod-broken:p-missing"),
            )
            assertEquals(MergePlanAction.BLOCKED, plan.planFor(BackupCollectionType.MACHINES).actionForRecord("mach-broken"))
            assertEquals(
                MergePlanAction.BLOCKED,
                plan.planFor(BackupCollectionType.MACHINE_INSTALLMENT_RATES).actionForRecord("mach-broken:2"),
            )
            assertTrue(plan.blockReasons.size >= 2)
            assertFalse(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            localDb.close()
        }
    }

    @Test
    fun test15_validHistoricalSaleWithDeletedCatalogEntriesRemainsAdd() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(backupDb, idPrefix = "hist")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)

            // Simula backup histórico válido onde pessoas, produtos e maquininhas do catálogo foram excluídos após a venda
            val root = JSONObject(exported.jsonContent)
            val collections = root.getJSONObject("collections")
            collections.put("people", JSONArray())
            collections.put("products", JSONArray())
            collections.put("productParticipants", JSONArray())
            collections.put("machines", JSONArray())
            collections.put("machineBrandRates", JSONArray())
            collections.put("machineInstallmentRates", JSONArray())
            val counts = root.getJSONObject("counts")
            counts.put("people", 0)
            counts.put("products", 0)
            counts.put("productParticipants", 0)
            counts.put("machines", 0)
            counts.put("machineBrandRates", 0)
            counts.put("machineInstallmentRates", 0)

            val readResult = JsonBackupReader.readAndValidate(root.toString())
            assertTrue(readResult.isValid)

            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            assertEquals(MergePlanAction.ADD, plan.planFor(BackupCollectionType.SALES).actionForRecord(refs.saleId))
            assertEquals(1, plan.planFor(BackupCollectionType.SALE_ITEMS).addCount)
            assertEquals(2, plan.planFor(BackupCollectionType.SALE_ITEM_PARTICIPANTS).addCount)
            assertEquals(2, plan.planFor(BackupCollectionType.SALE_PERSON_TOTALS).addCount)
            assertEquals(0, plan.blockedOperationsCount)
            assertEquals(0, plan.pendingDependenciesCount)
            assertTrue(plan.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test16_planningIsDeterministicAcrossRepeatedRunsAndDifferentCollectionOrders() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                populateStandardScenario(backupDb, idPrefix = "b")
                populateStandardScenario(localDb, idPrefix = "l")

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
                val read1 = JsonBackupReader.readAndValidate(exported.jsonContent)
                val validated1 = read1.validatedData!!

                // Reordena coleções em ordem reversa para garantir independência de ordenação de entrada
                val reversedValidated = ValidatedBackupData(
                    format = validated1.format,
                    backupSchemaVersion = validated1.backupSchemaVersion,
                    applicationId = validated1.applicationId,
                    roomSchemaVersion = validated1.roomSchemaVersion,
                    exportedAtEpochMs = validated1.exportedAtEpochMs,
                    preferences = validated1.preferences.reversed(),
                    people = validated1.people.reversed(),
                    products = validated1.products.reversed(),
                    productParticipants = validated1.productParticipants.reversed(),
                    machines = validated1.machines.reversed(),
                    machineBrandRates = validated1.machineBrandRates.reversed(),
                    machineInstallmentRates = validated1.machineInstallmentRates.reversed(),
                    sales = validated1.sales.reversed(),
                    saleItems = validated1.saleItems.reversed(),
                    saleItemParticipants = validated1.saleItemParticipants.reversed(),
                    salePersonTotals = validated1.salePersonTotals.reversed(),
                    declaredCounts = validated1.declaredCounts,
                )

                val plan1 = JsonBackupMergePlanner.plan(read1, localDb)
                val plan2 = JsonBackupMergePlanner.plan(reversedValidated, localDb)
                assertEquals(plan1, plan2)
                assertEquals(plan1.hashCode(), plan2.hashCode())

                // Rejeita explicitamente resultado de leitura inválido
                val invalidRead = JsonBackupReader.readAndValidate("{ invalido")
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { JsonBackupMergePlanner.plan(invalidRead, localDb) }
                }
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test17_planningLeavesLocalRoomDatabaseCompletelyUnchanged() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "b", saleFinalOverrideCents = 18_000L)
            populateStandardScenario(localDb, idPrefix = "l", saleFinalOverrideCents = 17_000L)

            val localBefore = JsonBackupExporter.export(localDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(
                JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent,
            )

            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            assertEquals(6, plan.candidateAdditionsCount) // venda histórica (1+1+2+2) com IDs exclusivos do backup
            assertEquals(3, plan.conflictsCount) // colisões de nome em people (2) e machines (1)
            assertTrue(plan.pendingDependenciesCount > 0)

            val localAfter = JsonBackupExporter.export(localDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            assertEquals(localBefore.jsonContent, localAfter.jsonContent)

            val secondPlan = JsonBackupMergePlanner.plan(readResult, localDb)
            assertEquals(plan.preconditions.expectedLocalStateFingerprint, secondPlan.preconditions.expectedLocalStateFingerprint)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }
}
