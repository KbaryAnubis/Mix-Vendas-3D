package com.mixvendas3d.app

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.CsvExportPeriod
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.util.CurrencyFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealSaleDateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdDbNames = mutableListOf<String>()

    @After
    fun cleanUp() {
        val app = context as MixVendasApplication
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
        createdDbNames.forEach { dbName ->
            runCatching { context.deleteDatabase(dbName) }
        }
        createdDbNames.clear()
    }

    private fun waitForTag(testTag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(testTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun waitForTagGone(testTag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            composeRule.onAllNodesWithTag(testTag).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun scrollToTagInList(listTag: String, itemTag: String, timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(itemTag))
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    private fun buildPixSaleResult(
        productId: String = "prod-500",
        productName: String = "Kit Colecionavel 3D",
        unitPriceCents: Long = 50_000L,
        quantity: Int = 1,
        note: String = "",
    ): SaleCalculationResult {
        val product = Product(
            id = productId,
            name = productName,
            price = MoneyCents(unitPriceCents),
            active = true,
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
            participants = listOf(
                ProductParticipantSplit(
                    personId = "person-ana",
                    personName = "Ana",
                    personColorIndex = 0,
                    amount = MoneyCents(unitPriceCents),
                ),
            ),
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_000_000L,
        )
        return SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(product = product, quantity = quantity)),
                paymentMethod = SalePaymentMethod.PIX,
                note = note,
            ),
        )
    }

    private fun createInMemoryDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun seedCatalogForUi(
        app: MixVendasApplication,
        productId: String = "prod-ui-500",
        productName: String = "Escultura 3D Premium",
        priceCents: Long = 50_000L,
    ) {
        runBlocking(Dispatchers.IO) {
            val person = app.catalogRepository.createPerson("Ana")
            app.database.productDao().insertProduct(
                ProductEntity(
                    id = productId,
                    name = productName,
                    priceCents = priceCents,
                    active = true,
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL.storageKey,
                    feeOwnerPersonId = null,
                    createdAtEpochMs = 1_700_000_000_000L,
                    updatedAtEpochMs = 1_700_000_000_000L,
                ),
            )
            app.database.productDao().insertParticipants(
                listOf(
                    ProductParticipantEntity(
                        productId = productId,
                        personId = person.id,
                        amountCents = priceCents,
                        sortOrder = 0,
                    ),
                ),
            )
        }
    }

    @Test
    fun `01 new sale defaults to current date and earlier date preserves current local time without UTC shift`() {
        val spZone = ZoneId.of("America/Sao_Paulo")
        val registrationMoment = ZonedDateTime.of(
            LocalDate.of(2026, 11, 21),
            LocalTime.of(14, 35, 22),
            spZone,
        ).toInstant().toEpochMilli()

        val db = createInMemoryDb()
        try {
            var seq = 1
            val repo = RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                idGenerator = { "sale-${seq++}" },
                clockMillis = { registrationMoment },
            )

            runBlocking {
                // 1) Nova venda usando data padrão (hoje = 21/11/2026)
                val defaultSale = repo.saveSale(buildPixSaleResult(unitPriceCents = 12_000L))
                assertEquals(registrationMoment, defaultSale.soldAtEpochMs)
                assertEquals(
                    LocalDate.of(2026, 11, 21),
                    DailySalesAggregator.toLocalDate(defaultSale.soldAtEpochMs, spZone),
                )

                // 2) Nova venda ocorrida em 20/11/2026 (R$ 500,00) registrada em 21/11/2026 às 14:35:22
                val earlierDate = LocalDate.of(2026, 11, 20)
                val backdatedSale = repo.saveSale(
                    result = buildPixSaleResult(unitPriceCents = 50_000L, note = "Feira de ontem"),
                    saleDate = earlierDate,
                    zoneId = spZone,
                )

                val persistedZoned = Instant.ofEpochMilli(backdatedSale.soldAtEpochMs).atZone(spZone)
                assertEquals(LocalDate.of(2026, 11, 20), persistedZoned.toLocalDate())
                assertEquals(LocalTime.of(14, 35, 22), persistedZoned.toLocalTime())
                assertEquals(50_000L, backdatedSale.calculation.finalAmount.rawCents)
                assertEquals("20/11/2026", CurrencyFormatter.formatDateOfEpochMillis(backdatedSale.soldAtEpochMs, spZone))
                assertEquals("14:35:22", CurrencyFormatter.formatTimeOfEpochMillis(backdatedSale.soldAtEpochMs, spZone))
            }

            // 3) Conversão DatePicker (UTC midnight) sem deslocamento de 1 dia em fusos negativos e positivos
            val testDate = LocalDate.of(2026, 11, 20)
            val pickerUtcMillis = DailySalesAggregator.localDateToUtcDatePickerMillis(testDate)
            assertEquals(
                ZonedDateTime.of(2026, 11, 20, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(),
                pickerUtcMillis,
            )
            assertEquals(testDate, DailySalesAggregator.utcDatePickerMillisToLocalDate(pickerUtcMillis))

            val tokyoZone = ZoneId.of("Asia/Tokyo")
            val tokyoRef = ZonedDateTime.of(2026, 11, 21, 0, 15, 0, 0, tokyoZone).toInstant().toEpochMilli()
            val tokyoCombined = DailySalesAggregator.combineLocalDateWithTimeOf(testDate, tokyoRef, tokyoZone)
            val tokyoResult = Instant.ofEpochMilli(tokyoCombined).atZone(tokyoZone)
            assertEquals(testDate, tokyoResult.toLocalDate())
            assertEquals(LocalTime.of(0, 15, 0), tokyoResult.toLocalTime())

            // Caso extremo próximo da meia-noite em fuso negativo (UTC-3 às 23:45)
            val lateNightSpRef = ZonedDateTime.of(2026, 11, 21, 23, 45, 10, 0, spZone).toInstant().toEpochMilli()
            val lateNightCombined = DailySalesAggregator.combineLocalDateWithTimeOf(testDate, lateNightSpRef, spZone)
            val lateNightResult = Instant.ofEpochMilli(lateNightCombined).atZone(spZone)
            assertEquals(testDate, lateNightResult.toLocalDate())
            assertEquals(LocalTime.of(23, 45, 10), lateNightResult.toLocalTime())
        } finally {
            db.close()
        }
    }

    @Test
    fun `02 real sale date persists across Room reopen and edit preserves or updates date with original time`() {
        val spZone = ZoneId.of("America/Sao_Paulo")
        val initialClock = ZonedDateTime.of(2026, 11, 21, 18, 20, 45, 0, spZone).toInstant().toEpochMilli()
        val editLaterClock = ZonedDateTime.of(2026, 11, 22, 9, 5, 11, 0, spZone).toInstant().toEpochMilli()

        val dbName = "real_date_reopen_${System.nanoTime()}.db"
        createdDbNames.add(dbName)
        context.deleteDatabase(dbName)

        var currentClock = initialClock
        val fileDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()

        val savedId: String
        val originalSoldAt: Long
        try {
            val repo = RoomSaleRepository(
                database = fileDb,
                saleDao = fileDb.saleDao(),
                idGenerator = { "sale-reopen-01" },
                clockMillis = { currentClock },
            )
            runBlocking {
                val created = repo.saveSale(
                    result = buildPixSaleResult(unitPriceCents = 50_000L, note = "Original"),
                    saleDate = LocalDate.of(2026, 11, 20),
                    zoneId = spZone,
                )
                savedId = created.id
                originalSoldAt = created.soldAtEpochMs
            }
        } finally {
            fileDb.close()
        }

        // Reabre banco em arquivo e valida persistência real + edição sem alterar data + edição alterando data
        val reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        try {
            currentClock = editLaterClock
            val repo = RoomSaleRepository(
                database = reopenedDb,
                saleDao = reopenedDb.saleDao(),
                clockMillis = { currentClock },
            )
            runBlocking {
                val loaded = repo.getSaleById(savedId)
                assertNotNull(loaded)
                assertEquals(originalSoldAt, loaded!!.soldAtEpochMs)
                assertEquals(
                    LocalDate.of(2026, 11, 20),
                    DailySalesAggregator.toLocalDate(loaded.soldAtEpochMs, spZone),
                )

                // Edição SEM alterar data: deve preservar exatamente soldAtEpochMs original
                val updatedNoteOnly = repo.updateSale(
                    saleId = savedId,
                    result = buildPixSaleResult(unitPriceCents = 50_000L, note = "Nota editada"),
                    saleDate = null,
                    zoneId = spZone,
                )
                assertEquals(originalSoldAt, updatedNoteOnly.soldAtEpochMs)
                assertEquals(editLaterClock, updatedNoteOnly.updatedAtEpochMs)

                // Edição ALTERANDO a data para 18/10/2026: preserva horário local original (18:20:45)
                val updatedDate = repo.updateSale(
                    saleId = savedId,
                    result = buildPixSaleResult(unitPriceCents = 50_000L, note = "Data ajustada"),
                    saleDate = LocalDate.of(2026, 10, 18),
                    zoneId = spZone,
                )
                val updatedZoned = Instant.ofEpochMilli(updatedDate.soldAtEpochMs).atZone(spZone)
                assertEquals(LocalDate.of(2026, 10, 18), updatedZoned.toLocalDate())
                assertEquals(LocalTime.of(18, 20, 45), updatedZoned.toLocalTime())
                assertEquals(50_000L, updatedDate.calculation.finalAmount.rawCents)
            }
        } finally {
            reopenedDb.close()
        }
    }

    @Test
    fun `03 daily and monthly aggregation and period filters use real sale date including 20-11 vs 21-11 example`() {
        val spZone = ZoneId.of("America/Sao_Paulo")
        val registerDay = ZonedDateTime.of(2026, 11, 21, 10, 15, 0, 0, spZone).toInstant().toEpochMilli()
        val legacyOctEpoch = ZonedDateTime.of(2026, 10, 15, 16, 40, 0, 0, spZone).toInstant().toEpochMilli()

        val db = createInMemoryDb()
        try {
            var clock = legacyOctEpoch
            var seq = 1
            val repo = RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                idGenerator = { "sale-agg-${seq++}" },
                clockMillis = { clock },
            )

            runBlocking {
                // Venda legada antiga já existente no Room (15/10/2026 - R$ 80,00)
                val legacySale = repo.saveSale(buildPixSaleResult(unitPriceCents = 8_000L, note = "Legada"))

                // Dia 21/11/2026: registra venda retroativa de R$ 500,00 ocorrida em 20/11/2026
                clock = registerDay
                val sale20Nov = repo.saveSale(
                    result = buildPixSaleResult(unitPriceCents = 50_000L, note = "Venda real 20/11"),
                    saleDate = LocalDate.of(2026, 11, 20),
                    zoneId = spZone,
                )

                // Mais uma venda no dia 20/11/2026 (R$ 150,00) e outra em 21/11/2026 (R$ 200,00)
                val secondSale20Nov = repo.saveSale(
                    result = buildPixSaleResult(unitPriceCents = 15_000L, note = "Segunda de 20/11"),
                    saleDate = LocalDate.of(2026, 11, 20),
                    zoneId = spZone,
                )
                val sale21Nov = repo.saveSale(
                    result = buildPixSaleResult(unitPriceCents = 20_000L, note = "Venda de hoje 21/11"),
                    saleDate = LocalDate.of(2026, 11, 21),
                    zoneId = spZone,
                )

                val allSales = repo.observeSales().first()
                assertEquals(4, allSales.size)

                // Agrupamento diário e mensal real via DailySalesAggregator
                val report = DailySalesAggregator.aggregate(allSales, spZone)
                assertEquals(4, report.totalSalesCount)
                assertEquals(93_000L, report.totalSold.rawCents)
                assertEquals(3, report.totalDaysCount)
                assertEquals(2, report.months.size)

                val novMonth = report.months.first { it.yearMonth == YearMonth.of(2026, 11) }
                assertEquals("NOVEMBRO 2026", novMonth.title)
                assertEquals(3, novMonth.salesCount)
                assertEquals(85_000L, novMonth.totalSold.rawCents)

                val day20 = novMonth.days.first { it.date == LocalDate.of(2026, 11, 20) }
                assertEquals(2, day20.salesCount)
                assertEquals(65_000L, day20.totalSold.rawCents)

                val day21 = novMonth.days.first { it.date == LocalDate.of(2026, 11, 21) }
                assertEquals(1, day21.salesCount)
                assertEquals(20_000L, day21.totalSold.rawCents)

                val octMonth = report.months.first { it.yearMonth == YearMonth.of(2026, 10) }
                assertEquals("OUTUBRO 2026", octMonth.title)
                assertEquals(1, octMonth.salesCount)
                assertEquals(8_000L, octMonth.totalSold.rawCents)
                assertEquals(LocalDate.of(2026, 10, 15), octMonth.days.single().date)
                assertEquals(legacySale.id, allSales.first { DailySalesAggregator.toLocalDate(it.soldAtEpochMs, spZone) == LocalDate.of(2026, 10, 15) }.id)
                assertTrue(listOf(sale20Nov.id, secondSale20Nov.id).all { id -> allSales.any { it.id == id } })
                assertEquals(sale21Nov.id, allSales.first { DailySalesAggregator.toLocalDate(it.soldAtEpochMs, spZone) == LocalDate.of(2026, 11, 21) }.id)

                // Filtro por período (ex: apenas 20/11/2026 vs apenas 21/11/2026)
                val onlyNov20 = repo.observeSalesByDateRange(
                    fromDate = LocalDate.of(2026, 11, 20),
                    toDate = LocalDate.of(2026, 11, 20),
                    zoneId = spZone,
                ).first()
                assertEquals(2, onlyNov20.size)
                assertEquals(65_000L, onlyNov20.sumOf { it.calculation.finalAmount.rawCents })

                val onlyNov21 = repo.observeSalesByDateRange(
                    fromDate = LocalDate.of(2026, 11, 21),
                    toDate = LocalDate.of(2026, 11, 21),
                    zoneId = spZone,
                ).first()
                assertEquals(1, onlyNov21.size)
                assertEquals(20_000L, onlyNov21.single().calculation.finalAmount.rawCents)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun `04 json backup export import merge and csv export preserve real sale dates accurately`() {
        val spZone = ZoneId.of("America/Sao_Paulo")
        val registerMoment = ZonedDateTime.of(2026, 11, 21, 15, 10, 5, 0, spZone).toInstant().toEpochMilli()

        val sourceDb = createInMemoryDb()
        val cleanRestoreDb = createInMemoryDb()
        val mergeTargetDb = createInMemoryDb()
        try {
            val sourceRepo = RoomSaleRepository(
                database = sourceDb,
                saleDao = sourceDb.saleDao(),
                idGenerator = { "sale-real-date-backup" },
                clockMillis = { registerMoment },
            )
            val targetRepo = RoomSaleRepository(
                database = mergeTargetDb,
                saleDao = mergeTargetDb.saleDao(),
                idGenerator = { "sale-existing-target" },
                clockMillis = { registerMoment },
            )

            runBlocking {
                // Venda retroativa 20/11/2026 (R$ 500,00) registrada em 21/11/2026
                val backdatedSale = sourceRepo.saveSale(
                    result = buildPixSaleResult(unitPriceCents = 50_000L, note = "Backup 20/11"),
                    saleDate = LocalDate.of(2026, 11, 20),
                    zoneId = spZone,
                )

                // Venda pré-existente no banco destino de merge (21/11/2026 - R$ 90,00)
                val targetExistingSale = targetRepo.saveSale(
                    result = buildPixSaleResult(
                        productId = "prod-90",
                        productName = "Chaveiro 3D",
                        unitPriceCents = 9_000L,
                        note = "Local 21/11",
                    ),
                    saleDate = LocalDate.of(2026, 11, 21),
                    zoneId = spZone,
                )

                // 1) Export CSV (Resumo + Detalhado) deve refletir data real 20/11/2026 e horário 15:10:05
                val csv = CsvSaleExporter.export(
                    sales = listOf(backdatedSale),
                    period = CsvExportPeriod(
                        fromDate = LocalDate.of(2026, 11, 20),
                        toDate = LocalDate.of(2026, 11, 20),
                    ),
                    zoneId = spZone,
                    generatedOnDate = LocalDate.of(2026, 11, 21),
                )
                assertEquals(1, csv.exportedSalesCount)
                assertTrue(csv.summaryCsvContent.contains("20/11/2026;15:10:05"))
                assertTrue(csv.summaryCsvContent.contains("500,00"))
                assertTrue(csv.detailedCsvContent.contains("20/11/2026;15:10:05"))

                // 2) Exportação + Importação limpa de Backup JSON
                val exported = JsonBackupExporter.export(sourceDb, spZone, registerMoment)
                val readResult = JsonBackupReader.readAndValidate(exported.jsonContent)
                assertTrue("Backup JSON inválido: ${readResult.errors}", readResult.isValid)

                val cleanPlan = JsonBackupMergePlanner.plan(readResult, cleanRestoreDb)
                val cleanValidation = JsonBackupMergeDecisionValidator.validate(cleanPlan, readResult, cleanRestoreDb)
                assertTrue(cleanValidation.isReadyForNextPhase)
                val cleanPreview = JsonBackupMergePreviewBuilder.buildPreview(
                    readResult,
                    cleanPlan,
                    emptyMap(),
                    cleanValidation,
                    cleanRestoreDb,
                )
                val cleanExec = JsonBackupMergeExecutor.execute(
                    readResult = readResult,
                    plan = cleanPlan,
                    decisionsByConflictKey = emptyMap(),
                    decisionValidation = cleanValidation,
                    previewResult = cleanPreview,
                    executionContract = cleanPreview.executionContract,
                    userConfirmed = true,
                    database = cleanRestoreDb,
                )
                assertTrue(cleanExec.success)

                val restoredSale = RoomSaleRepository(cleanRestoreDb, cleanRestoreDb.saleDao())
                    .getSaleById(backdatedSale.id)
                assertNotNull(restoredSale)
                assertEquals(backdatedSale.soldAtEpochMs, restoredSale!!.soldAtEpochMs)
                assertEquals(
                    LocalDate.of(2026, 11, 20),
                    DailySalesAggregator.toLocalDate(restoredSale.soldAtEpochMs, spZone),
                )

                // 3) Mesclagem (merge) em banco com venda existente preserva ambas as datas reais
                val mergePlan = JsonBackupMergePlanner.plan(readResult, mergeTargetDb)
                val mergeValidation = JsonBackupMergeDecisionValidator.validate(mergePlan, readResult, mergeTargetDb)
                assertTrue(mergeValidation.isReadyForNextPhase)
                val mergePreview = JsonBackupMergePreviewBuilder.buildPreview(
                    readResult,
                    mergePlan,
                    emptyMap(),
                    mergeValidation,
                    mergeTargetDb,
                )
                val mergeExec = JsonBackupMergeExecutor.execute(
                    readResult = readResult,
                    plan = mergePlan,
                    decisionsByConflictKey = emptyMap(),
                    decisionValidation = mergeValidation,
                    previewResult = mergePreview,
                    executionContract = mergePreview.executionContract,
                    userConfirmed = true,
                    database = mergeTargetDb,
                )
                assertTrue(mergeExec.success)

                val mergedSales = RoomSaleRepository(mergeTargetDb, mergeTargetDb.saleDao())
                    .observeSales().first()
                assertEquals(2, mergedSales.size)
                val mergedBackdated = mergedSales.first { it.id == backdatedSale.id }
                val mergedLocal = mergedSales.first { it.id == targetExistingSale.id }
                assertEquals(
                    LocalDate.of(2026, 11, 20),
                    DailySalesAggregator.toLocalDate(mergedBackdated.soldAtEpochMs, spZone),
                )
                assertEquals(
                    LocalDate.of(2026, 11, 21),
                    DailySalesAggregator.toLocalDate(mergedLocal.soldAtEpochMs, spZone),
                )
            }
        } finally {
            sourceDb.close()
            cleanRestoreDb.close()
            mergeTargetDb.close()
        }
    }

    @Test
    fun `05 end-to-end UI flow selects yesterday date shows receipt in Vendas and reflects real date in Fechamento and edit`() {
        val app = context as MixVendasApplication
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val yesterdayFormatted = CurrencyFormatter.formatLocalDate(yesterday)
        val todayFormatted = CurrencyFormatter.formatLocalDate(today)

        seedCatalogForUi(app, productId = "prod-ui-500", productName = "Escultura 3D Premium", priceCents = 50_000L)

        // 1) Abre aba Vender e verifica data padrão = Hoje
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        waitForTag("screen_vender")
        composeRule.onNodeWithTag("sale_date_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sale_date_value_text").assertTextContains(todayFormatted)

        // 2) Abre o seletor de data e escolhe Ontem (atalho seleciona e fecha o modal)
        composeRule.onNodeWithTag("sale_date_selector_button").performScrollTo().performClick()
        waitForTag("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_quick_yesterday").performClick()
        waitForTagGone("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_value_text").assertTextContains(yesterdayFormatted)

        // Confirma também o fluxo via botão Confirmar do calendário Material 3 mantendo Ontem
        composeRule.onNodeWithTag("sale_date_selector_button").performScrollTo().performClick()
        waitForTag("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_picker_confirm").performClick()
        waitForTagGone("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_value_text").assertTextContains(yesterdayFormatted)

        // 3) Seleciona produto (R$ 500,00) e salva venda em PIX
        composeRule.onNodeWithTag("open_product_picker_button").performScrollTo().performClick()
        waitForTag("product_picker_dialog")
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            runCatching {
                composeRule.onNodeWithTag("product_picker_list")
                    .performScrollToNode(hasTestTag("picker_row_prod-ui-500"))
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("picker_checkbox_prod-ui-500").performClick()
        composeRule.onNodeWithTag("close_product_picker_button").performClick()
        waitForTagGone("product_picker_dialog")

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitForTag("vender_feedback_banner")
        composeRule.onNodeWithTag("vender_feedback_banner").assertTextContains(yesterdayFormatted, substring = true)

        val persistedSales = runBlocking(Dispatchers.IO) {
            app.saleRepository.observeSales().first()
        }
        assertEquals(1, persistedSales.size)
        val savedSale = persistedSales.single()
        val expectedTime = CurrencyFormatter.formatTimeOfEpochMillis(savedSale.soldAtEpochMs, zoneId)
        assertEquals(yesterday, DailySalesAggregator.toLocalDate(savedSale.soldAtEpochMs, zoneId))

        // 4) Confere histórico em VENDAS + Comprovante da venda (data real, horário, valor final)
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitForTag("screen_vendas")
        // Garante filtro Todos para listar a venda de ontem independente do padrão
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_all").performClick()
        composeRule.waitForIdle()

        scrollToTagInList("screen_vendas", "sale_history_card_${savedSale.id}")
        composeRule.onNodeWithTag("sale_history_date_${savedSale.id}")
            .assertTextContains(yesterdayFormatted, substring = true)

        composeRule.onNodeWithTag("sale_details_toggle_${savedSale.id}").performScrollTo().performClick()
        waitForTag("sale_receipt_summary_${savedSale.id}")
        composeRule.onNodeWithTag("sale_receipt_summary_${savedSale.id}")
            .performScrollTo()
            .assertTextContains(yesterdayFormatted, substring = true)
            .assertTextContains(expectedTime, substring = true)
            .assertTextContains("R$ 500,00", substring = true)

        // 5) Confere Fechamento: filtro Hoje NÃO inclui a venda de ontem; filtro Todos inclui no dia de ontem
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        waitForTag("screen_fechamento")

        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("closing_daily_empty_message").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_all").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("closing_daily_sales_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("closing_day_$yesterday")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(yesterdayFormatted, substring = true)
            .assertTextContains("1 venda", substring = true)
            .assertTextContains("R$ 500,00", substring = true)

        // 6) Edita a venda, carrega automaticamente a data original (ontem), altera para Hoje e salva
        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitForTag("screen_vendas")
        scrollToTagInList("screen_vendas", "sale_history_card_${savedSale.id}")
        composeRule.onNodeWithTag("sale_edit_${savedSale.id}").performScrollTo().performClick()
        waitForTag("vender_editing_banner")
        composeRule.onNodeWithTag("sale_date_value_text").performScrollTo().assertTextContains(yesterdayFormatted)

        composeRule.onNodeWithTag("sale_date_selector_button").performScrollTo().performClick()
        waitForTag("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_quick_today").performClick()
        waitForTagGone("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_value_text").performScrollTo().assertTextContains(todayFormatted)

        composeRule.onNodeWithTag("save_sale_button").performScrollTo().performClick()
        waitForTag("vender_feedback_banner")

        val editedSale = runBlocking(Dispatchers.IO) {
            app.saleRepository.getSaleById(savedSale.id)
        }
        assertNotNull(editedSale)
        assertEquals(today, DailySalesAggregator.toLocalDate(editedSale!!.soldAtEpochMs, zoneId))
        // Horário local original preservado!
        assertEquals(expectedTime, CurrencyFormatter.formatTimeOfEpochMillis(editedSale.soldAtEpochMs, zoneId))

        // 7) Volta ao Fechamento -> filtro Hoje agora exibe a venda movida para Hoje
        composeRule.onNodeWithTag("nav_tab_fechamento").performClick()
        waitForTag("screen_fechamento")
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("closing_day_$today")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains(todayFormatted, substring = true)
            .assertTextContains("R$ 500,00", substring = true)
    }

    @Test
    fun `06 edit mode date change marks unsaved changes and triggers discard confirmation`() {
        val app = context as MixVendasApplication
        val zoneId = ZoneId.systemDefault()
        val yesterday = LocalDate.now(zoneId).minusDays(1)
        val savedId = runBlocking(Dispatchers.IO) {
            val saved = app.saleRepository.saveSale(
                result = buildPixSaleResult(unitPriceCents = 25_000L, note = "Teste dirty"),
                saleDate = yesterday,
                zoneId = zoneId,
            )
            saved.id
        }

        composeRule.onNodeWithTag("nav_tab_vendas").performClick()
        waitForTag("screen_vendas")
        composeRule.onNodeWithTag("period_dropdown_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("period_option_all").performClick()
        composeRule.waitForIdle()

        scrollToTagInList("screen_vendas", "sale_history_card_$savedId")
        composeRule.onNodeWithTag("sale_edit_$savedId").performScrollTo().performClick()
        waitForTag("vender_editing_banner")

        // Altera somente a data para Hoje e tenta cancelar edição -> deve pedir confirmação de descarte
        composeRule.onNodeWithTag("sale_date_selector_button").performScrollTo().performClick()
        waitForTag("sale_date_picker_dialog")
        composeRule.onNodeWithTag("sale_date_quick_today").performClick()
        waitForTagGone("sale_date_picker_dialog")

        composeRule.onNodeWithTag("topbar_back_button").performClick()
        waitForTag("discard_edit_dialog")
        composeRule.onNodeWithTag("discard_edit_confirm_button").performClick()
        waitForTag("screen_vendas")

        // Verifica que o banco manteve intacta a data original (ontem)
        val untouched = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(savedId) }
        assertNotNull(untouched)
        assertEquals(yesterday, DailySalesAggregator.toLocalDate(untouched!!.soldAtEpochMs, zoneId))
        assertFalse(DailySalesAggregator.toLocalDate(untouched.soldAtEpochMs, zoneId) == LocalDate.now(zoneId))
    }
}
