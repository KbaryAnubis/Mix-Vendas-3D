package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.util.RateFormatter

class SalePersistenceException(message: String) : IllegalArgumentException(message)

/**
 * Valida integralmente um [SaleCalculationResult] antes de iniciar qualquer transação no Room,
 * garantindo que valores negativos, duplicados, erros de soma de centavos ou metadados
 * inconsistentes sejam rejeitados antes de qualquer escrita em disco.
 */
object SalePersistenceValidator {

    fun validateForPersistence(result: SaleCalculationResult) {
        try {
            validateInternal(result)
        } catch (ex: FinancialOverflowException) {
            throw SalePersistenceException(
                ex.message ?: "Estouro numérico detectado ao validar snapshot da venda.",
            )
        }
    }

    private fun validateInternal(result: SaleCalculationResult) {
        if (result.items.isEmpty()) {
            throw SalePersistenceException("A venda precisa conter pelo menos um item para ser gravada.")
        }
        if (result.personTotals.isEmpty()) {
            throw SalePersistenceException("A venda precisa conter o total consolidado de pelo menos uma pessoa.")
        }

        if (result.originalSubtotal.rawCents <= 0L) {
            throw SalePersistenceException("Subtotal original da venda deve ser maior que zero.")
        }
        if (result.discountTotal.rawCents < 0L) {
            throw SalePersistenceException("Desconto total da venda não pode ser negativo.")
        }
        if (result.finalAmount.rawCents <= 0L) {
            throw SalePersistenceException("Valor final da venda deve ser maior que zero.")
        }
        if (result.feeTotal.rawCents < 0L) {
            throw SalePersistenceException("Tarifa total da venda não pode ser negativa.")
        }
        if (result.netTotal.rawCents < 0L) {
            throw SalePersistenceException("Líquido total da venda não pode ser negativo.")
        }
        if (result.effectiveFeeRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
            throw SalePersistenceException("Taxa efetiva da venda fora do intervalo permitido (0% a 100%).")
        }

        validatePaymentAndMachineSnapshot(result)

        val seenProductIds = HashSet<String>(result.items.size)
        val personAccumulated = linkedMapOf<String, PersonAccum>()
        var itemsOriginalSubtotalSum = 0L
        var itemsDiscountSum = 0L
        var itemsFinalSum = 0L
        var itemsFeeSum = 0L
        var itemsNetSum = 0L

        result.items.forEach { item ->
            if (item.productId.isBlank()) {
                throw SalePersistenceException("Item da venda sem identificador de produto.")
            }
            if (item.productName.isBlank()) {
                throw SalePersistenceException("Item da venda sem nome de produto.")
            }
            if (!seenProductIds.add(item.productId)) {
                throw SalePersistenceException("Produto duplicado no snapshot da venda (${item.productName}).")
            }
            if (item.quantity !in SaleFinancialEngine.MIN_ITEM_QUANTITY..SaleFinancialEngine.MAX_ITEM_QUANTITY) {
                throw SalePersistenceException("Quantidade inválida no item \"${item.productName}\": ${item.quantity}.")
            }
            if (item.unitPriceOriginal.rawCents <= 0L) {
                throw SalePersistenceException("Preço unitário original inválido no item \"${item.productName}\".")
            }

            val expectedOriginalSubtotal = MonetaryRounding.safeMultiply(item.unitPriceOriginal.rawCents, item.quantity)
            if (item.originalSubtotal.rawCents != expectedOriginalSubtotal) {
                throw SalePersistenceException(
                    "Subtotal original inconsistente no item \"${item.productName}\".",
                )
            }
            if (item.discountAmount.rawCents < 0L || item.discountAmount.rawCents >= item.originalSubtotal.rawCents) {
                throw SalePersistenceException(
                    "Desconto inválido no item \"${item.productName}\".",
                )
            }
            val expectedItemFinal = MonetaryRounding.safeSubtract(
                item.originalSubtotal.rawCents,
                item.discountAmount.rawCents,
            )
            if (item.finalAmount.rawCents <= 0L || item.finalAmount.rawCents != expectedItemFinal) {
                throw SalePersistenceException(
                    "Valor final inconsistente no item \"${item.productName}\".",
                )
            }
            if (item.appliedFeeRateBps != result.effectiveFeeRateBps) {
                throw SalePersistenceException(
                    "Taxa aplicada no item \"${item.productName}\" difere da taxa efetiva da venda.",
                )
            }
            if (item.feeAmount.rawCents < 0L || item.netAmount.rawCents < 0L) {
                throw SalePersistenceException(
                    "Tarifa ou líquido negativo no item \"${item.productName}\".",
                )
            }
            val expectedFee = MonetaryRounding.applyBasisPointsHalfUp(
                amount = MoneyCents(item.finalAmount.rawCents),
                basisPoints = item.appliedFeeRateBps,
            ).rawCents
            if (item.feeAmount.rawCents != expectedFee) {
                throw SalePersistenceException(
                    "Tarifa divergente do cálculo determinístico no item \"${item.productName}\".",
                )
            }
            val expectedItemNet = MonetaryRounding.safeSubtract(
                item.finalAmount.rawCents,
                item.feeAmount.rawCents,
            )
            if (item.netAmount.rawCents != expectedItemNet) {
                throw SalePersistenceException(
                    "Líquido inconsistente no item \"${item.productName}\".",
                )
            }

            if (item.participantSplits.isEmpty()) {
                throw SalePersistenceException(
                    "O item \"${item.productName}\" não possui participantes registrados.",
                )
            }

            val seenItemPeople = HashSet<String>(item.participantSplits.size)
            var splitsOrigSubtotalSum = 0L
            var splitsDiscountSum = 0L
            var splitsGrossSum = 0L
            var splitsFeeSum = 0L
            var splitsNetSum = 0L

            item.participantSplits.forEach { split ->
                if (split.personId.isBlank() || split.personName.isBlank()) {
                    throw SalePersistenceException(
                        "Participante inválido no item \"${item.productName}\".",
                    )
                }
                if (!seenItemPeople.add(split.personId)) {
                    throw SalePersistenceException(
                        "Participante duplicado no item \"${item.productName}\".",
                    )
                }
                if (split.originalUnitAmount.rawCents < 0L ||
                    split.originalSubtotal.rawCents < 0L ||
                    split.discountAmount.rawCents < 0L ||
                    split.grossAmount.rawCents < 0L ||
                    split.feeAmount.rawCents < 0L ||
                    split.netAmount.rawCents < 0L
                ) {
                    throw SalePersistenceException(
                        "Valor negativo encontrado para participante \"${split.personName}\" no item \"${item.productName}\".",
                    )
                }
                val expectedParticipantOrigSub = MonetaryRounding.safeMultiply(
                    split.originalUnitAmount.rawCents,
                    item.quantity,
                )
                if (split.originalSubtotal.rawCents != expectedParticipantOrigSub) {
                    throw SalePersistenceException(
                        "Subtotal original inconsistente para participante \"${split.personName}\" no item \"${item.productName}\".",
                    )
                }
                if (MonetaryRounding.safeSubtract(split.originalSubtotal.rawCents, split.discountAmount.rawCents) != split.grossAmount.rawCents) {
                    throw SalePersistenceException(
                        "Desconto inconsistente para participante \"${split.personName}\" no item \"${item.productName}\".",
                    )
                }
                if (MonetaryRounding.safeSubtract(split.grossAmount.rawCents, split.feeAmount.rawCents) != split.netAmount.rawCents) {
                    throw SalePersistenceException(
                        "Líquido inconsistente para participante \"${split.personName}\" no item \"${item.productName}\".",
                    )
                }

                splitsOrigSubtotalSum = MonetaryRounding.safeAdd(splitsOrigSubtotalSum, split.originalSubtotal.rawCents)
                splitsDiscountSum = MonetaryRounding.safeAdd(splitsDiscountSum, split.discountAmount.rawCents)
                splitsGrossSum = MonetaryRounding.safeAdd(splitsGrossSum, split.grossAmount.rawCents)
                splitsFeeSum = MonetaryRounding.safeAdd(splitsFeeSum, split.feeAmount.rawCents)
                splitsNetSum = MonetaryRounding.safeAdd(splitsNetSum, split.netAmount.rawCents)

                val accum = personAccumulated.getOrPut(split.personId) {
                    PersonAccum(personId = split.personId, personName = split.personName)
                }
                if (accum.personName != split.personName) {
                    throw SalePersistenceException(
                        "Nome inconsistente para a pessoa \"${split.personId}\" entre os itens da venda.",
                    )
                }
                accum.grossCents = MonetaryRounding.safeAdd(accum.grossCents, split.grossAmount.rawCents)
                accum.feeCents = MonetaryRounding.safeAdd(accum.feeCents, split.feeAmount.rawCents)
                accum.netCents = MonetaryRounding.safeAdd(accum.netCents, split.netAmount.rawCents)
            }

            if (item.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL) {
                val ownerId = item.feeOwnerPersonId?.takeIf { it.isNotBlank() }
                    ?: throw SalePersistenceException("Item \"${item.productName}\" em modo INDIVIDUAL exige responsável pela tarifa.")
                if (!seenItemPeople.contains(ownerId)) {
                    throw SalePersistenceException("Responsável pela tarifa do item \"${item.productName}\" não está entre os participantes.")
                }
                item.participantSplits.forEach { split ->
                    if (split.personId != ownerId && split.feeAmount.rawCents != 0L) {
                        throw SalePersistenceException(
                            "Item \"${item.productName}\" em modo INDIVIDUAL possui tarifa atribuída a participante diferente do responsável.",
                        )
                    }
                }
            }

            if (splitsOrigSubtotalSum != item.originalSubtotal.rawCents ||
                splitsDiscountSum != item.discountAmount.rawCents ||
                splitsGrossSum != item.finalAmount.rawCents ||
                splitsFeeSum != item.feeAmount.rawCents ||
                splitsNetSum != item.netAmount.rawCents
            ) {
                throw SalePersistenceException(
                    "As parcelas dos participantes não fecham exatamente com os totais do item \"${item.productName}\".",
                )
            }

            itemsOriginalSubtotalSum = MonetaryRounding.safeAdd(itemsOriginalSubtotalSum, item.originalSubtotal.rawCents)
            itemsDiscountSum = MonetaryRounding.safeAdd(itemsDiscountSum, item.discountAmount.rawCents)
            itemsFinalSum = MonetaryRounding.safeAdd(itemsFinalSum, item.finalAmount.rawCents)
            itemsFeeSum = MonetaryRounding.safeAdd(itemsFeeSum, item.feeAmount.rawCents)
            itemsNetSum = MonetaryRounding.safeAdd(itemsNetSum, item.netAmount.rawCents)
        }

        validateDiscountConfig(result)

        // Igualdades matemáticas obrigatórias da venda
        if (MonetaryRounding.safeSubtract(result.originalSubtotal.rawCents, result.discountTotal.rawCents) != result.finalAmount.rawCents) {
            throw SalePersistenceException("Inconsistência: subtotal original - desconto total != valor final.")
        }
        if (itemsOriginalSubtotalSum != result.originalSubtotal.rawCents) {
            throw SalePersistenceException("Inconsistência: soma dos subtotais originais dos itens != subtotal original.")
        }
        if (itemsDiscountSum != result.discountTotal.rawCents) {
            throw SalePersistenceException("Inconsistência: soma dos descontos dos itens != desconto total.")
        }
        if (itemsFinalSum != result.finalAmount.rawCents) {
            throw SalePersistenceException("Inconsistência: soma dos valores finais dos itens != valor final.")
        }
        if (itemsFeeSum != result.feeTotal.rawCents) {
            throw SalePersistenceException("Inconsistência: soma das tarifas dos itens != tarifa total.")
        }
        if (itemsNetSum != result.netTotal.rawCents) {
            throw SalePersistenceException("Inconsistência: soma dos líquidos dos itens != líquido total.")
        }
        if (MonetaryRounding.safeSubtract(result.finalAmount.rawCents, result.feeTotal.rawCents) != result.netTotal.rawCents) {
            throw SalePersistenceException("Inconsistência: valor final - tarifa total != líquido total.")
        }

        // Consistência estrita de personTotals
        val seenTotalsPeople = HashSet<String>(result.personTotals.size)
        var personGrossSum = 0L
        var personFeeSum = 0L
        var personNetSum = 0L

        result.personTotals.forEach { total ->
            if (total.personId.isBlank() || total.personName.isBlank()) {
                throw SalePersistenceException("Total por pessoa possui identificação inválida.")
            }
            if (!seenTotalsPeople.add(total.personId)) {
                throw SalePersistenceException("Pessoa duplicada nos totais da venda (${total.personName}).")
            }
            if (total.grossAmount.rawCents < 0L || total.feeAmount.rawCents < 0L || total.netAmount.rawCents < 0L) {
                throw SalePersistenceException("Total negativo encontrado para \"${total.personName}\".")
            }
            if (MonetaryRounding.safeSubtract(total.grossAmount.rawCents, total.feeAmount.rawCents) != total.netAmount.rawCents) {
                throw SalePersistenceException("Líquido inconsistente nos totais de \"${total.personName}\".")
            }

            val expected = personAccumulated[total.personId]
                ?: throw SalePersistenceException("Pessoa \"${total.personName}\" nos totais não participa de nenhum item.")
            if (total.grossAmount.rawCents != expected.grossCents ||
                total.feeAmount.rawCents != expected.feeCents ||
                total.netAmount.rawCents != expected.netCents
            ) {
                throw SalePersistenceException("Totais de \"${total.personName}\" não correspondem à soma exata dos itens.")
            }

            personGrossSum = MonetaryRounding.safeAdd(personGrossSum, total.grossAmount.rawCents)
            personFeeSum = MonetaryRounding.safeAdd(personFeeSum, total.feeAmount.rawCents)
            personNetSum = MonetaryRounding.safeAdd(personNetSum, total.netAmount.rawCents)
        }

        if (seenTotalsPeople.size != personAccumulated.size) {
            throw SalePersistenceException("Conjunto de pessoas nos totais difere dos participantes dos itens.")
        }
        if (personGrossSum != result.finalAmount.rawCents) {
            throw SalePersistenceException("Inconsistência: soma dos brutos por pessoa != valor final.")
        }
        if (personFeeSum != result.feeTotal.rawCents) {
            throw SalePersistenceException("Inconsistência: soma das tarifas por pessoa != tarifa total.")
        }
        if (personNetSum != result.netTotal.rawCents) {
            throw SalePersistenceException("Inconsistência: soma dos líquidos por pessoa != líquido total.")
        }
    }

    private fun validatePaymentAndMachineSnapshot(result: SaleCalculationResult) {
        when (result.paymentMethod) {
            SalePaymentMethod.CASH,
            SalePaymentMethod.PIX -> {
                if (result.machineSnapshot != null) {
                    throw SalePersistenceException("Pagamento em ${result.paymentMethod.label} não pode conter snapshot de maquininha.")
                }
                if (result.effectiveFeeRateBps != 0 || result.feeTotal.rawCents != 0L) {
                    throw SalePersistenceException("Pagamento em ${result.paymentMethod.label} deve possuir taxa e tarifa zeradas.")
                }
            }

            SalePaymentMethod.DEBIT,
            SalePaymentMethod.CREDIT -> {
                val snapshot = result.machineSnapshot
                    ?: throw SalePersistenceException("Pagamento em ${result.paymentMethod.label} exige snapshot completo da maquininha.")
                if (snapshot.machineId.isBlank() || snapshot.machineName.isBlank()) {
                    throw SalePersistenceException("Snapshot da maquininha inválido.")
                }
                if (snapshot.debitRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS ||
                    snapshot.creditDefaultRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS ||
                    snapshot.automaticRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS ||
                    snapshot.effectiveRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS
                ) {
                    throw SalePersistenceException("Taxas da maquininha no snapshot estão fora do intervalo permitido.")
                }
                val manual = snapshot.manualRateOverrideBps
                if (manual != null && manual !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                    throw SalePersistenceException("Tarifa manual da maquininha fora do intervalo permitido.")
                }
                if (snapshot.effectiveRateBps != (manual ?: snapshot.automaticRateBps)) {
                    throw SalePersistenceException("Taxa efetiva do snapshot da maquininha está inconsistente.")
                }
                if (snapshot.effectiveRateBps != result.effectiveFeeRateBps) {
                    throw SalePersistenceException("Taxa efetiva do snapshot da maquininha difere da venda.")
                }
                val resolvedBrandKey = snapshot.resolvedAppliedBrandKey
                if (resolvedBrandKey != null) {
                    if (!CustomBrandKeyCodec.isKnownBrandKey(resolvedBrandKey)) {
                        throw SalePersistenceException("Chave de bandeira inválida no snapshot da venda: $resolvedBrandKey.")
                    }
                    val expectedStandard = CardBrand.fromStorageKey(resolvedBrandKey)
                    if (snapshot.appliedBrand != null && snapshot.appliedBrand.storageKey != resolvedBrandKey) {
                        throw SalePersistenceException("Bandeira aplicada inconsistente no snapshot da venda.")
                    }
                    if (snapshot.appliedBrand == null && expectedStandard != null) {
                        throw SalePersistenceException("Bandeira padrão inconsistente no snapshot da venda.")
                    }
                }
                if (result.paymentMethod == SalePaymentMethod.DEBIT) {
                    if (snapshot.appliedInstallments != null) {
                        throw SalePersistenceException("Pagamento em Débito não deve possuir parcelamento aplicado.")
                    }
                    if (!snapshot.brandRatesEnabled && (snapshot.appliedBrand != null || resolvedBrandKey != null)) {
                        throw SalePersistenceException("Pagamento em Débito sem taxas por bandeira não deve possuir bandeira aplicada.")
                    }
                } else {
                    if (snapshot.brandRatesEnabled && resolvedBrandKey.isNullOrBlank()) {
                        throw SalePersistenceException("Snapshot em Crédito com taxas por bandeira exige a bandeira aplicada.")
                    }
                    val installments = snapshot.appliedInstallments
                        ?: throw SalePersistenceException("Snapshot em Crédito exige quantidade de parcelas aplicada.")
                    if (snapshot.installmentsEnabled && installments !in InstallmentTiers.SUPPORTED) {
                        throw SalePersistenceException("Quantidade de parcelas inválida no snapshot: $installments.")
                    }
                    if (!snapshot.installmentsEnabled && installments != 1) {
                        throw SalePersistenceException("Crédito com parcelamento desativado deve registrar 1x.")
                    }
                }
            }
        }
    }

    private fun validateDiscountConfig(result: SaleCalculationResult) {
        when (val config = result.discountConfig) {
            is SaleDiscountConfigSnapshot.Proportional -> Unit
            is SaleDiscountConfigSnapshot.SpecificProduct -> {
                if (config.productId.isBlank() || config.productNameSnapshot.isBlank()) {
                    throw SalePersistenceException("Desconto em produto específico exige identificador e nome do produto.")
                }
                val targetItem = result.items.firstOrNull { it.productId == config.productId }
                    ?: throw SalePersistenceException("Produto alvo do desconto específico não pertence aos itens da venda.")
                if (targetItem.discountAmount.rawCents != result.discountTotal.rawCents) {
                    throw SalePersistenceException("Desconto específico no produto alvo difere do desconto total da venda.")
                }
                result.items.forEach { item ->
                    if (item.productId != config.productId && item.discountAmount.rawCents != 0L) {
                        throw SalePersistenceException("Outro item recebeu desconto apesar do modo específico.")
                    }
                }
            }
            is SaleDiscountConfigSnapshot.SelectedProducts -> {
                if (config.productIds.isEmpty() || config.productIds.any { it.isBlank() }) {
                    throw SalePersistenceException("Desconto em produtos selecionados exige ao menos um produto válido.")
                }
                val targetSet = config.productIds.toSet()
                if (targetSet.size != config.productIds.size) {
                    throw SalePersistenceException("Lista de produtos selecionados para desconto contém IDs duplicados.")
                }
                val itemIds = result.items.mapTo(HashSet(result.items.size)) { it.productId }
                if (!itemIds.containsAll(targetSet)) {
                    throw SalePersistenceException("Um ou mais produtos selecionados para desconto não pertencem aos itens da venda.")
                }
                var selectedDiscountSum = 0L
                result.items.forEach { item ->
                    if (item.productId in targetSet) {
                        selectedDiscountSum = MonetaryRounding.safeAdd(selectedDiscountSum, item.discountAmount.rawCents)
                    } else if (item.discountAmount.rawCents != 0L) {
                        throw SalePersistenceException("Produto não selecionado recebeu desconto indevidamente (${item.productName}).")
                    }
                }
                if (selectedDiscountSum != result.discountTotal.rawCents) {
                    throw SalePersistenceException("Soma dos descontos nos produtos selecionados difere do desconto total da venda.")
                }
            }
        }
    }

    private class PersonAccum(
        val personId: String,
        val personName: String,
        var grossCents: Long = 0L,
        var feeCents: Long = 0L,
        var netCents: Long = 0L,
    )
}
