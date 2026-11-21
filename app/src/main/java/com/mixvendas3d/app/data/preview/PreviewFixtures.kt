package com.mixvendas3d.app.data.preview

import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PreviewMachine
import com.mixvendas3d.app.domain.models.PreviewPaymentMethod
import com.mixvendas3d.app.domain.models.PreviewPerson
import com.mixvendas3d.app.domain.models.PreviewPersonAllocation
import com.mixvendas3d.app.domain.models.PreviewProduct
import com.mixvendas3d.app.domain.models.PreviewSale
import com.mixvendas3d.app.domain.models.PreviewSaleItem
import java.time.LocalDate

/**
 * Dados temporários em memória exclusivos para demonstração visual da Etapa 2A.
 * Nada deste arquivo é gravado no banco Room local.
 */
object PreviewFixtures {

    val people: List<PreviewPerson> = listOf(
        PreviewPerson(id = "p-1", name = "Pessoa 1 (Prévia)", colorIndex = 0),
        PreviewPerson(id = "p-2", name = "Pessoa 2 (Prévia)", colorIndex = 1),
        PreviewPerson(id = "p-3", name = "Pessoa 3 (Prévia)", colorIndex = 2),
        PreviewPerson(id = "p-4", name = "Pessoa 4 (Prévia)", colorIndex = 3),
    )

    val products: List<PreviewProduct> = listOf(
        PreviewProduct(
            id = "prod-1",
            name = "Impressão 3D Miniatura",
            price = MoneyCents(6_000L),
            splitSummary = "Pessoa 1 R$ 36,00 · Pessoa 2 R$ 24,00",
        ),
        PreviewProduct(
            id = "prod-2",
            name = "Suporte Articulado PLA",
            price = MoneyCents(4_500L),
            splitSummary = "Pessoa 1 R$ 25,00 · Pessoa 3 R$ 20,00",
        ),
        PreviewProduct(
            id = "prod-3",
            name = "Chaveiro Personalizado 3D",
            price = MoneyCents(2_000L),
            splitSummary = "Pessoa 2 R$ 10,00 · Pessoa 4 R$ 10,00",
        ),
        PreviewProduct(
            id = "prod-4",
            name = "Busto Colecionável Resina",
            price = MoneyCents(12_000L),
            splitSummary = "Pessoa 1 R$ 70,00 · Pessoa 2 R$ 50,00",
        ),
        PreviewProduct(
            id = "prod-5",
            name = "Organizador Modular Mesa",
            price = MoneyCents(8_000L),
            splitSummary = "Pessoa 3 R$ 50,00 · Pessoa 4 R$ 30,00",
        ),
    )

    val machines: List<PreviewMachine> = listOf(
        PreviewMachine(
            id = "mac-1",
            name = "Ton Pro (Prévia)",
            supportsCredit = true,
            supportsBrands = true,
            supportsInstallments = true,
            defaultDebitBasisPoints = 149,
            defaultCreditBasisPoints = 319,
        ),
        PreviewMachine(
            id = "mac-2",
            name = "InfinitePay (Prévia)",
            supportsCredit = true,
            supportsBrands = false,
            supportsInstallments = true,
            defaultDebitBasisPoints = 138,
            defaultCreditBasisPoints = 299,
        ),
        PreviewMachine(
            id = "mac-3",
            name = "Somente Débito (Prévia)",
            supportsCredit = false,
            supportsBrands = false,
            supportsInstallments = false,
            defaultDebitBasisPoints = 165,
            defaultCreditBasisPoints = 0,
        ),
    )

    val cardBrands: List<String> = listOf("Visa", "Mastercard", "Elo", "Hipercard")
    val installmentsList: List<Int> = (1..12).toList()

    fun buildPreviewSales(referenceDate: LocalDate = LocalDate.now()): List<PreviewSale> {
        val yesterday = referenceDate.minusDays(1)
        val threeDaysAgo = referenceDate.minusDays(3)
        val previousMonth = referenceDate.minusMonths(1)

        return listOf(
            PreviewSale(
                id = "demo-1",
                date = referenceDate,
                timeLabel = "14:25",
                title = "2 produtos (Prévia)",
                method = PreviewPaymentMethod.CREDIT,
                machineName = "Ton Pro (Prévia)",
                brand = "Visa",
                installments = 2,
                originalGross = MoneyCents(10_500L),
                discountTotal = MoneyCents(500L),
                gross = MoneyCents(10_000L),
                fee = MoneyCents(420L),
                net = MoneyCents(9_580L),
                items = listOf(
                    PreviewSaleItem(
                        productId = "prod-1",
                        productName = "Impressão 3D Miniatura",
                        quantity = 1,
                        unitPrice = MoneyCents(6_000L),
                        originalGross = MoneyCents(6_000L),
                        discount = MoneyCents(300L),
                        gross = MoneyCents(5_700L),
                        fee = MoneyCents(240L),
                        net = MoneyCents(5_460L),
                        rateLabel = "4,20%",
                        allocations = listOf(
                            PreviewPersonAllocation("p-1", "Pessoa 1 (Prévia)", MoneyCents(3_420L), MoneyCents(144L), MoneyCents(3_276L)),
                            PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(2_280L), MoneyCents(96L), MoneyCents(2_184L)),
                        ),
                    ),
                    PreviewSaleItem(
                        productId = "prod-2",
                        productName = "Suporte Articulado PLA",
                        quantity = 1,
                        unitPrice = MoneyCents(4_500L),
                        originalGross = MoneyCents(4_500L),
                        discount = MoneyCents(200L),
                        gross = MoneyCents(4_300L),
                        fee = MoneyCents(180L),
                        net = MoneyCents(4_120L),
                        rateLabel = "4,20%",
                        allocations = listOf(
                            PreviewPersonAllocation("p-1", "Pessoa 1 (Prévia)", MoneyCents(2_400L), MoneyCents(100L), MoneyCents(2_300L)),
                            PreviewPersonAllocation("p-3", "Pessoa 3 (Prévia)", MoneyCents(1_900L), MoneyCents(80L), MoneyCents(1_820L)),
                        ),
                    ),
                ),
                allocations = listOf(
                    PreviewPersonAllocation("p-1", "Pessoa 1 (Prévia)", MoneyCents(5_820L), MoneyCents(244L), MoneyCents(5_576L)),
                    PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(2_280L), MoneyCents(96L), MoneyCents(2_184L)),
                    PreviewPersonAllocation("p-3", "Pessoa 3 (Prévia)", MoneyCents(1_900L), MoneyCents(80L), MoneyCents(1_820L)),
                ),
                note = "Venda demonstrativa em memória (desconto proporcional).",
            ),
            PreviewSale(
                id = "demo-2",
                date = yesterday,
                timeLabel = "17:10",
                title = "Busto Colecionável Resina",
                method = PreviewPaymentMethod.PIX,
                machineName = null,
                brand = null,
                installments = null,
                originalGross = MoneyCents(12_000L),
                discountTotal = MoneyCents.ZERO,
                gross = MoneyCents(12_000L),
                fee = MoneyCents.ZERO,
                net = MoneyCents(12_000L),
                items = listOf(
                    PreviewSaleItem(
                        productId = "prod-4",
                        productName = "Busto Colecionável Resina",
                        quantity = 1,
                        unitPrice = MoneyCents(12_000L),
                        originalGross = MoneyCents(12_000L),
                        discount = MoneyCents.ZERO,
                        gross = MoneyCents(12_000L),
                        fee = MoneyCents.ZERO,
                        net = MoneyCents(12_000L),
                        rateLabel = "0,00%",
                        allocations = listOf(
                            PreviewPersonAllocation("p-1", "Pessoa 1 (Prévia)", MoneyCents(7_000L), MoneyCents.ZERO, MoneyCents(7_000L)),
                            PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(5_000L), MoneyCents.ZERO, MoneyCents(5_000L)),
                        ),
                    ),
                ),
                allocations = listOf(
                    PreviewPersonAllocation("p-1", "Pessoa 1 (Prévia)", MoneyCents(7_000L), MoneyCents.ZERO, MoneyCents(7_000L)),
                    PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(5_000L), MoneyCents.ZERO, MoneyCents(5_000L)),
                ),
                note = "",
            ),
            PreviewSale(
                id = "demo-3",
                date = threeDaysAgo,
                timeLabel = "11:40",
                title = "Organizador Modular Mesa + Chaveiro",
                method = PreviewPaymentMethod.DEBIT,
                machineName = "InfinitePay (Prévia)",
                brand = null,
                installments = null,
                originalGross = MoneyCents(10_000L),
                discountTotal = MoneyCents.ZERO,
                gross = MoneyCents(10_000L),
                fee = MoneyCents(138L),
                net = MoneyCents(9_862L),
                items = listOf(
                    PreviewSaleItem(
                        productId = "prod-5",
                        productName = "Organizador Modular Mesa",
                        quantity = 1,
                        unitPrice = MoneyCents(8_000L),
                        originalGross = MoneyCents(8_000L),
                        discount = MoneyCents.ZERO,
                        gross = MoneyCents(8_000L),
                        fee = MoneyCents(110L),
                        net = MoneyCents(7_890L),
                        rateLabel = "1,38%",
                        allocations = listOf(
                            PreviewPersonAllocation("p-3", "Pessoa 3 (Prévia)", MoneyCents(5_000L), MoneyCents(69L), MoneyCents(4_931L)),
                            PreviewPersonAllocation("p-4", "Pessoa 4 (Prévia)", MoneyCents(3_000L), MoneyCents(41L), MoneyCents(2_959L)),
                        ),
                    ),
                    PreviewSaleItem(
                        productId = "prod-3",
                        productName = "Chaveiro Personalizado 3D",
                        quantity = 1,
                        unitPrice = MoneyCents(2_000L),
                        originalGross = MoneyCents(2_000L),
                        discount = MoneyCents.ZERO,
                        gross = MoneyCents(2_000L),
                        fee = MoneyCents(28L),
                        net = MoneyCents(1_972L),
                        rateLabel = "1,38%",
                        allocations = listOf(
                            PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(1_000L), MoneyCents(14L), MoneyCents(986L)),
                            PreviewPersonAllocation("p-4", "Pessoa 4 (Prévia)", MoneyCents(1_000L), MoneyCents(14L), MoneyCents(986L)),
                        ),
                    ),
                ),
                allocations = listOf(
                    PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(1_000L), MoneyCents(14L), MoneyCents(986L)),
                    PreviewPersonAllocation("p-3", "Pessoa 3 (Prévia)", MoneyCents(5_000L), MoneyCents(69L), MoneyCents(4_931L)),
                    PreviewPersonAllocation("p-4", "Pessoa 4 (Prévia)", MoneyCents(4_000L), MoneyCents(55L), MoneyCents(3_945L)),
                ),
                note = "Exemplo com múltiplas pessoas e maquininha no débito.",
            ),
            PreviewSale(
                id = "demo-4",
                date = previousMonth,
                timeLabel = "16:05",
                title = "2× Chaveiro Personalizado 3D",
                method = PreviewPaymentMethod.CASH,
                machineName = null,
                brand = null,
                installments = null,
                originalGross = MoneyCents(4_000L),
                discountTotal = MoneyCents.ZERO,
                gross = MoneyCents(4_000L),
                fee = MoneyCents.ZERO,
                net = MoneyCents(4_000L),
                items = listOf(
                    PreviewSaleItem(
                        productId = "prod-3",
                        productName = "Chaveiro Personalizado 3D",
                        quantity = 2,
                        unitPrice = MoneyCents(2_000L),
                        originalGross = MoneyCents(4_000L),
                        discount = MoneyCents.ZERO,
                        gross = MoneyCents(4_000L),
                        fee = MoneyCents.ZERO,
                        net = MoneyCents(4_000L),
                        rateLabel = "0,00%",
                        allocations = listOf(
                            PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(2_000L), MoneyCents.ZERO, MoneyCents(2_000L)),
                            PreviewPersonAllocation("p-4", "Pessoa 4 (Prévia)", MoneyCents(2_000L), MoneyCents.ZERO, MoneyCents(2_000L)),
                        ),
                    ),
                ),
                allocations = listOf(
                    PreviewPersonAllocation("p-2", "Pessoa 2 (Prévia)", MoneyCents(2_000L), MoneyCents.ZERO, MoneyCents(2_000L)),
                    PreviewPersonAllocation("p-4", "Pessoa 4 (Prévia)", MoneyCents(2_000L), MoneyCents.ZERO, MoneyCents(2_000L)),
                ),
                note = "Mês anterior para demonstrar os totais mensais.",
            ),
        )
    }
}
