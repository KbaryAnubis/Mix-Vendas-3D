package com.mixvendas3d.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.RoomCatalogRepository
import com.mixvendas3d.app.data.repository.RoomMachineRepository
import com.mixvendas3d.app.data.repository.RoomSaleRepository
import com.mixvendas3d.app.domain.calculation.MachineInputDraft
import com.mixvendas3d.app.domain.calculation.ProductParticipantDraft
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.CsvSaleExporter
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Stage5ACsvExportRoomSnapshotTest {

    private lateinit var database: AppDatabase
    private lateinit var catalogRepository: RoomCatalogRepository
    private lateinit var machineRepository: RoomMachineRepository
    private lateinit var saleRepository: RoomSaleRepository

    private var fakeClockMs: Long = 1_794_840_000_000L
    private var seqCounter: Int = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<MixVendasApplication>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val idGen = {
            seqCounter += 1
            "id-$seqCounter"
        }
        val clock = { fakeClockMs }

        catalogRepository = RoomCatalogRepository(
            personDao = database.personDao(),
            productDao = database.productDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )
        machineRepository = RoomMachineRepository(
            machineDao = database.machineDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )
        saleRepository = RoomSaleRepository(
            database = database,
            saleDao = database.saleDao(),
            idGenerator = idGen,
            clockMillis = clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `room v4 persisted sale preserves historical product, person and machine snapshot in CSV after catalog mutations`() = runBlocking {
        val zone = ZoneId.of("America/Sao_Paulo")
        fakeClockMs = LocalDateTime.of(2026, 11, 12, 15, 30, 45).atZone(zone).toInstant().toEpochMilli()

        val ana = catalogRepository.createPerson("Ana Histórica")
        val bruno = catalogRepository.createPerson("Bruno Antigo")
        val produto = catalogRepository.createProduct(
            name = "Vaso Espiral v1",
            price = MoneyCents(10_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(6_000L)),
                ProductParticipantDraft(bruno.id, MoneyCents(4_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
            feeOwnerPersonId = null,
        )
        val maquininha = machineRepository.createMachine(
            MachineInputDraft(
                name = "PagSeguro Antiga",
                debitRateBps = 200,
                creditEnabled = true,
                creditDefaultRateBps = 500,
                brandRatesEnabled = true,
                brandRatesBps = mapOf(CardBrand.VISA to 450),
                installmentsEnabled = true,
                installmentAddonsBps = mapOf(1 to 0, 2 to 150), // 6,00%
            ),
        )

        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(produto, 1)),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = SalePaymentMethod.CREDIT,
                machine = maquininha,
                cardBrand = CardBrand.VISA,
                installments = 2,
                note = "Snapshot original",
            ),
        )
        val persisted = saleRepository.saveSale(calc)

        // Alterações posteriores no catálogo e na maquininha NÃO podem afetar o CSV histórico
        catalogRepository.renamePerson(ana.id, "Ana Atualizada")
        catalogRepository.updateProduct(
            productId = produto.id,
            name = "Vaso Espiral v2 CARO",
            price = MoneyCents(25_000L),
            active = true,
            participants = listOf(
                ProductParticipantDraft(ana.id, MoneyCents(15_000L)),
                ProductParticipantDraft(bruno.id, MoneyCents(10_000L)),
            ),
            feeResponsibilityMode = FeeResponsibilityMode.EQUAL,
            feeOwnerPersonId = null,
        )
        machineRepository.updateMachine(
            machineId = maquininha.id,
            draft = MachineInputDraft(
                name = "Maquininha Nova",
                debitRateBps = 999,
                creditEnabled = true,
                creditDefaultRateBps = 999,
                brandRatesEnabled = false,
                brandRatesBps = emptyMap(),
                installmentsEnabled = false,
                installmentAddonsBps = emptyMap(),
            ),
        )

        val reloadedSales = saleRepository.observeSales().first()
        assertEquals(1, reloadedSales.size)
        assertEquals(persisted.id, reloadedSales.single().id)

        val csvResult = CsvSaleExporter.export(reloadedSales, zoneId = zone)
        assertEquals(1, csvResult.exportedSalesCount)
        assertEquals(2, csvResult.detailedRowsCount)

        // Verifica preservação integral do histórico sem recálculo
        assertTrue(csvResult.summaryCsvContent.contains(";100,00;0,00;100,00;Crédito;PagSeguro Antiga;Visa;2x;;6,00;94,00;Snapshot original\r\n"))
        assertTrue(csvResult.detailedCsvContent.contains(";Vaso Espiral v1;1;100,00;100,00;0,00;100,00;${ana.id};Ana Histórica;60,00;3,60;56,40;Crédito;PagSeguro Antiga;Visa;2x;6,00%;6,00;Divisão proporcional;"))
        assertTrue(csvResult.detailedCsvContent.contains(";Vaso Espiral v1;1;100,00;100,00;0,00;100,00;${bruno.id};Bruno Antigo;40,00;2,40;37,60;Crédito;PagSeguro Antiga;Visa;2x;6,00%;6,00;Divisão proporcional;"))
    }
}
