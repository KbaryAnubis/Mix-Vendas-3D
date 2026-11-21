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
import com.mixvendas3d.app.domain.export.DecisionValidationIssueType
import com.mixvendas3d.app.domain.export.EligibleOperationDisposition
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictItem
import com.mixvendas3d.app.domain.export.MergeConflictKind
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeUnitType
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
class Stage6C1JsonBackupMergeDecisionValidatorTest {

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
    fun test01_noConflictsAllValidRecordsAndUnitsEligible() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(backupDb, idPrefix = "new")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            val result = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)

            assertEquals(0, result.totalConflictsCount)
            assertEquals(0, result.resolvedConflictsCount)
            assertEquals(0, result.unresolvedConflictsCount)
            assertEquals(0, result.pendingDependenciesCount)
            assertEquals(0, result.incompatibleDecisionsCount)
            assertEquals(0, result.blockedOperationsCount)
            assertTrue(result.localStateMatchesPlan)
            assertTrue(result.isReadyForNextPhase)

            // 1 pref + 2 people = 3 standalone ADD_NEW; 3 unidades (produto, maquininha, venda) = ADD_NEW
            assertEquals(3, result.eligibleStandaloneRecords.count { it.disposition == EligibleOperationDisposition.ADD_NEW })
            assertEquals(3, result.eligibleUnits.count { it.disposition == EligibleOperationDisposition.ADD_NEW })
            assertTrue(result.eligibleUnits.any { it.unitType == MergeUnitType.PRODUCT && it.unitId == refs.productId })
            assertTrue(result.eligibleUnits.any { it.unitType == MergeUnitType.MACHINE && it.unitId == refs.machineId })
            assertTrue(result.eligibleUnits.any { it.unitType == MergeUnitType.SALE && it.unitId == refs.saleId })
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test02_unresolvedConflictRemainsBlockedWithoutAutoChoice() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            val result = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, emptyMap())

            assertEquals(1, result.totalConflictsCount)
            assertEquals(0, result.resolvedConflictsCount)
            assertEquals(1, result.unresolvedConflictsCount)
            assertEquals("app_theme", result.unresolvedConflicts.single().recordId)
            assertEquals(listOf("preferences:app_theme"), result.preconditions.unresolvedConflictIds)
            assertFalse(result.isReadyForNextPhase)
            assertFalse(result.preconditions.canExecuteAdditiveWithoutUserDecisions)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test03_keepLocalDecisionResolvesConflictAndSkipsBackupWrite() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val plan = JsonBackupMergePlanner.plan(readResult, localDb)

            val result = JsonBackupMergeDecisionValidator.validate(
                plan = plan,
                readResult = readResult,
                database = localDb,
                decisionsByConflictKey = mapOf("preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL),
            )

            assertEquals(1, result.totalConflictsCount)
            assertEquals(1, result.resolvedConflictsCount)
            assertEquals(0, result.unresolvedConflictsCount)
            assertTrue(result.isReadyForNextPhase)
            val rec = result.eligibleStandaloneRecords.single { it.recordId == "app_theme" }
            assertEquals(EligibleOperationDisposition.SKIP_BACKUP, rec.disposition)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test04_useBackupDecisionMarksEligibleApplyBackup() = runBlocking<Unit>(Dispatchers.IO) {
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

            val result = JsonBackupMergeDecisionValidator.validate(
                plan = plan,
                readResult = readResult,
                database = localDb,
                decisionsByConflictKey = mapOf("people:p-1" to MergeConflictResolution.USE_BACKUP),
            )

            assertEquals(1, result.resolvedConflictsCount)
            assertEquals(0, result.unresolvedConflictsCount)
            assertTrue(result.isReadyForNextPhase)
            val rec = result.eligibleStandaloneRecords.single { it.collection == BackupCollectionType.PEOPLE && it.recordId == "p-1" }
            assertEquals(EligibleOperationDisposition.APPLY_BACKUP, rec.disposition)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test05_ignoreImportDecisionResolvesConflictAndSkipsBackupWrite() = runBlocking<Unit>(Dispatchers.IO) {
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

            val result = JsonBackupMergeDecisionValidator.validate(
                plan = plan,
                readResult = readResult,
                database = localDb,
                decisionsByConflictKey = mapOf("people:p-1" to MergeConflictResolution.IGNORE_IMPORT),
            )

            assertEquals(1, result.resolvedConflictsCount)
            assertEquals(0, result.unresolvedConflictsCount)
            assertTrue(result.isReadyForNextPhase)
            assertEquals(
                EligibleOperationDisposition.SKIP_BACKUP,
                result.eligibleStandaloneRecords.single { it.recordId == "p-1" }.disposition,
            )
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test06_invalidDecisionKeyOrDisallowedResolutionReportedAndBlocksReady() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
            localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
            val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
            val basePlan = JsonBackupMergePlanner.plan(readResult, localDb)

            // 1) Chave de conflito inexistente
            val unknownKeyResult = JsonBackupMergeDecisionValidator.validate(
                plan = basePlan,
                readResult = readResult,
                database = localDb,
                decisionsByConflictKey = mapOf(
                    "preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL,
                    "people:nao_existe" to MergeConflictResolution.USE_BACKUP,
                ),
            )
            assertFalse(unknownKeyResult.isReadyForNextPhase)
            assertTrue(unknownKeyResult.invalidDecisionInputs.any { it.type == DecisionValidationIssueType.UNKNOWN_CONFLICT_KEY })

            // 2) Resolução não permitida para o item restrito
            val restrictedConflict = MergeConflictItem(
                collection = basePlan.conflictItems.single().collection,
                recordId = basePlan.conflictItems.single().recordId,
                keyParts = basePlan.conflictItems.single().keyParts,
                kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                divergentFields = basePlan.conflictItems.single().divergentFields,
                allowedResolutions = listOf(MergeConflictResolution.KEEP_LOCAL),
            )
            val restrictedPlan = com.mixvendas3d.app.domain.export.JsonBackupMergePlanResult(
                backupFormat = basePlan.backupFormat,
                backupSchemaVersion = basePlan.backupSchemaVersion,
                applicationId = basePlan.applicationId,
                roomSchemaVersion = basePlan.roomSchemaVersion,
                exportedAtEpochMs = basePlan.exportedAtEpochMs,
                collectionPlans = basePlan.collectionPlans,
                relatedUnits = basePlan.relatedUnits,
                conflictItems = listOf(restrictedConflict),
                pendingDependencies = basePlan.pendingDependencies,
                blockReasons = basePlan.blockReasons,
                preconditions = basePlan.preconditions,
            )
            val disallowedResult = JsonBackupMergeDecisionValidator.validate(
                plan = restrictedPlan,
                readResult = readResult,
                database = localDb,
                decisionsByConflictKey = mapOf("preferences:app_theme" to MergeConflictResolution.USE_BACKUP),
            )
            assertFalse(disallowedResult.isReadyForNextPhase)
            assertTrue(disallowedResult.invalidDecisionInputs.any { it.type == DecisionValidationIssueType.DISALLOWED_RESOLUTION })

            // Rejeita JsonBackupReadResult inválido
            val invalidRead = JsonBackupReader.readAndValidate("{ invalido")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { JsonBackupMergeDecisionValidator.validate(basePlan, invalidRead, localDb) }
            }
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test07_productWithConflictingParticipantsRejectsIncompatibleDecisionsAndAcceptsAtomicResolution() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                // Produto com mesmo ID e preço, cabeçalho com nome diferente e divisão diferente de participantes
                RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_000_000L }).createPerson("Ana")
                RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-2" }, { 1_730_000_000_000L }).createPerson("Bruno")
                RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-1" }, { 1_730_000_000_000L }).createProduct(
                    name = "Luminária Backup",
                    price = MoneyCents(10_000L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft("p-1", MoneyCents(7_000L)),
                        ProductParticipantDraft("p-2", MoneyCents(3_000L)),
                    ),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )

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

                // Decisões incompatíveis dentro do mesmo produto (cabeçalho USE_BACKUP e participantes KEEP_LOCAL)
                val incompatible = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "products:prod-1" to MergeConflictResolution.USE_BACKUP,
                        "productParticipants:prod-1:p-1" to MergeConflictResolution.KEEP_LOCAL,
                        "productParticipants:prod-1:p-2" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertFalse(incompatible.isReadyForNextPhase)
                assertTrue(incompatible.incompatibleDecisions.any {
                    it.type == DecisionValidationIssueType.INCOMPATIBLE_UNIT_DECISION &&
                        it.relatedUnitType == MergeUnitType.PRODUCT &&
                        it.relatedUnitId == "prod-1"
                })

                // Decisão parcial (esquece um participante em conflito)
                val incomplete = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "products:prod-1" to MergeConflictResolution.USE_BACKUP,
                        "productParticipants:prod-1:p-1" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertFalse(incomplete.isReadyForNextPhase)
                assertTrue(incomplete.incompatibleDecisions.any { it.type == DecisionValidationIssueType.INCOMPLETE_UNIT_DECISION })

                // Decisão atômica unificada (USE_BACKUP em toda a unidade)
                val unified = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "products:prod-1" to MergeConflictResolution.USE_BACKUP,
                        "productParticipants:prod-1:p-1" to MergeConflictResolution.USE_BACKUP,
                        "productParticipants:prod-1:p-2" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertTrue(unified.isReadyForNextPhase)
                assertEquals(
                    EligibleOperationDisposition.APPLY_BACKUP,
                    unified.eligibleUnits.single { it.unitType == MergeUnitType.PRODUCT && it.unitId == "prod-1" }.disposition,
                )
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test08_machineWithConflictingRatesRejectsIncompatibleDecisionsAndInvalidRates() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                RoomMachineRepository(backupDb.machineDao(), idGenerator = { "m-1" }, clockMillis = { 1_730_000_000_000L })
                    .createMachine(
                        MachineInputDraft(
                            name = "Stone Pro",
                            debitRateBps = 199,
                            creditEnabled = true,
                            creditDefaultRateBps = 450,
                            brandRatesEnabled = true,
                            brandRatesBps = mapOf(CardBrand.VISA to 399),
                            installmentsEnabled = true,
                            installmentAddonsBps = mapOf(2 to 150),
                        ),
                    )
                RoomMachineRepository(localDb.machineDao(), idGenerator = { "m-1" }, clockMillis = { 1_730_000_000_000L })
                    .createMachine(
                        MachineInputDraft(
                            name = "Stone Pro",
                            debitRateBps = 210,
                            creditEnabled = true,
                            creditDefaultRateBps = 480,
                            brandRatesEnabled = true,
                            brandRatesBps = mapOf(CardBrand.VISA to 430),
                            installmentsEnabled = true,
                            installmentAddonsBps = mapOf(2 to 200),
                        ),
                    )

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_500_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)

                // Mistura incompatível: maquininha USE_BACKUP + taxa bandeira KEEP_LOCAL
                val incompatible = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "machines:m-1" to MergeConflictResolution.USE_BACKUP,
                        "machineBrandRates:m-1:VISA" to MergeConflictResolution.KEEP_LOCAL,
                        "machineInstallmentRates:m-1:2" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertFalse(incompatible.isReadyForNextPhase)
                assertTrue(incompatible.incompatibleDecisions.any {
                    it.type == DecisionValidationIssueType.INCOMPATIBLE_UNIT_DECISION &&
                        it.relatedUnitType == MergeUnitType.MACHINE
                })

                // Decisão unificada válida -> unidade elegível
                val unified = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "machines:m-1" to MergeConflictResolution.USE_BACKUP,
                        "machineBrandRates:m-1:VISA" to MergeConflictResolution.USE_BACKUP,
                        "machineInstallmentRates:m-1:2" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertTrue(unified.isReadyForNextPhase)
                assertEquals(
                    EligibleOperationDisposition.APPLY_BACKUP,
                    unified.eligibleUnits.single { it.unitType == MergeUnitType.MACHINE && it.unitId == "m-1" }.disposition,
                )

                // Taxa corrompida (> 100%) no backup ao tentar USE_BACKUP -> bloqueada
                val validBackup = readResult.validatedData!!
                val corruptedBackup = ValidatedBackupData(
                    format = validBackup.format,
                    backupSchemaVersion = validBackup.backupSchemaVersion,
                    applicationId = validBackup.applicationId,
                    roomSchemaVersion = validBackup.roomSchemaVersion,
                    exportedAtEpochMs = validBackup.exportedAtEpochMs,
                    preferences = validBackup.preferences,
                    people = validBackup.people,
                    products = validBackup.products,
                    productParticipants = validBackup.productParticipants,
                    machines = validBackup.machines,
                    machineBrandRates = validBackup.machineBrandRates.map { it.copy(rateBps = 9_950) },
                    machineInstallmentRates = validBackup.machineInstallmentRates,
                    sales = validBackup.sales,
                    saleItems = validBackup.saleItems,
                    saleItemParticipants = validBackup.saleItemParticipants,
                    salePersonTotals = validBackup.salePersonTotals,
                    declaredCounts = validBackup.declaredCounts,
                )
                val blockedInvalidRates = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    validatedBackup = corruptedBackup,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "machines:m-1" to MergeConflictResolution.USE_BACKUP,
                        "machineBrandRates:m-1:VISA" to MergeConflictResolution.USE_BACKUP,
                        "machineInstallmentRates:m-1:2" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertFalse(blockedInvalidRates.isReadyForNextPhase)
                assertTrue(blockedInvalidRates.blockedOperations.any {
                    it.type == DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR &&
                        it.relatedUnitType == MergeUnitType.MACHINE
                })
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test09_saleWithDivergentComponentsRejectsPartialMixingAndValidatesFullFinancialUnit() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                val refs = populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
                populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)

                val saleConflicts = plan.conflictItems.filter {
                    it.relatedUnitType == MergeUnitType.SALE && it.relatedUnitId == refs.saleId
                }
                assertTrue(saleConflicts.size >= 4)

                // Mistura inválida: cabeçalho da venda USE_BACKUP e demais itens KEEP_LOCAL
                val mixedDecisions = saleConflicts.associate { conflict ->
                    val key = "${conflict.collection.key}:${conflict.recordId}"
                    val res = if (conflict.collection == BackupCollectionType.SALES) {
                        MergeConflictResolution.USE_BACKUP
                    } else {
                        MergeConflictResolution.KEEP_LOCAL
                    }
                    key to res
                }
                val incompatible = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, mixedDecisions)
                assertFalse(incompatible.isReadyForNextPhase)
                assertTrue(incompatible.incompatibleDecisions.any {
                    it.type == DecisionValidationIssueType.INCOMPATIBLE_UNIT_DECISION &&
                        it.relatedUnitType == MergeUnitType.SALE
                })

                // Decisão unificada USE_BACKUP em toda a venda -> elegível atomicamente
                val unifiedDecisions = saleConflicts.associate { "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP }
                val validUnified = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, unifiedDecisions)
                assertTrue(validUnified.isReadyForNextPhase)
                assertEquals(
                    EligibleOperationDisposition.APPLY_BACKUP,
                    validUnified.eligibleUnits.single { it.unitType == MergeUnitType.SALE && it.unitId == refs.saleId }.disposition,
                )

                // Venda financeiramente inconsistente no backup ao aplicar USE_BACKUP -> bloqueada
                val validData = readResult.validatedData!!
                val brokenSaleData = ValidatedBackupData(
                    format = validData.format,
                    backupSchemaVersion = validData.backupSchemaVersion,
                    applicationId = validData.applicationId,
                    roomSchemaVersion = validData.roomSchemaVersion,
                    exportedAtEpochMs = validData.exportedAtEpochMs,
                    preferences = validData.preferences,
                    people = validData.people,
                    products = validData.products,
                    productParticipants = validData.productParticipants,
                    machines = validData.machines,
                    machineBrandRates = validData.machineBrandRates,
                    machineInstallmentRates = validData.machineInstallmentRates,
                    sales = validData.sales.map { it.copy(netTotalCents = it.netTotalCents - 1L) },
                    saleItems = validData.saleItems,
                    saleItemParticipants = validData.saleItemParticipants,
                    salePersonTotals = validData.salePersonTotals,
                    declaredCounts = validData.declaredCounts,
                )
                val blockedSale = JsonBackupMergeDecisionValidator.validate(plan, brokenSaleData, localDb, unifiedDecisions)
                assertFalse(blockedSale.isReadyForNextPhase)
                assertTrue(blockedSale.blockedOperations.any {
                    it.type == DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR &&
                        it.relatedUnitType == MergeUnitType.SALE
                })
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test10_normalizedNameCollisionBlocksUseBackupAndAllowsKeepLocalOrIgnoreImport() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "person-b" }, { 1_730_000_100_000L })
                    .createPerson("Ana Silva")
                RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "person-l" }, { 1_730_000_200_000L })
                    .createPerson("  ana silva ")

                RoomMachineRepository(backupDb.machineDao(), idGenerator = { "mach-b" }, clockMillis = { 1_730_000_100_000L })
                    .createMachine(MachineInputDraft("Stone Pro", 199, true, 450, false, emptyMap(), false, emptyMap()))
                RoomMachineRepository(localDb.machineDao(), idGenerator = { "mach-l" }, clockMillis = { 1_730_000_200_000L })
                    .createMachine(MachineInputDraft("  stone pro ", 210, true, 480, false, emptyMap(), false, emptyMap()))

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)

                // Tentar USE_BACKUP em colisão sem remapeamento -> bloqueado (evita violação UNIQUE)
                val blocked = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "people:person-b" to MergeConflictResolution.USE_BACKUP,
                        "machines:mach-b" to MergeConflictResolution.USE_BACKUP,
                    ),
                )
                assertFalse(blocked.isReadyForNextPhase)
                assertEquals(2, blocked.blockedOperationsCount)
                assertTrue(blocked.blockedOperations.all { it.type == DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED })

                // KEEP_LOCAL / IGNORE_IMPORT preservam os registros locais sem colisão
                val allowed = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf(
                        "people:person-b" to MergeConflictResolution.KEEP_LOCAL,
                        "machines:mach-b" to MergeConflictResolution.IGNORE_IMPORT,
                    ),
                )
                assertTrue(allowed.isReadyForNextPhase)
                assertEquals(0, allowed.blockedOperationsCount)
                assertEquals(
                    EligibleOperationDisposition.SKIP_BACKUP,
                    allowed.eligibleStandaloneRecords.single { it.recordId == "person-b" }.disposition,
                )
                assertEquals(
                    EligibleOperationDisposition.SKIP_BACKUP,
                    allowed.eligibleUnits.single { it.unitType == MergeUnitType.MACHINE && it.unitId == "mach-b" }.disposition,
                )
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test11_unresolvedPersonDependencyBlocksProductUntilExplicitlyResolved() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                // Pessoa p-1 existe nos dois bancos com nomes diferentes (mesmo ID) -> SAME_KEY_DATA_DIVERGENCE
                val pB = RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
                    .createPerson("Ana Backup")
                RoomCatalogRepository(localDb.personDao(), localDb.productDao(), { "p-1" }, { 1_730_000_000_000L })
                    .createPerson("Ana Local")

                // Produto novo no backup depende da pessoa p-1
                RoomCatalogRepository(backupDb.personDao(), backupDb.productDao(), { "prod-new" }, { 1_730_000_100_000L })
                    .createProduct(
                        name = "Chaveiro 3D",
                        price = MoneyCents(3_500L),
                        active = true,
                        participants = listOf(ProductParticipantDraft(pB.id, MoneyCents(3_500L))),
                        feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                        feeOwnerPersonId = pB.id,
                    )

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_200_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)

                // Sem resolver pessoa p-1, produto novo fica pendente
                val pending = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, emptyMap())
                assertFalse(pending.isReadyForNextPhase)
                assertEquals(1, pending.pendingDependenciesCount)
                assertEquals("prod-new", pending.pendingDependencies.single().recordId)

                // Resolvendo p-1 com USE_BACKUP, prod-new passa a ser elegível (ADD_NEW)
                val resolved = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf("people:p-1" to MergeConflictResolution.USE_BACKUP),
                )
                assertTrue(resolved.isReadyForNextPhase)
                assertEquals(0, resolved.pendingDependenciesCount)
                assertEquals(
                    EligibleOperationDisposition.ADD_NEW,
                    resolved.eligibleUnits.single { it.unitType == MergeUnitType.PRODUCT && it.unitId == "prod-new" }.disposition,
                )
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test12_historicalSaleWithoutCatalogEntriesIsEligibleForNextPhase() = runBlocking<Unit>(Dispatchers.IO) {
        val backupDb = newInMemoryDb()
        val localDb = newInMemoryDb()
        try {
            val refs = populateStandardScenario(backupDb, idPrefix = "hist")
            val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_000_000L)

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
            val result = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb)

            assertTrue(result.isReadyForNextPhase)
            assertEquals(0, result.pendingDependenciesCount)
            assertEquals(0, result.blockedOperationsCount)
            val saleUnit = result.eligibleUnits.single { it.unitType == MergeUnitType.SALE && it.unitId == refs.saleId }
            assertEquals(EligibleOperationDisposition.ADD_NEW, saleUnit.disposition)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }

    @Test
    fun test13_fingerprintPreservedAndDetectsLocalDatabaseMutationAfterPlanning() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_100_000L))
                localDb.preferenceDao().upsert(PreferenceEntity("app_theme", "dark", 1_730_000_200_000L))

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_000_300_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)

                // Antes da mutação: fingerprint bate
                val beforeMutation = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf("preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL),
                )
                assertTrue(beforeMutation.localStateMatchesPlan)
                assertEquals(plan.preconditions.expectedLocalStateFingerprint, beforeMutation.expectedLocalStateFingerprint)
                assertEquals(beforeMutation.expectedLocalStateFingerprint, beforeMutation.observedLocalStateFingerprint)
                assertTrue(beforeMutation.isReadyForNextPhase)

                // Altera o Room local após o planejamento
                localDb.preferenceDao().upsert(PreferenceEntity("extra_key", "mutated", 1_730_000_400_000L))

                val afterMutation = JsonBackupMergeDecisionValidator.validate(
                    plan = plan,
                    readResult = readResult,
                    database = localDb,
                    decisionsByConflictKey = mapOf("preferences:app_theme" to MergeConflictResolution.KEEP_LOCAL),
                )
                assertFalse(afterMutation.localStateMatchesPlan)
                assertEquals(plan.preconditions.expectedLocalStateFingerprint, afterMutation.expectedLocalStateFingerprint)
                assertTrue(afterMutation.observedLocalStateFingerprint != afterMutation.expectedLocalStateFingerprint)
                assertTrue(afterMutation.blockedOperations.any { it.type == DecisionValidationIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING })
                assertFalse(afterMutation.isReadyForNextPhase)
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test14_validationResultIsDeterministicAcrossRepeatedRunsAndKeyOrders() =
        runBlocking<Unit>(Dispatchers.IO) {
            val backupDb = newInMemoryDb()
            val localDb = newInMemoryDb()
            try {
                populateStandardScenario(backupDb, idPrefix = "same", saleFinalOverrideCents = 18_000L)
                populateStandardScenario(localDb, idPrefix = "same", saleFinalOverrideCents = 17_000L)
                backupDb.preferenceDao().upsert(PreferenceEntity("app_theme", "light", 1_730_000_999_000L))

                val exported = JsonBackupExporter.export(backupDb, ZoneId.of("UTC"), 1_730_001_000_000L)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                val plan = JsonBackupMergePlanner.plan(readResult, localDb)

                val decisionsForward = LinkedHashMap<String, MergeConflictResolution>().apply {
                    plan.conflictItems.forEach {
                        put("${it.collection.key}:${it.recordId}", MergeConflictResolution.USE_BACKUP)
                    }
                }
                val decisionsReversed = LinkedHashMap<String, MergeConflictResolution>().apply {
                    plan.conflictItems.reversed().forEach {
                        put("${it.collection.key}:${it.recordId}", MergeConflictResolution.USE_BACKUP)
                    }
                }

                val r1 = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisionsForward)
                val r2 = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, decisionsReversed)

                assertEquals(r1, r2)
                assertEquals(r1.hashCode(), r2.hashCode())
                assertTrue(r1.isReadyForNextPhase)
            } finally {
                backupDb.close()
                localDb.close()
            }
        }

    @Test
    fun test15_validationNeverWritesOrModifiesLocalRoomDatabase() = runBlocking<Unit>(Dispatchers.IO) {
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
            val allUseBackup = plan.conflictItems.associate {
                "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
            }

            val result = JsonBackupMergeDecisionValidator.validate(plan, readResult, localDb, allUseBackup)
            assertTrue(result.isReadyForNextPhase)

            val localAfter = JsonBackupExporter.export(localDb, ZoneId.of("UTC"), 1_730_000_000_000L)
            assertEquals(localBefore.jsonContent, localAfter.jsonContent)
        } finally {
            backupDb.close()
            localDb.close()
        }
    }
}
