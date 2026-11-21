package com.mixvendas3d.app.domain.export

import java.nio.charset.StandardCharsets
import java.time.LocalDate

/**
 * Filtro opcional de período em datas locais do dispositivo para exportação CSV.
 */
data class CsvExportPeriod(
    val fromDate: LocalDate? = null,
    val toDate: LocalDate? = null,
) {
    init {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw IllegalArgumentException(
                "Período de exportação inválido: data inicial ($fromDate) posterior à data final ($toDate).",
            )
        }
    }
}

/**
 * Exceção lançada quando alguma venda possui snapshot inconsistente, duplicado ou incompleto,
 * identificando explicitamente o ID da venda problemática para impedir exportação corrompida.
 */
class CsvExportValidationException(
    val saleId: String,
    message: String,
) : IllegalArgumentException(message)

/**
 * Resultado completo da geração dos dois CSVs oficiais (Resumo das Vendas e Detalhado),
 * já codificados com BOM UTF-8, separador `;` e quebra de linha `\r\n` compatíveis com Excel PT-BR.
 */
data class CsvExportResult(
    val summaryCsvContent: String,
    val detailedCsvContent: String,
    val exportedSalesCount: Int,
    val detailedRowsCount: Int,
    val suggestedSummaryFileName: String,
    val suggestedDetailedFileName: String,
) {
    fun summaryCsvBytes(): ByteArray = summaryCsvContent.toByteArray(StandardCharsets.UTF_8)

    fun detailedCsvBytes(): ByteArray = detailedCsvContent.toByteArray(StandardCharsets.UTF_8)
}

object CsvExportColumns {
    val SUMMARY_HEADERS: List<String> = listOf(
        "ID da venda",
        "Data original",
        "Hora original",
        "Data da última atualização",
        "Quantidade total de unidades",
        "Subtotal original (R$)",
        "Desconto total (R$)",
        "Valor final cobrado (R$)",
        "Forma de pagamento",
        "Maquininha utilizada",
        "Bandeira",
        "Parcelamento",
        "Tarifa manual (%)",
        "Tarifa total (R$)",
        "Total líquido (R$)",
        "Observação",
    )

    val DETAILED_HEADERS: List<String> = listOf(
        "ID da venda",
        "Data da venda",
        "Hora da venda",
        "ID do item",
        "ID original do produto",
        "Nome histórico do produto",
        "Quantidade do item",
        "Preço unitário original (R$)",
        "Subtotal original do item [informativo] (R$)",
        "Desconto do item [informativo] (R$)",
        "Valor final do item [informativo] (R$)",
        "ID da pessoa",
        "Nome histórico da pessoa",
        "Valor bruto do participante após descontos (R$)",
        "Tarifa atribuída ao participante (R$)",
        "Valor líquido do participante (R$)",
        "Forma de pagamento",
        "Maquininha utilizada",
        "Bandeira",
        "Parcelamento",
        "Taxa aplicada ao item (%)",
        "Tarifa do item [informativo] (R$)",
        "Regra de responsabilidade pela tarifa",
        "Responsável individual pela tarifa",
        "Tipo de desconto da venda",
        "ID do produto-alvo do desconto",
        "Produto-alvo do desconto",
    )
}
