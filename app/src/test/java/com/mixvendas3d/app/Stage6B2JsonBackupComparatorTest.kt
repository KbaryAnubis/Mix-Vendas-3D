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
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.RelationalRiskType
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
class Stage6B2JsonBackupComparatorTest {

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

    private suspend fun populateStandardScenario(
        db: AppDatabase,
        idPrefix: String = "id",
        baseClock: Long = 1_730_000_000_000L,
        productPriceCents: Long = 10_000L,
        anaShareCents: Long = 6_000L,
        brunoShareCents: Long = 4_000L,
        debitRateBps: Int = 199,
        creditDefaultRateBps: Int = 450,
        visaRateBps: Int = 399,
        installment2AddonBps: Int = 150,
        saleFinalOverrideCents: Long = 18_000L,
    ): String {
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

        val ana = catalogRepo.createPerson("Ana Silva")
        val bruno = catalogRepo.createPerson("Bruno Costa")

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
                name = "Stone Pro",
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
        return sale.id
    }

    @Test
    fun testEmptyBackupAndEmptyDatabase() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val comparison = JsonBackupComparator.compare(readResult, localDb)

            assertEquals(11, comparison.collectionSummaries.size)
            assertEquals(0, comparison.totalBackupRecords)
            assertEquals(0, comparison.totalLocalRecords)
            assertEquals(0, comparison.totalNewCount)
            assertEquals(0, comparison.totalIdenticalCount)
            assertEquals(0, comparison.totalConflictingCount)
            assertEquals(0, comparison.totalLocalOnlyCount)
            assertFalse(comparison.hasAnyConflicts)
            assertFalse(comparison.hasRelationalRisks)
            assertTrue(comparison.isSafeForAdditiveMerge)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testFullBackupAndEmptyDatabaseMarksAllNewAndSafeToMerge() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb)
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val comparison = JsonBackupComparator.compare(readResult, localDb)

            assertEquals(15, comparison.totalBackupRecords)
            assertEquals(0, comparison.totalLocalRecords)
            assertEquals(15, comparison.totalNewCount)
            assertEquals(0, comparison.totalIdenticalCount)
            assertEquals(0, comparison.totalConflictingCount)
            assertEquals(0, comparison.totalLocalOnlyCount)
            assertFalse(comparison.hasAnyConflicts)
            assertFalse(comparison.hasRelationalRisks)
            assertTrue(comparison.isSafeForAdditiveMerge)

            assertEquals(1, comparison.summaryFor(BackupCollectionType.PREFERENCES).newCount)
            assertEquals(2, comparison.summaryFor(BackupCollectionType.PEOPLE).newCount)
            assertEquals(1, comparison.summaryFor(BackupCollectionType.PRODUCTS).newCount)
            assertEquals(2, comparison.summaryFor(BackupCollectionType.PRODUCT_PARTICIPANTS).newCount)
            assertEquals(1, comparison.summaryFor(BackupCollectionType.MACHINES).newCount)
            assertEquals(1, comparison.summaryFor(BackupCollectionType.MACHINE_BRAND_RATES).newCount)
            assertEquals(1, comparison.summaryFor(BackupCollectionType.MACHINE_INSTALLMENT_RATES).newCount)
            assertEquals(1, comparison.summaryFor(BackupCollectionType.SALES).newCount)
            assertEquals(1, comparison.summaryFor(BackupCollectionType.SALE_ITEMS).newCount)
            assertEquals(2, comparison.summaryFor(BackupCollectionType.SALE_ITEM_PARTICIPANTS).newCount)
            assertEquals(2, comparison.summaryFor(BackupCollectionType.SALE_PERSON_TOTALS).newCount)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testEmptyBackupAndPopulatedDatabaseMarksAllLocalOnly() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(localDb)
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val comparison = JsonBackupComparator.compare(readResult, localDb)

            assertEquals(0, comparison.totalBackupRecords)
            assertEquals(15, comparison.totalLocalRecords)
            assertEquals(0, comparison.totalNewCount)
            assertEquals(0, comparison.totalIdenticalCount)
            assertEquals(0, comparison.totalConflictingCount)
            assertEquals(15, comparison.totalLocalOnlyCount)
            assertFalse(comparison.hasAnyConflicts)
            assertFalse(comparison.hasRelationalRisks)
            assertTrue(comparison.isSafeForAdditiveMerge)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testIdenticalBackupAndLocalDatabaseMarksAllIdenticalAndNoConflicts() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same")
            populateStandardScenario(localDb, idPrefix = "same")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val comparison = JsonBackupComparator.compare(readResult, localDb)

            assertEquals(15, comparison.totalBackupRecords)
            assertEquals(15, comparison.totalLocalRecords)
            assertEquals(0, comparison.totalNewCount)
            assertEquals(15, comparison.totalIdenticalCount)
            assertEquals(0, comparison.totalConflictingCount)
            assertEquals(0, comparison.totalLocalOnlyCount)
            assertFalse(comparison.hasAnyConflicts)
            assertFalse(comparison.hasRelationalRisks)
            assertTrue(comparison.isSafeForAdditiveMerge)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testNewRecordsInBackupWithExistingLocalRecordsClassifiedCorrectly() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "shared")
            populateStandardScenario(localDb, idPrefix = "shared")

            // Adiciona registros extras distintos exclusivamente no backup
            val extraCatalog = RoomCatalogRepository(
                backupDb.personDao(),
                backupDb.productDao(),
                idGenerator = { "extra-person-1" },
                clockMillis = { 1_730_000_500_000L },
            )
            extraCatalog.createPerson("Carlos Novo")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_600_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val comparison = JsonBackupComparator.compare(readResult, localDb)

            val peopleSummary = comparison.summaryFor(BackupCollectionType.PEOPLE)
            assertEquals(3, peopleSummary.backupCount)
            assertEquals(2, peopleSummary.localCount)
            assertEquals(1, peopleSummary.newCount)
            assertEquals(2, peopleSummary.identicalCount)
            assertEquals(0, peopleSummary.conflictingCount)
            assertEquals(0, peopleSummary.localOnlyCount)
            assertEquals(listOf("extra-person-1"), peopleSummary.newRecordIds)
            assertTrue(comparison.isSafeForAdditiveMerge)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testConflictingRecordsDetectExactDivergentFieldsAndRejectInvalidReadResult() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
            populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

            val invalidRead = JsonBackupReader.readAndValidate("{ invalido")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { JsonBackupComparator.compare(invalidRead, localDb) }
            }

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val validRead = JsonBackupReader.readAndValidate(exported.jsonContent)
            val comparison = JsonBackupComparator.compare(validRead, localDb)

            assertTrue(comparison.hasAnyConflicts)
            assertFalse(comparison.isSafeForAdditiveMerge)

            val salesSummary = comparison.summaryFor(BackupCollectionType.SALES)
            assertEquals(1, salesSummary.conflictingCount)
            val saleConflict = salesSummary.conflictingRecords.single()
            assertTrue(saleConflict.hasFinancialOrRateDivergence)
            assertTrue(saleConflict.divergentFields.contains("discountTotalCents"))
            assertTrue(saleConflict.divergentFields.contains("finalAmountCents"))
            assertTrue(saleConflict.divergentFields.contains("feeTotalCents"))
            assertTrue(saleConflict.divergentFields.contains("netTotalCents"))
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testMixedNewIdenticalConflictingAndLocalOnlyAcrossCollections() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("pref_identica", "a", 1_730_000_000_000L))
            backupDb.preferenceDao().upsert(PreferenceEntity("pref_conflito", "valor_backup", 1_730_000_000_000L))
            backupDb.preferenceDao().upsert(PreferenceEntity("pref_nova", "nova", 1_730_000_000_000L))

            localDb.preferenceDao().upsert(PreferenceEntity("pref_identica", "a", 1_730_000_000_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("pref_conflito", "valor_local", 1_730_000_000_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("pref_somente_local", "local", 1_730_000_000_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val comparison = JsonBackupComparator.compare(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            val prefSummary = comparison.summaryFor(BackupCollectionType.PREFERENCES)
            assertEquals(3, prefSummary.backupCount)
            assertEquals(3, prefSummary.localCount)
            assertEquals(1, prefSummary.newCount)
            assertEquals(1, prefSummary.identicalCount)
            assertEquals(1, prefSummary.conflictingCount)
            assertEquals(1, prefSummary.localOnlyCount)
            assertEquals(listOf("pref_nova"), prefSummary.newRecordIds)
            assertEquals(listOf("pref_identica"), prefSummary.identicalRecordIds)
            assertEquals(listOf("pref_conflito"), prefSummary.conflictingRecordIds)
            assertEquals(listOf("pref_somente_local"), prefSummary.localOnlyRecordIds)
            assertEquals(listOf("value"), prefSummary.conflictingRecords.single().divergentFields)

            // Imutabilidade do resumo
            assertThrows(UnsupportedOperationException::class.java) {
                (prefSummary.newRecordIds as MutableList).add("x")
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (comparison.collectionSummaries as MutableList).clear()
            }
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testCompositeKeyConflictsInProductParticipantsMachineRatesAndSaleTables() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(
                db = backupDb,
                idPrefix = "same",
                anaShareCents = 6_000L,
                brunoShareCents = 4_000L,
                visaRateBps = 399,
                installment2AddonBps = 150,
            )
            populateStandardScenario(
                db = localDb,
                idPrefix = "same",
                anaShareCents = 5_500L,
                brunoShareCents = 4_500L,
                visaRateBps = 415,
                installment2AddonBps = 180,
            )

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val comparison = JsonBackupComparator.compare(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            val ppSummary = comparison.summaryFor(BackupCollectionType.PRODUCT_PARTICIPANTS)
            assertEquals(2, ppSummary.conflictingCount)
            assertTrue(ppSummary.conflictingRecords.all { it.divergentFields == listOf("amountCents") })
            assertTrue(ppSummary.conflictingRecords.all { it.keyParts.containsKey("productId") && it.keyParts.containsKey("personId") })

            val brSummary = comparison.summaryFor(BackupCollectionType.MACHINE_BRAND_RATES)
            assertEquals(1, brSummary.conflictingCount)
            assertEquals(listOf("rateBps"), brSummary.conflictingRecords.single().divergentFields)

            val irSummary = comparison.summaryFor(BackupCollectionType.MACHINE_INSTALLMENT_RATES)
            assertEquals(1, irSummary.conflictingCount)
            assertEquals(listOf("addonBps"), irSummary.conflictingRecords.single().divergentFields)

            val sipSummary = comparison.summaryFor(BackupCollectionType.SALE_ITEM_PARTICIPANTS)
            assertEquals(2, sipSummary.conflictingCount)
            assertTrue(sipSummary.conflictingRecords.all { it.hasFinancialOrRateDivergence })

            val sptSummary = comparison.summaryFor(BackupCollectionType.SALE_PERSON_TOTALS)
            assertEquals(2, sptSummary.conflictingCount)
            assertTrue(sptSummary.conflictingRecords.all { it.hasFinancialOrRateDivergence })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testLongPrecisionAbove2Pow53DetectsSingleCentDifferenceAsConflict() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val baseAbove2Pow53 = 9_007_199_254_740_993L // 2^53 + 1
            val diffByOneCent = 9_007_199_254_740_992L // Em Double ambos arredondariam para 9007199254740992.0!

            suspend fun seedLarge(db: AppDatabase, price: Long) {
                var seq = 0
                val idGen = { seq += 1; "lg-$seq" }
                val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), idGen, { 9_007_199_254_740_999L })
                val p1 = catalog.createPerson("Sócio Grande")
                val prod = catalog.createProduct(
                    name = "Produto Precisão",
                    price = MoneyCents(price),
                    active = true,
                    participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(price))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
                val saleRepo = RoomSaleRepository(db, db.saleDao(), idGen, { 9_007_199_254_740_999L })
                saleRepo.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(items = listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.PIX),
                    ),
                )
            }

            seedLarge(backupDb, baseAbove2Pow53)
            seedLarge(localDb, diffByOneCent)

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), Long.MAX_VALUE)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.isValid)

            val comparison = JsonBackupComparator.compare(readResult, localDb)
            val prodConflict = comparison.summaryFor(BackupCollectionType.PRODUCTS).conflictingRecords.single()
            assertEquals(listOf("priceCents"), prodConflict.divergentFields)
            assertTrue(prodConflict.hasFinancialOrRateDivergence)

            val saleConflict = comparison.summaryFor(BackupCollectionType.SALES).conflictingRecords.single()
            assertTrue("originalSubtotalCents" in saleConflict.divergentFields)
            assertTrue("finalAmountCents" in saleConflict.divergentFields)
            assertTrue("netTotalCents" in saleConflict.divergentFields)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testHistoricalSaleWithDeletedCatalogEntriesIsNewAndWithoutFalseRelationalRisk() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val saleId = populateStandardScenario(backupDb, idPrefix = "hist")
            val sqlite = backupDb.openHelper.writableDatabase
            val machineId = backupDb.machineDao().getAllMachines().single().id
            val productId = backupDb.productDao().getAllProducts().single().id
            val personIds = backupDb.personDao().getAllPeople().map { it.id }

            sqlite.execSQL("DELETE FROM machines WHERE id = ?", arrayOf(machineId))
            sqlite.execSQL("DELETE FROM products WHERE id = ?", arrayOf(productId))
            personIds.forEach { pid -> sqlite.execSQL("DELETE FROM people WHERE id = ?", arrayOf(pid)) }

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            assertTrue(readResult.isValid)

            val comparison = JsonBackupComparator.compare(readResult, localDb)
            assertEquals(listOf(saleId), comparison.summaryFor(BackupCollectionType.SALES).newRecordIds)
            assertFalse(comparison.hasAnyConflicts)
            assertFalse("Venda histórica sem catálogo não deve gerar risco relacional falso", comparison.hasRelationalRisks)
            assertTrue(comparison.isSafeForAdditiveMerge)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testComparisonIsIndependentOfCollectionOrder() = runBlocking<Unit>(Dispatchers.IO) {
        val db = newInMemoryDb()
        try {
            populateStandardScenario(db, idPrefix = "ord")
            val exported = JsonBackupExporter.export(db, ZoneId.of("UTC"), 1_730_000_000_000L)
            val data = JsonBackupReader.readAndValidate(exported.jsonContent).validatedData!!

            val reversedData = ValidatedBackupData(
                format = data.format,
                backupSchemaVersion = data.backupSchemaVersion,
                applicationId = data.applicationId,
                roomSchemaVersion = data.roomSchemaVersion,
                exportedAtEpochMs = data.exportedAtEpochMs,
                preferences = data.preferences.reversed(),
                people = data.people.reversed(),
                products = data.products.reversed(),
                productParticipants = data.productParticipants.reversed(),
                machines = data.machines.reversed(),
                machineBrandRates = data.machineBrandRates.reversed(),
                machineInstallmentRates = data.machineInstallmentRates.reversed(),
                sales = data.sales.reversed(),
                saleItems = data.saleItems.reversed(),
                saleItemParticipants = data.saleItemParticipants.reversed(),
                salePersonTotals = data.salePersonTotals.reversed(),
                declaredCounts = data.declaredCounts,
            )

            val comparison = JsonBackupComparator.compare(reversedData, db)
            assertEquals(15, comparison.totalIdenticalCount)
            assertEquals(0, comparison.totalConflictingCount)
            assertEquals(0, comparison.totalNewCount)
            assertEquals(0, comparison.totalLocalOnlyCount)
            assertTrue(comparison.isSafeForAdditiveMerge)
        } finally {
            db.close()
        }
    }

    @Test
    fun testRelationalRisksDetectedForUniqueCollisionsAndPartialFinancialMerges() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            // Mesmo nome normalizado em people e machines, porém com IDs diferentes
            populateStandardScenario(backupDb, idPrefix = "bk")
            populateStandardScenario(localDb, idPrefix = "lc")

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val comparison = JsonBackupComparator.compare(JsonBackupReader.readAndValidate(exported.jsonContent), localDb)

            assertTrue(comparison.hasRelationalRisks)
            assertFalse(comparison.isSafeForAdditiveMerge)
            assertTrue(
                comparison.relationalRisks.any {
                    it.type == RelationalRiskType.UNIQUE_NAME_COLLISION && it.collection == BackupCollectionType.PEOPLE
                },
            )
            assertTrue(
                comparison.relationalRisks.any {
                    it.type == RelationalRiskType.UNIQUE_NAME_COLLISION && it.collection == BackupCollectionType.MACHINES
                },
            )

            // Agora cenário com mesmo saleId e mesmo productId porém saleItemId distinto + divergência financeira
            val localSameSaleDb = newInMemoryDb()
            try {
                populateStandardScenario(localSameSaleDb, idPrefix = "bk", saleFinalOverrideCents = 17_500L)
                val root = JSONObject(exported.jsonContent)
                val cols = root.getJSONObject("collections")
                val items = cols.getJSONArray("saleItems")
                val origItemId = items.getJSONObject(0).getString("id")
                items.getJSONObject(0).put("id", "item-id-diferente")
                val sips = cols.getJSONArray("saleItemParticipants")
                for (i in 0 until sips.length()) {
                    if (sips.getJSONObject(i).getString("saleItemId") == origItemId) {
                        sips.getJSONObject(i).put("saleItemId", "item-id-diferente")
                    }
                }
                val readModified = JsonBackupReader.readAndValidate(root.toString())
                assertTrue(readModified.isValid)

                val saleRiskComparison = JsonBackupComparator.compare(readModified, localSameSaleDb)
                assertTrue(
                    saleRiskComparison.relationalRisks.any {
                        it.type == RelationalRiskType.SALE_FINANCIAL_CONFLICT_RISK &&
                            it.collection == BackupCollectionType.SALES
                    },
                )
                assertTrue(
                    saleRiskComparison.relationalRisks.any {
                        it.type == RelationalRiskType.SALE_ITEM_UNIQUE_PRODUCT_COLLISION &&
                            it.collection == BackupCollectionType.SALE_ITEMS
                    },
                )
            } finally {
                localSameSaleDb.close()
            }
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun testComparisonLeavesLocalRoomDatabaseCompletelyUnchanged() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            populateStandardScenario(backupDb, idPrefix = "bk", saleFinalOverrideCents = 19_000L)
            populateStandardScenario(localDb, idPrefix = "bk", saleFinalOverrideCents = 18_000L)

            val localBefore = JsonBackupExporter.captureConsistentSnapshot(localDb)
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)

            val comparison = JsonBackupComparator.compare(readResult, localDb)
            assertTrue(comparison.hasAnyConflicts)

            val localAfter = JsonBackupExporter.captureConsistentSnapshot(localDb)
            assertEquals(localBefore, localAfter)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }
}
