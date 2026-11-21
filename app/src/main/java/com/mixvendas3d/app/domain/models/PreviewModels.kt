package com.mixvendas3d.app.domain.models

import java.time.LocalDate

enum class PreviewPaymentMethod(val label: String) {
    CASH("Dinheiro"),
    PIX("Pix"),
    DEBIT("Débito"),
    CREDIT("Crédito"),
}

enum class PreviewPeriodFilter(val label: String) {
    TODAY("Hoje"),
    WEEK("7 dias"),
    MONTH("Este mês"),
    ALL("Todos"),
    CUSTOM("Datas"),
}

data class PreviewPerson(
    val id: String,
    val name: String,
    val colorIndex: Int,
)

data class PreviewProduct(
    val id: String,
    val name: String,
    val price: MoneyCents,
    val splitSummary: String,
)

data class PreviewMachine(
    val id: String,
    val name: String,
    val supportsCredit: Boolean,
    val supportsBrands: Boolean,
    val supportsInstallments: Boolean,
    val defaultDebitBasisPoints: Int,
    val defaultCreditBasisPoints: Int,
)

data class PreviewPersonAllocation(
    val personId: String,
    val personName: String,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
)

data class PreviewSaleItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: MoneyCents,
    val originalGross: MoneyCents,
    val discount: MoneyCents,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
    val rateLabel: String,
    val allocations: List<PreviewPersonAllocation>,
)

data class PreviewSale(
    val id: String,
    val date: LocalDate,
    val timeLabel: String,
    val title: String,
    val method: PreviewPaymentMethod,
    val machineName: String?,
    val brand: String?,
    val installments: Int?,
    val originalGross: MoneyCents,
    val discountTotal: MoneyCents,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
    val items: List<PreviewSaleItem>,
    val allocations: List<PreviewPersonAllocation>,
    val note: String,
)

data class PreviewPersonSummary(
    val person: PreviewPerson,
    val gross: MoneyCents,
    val fee: MoneyCents,
    val net: MoneyCents,
)
