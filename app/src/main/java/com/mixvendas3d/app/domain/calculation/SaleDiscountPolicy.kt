package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import java.util.Locale

enum class DiscountPeopleSelectionMode {
    UNIFIED,
    INDIVIDUAL_BY_PRODUCT,
}

data class DiscountUnifiedPersonOption(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val participatingProductIds: List<String>,
    val participatingProductNames: List<String>,
    val totalOriginalSubtotal: MoneyCents,
    val isSelected: Boolean,
)

data class DiscountProductPersonOption(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val unitAmount: MoneyCents,
    val lineOriginalSubtotal: MoneyCents,
    val isSelected: Boolean,
)

data class DiscountProductPeopleGroup(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val originalSubtotal: MoneyCents,
    val people: List<DiscountProductPersonOption>,
) {
    val selectedCount: Int
        get() = people.count { it.isSelected }

    val totalCount: Int
        get() = people.size
}

data class DiscountPreviewProductPersonLine(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val originalShare: MoneyCents,
    val discountAbsorbed: MoneyCents,
    val finalShare: MoneyCents,
    val isSelectedForDiscount: Boolean,
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
    val selectedRecipients: List<DiscountPreviewProductPersonLine> = emptyList(),
    val unselectedParticipants: List<DiscountPreviewProductPersonLine> = emptyList(),
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
    val receivesDiscountInAnyProduct: Boolean,
    val participatesInSelectedProducts: Boolean,
)

data class VenderDiscountPreview(
    val selectedProducts: List<DiscountPreviewProductLine>,
    val unselectedProducts: List<DiscountPreviewProductLine>,
    val selectedOriginalSubtotal: MoneyCents,
    val discountTotal: MoneyCents,
    val selectedFinalAmount: MoneyCents,
    val allPersonBreakdowns: List<DiscountPreviewPersonLine>,
    val calculationResult: SaleCalculationResult?,
    val validationError: String?,
) {
    val isValid: Boolean
        get() = validationError == null && calculationResult != null
}

/**
 * Regras centralizadas de política de desconto por produto e por pessoa:
 * - Seleção independente das pessoas que RECEBERÃO o desconto dentro de cada produto marcado.
 * - Remoção completa de proteção automática por valor fixo (R$ 5,00/un).
 * - Reconstrução determinística das seleções por produto ao reabrir uma venda salva para edição.
 * - Construção da prévia detalhada em tempo real diretamente sobre o [SaleFinancialEngine].
 */
object SaleDiscountPolicy {
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * Constrói os grupos independentes por produto selecionado na Caixa 1,
     * listando as pessoas participantes de cada produto com seus respectivos valores originais
     * e estado de marcação independente por produto.
     */
    fun buildProductPeopleGroups(
        selectedDiscountEntries: List<Pair<Product, Int>>,
        selectedPeopleByProduct: Map<String, Set<String>>,
    ): List<DiscountProductPeopleGroup> {
        return selectedDiscountEntries
            .filter { it.second > 0 }
            .map { (product, qty) ->
                val selectedSet = selectedPeopleByProduct[product.id].orEmpty()
                val lineSubtotal = MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, qty) ?: 0L
                val options = product.participants
                    .sortedWith(
                        Comparator { a, b ->
                            val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                            if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                        },
                    )
                    .map { split ->
                        val lineOrig = MonetaryRounding.safeMultiplyOrNull(split.amount.rawCents, qty) ?: 0L
                        DiscountProductPersonOption(
                            personId = split.personId,
                            personName = split.personName,
                            personColorIndex = split.personColorIndex,
                            unitAmount = split.amount,
                            lineOriginalSubtotal = MoneyCents(lineOrig),
                            isSelected = split.personId in selectedSet,
                        )
                    }
                DiscountProductPeopleGroup(
                    productId = product.id,
                    productName = product.name,
                    quantity = qty,
                    originalSubtotal = MoneyCents(lineSubtotal),
                    people = options,
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
     * Constrói a lista unificada e sem repetições das pessoas participantes dos produtos
     * selecionados para desconto, preservando o valor original acumulado e os produtos vinculados.
     */
    fun buildUnifiedPeopleOptions(
        selectedDiscountEntries: List<Pair<Product, Int>>,
        unifiedSelectedPersonIds: Set<String>,
    ): List<DiscountUnifiedPersonOption> {
        data class Acc(
            val personId: String,
            val personName: String,
            val personColorIndex: Int,
            val productIds: MutableList<String> = mutableListOf(),
            val productNames: MutableList<String> = mutableListOf(),
            var totalOriginalCents: Long = 0L,
        )

        val byPerson = LinkedHashMap<String, Acc>()
        selectedDiscountEntries
            .filter { it.second > 0 }
            .forEach { (product, qty) ->
                product.participants.forEach { split ->
                    val lineOrig = MonetaryRounding.safeMultiplyOrNull(split.amount.rawCents, qty) ?: 0L
                    val acc = byPerson.getOrPut(split.personId) {
                        Acc(
                            personId = split.personId,
                            personName = split.personName,
                            personColorIndex = split.personColorIndex,
                        )
                    }
                    if (product.id !in acc.productIds) {
                        acc.productIds.add(product.id)
                        acc.productNames.add(product.name)
                    }
                    acc.totalOriginalCents = MonetaryRounding.safeAddOrNull(acc.totalOriginalCents, lineOrig)
                        ?: acc.totalOriginalCents
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
                DiscountUnifiedPersonOption(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    participatingProductIds = acc.productIds.toList(),
                    participatingProductNames = acc.productNames.toList(),
                    totalOriginalSubtotal = MoneyCents(acc.totalOriginalCents),
                    isSelected = acc.personId in unifiedSelectedPersonIds,
                )
            }
    }

    /**
     * Resolve o mapa efetivo `productId -> Set<personId>` de acordo com o modo ativo
     * (`UNIFIED` ou `INDIVIDUAL_BY_PRODUCT`), garantindo que cada pessoa só seja vinculada
     * aos produtos selecionados em que realmente participa.
     */
    fun resolveEffectiveRecipientsByProduct(
        mode: DiscountPeopleSelectionMode,
        selectedDiscountProducts: List<Product>,
        unifiedSelectedPersonIds: Set<String>,
        individualSelectedPeopleByProduct: Map<String, Set<String>>,
    ): Map<String, Set<String>> {
        val result = LinkedHashMap<String, Set<String>>(selectedDiscountProducts.size)
        selectedDiscountProducts.forEach { product ->
            val validParticipants = product.participants.mapTo(LinkedHashSet()) { it.personId }
            result[product.id] = when (mode) {
                DiscountPeopleSelectionMode.UNIFIED ->
                    validParticipants.intersect(unifiedSelectedPersonIds)
                DiscountPeopleSelectionMode.INDIVIDUAL_BY_PRODUCT ->
                    individualSelectedPeopleByProduct[product.id]
                        ?.intersect(validParticipants)
                        ?: validParticipants
            }
        }
        return result
    }

    /**
     * Infere o modo de seleção de pessoas (`UNIFIED` vs `INDIVIDUAL_BY_PRODUCT`) a partir de uma
     * venda salva. Se todas as pessoas presentes nos produtos com desconto tiveram a mesma escolha
     * em todos os produtos selecionados onde participam, restaura em `UNIFIED`; caso haja seleção
     * divergente entre produtos, restaura em `INDIVIDUAL_BY_PRODUCT`.
     */
    fun inferDiscountSelectionModeFromSnapshot(
        calc: SaleCalculationResult,
        selectedDiscountProductIds: Set<String> = selectedProductIdsFromSnapshot(calc),
        recipientsByProduct: Map<String, Set<String>> = inferDiscountRecipientsByProductFromSnapshot(
            calc = calc,
            selectedDiscountProductIds = selectedDiscountProductIds,
        ),
    ): Pair<DiscountPeopleSelectionMode, Set<String>> {
        val discountItems = calc.items.filter { it.productId in selectedDiscountProductIds }
        if (discountItems.isEmpty()) {
            val allParticipants = calc.items
                .flatMap { it.participantSplits }
                .mapTo(LinkedHashSet()) { it.personId }
            return DiscountPeopleSelectionMode.UNIFIED to allParticipants
        }

        val selectionsByPerson = LinkedHashMap<String, MutableList<Boolean>>()
        val unionSelected = LinkedHashSet<String>()
        discountItems.forEach { item ->
            val selectedForItem = recipientsByProduct[item.productId].orEmpty()
            item.participantSplits.forEach { sp ->
                val isSelected = sp.personId in selectedForItem
                if (isSelected) {
                    unionSelected.add(sp.personId)
                }
                selectionsByPerson.getOrPut(sp.personId) { mutableListOf() }.add(isSelected)
            }
        }

        val hasMixedChoiceAcrossProducts = selectionsByPerson.values.any { choices ->
            choices.any { it } && choices.any { !it }
        }
        val mode = if (hasMixedChoiceAcrossProducts) {
            DiscountPeopleSelectionMode.INDIVIDUAL_BY_PRODUCT
        } else {
            DiscountPeopleSelectionMode.UNIFIED
        }
        return mode to unionSelected
    }

    /**
     * Reconstrói deterministicamente o mapa `productId -> Set<personId>` das pessoas que
     * receberam desconto em cada produto a partir dos snapshots reais gravados em [SaleCalculationResult.items].
     */
    fun inferDiscountRecipientsByProductFromSnapshot(
        calc: SaleCalculationResult,
        selectedDiscountProductIds: Set<String> = selectedProductIdsFromSnapshot(calc),
    ): Map<String, Set<String>> {
        val result = LinkedHashMap<String, Set<String>>(calc.items.size)
        calc.items.forEach { item: SaleItemCalculationResult ->
            if (item.productId !in selectedDiscountProductIds) {
                result[item.productId] = item.participantSplits.mapTo(LinkedHashSet()) { it.personId }
            } else if (item.discountAmount.rawCents > 0L) {
                val recipients = item.participantSplits
                    .filter { it.discountAmount.rawCents > 0L }
                    .mapTo(LinkedHashSet()) { it.personId }
                result[item.productId] = recipients
            } else {
                result[item.productId] = item.participantSplits.mapTo(LinkedHashSet()) { it.personId }
            }
        }
        return result
    }

    /**
     * Constrói a prévia financeira completa antes de salvar a venda, usando o próprio
     * [SaleFinancialEngine.calculate] como única fonte da verdade para cálculos e validações.
     */
    fun buildPreview(
        selectedEntries: List<Pair<Product, Int>>,
        discountSelectedProductIds: Set<String>,
        selectedPeopleByProduct: Map<String, Set<String>>,
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

        val peopleInSelectedProducts = LinkedHashSet<String>()
        selectedEntryList.forEach { (product, _) ->
            product.participants.forEach { split ->
                peopleInSelectedProducts.add(split.personId)
            }
        }

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
        val personIdsReceivingAnyDiscount = LinkedHashSet<String>()

        val selectedProductLines = selectedEntryList.map { (product, qty) ->
            val origSub = MonetaryRounding.safeMultiplyOrNull(product.price.rawCents, qty) ?: 0L
            val computed = itemResultById[product.id]
            val selectedIdsInProduct = selectedPeopleByProduct[product.id].orEmpty()
            val computedSplitsByPerson = computed?.participantSplits?.associateBy { it.personId }.orEmpty()

            val orderedParticipants = product.participants.sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )

            val personLinesForProduct = orderedParticipants.map { split ->
                val lineOrig = MonetaryRounding.safeMultiplyOrNull(split.amount.rawCents, qty) ?: 0L
                val computedSplit = computedSplitsByPerson[split.personId]
                val disc = computedSplit?.discountAmount?.rawCents ?: 0L
                val finalShare = computedSplit?.grossAmount?.rawCents ?: lineOrig
                val isSelected = split.personId in selectedIdsInProduct
                if (isSelected && disc > 0L) {
                    personIdsReceivingAnyDiscount.add(split.personId)
                }
                DiscountPreviewProductPersonLine(
                    personId = split.personId,
                    personName = split.personName,
                    personColorIndex = split.personColorIndex,
                    originalShare = MoneyCents(lineOrig),
                    discountAbsorbed = MoneyCents(disc),
                    finalShare = MoneyCents(finalShare),
                    isSelectedForDiscount = isSelected,
                )
            }

            DiscountPreviewProductLine(
                productId = product.id,
                productName = product.name,
                quantity = qty,
                unitPriceOriginal = product.price,
                originalSubtotal = MoneyCents(origSub),
                discountAmount = computed?.discountAmount ?: MoneyCents.ZERO,
                finalAmount = computed?.finalAmount ?: MoneyCents(origSub),
                isSelectedForDiscount = true,
                selectedRecipients = personLinesForProduct.filter { it.isSelectedForDiscount },
                unselectedParticipants = personLinesForProduct.filter { !it.isSelectedForDiscount },
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
                val receivesAnyDiscount = inSelected && (
                    acc.personId in personIdsReceivingAnyDiscount ||
                        selectedEntryList.any { (prod, _) ->
                            acc.personId in selectedPeopleByProduct[prod.id].orEmpty()
                        }
                    )
                DiscountPreviewPersonLine(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    beforeDiscount = MoneyCents(acc.originalTotalCents),
                    discountAbsorbed = MoneyCents(absorbed),
                    afterDiscountGross = MoneyCents(afterGross),
                    feeAmount = MoneyCents(fee),
                    netAmount = MoneyCents(net),
                    receivesDiscountInAnyProduct = receivesAnyDiscount,
                    participatesInSelectedProducts = inSelected,
                )
            }

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
            allPersonBreakdowns = allPersonLines,
            calculationResult = calcResult,
            validationError = errorMsg,
        )
    }
}
