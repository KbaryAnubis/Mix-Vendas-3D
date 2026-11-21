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
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeUnitType
import com.mixvendas3d.app.domain.export.PreviewChangeOperation
import com.mixvendas3d.app.domain.export.PreviewIssueType
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage6C2JsonBackupMergePreviewTest {

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
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertTrue(preview.localStateMatchesPlan)
            assertEquals(0, preview.newRecordsCount)
            assertEquals(0, preview.keptRecordsCount)
            assertEquals(0, preview.unitsToReplaceCount)
            assertEquals(0, preview.ignoredRecordsCount)
            assertEquals(0, preview.localOnlyPreservedCount)
            assertTrue(preview.finalTableCounts.values.all { it == 0 })
            assertEquals(0, preview.changedEntries.size)
            assertTrue(preview.issues.isEmpty())
            assertTrue(preview.executionContract.requiresSingleTransaction)
            assertTrue(preview.executionContract.requiresFinalValidationBeforeCommit)
            assertTrue(preview.executionContract.requiresRollbackOnAnyFailure)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test02_addNewRecordsAndFinalCounts() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "new")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(15, preview.newRecordsCount)
            assertEquals(0, preview.keptRecordsCount)
            assertEquals(0, preview.unitsToReplaceCount)
            assertEquals(15, preview.finalTableCounts.values.sum())
            assertEquals(1, preview.mergedCollections.sales.size)
            assertEquals(18_000L, preview.financialSummary.finalTotalFinalAmountCents)
            assertEquals(18_000L, preview.financialSummary.addedSalesFinalAmountCents)
            assertEquals(18_000L, preview.financialSummary.netFinalAmountDeltaCents)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test03_localOnlyRecordsPreservedIntact() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(localDb, idPrefix = "local")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(0, preview.newRecordsCount)
            assertEquals(15, preview.keptRecordsCount)
            assertEquals(15, preview.localOnlyPreservedCount)
            assertEquals(15, preview.finalTableCounts.values.sum())
            assertEquals(listOf(refs.saleId), preview.mergedCollections.sales.map { it.id })
            assertEquals(0L, preview.financialSummary.netFinalAmountDeltaCents)
            assertEquals(0, preview.changedEntries.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test04_identicalRecordsPreservedWithoutDuplication() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same")
            populateStandardScenario(localDb, idPrefix = "same")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(0, preview.newRecordsCount)
            assertEquals(15, preview.keptRecordsCount)
            assertEquals(0, preview.localOnlyPreservedCount)
            assertEquals(0, preview.unitsToReplaceCount)
            assertEquals(15, preview.finalTableCounts.values.sum())
            assertEquals(1, preview.mergedCollections.sales.size)
            assertEquals(0, preview.changedEntries.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test05_keepLocalConflictPreservesLocalAndCountsIgnored() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = mapOf("preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(1, preview.keptRecordsCount)
            assertEquals(1, preview.ignoredRecordsCount)
            assertEquals(0, preview.unitsToReplaceCount)
            assertEquals("dark", preview.mergedCollections.preferences.single().value)
            assertEquals(0, preview.changedEntries.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test06_useBackupConflictPreparesReplacement() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = mapOf("preferences:app_theme" to MergeConflictResolution.USE_BACKUP)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(1, preview.unitsToReplaceCount)
            assertEquals(0, preview.ignoredRecordsCount)
            assertEquals("light", preview.mergedCollections.preferences.single().value)
            assertEquals(PreviewChangeOperation.REPLACE, preview.changedEntries.single().operation)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test07_ignoreImportSkipsBackupAndPreservesLocal() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_100_000L })
                .createPerson("Ana Backup")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-1" }, { 1_730_000_200_000L })
                .createPerson("Ana Local")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = mapOf("people:p-1" to MergeConflictResolution.IGNORE_IMPORT)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(1, preview.ignoredRecordsCount)
            assertEquals(1, preview.keptRecordsCount)
            assertEquals("Ana Local", preview.mergedCollections.people.single().name)
            assertEquals(0, preview.changedEntries.size)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test08_newProductWithMultipleParticipantsPreservedInPreview() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            var seq = 0
            val cat = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { seq += 1; "id-$seq" }, { 1_730_000_000_000L })
            val p1 = cat.createPerson("Ana")
            val p2 = cat.createPerson("Bruno")
            val p3 = cat.createPerson("Carla")
            val prod = cat.createProduct(
                name = "Kit 3D",
                price = MoneyCents(15_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(p3.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = p2.id,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(3, preview.finalTableCounts[BackupCollectionType.PEOPLE])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.PRODUCTS])
            assertEquals(3, preview.finalTableCounts[BackupCollectionType.PRODUCT_PARTICIPANTS])
            assertEquals(prod.id, preview.mergedCollections.products.single().id)
            assertEquals(15_000L, preview.mergedCollections.productParticipants.sumOf { it.amountCents })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test09_productFullAtomicReplacementDoesNotMixOldParticipants() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Backup: prod-1 com p-1 (5000), p-2 (3000), p-3 (2000) = 10000
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_000_000L }).createPerson("Ana")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-2" }, { 1_730_000_000_000L }).createPerson("Bruno")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-3" }, { 1_730_000_000_000L }).createPerson("Carla")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-1" }, { 1_730_000_000_000L }).createProduct(
                name = "Luminária Backup",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft("p-1", MoneyCents(5_000L)),
                    ProductParticipantDraft("p-2", MoneyCents(3_000L)),
                    ProductParticipantDraft("p-3", MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            // Local: prod-1 com p-1 (6000) e p-2 (4000) = 10000
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-1" }, { 1_730_000_000_000L }).createPerson("Ana")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-2" }, { 1_730_000_000_000L }).createPerson("Bruno")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "prod-1" }, { 1_730_000_000_000L }).createProduct(
                name = "Luminária Local",
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
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = plan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals("Luminária Backup", preview.mergedCollections.products.single().name)
            assertEquals(3, preview.mergedCollections.productParticipants.size)
            assertEquals(mapOf("p-1" to 5_000L, "p-2" to 3_000L, "p-3" to 2_000L), preview.mergedCollections.productParticipants.associate { it.personId to it.amountCents })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test10_machineFullAtomicReplacementDoesNotMixOldRates() = runBlocking<Unit>(Dispatchers.IO) {
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

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = plan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(180, preview.mergedCollections.machines.single().debitRateBps)
            // Taxas antigas exclusivas locais (ELO) NÃO devem permanecer após substituição integral!
            assertEquals(setOf("MASTERCARD", "VISA"), preview.mergedCollections.machineBrandRates.map { it.brandKey }.toSet())
            assertEquals(mapOf(2 to 100, 3 to 200), preview.mergedCollections.machineInstallmentRates.associate { it.installments to it.addonBps })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test11_newMultiItemSalePreservedInFourTables() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            var seq = 0
            val idGen = { seq += 1; "mp-$seq" }
            val clock = { 1_730_000_000_000L }
            val cat = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), idGen, clock)
            val saleRepo = RoomSaleRepository(backupDb, backupDb.saleDao(), idGen, clock)

            val ana = cat.createPerson("Ana")
            val bruno = cat.createPerson("Bruno")
            val p1 = cat.createProduct(
                "Peça 1", MoneyCents(10_000L), true,
                listOf(ProductParticipantDraft(ana.id, MoneyCents(6_000L)), ProductParticipantDraft(bruno.id, MoneyCents(4_000L))),
                FeeResponsibilityMode.PROPORTIONAL, null,
            )
            val p2 = cat.createProduct(
                "Peça 2", MoneyCents(5_000L), true,
                listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                FeeResponsibilityMode.PROPORTIONAL, null,
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
                        note = "Multi",
                    ),
                ),
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_100_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.SALES])
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.SALE_ITEMS])
            assertEquals(3, preview.finalTableCounts[BackupCollectionType.SALE_ITEM_PARTICIPANTS])
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.SALE_PERSON_TOTALS])
            assertEquals(sale.id, preview.mergedCollections.sales.single().id)
            assertEquals(20_000L, preview.financialSummary.finalTotalFinalAmountCents)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test12_saleFullAtomicReplacementUpdatesFinancialDeltaWithoutMixingItems() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                val refs = populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
                populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)
                val decisions = plan.conflictItems.associate {
                    "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
                }
                val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
                val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

                assertTrue(preview.isSafeToApply)
                assertEquals(refs.saleId, preview.mergedCollections.sales.single().id)
                assertEquals(18_000L, preview.mergedCollections.sales.single().finalAmountCents)
                assertEquals(17_000L, preview.financialSummary.localTotalFinalAmountCents)
                assertEquals(18_000L, preview.financialSummary.finalTotalFinalAmountCents)
                assertEquals(1_000L, preview.financialSummary.replacedSalesFinalDeltaCents)
                assertEquals(1_000L, preview.financialSummary.netFinalAmountDeltaCents)
                assertEquals(1, preview.financialSummary.replacedSalesCount)
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test13_historicalSaleDiscountRateAndDeletedCatalogPreservedWithoutRecalculation() =
        runBlocking<Unit>(Dispatchers.IO) {
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

                // Remove todo catálogo do backup, preservando somente a venda histórica completa
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
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)
                val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)
                val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

                assertTrue(preview.isSafeToApply)
                val mergedSale = preview.mergedCollections.sales.single()
                assertEquals(refs.saleId, mergedSale.id)
                assertEquals(20_000L, mergedSale.originalSubtotalCents)
                assertEquals(2_000L, mergedSale.discountTotalCents)
                assertEquals(18_000L, mergedSale.finalAmountCents)
                assertEquals(549, mergedSale.effectiveFeeRateBps) // 399 + 150 bps históricos mantidos
                assertEquals("SPECIFIC_PRODUCT", mergedSale.discountMode)
                assertEquals(0, preview.finalTableCounts[BackupCollectionType.PRODUCTS])
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test14_normalizedNameCollisionRejectsUnsafePreview() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "person-b" }, { 1_730_000_100_000L })
                .createPerson("Ana Silva")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "person-l" }, { 1_730_000_200_000L })
                .createPerson("  ana silva ")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = mapOf("people:person-b" to MergeConflictResolution.USE_BACKUP)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertFalse(validation.isReadyForNextPhase)
            assertFalse(preview.isSafeToApply)
            assertTrue(preview.issues.any { it.type == PreviewIssueType.IDENTITY_NAME_COLLISION })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test15_unresolvedDependencyRejectsPreviewWithIsSafeToApplyFalse() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val pB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
                .createPerson("Ana Backup")
            RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
                .createPerson("Ana Local")
            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-1" }, { 1_730_000_100_000L })
                .createProduct(
                    name = "Chaveiro",
                    price = MoneyCents(4_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(pB.id, MoneyCents(4_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_200_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, emptyMap())
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertFalse(preview.isSafeToApply)
            assertTrue(preview.issues.any { it.type == PreviewIssueType.REFERENTIAL_OR_DEPENDENCY_ERROR })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test16_outdatedFingerprintInvalidatesPreview() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "b")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)

            // Altera o banco local entre a validação e a construção da prévia
            localDb.preferenceDao().upsert(PreferenceEntity("dirty_flag", "changed", 1_730_000_999_000L))

            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, localDb)

            assertFalse(preview.localStateMatchesPlan)
            assertFalse(preview.isSafeToApply)
            assertTrue(preview.issues.any { it.type == PreviewIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test17_inconsistentFinancialValuesRejectedByFinalValidator() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val cleanValidation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)

            // Injeta inconsistência financeira (netTotalCents adulterado)
            val cleanData = readResult.validatedData!!
            val brokenData = ValidatedBackupData(
                format = cleanData.format,
                backupSchemaVersion = cleanData.backupSchemaVersion,
                applicationId = cleanData.applicationId,
                roomSchemaVersion = cleanData.roomSchemaVersion,
                exportedAtEpochMs = cleanData.exportedAtEpochMs,
                preferences = cleanData.preferences,
                people = cleanData.people,
                products = cleanData.products,
                productParticipants = cleanData.productParticipants,
                machines = cleanData.machines,
                machineBrandRates = cleanData.machineBrandRates,
                machineInstallmentRates = cleanData.machineInstallmentRates,
                sales = cleanData.sales.map { it.copy(netTotalCents = it.netTotalCents - 1L) },
                saleItems = cleanData.saleItems,
                saleItemParticipants = cleanData.saleItemParticipants,
                salePersonTotals = cleanData.salePersonTotals,
                declaredCounts = cleanData.declaredCounts,
            )

            val preview = JsonBackupMergePreviewBuilder.buildPreview(brokenData, plan, emptyMap(), cleanValidation, localDb)
            assertFalse(preview.isSafeToApply)
            assertTrue(preview.issues.any { it.type == PreviewIssueType.FINANCIAL_VALIDATION_ERROR })

            // Também rejeita JsonBackupReadResult inválido imediatamente
            val invalidRead = JsonBackupReader.readAndValidate("{ invalido")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    JsonBackupMergePreviewBuilder.buildPreview(invalidRead, plan, emptyMap(), cleanValidation, localDb)
                }
            }
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test18_finalElevenTableCountsAndChangedUnitsMatchExpectedState() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // 15 registros idênticos/conflitantes + 1 pessoa nova no backup + 1 preferência somente local
            populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
            populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

            RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "extra-p" }, { 1_730_000_500_000L })
                .createPerson("Pessoa Extra Backup")
            localDb.preferenceDao().upsert(PreferenceEntity("local_only_pref", "v1", 1_730_000_500_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_600_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = plan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)
            assertEquals(11, preview.finalTableCounts.size)
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.PREFERENCES])
            assertEquals(3, preview.finalTableCounts[BackupCollectionType.PEOPLE])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.PRODUCTS])
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.PRODUCT_PARTICIPANTS])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.MACHINES])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.MACHINE_BRAND_RATES])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.MACHINE_INSTALLMENT_RATES])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.SALES])
            assertEquals(1, preview.finalTableCounts[BackupCollectionType.SALE_ITEMS])
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.SALE_ITEM_PARTICIPANTS])
            assertEquals(2, preview.finalTableCounts[BackupCollectionType.SALE_PERSON_TOTALS])

            assertEquals(1, preview.newRecordsCount) // extra-p
            assertEquals(1, preview.localOnlyPreservedCount) // local_only_pref
            assertEquals(1, preview.unitsToReplaceCount) // unidade SALE substituída
            assertTrue(preview.changedEntries.any { it.operation == PreviewChangeOperation.ADD && it.targetId == "extra-p" })
            assertTrue(preview.changedEntries.any { it.operation == PreviewChangeOperation.REPLACE && it.unitType == MergeUnitType.SALE })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test19_previewIsDeterministicAcrossRepeatedRunsAndOrderings() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
            populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            val forward = LinkedHashMap<String, MergeConflictResolution>().apply {
                plan.conflictItems.forEach { put("${it.collection.key}:${it.recordId}", MergeConflictResolution.USE_BACKUP) }
            }
            val reversed = LinkedHashMap<String, MergeConflictResolution>().apply {
                plan.conflictItems.reversed().forEach { put("${it.collection.key}:${it.recordId}", MergeConflictResolution.USE_BACKUP) }
            }

            val v1 = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, forward)
            val v2 = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, reversed)

            val p1 = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, forward, v1, localDb)
            val p2 = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, reversed, v2, localDb)

            assertEquals(p1, p2)
            assertEquals(p1.hashCode(), p2.hashCode())
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test20_previewBuilderNeverModifiesLocalRoomDatabase() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
            populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

            val localBefore = JsonBackupExporter.export(localDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(
                JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L).jsonContent,
            )
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)
            val decisions = plan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }
            val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisions)
            val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, localDb)

            assertTrue(preview.isSafeToApply)

            val localAfter = JsonBackupExporter.export(localDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            assertEquals(localBefore.jsonContent, localAfter.jsonContent)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }
}
