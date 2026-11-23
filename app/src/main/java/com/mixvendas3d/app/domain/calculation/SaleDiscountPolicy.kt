package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import com.mixvendas3d.app.util.CurrencyFormatter
import java.util.Locale

data class DiscountSelectablePersonItem(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val productCount: Int,
    val totalUnitsAcrossSelected: Int,
    val originalTotalInSelectedProducts: MoneyCents,
    val fixedUnitAmountCents: Long?,
    val isProtected: Boolean,
    val isFixedUnitProtectedByDefault: Boolean,
    val isFixedReductionAuthorized: Boolean,
    val participationSummary: String,
)

data class DiscountPreviewProductLine(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceOriginal: MoneyCents,
    val originalSubtotal: MoneyCents,
    val discountAmount: MoneyCents,
    val finalAmount: MoneyCents,
    val isSelectedForDiscount: Boolean,
)

data class DiscountPreviewPersonLine(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val beforeDiscount: MoneyCents,
    val discountAbsorbed: MoneyCents,
    val afterDiscountGross: MoneyCents,
    val feeAmount: MoneyCents,
    val netAmount: MoneyCents,
    val isProtected: Boolean,
    val isFixedUnitShare: Boolean,
    val participatesInSelectedProducts: Boolean,
)

data class VenderDiscountPreview(
    val selectedProducts: List<DiscountPreviewProductLine>,
    val unselectedProducts: List<DiscountPreviewProductLine>,
    val selectedOriginalSubtotal: MoneyCents,
    val discountTotal: MoneyCents,
    val selectedFinalAmount: MoneyCents,
    val protectedPeople: List<DiscountPreviewPersonLine>,
    val eligiblePeople: List<DiscountPreviewPersonLine>,
    val allPersonBreakdowns: List<DiscountPreviewPersonLine>,
    val calculationResult: SaleCalculationResult?,
    val validationError: String?,
) {
    val isValid: Boolean
        get() = validationError == null && calculationResult != null
}

/**
 * Regras centralizadas de política de desconto:
 * - Identificação de remuneração fixa por unidade (caso real de R$ 5,00/un) protegida por padrão.
 * - Agregação deduplicada de pessoas participantes dos produtos selecionados para desconto.
 * - Reconstrução determinística das pessoas protegidas ao reabrir uma venda salva para edição.
 * - Construção da prévia detalhada em tempo real diretamente sobre o [SaleFinancialEngine].
 */
object SaleDiscountPolicy {
    const val FIXED_UNIT_SHARE_PROTECTED_BY_DEFAULT_CENTS: Long = 500L // R$ 5,00 por unidade
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * Identifica o padrão real de remuneração fixa por unidade (R$ 5,00/un) em produtos
     * compartilhados onde existe pelo menos outro participante com fatia unitária maior.
     */
    fun isFixedUnitShareParticipant(
        product: Product,
        split: ProductParticipantSplit,
    ): Boolean {
        if (product.participants.size < 2) return false
        if (split.amount.rawCents != FIXED_UNIT_SHARE_PROTECTED_BY_DEFAULT_CENTS) return false
        return product.participants.any { other ->
            other.personId != split.personId && other.amount.rawCents > split.amount.rawCents
        }
    }

    /**
     * Retorna o conjunto de IDs de pessoas que possuem remuneração fixa de R$ 5,00/un
     * em pelo menos um dos [selectedProducts].
     */
    fun detectFixedUnitSharePersonIds(selectedProducts: List<Product>): Set<String> {
        val result = LinkedHashSet<String>()
        selectedProducts.forEach { product ->
            product.participants.forEach { split ->
                if (isFixedUnitShareParticipant(product, split)) {
                    result.add(split.personId)
                }
            }
        }
        return result
    }

    /**
     * Retorna os IDs de todas as pessoas vinculadas aos produtos selecionados na primeira caixa.
     */
    fun collectPersonIdsInProducts(products: List<Product>): Set<String> {
        val result = LinkedHashSet<String>()
        products.forEach { product ->
            product.participants.forEach { split ->
                result.add(split.personId)
            }
        }
        return result
    }

    /**
     * Consolida, sem duplicação, as pessoas participantes dos produtos selecionados para desconto,
     * calculando resumo de participação e sinalizando proteção padrão para remuneração fixa (R$ 5,00/un).
     */
    fun buildSelectablePeopleForDiscount(
        selectedDiscountEntries: List<Pair<Product, Int>>,
        protectedPersonIds: Set<String>,
        unprotectedFixedPersonIds: Set<String>,
    ): List<DiscountSelectablePersonItem> {
        data class Acc(
            val personId: String,
            val personName: String,
            val personColorIndex: Int,
            var productCount: Int = 0,
            var totalUnits: Int = 0,
            var totalOriginalCents: Long = 0L,
            var hasFixedUnitShare: Boolean = false,
            val unitAmounts: MutableSet<Long> = LinkedHashSet(),
        )

        val byPerson = LinkedHashMap<String, Acc>()
        selectedDiscountEntries.forEach { (product, qty) ->
            if (qty <= 0) return@forEach
            product.participants.forEach { split ->
                val lineCents = MonetaryRounding.safeMultiplyOrNull(split.amount.rawCents, qty) ?: 0L
                val acc = byPerson.getOrPut(split.personId) {
                    Acc(
                        personId = split.personId,
                        personName = split.personName,
                        personColorIndex = split.personColorIndex,
                    )
                }
                acc.productCount += 1
                acc.totalUnits += qty
                acc.totalOriginalCents = MonetaryRounding.safeAddOrNull(acc.totalOriginalCents, lineCents)
                    ?: acc.totalOriginalCents
                acc.unitAmounts.add(split.amount.rawCents)
                if (isFixedUnitShareParticipant(product, split)) {
                    acc.hasFixedUnitShare = true
                }
            }
        }

        return byPerson.values
            .sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )
            .map { acc ->
                val isProtected = acc.personId in protectedPersonIds
                val fixedAuthorized = acc.hasFixedUnitShare && !isProtected && acc.personId in unprotectedFixedPersonIds
                val productWord = if (acc.productCount == 1) "produto" else "produtos"
                val totalText = CurrencyFormatter.formatBrl(MoneyCents(acc.totalOriginalCents))
                val summary = if (acc.hasFixedUnitShare) {
                    val fixedFormatted = CurrencyFormatter.formatBrl(MoneyCents(FIXED_UNIT_SHARE_PROTECTED_BY_DEFAULT_CENTS))
                    "$totalText em ${acc.productCount} $productWord (${acc.totalUnits}× $fixedFormatted/un fixo)"
                } else if (acc.unitAmounts.size == 1) {
                    val unitFormatted = CurrencyFormatter.formatBrl(MoneyCents(acc.unitAmounts.first()))
                    "$totalText em ${acc.productCount} $productWord (${acc.totalUnits}× $unitFormatted/un)"
                } else {
                    "$totalText em ${acc.productCount} $productWord"
                }

                DiscountSelectablePersonItem(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    productCount = acc.productCount,
                    totalUnitsAcrossSelected = acc.totalUnits,
                    originalTotalInSelectedProducts = MoneyCents(acc.totalOriginalCents),
                    fixedUnitAmountCents = if (acc.hasFixedUnitShare) FIXED_UNIT_SHARE_PROTECTED_BY_DEFAULT_CENTS else null,
                    isProtected = isProtected,
                    isFixedUnitProtectedByDefault = acc.hasFixedUnitShare,
                    isFixedReductionAuthorized = fixedAuthorized,
                    participationSummary = summary,
                )
            }
    }

    /**
     * Resolve o conjunto de IDs de produtos selecionados para desconto a partir de um snapshot salvo.
     */
    fun selectedProductIdsFromSnapshot(calc: SaleCalculationResult): Set<String> {
        return when (val cfg = calc.discountConfig) {
            is SaleDiscountConfigSnapshot.Proportional -> calc.items.mapTo(LinkedHashSet()) { it.productId }
            is SaleDiscountConfigSnapshot.SpecificProduct -> setOf(cfg.productId)
            is SaleDiscountConfigSnapshot.SelectedProducts -> cfg.productIds.toCollection(LinkedHashSet())
        }
    }

    /**
     * Reconstrói deterministicamente as pessoas protegidas contra desconto a partir dos snapshots
     * reais gravados em [SaleCalculationResult.items]:
     * Uma pessoa é identificada como protegida quando participa de itens descontados com participação
     * original positiva, recebeu desconto zero em TODOS os itens selecionados dos quais participa, e
     * receberia >= 1 centavo de desconto caso a distribuição proporcional padrão sem proteção fosse aplicada.
     */
    fun inferProtectedPersonIdsFromSnapshot(
        calc: SaleCalculationResult,
        selectedDiscountProductIds: Set<String> = selectedProductIdsFromSnapshot(calc),
    ): Set<String> {
        if (calc.discountTotal.rawCents <= 0L) return emptySet()

        val confirmedProtectedCandidates = LinkedHashSet<String>()
        val absorbedAnyDiscount = LinkedHashSet<String>()

        calc.items.forEach { item: SaleItemCalculationResult ->
            if (item.productId !in selectedDiscountProductIds) return@forEach
            val itemDiscount = item.discountAmount.rawCents
            if (itemDiscount <= 0L) return@forEach

            item.participantSplits.forEach { split ->
                if (split.discountAmount.rawCents > 0L) {
                    absorbedAnyDiscount.add(split.personId)
                }
            }

            // Calcula o que cada participante receberia de desconto SEM proteção neste item
            val allBuckets = item.participantSplits.map { split ->
                WeightedAllocationBucket(
                    key = split.personId,
                    weight = split.originalSubtotal.rawCents,
                )
            }
            val totalWeight = allBuckets.sumOf { it.weight }
            if (totalWeight <= 0L) return@forEach

            val unprotectedGrossMap = MonetaryRounding.allocateByWeightsDeterministic(
                totalAmount = item.finalAmount.rawCents,
                buckets = allBuckets,
            )

            item.participantSplits.forEach { split ->
                val orig = split.originalSubtotal.rawCents
                val actualDiscount = split.discountAmount.rawCents
                val unprotectedGross = unprotectedGrossMap.getValue(split.personId)
                val unprotectedDiscount = orig - unprotectedGross
                if (orig > 0L && actualDiscount == 0L && unprotectedDiscount > 0L) {
                    confirmedProtectedCandidates.add(split.personId)
                }
            }
        }

        confirmedProtectedCandidates.removeAll(absorbedAnyDiscount)
        return confirmedProtectedCandidates
    }

    /**
     * Constrói a prévia financeira completa antes de salvar a venda, usando o próprio
     * [SaleFinancialEngine.calculate] como única fonte da verdade para cálculos e validações.
     */
    fun buildPreview(
        selectedEntries: List<Pair<Product, Int>>,
        discountSelectedProductIds: Set<String>,
        protectedPersonIds: Set<String>,
        discountCents: Long,
        calculationInputBuilder: () -> SaleCalculationInput,
        preValidationError: String? = null,
    ): VenderDiscountPreview {
        val selectedEntryList = selectedEntries.filter { it.first.id in discountSelectedProductIds }
        val unselectedEntryList = selectedEntries.filter { it.first.id !in discountSelectedProductIds }

        val selectedOriginalCents = selectedEntryList.fold(0L) { acc, (product, qty) ->
            MonetaryRounding.safeAddOrNull(
                acc,
                MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, qty) ?: 0L,
            ) ?: acc
        }

        val fixedPersonIds = detectFixedUnitSharePersonIds(selectedEntryList.map { it.first })
        val peopleInSelectedProducts = collectPersonIdsInProducts(selectedEntryList.map { it.first })

        // Tenta executar o motor financeiro real se não houver erro de entrada prévio
        var calcResult: SaleCalculationResult? = null
        var errorMsg: String? = preValidationError

        if (errorMsg == null) {
            if (discountCents > 0L && selectedEntryList.isEmpty()) {
                errorMsg = "Selecione pelo menos um produto para aplicar o desconto."
            } else {
                try {
                    calcResult = SaleFinancialEngine.calculate(calculationInputBuilder())
                } catch (ex: Throwable) {
                    errorMsg = ex.message?.takeIf { it.isNotBlank() }
                        ?: "Não foi possível calcular o desconto com a seleção atual."
                }
            }
        }

        val itemResultById = calcResult?.items?.associateBy { it.productId }.orEmpty()

        val selectedProductLines = selectedEntryList.map { (product, qty) ->
            val origSub = MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, qty) ?: 0L
            val computed = itemResultById[product.id]
            DiscountPreviewProductLine(
                productId = product.id,
                productName = product.name,
                quantity = qty,
                unitPriceOriginal = product.price,
                originalSubtotal = MoneyCents(origSub),
                discountAmount = computed?.discountAmount ?: MoneyCents.ZERO,
                finalAmount = computed?.finalAmount ?: MoneyCents(origSub),
                isSelectedForDiscount = true,
            )
        }

        val unselectedProductLines = unselectedEntryList.map { (product, qty) ->
            val origSub = MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, qty) ?: 0L
            DiscountPreviewProductLine(
                productId = product.id,
                productName = product.name,
                quantity = qty,
                unitPriceOriginal = product.price,
                originalSubtotal = MoneyCents(origSub),
                discountAmount = MoneyCents.ZERO,
                finalAmount = MoneyCents(origSub),
                isSelectedForDiscount = false,
            )
        }

        // Consolida participação original por pessoa em toda a venda
        data class PersonBaseAcc(
            val personId: String,
            val personName: String,
            val personColorIndex: Int,
            var originalTotalCents: Long = 0L,
        )

        val basePeople = LinkedHashMap<String, PersonBaseAcc>()
        selectedEntries.forEach { (product, qty) ->
            product.participants.forEach { split ->
                val lineOrig = MonetaryRounding.safeMultiplyOrNull(split.amount.rawCents, qty) ?: 0L
                val acc = basePeople.getOrPut(split.personId) {
                    PersonBaseAcc(split.personId, split.personName, split.personColorIndex)
                }
                acc.originalTotalCents = MonetaryRounding.safeAddOrNull(acc.originalTotalCents, lineOrig)
                    ?: acc.originalTotalCents
            }
        }

        val computedPersonTotals = calcResult?.personTotals?.associateBy { it.personId }.orEmpty()

        val allPersonLines = basePeople.values
            .sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )
            .map { acc ->
                val computed = computedPersonTotals[acc.personId]
                val afterGross = computed?.grossAmount?.rawCents ?: acc.originalTotalCents
                val absorbed = (acc.originalTotalCents - afterGross).coerceAtLeast(0L)
                val fee = computed?.feeAmount?.rawCents ?: 0L
                val net = computed?.netAmount?.rawCents ?: afterGross
                val inSelected = acc.personId in peopleInSelectedProducts
                DiscountPreviewPersonLine(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    beforeDiscount = MoneyCents(acc.originalTotalCents),
                    discountAbsorbed = MoneyCents(absorbed),
                    afterDiscountGross = MoneyCents(afterGross),
                    feeAmount = MoneyCents(fee),
                    netAmount = MoneyCents(net),
                    isProtected = inSelected && acc.personId in protectedPersonIds,
                    isFixedUnitShare = inSelected && acc.personId in fixedPersonIds,
                    participatesInSelectedProducts = inSelected,
                )
            }

        val protectedPeople = allPersonLines.filter { it.participatesInSelectedProducts && it.isProtected }
        val eligiblePeople = allPersonLines.filter { it.participatesInSelectedProducts && !it.isProtected }

        val selectedFinalCents = if (calcResult != null) {
            selectedProductLines.sumOf { it.finalAmount.rawCents }
        } else {
            (selectedOriginalCents - discountCents).coerceAtLeast(0L)
        }

        return VenderDiscountPreview(
            selectedProducts = selectedProductLines,
            unselectedProducts = unselectedProductLines,
            selectedOriginalSubtotal = MoneyCents(selectedOriginalCents),
            discountTotal = MoneyCents(discountCents),
            selectedFinalAmount = MoneyCents(selectedFinalCents),
            protectedPeople = protectedPeople,
            eligiblePeople = eligiblePeople,
            allPersonBreakdowns = allPersonLines,
            calculationResult = calcResult,
            validationError = errorMsg,
        )
    }
}
