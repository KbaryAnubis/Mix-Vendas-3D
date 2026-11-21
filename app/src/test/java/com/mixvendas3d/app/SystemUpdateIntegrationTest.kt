package com.mixvendas3d.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.data.repository.RoomSystemUpdateRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.domain.update.SystemUpdateConflictCode
import com.mixvendas3d.app.domain.update.SystemUpdateFailureReason
import java.io.File
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemUpdateIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdTestDbNames = linkedSetOf<String>()
    private val tempFiles = mutableListOf<File>()

    @After
    fun tearDown() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        runBlocking(Dispatchers.IO) {
            app.database.clearAllTables()
        }
        createdTestDbNames.forEach { dbName ->
            assertNotEquals(AppDatabase.DATABASE_NAME, dbName)
            context.deleteDatabase(dbName)
        }
        createdTestDbNames.clear()
    }

    private fun newFileDbName(tag: String): String {
        val name = shortTestDatabaseName(tag)
        createdTestDbNames.add(name)
        context.deleteDatabase(name)
        return name
    }

    private fun openFileDb(dbName: String): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
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
    }

    private fun newTempJsonFile(name: String): File {
        val file = File(composeRule.activity.cacheDir, name)
        file.delete()
        tempFiles.add(file)
        return file
    }

    private fun waitUiUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun openUpdateSystemScreen() {
        composeRule.onNodeWithTag("nav_tab_mais").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mais_item_update_system")
            .performScrollTo()
            .performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("screen_mais_detail_update_system").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `01 empty database and synchronized history report zero updates and zero conflicts`() = runBlocking {
        val db = openFileDb(newFileDbName("empty"))
        try {
            val updateRepo = RoomSystemUpdateRepository(db)
            val emptyAnalysis = updateRepo.analyze()
            assertEquals(0, emptyAnalysis.totalSalesEvaluated)
            assertEquals(0, emptyAnalysis.salesToUpdateCount)
            assertEquals(0, emptyAnalysis.alreadyCorrectCount)
            assertEquals(0, emptyAnalysis.requiresReviewCount)
            assertFalse(emptyAnalysis.hasChangesToApply)
            assertNull(updateRepo.getUndoBatchSummary())

            val catalog = RoomCatalogRepository(db.personDao(), db.productDao())
            val sales = RoomSaleRepository(db, db.saleDao())
            val p1 = catalog.createPerson("Ana")
            val prod = catalog.createProduct(
                name = "Chaveiro 3D",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(2_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )

            val syncAnalysis = updateRepo.analyze()
            assertEquals(1, syncAnalysis.totalSalesEvaluated)
            assertEquals(0, syncAnalysis.salesToUpdateCount)
            assertEquals(1, syncAnalysis.alreadyCorrectCount)
            assertEquals(0, syncAnalysis.requiresReviewCount)

            val nothingResult = updateRepo.applyUpdate(
                expectedFingerprint = syncAnalysis.localStateFingerprint,
                allowPartialUpdate = false,
            )
            assertFalse(nothingResult.success)
            assertEquals(SystemUpdateFailureReason.NOTHING_TO_UPDATE, nothingResult.failureReason)
        } finally {
            db.close()
        }
    }

    @Test
    fun `02 analyze never mutates Room tables before explicit confirmation`() = runBlocking {
        val db = openFileDb(newFileDbName("readonly_analyze"))
        try {
            var clock = 1_761_000_000_000L
            val nextClock = {
                clock += 1_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val ana = catalog.createPerson("Ana Antiga")
            val prod = catalog.createProduct(
                name = "Vaso Espiral",
                price = MoneyCents(5_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(5_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "Ton",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 400,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val sale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )

            catalog.renamePerson(ana.id, "Ana Renomeada")
            machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Ton Plus",
                    debitRateBps = 150,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            val preAnalyzeRawSales = db.saleDao().getAllSales()
            val preAnalyzeRawItems = db.saleDao().getAllSaleItems()
            val preAnalyzeRawParticipants = db.saleDao().getAllSaleItemParticipants()
            val preAnalyzeRawTotals = db.saleDao().getAllSalePersonTotals()
            val preAnalyzePrefCount = db.preferenceDao().getAllPreferences().size

            val analysis = updateRepo.analyze()
            assertEquals(1, analysis.salesToUpdateCount)
            assertEquals(0, analysis.requiresReviewCount)
            assertEquals(400, analysis.updatableItems.single().historicalFeeRateBps)
            assertEquals(300, analysis.updatableItems.single().proposedFeeRateBps)

            // Análise não gravou nada em Room
            assertEquals(preAnalyzeRawSales, db.saleDao().getAllSales())
            assertEquals(preAnalyzeRawItems, db.saleDao().getAllSaleItems())
            assertEquals(preAnalyzeRawParticipants, db.saleDao().getAllSaleItemParticipants())
            assertEquals(preAnalyzeRawTotals, db.saleDao().getAllSalePersonTotals())
            assertEquals(preAnalyzePrefCount, db.preferenceDao().getAllPreferences().size)
            assertEquals(sale, sales.getSaleById(sale.id))
            assertNull(updateRepo.getUndoBatchSummary())
        } finally {
            db.close()
        }
    }

    @Test
    fun `03 renaming person updates sales reports csv and json backup while keeping date qty and charged price`() = runBlocking {
        val db = openFileDb(newFileDbName("rename_person"))
        try {
            var clock = 1_761_100_000_000L
            val nextClock = {
                clock += 2_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val p1 = catalog.createPerson("Maria Antiga")
            val p2 = catalog.createPerson("João")
            val prod = catalog.createProduct(
                name = "Luminária Lua",
                price = MoneyCents(8_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(p1.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(p2.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val savedSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        finalAmountOverride = MoneyCents(15_000L),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )

            catalog.renamePerson(p1.id, "Maria Nova")
            val analysis = updateRepo.analyze()
            assertEquals(1, analysis.salesToUpdateCount)
            val exec = updateRepo.applyUpdate(analysis.localStateFingerprint, allowPartialUpdate = false)
            assertTrue(exec.success)
            assertEquals(1, exec.updatedSalesCount)

            val updatedSale = sales.getSaleById(savedSale.id)!!
            assertEquals(savedSale.soldAtEpochMs, updatedSale.soldAtEpochMs)
            assertEquals(15_000L, updatedSale.calculation.finalAmount.rawCents)
            assertEquals(2, updatedSale.calculation.items.single().quantity)
            assertTrue(updatedSale.calculation.personTotals.any { it.personId == p1.id && it.personName == "Maria Nova" })
            assertTrue(updatedSale.calculation.personTotals.none { it.personName == "Maria Antiga" })

            val report = DailySalesAggregator.aggregate(listOf(updatedSale))
            assertEquals(15_000L, report.totalSold.rawCents)

            val csv = CsvSaleExporter.export(listOf(updatedSale))
            assertTrue(csv.detailedCsvContent.contains("Maria Nova"))
            assertFalse(csv.detailedCsvContent.contains("Maria Antiga"))

            val json = JsonBackupExporter.export(db)
            val parsed = JsonBackupReader.readAndValidate(json.utf8Bytes)
            assertTrue(parsed.isValid)
            assertTrue(parsed.validatedData!!.salePersonTotals.any { it.personName == "Maria Nova" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `04 changing machine rate recalculates automatic card sales in cents and preserves manual override`() = runBlocking {
        val db = openFileDb(newFileDbName("machine_rates"))
        try {
            var clock = 1_761_200_000_000L
            val nextClock = {
                clock += 2_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val ana = catalog.createPerson("Ana")
            val prod = catalog.createProduct(
                name = "Boneco 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "Stone Pro",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 500),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100),
                ),
            )

            // Venda automática Crédito Visa 2x: 500 + 100 = 600 bps -> R$ 6,00 taxa, R$ 94,00 líquido
            val autoSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 2,
                    ),
                ),
            )
            // Venda com tarifa manual substitutiva: 275 bps -> R$ 2,75 taxa, R$ 97,25 líquido
            val manualSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 2,
                        manualRateOverrideBps = 275,
                    ),
                ),
            )

            machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Stone Pro V2",
                    debitRateBps = 150,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 300),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 50),
                ),
            )

            val analysis = updateRepo.analyze()
            assertEquals(2, analysis.salesToUpdateCount)
            val exec = updateRepo.applyUpdate(analysis.localStateFingerprint, allowPartialUpdate = false)
            assertTrue(exec.success)

            val updatedAuto = sales.getSaleById(autoSale.id)!!
            // Nova taxa automática Visa 2x: 300 + 50 = 350 bps -> R$ 3,50 taxa, R$ 96,50 líquido
            assertEquals(350, updatedAuto.calculation.effectiveFeeRateBps)
            assertEquals(350L, updatedAuto.calculation.feeTotal.rawCents)
            assertEquals(9_650L, updatedAuto.calculation.netTotal.rawCents)
            assertEquals("Stone Pro V2", updatedAuto.calculation.machineSnapshot?.machineName)

            val updatedManual = sales.getSaleById(manualSale.id)!!
            // Tarifa manual DEVE ser preservada em 275 bps, apenas nome da maquininha acompanha o cadastro
            assertEquals(275, updatedManual.calculation.machineSnapshot?.manualRateOverrideBps)
            assertEquals(275, updatedManual.calculation.effectiveFeeRateBps)
            assertEquals(275L, updatedManual.calculation.feeTotal.rawCents)
            assertEquals(9_725L, updatedManual.calculation.netTotal.rawCents)
            assertEquals("Stone Pro V2", updatedManual.calculation.machineSnapshot?.machineName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `05 changing product split or fee responsibility recalculates safely while preserving historical charged price`() = runBlocking {
        val db = openFileDb(newFileDbName("product_split_price"))
        try {
            var clock = 1_761_300_000_000L
            val nextClock = {
                clock += 2_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val ana = catalog.createPerson("Ana")
            val bruno = catalog.createPerson("Bruno")
            val prodSplit = catalog.createProduct(
                name = "Suporte Dual",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(7_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            val prodPriceChanged = catalog.createProduct(
                name = "Porta Caneta",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(2_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "PagSeguro",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 1_000,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1: Crédito 10% sobre R$ 100,00 -> Taxa R$ 10,00 inteira para Ana (60,00) e Bruno (30,00)
            val s1 = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodSplit, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )
            // Venda 2: Porta Caneta vendido a R$ 40,00 em Crédito 10%
            val s2 = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodPriceChanged, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )

            // Altera divisão e modo de tarifa do prodSplit mantendo preço R$ 100,00
            catalog.updateProduct(
                productId = prodSplit.id,
                name = "Suporte Dual",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(5_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )

            // Altera preço do catálogo de prodPriceChanged de R$ 40,00 para R$ 65,00 (com os mesmos participantes)
            catalog.updateProduct(
                productId = prodPriceChanged.id,
                name = "Porta Caneta Luxo",
                price = MoneyCents(6_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )

            val analysis = updateRepo.analyze()
            assertEquals(2, analysis.salesToUpdateCount)
            assertEquals(0, analysis.requiresReviewCount)
            val exec = updateRepo.applyUpdate(analysis.localStateFingerprint, allowPartialUpdate = false)
            assertTrue(exec.success)

            val s1Updated = sales.getSaleById(s1.id)!!
            // Agora cada participante leva 50% (R$ 50,00) - metade da taxa (R$ 5,00) = R$ 45,00 líquido
            assertEquals(4_500L, s1Updated.calculation.personTotals.first { it.personId == ana.id }.netAmount.rawCents)
            assertEquals(4_500L, s1Updated.calculation.personTotals.first { it.personId == bruno.id }.netAmount.rawCents)

            val s2Updated = sales.getSaleById(s2.id)!!
            // Preço histórico cobrado R$ 40,00 permanece preservado mesmo com catálogo a R$ 65,00
            assertEquals(4_000L, s2Updated.calculation.items.single().unitPriceOriginal.rawCents)
            assertEquals(4_000L, s2Updated.calculation.finalAmount.rawCents)
            assertEquals("Porta Caneta Luxo", s2Updated.calculation.items.single().productName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `06 conflicts block unsafe updates and explicit partial opt-in preserves conflicted sales untouched`() = runBlocking {
        val db = openFileDb(newFileDbName("conflicts_partial"))
        try {
            var clock = 1_761_400_000_000L
            val nextClock = {
                clock += 2_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val ana = catalog.createPerson("Ana Conflito")
            val bruno = catalog.createPerson("Bruno")
            val prodPriceMismatch = catalog.createProduct(
                name = "Produto Preço e Pessoas",
                price = MoneyCents(4_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(4_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodValid = catalog.createProduct(
                name = "Produto Válido",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machineCreditDisabled = machines.createMachine(
                MachineInputDraft(
                    name = "SumUp",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 400,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            val validSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodValid, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            val conflictPriceSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodPriceMismatch, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
            val conflictMachineSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodValid, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machineCreditDisabled,
                    ),
                ),
            )

            // Renomeia Ana -> afeta validSale; muda preço + participantes -> conflito em conflictPriceSale;
            // desativa crédito -> conflito em conflictMachineSale
            catalog.renamePerson(ana.id, "Ana Atualizada")
            catalog.updateProduct(
                productId = prodPriceMismatch.id,
                name = "Produto Preço e Pessoas",
                price = MoneyCents(5_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            machines.setCreditEnabled(machineCreditDisabled.id, creditEnabled = false)

            val analysis = updateRepo.analyze()
            assertEquals(1, analysis.salesToUpdateCount)
            assertEquals(2, analysis.requiresReviewCount)
            val codesBySale = analysis.conflictItems.associate { it.saleId to it.codes }
            assertTrue(
                codesBySale.getValue(conflictPriceSale.id)
                    .contains(SystemUpdateConflictCode.PRICE_AND_RULES_MISMATCH),
            )
            assertTrue(
                codesBySale.getValue(conflictMachineSale.id)
                    .contains(SystemUpdateConflictCode.CREDIT_DISABLED),
            )

            // Sem opt-in parcial, bloqueia a gravação
            val blocked = updateRepo.applyUpdate(
                expectedFingerprint = analysis.localStateFingerprint,
                allowPartialUpdate = false,
            )
            assertFalse(blocked.success)
            assertEquals(SystemUpdateFailureReason.CONFLICTS_NOT_CONFIRMED_FOR_PARTIAL, blocked.failureReason)
            assertEquals(validSale, sales.getSaleById(validSale.id))

            // Com opt-in parcial explícito, atualiza apenas validSale e mantém conflitos intactos
            val partial = updateRepo.applyUpdate(
                expectedFingerprint = analysis.localStateFingerprint,
                allowPartialUpdate = true,
            )
            assertTrue(partial.success)
            assertEquals(1, partial.updatedSalesCount)
            assertEquals(2, partial.preservedConflictCount)
            assertEquals(
                "Ana Atualizada",
                sales.getSaleById(validSale.id)!!.calculation.personTotals.single().personName,
            )
            assertEquals(conflictPriceSale, sales.getSaleById(conflictPriceSale.id))
            assertEquals(conflictMachineSale, sales.getSaleById(conflictMachineSale.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun `07 stale fingerprint rejects apply and transactional failure rolls back completely`() = runBlocking {
        val db = openFileDb(newFileDbName("stale_and_rollback"))
        try {
            var clock = 1_761_500_000_000L
            val nextClock = {
                clock += 2_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val baseRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val p = catalog.createPerson("Carlos")
            val prod = catalog.createProduct(
                name = "Produto Teste",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(p.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val s1 = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.PIX),
                ),
            )
            val s2 = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(listOf(SaleItemInput(prod, 2)), paymentMethod = SalePaymentMethod.CASH),
                ),
            )

            catalog.renamePerson(p.id, "Carlos V2")
            val staleAnalysis = baseRepo.analyze()

            // Altera cadastro após análise -> fingerprint deve rejeitar
            catalog.renamePerson(p.id, "Carlos V3")
            val staleRes = baseRepo.applyUpdate(staleAnalysis.localStateFingerprint, allowPartialUpdate = false)
            assertFalse(staleRes.success)
            assertEquals(SystemUpdateFailureReason.LOCAL_STATE_CHANGED, staleRes.failureReason)
            assertEquals(s1, sales.getSaleById(s1.id))

            // Nova análise válida, mas falha no meio da segunda gravação dentro da transação -> rollback completo
            val freshAnalysis = baseRepo.analyze()
            assertEquals(2, freshAnalysis.salesToUpdateCount)
            val failingRepo = RoomSystemUpdateRepository(
                database = db,
                clockMillis = nextClock,
                onAfterPartialUpdateWrite = { index ->
                    if (index == 0) {
                        throw IllegalStateException("Falha simulada após gravar 1ª venda")
                    }
                },
            )
            val rollbackRes = failingRepo.applyUpdate(freshAnalysis.localStateFingerprint, allowPartialUpdate = false)
            assertFalse(rollbackRes.success)
            assertEquals(SystemUpdateFailureReason.EXECUTION_FAILED_ROLLED_BACK, rollbackRes.failureReason)
            assertTrue(rollbackRes.rolledBack)
            assertEquals(s1, sales.getSaleById(s1.id))
            assertEquals(s2, sales.getSaleById(s2.id))
            assertNull(baseRepo.getUndoBatchSummary())
        } finally {
            db.close()
        }
    }

    @Test
    fun `08 undo restores exact snapshot leaves newer sales intact and blocks blind undo if edited afterwards`() = runBlocking {
        val db = openFileDb(newFileDbName("undo_and_conflict"))
        try {
            var clock = 1_761_600_000_000L
            val nextClock = {
                clock += 2_000L
                clock
            }
            val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

            val ana = catalog.createPerson("Ana Undo")
            val prod = catalog.createProduct(
                name = "Vaso Undo",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "Máquina Undo",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val oldSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )

            machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Máquina Undo",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 350,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val analysis = updateRepo.analyze()
            assertTrue(updateRepo.applyUpdate(analysis.localStateFingerprint, allowPartialUpdate = false).success)
            assertEquals(350, sales.getSaleById(oldSale.id)!!.calculation.effectiveFeeRateBps)

            // Nova venda criada DEPOIS da atualização deve permanecer intacta ao desfazer
            val newerSale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )

            val undoRes = updateRepo.revertLastUpdate()
            assertTrue(undoRes.success)
            assertEquals(1, undoRes.revertedSalesCount)
            assertEquals(oldSale, sales.getSaleById(oldSale.id))
            assertEquals(newerSale, sales.getSaleById(newerSale.id))
            assertNull(updateRepo.getUndoBatchSummary())

            // Reaplica atualização e edita a venda manualmente -> desfazer deve bloquear
            val reAnalysis = updateRepo.analyze()
            assertTrue(updateRepo.applyUpdate(reAnalysis.localStateFingerprint, allowPartialUpdate = false).success)
            val modifiedCalc = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    paymentMethod = SalePaymentMethod.CASH,
                ),
            )
            sales.updateSale(oldSale.id, modifiedCalc)

            val summaryAfterEdit = updateRepo.getUndoBatchSummary()!!
            assertFalse(summaryAfterEdit.canUndoSafely)
            assertTrue(summaryAfterEdit.undoBlockReasons.isNotEmpty())

            val blockedUndo = updateRepo.revertLastUpdate()
            assertFalse(blockedUndo.success)
            assertEquals(SystemUpdateFailureReason.UNDO_CONFLICT_POST_UPDATE_MODIFIED, blockedUndo.failureReason)
        } finally {
            db.close()
        }
    }

    @Test
    fun `09 updated history and undo batch survive Room close and reopen`() = runBlocking {
        val dbName = newFileDbName("reopen_persistence")
        var clock = 1_761_700_000_000L
        val nextClock = {
            clock += 2_000L
            clock
        }
        val saleId: String
        val originalNetCents: Long
        val updatedNetCents: Long

        val db1 = openFileDb(dbName)
        try {
            val catalog = RoomCatalogRepository(db1.personDao(), db1.productDao(), clockMillis = nextClock)
            val machines = RoomMachineRepository(db1.machineDao(), clockMillis = nextClock)
            val sales = RoomSaleRepository(db1, db1.saleDao(), clockMillis = nextClock)
            val updateRepo = RoomSystemUpdateRepository(db1, clockMillis = nextClock)

            val p = catalog.createPerson("Reabertura")
            val prod = catalog.createProduct(
                name = "Caixa 3D",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "Point",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val sale = sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )
            saleId = sale.id
            originalNetCents = sale.calculation.netTotal.rawCents // 9500

            machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Point",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 250,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val analysis = updateRepo.analyze()
            assertTrue(updateRepo.applyUpdate(analysis.localStateFingerprint, allowPartialUpdate = false).success)
            updatedNetCents = sales.getSaleById(saleId)!!.calculation.netTotal.rawCents // 9750
            assertEquals(9_750L, updatedNetCents)
        } finally {
            db1.close()
        }

        // Reabre o banco: valores atualizados E lote para desfazer devem estar íntegros
        val db2 = openFileDb(dbName)
        try {
            val sales2 = RoomSaleRepository(db2, db2.saleDao(), clockMillis = nextClock)
            val updateRepo2 = RoomSystemUpdateRepository(db2, clockMillis = nextClock)

            assertEquals(updatedNetCents, sales2.getSaleById(saleId)!!.calculation.netTotal.rawCents)
            val undoSummary = updateRepo2.getUndoBatchSummary()
            assertNotNull(undoSummary)
            assertTrue(undoSummary!!.canUndoSafely)
            assertEquals(1, undoSummary.updatedSalesCount)

            val undoRes = updateRepo2.revertLastUpdate()
            assertTrue(undoRes.success)
        } finally {
            db2.close()
        }

        // Reabre terceira vez após desfazer: volta exatamente ao valor original e limpa lote de undo
        val db3 = openFileDb(dbName)
        try {
            val sales3 = RoomSaleRepository(db3, db3.saleDao())
            val updateRepo3 = RoomSystemUpdateRepository(db3)
            assertEquals(originalNetCents, sales3.getSaleById(saleId)!!.calculation.netTotal.rawCents)
            assertNull(updateRepo3.getUndoBatchSummary())
        } finally {
            db3.close()
        }
    }

    @Test
    fun `10 compose ui executes preview backup cancel confirm apply dialog undo dialog and Produtos hints`() {
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val saleId = runBlocking(Dispatchers.IO) {
            val p1 = app.catalogRepository.createPerson("Lucas Antes")
            val prod = app.catalogRepository.createProduct(
                name = "Organizador Modular",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(10_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val m = app.machineRepository.createMachine(
                MachineInputDraft(
                    name = "InfinitePay",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            val createdSale = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = m,
                    ),
                ),
            )
            app.catalogRepository.renamePerson(p1.id, "Lucas Depois")
            app.machineRepository.updateMachine(
                machineId = m.id,
                draft = MachineInputDraft(
                    name = "InfinitePay",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 300,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            createdSale.id
        }

        // Verifica também que a dica em Vender aponta para "Mais → Produtos."
        composeRule.onNodeWithTag("nav_tab_vender").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Tipos / produtos", substring = true, ignoreCase = true)
            .assertCountEquals(0)

        openUpdateSystemScreen()

        // 1. Analisar ajustes -> Prévia Antes/Depois sem alterar banco
        composeRule.onNodeWithTag("update_system_analyze_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_summary_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("update_system_metric_to_update").assertTextContains("1", substring = true)
        composeRule.onNodeWithTag("update_system_metric_to_update").assertTextContains("Vendas a atualizar", substring = true)
        composeRule.onNodeWithTag("update_system_metric_already_correct").assertTextContains("Já corretas", substring = true)
        composeRule.onNodeWithTag("update_system_metric_requires_review").assertTextContains("Exigem revisão", substring = true)

        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_preview_banner"))
        composeRule.onNodeWithTag("update_system_preview_banner")
            .assertTextContains("Prévia: nada mudou no histórico ainda.", substring = true)
        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_preview_card_$saleId"))
        composeRule.onNodeWithTag("update_system_preview_card_$saleId").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Lucas Antes", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Lucas Depois", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("5,00%", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("3,00%", substring = true).fetchSemanticsNodes().isNotEmpty())

        // Banco ainda intacto após prévia
        val saleDuringPreview = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(500, saleDuringPreview.calculation.effectiveFeeRateBps)

        // 2. Backup JSON via SAF (cancelamento seguro e depois salvamento real)
        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_backup_button"))
        composeRule.onNodeWithTag("update_system_backup_button").performClick()

        val shadowActivity = Shadows.shadowOf(composeRule.activity)
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val cancelStarted = shadowActivity.nextStartedActivityForResult
        shadowActivity.receiveResult(cancelStarted.intent, Activity.RESULT_CANCELED, null)

        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_backup_feedback").fetchSemanticsNodes().isNotEmpty()
        }

        // Exporta JSON real
        composeRule.onNodeWithTag("update_system_backup_button").performClick()
        waitUiUntil { shadowActivity.peekNextStartedActivityForResult() != null }
        val okStarted = shadowActivity.nextStartedActivityForResult
        val backupFile = newTempJsonFile(okStarted.intent.getStringExtra(Intent.EXTRA_TITLE) ?: "backup.json")
        shadowActivity.receiveResult(
            okStarted.intent,
            Activity.RESULT_OK,
            Intent().setData(Uri.fromFile(backupFile)),
        )
        waitUiUntil { backupFile.exists() && backupFile.length() > 0L }
        assertTrue(JsonBackupReader.readAndValidate(backupFile.readBytes()).isValid)

        // 3. Confirmação explícita e execução da atualização
        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_apply_button"))
        composeRule.onNodeWithTag("update_system_apply_button").assertIsEnabled().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_confirm_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("update_system_confirm_apply_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_success_banner").fetchSemanticsNodes().isNotEmpty()
        }
        val saleAfterApply = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(300, saleAfterApply.calculation.effectiveFeeRateBps)
        assertEquals("Lucas Depois", saleAfterApply.calculation.personTotals.single().personName)

        // 4. Banner e execução de Desfazer última atualização
        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_undo_banner"))
        composeRule.onNodeWithTag("update_system_undo_banner").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText("Existe uma atualização anterior que pode ser desfeita.", substring = true)
                .fetchSemanticsNodes().isNotEmpty(),
        )
        composeRule.onNodeWithTag("update_system_undo_button").assertIsEnabled().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_undo_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("update_system_confirm_undo_button").performClick()

        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_undo_success_banner").fetchSemanticsNodes().isNotEmpty()
        }
        val saleAfterUndo = runBlocking(Dispatchers.IO) { app.saleRepository.getSaleById(saleId)!! }
        assertEquals(500, saleAfterUndo.calculation.effectiveFeeRateBps)
        assertEquals("Lucas Antes", saleAfterUndo.calculation.personTotals.single().personName)
    }

    @Test
    fun `11 missing entities brand installment negative net conflicts and undo rollback work with Compose conflict UI`() {
        val db = openFileDb(newFileDbName("extra_conflicts"))
        try {
            runBlocking {
                var clock = 1_761_800_000_000L
                val nextClock = {
                    clock += 2_000L
                    clock
                }
                val catalog = RoomCatalogRepository(db.personDao(), db.productDao(), clockMillis = nextClock)
                val machines = RoomMachineRepository(db.machineDao(), clockMillis = nextClock)
                val sales = RoomSaleRepository(db, db.saleDao(), clockMillis = nextClock)
                val updateRepo = RoomSystemUpdateRepository(db, clockMillis = nextClock)

                val ana = catalog.createPerson("Ana Extra")
                val bruno = catalog.createPerson("Bruno Extra")
                val prodMissing = catalog.createProduct(
                    name = "Prod Deletado",
                    price = MoneyCents(3_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(3_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
                val prodFeeOwner = catalog.createProduct(
                    name = "Prod Dono Taxa",
                    price = MoneyCents(5_000L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft(ana.id, MoneyCents(4_500L)),
                        ProductParticipantDraft(bruno.id, MoneyCents(500L)),
                    ),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
                val machineBrandAndInst = machines.createMachine(
                    MachineInputDraft(
                        name = "Cielo Extra",
                        debitRateBps = 200,
                        creditEnabled = true,
                        creditDefaultRateBps = 400,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = true,
                        installmentAddonsBps = mapOf(1 to 0, 2 to 100),
                    ),
                )
                val machineDeleted = machines.createMachine(
                    MachineInputDraft(
                        name = "Maq Deletada",
                        debitRateBps = 200,
                        creditEnabled = true,
                        creditDefaultRateBps = 300,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )

                val sMissingProd = sales.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(listOf(SaleItemInput(prodMissing, 1)), paymentMethod = SalePaymentMethod.PIX),
                    ),
                )
                val sMissingBrand = sales.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(prodFeeOwner, 1)),
                            paymentMethod = SalePaymentMethod.CREDIT,
                            machine = machineBrandAndInst,
                            installments = 2,
                        ),
                    ),
                )
                val sDeletedMachine = sales.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(prodFeeOwner, 1)),
                            paymentMethod = SalePaymentMethod.DEBIT,
                            machine = machineDeleted,
                        ),
                    ),
                )
                val sNegNet = sales.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(prodFeeOwner, 1)),
                            paymentMethod = SalePaymentMethod.CREDIT,
                            machine = machineBrandAndInst,
                            cardBrand = CardBrand.VISA,
                            manualRateOverrideBps = 1_500, // 15% taxa = 7,50 > 5,00 parte do Bruno
                        ),
                    ),
                )

                // Agora provoca conflitos reais:
                // 1) Remove produto vendido
                db.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE id = '${prodMissing.id}'")
                // 2) Exige bandeira e desativa parcelamento
                machines.updateMachine(
                    machineId = machineBrandAndInst.id,
                    draft = MachineInputDraft(
                        name = "Cielo Extra",
                        debitRateBps = 200,
                        creditEnabled = true,
                        creditDefaultRateBps = 400,
                        brandRatesEnabled = true,
                        brandRatesBps = mapOf(CardBrand.VISA to 400),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
                // 3) Remove máquina usada em sDeletedMachine
                db.openHelper.writableDatabase.execSQL("DELETE FROM machines WHERE id = '${machineDeleted.id}'")
                // 4) Muda tarifa do produto para INDIVIDUAL no Bruno (5,00 < 7,50 taxa -> líquido negativo)
                catalog.updateProduct(
                    productId = prodFeeOwner.id,
                    name = "Prod Dono Taxa",
                    price = MoneyCents(5_000L),
                    active = true,
                    participants = listOf(
                        ProductParticipantDraft(ana.id, MoneyCents(4_500L)),
                        ProductParticipantDraft(bruno.id, MoneyCents(500L)),
                    ),
                    feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                    feeOwnerPersonId = bruno.id,
                )

                val analysis = updateRepo.analyze()
                val byId = analysis.conflictItems.associate { it.saleId to it.codes }
                assertTrue(byId.getValue(sMissingProd.id).contains(SystemUpdateConflictCode.PRODUCT_NOT_FOUND))
                assertTrue(byId.getValue(sMissingBrand.id).contains(SystemUpdateConflictCode.MISSING_REQUIRED_BRAND))
                assertTrue(byId.getValue(sMissingBrand.id).contains(SystemUpdateConflictCode.INSTALLMENTS_DISABLED))
                assertTrue(byId.getValue(sDeletedMachine.id).contains(SystemUpdateConflictCode.MACHINE_NOT_FOUND))
                assertTrue(byId.getValue(sNegNet.id).contains(SystemUpdateConflictCode.NEGATIVE_PARTICIPANT_NET))

                // Verifica também rollback transacional quando revertLastUpdate falha no meio
                val validMachine = machines.createMachine(
                    MachineInputDraft(
                        name = "Ok Machine",
                        debitRateBps = 100,
                        creditEnabled = true,
                        creditDefaultRateBps = 200,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
                val pOk = catalog.createProduct(
                    name = "Prod Ok",
                    price = MoneyCents(2_000L),
                    active = true,
                    participants = listOf(ProductParticipantDraft(ana.id, MoneyCents(2_000L))),
                    feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                    feeOwnerPersonId = null,
                )
                val sOk = sales.saveSale(
                    SaleFinancialEngine.calculate(
                        SaleCalculationInput(
                            items = listOf(SaleItemInput(pOk, 1)),
                            paymentMethod = SalePaymentMethod.DEBIT,
                            machine = validMachine,
                        ),
                    ),
                )
                machines.updateMachine(
                    machineId = validMachine.id,
                    draft = MachineInputDraft(
                        name = "Ok Machine",
                        debitRateBps = 150,
                        creditEnabled = true,
                        creditDefaultRateBps = 200,
                        brandRatesEnabled = false,
                        brandRatesBps = emptyMap(),
                        installmentsEnabled = false,
                        installmentAddonsBps = emptyMap(),
                    ),
                )
                val partialAnalysis = updateRepo.analyze()
                assertEquals(1, partialAnalysis.salesToUpdateCount)
                assertTrue(updateRepo.applyUpdate(partialAnalysis.localStateFingerprint, allowPartialUpdate = true).success)
                assertEquals(150, sales.getSaleById(sOk.id)!!.calculation.effectiveFeeRateBps)

                val failingUndoRepo = RoomSystemUpdateRepository(
                    database = db,
                    clockMillis = nextClock,
                    onAfterPartialUndoWrite = {
                        throw IllegalStateException("Falha simulada ao desfazer")
                    },
                )
                val failedUndo = failingUndoRepo.revertLastUpdate()
                assertFalse(failedUndo.success)
                assertEquals(SystemUpdateFailureReason.UNDO_FAILED_ROLLED_BACK, failedUndo.failureReason)
                assertTrue(failedUndo.rolledBack)
                assertEquals(150, sales.getSaleById(sOk.id)!!.calculation.effectiveFeeRateBps)
                assertNotNull(updateRepo.getUndoBatchSummary())
            }
        } finally {
            db.close()
        }

        // Compose UI: banner "Corrija estes conflitos antes de atualizar.", botão travado sem opt-in, habilitado após opt-in
        val app = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        val (validSaleId, conflictSaleId) = runBlocking(Dispatchers.IO) {
            val p = app.catalogRepository.createPerson("Pessoa UI")
            val prodOk = app.catalogRepository.createProduct(
                name = "Item UI Ok",
                price = MoneyCents(3_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p.id, MoneyCents(3_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val prodBroken = app.catalogRepository.createProduct(
                name = "Item UI Removido",
                price = MoneyCents(2_000L),
                active = true,
                participants = listOf(ProductParticipantDraft(p.id, MoneyCents(2_000L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val sOk = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(listOf(SaleItemInput(prodOk, 1)), paymentMethod = SalePaymentMethod.PIX),
                ),
            )
            val sBroken = app.saleRepository.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(listOf(SaleItemInput(prodBroken, 1)), paymentMethod = SalePaymentMethod.PIX),
                ),
            )
            app.catalogRepository.renamePerson(p.id, "Pessoa UI Editada")
            app.database.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE id = '${prodBroken.id}'")
            sOk.id to sBroken.id
        }

        openUpdateSystemScreen()
        composeRule.onNodeWithTag("update_system_analyze_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_summary_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("update_system_apply_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_conflicts_banner"))
        composeRule.onNodeWithTag("update_system_conflicts_banner")
            .assertTextContains("Corrija estes conflitos antes de atualizar.", substring = true)

        composeRule.onNodeWithTag("screen_mais_detail_update_system")
            .performScrollToNode(hasTestTag("update_system_partial_opt_in_checkbox"))
        composeRule.onNodeWithTag("update_system_partial_opt_in_checkbox").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("update_system_apply_button").assertIsEnabled().performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_confirm_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("update_system_confirm_apply_button").performClick()
        waitUiUntil {
            composeRule.onAllNodesWithTag("update_system_success_banner").fetchSemanticsNodes().isNotEmpty()
        }

        runBlocking(Dispatchers.IO) {
            assertEquals("Pessoa UI Editada", app.saleRepository.getSaleById(validSaleId)!!.calculation.personTotals.single().personName)
            assertEquals("Pessoa UI", app.saleRepository.getSaleById(conflictSaleId)!!.calculation.personTotals.single().personName)
        }
    }
}
