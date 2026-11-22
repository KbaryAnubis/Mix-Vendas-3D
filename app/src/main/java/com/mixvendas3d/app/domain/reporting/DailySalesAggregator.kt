package com.mixvendas3d.app.domain.reporting

import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

data class RealDaySalesSummary(
    val date: LocalDate,
    val salesCount: Int,
    val totalSold: MoneyCents,
)

data class RealMonthSalesSummary(
    val yearMonth: YearMonth,
    val title: String,
    val salesCount: Int,
    val totalSold: MoneyCents,
    val days: List<RealDaySalesSummary>,
)

data class RealDailySalesReport(
    val totalSold: MoneyCents = MoneyCents.ZERO,
    val totalSalesCount: Int = 0,
    val totalDaysCount: Int = 0,
    val months: List<RealMonthSalesSummary> = emptyList(),
)

/**
 * Motor puro de agrupamento diário e mensal de vendas reais persistidas no Room.
 *
 * Regras aplicadas (V19 - Etapa 4A):
 * - Converte [PersistedSale.soldAtEpochMs] para a data local do fuso informado ([ZoneId]).
 * - Soma exclusivamente [PersistedSale.calculation.finalAmount] (valor efetivamente cobrado após
 *   descontos e antes de tarifas de maquininha), utilizando centavos inteiros [Long].
 * - Nunca recalcula vendas históricas usando cadastros atuais.
 * - Meses e dias são ordenados em ordem decrescente (mais recentes primeiro).
 * - Dias e meses sem vendas não são incluídos.
 */
object DailySalesAggregator {

    fun toLocalDate(
        soldAtEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LocalDate {
        return Instant.ofEpochMilli(soldAtEpochMs)
            .atZone(zoneId)
            .toLocalDate()
    }

    /**
     * Combina a data local escolhida ([saleDate]) com o horário local de [referenceEpochMs]
     * no fuso horário [zoneId]. Se [saleDate] já coincidir com a data local de [referenceEpochMs],
     * devolve [referenceEpochMs] intacto para preservar exatamente o instante original.
     */
    fun combineLocalDateWithTimeOf(
        saleDate: LocalDate,
        referenceEpochMs: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val referenceZoned = Instant.ofEpochMilli(referenceEpochMs).atZone(zoneId)
        if (referenceZoned.toLocalDate() == saleDate) {
            return referenceEpochMs
        }
        return saleDate
            .atTime(referenceZoned.toLocalTime())
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    /**
     * Converte o timestamp UTC de meia-noite devolvido pelo DatePicker do Material 3
     * para a [LocalDate] exata, evitando deslocamento por fuso horário local.
     */
    fun utcDatePickerMillisToLocalDate(utcMidnightMillis: Long): LocalDate {
        return Instant.ofEpochMilli(utcMidnightMillis)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
    }

    /**
     * Converte uma [LocalDate] para o timestamp UTC de meia-noite esperado pelo
     * DatePicker do Material 3.
     */
    fun localDateToUtcDatePickerMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * Retorna o intervalo semiaberto `[startInclusiveEpochMs, endExclusiveEpochMs)` para uma data
     * local, usando `atStartOfDay(zoneId)` e `plusDays(1).atStartOfDay(zoneId)` para lidar
     * corretamente com viradas de dia, fuso horário e horário de verão.
     */
    fun localDayEpochRange(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Pair<Long, Long> {
        val startInclusive = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endExclusive = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return startInclusive to endExclusive
    }

    fun formatMonthHeader(yearMonth: YearMonth): String {
        val monthName = when (yearMonth.month) {
            Month.JANUARY -> "JANEIRO"
            Month.FEBRUARY -> "FEVEREIRO"
            Month.MARCH -> "MARÇO"
            Month.APRIL -> "ABRIL"
            Month.MAY -> "MAIO"
            Month.JUNE -> "JUNHO"
            Month.JULY -> "JULHO"
            Month.AUGUST -> "AGOSTO"
            Month.SEPTEMBER -> "SETEMBRO"
            Month.OCTOBER -> "OUTUBRO"
            Month.NOVEMBER -> "NOVEMBRO"
            Month.DECEMBER -> "DEZEMBRO"
        }
        return "$monthName ${yearMonth.year}"
    }

    fun aggregate(
        sales: List<PersistedSale>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RealDailySalesReport {
        if (sales.isEmpty()) {
            return RealDailySalesReport()
        }

        val byDate = linkedMapOf<LocalDate, MutableDayAcc>()
        sales.forEach { sale ->
            val localDate = toLocalDate(sale.soldAtEpochMs, zoneId)
            val acc = byDate.getOrPut(localDate) { MutableDayAcc() }
            acc.salesCount += 1
            acc.totalSoldCents = MonetaryRounding.safeAdd(
                acc.totalSoldCents,
                sale.calculation.finalAmount.rawCents,
            )
        }

        val sortedDays = byDate.entries
            .sortedByDescending { it.key }
            .map { (date, acc) ->
                RealDaySalesSummary(
                    date = date,
                    salesCount = acc.salesCount,
                    totalSold = MoneyCents(acc.totalSoldCents),
                )
            }

        val months = sortedDays
            .groupBy { YearMonth.from(it.date) }
            .entries
            .sortedByDescending { it.key }
            .map { (yearMonth, monthDays) ->
                var monthTotalCents = 0L
                var monthSalesCount = 0
                monthDays.forEach { day ->
                    monthTotalCents = MonetaryRounding.safeAdd(monthTotalCents, day.totalSold.rawCents)
                    monthSalesCount += day.salesCount
                }
                RealMonthSalesSummary(
                    yearMonth = yearMonth,
                    title = formatMonthHeader(yearMonth),
                    salesCount = monthSalesCount,
                    totalSold = MoneyCents(monthTotalCents),
                    days = monthDays,
                )
            }

        var grandTotalCents = 0L
        var grandSalesCount = 0
        var grandDaysCount = 0
        months.forEach { month ->
            grandTotalCents = MonetaryRounding.safeAdd(grandTotalCents, month.totalSold.rawCents)
            grandSalesCount += month.salesCount
            grandDaysCount += month.days.size
        }

        return RealDailySalesReport(
            totalSold = MoneyCents(grandTotalCents),
            totalSalesCount = grandSalesCount,
            totalDaysCount = grandDaysCount,
            months = months,
        )
    }

    private class MutableDayAcc(
        var salesCount: Int = 0,
        var totalSoldCents: Long = 0L,
    )
}
