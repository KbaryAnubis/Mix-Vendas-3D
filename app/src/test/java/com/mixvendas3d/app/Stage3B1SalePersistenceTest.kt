package com.mixvendas3d.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomPreferenceRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage3B1SalePersistenceTest {

    private fun productFixture(
        id: String,
        name: String,
        priceCents: Long,
        feeMode: FeeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
        feeOwnerPersonId: String? = null,
        participants: List<ProductParticipantSplit>,
    ): Product = Product(
        id = id,
        name = name,
        price = MoneyCents(priceCents),
        active = true,
        feeResponsibilityMode = feeMode,
        feeOwnerPersonId = feeOwnerPersonId,
        participants = participants,
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_000L,
    )

    private fun split(personId: String, name: String, cents: Long, colorIndex: Int = 0): ProductParticipantSplit {
        return ProductParticipantSplit(
            personId = personId,
            personName = name,
            personColorIndex = colorIndex,
            amount = MoneyCents(cents),
        )
    }

    private fun machineFixture(
        id: String = "mach-1",
        name: String = "Ton Pro",
        debitBps: Int = 190,
        creditEnabled: Boolean = true,
        creditDefaultBps: Int = 280,
        brandEnabled: Boolean = true,
        brandRates: Map<CardBrand, Int> = mapOf(
            CardBrand.VISA to 250,
            CardBrand.MASTERCARD to 270,
        ),
        installmentsEnabled: Boolean = true,
        installmentAddons: Map<Int, Int> = mapOf(1 to 0, 2 to 110, 3 to 160, 4 to 210, 6 to 320, 12 to 650),
    ): Machine = Machine(
        id = id,
        name = name,
        debitRateBps = debitBps,
        creditEnabled = creditEnabled,
        creditDefaultRateBps = creditDefaultBps,
        brandRatesEnabled = brandEnabled,
        brandRatesBps = brandRates,
        installmentsEnabled = installmentsEnabled,
        installmentAddonsBps = installmentAddons,
        createdAtEpochMs = 1_700_000_000_000L,
        updatedAtEpochMs = 1_700_000_000_000L,
    )

    @Test
    fun `01 save single product cash and pix sales preserves zero fees and exact snapshots`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var now = 1_710_000_000_000L
        val repo = RoomSaleRepository(db, db.saleDao(), clockMillis = { now })

        val prodA = productFixture(
            id = "prod-a",
            name = "Produto A",
            priceCents = 2_200L,
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = "p1",
            participants = listOf(split("p1", "Pessoa 1", 1_700L, 0), split("p2", "Pessoa 2", 500L, 1)),
        )

        val cashResult = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1)),
                paymentMethod = SalePaymentMethod.CASH,
                note = "Pagamento em espécie",
            ),
        )
        val savedCash = repo.saveSale(cashResult)

        now += 5_000L
        val pixResult = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1)),
                paymentMethod = SalePaymentMethod.PIX,
                note = "Pix instantâneo",
            ),
        )
        val savedPix = repo.saveSale(pixResult)

        assertEquals(SalePaymentMethod.CASH, savedCash.calculation.paymentMethod)
        assertNull(savedCash.calculation.machineSnapshot)
        assertEquals(0, savedCash.calculation.effectiveFeeRateBps)
        assertEquals(2_200L, savedCash.calculation.finalAmount.rawCents)
        assertEquals(0L, savedCash.calculation.feeTotal.rawCents)
        assertEquals(2_200L, savedCash.calculation.netTotal.rawCents)

        assertEquals(SalePaymentMethod.PIX, savedPix.calculation.paymentMethod)
        assertNull(savedPix.calculation.machineSnapshot)
        assertEquals(2_200L, savedPix.calculation.netTotal.rawCents)

        val loadedAll = repo.observeSales().first()
        assertEquals(2, loadedAll.size)
        // Ordem decrescente por soldAtEpochMs
        assertEquals(savedPix.id, loadedAll[0].id)
        assertEquals(savedCash.id, loadedAll[1].id)

        db.close()
    }

    @Test
    fun `02 save sale with multiple products preserves all item snapshots and totals`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 1_710_000_100_000L })

        val prodA = productFixture(
            id = "prod-a",
            name = "Produto A",
            priceCents = 2_200L,
            participants = listOf(split("p1", "Pessoa 1", 1_700L), split("p2", "Pessoa 2", 500L)),
        )
        val prodB = productFixture(
            id = "prod-b",
            name = "Produto B",
            priceCents = 3_500L,
            participants = listOf(split("p1", "Pessoa 1", 1_500L), split("p3", "Pessoa 3", 2_000L)),
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                paymentMethod = SalePaymentMethod.PIX,
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        assertEquals(2, reloaded.calculation.items.size)
        assertEquals(5_700L, reloaded.calculation.finalAmount.rawCents)
        assertEquals(3, reloaded.calculation.personTotals.size)
        assertEquals(3_200L, reloaded.calculation.personTotals.first { it.personId == "p1" }.netAmount.rawCents)
        assertEquals(500L, reloaded.calculation.personTotals.first { it.personId == "p2" }.netAmount.rawCents)
        assertEquals(2_000L, reloaded.calculation.personTotals.first { it.personId == "p3" }.netAmount.rawCents)

        db.close()
    }

    @Test
    fun `03 save sale with different quantities and consolidated lines persists exact quantities`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())

        val prodA = productFixture(
            id = "prod-a",
            name = "Suporte",
            priceCents = 2_000L,
            participants = listOf(split("p1", "Ana", 1_200L), split("p2", "Beto", 800L)),
        )
        val prodB = productFixture(
            id = "prod-b",
            name = "Base",
            priceCents = 1_500L,
            participants = listOf(split("p1", "Ana", 500L), split("p2", "Beto", 1_000L)),
        )

        // Produto A repetido na entrada (2 + 1 = 3) + Produto B (2) + limite máximo 99 suportado
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 2), SaleItemInput(prodA, 1), SaleItemInput(prodB, 99)),
                paymentMethod = SalePaymentMethod.CASH,
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        assertEquals(2, reloaded.calculation.items.size)
        val itemB = reloaded.calculation.items.first { it.productId == "prod-b" }
        val itemA = reloaded.calculation.items.first { it.productId == "prod-a" }
        assertEquals(99, itemB.quantity)
        assertEquals(148_500L, itemB.originalSubtotal.rawCents)
        assertEquals(3, itemA.quantity)
        assertEquals(6_000L, itemA.originalSubtotal.rawCents)
        assertEquals(154_500L, reloaded.calculation.finalAmount.rawCents)

        db.close()
    }

    @Test
    fun `04 save sale with opposite splits between two people persists independent per-item shares`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())

        val prodA = productFixture(
            id = "prod-a",
            name = "Produto A",
            priceCents = 2_200L,
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = "p1",
            participants = listOf(split("p1", "Pessoa 1", 1_700L), split("p2", "Pessoa 2", 500L)),
        )
        val prodB = productFixture(
            id = "prod-b",
            name = "Produto B",
            priceCents = 2_200L,
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = "p2",
            participants = listOf(split("p1", "Pessoa 1", 500L), split("p2", "Pessoa 2", 1_700L)),
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                paymentMethod = SalePaymentMethod.DEBIT,
                machine = machineFixture(debitBps = 200),
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        val reloadedA = reloaded.calculation.items.first { it.productId == "prod-a" }
        val reloadedB = reloaded.calculation.items.first { it.productId == "prod-b" }

        assertEquals(1_700L, reloadedA.participantSplits.first { it.personId == "p1" }.grossAmount.rawCents)
        assertEquals(44L, reloadedA.participantSplits.first { it.personId == "p1" }.feeAmount.rawCents)
        assertEquals(500L, reloadedA.participantSplits.first { it.personId == "p2" }.grossAmount.rawCents)
        assertEquals(0L, reloadedA.participantSplits.first { it.personId == "p2" }.feeAmount.rawCents)

        assertEquals(500L, reloadedB.participantSplits.first { it.personId == "p1" }.grossAmount.rawCents)
        assertEquals(0L, reloadedB.participantSplits.first { it.personId == "p1" }.feeAmount.rawCents)
        assertEquals(1_700L, reloadedB.participantSplits.first { it.personId == "p2" }.grossAmount.rawCents)
        assertEquals(44L, reloadedB.participantSplits.first { it.personId == "p2" }.feeAmount.rawCents)

        assertEquals(2_156L, reloaded.calculation.personTotals.first { it.personId == "p1" }.netAmount.rawCents)
        assertEquals(2_156L, reloaded.calculation.personTotals.first { it.personId == "p2" }.netAmount.rawCents)

        db.close()
    }

    @Test
    fun `05 save sale with three or more participants persists exact splits and rounding`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())

        val prod4 = productFixture(
            id = "prod-4",
            name = "Kit 4 Sócios",
            priceCents = 10_000L,
            feeMode = FeeResponsibilityMode.EQUAL,
            participants = listOf(
                split("p1", "Ana", 4_000L, 0),
                split("p2", "Bruno", 3_000L, 1),
                split("p3", "Carla", 2_000L, 2),
                split("p4", "Daniel", 1_000L, 3),
            ),
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod4, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machineFixture(brandEnabled = false, installmentsEnabled = false, creditDefaultBps = 253),
                installments = 1,
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        assertEquals(4, reloaded.calculation.personTotals.size)
        assertEquals(4, reloaded.calculation.items.single().participantSplits.size)
        assertEquals(10_000L, reloaded.calculation.personTotals.sumOf { it.grossAmount.rawCents })
        assertEquals(253L, reloaded.calculation.personTotals.sumOf { it.feeAmount.rawCents })
        assertEquals(9_747L, reloaded.calculation.personTotals.sumOf { it.netAmount.rawCents })

        db.close()
    }

    @Test
    fun `06 save sale with specific product discount preserves target product and zero discount on siblings`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())

        val prodA = productFixture(
            id = "prod-a",
            name = "Produto A",
            priceCents = 5_000L,
            participants = listOf(split("p1", "Pessoa 1", 3_000L), split("p2", "Pessoa 2", 2_000L)),
        )
        val prodB = productFixture(
            id = "prod-b",
            name = "Produto B",
            priceCents = 5_000L,
            participants = listOf(split("p1", "Pessoa 1", 5_000L)),
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(9_000L),
                discountMode = SaleDiscountInput.SpecificProduct("prod-a"),
                paymentMethod = SalePaymentMethod.CASH,
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        val config = reloaded.calculation.discountConfig
        assertTrue(config is com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot.SpecificProduct)
        assertEquals("prod-a", (config as com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot.SpecificProduct).productId)
        assertEquals("Produto A", config.productNameSnapshot)
        assertEquals(1_000L, reloaded.calculation.discountTotal.rawCents)
        assertEquals(1_000L, reloaded.calculation.items.first { it.productId == "prod-a" }.discountAmount.rawCents)
        assertEquals(0L, reloaded.calculation.items.first { it.productId == "prod-b" }.discountAmount.rawCents)

        db.close()
    }

    @Test
    fun `07 save sale with proportional discount preserves exact proportional distribution`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())

        val prodA = productFixture(
            id = "prod-a",
            name = "A",
            priceCents = 6_000L,
            participants = listOf(split("p1", "P1", 6_000L)),
        )
        val prodB = productFixture(
            id = "prod-b",
            name = "B",
            priceCents = 4_000L,
            participants = listOf(split("p2", "P2", 4_000L)),
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
                finalAmountOverride = MoneyCents(9_000L),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.PIX,
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        assertTrue(reloaded.calculation.discountConfig is com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot.Proportional)
        assertEquals(600L, reloaded.calculation.items.first { it.productId == "prod-a" }.discountAmount.rawCents)
        assertEquals(400L, reloaded.calculation.items.first { it.productId == "prod-b" }.discountAmount.rawCents)
        assertEquals(9_000L, reloaded.calculation.finalAmount.rawCents)

        db.close()
    }

    @Test
    fun `08 save debit and credit sales stores exact machine snapshots`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())
        val prod = productFixture(
            id = "prod-1",
            name = "Produto",
            priceCents = 10_000L,
            participants = listOf(split("p1", "P1", 10_000L)),
        )
        val machine = machineFixture(brandEnabled = false, installmentsEnabled = false, debitBps = 190, creditDefaultBps = 280)

        val debit = repo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = machine,
                ),
            ),
        )
        val credit = repo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    installments = 1,
                ),
            ),
        )

        val loadedDebit = repo.getSaleById(debit.id)!!
        assertEquals(SalePaymentMethod.DEBIT, loadedDebit.calculation.paymentMethod)
        assertEquals(190, loadedDebit.calculation.effectiveFeeRateBps)
        assertEquals(190L, loadedDebit.calculation.feeTotal.rawCents)
        assertNull(loadedDebit.calculation.machineSnapshot?.appliedBrand)
        assertNull(loadedDebit.calculation.machineSnapshot?.appliedInstallments)

        val loadedCredit = repo.getSaleById(credit.id)!!
        assertEquals(SalePaymentMethod.CREDIT, loadedCredit.calculation.paymentMethod)
        assertEquals(280, loadedCredit.calculation.effectiveFeeRateBps)
        assertEquals(280L, loadedCredit.calculation.feeTotal.rawCents)
        assertEquals(1, loadedCredit.calculation.machineSnapshot?.appliedInstallments)

        db.close()
    }

    @Test
    fun `09 save credit sale with brand rate plus installment addon preserves exact snapshot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())
        val prod = productFixture(
            id = "prod-1",
            name = "Peça 3D Grande",
            priceCents = 20_000L,
            participants = listOf(split("p1", "P1", 12_000L), split("p2", "P2", 8_000L)),
        )
        val machine = machineFixture() // Visa = 250 bps, 3x addon = 160 bps => 410 bps

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.VISA,
                installments = 3,
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        val snap = reloaded.calculation.machineSnapshot!!
        assertEquals(CardBrand.VISA, snap.appliedBrand)
        assertEquals(3, snap.appliedInstallments)
        assertEquals(410, snap.automaticRateBps)
        assertNull(snap.manualRateOverrideBps)
        assertEquals(410, snap.effectiveRateBps)
        assertEquals(820L, reloaded.calculation.feeTotal.rawCents)
        assertEquals(19_180L, reloaded.calculation.netTotal.rawCents)

        db.close()
    }

    @Test
    fun `10 save sale with manual fee override preserves automatic and manual rates distinctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())
        val prod = productFixture(
            id = "prod-1",
            name = "Item",
            priceCents = 10_000L,
            participants = listOf(split("p1", "P1", 10_000L)),
        )
        val machine = machineFixture() // Mastercard 270 + 6x 320 = 590 bps automático

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(prod, 1)),
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = machine,
                cardBrand = CardBrand.MASTERCARD,
                installments = 6,
                manualRateOverrideBps = 500, // 5,00% manual
            ),
        )
        val saved = repo.saveSale(calc)
        val reloaded = repo.getSaleById(saved.id)!!

        val snap = reloaded.calculation.machineSnapshot!!
        assertEquals(590, snap.automaticRateBps)
        assertEquals(500, snap.manualRateOverrideBps)
        assertEquals(500, snap.effectiveRateBps)
        assertEquals(500L, reloaded.calculation.feeTotal.rawCents)
        assertEquals(9_500L, reloaded.calculation.netTotal.rawCents)

        db.close()
    }

    @Test
    fun `11 full query by id, date order, period, and reactive flow survive close and reopen`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "reopen.db"
        context.deleteDatabase(dbName)

        var clock = LocalDate.of(2025, 3, 10).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3600_000L
        val db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val repo1 = RoomSaleRepository(db1, db1.saleDao(), clockMillis = { clock })

        val prod = productFixture("p-1", "Chaveiro", 2_000L, participants = listOf(split("u1", "Lucas", 2_000L)))
        val calc1 = SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH, note = "Dia 10"))
        val saleDay10 = repo1.saveSale(calc1)

        clock = LocalDate.of(2025, 3, 15).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() + 3600_000L
        val calc2 = SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 2)), paymentMethod = SalePaymentMethod.PIX, note = "Dia 15"))
        val saleDay15 = repo1.saveSale(calc2)
        db1.close()

        val db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        val repo2 = RoomSaleRepository(db2, db2.saleDao())

        val allOrdered = repo2.observeSales().first()
        assertEquals(2, allOrdered.size)
        assertEquals(saleDay15.id, allOrdered[0].id)
        assertEquals(saleDay10.id, allOrdered[1].id)

        val single = repo2.observeSaleById(saleDay15.id).first()!!
        assertEquals("Dia 15", single.calculation.note)
        assertEquals(4_000L, single.calculation.finalAmount.rawCents)

        val periodMarch14To16 = repo2.observeSalesByDateRange(
            fromDate = LocalDate.of(2025, 3, 14),
            toDate = LocalDate.of(2025, 3, 16),
            zoneId = ZoneOffset.UTC,
        ).first()
        assertEquals(1, periodMarch14To16.size)
        assertEquals(saleDay15.id, periodMarch14To16.single().id)

        // Atualiza fluxo reativamente ao inserir nova venda
        clock = LocalDate.of(2025, 3, 20).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val repo2Writable = RoomSaleRepository(db2, db2.saleDao(), clockMillis = { clock })
        val saleDay20 = repo2Writable.saveSale(calc1)
        val afterInsert = repo2.observeSales().first()
        assertEquals(3, afterInsert.size)
        assertEquals(saleDay20.id, afterInsert.first().id)

        db2.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `12 historical sale snapshot remains immutable when catalog people, products, splits, or machines are edited or deleted`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
        val machineRepo = RoomMachineRepository(db.machineDao())
        val saleRepo = RoomSaleRepository(db, db.saleDao())

        val p1 = catalogRepo.createPerson("Carlos")
        val p2 = catalogRepo.createPerson("Mariana")
        val prod = catalogRepo.createProduct(
            name = "Dragão 3D",
            price = MoneyCents(4_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(3_000L)),
                ProductParticipantDraft(p2.id, MoneyCents(1_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerPersonId = p1.id,
        )
        val machine = machineRepo.createMachine(
            MachineInputDraft(
                name = "SumUp Top",
                debitRateBps = 190,
                creditEnabled = true,
                creditDefaultRateBps = 310,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 290),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 2 to 100),
            ),
        )

        val sale = saleRepo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 2,
                ),
            ),
        ) // 8.000 centavos, taxa 390 bps (290 + 100), tarifa 312 centavos para Carlos

        // Agora altera nomes, preço, divisão, responsável de tarifa e apaga/edita maquininha no catálogo
        catalogRepo.renamePerson(p1.id, "Carlos Renomeado")
        catalogRepo.renamePerson(p2.id, "Mariana Renomeada")
        catalogRepo.updateProduct(
            productId = prod.id,
            name = "Dragão Atualizado",
            price = MoneyCents(9_900L),
            active = false,
            participants = listOf(
                ProductParticipantDraft(p1.id, MoneyCents(4_900L)),
                ProductParticipantDraft(p2.id, MoneyCents(5_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
            feeOwnerPersonId = null,
        )
        machineRepo.updateMachine(
            machineId = machine.id,
            draft = MachineInputDraft(
                name = "Maquininha Nova",
                debitRateBps = 99,
                creditEnabled = false,
                creditDefaultRateBps = 0,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )
        db.machineDao().deleteById(machine.id)

        // A venda salva precisa permanecer 100% intacta com os dados históricos
        val historical = saleRepo.getSaleById(sale.id)!!
        val item = historical.calculation.items.single()
        assertEquals("Dragão 3D", item.productName)
        assertEquals(4_000L, item.unitPriceOriginal.rawCents)
        assertEquals(8_000L, item.finalAmount.rawCents)
        assertEquals(FeeResponsibilityMode.INDIVIDUAL, item.feeResponsibilityMode)
        assertEquals("Carlos", item.feeOwnerPersonName)
        assertEquals("SumUp Top", historical.calculation.machineSnapshot?.machineName)
        assertEquals(390, historical.calculation.effectiveFeeRateBps)
        assertEquals(312L, historical.calculation.feeTotal.rawCents)
        assertEquals("Carlos", historical.calculation.personTotals.first { it.personId == p1.id }.personName)
        assertEquals(5_688L, historical.calculation.personTotals.first { it.personId == p1.id }.netAmount.rawCents)
        assertEquals("Mariana", historical.calculation.personTotals.first { it.personId == p2.id }.personName)
        assertEquals(2_000L, historical.calculation.personTotals.first { it.personId == p2.id }.netAmount.rawCents)

        db.close()
    }

    @Test
    fun `13 update sale preserves original id and soldAt while advancing updatedAt and atomically replacing items and totals`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var clock = 1_710_000_000_000L
        val repo = RoomSaleRepository(db, db.saleDao(), clockMillis = { clock })

        val prodA = productFixture("prod-a", "A", 2_000L, participants = listOf(split("p1", "P1", 1_200L), split("p2", "P2", 800L)))
        val prodB = productFixture("prod-b", "B", 3_000L, participants = listOf(split("p1", "P1", 1_500L), split("p3", "P3", 1_500L)))

        val initial = repo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodA, 1)),
                    paymentMethod = SalePaymentMethod.CASH,
                    note = "Original",
                ),
            ),
        )
        assertEquals(1_710_000_000_000L, initial.soldAtEpochMs)
        assertEquals(1_710_000_000_000L, initial.updatedAtEpochMs)

        clock = 1_710_000_500_000L
        val edited = repo.updateSale(
            saleId = initial.id,
            result = SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodB, 2)),
                    paymentMethod = SalePaymentMethod.PIX,
                    note = "Editada",
                ),
            ),
        )

        assertEquals(initial.id, edited.id)
        assertEquals(1_710_000_000_000L, edited.soldAtEpochMs)
        assertEquals(1_710_000_500_000L, edited.updatedAtEpochMs)
        assertEquals("Editada", edited.calculation.note)
        assertEquals(1, edited.calculation.items.size)
        assertEquals("prod-b", edited.calculation.items.single().productId)
        assertEquals(6_000L, edited.calculation.finalAmount.rawCents)
        assertEquals(1, db.saleDao().countSales())
        assertEquals(1, db.saleDao().countSaleItems())
        assertEquals(2, db.saleDao().countSaleItemParticipants())
        assertEquals(2, db.saleDao().countSalePersonTotals())

        db.close()
    }

    @Test
    fun `14 editing previously discounted sale preserves original unit prices without applying discount twice`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var clock = 1_710_000_000_000L
        val repo = RoomSaleRepository(db, db.saleDao(), clockMillis = { clock })

        val prodA = productFixture(
            id = "prod-a",
            name = "Vaso 3D",
            priceCents = 2_200L,
            participants = listOf(split("p1", "Pessoa 1", 1_700L), split("p2", "Pessoa 2", 500L)),
        )
        // Salva com desconto de R$ 4,00 (final R$ 18,00)
        val initial = repo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prodA, 1)),
                    finalAmountOverride = MoneyCents(1_800L),
                    discountMode = SaleDiscountInput.Proportional,
                    paymentMethod = SalePaymentMethod.CASH,
                ),
            ),
        )
        assertEquals(2_200L, initial.calculation.originalSubtotal.rawCents)
        assertEquals(400L, initial.calculation.discountTotal.rawCents)
        assertEquals(1_800L, initial.calculation.finalAmount.rawCents)

        // Reconstrói para edição e altera apenas a observação (mantendo final R$ 18,00)
        clock += 10_000L
        val sameAmountRecomputed = SaleFinancialEngine.calculate(
            initial.toEditableCalculationInput(overrideNote = "Conferido"),
        )
        val updatedSameAmount = repo.updateSale(initial.id, sameAmountRecomputed)
        assertEquals(2_200L, updatedSameAmount.calculation.originalSubtotal.rawCents)
        assertEquals(400L, updatedSameAmount.calculation.discountTotal.rawCents)
        assertEquals(1_800L, updatedSameAmount.calculation.finalAmount.rawCents)
        assertEquals(2_200L, updatedSameAmount.calculation.items.single().unitPriceOriginal.rawCents)

        // Agora reduz o desconto para R$ 2,00 (final R$ 20,00) e garante que calcula a partir de R$ 22,00 original
        clock += 10_000L
        val reducedDiscountRecomputed = SaleFinancialEngine.calculate(
            updatedSameAmount.toEditableCalculationInput(overrideFinalAmount = MoneyCents(2_000L)),
        )
        val updatedReduced = repo.updateSale(initial.id, reducedDiscountRecomputed)
        assertEquals(2_200L, updatedReduced.calculation.originalSubtotal.rawCents)
        assertEquals(200L, updatedReduced.calculation.discountTotal.rawCents)
        assertEquals(2_000L, updatedReduced.calculation.finalAmount.rawCents)

        db.close()
    }

    @Test
    fun `15 explicit delete removes only target sale and keeps other sales and catalog intact`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val catalogRepo = RoomCatalogRepository(db.personDao(), db.productDao())
        val machineRepo = RoomMachineRepository(db.machineDao())
        val prefRepo = RoomPreferenceRepository(db.preferenceDao())
        val saleRepo = RoomSaleRepository(db, db.saleDao())

        prefRepo.savePreference("cfg", "ok")
        val p1 = catalogRepo.createPerson("Pessoa 1")
        val prod = catalogRepo.createProduct(
            name = "Produto Fixo",
            price = MoneyCents(3_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(p1.id, MoneyCents(3_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machine = machineRepo.createMachine(
            MachineInputDraft(
                name = "Point",
                debitRateBps = 190,
                creditEnabled = false,
                creditDefaultRateBps = 0,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )

        val sale1 = saleRepo.saveSale(
            SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.DEBIT, machine = machine)),
        )
        val sale2 = saleRepo.saveSale(
            SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 2)), paymentMethod = SalePaymentMethod.CASH)),
        )

        saleRepo.deleteSale(sale1.id)

        assertNull(saleRepo.getSaleById(sale1.id))
        assertNotNull(saleRepo.getSaleById(sale2.id))
        assertEquals(1, db.saleDao().countSales())
        assertEquals(1, db.saleDao().countSaleItems())
        assertEquals(1, db.saleDao().countSaleItemParticipants())
        assertEquals(1, db.saleDao().countSalePersonTotals())

        // Cadastros e preferências seguem preservados
        assertEquals(1, catalogRepo.observePeople().first().size)
        assertEquals(1, catalogRepo.observeAllProducts().first().size)
        assertEquals(1, machineRepo.observeMachines().first().size)
        assertEquals("ok", prefRepo.getPreference("cfg")?.value)

        db.close()
    }

    @Test
    fun `16 simulated failure mid-transaction rolls back save and update atomically with zero partial records`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val prod = productFixture("prod-1", "Item", 5_000L, participants = listOf(split("p1", "P1", 3_000L), split("p2", "P2", 2_000L)))
        val calcInitial = SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH, note = "Estável"))
        val calcEdited = SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 2)), paymentMethod = SalePaymentMethod.PIX, note = "Falhou"))

        val failingRepo = RoomSaleRepository(
            database = db,
            saleDao = db.saleDao(),
            onBeforeInsertChildren = { throw IllegalStateException("Falha simulada durante gravação") },
        )

        // 1) Falha no saveSale: 0 linhas podem sobrar em qualquer das 4 tabelas
        val saveFailure = runCatching { failingRepo.saveSale(calcInitial) }
        assertTrue(saveFailure.exceptionOrNull() is IllegalStateException)
        assertEquals(0, db.saleDao().countSales())
        assertEquals(0, db.saleDao().countSaleItems())
        assertEquals(0, db.saleDao().countSaleItemParticipants())
        assertEquals(0, db.saleDao().countSalePersonTotals())

        // 2) Grava normalmente com repositório saudável, depois falha no updateSale
        val normalRepo = RoomSaleRepository(db, db.saleDao(), clockMillis = { 1_710_000_000_000L })
        val persisted = normalRepo.saveSale(calcInitial)

        val updateFailure = runCatching { failingRepo.updateSale(persisted.id, calcEdited) }
        assertTrue(updateFailure.exceptionOrNull() is IllegalStateException)

        // Deve manter exatamente o estado anterior intacto (rollback completo de cabeçalho + filhos deletados)
        val restored = normalRepo.getSaleById(persisted.id)!!
        assertEquals("Estável", restored.calculation.note)
        assertEquals(5_000L, restored.calculation.finalAmount.rawCents)
        assertEquals(1, db.saleDao().countSales())
        assertEquals(1, db.saleDao().countSaleItems())
        assertEquals(2, db.saleDao().countSaleItemParticipants())
        assertEquals(2, db.saleDao().countSalePersonTotals())

        db.close()
    }

    @Test
    fun `17 persistence validator rejects inconsistent or corrupted calculation snapshots before touching database`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = RoomSaleRepository(db, db.saleDao())
        val prod = productFixture("prod-1", "Item", 5_000L, participants = listOf(split("p1", "P1", 3_000L), split("p2", "P2", 2_000L)))
        val valid = SaleFinancialEngine.calculate(SaleCalculationInput(listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.CASH))

        // Divergência de 1 centavo no líquido total
        val corruptedNet = valid.copy(netTotal = MoneyCents(4_999L))
        assertTrue(runCatching { repo.saveSale(corruptedNet) }.exceptionOrNull() is SalePersistenceException)

        // Divergência nos totais por pessoa
        val corruptedPerson = valid.copy(
            personTotals = listOf(
                valid.personTotals[0].copy(netAmount = MoneyCents(2_999L)),
                valid.personTotals[1],
            ),
        )
        assertTrue(runCatching { repo.saveSale(corruptedPerson) }.exceptionOrNull() is SalePersistenceException)

        // Pagamento em PIX carregando snapshot de maquininha indevido
        val corruptedPixMachine = valid.copy(machineSnapshot = machineFixture().let {
            SaleFinancialEngine.calculate(
                SaleCalculationInput(listOf(SaleItemInput(prod, 1)), paymentMethod = SalePaymentMethod.DEBIT, machine = it),
            ).machineSnapshot
        })
        assertTrue(runCatching { repo.saveSale(corruptedPixMachine) }.exceptionOrNull() is SalePersistenceException)

        // Lista de itens vazia
        val emptyItems = valid.copy(items = emptyList())
        assertTrue(runCatching { repo.saveSale(emptyItems) }.exceptionOrNull() is SalePersistenceException)

        assertEquals(0, db.saleDao().countSales())
        db.close()
    }

    @Test
    fun `18 migration 3 to 4 preserves all v3 tables and enables full v4 sales persistence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "m34.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Cria banco exatamente na v3 com todas as 7 tabelas populadas
        val v3Sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `app_preferences` (`pref_key` TEXT NOT NULL, `pref_value` TEXT NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`pref_key`))")
        v3Sqlite.execSQL("INSERT INTO `app_preferences` VALUES ('k3', 'Preservado V3', 1700000300000)")

        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `people` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `color_index` INTEGER NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        v3Sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_normalized_name` ON `people` (`normalized_name`)")
        v3Sqlite.execSQL("INSERT INTO `people` VALUES ('p-1', 'Joana', 'joana', 0, 1700000300000, 1700000300000)")

        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `price_cents` INTEGER NOT NULL, `active` INTEGER NOT NULL, `fee_responsibility_mode` TEXT NOT NULL, `fee_owner_person_id` TEXT, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`fee_owner_person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        v3Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_products_fee_owner_person_id` ON `products` (`fee_owner_person_id`)")
        v3Sqlite.execSQL("INSERT INTO `products` VALUES ('prod-1', 'Miniatura', 2500, 1, 'INDIVIDUAL', 'p-1', 1700000300000, 1700000300000)")

        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `product_participants` (`product_id` TEXT NOT NULL, `person_id` TEXT NOT NULL, `amount_cents` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`product_id`, `person_id`), FOREIGN KEY(`product_id`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`person_id`) REFERENCES `people`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
        v3Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_product_participants_product_id` ON `product_participants` (`product_id`)")
        v3Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_product_participants_person_id` ON `product_participants` (`person_id`)")
        v3Sqlite.execSQL("INSERT INTO `product_participants` VALUES ('prod-1', 'p-1', 2500, 0)")

        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `machines` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `normalized_name` TEXT NOT NULL, `debit_rate_bps` INTEGER NOT NULL, `credit_enabled` INTEGER NOT NULL, `credit_default_rate_bps` INTEGER NOT NULL, `brand_rates_enabled` INTEGER NOT NULL, `installments_enabled` INTEGER NOT NULL, `created_at_epoch_ms` INTEGER NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        v3Sqlite.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_machines_normalized_name` ON `machines` (`normalized_name`)")
        v3Sqlite.execSQL("INSERT INTO `machines` VALUES ('m-1', 'Ton V3', 'ton v3', 190, 1, 280, 1, 1, 1700000300000, 1700000300000)")

        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `machine_brand_rates` (`machine_id` TEXT NOT NULL, `brand_key` TEXT NOT NULL, `rate_bps` INTEGER NOT NULL, PRIMARY KEY(`machine_id`, `brand_key`), FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        v3Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_brand_rates_machine_id` ON `machine_brand_rates` (`machine_id`)")
        v3Sqlite.execSQL("INSERT INTO `machine_brand_rates` VALUES ('m-1', 'VISA', 250)")

        v3Sqlite.execSQL("CREATE TABLE IF NOT EXISTS `machine_installment_rates` (`machine_id` TEXT NOT NULL, `installments` INTEGER NOT NULL, `addon_bps` INTEGER NOT NULL, PRIMARY KEY(`machine_id`, `installments`), FOREIGN KEY(`machine_id`) REFERENCES `machines`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        v3Sqlite.execSQL("CREATE INDEX IF NOT EXISTS `index_machine_installment_rates_machine_id` ON `machine_installment_rates` (`machine_id`)")
        v3Sqlite.execSQL("INSERT INTO `machine_installment_rates` VALUES ('m-1', 3, 150)")

        v3Sqlite.version = 3
        v3Sqlite.close()

        val dbV4 = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        // Confirma preservação integral dos registros v3
        assertEquals("Preservado V3", RoomPreferenceRepository(dbV4.preferenceDao()).getPreference("k3")?.value)
        val catalogRepo = RoomCatalogRepository(dbV4.personDao(), dbV4.productDao())
        val prod = catalogRepo.observeAllProducts().first().single()
        assertEquals("Miniatura", prod.name)
        val machine = RoomMachineRepository(dbV4.machineDao()).observeMachines().first().single()
        assertEquals("Ton V3", machine.name)
        assertEquals(250, machine.brandRatesBps[CardBrand.VISA])
        assertEquals(150, machine.installmentAddonsBps[3])

        // Grava venda usando produto e maquininha migrados
        val saleRepo = RoomSaleRepository(dbV4, dbV4.saleDao())
        val saved = saleRepo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 2)),
                    paymentMethod = SalePaymentMethod.CREDIT,
                    machine = machine,
                    cardBrand = CardBrand.VISA,
                    installments = 3,
                ),
            ),
        )
        assertEquals(5_000L, saved.calculation.finalAmount.rawCents)
        assertEquals(400, saved.calculation.effectiveFeeRateBps)
        assertEquals(200L, saved.calculation.feeTotal.rawCents)
        assertEquals(4_800L, saved.calculation.netTotal.rawCents)

        dbV4.close()
        assertTrue(context.deleteDatabase(dbName))
    }

    @Test
    fun `19 full migration path 1 to 2 to 3 to 4 upgrades cleanly preserving prior preferences`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "m14.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val v1Sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        v1Sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_preferences` (`pref_key` TEXT NOT NULL, `pref_value` TEXT NOT NULL, `updated_at_epoch_ms` INTEGER NOT NULL, PRIMARY KEY(`pref_key`))",
        )
        v1Sqlite.execSQL(
            "INSERT INTO `app_preferences` VALUES ('etapa1', 'Intacto Ate V4', 1700000000001)",
        )
        v1Sqlite.version = 1
        v1Sqlite.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()

        val pref = RoomPreferenceRepository(migratedDb.preferenceDao()).getPreference("etapa1")
        assertNotNull(pref)
        assertEquals("Intacto Ate V4", pref?.value)

        val catalogRepo = RoomCatalogRepository(migratedDb.personDao(), migratedDb.productDao())
        val p = catalogRepo.createPerson("Sócio V4")
        val prod = catalogRepo.createProduct(
            name = "Produto V4",
            price = MoneyCents(3_000L),
            active = true,
            participants = listOf(ProductParticipantDraft(p.id, MoneyCents(3_000L))),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val machineRepo = RoomMachineRepository(migratedDb.machineDao())
        val mach = machineRepo.createMachine(
            MachineInputDraft(
                name = "PagBank V4",
                debitRateBps = 199,
                creditEnabled = true,
                creditDefaultRateBps = 319,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = true,
                installmentAddonsBps = InstallmentTiers.SUPPORTED.associateWith { 0 },
            ),
        )
        val saleRepo = RoomSaleRepository(migratedDb, migratedDb.saleDao())
        val saved = saleRepo.saveSale(
            SaleFinancialEngine.calculate(
                SaleCalculationInput(
                    items = listOf(SaleItemInput(prod, 1)),
                    paymentMethod = SalePaymentMethod.DEBIT,
                    machine = mach,
                ),
            ),
        )
        assertEquals(3_000L, saved.calculation.finalAmount.rawCents)
        assertEquals(60L, saved.calculation.feeTotal.rawCents)
        assertEquals(2_940L, saved.calculation.netTotal.rawCents)

        migratedDb.close()
        assertTrue(context.deleteDatabase(dbName))
    }
}
