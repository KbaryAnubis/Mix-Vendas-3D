package com.mixvendas3d.app

import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.export.CsvExportColumns
import com.mixvendas3d.app.domain.export.CsvExportPeriod
import com.mixvendas3d.app.domain.export.CsvExportValidationException
import com.mixvendas3d.app.domain.export.CsvSaleExporter
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
import com.mixvendas3d.app.util.CurrencyFormatter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage5ACsvExportTest {

    private val spZone: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun parseCsvRows(csvWithBom: String): List<List<String>> {
        val content = if (csvWithBom.startsWith("\uFEFF")) csvWithBom.substring(1) else csvWithBom
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentCell = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < content.length) {
            val ch = content[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < content.length && content[i + 1] == '"') {
                        currentCell.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentCell.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ';' -> {
                        currentRow.add(currentCell.toString())
                        currentCell.setLength(0)
                    }
                    '\r' -> {
                        if (i + 1 < content.length && content[i + 1] == '\n') {
                            i += 1
                        }
                        currentRow.add(currentCell.toString())
                        currentCell.setLength(0)
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    '\n' -> {
                        currentRow.add(currentCell.toString())
                        currentCell.setLength(0)
                        rows.add(currentRow.toList())
                        currentRow.clear()
                    }
                    else -> currentCell.append(ch)
                }
            }
            i += 1
        }
        if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentCell.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }

    private fun epochAtSp(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second)
        .atZone(spZone)
        .toInstant()
        .toEpochMilli()

    private fun makeProduct(
        id: String,
        name: String,
        priceCents: Long,
        splits: List<Pair<Pair<String, String>, Long>>,
        feeMode: FeeResponsibilityMode = FeeResponsibilityMode.PROPORTIONAL,
        feeOwnerId: String? = null,
    ): Product {
        return Product(
            id = id,
            name = name,
            price = MoneyCents(priceCents),
            active = true,
            feeResponsibilityMode = feeMode,
            feeOwnerPersonId = feeOwnerId,
            participants = splits.mapIndexed { index, (person, cents) ->
                ProductParticipantSplit(
                    personId = person.first,
                    personName = person.second,
                    personColorIndex = index,
                    amount = MoneyCents(cents),
                )
            },
            createdAtEpochMs = 1L,
            updatedAtEpochMs = 1L,
        )
    }

    private fun makeMachine(
        id: String = "mach-1",
        name: String = "Ton Ultra",
        debitBps: Int = 199,
        creditDefaultBps: Int = 450,
        brandRates: Map<CardBrand, Int> = mapOf(CardBrand.VISA to 420, CardBrand.MASTERCARD to 480),
        installmentAddons: Map<Int, Int> = mapOf(1 to 0, 2 to 150, 3 to 300),
    ): Machine = Machine(
        id = id,
        name = name,
        debitRateBps = debitBps,
        creditEnabled = true,
        creditDefaultRateBps = creditDefaultBps,
        brandRatesEnabled = brandRates.isNotEmpty(),
        brandRatesBps = brandRates,
        installmentsEnabled = installmentAddons.isNotEmpty(),
        installmentAddonsBps = installmentAddons,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private fun buildSale(
        id: String,
        soldAtEpochMs: Long,
        updatedAtEpochMs: Long = soldAtEpochMs,
        items: List<SaleItemInput>,
        finalAmountOverrideCents: Long? = null,
        discountMode: SaleDiscountInput = SaleDiscountInput.Proportional,
        paymentMethod: SalePaymentMethod = SalePaymentMethod.PIX,
        machine: Machine? = null,
        cardBrand: CardBrand? = null,
        installments: Int? = null,
        manualRateOverrideBps: Int? = null,
        note: String = "",
    ): PersistedSale {
        val calc = SaleFinancialEngine.calculate(
            SaleCalculationInput(
                items = items,
                finalAmountOverride = finalAmountOverrideCents?.let(::MoneyCents),
                discountMode = discountMode,
                paymentMethod = paymentMethod,
                machine = machine,
                cardBrand = cardBrand,
                installments = installments,
                manualRateOverrideBps = manualRateOverrideBps,
                note = note,
            ),
        )
        return PersistedSale(
            id = id,
            soldAtEpochMs = soldAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            calculation = calc,
        )
    }

    @Test
    fun `01 empty sales produces headers only with UTF-8 BOM, semicolon separator and CRLF`() {
        val result = CsvSaleExporter.export(
            sales = emptyList(),
            zoneId = spZone,
            generatedOnDate = LocalDate.of(2026, 11, 17),
        )

        assertEquals(0, result.exportedSalesCount)
        assertEquals(0, result.detailedRowsCount)
        assertEquals("mix_vendas_3d_resumo_20261117.csv", result.suggestedSummaryFileName)
        assertEquals("mix_vendas_3d_detalhado_20261117.csv", result.suggestedDetailedFileName)

        assertTrue(result.summaryCsvContent.startsWith("\uFEFF"))
        assertTrue(result.detailedCsvContent.startsWith("\uFEFF"))
        assertTrue(result.summaryCsvContent.endsWith("\r\n"))
        assertTrue(result.detailedCsvContent.endsWith("\r\n"))

        val summaryRows = parseCsvRows(result.summaryCsvContent)
        val detailedRows = parseCsvRows(result.detailedCsvContent)
        assertEquals(1, summaryRows.size)
        assertEquals(1, detailedRows.size)
        assertEquals(CsvExportColumns.SUMMARY_HEADERS, summaryRows[0])
        assertEquals(CsvExportColumns.DETAILED_HEADERS, detailedRows[0])
    }

    @Test
    fun `02 and 12 single sale in cash and pix exports zero fee and zero rate without machine fields`() {
        val prod = makeProduct(
            id = "p-cash",
            name = "Vaso Geométrico",
            priceCents = 4_500L,
            splits = listOf(("u-1" to "Ana") to 4_500L),
        )
        val soldAt = epochAtSp(2026, 11, 10, 14, 5, 9)
        val updatedAt = epochAtSp(2026, 11, 11, 9, 0, 0)
        val cashSale = buildSale(
            id = "sale-cash-1",
            soldAtEpochMs = soldAt,
            updatedAtEpochMs = updatedAt,
            items = listOf(SaleItemInput(prod, 2)),
            paymentMethod = SalePaymentMethod.CASH,
            note = "Pagamento presencial",
        )
        val pixSale = buildSale(
            id = "sale-pix-2",
            soldAtEpochMs = epochAtSp(2026, 11, 10, 16, 30, 0),
            items = listOf(SaleItemInput(prod, 1)),
            paymentMethod = SalePaymentMethod.PIX,
        )

        val singleResult = CsvSaleExporter.export(listOf(cashSale), zoneId = spZone)
        assertEquals(1, singleResult.exportedSalesCount)
        assertEquals(1, singleResult.detailedRowsCount)

        val summaryRows = parseCsvRows(singleResult.summaryCsvContent)
        assertEquals(2, summaryRows.size)
        val sRow = summaryRows[1]
        assertEquals("sale-cash-1", sRow[0])
        assertEquals("10/11/2026", sRow[1])
        assertEquals("14:05:09", sRow[2])
        assertEquals("11/11/2026", sRow[3])
        assertEquals("2", sRow[4])
        assertEquals("90,00", sRow[5])
        assertEquals("0,00", sRow[6])
        assertEquals("90,00", sRow[7])
        assertEquals("Dinheiro", sRow[8])
        assertEquals("", sRow[9])
        assertEquals("", sRow[10])
        assertEquals("", sRow[11])
        assertEquals("", sRow[12])
        assertEquals("0,00", sRow[13])
        assertEquals("90,00", sRow[14])
        assertEquals("Pagamento presencial", sRow[15])

        val pixResult = CsvSaleExporter.export(listOf(pixSale), zoneId = spZone)
        val pixSummary = parseCsvRows(pixResult.summaryCsvContent)[1]
        val pixDetailed = parseCsvRows(pixResult.detailedCsvContent)[1]
        assertEquals("Pix", pixSummary[8])
        assertEquals("0,00", pixSummary[13])
        assertEquals("0,00%", pixDetailed[20])
        assertEquals("0,00", pixDetailed[21])
    }

    @Test
    fun `03 multiple sales and period filter respects local dates inclusively around midnight`() {
        val prod = makeProduct("p-1", "Chaveiro", 2_000L, listOf(("u-1" to "Ana") to 2_000L))
        // 2026-11-30 22:30-03:00 (2026-12-01 01:30Z -> ainda 30/11 em SP!)
        val nov30Local = ZonedDateTime.of(2026, 12, 1, 1, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        val dec01Local = epochAtSp(2026, 12, 1, 10, 0, 0)
        val dec05Local = epochAtSp(2026, 12, 5, 23, 59, 59)
        val dec06Local = epochAtSp(2026, 12, 6, 0, 0, 1)

        val sales = listOf(
            buildSale("s-nov30", nov30Local, items = listOf(SaleItemInput(prod, 1))),
            buildSale("s-dec01", dec01Local, items = listOf(SaleItemInput(prod, 1))),
            buildSale("s-dec05", dec05Local, items = listOf(SaleItemInput(prod, 1))),
            buildSale("s-dec06", dec06Local, items = listOf(SaleItemInput(prod, 1))),
        )

        val allResult = CsvSaleExporter.export(sales, CsvExportPeriod(), zoneId = spZone)
        assertEquals(4, allResult.exportedSalesCount)

        val decRangeResult = CsvSaleExporter.export(
            sales = sales,
            period = CsvExportPeriod(
                fromDate = LocalDate.of(2026, 12, 1),
                toDate = LocalDate.of(2026, 12, 5),
            ),
            zoneId = spZone,
        )
        assertEquals(2, decRangeResult.exportedSalesCount)
        val ids = parseCsvRows(decRangeResult.summaryCsvContent).drop(1).map { it[0] }
        assertEquals(listOf("s-dec01", "s-dec05"), ids)
    }

    @Test
    fun `04, 05, 06 and 20 multiple products, 3+ participants, opposite splits, informative detailed columns and no duplicated totals`() {
        val prodA = makeProduct(
            id = "p-a",
            name = "Suporte Headset",
            priceCents = 2_200L,
            splits = listOf(
                ("u-1" to "Alice") to 1_700L,
                ("u-2" to "Bruno") to 500L,
            ),
        )
        val prodB = makeProduct(
            id = "p-b",
            name = "Organizador Cabos",
            priceCents = 1_800L,
            splits = listOf(
                ("u-1" to "Alice") to 300L,
                ("u-2" to "Bruno") to 1_500L,
            ),
        )
        val prodC = makeProduct(
            id = "p-c",
            name = "Torre Multipla",
            priceCents = 3_000L,
            splits = listOf(
                ("u-1" to "Alice") to 1_000L,
                ("u-2" to "Bruno") to 1_000L,
                ("u-3" to "Carla") to 1_000L,
            ),
            feeMode = FeeResponsibilityMode.EQUAL,
        )

        val sale = buildSale(
            id = "sale-multi",
            soldAtEpochMs = epochAtSp(2026, 11, 15, 11, 20, 30),
            items = listOf(
                SaleItemInput(prodA, 1),
                SaleItemInput(prodB, 1),
                SaleItemInput(prodC, 2),
            ),
            paymentMethod = SalePaymentMethod.CREDIT,
            machine = makeMachine(),
            cardBrand = CardBrand.VISA,
            installments = 1, // 4,20%
        )

        val result = CsvSaleExporter.export(listOf(sale), zoneId = spZone)
        assertEquals(1, result.exportedSalesCount)
        // prodA (2) + prodB (2) + prodC (3) = 7 linhas detalhadas
        assertEquals(7, result.detailedRowsCount)

        val summaryRows = parseCsvRows(result.summaryCsvContent)
        assertEquals(2, summaryRows.size) // 1 cabecalho + 1 venda única!
        assertEquals("4", summaryRows[1][4]) // 1 + 1 + 2 = 4 unidades
        assertEquals("100,00", summaryRows[1][5]) // 2200 + 1800 + (2 * 3000) = 10000 => 100,00

        val detailedRows = parseCsvRows(result.detailedCsvContent).drop(1)
        assertEquals(7, detailedRows.size)
        // IDs determinísticos dos itens
        assertEquals(
            listOf(
                "sale-multi#1", "sale-multi#1",
                "sale-multi#2", "sale-multi#2",
                "sale-multi#3", "sale-multi#3", "sale-multi#3",
            ),
            detailedRows.map { it[3] },
        )
        // Colunas de item repetidas marcadas como [informativo]
        assertTrue(CsvExportColumns.DETAILED_HEADERS[8].contains("[informativo]"))
        assertTrue(CsvExportColumns.DETAILED_HEADERS[9].contains("[informativo]"))
        assertTrue(CsvExportColumns.DETAILED_HEADERS[10].contains("[informativo]"))
        assertTrue(CsvExportColumns.DETAILED_HEADERS[21].contains("[informativo]"))

        // Soma das parcelas dos participantes no CSV detalhado fecha exatamente no total da venda
        val detailedGrossSum = detailedRows.sumOf { CurrencyFormatter.parseBrlToCents(it[13])!! }
        val detailedFeeSum = detailedRows.sumOf { CurrencyFormatter.parseBrlToCents(it[14])!! }
        val detailedNetSum = detailedRows.sumOf { CurrencyFormatter.parseBrlToCents(it[15])!! }
        assertEquals(sale.calculation.finalAmount.rawCents, detailedGrossSum)
        assertEquals(sale.calculation.feeTotal.rawCents, detailedFeeSum)
        assertEquals(sale.calculation.netTotal.rawCents, detailedNetSum)
    }

    @Test
    fun `07 and 08 specific product discount and proportional discount reflect accurately in detailed CSV`() {
        val prodA = makeProduct("p-a", "Dragão Articulado", 10_000L, listOf(("u-1" to "Ana") to 10_000L))
        val prodB = makeProduct("p-b", "Base Display", 5_000L, listOf(("u-2" to "Beto") to 5_000L))

        val specificSale = buildSale(
            id = "sale-spec",
            soldAtEpochMs = epochAtSp(2026, 11, 1, 10, 0, 0),
            items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
            finalAmountOverrideCents = 13_000L, // R$ 20,00 desconto só no prodB
            discountMode = SaleDiscountInput.SpecificProduct("p-b"),
            paymentMethod = SalePaymentMethod.PIX,
        )
        val propSale = buildSale(
            id = "sale-prop",
            soldAtEpochMs = epochAtSp(2026, 11, 2, 10, 0, 0),
            items = listOf(SaleItemInput(prodA, 1), SaleItemInput(prodB, 1)),
            finalAmountOverrideCents = 12_000L, // R$ 30,00 desconto proporcional (20 em A, 10 em B)
            discountMode = SaleDiscountInput.Proportional,
            paymentMethod = SalePaymentMethod.PIX,
        )

        val result = CsvSaleExporter.export(listOf(specificSale, propSale), zoneId = spZone)
        val detailed = parseCsvRows(result.detailedCsvContent).drop(1)

        val specificRowsByProd = detailed.filter { it[0] == "sale-spec" }.associateBy { it[4] }
        val specA = specificRowsByProd.getValue("p-a")
        val specB = specificRowsByProd.getValue("p-b")
        assertEquals("0,00", specA[9])
        assertEquals("20,00", specB[9])
        assertEquals("Produto específico", specA[24])
        assertEquals("p-b", specA[25])
        assertEquals("Base Display", specA[26])

        val propRowsByProd = detailed.filter { it[0] == "sale-prop" }.associateBy { it[4] }
        val propA = propRowsByProd.getValue("p-a")
        val propB = propRowsByProd.getValue("p-b")
        assertEquals("20,00", propA[9])
        assertEquals("10,00", propB[9])
        assertEquals("Proporcional", propA[24])
        assertEquals("", propA[25])
        assertEquals("", propA[26])
    }

    @Test
    fun `09, 10 and 11 debit, credit with brand and installments, and manual fee override export exact snapshot`() {
        val prod = makeProduct(
            id = "p-card",
            name = "Estatueta",
            priceCents = 10_000L,
            splits = listOf(("u-1" to "Ana") to 10_000L),
            feeMode = FeeResponsibilityMode.INDIVIDUAL,
            feeOwnerId = "u-1",
        )
        val machine = makeMachine()

        val debitSale = buildSale(
            id = "sale-debit",
            soldAtEpochMs = epochAtSp(2026, 11, 1, 9, 0, 0),
            items = listOf(SaleItemInput(prod, 1)),
            paymentMethod = SalePaymentMethod.DEBIT,
            machine = machine,
        )
        val creditAuto = buildSale(
            id = "sale-credit-auto",
            soldAtEpochMs = epochAtSp(2026, 11, 1, 10, 0, 0),
            items = listOf(SaleItemInput(prod, 1)),
            paymentMethod = SalePaymentMethod.CREDIT,
            machine = machine,
            cardBrand = CardBrand.MASTERCARD,
            installments = 3, // 4,80% + 3,00% = 7,80%
        )
        val creditManual = buildSale(
            id = "sale-credit-manual",
            soldAtEpochMs = epochAtSp(2026, 11, 1, 11, 0, 0),
            items = listOf(SaleItemInput(prod, 1)),
            paymentMethod = SalePaymentMethod.CREDIT,
            machine = machine,
            cardBrand = CardBrand.VISA,
            installments = 2, // auto seria 5,70%, manual = 3,25%
            manualRateOverrideBps = 325,
        )

        val result = CsvSaleExporter.export(listOf(debitSale, creditAuto, creditManual), zoneId = spZone)
        val summary = parseCsvRows(result.summaryCsvContent).drop(1)
        val detailed = parseCsvRows(result.detailedCsvContent).drop(1)

        // Débito
        assertEquals("Débito", summary[0][8])
        assertEquals("Ton Ultra", summary[0][9])
        assertEquals("", summary[0][10])
        assertEquals("", summary[0][11])
        assertEquals("", summary[0][12])
        assertEquals("1,99", summary[0][13])
        assertEquals("98,01", summary[0][14])
        assertEquals("1,99%", detailed[0][20])

        // Crédito automático com bandeira e parcelamento
        assertEquals("Crédito", summary[1][8])
        assertEquals("Ton Ultra", summary[1][9])
        assertEquals("Mastercard", summary[1][10])
        assertEquals("3x", summary[1][11])
        assertEquals("", summary[1][12])
        assertEquals("7,80", summary[1][13])
        assertEquals("92,20", summary[1][14])
        assertEquals("7,80%", detailed[1][20])
        assertEquals("Pessoa específica", detailed[1][22])
        assertEquals("Ana", detailed[1][23])

        // Crédito com tarifa manual (não soma com automática!)
        assertEquals("Crédito", summary[2][8])
        assertEquals("Visa", summary[2][10])
        assertEquals("2x", summary[2][11])
        assertEquals("3,25%", summary[2][12])
        assertEquals("3,25", summary[2][13])
        assertEquals("96,75", summary[2][14])
        assertEquals("3,25%", detailed[2][20])
    }

    @Test
    fun `13 and 14 exact cent formatting without float drift and historical snapshot preservation`() {
        // Valor quebrado propenso a erro de Double/Float: 99,99 e taxa 3,33%
        val prod = makeProduct("p-cents", "Peça Precisão", 9_999L, listOf(("u-1" to "João") to 9_999L))
        val machine = makeMachine(name = "Maquininha Antiga", creditDefaultBps = 333, brandRates = emptyMap(), installmentAddons = emptyMap())
        val sale = buildSale(
            id = "sale-cents",
            soldAtEpochMs = epochAtSp(2026, 11, 3, 12, 0, 0),
            items = listOf(SaleItemInput(prod, 1)),
            paymentMethod = SalePaymentMethod.CREDIT,
            machine = machine,
            installments = 1,
        )

        val result = CsvSaleExporter.export(listOf(sale), zoneId = spZone)
        val sRow = parseCsvRows(result.summaryCsvContent)[1]
        // 9999 * 333 / 10000 = 332,9667 -> 333 centavos = 3,33; net = 9999 - 333 = 9666 = 96,66
        assertEquals("99,99", sRow[5])
        assertEquals("3,33", sRow[13])
        assertEquals("96,66", sRow[14])
        // Sem separador de milhar "." nem símbolo R$ no corpo das células numéricas
        assertFalse(sRow[5].contains("."))
        assertFalse(sRow[5].contains("R$"))
    }

    @Test
    fun `15, 16 and 17 UTF-8 BOM bytes, semicolon separator, quotes, line breaks and accents export cleanly`() {
        val trickyProd = makeProduct(
            id = "p-tricky",
            name = "Ação \"Especial\"; Linha 1\nLinha 2 — Café",
            priceCents = 5_000L,
            splits = listOf(("u-1" to "José \"Zé\"; Silva") to 5_000L),
        )
        val sale = buildSale(
            id = "sale-tricky",
            soldAtEpochMs = epochAtSp(2026, 11, 4, 8, 15, 0),
            items = listOf(SaleItemInput(trickyProd, 1)),
            paymentMethod = SalePaymentMethod.PIX,
            note = "Cliente pediu \"caixa\"; entregar\r\napós às 18h",
        )

        val result = CsvSaleExporter.export(listOf(sale), zoneId = spZone)
        val summaryBytes = result.summaryCsvBytes()
        val detailedBytes = result.detailedCsvBytes()

        // BOM EF BB BF
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            summaryBytes.copyOfRange(0, 3),
        )
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            detailedBytes.copyOfRange(0, 3),
        )
        assertTrue(String(summaryBytes, StandardCharsets.UTF_8).contains("após às 18h"))
        assertTrue(String(detailedBytes, StandardCharsets.UTF_8).contains("Café"))

        val parsedSummary = parseCsvRows(result.summaryCsvContent)
        assertEquals(2, parsedSummary.size)
        assertEquals("Cliente pediu \"caixa\"; entregar\r\napós às 18h", parsedSummary[1][15])

        val parsedDetailed = parseCsvRows(result.detailedCsvContent)
        assertEquals(2, parsedDetailed.size)
        assertEquals("Ação \"Especial\"; Linha 1\nLinha 2 — Café", parsedDetailed[1][5])
        assertEquals("José \"Zé\"; Silva", parsedDetailed[1][12])
    }

    @Test
    fun `18 formula injection payloads in text columns are neutralized with leading apostrophe`() {
        val prodEq = makeProduct("p-1", "=CMD('calc')", 3_000L, listOf(("u-1" to "+Ana") to 3_000L))
        val prodAt = makeProduct("p-2", "@SUM(1+1)", 2_000L, listOf(("u-2" to "-Beto") to 2_000L))
        val prodTab = makeProduct("p-3", "\t=1+1", 1_000L, listOf(("u-3" to " =Carla") to 1_000L))
        val machine = makeMachine(name = "=TonMaliciosa", debitBps = 200)

        val sale = buildSale(
            id = "sale-sec",
            soldAtEpochMs = epochAtSp(2026, 11, 5, 13, 0, 0),
            items = listOf(SaleItemInput(prodEq, 1), SaleItemInput(prodAt, 1), SaleItemInput(prodTab, 1)),
            paymentMethod = SalePaymentMethod.DEBIT,
            machine = machine,
            note = "+HYPERLINK(\"http://evil\")",
        )

        val result = CsvSaleExporter.export(listOf(sale), zoneId = spZone)
        val summary = parseCsvRows(result.summaryCsvContent)[1]
        val detailed = parseCsvRows(result.detailedCsvContent).drop(1)

        assertEquals("'=TonMaliciosa", summary[9])
        assertEquals("'+HYPERLINK(\"http://evil\")", summary[15])

        val detailedByProd = detailed.associateBy { it[4] }
        assertEquals("'=CMD('calc')", detailedByProd.getValue("p-1")[5])
        assertEquals("'+Ana", detailedByProd.getValue("p-1")[12])
        assertEquals("'@SUM(1+1)", detailedByProd.getValue("p-2")[5])
        assertEquals("'-Beto", detailedByProd.getValue("p-2")[12])
        assertEquals("'\t=1+1", detailedByProd.getValue("p-3")[5])
        assertEquals("' =Carla", detailedByProd.getValue("p-3")[12])
    }

    @Test
    fun `19 deterministic ordering sorts by soldAtEpochMs ascending then id regardless of input order`() {
        val prod = makeProduct("p-1", "Item", 1_000L, listOf(("u-1" to "Ana") to 1_000L))
        val t1 = epochAtSp(2026, 11, 1, 9, 0, 0)
        val t2 = epochAtSp(2026, 11, 2, 9, 0, 0)

        val sB = buildSale("sale-b", t1, items = listOf(SaleItemInput(prod, 1)))
        val sA = buildSale("sale-a", t1, items = listOf(SaleItemInput(prod, 1)))
        val sC = buildSale("sale-c", t2, items = listOf(SaleItemInput(prod, 1)))

        val order1 = CsvSaleExporter.export(listOf(sC, sB, sA), zoneId = spZone)
        val order2 = CsvSaleExporter.export(listOf(sA, sC, sB), zoneId = spZone)

        assertEquals(order1.summaryCsvContent, order2.summaryCsvContent)
        assertEquals(order1.detailedCsvContent, order2.detailedCsvContent)

        val exportedIds = parseCsvRows(order1.summaryCsvContent).drop(1).map { it[0] }
        assertEquals(listOf("sale-a", "sale-b", "sale-c"), exportedIds)
    }

    @Test
    fun `21 corrupted snapshots and duplicate or blank sale IDs throw CsvExportValidationException with saleId`() {
        val prod = makeProduct("p-1", "Item", 10_000L, listOf(("u-1" to "Ana") to 10_000L))
        val valid = buildSale("sale-ok", epochAtSp(2026, 11, 6, 10, 0, 0), items = listOf(SaleItemInput(prod, 1)))

        // 1) Corrupcao subtotal - desconto != valor final
        val badSubtotal = valid.copy(
            id = "sale-bad-subtotal",
            calculation = valid.calculation.copy(finalAmount = MoneyCents(9_500L)),
        )
        val ex1 = assertThrows(CsvExportValidationException::class.java) {
            CsvSaleExporter.export(listOf(badSubtotal), zoneId = spZone)
        }
        assertEquals("sale-bad-subtotal", ex1.saleId)
        assertTrue(ex1.message.orEmpty().contains("sale-bad-subtotal"))

        // 2) Corrupcao final - tarifa != liquido
        val badNet = valid.copy(
            id = "sale-bad-net",
            calculation = valid.calculation.copy(netTotal = MoneyCents(9_900L)),
        )
        val ex2 = assertThrows(CsvExportValidationException::class.java) {
            CsvSaleExporter.export(listOf(badNet), zoneId = spZone)
        }
        assertEquals("sale-bad-net", ex2.saleId)

        // 3) Corrupcao soma personTotals != totais da venda
        val badPersonTotals = valid.copy(
            id = "sale-bad-person",
            calculation = valid.calculation.copy(
                personTotals = listOf(
                    valid.calculation.personTotals.first().copy(netAmount = MoneyCents(8_000L)),
                ),
            ),
        )
        val ex3 = assertThrows(CsvExportValidationException::class.java) {
            CsvSaleExporter.export(listOf(badPersonTotals), zoneId = spZone)
        }
        assertEquals("sale-bad-person", ex3.saleId)

        // 4) ID duplicado
        val ex4 = assertThrows(CsvExportValidationException::class.java) {
            CsvSaleExporter.export(listOf(valid, valid), zoneId = spZone)
        }
        assertEquals("sale-ok", ex4.saleId)

        // 5) ID em branco
        val blankSale = valid.copy(id = "   ")
        val ex5 = assertThrows(CsvExportValidationException::class.java) {
            CsvSaleExporter.export(listOf(blankSale), zoneId = spZone)
        }
        assertEquals("   ", ex5.saleId)

        // 6) Timestamp negativo
        val negativeTime = valid.copy(id = "sale-bad-time", soldAtEpochMs = -5L)
        val ex6 = assertThrows(CsvExportValidationException::class.java) {
            CsvSaleExporter.export(listOf(negativeTime), zoneId = spZone)
        }
        assertEquals("sale-bad-time", ex6.saleId)
    }
}
