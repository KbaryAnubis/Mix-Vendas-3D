package com.mixvendas3d.app

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.CatalogValidationException
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.ui.screens.MixVendasShellUiState
import com.mixvendas3d.app.ui.screens.MixVendasShellViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage8AOfflinePersistenceIntegrationTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val createdTestDbNames = linkedSetOf<String>()
    private var savedTimeZone: TimeZone? = null

    @After
    fun tearDown() {
        savedTimeZone?.let { TimeZone.setDefault(it) }
        savedTimeZone = null
        createdTestDbNames.forEach { dbName ->
            assertNotEquals(AppDatabase.DATABASE_NAME, dbName)
            context.deleteDatabase(dbName)
        }
        createdTestDbNames.clear()
    }

    private fun newUniqueTestDbName(tag: String): String {
        val name = shortTestDatabaseName(tag)
        assertNotEquals(AppDatabase.DATABASE_NAME, name)
        createdTestDbNames.add(name)
        context.deleteDatabase(name)
        return name
    }

    private fun openFileBackedDatabase(dbName: String): AppDatabase {
        assertNotEquals(AppDatabase.DATABASE_NAME, dbName)
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

    private data class RepoBundle(
        val preferences: RoomPreferenceRepository,
        val catalog: RoomCatalogRepository,
        val machines: RoomMachineRepository,
        val sales: RoomSaleRepository,
    )

    private fun repositoriesFor(
        db: AppDatabase,
        clockMillis: () -> Long = { System.currentTimeMillis() },
    ): RepoBundle {
        return RepoBundle(
            preferences = RoomPreferenceRepository(
                preferenceDao = db.preferenceDao(),
                clockMillis = clockMillis,
            ),
            catalog = RoomCatalogRepository(
                personDao = db.personDao(),
                productDao = db.productDao(),
                clockMillis = clockMillis,
            ),
            machines = RoomMachineRepository(
                machineDao = db.machineDao(),
                clockMillis = clockMillis,
            ),
            sales = RoomSaleRepository(
                database = db,
                saleDao = db.saleDao(),
                clockMillis = clockMillis,
            ),
        )
    }

    private fun querySingleInt(db: AppDatabase, sql: String): Int {
        return db.openHelper.writableDatabase.query(sql).use { cursor ->
            assertTrue("Query deveria retornar ao menos uma linha: $sql", cursor.moveToFirst())
            cursor.getInt(0)
        }
    }

    private suspend fun awaitShellState(
        vm: MixVendasShellViewModel,
        predicate: (MixVendasShellUiState) -> Boolean,
    ): MixVendasShellUiState {
        val collectJob = CoroutineScope(Dispatchers.Main).launch {
            vm.uiState.collect {}
        }
        try {
            return withTimeout(5_000L) {
                while (!predicate(vm.uiState.value)) {
                    Shadows.shadowOf(Looper.getMainLooper()).idle()
                    delay(15L)
                }
                vm.uiState.value
            }
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `01 new file-backed database starts completely empty and stays empty across close and reopen`() = runBlocking {
        val dbName = newUniqueTestDbName("empty")

        val db1 = openFileBackedDatabase(dbName)
        try {
            val repos1 = repositoriesFor(db1)
            assertEquals(7, db1.openHelper.readableDatabase.version)
            assertEquals(1, querySingleInt(db1, "PRAGMA foreign_keys"))
            assertEquals(0, repos1.preferences.observePreferenceCount().first())
            assertTrue(repos1.catalog.observePeople().first().isEmpty())
            assertTrue(repos1.catalog.observeAllProducts().first().isEmpty())
            assertTrue(repos1.machines.observeMachines().first().isEmpty())
            assertTrue(repos1.sales.observeSales().first().isEmpty())
            val emptyReport = DailySalesAggregator.aggregate(emptyList())
            assertEquals(0, emptyReport.totalSalesCount)
            assertEquals(0L, emptyReport.totalSold.rawCents)
        } finally {
            db1.close()
        }

        assertTrue(context.getDatabasePath(dbName).exists())

        val db2 = openFileBackedDatabase(dbName)
        try {
            val repos2 = repositoriesFor(db2)
            assertEquals(7, db2.openHelper.readableDatabase.version)
            assertEquals(0, repos2.preferences.observePreferenceCount().first())
            assertTrue(repos2.catalog.observePeopleWithUsage().first().isEmpty())
            assertTrue(repos2.catalog.observeAllProducts().first().isEmpty())
            assertTrue(repos2.catalog.observeActiveProducts().first().isEmpty())
            assertTrue(repos2.machines.observeMachines().first().isEmpty())
            assertTrue(repos2.sales.observeSales().first().isEmpty())
        } finally {
            db2.close()
        }
    }

    @Test
    fun `02 people products fee modes machines and preferences persist identically after close and reopen`() = runBlocking {
        val dbName = newUniqueTestDbName("catalog")
        var fakeClock = 1_760_000_000_000L
        val nextClock = {
            fakeClock += 1_000L
            fakeClock
        }

        val db1 = openFileBackedDatabase(dbName)
        val expectedPeople: List<com.mixvendas3d.app.domain.models.PersonWithUsage>
        val expectedProducts: List<com.mixvendas3d.app.domain.models.Product>
        val expectedMachines: List<com.mixvendas3d.app.domain.models.Machine>
        try {
            val repos1 = repositoriesFor(db1, nextClock)

            repos1.preferences.savePreference("closing_default_period", "week")
            repos1.preferences.savePreference("csv_export_hint", "enabled")

            val ana = repos1.catalog.createPerson("Ana Clara")
            val bruno = repos1.catalog.createPerson("Bruno Silva")
            val carla = repos1.catalog.createPerson("Carla Dias")
            val renamedBruno = repos1.catalog.renamePerson(bruno.id, "Bruno Souza")
            assertEquals("Bruno Souza", renamedBruno.name)

            repos1.catalog.createProduct(
                name = "Vaso Espiral M",
                price = MoneyCents(8_550L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(5_550L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            repos1.catalog.createProduct(
                name = "Luminária Hex",
                price = MoneyCents(12_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(carla.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val inactiveProd = repos1.catalog.createProduct(
                name = "Suporte Controle",
                price = MoneyCents(3_999L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(carla.id, MoneyCents(2_500L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(1_499L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            repos1.catalog.setProductActive(inactiveProd.id, active = false)

            repos1.machines.createMachine(
                MachineInputDraft(
                    name = "Stone Pro NFC",
                    debitRateBps = 189,
                    creditEnabled = true,
                    creditDefaultRateBps = 349,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(
                        CardBrand.VISA to 329,
                        CardBrand.MASTERCARD to 365,
                        CardBrand.ELO to 425,
                    ),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(
                        1 to 0,
                        2 to 110,
                        3 to 185,
                        6 to 390,
                        12 to 740,
                    ),
                ),
            )
            repos1.machines.createMachine(
                MachineInputDraft(
                    name = "SumUp Solo Débito",
                    debitRateBps = 135,
                    creditEnabled = false,
                    creditDefaultRateBps = 0,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            expectedPeople = repos1.catalog.observePeopleWithUsage().first()
            expectedProducts = repos1.catalog.observeAllProducts().first()
            expectedMachines = repos1.machines.observeMachines().first()
        } finally {
            db1.close()
        }

        assertTrue(context.getDatabasePath(dbName).exists())

        val db2 = openFileBackedDatabase(dbName)
        try {
            val repos2 = repositoriesFor(db2)

            assertEquals(2, repos2.preferences.observePreferenceCount().first())
            assertEquals("week", repos2.preferences.getPreference("closing_default_period")?.value)
            assertEquals("enabled", repos2.preferences.getPreference("csv_export_hint")?.value)

            val reopenedPeople = repos2.catalog.observePeopleWithUsage().first()
            assertEquals(expectedPeople, reopenedPeople)

            val reopenedAllProducts = repos2.catalog.observeAllProducts().first()
            assertEquals(expectedProducts, reopenedAllProducts)

            val reopenedActiveProducts = repos2.catalog.observeActiveProducts().first()
            assertEquals(2, reopenedActiveProducts.size)
            assertTrue(reopenedActiveProducts.none { it.name == "Suporte Controle" })

            val byName = reopenedAllProducts.associateBy { it.name }
            assertEquals(FeeResponsibilityMode.INDIVIDUAL, byName.getValue("Vaso Espiral M").feeResponsibilityMode)
            assertEquals(expectedPeople.first { it.person.name == "Ana Clara" }.person.id, byName.getValue("Vaso Espiral M").feeOwnerPersonId)
            assertEquals(FeeResponsibilityMode.EQUAL, byName.getValue("Luminária Hex").feeResponsibilityMode)
            assertNull(byName.getValue("Luminária Hex").feeOwnerPersonId)
            assertEquals(FeeResponsibilityMode.PROPORTIONAL, byName.getValue("Suporte Controle").feeResponsibilityMode)
            assertFalse(byName.getValue("Suporte Controle").active)

            val reopenedMachines = repos2.machines.observeMachines().first()
            assertEquals(expectedMachines, reopenedMachines)
            val stone = reopenedMachines.first { it.name == "Stone Pro NFC" }
            assertEquals(189, stone.debitRateBps)
            assertEquals(365, stone.brandRatesBps[CardBrand.MASTERCARD])
            assertEquals(740, stone.installmentAddonsBps[12])
            assertEquals(550, stone.effectiveCreditRateBps(CardBrand.MASTERCARD, 3))
        } finally {
            db2.close()
        }
    }

    @Test
    fun `03 multi-product financial sales with discounts rates brands installments and splits persist identically across close and reopen`() = runBlocking {
        val dbName = newUniqueTestDbName("sales")
        var fakeClock = 1_760_100_000_000L
        val nextClock = {
            fakeClock += 5_000L
            fakeClock
        }

        val db1 = openFileBackedDatabase(dbName)
        val savedSalesPreClose: List<PersistedSale>
        try {
            val repos1 = repositoriesFor(db1, nextClock)

            val ana = repos1.catalog.createPerson("Ana")
            val bruno = repos1.catalog.createPerson("Bruno")
            val carla = repos1.catalog.createPerson("Carla")

            val prodInd = repos1.catalog.createProduct(
                name = "Dragão Articulado",
                price = MoneyCents(4_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(1_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            val prodEq = repos1.catalog.createProduct(
                name = "Porta Copo Trio",
                price = MoneyCents(3_333L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(1_111L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(1_111L)),
                    ProductParticipantDraft(carla.id, MoneyCents(1_111L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            val prodProp = repos1.catalog.createProduct(
                name = "Suporte Headset",
                price = MoneyCents(7_500L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(bruno.id, MoneyCents(5_000L)),
                    ProductParticipantDraft(carla.id, MoneyCents(2_500L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )

            val stone = repos1.machines.createMachine(
                MachineInputDraft(
                    name = "Stone Dual",
                    debitRateBps = 199,
                    creditEnabled = true,
                    creditDefaultRateBps = 399,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(
                        CardBrand.VISA to 399,
                        CardBrand.MASTERCARD to 450,
                    ),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100, 3 to 150),
                ),
            )

            // Venda 1: Multi-produto (2x Dragão = 90,00 + 1x Suporte = 75,00 -> 165,00)
            // Desconto específico no Suporte para 150,00; Crédito Mastercard 3x (450 + 150 = 600 bps = 6,00%)
            val s1 = repos1.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(
                            SaleItemInput(prodInd, 2),
                            SaleItemInput(prodProp, 1),
                        ),
                        finalAmountOverride = MoneyCents(15_000L),
                        discountMode = SaleDiscountInput.SpecificProduct(prodProp.id),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = stone,
                        cardBrand = CardBrand.MASTERCARD,
                        installments = 3,
                        note = "Entrega combinada cliente VIP",
                    ),
                ),
            )

            // Venda 2: Multi-produto com desconto proporcional + tarifa manual substitutiva
            val s2 = repos1.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(
                            SaleItemInput(prodEq, 3), // 99,99
                            SaleItemInput(prodInd, 1), // 45,00 -> subtotal 144,99
                        ),
                        finalAmountOverride = MoneyCents(13_000L),
                        discountMode = SaleDiscountInput.Proportional,
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = stone,
                        cardBrand = CardBrand.VISA,
                        installments = 1,
                        manualRateOverrideBps = 275,
                        note = "Taxa negociada manualmente",
                    ),
                ),
            )

            // Venda 3: Pix sem taxa
            val s3 = repos1.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodEq, 1)),
                        paymentMethod = SalePaymentMethod.PIX,
                        note = "",
                    ),
                ),
            )

            savedSalesPreClose = listOf(s3, s2, s1) // order: DESC by soldAtEpochMs
            savedSalesPreClose.forEach { sale ->
                assertEquals(
                    sale.calculation.originalSubtotal.rawCents - sale.calculation.discountTotal.rawCents,
                    sale.calculation.finalAmount.rawCents,
                )
                assertEquals(
                    sale.calculation.finalAmount.rawCents - sale.calculation.feeTotal.rawCents,
                    sale.calculation.netTotal.rawCents,
                )
                assertEquals(
                    sale.calculation.netTotal.rawCents,
                    sale.calculation.personTotals.sumOf { it.netAmount.rawCents },
                )
            }
            assertEquals(savedSalesPreClose, repos1.sales.observeSales().first())
        } finally {
            db1.close()
        }

        val db2 = openFileBackedDatabase(dbName)
        try {
            val repos2 = repositoriesFor(db2)
            val reopenedList = repos2.sales.observeSales().first()
            assertEquals(savedSalesPreClose, reopenedList)

            savedSalesPreClose.forEach { expected ->
                val byId = repos2.sales.getSaleById(expected.id)
                assertEquals(expected, byId)
            }

            // Verifica detalhes específicos de desconto específico, tarifa manual e parcelas após reabertura
            val sale1Reopened = repos2.sales.getSaleById(savedSalesPreClose[2].id)!!
            assertTrue(sale1Reopened.calculation.discountConfig is SaleDiscountConfigSnapshot.SpecificProduct)
            assertEquals(CardBrand.MASTERCARD, sale1Reopened.calculation.machineSnapshot?.appliedBrand)
            assertEquals(3, sale1Reopened.calculation.machineSnapshot?.appliedInstallments)
            assertEquals(600, sale1Reopened.calculation.effectiveFeeRateBps)
            assertEquals("Entrega combinada cliente VIP", sale1Reopened.calculation.note)

            val sale2Reopened = repos2.sales.getSaleById(savedSalesPreClose[1].id)!!
            assertTrue(sale2Reopened.calculation.discountConfig is SaleDiscountConfigSnapshot.Proportional)
            assertEquals(275, sale2Reopened.calculation.machineSnapshot?.manualRateOverrideBps)
            assertEquals(275, sale2Reopened.calculation.effectiveFeeRateBps)
        } finally {
            db2.close()
        }
    }

    @Test
    fun `04 post-sale catalog edits and person deletion never alter historical sales across close and reopen`() = runBlocking {
        val dbName = newUniqueTestDbName("immutability")
        var fakeClock = 1_760_200_000_000L
        val nextClock = {
            fakeClock += 2_000L
            fakeClock
        }

        val db1 = openFileBackedDatabase(dbName)
        val historicalSaleBeforeEdits: PersistedSale
        val anaId: String
        val prodId: String
        val machineId: String
        try {
            val repos1 = repositoriesFor(db1, nextClock)
            val ana = repos1.catalog.createPerson("Ana Original")
            val bruno = repos1.catalog.createPerson("Bruno Original")
            val carla = repos1.catalog.createPerson("Carla Removida")
            anaId = ana.id

            val prod = repos1.catalog.createProduct(
                name = "Kit Miniaturas Original",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(3_000L)),
                    ProductParticipantDraft(carla.id, MoneyCents(1_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )
            prodId = prod.id

            val machine = repos1.machines.createMachine(
                MachineInputDraft(
                    name = "Cielo Zip Antiga",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 500),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )
            machineId = machine.id

            historicalSaleBeforeEdits = repos1.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        finalAmountOverride = MoneyCents(18_000L),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.VISA,
                        installments = 1,
                        note = "Snapshot histórico congelado",
                    ),
                ),
            )

            // Edições posteriores no catálogo + exclusão de Carla após desvinculá-la
            repos1.catalog.renamePerson(ana.id, "Ana Atualizada")
            repos1.catalog.updateProduct(
                productId = prod.id,
                name = "Kit Miniaturas V2 Caro",
                price = MoneyCents(45_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(20_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(25_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
                feeOwnerPersonId = null,
            )
            repos1.catalog.deletePerson(carla.id)
            repos1.machines.updateMachine(
                machineId = machine.id,
                draft = MachineInputDraft(
                    name = "Cielo Zip Nova Taxa",
                    debitRateBps = 450,
                    creditEnabled = true,
                    creditDefaultRateBps = 1_200,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 1_200),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Mesmo antes de fechar, a venda permanece intacta
            assertEquals(historicalSaleBeforeEdits, repos1.sales.getSaleById(historicalSaleBeforeEdits.id))
        } finally {
            db1.close()
        }

        val db2 = openFileBackedDatabase(dbName)
        try {
            val repos2 = repositoriesFor(db2)

            // Catálogo refletiu as edições e exclusão após reabertura
            val people = repos2.catalog.observePeople().first()
            assertEquals(2, people.size)
            assertEquals("Ana Atualizada", people.first { it.id == anaId }.name)

            val updatedProd = repos2.catalog.observeAllProducts().first().single { it.id == prodId }
            assertEquals("Kit Miniaturas V2 Caro", updatedProd.name)
            assertEquals(45_000L, updatedProd.price.rawCents)
            assertEquals(FeeResponsibilityMode.EQUAL, updatedProd.feeResponsibilityMode)

            val updatedMachine = repos2.machines.observeMachines().first().single { it.id == machineId }
            assertEquals("Cielo Zip Nova Taxa", updatedMachine.name)
            assertEquals(1_200, updatedMachine.creditDefaultRateBps)

            // Venda histórica permanece 100% imutável após close() + reopen()
            val reopenedSale = repos2.sales.getSaleById(historicalSaleBeforeEdits.id)
            assertEquals(historicalSaleBeforeEdits, reopenedSale)
            assertEquals("Kit Miniaturas Original", reopenedSale!!.calculation.items.single().productName)
            assertEquals("Cielo Zip Antiga", reopenedSale.calculation.machineSnapshot?.machineName)
            assertEquals(500, reopenedSale.calculation.effectiveFeeRateBps)
            assertEquals(18_000L, reopenedSale.calculation.finalAmount.rawCents)
            assertEquals(900L, reopenedSale.calculation.feeTotal.rawCents)
            assertEquals(17_100L, reopenedSale.calculation.netTotal.rawCents)
            assertTrue(reopenedSale.calculation.personTotals.any { it.personName == "Ana Original" })
            assertTrue(reopenedSale.calculation.personTotals.any { it.personName == "Carla Removida" })
        } finally {
            db2.close()
        }
    }

    @Test
    fun `05 referential integrity foreign keys and cascade deletes hold after close and reopen with zero orphans`() = runBlocking {
        val dbName = newUniqueTestDbName("integrity")
        val db1 = openFileBackedDatabase(dbName)
        val anaId: String
        val brunoId: String
        val saleToDeleteId: String
        val saleToKeepId: String
        try {
            val repos1 = repositoriesFor(db1)
            val ana = repos1.catalog.createPerson("Ana FK")
            val bruno = repos1.catalog.createPerson("Bruno FK")
            anaId = ana.id
            brunoId = bruno.id

            val prod = repos1.catalog.createProduct(
                name = "Produto FK",
                price = MoneyCents(6_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(4_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(2_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
                feeOwnerPersonId = ana.id,
            )

            val machine = repos1.machines.createMachine(
                MachineInputDraft(
                    name = "Machine FK",
                    debitRateBps = 190,
                    creditEnabled = true,
                    creditDefaultRateBps = 390,
                    brandRatesEnabled = true,
                    brandRatesBps = mapOf(CardBrand.VISA to 390, CardBrand.ELO to 440),
                    installmentsEnabled = true,
                    installmentAddonsBps = mapOf(1 to 0, 2 to 100),
                ),
            )

            saleToKeepId = repos1.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                        cardBrand = CardBrand.ELO,
                        installments = 2,
                    ),
                ),
            ).id

            saleToDeleteId = repos1.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            ).id
        } finally {
            db1.close()
        }

        val db2 = openFileBackedDatabase(dbName)
        try {
            val repos2 = repositoriesFor(db2)

            assertEquals(1, querySingleInt(db2, "PRAGMA foreign_keys"))

            // Nenhum órfão antes de exclusão
            assertZeroOrphans(db2)

            // Exclusão de pessoa vinculada continua bloqueada transacionalmente e por ON DELETE RESTRICT após reabertura
            val participantDeleteErr = runCatching { repos2.catalog.deletePerson(brunoId) }.exceptionOrNull()
            assertTrue(participantDeleteErr is CatalogValidationException)

            val ownerDeleteErr = runCatching { repos2.catalog.deletePerson(anaId) }.exceptionOrNull()
            assertTrue(ownerDeleteErr is CatalogValidationException)

            val rawFkViolation = runCatching {
                db2.openHelper.writableDatabase.execSQL("DELETE FROM people WHERE id = '$anaId'")
            }.exceptionOrNull()
            assertTrue(rawFkViolation is SQLiteConstraintException)

            // Exclui uma venda e verifica remoção em cascata dos filhos sem deixar órfãos
            repos2.sales.deleteSale(saleToDeleteId)
            assertNull(repos2.sales.getSaleById(saleToDeleteId))
            assertNotNull(repos2.sales.getSaleById(saleToKeepId))
            assertEquals(1, db2.saleDao().countSales())
            assertEquals(1, db2.saleDao().countSaleItems())
            assertEquals(2, db2.saleDao().countSaleItemParticipants())
            assertEquals(2, db2.saleDao().countSalePersonTotals())
            assertZeroOrphans(db2)
        } finally {
            db2.close()
        }

        // Reabre terceira vez após deleteSale para confirmar persistência da exclusão em cascata
        val db3 = openFileBackedDatabase(dbName)
        try {
            assertEquals(1, db3.saleDao().countSales())
            assertEquals(1, db3.saleDao().countSaleItems())
            assertEquals(2, db3.saleDao().countSaleItemParticipants())
            assertEquals(2, db3.saleDao().countSalePersonTotals())
            assertZeroOrphans(db3)
        } finally {
            db3.close()
        }
    }

    private fun assertZeroOrphans(db: AppDatabase) {
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM product_participants pp LEFT JOIN products p ON pp.product_id = p.id WHERE p.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM product_participants pp LEFT JOIN people pe ON pp.person_id = pe.id WHERE pe.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM products p LEFT JOIN people pe ON p.fee_owner_person_id = pe.id WHERE p.fee_owner_person_id IS NOT NULL AND pe.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM machine_brand_rates mbr LEFT JOIN machines m ON mbr.machine_id = m.id WHERE m.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM machine_installment_rates mir LEFT JOIN machines m ON mir.machine_id = m.id WHERE m.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM sale_items si LEFT JOIN sales s ON si.sale_id = s.id WHERE s.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM sale_item_participants sip LEFT JOIN sale_items si ON sip.sale_item_id = si.id WHERE si.id IS NULL",
            ),
        )
        assertEquals(
            0,
            querySingleInt(
                db,
                "SELECT COUNT(*) FROM sale_person_totals spt LEFT JOIN sales s ON spt.sale_id = s.id WHERE s.id IS NULL",
            ),
        )
    }

    @Test
    fun `06 reporting aggregations period queries and viewmodel home and closing pipelines remain identical after reopen`() = runBlocking {
        savedTimeZone = TimeZone.getDefault()
        val spTz = TimeZone.getTimeZone("America/Sao_Paulo")
        TimeZone.setDefault(spTz)
        val zone: ZoneId = spTz.toZoneId()
        val today = LocalDate.now(zone)

        val dbName = newUniqueTestDbName("reporting")
        val db1 = openFileBackedDatabase(dbName)

        val preCloseAllSales: List<PersistedSale>
        val preCloseReport: com.mixvendas3d.app.domain.reporting.RealDailySalesReport
        val preCloseTodaySales: List<PersistedSale>
        val preCloseVmState: MixVendasShellUiState
        try {
            val catalog = RoomCatalogRepository(db1.personDao(), db1.productDao())
            val machines = RoomMachineRepository(db1.machineDao())
            val prefs = RoomPreferenceRepository(db1.preferenceDao())

            val ana = catalog.createPerson("Ana Relatório")
            val bruno = catalog.createPerson("Bruno Relatório")
            val prod = catalog.createProduct(
                name = "Estatueta Premium",
                price = MoneyCents(10_000L),
                active = true,
                participants = listOf(
                    ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                    ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
                ),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            val machine = machines.createMachine(
                MachineInputDraft(
                    name = "PagBank Smart",
                    debitRateBps = 200,
                    creditEnabled = true,
                    creditDefaultRateBps = 500,
                    brandRatesEnabled = false,
                    brandRatesBps = emptyMap(),
                    installmentsEnabled = false,
                    installmentAddonsBps = emptyMap(),
                ),
            )

            // Venda 1: hoje 22:30 America/Sao_Paulo (= 01:30 UTC do dia seguinte), Crédito 5% -> R$ 100,00 bruto, R$ 5,00 taxa, R$ 95,00 líq
            val todayNightMs = ZonedDateTime.of(
                today.plusDays(1),
                LocalTime.of(1, 30),
                ZoneOffset.UTC,
            ).toInstant().toEpochMilli()
            RoomSaleRepository(db1, db1.saleDao(), clockMillis = { todayNightMs }).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        paymentMethod = SalePaymentMethod.CREDIT,
                        machine = machine,
                    ),
                ),
            )

            // Venda 2: 3 dias atrás 15:00 local, Débito 2% -> 2x = R$ 200,00 bruto, R$ 4,00 taxa, R$ 196,00 líq
            val threeDaysAgoMs = today.minusDays(3).atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
            RoomSaleRepository(db1, db1.saleDao(), clockMillis = { threeDaysAgoMs }).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 2)),
                        paymentMethod = SalePaymentMethod.DEBIT,
                        machine = machine,
                    ),
                ),
            )

            // Venda 3: 38 dias atrás 10:00 local, Pix com desconto de 10,00 -> R$ 90,00 bruto, R$ 0,00 taxa, R$ 90,00 líq
            val oldSaleMs = today.minusDays(38).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
            RoomSaleRepository(db1, db1.saleDao(), clockMillis = { oldSaleMs }).saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prod, 1)),
                        finalAmountOverride = MoneyCents(9_000L),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )

            val saleRepo1 = RoomSaleRepository(db1, db1.saleDao())
            preCloseAllSales = saleRepo1.observeSales().first()
            preCloseTodaySales = saleRepo1.observeSalesByDateRange(today, today, zone).first()
            preCloseReport = DailySalesAggregator.aggregate(preCloseAllSales, zone)

            val vm1 = MixVendasShellViewModel(prefs, catalog, machines, saleRepo1)
            preCloseVmState = awaitShellState(vm1) { state ->
                state.homeRealTotalSalesCount == 3 && state.closingRealTotalSalesCount == 3
            }
        } finally {
            db1.close()
        }

        val db2 = openFileBackedDatabase(dbName)
        try {
            val repos2 = repositoriesFor(db2)
            val reopenedAllSales = repos2.sales.observeSales().first()
            val reopenedTodaySales = repos2.sales.observeSalesByDateRange(today, today, zone).first()
            val (dayStart, dayEndExclusive) = DailySalesAggregator.localDayEpochRange(today, zone)
            val reopenedPeriodSales = repos2.sales.observeSalesByPeriod(dayStart, dayEndExclusive - 1L).first()
            val reopenedReport = DailySalesAggregator.aggregate(reopenedAllSales, zone)

            assertEquals(preCloseAllSales, reopenedAllSales)
            assertEquals(1, preCloseTodaySales.size)
            assertEquals(preCloseTodaySales, reopenedTodaySales)
            assertEquals(reopenedTodaySales, reopenedPeriodSales)
            assertEquals(preCloseReport, reopenedReport)
            assertEquals(3, reopenedReport.totalSalesCount)
            assertEquals(3, reopenedReport.totalDaysCount)
            assertEquals(39_000L, reopenedReport.totalSold.rawCents)

            val vm2 = MixVendasShellViewModel(
                repos2.preferences,
                repos2.catalog,
                repos2.machines,
                repos2.sales,
            )
            val reopenedVmState = awaitShellState(vm2) { state ->
                state.homeRealTotalSalesCount == 3 && state.closingRealTotalSalesCount == 3
            }

            assertEquals(preCloseVmState.homeRealTotalGross, reopenedVmState.homeRealTotalGross)
            assertEquals(preCloseVmState.homeRealTotalFee, reopenedVmState.homeRealTotalFee)
            assertEquals(preCloseVmState.homeRealTotalNet, reopenedVmState.homeRealTotalNet)
            assertEquals(preCloseVmState.homeRealPersonSummaries, reopenedVmState.homeRealPersonSummaries)
            assertEquals(preCloseVmState.homeDailyReport, reopenedVmState.homeDailyReport)

            assertEquals(40_000L, reopenedVmState.closingRealOriginalSubtotal.rawCents)
            assertEquals(1_000L, reopenedVmState.closingRealTotalDiscount.rawCents)
            assertEquals(39_000L, reopenedVmState.closingRealTotalGross.rawCents)
            assertEquals(900L, reopenedVmState.closingRealTotalFee.rawCents)
            assertEquals(38_100L, reopenedVmState.closingRealTotalNet.rawCents)
            assertEquals(preCloseVmState.closingRealPaymentBreakdowns, reopenedVmState.closingRealPaymentBreakdowns)
            assertEquals(preCloseVmState.closingRealMachineBreakdowns, reopenedVmState.closingRealMachineBreakdowns)
            assertEquals(
                reopenedVmState.closingRealTotalNet.rawCents,
                reopenedVmState.closingRealPersonSummaries.sumOf { it.netAmount.rawCents },
            )
        } finally {
            db2.close()
        }
    }

    @Test
    fun `07 separate file-backed test databases remain strictly isolated across close and reopen`() = runBlocking {
        val dbNameA = newUniqueTestDbName("isolated_a")
        val dbNameB = newUniqueTestDbName("isolated_b")

        val dbA1 = openFileBackedDatabase(dbNameA)
        val dbB1 = openFileBackedDatabase(dbNameB)
        try {
            val reposA = repositoriesFor(dbA1)
            val reposB = repositoriesFor(dbB1)

            val personA = reposA.catalog.createPerson("Pessoa Exclusiva A")
            val prodA = reposA.catalog.createProduct(
                name = "Produto Exclusivo A",
                price = MoneyCents(2_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(personA.id, MoneyCents(2_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            reposA.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodA, 1)),
                        paymentMethod = SalePaymentMethod.CASH,
                    ),
                ),
            )

            val personB = reposB.catalog.createPerson("Pessoa Exclusiva B")
            val prodB = reposB.catalog.createProduct(
                name = "Produto Exclusivo B",
                price = MoneyCents(7_500L),
                active = true,
                participants = listOf(ProductParticipantDraft(personB.id, MoneyCents(7_500L))),
                feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
                feeOwnerPersonId = null,
            )
            reposB.sales.saveSale(
                SaleFinancialEngine.calculate(
                    SaleCalculationInput(
                        items = listOf(SaleItemInput(prodB, 2)),
                        paymentMethod = SalePaymentMethod.PIX,
                    ),
                ),
            )
        } finally {
            dbA1.close()
            dbB1.close()
        }

        val dbA2 = openFileBackedDatabase(dbNameA)
        val dbB2 = openFileBackedDatabase(dbNameB)
        try {
            val reposA2 = repositoriesFor(dbA2)
            val reposB2 = repositoriesFor(dbB2)

            val peopleA = reposA2.catalog.observePeople().first()
            val salesA = reposA2.sales.observeSales().first()
            assertEquals(listOf("Pessoa Exclusiva A"), peopleA.map { it.name })
            assertEquals(1, salesA.size)
            assertEquals("Produto Exclusivo A", salesA.single().calculation.items.single().productName)
            assertEquals(2_500L, salesA.single().calculation.finalAmount.rawCents)

            val peopleB = reposB2.catalog.observePeople().first()
            val salesB = reposB2.sales.observeSales().first()
            assertEquals(listOf("Pessoa Exclusiva B"), peopleB.map { it.name })
            assertEquals(1, salesB.size)
            assertEquals("Produto Exclusivo B", salesB.single().calculation.items.single().productName)
            assertEquals(15_000L, salesB.single().calculation.finalAmount.rawCents)
        } finally {
            dbA2.close()
            dbB2.close()
        }
    }
}
