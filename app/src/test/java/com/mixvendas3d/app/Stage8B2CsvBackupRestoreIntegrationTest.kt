package com.mixvendas3d.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.CsvExportColumns
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.export.ExecutorTestHooks
import com.mixvendas3d.app.domain.export.JsonBackupComparator
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
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage8B2CsvBackupRestoreIntegrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdTestDbNames = linkedSetOf<String>()
    private val openedDatabases = mutableListOf<AppDatabase>()

    @After
    fun tearDown() {
        openedDatabases.forEach { db ->
            if (db.isOpen) {
                db.close()
            }
        }
        openedDatabases.clear()

        createdTestDbNames.forEach { dbName ->
            assertNotEquals(AppDatabase.DATABASE_NAME, dbName)
            context.deleteDatabase(dbName)
        }
        createdTestDbNames.clear()
    }

    private fun openTestDb(tag: String): AppDatabase {
        val name = shortTestDatabaseName(tag)
        assertNotEquals(AppDatabase.DATABASE_NAME, name)
        createdTestDbNames.add(name)
        context.deleteDatabase(name)
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
            )
            .allowMainThreadQueries()
            .build()
            .also { openedDatabases.add(it) }
    }

    private fun parseCsvLines(csvContent: String): List<List<String>> {
        val withoutBom = csvContent.removePrefix("\uFEFF")
        return withoutBom
            .split("\r\n")
            .filter { it.isNotEmpty() }
            .map { line -> line.split(';') }
    }

    @Test
    fun `01 csv export summary and detailed validate formula injection protection and exact formatting`() = runBlocking(Dispatchers.IO) {
        val zone = ZoneId.of("America/Sao_Paulo")
        val soldEpochMs = ZonedDateTime.of(2025, 3, 15, 14, 30, 45, 0, zone).toInstant().toEpochMilli()
        val db = openTestDb("csv_export")
        val prefRepo = RoomPreferenceRepository(preferenceDao = db.preferenceDao(), clockMillis = { soldEpochMs })
        val catalogRepo = RoomCatalogRepository(personDao = db.personDao(), productDao = db.productDao(), clockMillis = { soldEpochMs })
        val machRepo = RoomMachineRepository(machineDao = db.machineDao(), clockMillis = { soldEpochMs })
        val saleRepo = RoomSaleRepository(database = db, saleDao = db.saleDao(), clockMillis = { soldEpochMs })

        prefRepo.savePreference("test_mode", "active")
        val ana = catalogRepo.createPerson("Ana CSV")
        val bruno = catalogRepo.createPerson("Bruno CSV")

        val prod = catalogRepo.createProduct(
            name = "Vaso 3D",
            price = MoneyCents(5_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )

        val machine = machRepo.createMachine(
            MachineInputDraft(
                name = "Stone CSV",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 450,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 400),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 2 to 150),
            ),
        )

        // 2x 50,00 = 100,00 subtotal; desconto 10,00 -> 90,00 final; taxa 4,00% + 1,50% = 5,50% (550 bps) = 4,95; líquido = 85,05
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(9_000L),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.VISA,
                installments = 2,
                note = "=SUM(A1:A10)",
            ),
        )
        val saved = saleRepo.saveSale(calc)

        assertEquals(10_000L, saved.calculation.originalSubtotal.rawCents)
        assertEquals(1_000L, saved.calculation.discountTotal.rawCents)
        assertEquals(9_000L, saved.calculation.finalAmount.rawCents)
        assertEquals(550, saved.calculation.effectiveFeeRateBps)
        assertEquals(495L, saved.calculation.feeTotal.rawCents)
        assertEquals(8_505L, saved.calculation.netTotal.rawCents)

        val sales = saleRepo.observeSales().first()
        val exportResult = CsvSaleExporter.export(
            sales = sales,
            zoneId = zone,
            generatedOnDate = LocalDate.of(2025, 3, 15),
        )

        assertEquals(1, exportResult.exportedSalesCount)
        assertEquals(2, exportResult.detailedRowsCount)
        assertTrue(exportResult.summaryCsvContent.startsWith("\uFEFF"))
        assertTrue(exportResult.detailedCsvContent.startsWith("\uFEFF"))
        assertTrue(exportResult.summaryCsvContent.endsWith("\r\n"))
        assertTrue(exportResult.detailedCsvContent.endsWith("\r\n"))

        val summaryLines = parseCsvLines(exportResult.summaryCsvContent)
        assertEquals(2, summaryLines.size)
        assertEquals(CsvExportColumns.SUMMARY_HEADERS, summaryLines[0])
        val sRow = summaryLines[1]
        assertEquals(saved.id, sRow[0])
        assertEquals("15/03/2025", sRow[1])
        assertEquals("14:30:45", sRow[2])
        assertEquals("15/03/2025", sRow[3])
        assertEquals("2", sRow[4])
        assertEquals("100,00", sRow[5])
        assertEquals("10,00", sRow[6])
        assertEquals("90,00", sRow[7])
        assertEquals("Crédito", sRow[8])
        assertEquals("Stone CSV", sRow[9])
        assertEquals("Visa", sRow[10])
        assertEquals("2x", sRow[11])
        assertEquals("", sRow[12])
        assertEquals("4,95", sRow[13])
        assertEquals("85,05", sRow[14])
        assertEquals("'=SUM(A1:A10)", sRow[15])

        val detailedLines = parseCsvLines(exportResult.detailedCsvContent)
        assertEquals(3, detailedLines.size)
        assertEquals(CsvExportColumns.DETAILED_HEADERS, detailedLines[0])
        val anaRow = detailedLines.drop(1).first { it[11] == ana.id }
        assertEquals(saved.id, anaRow[0])
        assertEquals("15/03/2025", anaRow[1])
        assertEquals("14:30:45", anaRow[2])
        assertEquals("Vaso 3D", anaRow[5])
        assertEquals("2", anaRow[6])
        assertEquals("50,00", anaRow[7])
        assertEquals("100,00", anaRow[8])
        assertEquals("10,00", anaRow[9])
        assertEquals("90,00", anaRow[10])
        // Desconto R$ 10,00 dividido igualmente entre Ana e Bruno (-R$ 5,00 cada):
        // Ana: 60,00 -> 55,00 bruto; Bruno: 40,00 -> 35,00 bruto.
        // Tarifa total 495 bps * 9000 / 10000 = 4,95 (495 centavos): 495*5500/9000 = 302.5 e 495*3500/9000 = 192.5,
        // empate determinístico por personId -> um fica com 302/303 e o outro com 193/192, somando 495.
        val anaTotal = saved.calculation.personTotals.first { it.personId == ana.id }
        val brunoTotal = saved.calculation.personTotals.first { it.personId == bruno.id }
        assertEquals(5_500L, anaTotal.grossAmount.rawCents)
        assertEquals(3_500L, brunoTotal.grossAmount.rawCents)
        assertTrue(anaTotal.feeAmount.rawCents in listOf(302L, 303L))
        assertTrue(brunoTotal.feeAmount.rawCents in listOf(192L, 193L))
        assertEquals(495L, anaTotal.feeAmount.rawCents + brunoTotal.feeAmount.rawCents)

        assertEquals("Ana CSV", anaRow[12])
        assertEquals("55,00", anaRow[13])
        assertEquals(com.mixvendas3d.app.util.CurrencyFormatter.formatCentsOrZeroForInput(anaTotal.feeAmount.rawCents), anaRow[14])
        assertEquals(com.mixvendas3d.app.util.CurrencyFormatter.formatCentsOrZeroForInput(anaTotal.netAmount.rawCents), anaRow[15])
        assertEquals("5,50%", anaRow[20])
        assertEquals("4,95", anaRow[21])
        assertEquals(FeeResponsibilityMode.PROPORTIONAL.label, anaRow[22])

        val brunoRow = detailedLines.drop(1).first { it[11] == bruno.id }
        assertEquals("Bruno CSV", brunoRow[12])
        assertEquals("35,00", brunoRow[13])
        assertEquals(com.mixvendas3d.app.util.CurrencyFormatter.formatCentsOrZeroForInput(brunoTotal.feeAmount.rawCents), brunoRow[14])
        assertEquals(com.mixvendas3d.app.util.CurrencyFormatter.formatCentsOrZeroForInput(brunoTotal.netAmount.rawCents), brunoRow[15])
    }

    @Test
    fun `02 csv export updates correctly after editing sale preserving ID and totals`() = runBlocking(Dispatchers.IO) {
        val zone = ZoneId.of("America/Sao_Paulo")
        val createdEpochMs = ZonedDateTime.of(2025, 4, 10, 9, 15, 0, 0, zone).toInstant().toEpochMilli()
        val updatedEpochMs = ZonedDateTime.of(2025, 4, 12, 16, 45, 30, 0, zone).toInstant().toEpochMilli()
        var currentClock = createdEpochMs

        val db = openTestDb("csv_edit")
        val catalogRepo = RoomCatalogRepository(personDao = db.personDao(), productDao = db.productDao(), clockMillis = { currentClock })
        val machRepo = RoomMachineRepository(machineDao = db.machineDao(), clockMillis = { currentClock })
        val saleRepo = RoomSaleRepository(database = db, saleDao = db.saleDao(), clockMillis = { currentClock })

        val ana = catalogRepo.createPerson("Ana Edit")
        val prod = catalogRepo.createProduct(
            name = "Luminária",
            price = MoneyCents(8_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(8_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machine = machRepo.createMachine(
            MachineInputDraft(
                name = "Ton Edit",
                debitRateBps = 200,
                creditEnabled = false,
                creditDefaultRateBps = 0,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )

        val initialCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine,
            ),
        )
        val saved = saleRepo.saveSale(initialCalc)
        assertNotNull(saved.id)
        assertEquals(createdEpochMs, saved.soldAtEpochMs)

        val firstExport = CsvSaleExporter.export(
            sales = saleRepo.observeSales().first(),
            zoneId = zone,
            generatedOnDate = LocalDate.of(2025, 4, 10),
        )
        assertEquals(1, firstExport.exportedSalesCount)

        // Avança o relógio e edita a venda para quantidade = 2 (subtotal 160,00, débito 2,00% -> taxa 3,20, líq 156,80)
        currentClock = updatedEpochMs
        val updatedCalc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine,
                note = "Editada",
            ),
        )
        val updated = saleRepo.updateSale(saved.id, updatedCalc)
        assertEquals(saved.id, updated.id)
        assertEquals(createdEpochMs, updated.soldAtEpochMs)
        assertEquals(updatedEpochMs, updated.updatedAtEpochMs)

        val persistedList = saleRepo.observeSales().first()
        assertEquals(1, persistedList.size)

        val secondExport = CsvSaleExporter.export(
            sales = persistedList,
            zoneId = zone,
            generatedOnDate = LocalDate.of(2025, 4, 12),
        )
        assertEquals(1, secondExport.exportedSalesCount)

        val summaryLines = parseCsvLines(secondExport.summaryCsvContent)
        assertEquals(2, summaryLines.size)
        val sRow = summaryLines[1]
        assertEquals(saved.id, sRow[0])
        assertEquals("10/04/2025", sRow[1])
        assertEquals("09:15:00", sRow[2])
        assertEquals("12/04/2025", sRow[3])
        assertEquals("2", sRow[4])
        assertEquals("160,00", sRow[5])
        assertEquals("0,00", sRow[6])
        assertEquals("160,00", sRow[7])
        assertEquals("Débito", sRow[8])
        assertEquals("Ton Edit", sRow[9])
        assertEquals("3,20", sRow[13])
        assertEquals("156,80", sRow[14])
        assertEquals("Editada", sRow[15])

        val detailedLines = parseCsvLines(secondExport.detailedCsvContent)
        assertEquals(2, detailedLines.size)
        assertEquals("Luminária", detailedLines[1][5])
        assertEquals("2", detailedLines[1][6])
        assertEquals("160,00", detailedLines[1][10])
        assertEquals("Ana Edit", detailedLines[1][12])
        assertEquals("3,20", detailedLines[1][14])
        assertEquals("156,80", detailedLines[1][15])
    }

    @Test
    fun `03 complete json backup export and reader validation across all 11 collections`() = runBlocking(Dispatchers.IO) {
        val fixedMs = 1_745_000_000_000L
        val dbA = openTestDb("backup_a")
        RoomPreferenceRepository(preferenceDao = dbA.preferenceDao(), clockMillis = { fixedMs }).savePreference("mode", "rich")
        val catalogRepo = RoomCatalogRepository(personDao = dbA.personDao(), productDao = dbA.productDao(), clockMillis = { fixedMs })
        val machRepo = RoomMachineRepository(machineDao = dbA.machineDao(), clockMillis = { fixedMs })
        val saleRepo = RoomSaleRepository(database = dbA, saleDao = dbA.saleDao(), clockMillis = { fixedMs })

        val ana = catalogRepo.createPerson("Ana Backup")
        val bruno = catalogRepo.createPerson("Bruno Backup")
        val prod = catalogRepo.createProduct(
            name = "Dragão Backup",
            price = MoneyCents(15_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(9_000L)),
                ProductParticipantDraft(bruno.id, MoneyCents(6_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machine = machRepo.createMachine(
            MachineInputDraft(
                name = "Stone Backup",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 450,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.MASTERCARD to 450),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 3 to 250),
            ),
        )
        // 150,00 com desconto para 140,00, Crédito Mastercard 3x (4,50% + 2,50% = 7,00% = 700 bps) -> taxa 9,80 (980L), líq 130,20 (13_020L)
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                finalAmountOverride = MoneyCents(14_000L),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.MASTERCARD,
                installments = 3,
                note = "Backup Completo",
            ),
        )
        val savedSale = saleRepo.saveSale(calc)

        val backupResult = JsonBackupExporter.export(dbA, ZoneId.of("UTC"), fixedMs + 5_000L)
        assertNotNull(backupResult.jsonContent)

        val expectedCounts = mapOf(
            "preferences" to 1,
            "people" to 2,
            "products" to 1,
            "productParticipants" to 2,
            "machines" to 1,
            "machineBrandRates" to 1,
            "machineInstallmentRates" to 2,
            "sales" to 1,
            "saleItems" to 1,
            "saleItemParticipants" to 2,
            "salePersonTotals" to 2,
            "productCategories" to 0,
        )
        assertEquals(expectedCounts, backupResult.collectionsCounts)
        assertEquals(16, backupResult.totalRecordsCount)

        val readResult = JsonBackupReader.readAndValidate(backupResult.utf8Bytes)
        assertTrue(readResult.isValid)
        assertNotNull(readResult.validatedData)
        assertEquals(expectedCounts, readResult.collectionSummary.allCollectionsCounts)
        assertEquals(2, readResult.peopleCount)
        assertEquals(1, readResult.productsCount)
        assertEquals(1, readResult.machinesCount)
        assertEquals(1, readResult.salesCount)
        assertEquals(1, readResult.saleItemsCount)

        val data = readResult.validatedData!!
        val saleDto = data.sales.single()
        assertEquals(savedSale.id, saleDto.id)
        assertEquals(fixedMs, saleDto.soldAtEpochMs)
        assertEquals(15_000L, saleDto.originalSubtotalCents)
        assertEquals(1_000L, saleDto.discountTotalCents)
        assertEquals(14_000L, saleDto.finalAmountCents)
        assertEquals(980L, saleDto.feeTotalCents)
        assertEquals(13_020L, saleDto.netTotalCents)
        assertEquals(700, saleDto.effectiveFeeRateBps)
        assertEquals(2, data.salePersonTotals.size)
        assertEquals(13_020L, data.salePersonTotals.sumOf { it.netAmountCents })
    }

    @Test
    fun `04 cross database backup restore from bank a to bank b through real pipeline`() = runBlocking(Dispatchers.IO) {
        val fixedMs = 1_746_000_000_000L
        val dbA = openTestDb("restore_a")
        val dbB = openTestDb("restore_b")

        RoomPreferenceRepository(preferenceDao = dbA.preferenceDao(), clockMillis = { fixedMs }).savePreference("currency", "BRL")
        val catalogA = RoomCatalogRepository(personDao = dbA.personDao(), productDao = dbA.productDao(), clockMillis = { fixedMs })
        val machA = RoomMachineRepository(machineDao = dbA.machineDao(), clockMillis = { fixedMs })
        val saleA = RoomSaleRepository(database = dbA, saleDao = dbA.saleDao(), clockMillis = { fixedMs })

        val ana = catalogA.createPerson("Ana Origem")
        val bruno = catalogA.createPerson("Bruno Origem")
        val prod = catalogA.createProduct(
            name = "Item Origem",
            price = MoneyCents(8_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(5_000L)),
                ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = ana.id,
        )
        val machine = machA.createMachine(
            MachineInputDraft(
                name = "Maquininha A",
                debitRateBps = 200,
                creditEnabled = true,
                creditDefaultRateBps = 400,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 350),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(2 to 150),
            ),
        )
        // Crédito Visa 2x: 3,50% + 1,50% = 5,00% (500 bps) sobre R$ 80,00 -> taxa R$ 4,00 (400L) toda na Ana; líquido R$ 76,00 (7_600L)
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.VISA,
                installments = 2,
                note = "Histórica A->B",
            ),
        )
        val savedSale = saleA.saveSale(calc)

        val backupResult = JsonBackupExporter.export(dbA, ZoneId.of("UTC"), fixedMs + 1_000L)
        val snapshotA = JsonBackupExporter.captureConsistentSnapshot(dbA)

        val readResult = JsonBackupReader.readAndValidate(backupResult.utf8Bytes)
        assertTrue(readResult.isValid)

        val plan = JsonBackupMergePlanner.plan(readResult, dbB)
        val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, dbB)
        val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, dbB)
        assertTrue(preview.isSafeToApply)

        val execResult = JsonBackupMergeExecutor.execute(
            readResult = readResult,
            plan = plan,
            decisionsByConflictKey = emptyMap(),
            decisionValidation = validation,
            previewResult = preview,
            executionContract = preview.executionContract,
            userConfirmed = true,
            database = dbB,
        )

        assertTrue(execResult.success)
        assertFalse(execResult.rolledBack)
        assertEquals(snapshotA, JsonBackupExporter.captureConsistentSnapshot(dbB))

        val restoredSales = RoomSaleRepository(database = dbB, saleDao = dbB.saleDao()).observeSales().first()
        assertEquals(1, restoredSales.size)
        val restored = restoredSales[0]
        assertEquals(savedSale.id, restored.id)
        assertEquals(fixedMs, restored.soldAtEpochMs)
        assertEquals(fixedMs, restored.updatedAtEpochMs)
        assertEquals(8_000L, restored.calculation.originalSubtotal.rawCents)
        assertEquals(0L, restored.calculation.discountTotal.rawCents)
        assertEquals(8_000L, restored.calculation.finalAmount.rawCents)
        assertEquals(400L, restored.calculation.feeTotal.rawCents)
        assertEquals(7_600L, restored.calculation.netTotal.rawCents)
        assertEquals(500, restored.calculation.effectiveFeeRateBps)
        val totalsByPerson = restored.calculation.personTotals.associateBy { it.personId }
        assertEquals(4_600L, totalsByPerson.getValue(ana.id).netAmount.rawCents)
        assertEquals(400L, totalsByPerson.getValue(ana.id).feeAmount.rawCents)
        assertEquals(3_000L, totalsByPerson.getValue(bruno.id).netAmount.rawCents)
        assertEquals(0L, totalsByPerson.getValue(bruno.id).feeAmount.rawCents)
    }

    @Test
    fun `05 conflict resolution with KEEP_LOCAL, USE_BACKUP, IGNORE_IMPORT and atomic sales preservation`() = runBlocking(Dispatchers.IO) {
        val tBackup = 1_747_100_000_000L
        val tLocal = 1_747_000_000_000L
        val dbA = openTestDb("conf_a")
        val dbB = openTestDb("conf_b")

        // --- Dados compartilhados e divergentes entre Banco A (backup) e Banco B (local) ---
        // 1) Preferência em conflito (KEEP_LOCAL)
        dbA.preferenceDao().upsert(PreferenceEntity("pref_theme", "light_backup", tBackup))
        dbB.preferenceDao().upsert(PreferenceEntity("pref_theme", "dark_local", tLocal))

        // 2) Pessoa com mesmo ID e divergência (USE_BACKUP)
        val personUseBackupId = "person-shared-use"
        dbA.personDao().insert(PersonEntity(personUseBackupId, "Ana Backup", "ana backup", 0, tBackup, tBackup))
        dbB.personDao().insert(PersonEntity(personUseBackupId, "Ana Local", "ana local", 1, tLocal, tLocal))

        // 3) Colisão de nome normalizado com IDs diferentes (IGNORE_IMPORT)
        dbA.personDao().insert(PersonEntity("person-bkp-collision", "Carlos Colisão", "carlos colisao", 2, tBackup, tBackup))
        dbB.personDao().insert(PersonEntity("person-loc-existing", "Carlos Colisão", "carlos colisao", 4, tLocal, tLocal))

        // Auxiliares em A e B para compor vendas atômicas reais via SaleFinancialEngine
        val catA = RoomCatalogRepository(dbA.personDao(), dbA.productDao(), { "a-${UUID.randomUUID()}" }, { tBackup })
        val saleRepoA = RoomSaleRepository(dbA, dbA.saleDao(), { "sale-a-${UUID.randomUUID()}" }, { tBackup })
        val machRepoA = RoomMachineRepository(
            machineDao = dbA.machineDao(),
            idGenerator = { "mach-a-${UUID.randomUUID()}" },
            clockMillis = { tBackup },
        )

        val catB = RoomCatalogRepository(dbB.personDao(), dbB.productDao(), { "b-${UUID.randomUUID()}" }, { tLocal })
        val saleRepoB = RoomSaleRepository(dbB, dbB.saleDao(), { "sale-b-${UUID.randomUUID()}" }, { tLocal })
        val machRepoB = RoomMachineRepository(
            machineDao = dbB.machineDao(),
            idGenerator = { "mach-b-${UUID.randomUUID()}" },
            clockMillis = { tLocal },
        )

        val pessoaExtraA = catA.createPerson("Marina Backup")
        val pessoaExtraB = catB.createPerson("Lucas Local")

        val prodBkpS1 = catA.createProduct(
            name = "Produto S1 Backup",
            price = MoneyCents(6_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(pessoaExtraA.id, MoneyCents(6_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val prodLocS1 = catB.createProduct(
            name = "Produto S1 Local",
            price = MoneyCents(7_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(pessoaExtraB.id, MoneyCents(7_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machA = machRepoA.createMachine(
            MachineInputDraft("Maq A", 200, true, 500, false, emptyMap(), false, emptyMap()),
        )
        val machB = machRepoB.createMachine(
            MachineInputDraft("Maq B", 250, true, 400, false, emptyMap(), false, emptyMap()),
        )

        // Venda S1 conflitante (USE_BACKUP -> substituição atômica sem mistura)
        val s1Id = "sale-shared-use-backup"
        RoomSaleRepository(dbA, dbA.saleDao(), { s1Id }, { tBackup }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodBkpS1, 2)), // 120,00 Pix
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "S1 Backup",
                ),
            ),
        )
        RoomSaleRepository(dbB, dbB.saleDao(), { s1Id }, { tLocal }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodLocS1, 1)), // 70,00 Dinheiro
                    paymentMethod = SalePaymentMethod.CASH,
                    note = "S1 Local",
                ),
            ),
        )

        // Venda S2 conflitante (KEEP_LOCAL -> preserva snapshot histórico local)
        val s2Id = "sale-shared-keep-local"
        RoomSaleRepository(dbA, dbA.saleDao(), { s2Id }, { tBackup }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodBkpS1, 3)), // 180,00 Crédito 5% -> taxa 9,00, líq 171,00
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machA,
                    note = "S2 Backup",
                ),
            ),
        )
        val s2LocalOriginal = RoomSaleRepository(dbB, dbB.saleDao(), { s2Id }, { tLocal }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodLocS1, 1)), // 70,00 Débito 2,5% -> taxa 1,75, líq 68,25
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machB,
                    note = "S2 Local Histórico",
                ),
            ),
        )

        // Venda S3 conflitante (IGNORE_IMPORT -> não importa backup e mantém local)
        val s3Id = "sale-shared-ignore"
        RoomSaleRepository(dbA, dbA.saleDao(), { s3Id }, { tBackup }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodBkpS1, 4)), // 240,00
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "S3 Backup Ignorar",
                ),
            ),
        )
        val s3LocalOriginal = RoomSaleRepository(dbB, dbB.saleDao(), { s3Id }, { tLocal }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodLocS1, 2)), // 140,00 com desconto para 130,00
                    finalAmountOverride = MoneyCents(13_000L),
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "S3 Local Preservada",
                ),
            ),
        )

        // Venda S4 somente local (ausente do backup -> jamais excluída)
        val s4LocalOnly = saleRepoB.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodLocS1, 1)),
                    paymentMethod = SalePaymentMethod.CASH,
                    note = "S4 Somente Local",
                ),
            ),
        )

        // Venda S5 somente backup (nova -> adicionada ao Banco B)
        val s5BackupOnly = saleRepoA.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodBkpS1, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machA,
                    note = "S5 Somente Backup",
                ),
            ),
        )

        val backupResult = JsonBackupExporter.export(dbA, ZoneId.of("UTC"), tBackup + 10_000L)
        val readResult = JsonBackupReader.readAndValidate(backupResult.utf8Bytes)
        assertTrue(readResult.isValid)

        val plan = JsonBackupMergePlanner.plan(readResult, dbB)
        assertTrue(plan.conflictItems.isNotEmpty())
        // Nenhuma decisão é selecionada automaticamente
        assertTrue(plan.conflictItems.all { it.selectedResolution == null })

        val autoValidation = JsonBackupMergeDecisionValidator.validate(plan, readResult, dbB, emptyMap())
        assertFalse(autoValidation.isReadyForNextPhase)
        assertEquals(plan.conflictItems.size, autoValidation.unresolvedConflictsCount)
        val autoPreview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), autoValidation, dbB)
        assertFalse(autoPreview.isSafeToApply)
        val rejectedAutoExec = JsonBackupMergeExecutor.execute(
            readResult = readResult,
            plan = plan,
            decisionsByConflictKey = emptyMap(),
            decisionValidation = autoValidation,
            previewResult = autoPreview,
            executionContract = autoPreview.executionContract,
            userConfirmed = true,
            database = dbB,
        )
        assertFalse(rejectedAutoExec.success)
        assertEquals(MergeExecutionFailureReason.UNRESOLVED_CONFLICTS, rejectedAutoExec.failureReason)

        // Atribui decisões explícitas por unidade/registro
        val decisions = linkedMapOf<String, MergeConflictResolution>()
        plan.conflictItems.forEach { conflict ->
            val key = "${conflict.collection.key}:${conflict.recordId}"
            val resolution = when {
                conflict.collection == BackupCollectionType.PREFERENCES && conflict.recordId == "pref_theme" ->
                    MergeConflictResolution.KEEP_LOCAL
                conflict.collection == BackupCollectionType.PEOPLE && conflict.recordId == personUseBackupId ->
                    MergeConflictResolution.USE_BACKUP
                conflict.collection == BackupCollectionType.PEOPLE && conflict.recordId == "person-bkp-collision" ->
                    MergeConflictResolution.IGNORE_IMPORT
                conflict.relatedUnitId == s1Id || (conflict.collection == BackupCollectionType.SALES && conflict.recordId == s1Id) ->
                    MergeConflictResolution.USE_BACKUP
                conflict.relatedUnitId == s2Id || (conflict.collection == BackupCollectionType.SALES && conflict.recordId == s2Id) ->
                    MergeConflictResolution.KEEP_LOCAL
                conflict.relatedUnitId == s3Id || (conflict.collection == BackupCollectionType.SALES && conflict.recordId == s3Id) ->
                    MergeConflictResolution.IGNORE_IMPORT
                else -> error("Conflito inesperado no cenário: $key")
            }
            decisions[key] = resolution
        }

        val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, dbB, decisions)
        assertTrue(validation.isReadyForNextPhase)
        val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, dbB)
        assertTrue(preview.isSafeToApply)

        val execResult = JsonBackupMergeExecutor.execute(
            readResult = readResult,
            plan = plan,
            decisionsByConflictKey = decisions,
            decisionValidation = validation,
            previewResult = preview,
            executionContract = preview.executionContract,
            userConfirmed = true,
            database = dbB,
        )
        assertTrue(execResult.success)
        assertFalse(execResult.rolledBack)

        // Verificações KEEP_LOCAL
        val prefAfter = dbB.preferenceDao().getByKey("pref_theme")
        assertEquals("dark_local", prefAfter?.value)
        assertEquals(tLocal, prefAfter?.updatedAtEpochMs)

        val saleRepoBAfter = RoomSaleRepository(database = dbB, saleDao = dbB.saleDao())
        val s2After = saleRepoBAfter.getSaleById(s2Id)
        assertEquals(s2LocalOriginal, s2After)
        assertEquals(7_000L, s2After?.calculation?.finalAmount?.rawCents)
        assertEquals(175L, s2After?.calculation?.feeTotal?.rawCents)
        assertEquals(6_825L, s2After?.calculation?.netTotal?.rawCents)
        assertEquals(tLocal, s2After?.soldAtEpochMs)

        // Verificações USE_BACKUP (substituição atômica sem mistura)
        val updatedPerson = dbB.personDao().getById(personUseBackupId)
        assertEquals("Ana Backup", updatedPerson?.name)
        assertEquals("ana backup", updatedPerson?.normalizedName)

        val s1After = saleRepoBAfter.getSaleById(s1Id)!!
        assertEquals(tBackup, s1After.soldAtEpochMs)
        assertEquals(SalePaymentMethod.PIX, s1After.calculation.paymentMethod)
        assertEquals("S1 Backup", s1After.calculation.note)
        assertEquals(12_000L, s1After.calculation.finalAmount.rawCents)
        assertEquals(0L, s1After.calculation.feeTotal.rawCents)
        assertEquals(12_000L, s1After.calculation.netTotal.rawCents)
        assertEquals(1, s1After.calculation.items.size)
        assertEquals("Produto S1 Backup", s1After.calculation.items.single().productName)
        assertEquals(listOf(pessoaExtraA.id), s1After.calculation.personTotals.map { it.personId })

        // Verificações IGNORE_IMPORT (não importa colisão/backup, preserva local e não exclui ausentes)
        assertNull(dbB.personDao().getById("person-bkp-collision"))
        assertEquals("Carlos Colisão", dbB.personDao().getById("person-loc-existing")?.name)

        val s3After = saleRepoBAfter.getSaleById(s3Id)
        assertEquals(s3LocalOriginal, s3After)
        assertEquals(14_000L, s3After?.calculation?.originalSubtotal?.rawCents)
        assertEquals(1_000L, s3After?.calculation?.discountTotal?.rawCents)
        assertEquals(13_000L, s3After?.calculation?.finalAmount?.rawCents)

        // Somente local preservada + somente backup importada
        assertEquals(s4LocalOnly, saleRepoBAfter.getSaleById(s4LocalOnly.id))
        val s5After = saleRepoBAfter.getSaleById(s5BackupOnly.id)!!
        assertEquals(6_000L, s5After.calculation.finalAmount.rawCents)
        assertEquals(300L, s5After.calculation.feeTotal.rawCents)
        assertEquals(5_700L, s5After.calculation.netTotal.rawCents)
        assertEquals(5, saleRepoBAfter.observeSales().first().size)
    }

    @Test
    fun `06 post restoration reports csv export and new json backup consistency`() = runBlocking(Dispatchers.IO) {
        val zone = ZoneId.of("America/Sao_Paulo")
        val soldEpochMs = ZonedDateTime.of(2025, 5, 20, 11, 20, 30, 0, zone).toInstant().toEpochMilli()
        val dbA = openTestDb("post_a")
        val dbB = openTestDb("post_b")

        val catalogA = RoomCatalogRepository(personDao = dbA.personDao(), productDao = dbA.productDao(), clockMillis = { soldEpochMs })
        val machA = RoomMachineRepository(machineDao = dbA.machineDao(), clockMillis = { soldEpochMs })
        val saleA = RoomSaleRepository(database = dbA, saleDao = dbA.saleDao(), clockMillis = { soldEpochMs })

        val ana = catalogA.createPerson("Ana Pós")
        val prod = catalogA.createProduct(
            name = "Produto Pós",
            price = MoneyCents(20_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(20_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machine = machA.createMachine(
            MachineInputDraft(
                name = "Ton Pós",
                debitRateBps = 250, // 2,50%
                creditEnabled = false,
                creditDefaultRateBps = 0,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )
        // 2x 200,00 = 400,00 subtotal; desconto 50,00 -> 350,00 final; débito 2,50% -> taxa 8,75 (875L), líq 341,25 (34_125L)
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 2)),
                finalAmountOverride = MoneyCents(35_000L),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machine,
                note = "Venda Pós-Restauração",
            ),
        )
        val originalSale = saleA.saveSale(calc)

        val backup = JsonBackupExporter.export(dbA, zone, soldEpochMs + 1_000L)
        val readResult = JsonBackupReader.readAndValidate(backup.utf8Bytes)
        assertTrue(readResult.isValid)
        val plan = JsonBackupMergePlanner.plan(readResult, dbB)
        val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, dbB)
        val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, dbB)
        assertTrue(preview.isSafeToApply)

        val execResult = JsonBackupMergeExecutor.execute(
            readResult = readResult,
            plan = plan,
            decisionsByConflictKey = emptyMap(),
            decisionValidation = validation,
            previewResult = preview,
            executionContract = preview.executionContract,
            userConfirmed = true,
            database = dbB,
        )
        assertTrue(execResult.success)
        assertFalse(execResult.rolledBack)

        // 1) Conferência do Room restaurado
        val salesB = RoomSaleRepository(database = dbB, saleDao = dbB.saleDao()).observeSales().first()
        assertEquals(1, salesB.size)
        val restored = salesB.single()
        assertEquals(originalSale.id, restored.id)
        assertEquals(soldEpochMs, restored.soldAtEpochMs)
        assertEquals(soldEpochMs, restored.updatedAtEpochMs)
        assertEquals(40_000L, restored.calculation.originalSubtotal.rawCents)
        assertEquals(5_000L, restored.calculation.discountTotal.rawCents)
        assertEquals(35_000L, restored.calculation.finalAmount.rawCents)
        assertEquals(875L, restored.calculation.feeTotal.rawCents)
        assertEquals(34_125L, restored.calculation.netTotal.rawCents)
        assertEquals(250, restored.calculation.effectiveFeeRateBps)
        assertEquals(34_125L, restored.calculation.personTotals.single().netAmount.rawCents)

        // 2) Conferência dos relatórios (DailySalesAggregator) após restauração
        val dailyReport = DailySalesAggregator.aggregate(salesB, zone)
        assertEquals(1, dailyReport.totalSalesCount)
        assertEquals(1, dailyReport.totalDaysCount)
        assertEquals(35_000L, dailyReport.totalSold.rawCents)
        assertEquals(LocalDate.of(2025, 5, 20), dailyReport.months.single().days.single().date)

        // 3) Conferência do CSV Resumo (somente campos reais do formato Resumo) e CSV Detalhado (nome histórico do produto e participantes)
        val csvResult = CsvSaleExporter.export(
            sales = salesB,
            zoneId = zone,
            generatedOnDate = LocalDate.of(2025, 5, 20),
        )
        assertEquals(1, csvResult.exportedSalesCount)
        assertEquals(1, csvResult.detailedRowsCount)

        val expectedDateStr = Instant.ofEpochMilli(restored.soldAtEpochMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")))
        val expectedTimeStr = Instant.ofEpochMilli(restored.soldAtEpochMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.forLanguageTag("pt-BR")))

        val summaryLines = parseCsvLines(csvResult.summaryCsvContent)
        assertEquals(2, summaryLines.size)
        assertEquals(CsvExportColumns.SUMMARY_HEADERS, summaryLines[0])
        val summaryRow = summaryLines[1]
        assertEquals(originalSale.id, summaryRow[0])
        assertEquals(expectedDateStr, summaryRow[1])
        assertEquals(expectedTimeStr, summaryRow[2])
        assertEquals(expectedDateStr, summaryRow[3])
        assertEquals("2", summaryRow[4])
        assertEquals("400,00", summaryRow[5])
        assertEquals("50,00", summaryRow[6])
        assertEquals("350,00", summaryRow[7])
        assertEquals("Débito", summaryRow[8])
        assertEquals("Ton Pós", summaryRow[9])
        assertEquals("8,75", summaryRow[13])
        assertEquals("341,25", summaryRow[14])
        assertEquals("Venda Pós-Restauração", summaryRow[15])

        val detailedLines = parseCsvLines(csvResult.detailedCsvContent)
        assertEquals(2, detailedLines.size)
        assertEquals(CsvExportColumns.DETAILED_HEADERS, detailedLines[0])
        val detailedRow = detailedLines[1]
        assertEquals(originalSale.id, detailedRow[0])
        assertEquals(expectedDateStr, detailedRow[1])
        assertEquals(expectedTimeStr, detailedRow[2])
        assertEquals(prod.id, detailedRow[4])
        assertEquals("Produto Pós", detailedRow[5])
        assertEquals("2", detailedRow[6])
        assertEquals("200,00", detailedRow[7])
        assertEquals("400,00", detailedRow[8])
        assertEquals("50,00", detailedRow[9])
        assertEquals("350,00", detailedRow[10])
        assertEquals(ana.id, detailedRow[11])
        assertEquals("Ana Pós", detailedRow[12])
        assertEquals("350,00", detailedRow[13])
        assertEquals("8,75", detailedRow[14])
        assertEquals("341,25", detailedRow[15])
        assertEquals("2,50%", detailedRow[20])

        // 4) Novo backup JSON gerado a partir do Banco B restaurado coincide com o Banco B e passa na validação
        val newBackup = JsonBackupExporter.export(dbB, zone, soldEpochMs + 2_000L)
        val newRead = JsonBackupReader.readAndValidate(newBackup.utf8Bytes)
        assertTrue(newRead.isValid)
        assertEquals(1, newRead.salesCount)
        assertEquals(1, newRead.saleItemsCount)
        assertEquals(1, newRead.peopleCount)
        assertEquals(1, newRead.productsCount)
        assertEquals(1, newRead.machinesCount)
        val comparisonBA = JsonBackupComparator.compare(newRead, dbA)
        assertEquals(0, comparisonBA.totalNewCount)
        assertEquals(0, comparisonBA.totalConflictingCount)
        assertEquals(0, comparisonBA.totalLocalOnlyCount)
        assertFalse(comparisonBA.hasAnyConflicts)
        assertFalse(comparisonBA.hasRelationalRisks)
        assertTrue(comparisonBA.isSafeForAdditiveMerge)
        assertEquals(comparisonBA.totalBackupRecords, comparisonBA.totalIdenticalCount)
    }

    @Test
    fun `07 transaction failure and rollback preserves bank b untouched`() = runBlocking(Dispatchers.IO) {
        val dbA = openTestDb("fail_a")
        val dbB = openTestDb("fail_b")

        val catalogA = RoomCatalogRepository(personDao = dbA.personDao(), productDao = dbA.productDao())
        val saleA = RoomSaleRepository(database = dbA, saleDao = dbA.saleDao())
        val anaA = catalogA.createPerson("Ana Origem")
        val prodA = catalogA.createProduct(
            name = "Prod Origem",
            price = MoneyCents(5_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(anaA.id, MoneyCents(5_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        saleA.saveSale(SaleFinancialEngine.calculate(SaleCalculationInput(items = listOf(SaleItemInput(prodA, 1)), paymentMethod = SalePaymentMethod.CASH)))

        val catalogB = RoomCatalogRepository(personDao = dbB.personDao(), productDao = dbB.productDao())
        val anaB = catalogB.createPerson("Ana Original B")
        val initialPeopleCount = dbB.personDao().getAllPeople().size

        val backup = JsonBackupExporter.export(dbA)
        val readResult = JsonBackupReader.readAndValidate(backup.utf8Bytes)
        val plan = JsonBackupMergePlanner.plan(readResult, dbB)
        val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, dbB)
        val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, emptyMap(), validation, dbB)

        val execResult = JsonBackupMergeExecutor.execute(
            readResult = readResult,
            plan = plan,
            decisionsByConflictKey = emptyMap(),
            decisionValidation = validation,
            previewResult = preview,
            executionContract = preview.executionContract,
            userConfirmed = false,
            database = dbB,
        )

        assertFalse(execResult.success)
        assertEquals(MergeExecutionFailureReason.USER_CONFIRMATION_MISSING, execResult.failureReason)
        assertEquals(initialPeopleCount, dbB.personDao().getAllPeople().size)
        assertEquals("Ana Original B", dbB.personDao().getById(anaB.id)?.name)
    }

    @Test
    fun `08 real transactional rollback after partial writes preserves all 11 tables and existing records intact`() = runBlocking(Dispatchers.IO) {
        val tBackup = 1_748_100_000_000L
        val tLocal = 1_748_000_000_000L
        val dbA = openTestDb("real_rb_a")
        val dbB = openTestDb("real_rb_b")

        // Popula Banco A (origem do backup) com novas entidades e uma venda conflitante
        RoomPreferenceRepository(dbA.preferenceDao(), clockMillis = { tBackup }).savePreference("pref_new_a", "from_a")
        val catA = RoomCatalogRepository(dbA.personDao(), dbA.productDao(), { "a-${UUID.randomUUID()}" }, { tBackup })
        val machRepoA = RoomMachineRepository(
            machineDao = dbA.machineDao(),
            idGenerator = { "mach-a-${UUID.randomUUID()}" },
            clockMillis = { tBackup },
        )
        val saleRepoA = RoomSaleRepository(dbA, dbA.saleDao(), { "sale-a-new" }, { tBackup })

        val anaA = catA.createPerson("Ana Nova A")
        val prodA = catA.createProduct(
            name = "Produto Novo A",
            price = MoneyCents(10_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(anaA.id, MoneyCents(10_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machA = machRepoA.createMachine(
            MachineInputDraft("Maq Nova A", 190, true, 420, true, mapOf(CardBrand.VISA to 390), true, mapOf(2 to 110)),
        )
        saleRepoA.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodA, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machA,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                    note = "Venda Nova A",
                ),
            ),
        )

        // Venda compartilhada para provocar DELETE parcial de filhos antes da falha na transação
        val sharedSaleId = "sale-shared-rollback"
        RoomSaleRepository(dbA, dbA.saleDao(), { sharedSaleId }, { tBackup }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodA, 2)), // 200,00 Pix
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "Shared Backup",
                ),
            ),
        )

        // Popula Banco B (destino) com dados existentes em todas as 11 tabelas
        RoomPreferenceRepository(dbB.preferenceDao(), clockMillis = { tLocal }).savePreference("pref_existing_b", "from_b")
        val catB = RoomCatalogRepository(dbB.personDao(), dbB.productDao(), { "b-${UUID.randomUUID()}" }, { tLocal })
        val machRepoB = RoomMachineRepository(
            machineDao = dbB.machineDao(),
            idGenerator = { "mach-b-${UUID.randomUUID()}" },
            clockMillis = { tLocal },
        )
        val saleRepoB = RoomSaleRepository(dbB, dbB.saleDao(), { "sale-b-existing" }, { tLocal })

        val brunoB = catB.createPerson("Bruno Existente B")
        val prodB = catB.createProduct(
            name = "Produto Existente B",
            price = MoneyCents(7_500L),
            active = true,
            participants = listOf(ProductParticipantDraft(brunoB.id, MoneyCents(7_500L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machB = machRepoB.createMachine(
            MachineInputDraft("Maq Existente B", 210, true, 450, true, mapOf(CardBrand.ELO to 470), true, mapOf(2 to 140)),
        )
        val existingSaleB = saleRepoB.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodB, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machB,
                    cardBrand = CardBrand.ELO,
                    installments = 2,
                    note = "Venda Existente B",
                ),
            ),
        )
        RoomSaleRepository(dbB, dbB.saleDao(), { sharedSaleId }, { tLocal }).saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodB, 1)),
                    paymentMethod = SalePaymentMethod.CASH,
                    note = "Shared Local Intacto",
                ),
            ),
        )

        val beforeSnapshot = JsonBackupExporter.captureConsistentSnapshot(dbB)

        val backup = JsonBackupExporter.export(dbA, ZoneId.of("UTC"), tBackup + 5_000L)
        val readResult = JsonBackupReader.readAndValidate(backup.utf8Bytes)
        assertTrue(readResult.isValid)
        val plan = JsonBackupMergePlanner.plan(readResult, dbB)

        val decisions = plan.conflictItems.associate {
            "${it.collection.key}:${it.recordId}" to MergeConflictResolution.USE_BACKUP
        }
        val validation = JsonBackupMergeDecisionValidator.validate(plan, readResult, dbB, decisions)
        assertTrue(validation.isReadyForNextPhase)
        val preview = JsonBackupMergePreviewBuilder.buildPreview(readResult, plan, decisions, validation, dbB)
        assertTrue(preview.isSafeToApply)

        // Injeta falha controlada dentro da transação APÓS gravações iniciais (preferences, people, products, machines, nova venda)
        // e APÓS a exclusão dos filhos da venda compartilhada em duringSaleReplacement
        var hookExecuted = false
        val execResult = JsonBackupMergeExecutor.executeWithHooksForTest(
            readResult = readResult,
            plan = plan,
            decisionsByConflictKey = decisions,
            decisionValidation = validation,
            previewResult = preview,
            executionContract = preview.executionContract,
            userConfirmed = true,
            database = dbB,
            hooks = ExecutorTestHooks(
                duringSaleReplacement = { saleId ->
                    if (saleId == sharedSaleId) {
                        hookExecuted = true
                        throw IllegalStateException("Falha simulada após início das gravações na transação")
                    }
                },
            ),
        )

        assertTrue(hookExecuted)
        assertFalse(execResult.success)
        assertTrue(execResult.rolledBack)
        assertEquals(MergeExecutionFailureReason.TRANSACTION_ABORTED_OR_FAILED, execResult.failureReason)
        assertNotNull(execResult.failureMessage)

        // As 11 tabelas do Banco B devem permanecer 100% idênticas ao estado anterior
        val afterSnapshot = JsonBackupExporter.captureConsistentSnapshot(dbB)
        assertEquals(beforeSnapshot, afterSnapshot)

        // Conferência explícita de ausência de importação ou alteração parcial
        assertNull(dbB.preferenceDao().getByKey("pref_new_a"))
        assertNull(dbB.personDao().getById(anaA.id))
        assertNull(dbB.productDao().getWithParticipantsById(prodA.id))
        assertNull(dbB.machineDao().getWithRatesById(machA.id))
        assertNull(saleRepoB.getSaleById("sale-a-new"))

        assertEquals("from_b", dbB.preferenceDao().getByKey("pref_existing_b")?.value)
        assertEquals("Bruno Existente B", dbB.personDao().getById(brunoB.id)?.name)
        assertEquals(existingSaleB, saleRepoB.getSaleById(existingSaleB.id))
        val sharedAfter = saleRepoB.getSaleById(sharedSaleId)!!
        assertEquals("Shared Local Intacto", sharedAfter.calculation.note)
        assertEquals(7_500L, sharedAfter.calculation.finalAmount.rawCents)
        assertEquals(1, sharedAfter.calculation.items.size)
        assertEquals(1, sharedAfter.calculation.personTotals.size)
    }
}
