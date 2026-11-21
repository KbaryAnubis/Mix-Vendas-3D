package com.mixvendas3d.app

import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage4ADailySalesAggregatorTest {

    private val sampleProduct = Product(
        id = "prod-1",
        name = "Miniatura 3D",
        price = MoneyCents(10_000L), // R$ 100,00
        active = true,
        feeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
        feeOwnerPersonId = null,
        participants = listOf(
            ProductParticipantSplit(
                personId = "p-1",
                personName = "Ana",
                personColorIndex = 0,
                amount = MoneyCents(6_000L),
            ),
            ProductParticipantSplit(
                personId = "p-2",
                personName = "Bruno",
                personColorIndex = 1,
                amount = MoneyCents(4_000L),
            ),
        ),
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private val sampleMachine = Machine(
        id = "mach-1",
        name = "Ton Ultra",
        debitRateBps = 200,
        creditEnabled = true,
        creditDefaultRateBps = 500, // 5,00%
        brandRatesEnabled = true,
        brandRatesBps = mapOf(CardBrand.VISA to 500),
        installmentsEnabled = false,
        installmentAddonsBps = emptyMap(),
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun buildPersistedSale(
        id: String,
        soldAtEpochMs: Long,
        quantity: Int = 1,
        finalOverrideCents: Long? = null,
        paymentMethod: SalePaymentMethod = SalePaymentMethod.PIX,
    ): PersistedSale {
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = listOf(SaleItemInput(sampleProduct, quantity)),
                finalAmountOverride = finalOverrideCents?.let(::MoneyCents),
                discountMode = SaleDiscountInput.Proportional,
                paymentMethod = paymentMethod,
                machine = if (paymentMethod.usesMachine) sampleMachine else null,
                cardBrand = if (paymentMethod == SalePaymentMethod.CREDIT) CardBrand.VISA else null,
                installments = if (paymentMethod == SalePaymentMethod.CREDIT) 1 else null,
            ),
        )
        return PersistedSale(
            id = id,
            soldAtEpochMs = soldAtEpochMs,
            updatedAtEpochMs = soldAtEpochMs,
            calculation = calc,
        )
    }

    @Test
    fun `empty sales list returns zero totals and no months or days`() {
        val report = DailySalesAggregator.aggregate(emptyList(), ZoneId.of("America/Sao_Paulo"))
        assertEquals(0L, report.totalSold.rawCents)
        assertEquals(0, report.totalSalesCount)
        assertEquals(0, report.totalDaysCount)
        assertTrue(report.months.isEmpty())
    }

    @Test
    fun `single day, multiple sales same day, discounts, and fees respect finalAmount charged`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val day = LocalDate.of(2026, 11, 16)
        val t1 = day.atTime(9, 15).atZone(zone).toInstant().toEpochMilli()
        val t2 = day.atTime(14, 40).atZone(zone).toInstant().toEpochMilli()
        val t3 = day.atTime(20, 5).atZone(zone).toInstant().toEpochMilli()

        // Venda 1: 1x 100,00 sem desconto no Pix -> final 100,00
        val s1 = buildPersistedSale("s1", t1, quantity = 1, paymentMethod = SalePaymentMethod.PIX)
        // Venda 2: 2x (subtotal 200,00) com desconto para 150,00 no Crédito 5% (taxa 7,50, líquido 142,50) -> total vendido deve usar 150,00!
        val s2 = buildPersistedSale("s2", t2, quantity = 2, finalOverrideCents = 15_000L, paymentMethod = SalePaymentMethod.CREDIT)
        // Venda 3: 1x (subtotal 100,00) com desconto para 80,00 no Débito 2% (taxa 1,60, líquido 78,40) -> total vendido deve usar 80,00!
        val s3 = buildPersistedSale("s3", t3, quantity = 1, finalOverrideCents = 8_000L, paymentMethod = SalePaymentMethod.DEBIT)

        val singleReport = DailySalesAggregator.aggregate(listOf(s1), zone)
        assertEquals(10_000L, singleReport.totalSold.rawCents)
        assertEquals(1, singleReport.totalSalesCount)
        assertEquals(1, singleReport.totalDaysCount)

        val report = DailySalesAggregator.aggregate(listOf(s1, s2, s3), zone)
        // 100,00 + 150,00 + 80,00 = 330,00 (não 400,00 subtotal original e não 320,90 líquido pós-taxa)
        assertEquals(33_000L, report.totalSold.rawCents)
        assertEquals(3, report.totalSalesCount)
        assertEquals(1, report.totalDaysCount)
        assertEquals(1, report.months.size)

        val month = report.months.single()
        assertEquals(YearMonth.of(2026, 11), month.yearMonth)
        assertEquals("NOVEMBRO 2026", month.title)
        assertEquals(33_000L, month.totalSold.rawCents)
        assertEquals(3, month.salesCount)
        assertEquals(1, month.days.size)
        assertEquals(day, month.days.single().date)
        assertEquals(3, month.days.single().salesCount)
        assertEquals(33_000L, month.days.single().totalSold.rawCents)
    }

    @Test
    fun `months and days across years sort descending with exact hierarchical sums and omit empty days`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val sales = listOf(
            // 2025-12-31 -> R$ 100,00
            buildPersistedSale("y1", LocalDateTime.of(2025, 12, 31, 23, 50).atZone(zone).toInstant().toEpochMilli()),
            // 2026-10-28 -> R$ 120,00 (com desconto sobre 200)
            buildPersistedSale("m1a", LocalDateTime.of(2026, 10, 28, 10, 0).atZone(zone).toInstant().toEpochMilli(), quantity = 2, finalOverrideCents = 12_000L),
            // 2026-10-30 -> R$ 100,00
            buildPersistedSale("m1b", LocalDateTime.of(2026, 10, 30, 15, 0).atZone(zone).toInstant().toEpochMilli()),
            // 2026-11-14 -> R$ 90,00
            buildPersistedSale("m2a", LocalDateTime.of(2026, 11, 14, 11, 0).atZone(zone).toInstant().toEpochMilli(), finalOverrideCents = 9_000L),
            // 2026-11-16 -> R$ 100,00 + R$ 50,00 = R$ 150,00
            buildPersistedSale("m2b", LocalDateTime.of(2026, 11, 16, 8, 0).atZone(zone).toInstant().toEpochMilli()),
            buildPersistedSale("m2c", LocalDateTime.of(2026, 11, 16, 19, 0).atZone(zone).toInstant().toEpochMilli(), finalOverrideCents = 5_000L),
        )

        val report = DailySalesAggregator.aggregate(sales, zone)
        assertEquals(listOf(YearMonth.of(2026, 11), YearMonth.of(2026, 10), YearMonth.of(2025, 12)), report.months.map { it.yearMonth })
        assertEquals(listOf("NOVEMBRO 2026", "OUTUBRO 2026", "DEZEMBRO 2025"), report.months.map { it.title })

        val nov = report.months[0]
        assertEquals(listOf(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 14)), nov.days.map { it.date })
        assertEquals(24_000L, nov.totalSold.rawCents)
        assertEquals(nov.days.sumOf { it.totalSold.rawCents }, nov.totalSold.rawCents)

        val oct = report.months[1]
        assertEquals(listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 28)), oct.days.map { it.date })
        assertEquals(22_000L, oct.totalSold.rawCents)
        assertEquals(oct.days.sumOf { it.totalSold.rawCents }, oct.totalSold.rawCents)

        val dec25 = report.months[2]
        assertEquals(listOf(LocalDate.of(2025, 12, 31)), dec25.days.map { it.date })
        assertEquals(10_000L, dec25.totalSold.rawCents)

        assertEquals(report.months.sumOf { it.totalSold.rawCents }, report.totalSold.rawCents)
        assertEquals(56_000L, report.totalSold.rawCents)
        assertEquals(6, report.totalSalesCount)
        assertEquals(5, report.totalDaysCount)
    }

    @Test
    fun `utc timestamps near midnight map to local date and half-open interval respects timezone and dst`() {
        val spZone = ZoneId.of("America/Sao_Paulo") // UTC-3
        // 2026-12-01 01:30:00Z é 2026-11-30 22:30:00-03:00 (ainda novembro em São Paulo!)
        val utcDec1 = ZonedDateTime.of(2026, 12, 1, 1, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        // 2027-01-01 02:59:59.999Z é 2026-12-31 23:59:59.999-03:00 (virada de ano ainda em 2026 local!)
        val utcJan1 = ZonedDateTime.of(2027, 1, 1, 2, 59, 59, 999_000_000, ZoneOffset.UTC).toInstant().toEpochMilli()
        // 2027-01-01 03:00:00.000Z é 2027-01-01 00:00:00.000-03:00 (já 2027 local!)
        val utcJan1AfterMidnightSp = ZonedDateTime.of(2027, 1, 1, 3, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        val sales = listOf(
            buildPersistedSale("s1", utcDec1, finalOverrideCents = 4_000L),
            buildPersistedSale("s2", utcJan1, finalOverrideCents = 6_000L),
            buildPersistedSale("s3", utcJan1AfterMidnightSp, finalOverrideCents = 7_000L),
        )

        val spReport = DailySalesAggregator.aggregate(sales, spZone)
        assertEquals(
            listOf(YearMonth.of(2027, 1), YearMonth.of(2026, 12), YearMonth.of(2026, 11)),
            spReport.months.map { it.yearMonth },
        )
        assertEquals(LocalDate.of(2026, 11, 30), spReport.months[2].days.single().date)
        assertEquals(LocalDate.of(2026, 12, 31), spReport.months[1].days.single().date)
        assertEquals(LocalDate.of(2027, 1, 1), spReport.months[0].days.single().date)

        // Intervalo semiaberto de 31/12/2026 em SP inclui 23:59:59.999 e exclui 00:00:00.000 de 01/01/2027
        val (startDec31, endJan1) = DailySalesAggregator.localDayEpochRange(LocalDate.of(2026, 12, 31), spZone)
        assertTrue(utcJan1 in startDec31 until endJan1)
        assertTrue(utcJan1AfterMidnightSp !in startDec31 until endJan1)

        // Em fuso horário com DST (America/New_York em 01/11/2026 tem 25 horas!)
        val nyZone = ZoneId.of("America/New_York")
        val (nyStart, nyEnd) = DailySalesAggregator.localDayEpochRange(LocalDate.of(2026, 11, 1), nyZone)
        assertEquals(25 * 3600 * 1000L, nyEnd - nyStart)
    }

    @Test
    fun `leap year february 29 groups correctly under FEVEREIRO 2024`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val leapInstant = LocalDateTime.of(2024, 2, 29, 21, 0).atZone(zone).toInstant().toEpochMilli()
        val march1Instant = LocalDateTime.of(2024, 3, 1, 0, 5).atZone(zone).toInstant().toEpochMilli()

        val report = DailySalesAggregator.aggregate(
            listOf(
                buildPersistedSale("leap", leapInstant, finalOverrideCents = 8_500L),
                buildPersistedSale("mar", march1Instant, finalOverrideCents = 3_500L),
            ),
            zone,
        )

        assertEquals(listOf("MARÇO 2024", "FEVEREIRO 2024"), report.months.map { it.title })
        assertEquals(LocalDate.of(2024, 2, 29), report.months[1].days.single().date)
        assertEquals(8_500L, report.months[1].totalSold.rawCents)
        assertEquals(12_000L, report.totalSold.rawCents)
        assertEquals(2, report.totalDaysCount)
    }
}
