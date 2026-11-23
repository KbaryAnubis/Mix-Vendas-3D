package com.mixvendas3d.app.domain.export

import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gerador puro e determinístico dos arquivos CSV (Resumo e Detalhado) a partir dos snapshots
 * financeiros imutáveis persistidos no Room ([PersistedSale]).
 *
 * Regras (V19 - Etapa 5A):
 * - Totalmente independente de interface Compose, armazenamento Android (SAF) e cadastros atuais.
 * - Nunca recalcula valores históricos com preços, pessoas ou taxas atuais.
 * - Utiliza exclusivamente inteiros em centavos ([Long]) e pontos-base ([Int]), sem Float/Double.
 * - Gera CSV compatível com Excel brasileiro: UTF-8 com BOM, separador `;`, vírgula decimal,
 *   datas `dd/MM/yyyy`, horas `HH:mm:ss`, quebra de linha `\r\n` e proteção contra formula injection.
 */
object CsvSaleExporter {

    private const val UTF8_BOM: String = "\uFEFF"
    private const val SEPARATOR: Char = ';'
    private const val CRLF: String = "\r\n"

    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBrLocale)
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", ptBrLocale)
    private val fileDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd", ptBrLocale)

    private val saleOrderComparator: Comparator<PersistedSale> =
        compareBy<PersistedSale> { it.soldAtEpochMs }.thenBy { it.id }

    fun export(
        sales: Collection<PersistedSale>,
        period: CsvExportPeriod = CsvExportPeriod(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        generatedOnDate: LocalDate = LocalDate.now(zoneId),
    ): CsvExportResult {
        val seenSaleIds = HashSet<String>(sales.size)
        val filteredSales = ArrayList<PersistedSale>(sales.size)

        sales.forEach { sale ->
            if (sale.id.isBlank()) {
                throw CsvExportValidationException(
                    saleId = sale.id,
                    message = "Venda sem identificador não pode ser exportada para CSV.",
                )
            }
            if (!seenSaleIds.add(sale.id)) {
                throw CsvExportValidationException(
                    saleId = sale.id,
                    message = "ID de venda duplicado detectado ao gerar CSV: ${sale.id}.",
                )
            }

            val saleDate = DailySalesAggregator.toLocalDate(sale.soldAtEpochMs, zoneId)
            val matchesFrom = period.fromDate == null || !saleDate.isBefore(period.fromDate)
            val matchesTo = period.toDate == null || !saleDate.isAfter(period.toDate)
            if (matchesFrom && matchesTo) {
                validateSaleForExport(sale)
                filteredSales.add(sale)
            }
        }

        val sortedSales = filteredSales.sortedWith(saleOrderComparator)
        val summaryCsv = buildSummaryCsv(sortedSales, zoneId)
        val (detailedCsv, detailedRowsCount) = buildDetailedCsv(sortedSales, zoneId)

        val dateSuffix = generatedOnDate.format(fileDateFormatter)
        return CsvExportResult(
            summaryCsvContent = summaryCsv,
            detailedCsvContent = detailedCsv,
            exportedSalesCount = sortedSales.size,
            detailedRowsCount = detailedRowsCount,
            suggestedSummaryFileName = "mix_vendas_3d_resumo_$dateSuffix.csv",
            suggestedDetailedFileName = "mix_vendas_3d_detalhado_$dateSuffix.csv",
        )
    }

    private fun validateSaleForExport(sale: PersistedSale) {
        if (sale.soldAtEpochMs < 0L || sale.updatedAtEpochMs < 0L) {
            throw CsvExportValidationException(
                saleId = sale.id,
                message = "Data/hora inválida na venda ${sale.id}.",
            )
        }
        try {
            SalePersistenceValidator.validateForPersistence(sale.calculation)
        } catch (ex: SalePersistenceException) {
            throw CsvExportValidationException(
                saleId = sale.id,
                message = "Snapshot financeiro inconsistente na venda ${sale.id}: ${ex.message}",
            )
        } catch (ex: RuntimeException) {
            throw CsvExportValidationException(
                saleId = sale.id,
                message = "Falha ao validar venda ${sale.id} para exportação CSV: ${ex.message}",
            )
        }
    }

    private fun buildSummaryCsv(
        sales: List<PersistedSale>,
        zoneId: ZoneId,
    ): String {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        appendCsvRow(sb, CsvExportColumns.SUMMARY_HEADERS, escapeFormulas = false)

        sales.forEach { sale ->
            val calc = sale.calculation
            val soldZoned = Instant.ofEpochMilli(sale.soldAtEpochMs).atZone(zoneId)
            val updatedZoned = Instant.ofEpochMilli(sale.updatedAtEpochMs).atZone(zoneId)
            val totalUnits = safeTotalQuantity(sale)
            val machine = calc.machineSnapshot

            val row = listOf(
                sanitizeText(sale.id),
                soldZoned.format(dateFormatter),
                soldZoned.format(timeFormatter),
                updatedZoned.format(dateFormatter),
                totalUnits.toString(),
                formatMoney(calc.originalSubtotal),
                formatMoney(calc.discountTotal),
                formatMoney(calc.finalAmount),
                sanitizeText(calc.paymentMethod.label),
                sanitizeText(machine?.machineName.orEmpty()),
                sanitizeText(machine?.appliedBrandDisplayLabel.orEmpty()),
                sanitizeText(machine?.appliedInstallments?.let { "${it}x" }.orEmpty()),
                machine?.manualRateOverrideBps?.let(RateFormatter::formatBpsAsPercent).orEmpty(),
                formatMoney(calc.feeTotal),
                formatMoney(calc.netTotal),
                sanitizeText(calc.note),
            )
            appendCsvRow(sb, row, escapeFormulas = false)
        }

        return sb.toString()
    }

    private fun buildDetailedCsv(
        sales: List<PersistedSale>,
        zoneId: ZoneId,
    ): Pair<String, Int> {
        val sb = StringBuilder()
        sb.append(UTF8_BOM)
        appendCsvRow(sb, CsvExportColumns.DETAILED_HEADERS, escapeFormulas = false)

        var rowCount = 0

        sales.forEach { sale ->
            val calc = sale.calculation
            val soldZoned = Instant.ofEpochMilli(sale.soldAtEpochMs).atZone(zoneId)
            val saleDateStr = soldZoned.format(dateFormatter)
            val saleTimeStr = soldZoned.format(timeFormatter)
            val machine = calc.machineSnapshot

            val (discountModeLabel, discountTargetId, discountTargetName) = when (val cfg = calc.discountConfig) {
                is SaleDiscountConfigSnapshot.Proportional -> Triple("Proporcional", "", "")
                is SaleDiscountConfigSnapshot.SpecificProduct -> Triple(
                    "Produto específico",
                    cfg.productId,
                    cfg.productNameSnapshot,
                )
                is SaleDiscountConfigSnapshot.SelectedProducts -> Triple(
                    "Produtos selecionados",
                    cfg.productIds.joinToString(","),
                    cfg.productNameSnapshots.joinToString(", "),
                )
            }

            calc.items.forEachIndexed { itemIndex, item ->
                val deterministicItemId = "${sale.id}#${itemIndex + 1}"
                val feeOwnerName = if (item.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL) {
                    item.feeOwnerPersonName.orEmpty()
                } else {
                    ""
                }

                item.participantSplits.forEach { split ->
                    val row = listOf(
                        sanitizeText(sale.id),
                        saleDateStr,
                        saleTimeStr,
                        sanitizeText(deterministicItemId),
                        sanitizeText(item.productId),
                        sanitizeText(item.productName),
                        item.quantity.toString(),
                        formatMoney(item.unitPriceOriginal),
                        formatMoney(item.originalSubtotal),
                        formatMoney(item.discountAmount),
                        formatMoney(item.finalAmount),
                        sanitizeText(split.personId),
                        sanitizeText(split.personName),
                        formatMoney(split.grossAmount),
                        formatMoney(split.feeAmount),
                        formatMoney(split.netAmount),
                        sanitizeText(calc.paymentMethod.label),
                        sanitizeText(machine?.machineName.orEmpty()),
                        sanitizeText(machine?.appliedBrandDisplayLabel.orEmpty()),
                        sanitizeText(machine?.appliedInstallments?.let { "${it}x" }.orEmpty()),
                        RateFormatter.formatBpsAsPercent(item.appliedFeeRateBps),
                        formatMoney(item.feeAmount),
                        sanitizeText(item.feeResponsibilityMode.label),
                        sanitizeText(feeOwnerName),
                        sanitizeText(discountModeLabel),
                        sanitizeText(discountTargetId),
                        sanitizeText(discountTargetName),
                    )
                    appendCsvRow(sb, row, escapeFormulas = false)
                    rowCount += 1
                }
            }
        }

        return sb.toString() to rowCount
    }

    private fun safeTotalQuantity(sale: PersistedSale): Long {
        var total = 0L
        sale.calculation.items.forEach { item ->
            total = MonetaryRounding.safeAdd(total, item.quantity.toLong())
        }
        return total
    }

    private fun formatMoney(amount: MoneyCents): String {
        return CurrencyFormatter.formatCentsOrZeroForInput(amount.rawCents)
    }

    /**
     * Protege campos textuais contra CSV/Spreadsheet Formula Injection (=, +, -, @, TAB, CR),
     * inclusive quando antecedidos por espaços em branco.
     */
    internal fun sanitizeText(raw: String): String {
        if (raw.isEmpty()) return ""
        val firstChar = raw[0]
        val trimmedFirst = raw.trimStart().firstOrNull()
        val dangerous = firstChar == '=' ||
            firstChar == '+' ||
            firstChar == '-' ||
            firstChar == '@' ||
            firstChar == '\t' ||
            firstChar == '\r' ||
            trimmedFirst == '=' ||
            trimmedFirst == '+' ||
            trimmedFirst == '-' ||
            trimmedFirst == '@'
        return if (dangerous) "'$raw" else raw
    }

    private fun appendCsvRow(
        sb: StringBuilder,
        cells: List<String>,
        escapeFormulas: Boolean,
    ) {
        cells.forEachIndexed { index, rawCell ->
            if (index > 0) sb.append(SEPARATOR)
            val value = if (escapeFormulas) sanitizeText(rawCell) else rawCell
            sb.append(escapeCsvCell(value))
        }
        sb.append(CRLF)
    }

    internal fun escapeCsvCell(value: String): String {
        if (value.isEmpty()) return ""
        val requiresQuotes = value.any { ch ->
            ch == SEPARATOR || ch == '"' || ch == '\r' || ch == '\n'
        } || value.first().isWhitespace() || value.last().isWhitespace()

        if (!requiresQuotes) return value

        val escaped = StringBuilder(value.length + 4)
        escaped.append('"')
        value.forEach { ch ->
            if (ch == '"') {
                escaped.append("\"\"")
            } else {
                escaped.append(ch)
            }
        }
        escaped.append('"')
        return escaped.toString()
    }
}
