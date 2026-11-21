package com.mixvendas3d.app

import android.database.sqlite.SQLiteConstraintException
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
import com.mixvendas3d.app.domain.export.ExecutorTestHooks
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeExecutionFailureReason
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage6C3JsonBackupMergeExecutorTest {

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

    private suspend fun preparePipeline(
        jsonContent: String,
        localDb: AppDatabase,
        decisions: Map<String, MergeConflictResolution> = emptyMap(),
    ) = run {
        val readResult = JsonBackupReader.readAndValidate(jsonContent)
        val plan = JsonBackupMergePlanner.plan(readResult, localDb)
        val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
        val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)
        PipelineBundle(readResult, plan, validation, preview)
    }

    private data class PipelineBundle(
        val readResult: com.mixvendas3d.app.domain.export.JsonBackupReadResult,
        val plan: com.mixvendas3d.app.domain.export.JsonBackupMergePlanResult,
        val validation: com.mixvendas3d.app.domain.export.MergeDecisionValidationResult,
        val preview: com.mixvendas3d.app.domain.export.JsonBackupMergePreviewResult,
    )

    @Test
    fun test01_restoreWithNoChanges() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same")
            populateStandardScenario(localDb, idPrefix = "same")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)
            val beforeSnapshot = JsonBackupExporter.captureConsistentSnapshot(localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                operationId = "op-no-changes",
            )

            assertTrue(result.success)
            assertFalse(result.rolledBack)
            assertEquals("op-no-changes", result.operationId)
            assertEquals(0, result.addedRecordsCount)
            assertEquals(0, result.replacedUnitsCount)
            assertEquals(15, result.preservedRecordsCount)
            assertEquals(0, result.ignoredRecordsCount)
            assertNull(result.failureReason)
            assertEquals(beforeSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test02_addPersonPersistedCorrectly() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-new" }, { 1_730_000_000_000L })
                .createPerson("Carla Dias")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(1, result.addedRecordsCount)
            assertEquals(1, result.finalTableCounts[BackupCollectionType.PEOPLE])
            val saved = localDb.personDao().getAllPeople().single()
            assertEquals("p-new", saved.id)
            assertEquals("Carla Dias", saved.name)
            assertEquals("carla dias", saved.normalizedName)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test03_addProductWithParticipantsPersistedCorrectly() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            var seq = 0
            val cat = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { seq += 1; "id-$seq" }, { 1_730_000_000_000L })
            val p1 = cat.createPerson("Ana")
            val p2 = cat.createPerson("Bruno")
            val prod = cat.createProduct(
                name = "Vaso 3D",
                price = MoneyCents(9_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(5_500L)),
                    ProductParticipantDraft(p2.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = p1.id,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(1, result.finalTableCounts[BackupCollectionType.PRODUCTS])
            assertEquals(2, result.finalTableCounts[BackupCollectionType.PRODUCT_PARTICIPANTS])
            val persistedProd = localDb.productDao().getWithParticipantsById(prod.id)!!
            assertEquals(9_500L, persistedProd.product.priceCents)
            assertEquals(2, persistedProd.participants.size)
            assertEquals(9_500L, persistedProd.participants.sumOf { it.participant.amountCents })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test04_addMachineWithRatesPersistedCorrectly() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val mach = RoomMachineRepository(backupDb.machineDao(), idGenerator = { "m-add" }, clockMillis = { 1_730_000_000_000L })
                .createMachine(
                    MachineInputDraft(
                        name = "Mercado Pago",
                        debitRateBps = 190,
                        creditEnabled = true,
                        creditDefaultRateBps = 420,
                        brandRatesEnabled = true,
                        brandRatesBps = mapOf(CardBrand.VISA to 390, CardBrand.ELO to 450),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(2 to 120, 3 to 240),
                    ),
                )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(1, result.finalTableCounts[BackupCollectionType.MACHINES])
            assertEquals(2, result.finalTableCounts[BackupCollectionType.MACHINE_BRAND_RATES])
            assertEquals(2, result.finalTableCounts[BackupCollectionType.MACHINE_INSTALLMENT_RATES])
            val persisted = localDb.machineDao().getWithRatesById(mach.id)!!
            assertEquals(190, persisted.machine.debitRateBps)
            assertEquals(mapOf("ELO" to 450, "VISA" to 390), persisted.brandRates.associate { it.brandKey to it.rateBps })
            assertEquals(mapOf(2 to 120, 3 to 240), persisted.installmentRates.associate { it.installments to it.addonBps })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test05_addSaleWithItemsParticipantsAndTotalsPersistedCorrectly() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(backupDb, idPrefix = "s-add")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(15, result.addedRecordsCount)
            assertEquals(1, result.finalTableCounts[BackupCollectionType.SALES])
            assertEquals(1, result.finalTableCounts[BackupCollectionType.SALE_ITEMS])
            assertEquals(2, result.finalTableCounts[BackupCollectionType.SALE_ITEM_PARTICIPANTS])
            assertEquals(2, result.finalTableCounts[BackupCollectionType.SALE_PERSON_TOTALS])
            val persistedSale = localDb.saleDao().getWithDetailsById(refs.saleId)!!
            assertEquals(18_000L, persistedSale.sale.finalAmountCents)
            assertEquals(2, persistedSale.personTotals.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test06_keepLocalPreservesLocalVersion() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_500_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_600_000L)
            val decisions = mapOf("preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL)
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(0, result.addedRecordsCount)
            assertEquals(0, result.replacedUnitsCount)
            assertEquals(1, result.ignoredRecordsCount)
            assertEquals("dark", localDb.preferenceDao().getByKey("app_theme")?.value)
            assertEquals(1_730_000_200_000L, localDb.preferenceDao().getByKey("app_theme")?.updatedAtEpochMs)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test07_useBackupForProductReplacesAtomicallyWithoutTouchingOthers() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.personDao().insert(com.mixvendas3d.app.data.local.entity.PersonEntity("p-1", "Ana", "ana", 0, 1_730_000_000_000L, 1_730_000_000_000L))
            backupDb.personDao().insert(com.mixvendas3d.app.data.local.entity.PersonEntity("p-2", "Bruno", "bruno", 1, 1_730_000_000_000L, 1_730_000_000_000L))
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-1" }, { 1_730_000_100_000L })
                .createProduct(
                    name = "Suporte V2",
                    price = MoneyCents(12_000L),
                    active = false,
                    participants = listOf(ProductParticipantDraft("p-1", MoneyCents(12_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )

            localDb.personDao().insert(com.mixvendas3d.app.data.local.entity.PersonEntity("p-1", "Ana", "ana", 0, 1_730_000_000_000L, 1_730_000_000_000L))
            localDb.personDao().insert(com.mixvendas3d.app.data.local.entity.PersonEntity("p-2", "Bruno", "bruno", 1, 1_730_000_000_000L, 1_730_000_000_000L))
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "prod-1" }, { 1_730_000_050_000L })
                .createProduct(
                    name = "Suporte V1",
                    price = MoneyCents(10_000L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft("p-1", MoneyCents(6_000L)),
                        ProductParticipantDraft("p-2", MoneyCents(4_000L)),
                    ),
                    feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                    feeOwnerPersonId = "p-2",
                )
            // Outro produto local não envolvido deve permanecer intocado
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "prod-local-2" }, { 1_730_000_060_000L })
                .createProduct(
                    name = "Outro Produto Local",
                    price = MoneyCents(5_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft("p-2", MoneyCents(5_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val prePlan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)
            val decisions = prePlan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(1, result.replacedUnitsCount)
            val replaced = localDb.productDao().getWithParticipantsById("prod-1")!!
            assertEquals("Suporte V2", replaced.product.name)
            assertEquals(12_000L, replaced.product.priceCents)
            assertFalse(replaced.product.active)
            assertNull(replaced.product.feeOwnerPersonId)
            assertEquals(1, replaced.participants.size)
            assertEquals("p-1", replaced.participants.single().participant.personId)

            val untouched = localDb.productDao().getWithParticipantsById("prod-local-2")!!
            assertEquals("Outro Produto Local", untouched.product.name)
            assertEquals(5_000L, untouched.product.priceCents)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test08_useBackupForMachineReplacesRatesAtomicallyWithoutTouchingOthers() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomMachineRepository(backupDb.machineDao(), idGenerator = { "m-1" }, clockMillis = { 1_730_000_000_000L })
                .createMachine(
                    MachineInputDraft(
                        name = "Stone Pro",
                        debitRateBps = 180,
                        creditEnabled = true,
                        creditDefaultRateBps = 400,
                        brandRatesEnabled = true,
                        brandRatesBps = mapOf(CardBrand.VISA to 350, CardBrand.MASTERCARD to 360),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(2 to 100, 3 to 200),
                    ),
                )
            RoomMachineRepository(localDb.machineDao(), idGenerator = { "m-1" }, clockMillis = { 1_730_000_000_000L })
                .createMachine(
                    MachineInputDraft(
                        name = "Stone Pro",
                        debitRateBps = 220,
                        creditEnabled = true,
                        creditDefaultRateBps = 500,
                        brandRatesEnabled = true,
                        brandRatesBps = mapOf(CardBrand.VISA to 480, CardBrand.ELO to 510),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(2 to 250),
                    ),
                )
            RoomMachineRepository(localDb.machineDao(), idGenerator = { "m-local-2" }, clockMillis = { 1_730_000_010_000L })
                .createMachine(
                    MachineInputDraft(
                        name = "Ton",
                        debitRateBps = 140,
                        creditEnabled = false,
                        creditDefaultRateBps = 0,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val prePlan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)
            val decisions = prePlan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            val replaced = localDb.machineDao().getWithRatesById("m-1")!!
            assertEquals(180, replaced.machine.debitRateBps)
            assertEquals(mapOf("MASTERCARD" to 360, "VISA" to 350), replaced.brandRates.associate { it.brandKey to it.rateBps })
            assertEquals(mapOf(2 to 100, 3 to 200), replaced.installmentRates.associate { it.installments to it.addonBps })

            val untouched = localDb.machineDao().getWithRatesById("m-local-2")!!
            assertEquals("Ton", untouched.machine.name)
            assertEquals(140, untouched.machine.debitRateBps)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test09_useBackupForSaleReplacesAtomicallyWithoutMixingItems() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 19_000L)
            populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)
            // Venda local adicional intocada
            val extraSale = RoomSaleRepository(localDb, localDb.saleDao(), { "extra-sale-${System.nanoTime()}" }, { 1_730_000_050_000L })
                .let { repo ->
                    val prod = localDb.productDao().getWithParticipantsById(refs.productId)!!
                    val domainProd = com.mixvendas3d.app.domain.models.Product(
                        id = prod.product.id,
                        name = prod.product.name,
                        price = MoneyCents(prod.product.priceCents),
                        active = prod.product.active,
                        feeResponsibilityMode = FeeResponsibilityMode.fromStorageKey(prod.product.feeResponsibilityMode),
                        feeOwnerPersonId = prod.product.feeOwnerPersonId,
                        participants = prod.participants.map {
                            com.mixvendas3d.app.domain.models.ProductParticipantSplit(
                                personId = it.participant.personId,
                                personName = it.person.name,
                                personColorIndex = it.person.colorIndex,
                                amount = MoneyCents(it.participant.amountCents),
                            )
                        },
                        createdAtEpochMs = prod.product.createdAtEpochMs,
                        updatedAtEpochMs = prod.product.updatedAtEpochMs,
                    )
                    repo.saveSale(
                        SaleFinancialEngine.calculate(
                            SaleCalculationInput(
                                items = listOf(SaleItemInput(domainProd, 1)),
                                finalAmountOverride = null,
                                discountMode = SaleDiscountInput.Proportional,
                                paymentMethod = SalePaymentMethod.PIX,
                                machine = null,
                                cardBrand = null,
                                installments = null,
                                note = "Local intocada",
                            ),
                        ),
                    )
                }

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val prePlan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)
            val decisions = prePlan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            val replacedSale = localDb.saleDao().getWithDetailsById(refs.saleId)!!
            assertEquals(19_000L, replacedSale.sale.finalAmountCents)
            val untouchedSale = localDb.saleDao().getWithDetailsById(extraSale.id)!!
            assertEquals(10_000L, untouchedSale.sale.finalAmountCents)
            assertEquals("Local intocada", untouchedSale.sale.note)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test10_ignoreImportSkipsBackupAndPreservesLocal() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_500_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_600_000L)
            val decisions = mapOf("preferences:app_theme" to MergeConflictResolution.IGNORE_IMPORT)
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(1, result.ignoredRecordsCount)
            assertEquals("dark", localDb.preferenceDao().getByKey("app_theme")?.value)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test11_localOnlyRecordsPreservedIntact() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val backupRefs = populateStandardScenario(backupDb, idPrefix = "bkp", anaName = "Ana Bkp", brunoName = "Bruno Bkp", machineName = "BkpMach")
            val localRefs = populateStandardScenario(localDb, idPrefix = "loc", anaName = "Ana Loc", brunoName = "Bruno Loc", machineName = "LocMach")
            localDb.preferenceDao().upsert(PreferenceEntity("local_only_pref", "on", 1_730_000_000_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)
            assertTrue(bundle.plan.conflictItems.isEmpty())

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertNotNull(localDb.preferenceDao().getByKey("local_only_pref"))
            assertNotNull(localDb.personDao().getById(localRefs.anaId))
            assertNotNull(localDb.productDao().getWithParticipantsById(localRefs.productId))
            assertNotNull(localDb.machineDao().getWithRatesById(localRefs.machineId))
            assertNotNull(localDb.saleDao().getWithDetailsById(localRefs.saleId))
            assertNotNull(localDb.saleDao().getWithDetailsById(backupRefs.saleId))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test12_outdatedPlanRejectedWithoutModifyingDb() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-bkp" }, { 1_730_000_000_000L })
                .createPerson("Novo Backup")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            // Altera o banco local após gerar plano e prévia
            localDb.preferenceDao().upsert(PreferenceEntity("extra_change", "1", 1_730_000_200_000L))
            val beforeSnapshot = JsonBackupExporter.captureConsistentSnapshot(localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertFalse(result.success)
            assertTrue(result.rolledBack)
            assertEquals(MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING, result.failureReason)
            assertEquals(beforeSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test13_incompatibleBackupOrPreviewRejectedWithoutModifyingDb() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDbA = newInMemoryDb()
        val backupDbB = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDbA.personDao(), backupDbA.productDao(), { "p-a" }, { 1_730_000_000_000L })
                .createPerson("Pessoa A")
            RoomCatalogRepository(backupDbB.personDao(), backupDbB.productDao(), { "p-b" }, { 1_730_000_000_000L })
                .createPerson("Pessoa B")

            val exportedA = JsonBackupExporter.export(backupDbA, ZoneId.of("UTC"), 1_730_000_100_000L)
            val exportedB = JsonBackupExporter.export(backupDbB, ZoneId.of("UTC"), 1_730_000_200_000L)

            val bundleA = preparePipeline(exportedA.jsonContent, localDb)
            val bundleB = preparePipeline(exportedB.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundleB.readResult,
                plan = bundleA.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundleA.validation,
                previewResult = bundleA.preview,
                executionContract = bundleA.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertFalse(result.success)
            assertEquals(MergeExecutionFailureReason.PLAN_BACKUP_MISMATCH, result.failureReason)
            assertEquals(0, localDb.personDao().getAllPeople().size)
        } finally {
            backupDbA.close()
            backupDbB.close()
            localDb.close()
        }
    }

    @Test
    fun test14_missingExplicitUserConfirmationRejectedImmediately() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
                .createPerson("Pessoa")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = false,
                database = localDb,
            )

            assertFalse(result.success)
            assertEquals(MergeExecutionFailureReason.USER_CONFIRMATION_MISSING, result.failureReason)
            assertEquals(0, localDb.personDao().getAllPeople().size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test15_unresolvedConflictRejectedWithoutDefaulting() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_500_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_600_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb, emptyMap())

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertFalse(result.success)
            assertEquals(MergeExecutionFailureReason.UNRESOLVED_CONFLICTS, result.failureReason)
            assertEquals("dark", localDb.preferenceDao().getByKey("app_theme")?.value)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test16_foreignKeyViolationInsideTransactionRollsBackAllWrites() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "fk")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(
                    afterMultipleInserts = {
                        // Tenta inserir participante com person_id inexistente na mesma transação
                        localDb.productDao().insertParticipants(
                            listOf(
                                com.mixvendas3d.app.data.local.entity.ProductParticipantEntity(
                                    productId = "fk-3",
                                    personId = "pessoa-inexistente",
                                    amountCents = 10_000L,
                                    sortOrder = 99,
                                ),
                            ),
                        )
                    },
                ),
            )

            assertFalse(result.success)
            assertTrue(result.rolledBack)
            assertEquals(MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION, result.failureReason)
            assertTrue(result.finalTableCounts.values.all { it == 0 })
            assertEquals(0, localDb.personDao().getAllPeople().size)
            assertEquals(0, localDb.productDao().getAllProducts().size)
            assertEquals(0, localDb.saleDao().getAllSales().size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test17_uniqueIndexViolationInsideTransactionRollsBackAllWrites() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "uq")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(
                    afterMultipleInserts = {
                        // Tenta inserir pessoa com mesmo normalized_name ("ana silva") -> índice UNIQUE deve falhar
                        localDb.personDao().insert(
                            com.mixvendas3d.app.data.local.entity.PersonEntity(
                                id = "duplicada",
                                name = "ANA SILVA",
                                normalizedName = "ana silva",
                                colorIndex = 3,
                                createdAtEpochMs = 1_730_000_000_000L,
                                updatedAtEpochMs = 1_730_000_000_000L,
                            ),
                        )
                    },
                ),
            )

            assertFalse(result.success)
            assertTrue(result.rolledBack)
            assertEquals(MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION, result.failureReason)
            assertTrue(result.finalTableCounts.values.all { it == 0 })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test18_rollbackAfterFirstAndPartialInsertsRestoresExactPreState() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "rb")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)
            val initialSnapshot = JsonBackupExporter.captureConsistentSnapshot(localDb)

            val r1 = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(afterFirstWrite = { throw IllegalStateException("Falha na 1a escrita") }),
            )
            assertFalse(r1.success)
            assertTrue(r1.rolledBack)
            assertEquals(initialSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))

            val r2 = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(afterMultipleInserts = { throw IllegalStateException("Falha após múltiplas inserções") }),
            )
            assertFalse(r2.success)
            assertTrue(r2.rolledBack)
            assertEquals(initialSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test19_rollbackDuringPartialProductMachineAndSaleReplacementsRestoresOldChildren() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(
                backupDb,
                idPrefix = "same",
                productPriceCents = 12_000L,
                anaShareCents = 7_000L,
                brunoShareCents = 5_000L,
                debitRateBps = 180,
                visaRateBps = 350,
                saleFinalOverrideCents = 22_000L,
            )
            populateStandardScenario(
                localDb,
                idPrefix = "same",
                productPriceCents = 10_000L,
                anaShareCents = 6_000L,
                brunoShareCents = 4_000L,
                debitRateBps = 220,
                visaRateBps = 450,
                saleFinalOverrideCents = 18_000L,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val prePlan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)
            val decisions = prePlan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)
            val originalSnapshot = JsonBackupExporter.captureConsistentSnapshot(localDb)

            // 1) Falha no meio da substituição de produto (após apagar participantes antigos)
            val prodFail = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(duringProductReplacement = { throw IllegalStateException("Falha produto") }),
            )
            assertFalse(prodFail.success)
            assertTrue(prodFail.rolledBack)
            assertEquals(originalSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))

            // 2) Falha no meio da substituição de maquininha (após apagar taxas antigas)
            val machFail = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(duringMachineReplacement = { throw IllegalStateException("Falha maquininha") }),
            )
            assertFalse(machFail.success)
            assertTrue(machFail.rolledBack)
            assertEquals(originalSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))

            // 3) Falha no meio da substituição de venda (após apagar itens, participantes e totais antigos)
            val saleFail = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(duringSaleReplacement = { throw IllegalStateException("Falha venda") }),
            )
            assertFalse(saleFail.success)
            assertTrue(saleFail.rolledBack)
            assertEquals(originalSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))
            assertEquals(2, localDb.saleDao().getWithDetailsById(refs.saleId)!!.personTotals.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test20_postWriteStateDivergenceBeforeCommitTriggersRollback() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-div" }, { 1_730_000_000_000L })
                .createPerson("Lucas Prado")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val result = JsonBackupMergeExecutor.executeWithHooksForTest(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
                hooks = ExecutorTestHooks(
                    beforePostWriteVerification = {
                        // Altera um campo sem mudar a contagem antes da conferência final I
                        localDb.personDao().updateName(
                            id = "p-div",
                            name = "Nome Adulterado",
                            normalizedName = "nome adulterado",
                            updatedAtEpochMs = 1_730_000_999_000L,
                        )
                    },
                ),
            )

            assertFalse(result.success)
            assertTrue(result.rolledBack)
            assertEquals(MergeExecutionFailureReason.POST_WRITE_VERIFICATION_MISMATCH, result.failureReason)
            assertEquals(0, localDb.personDao().getAllPeople().size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test21_duplicateExecutionWithOldPlanRejected() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "dup")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val bundle = preparePipeline(exported.jsonContent, localDb)

            val firstRun = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )
            assertTrue(firstRun.success)
            val afterFirstSnapshot = JsonBackupExporter.captureConsistentSnapshot(localDb)

            val secondRun = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )
            assertFalse(secondRun.success)
            assertEquals(MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING, secondRun.failureReason)
            assertEquals(afterFirstSnapshot, JsonBackupExporter.captureConsistentSnapshot(localDb))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test22_historicalSalesPreservedWithoutRecalculationEvenWithDeletedCatalog() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(
                backupDb,
                idPrefix = "hist",
                productPriceCents = 10_000L,
                debitRateBps = 199,
                visaRateBps = 399,
                installment2AddonBps = 150,
                saleFinalOverrideCents = 18_000L,
            )
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)

            // Remove todo o catálogo mantendo apenas a venda histórica completa
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

            val bundle = preparePipeline(root.toString(), localDb)
            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = emptyMap(),
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(0, result.finalTableCounts[BackupCollectionType.PEOPLE])
            assertEquals(0, result.finalTableCounts[BackupCollectionType.PRODUCTS])
            assertEquals(0, result.finalTableCounts[BackupCollectionType.MACHINES])
            assertEquals(1, result.finalTableCounts[BackupCollectionType.SALES])

            val persistedSale = localDb.saleDao().getWithDetailsById(refs.saleId)!!
            assertEquals(20_000L, persistedSale.sale.originalSubtotalCents)
            assertEquals(2_000L, persistedSale.sale.discountTotalCents)
            assertEquals(18_000L, persistedSale.sale.finalAmountCents)
            assertEquals(549, persistedSale.sale.effectiveFeeRateBps)
            assertEquals("SPECIFIC_PRODUCT", persistedSale.sale.discountMode)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test23_fullContentComparisonAcrossAllElevenTablesAfterRestore() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Backup: preferência nova + conflito de preferência + cenário completo + venda nova
            backupDb.preferenceDao().upsert(PreferenceEntity("backup_only_pref", "yes", 1_730_000_000_000L))
            populateStandardScenario(
                backupDb,
                idPrefix = "shared",
                productPriceCents = 14_000L,
                anaShareCents = 8_000L,
                brunoShareCents = 6_000L,
                visaRateBps = 350,
                saleFinalOverrideCents = 25_000L,
            )

            // Local: mesmo conjunto shared divergente + registros somente locais adicionais
            populateStandardScenario(
                localDb,
                idPrefix = "shared",
                productPriceCents = 10_000L,
                anaShareCents = 6_000L,
                brunoShareCents = 4_000L,
                visaRateBps = 450,
                saleFinalOverrideCents = 18_000L,
            )
            localDb.preferenceDao().upsert(PreferenceEntity("local_only_pref", "keep", 1_730_000_000_000L))
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-local-extra" }, { 1_730_000_020_000L })
                .createPerson("Pessoa Local Extra")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val prePlan = JsonBackupMergePlanner.plan(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)
            val decisions = linkedMapOf<String, MergeConflictResolution>().apply {
                prePlan.conflictItems.forEach { item ->
                    val key = "${item.collection.key}:${item.recordId}"
                    this[key] = when (item.collection) {
                        BackupCollectionType.PREFERENCES -> MergeConflictResolution.KEEP_LOCAL
                        else -> MergeConflictResolution.USE_BACKUP
                    }
                }
            }
            val bundle = preparePipeline(exported.jsonContent, localDb, decisions)

            val result = JsonBackupMergeExecutor.execute(
                readResult = bundle.readResult,
                plan = bundle.plan,
                decisionsByConflictKey = decisions,
                decisionValidation = bundle.validation,
                previewResult = bundle.preview,
                executionContract = bundle.preview.executionContract,
                userConfirmed = true,
                database = localDb,
            )

            assertTrue(result.success)
            assertEquals(bundle.preview.finalTableCounts, result.finalTableCounts)

            val actualSnapshot = JsonBackupExporter.captureConsistentSnapshot(localDb)
            val actualCollections = JsonBackupMergePreviewBuilder.toPreviewMergedCollections(actualSnapshot)
            assertEquals(bundle.preview.mergedCollections, actualCollections)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }
}
