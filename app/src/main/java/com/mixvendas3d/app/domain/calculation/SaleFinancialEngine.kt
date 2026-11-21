package com.mixvendas3d.app.domain.calculation

import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SaleItemParticipantSnapshot
import com.mixvendas3d.app.domain.models.SaleMachineSnapshot
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter
import java.util.Locale

class SaleCalculationException(message: String) : IllegalArgumentException(message)

/**
 * Motor financeiro definitivo do MIX VENDAS 3D (Etapa 3A).
 * Independente de Compose e Room: executa cálculos determinísticos em centavos inteiros (Long)
 * e pontos-base (Int), retornando snapshots imutáveis prontos para a futura Etapa 3B.
 */
object SaleFinancialEngine {
    const val MIN_ITEM_QUANTITY = 1
    const val MAX_ITEM_QUANTITY = 99
    private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

    fun calculate(input: SaleCalculationInput): SaleCalculationResult {
        return try {
            calculateInternal(input)
        } catch (ex: FinancialOverflowException) {
            throw SaleCalculationException(
                ex.message ?: "Valor monetário excede o limite suportado pelo cálculo.",
            )
        }
    }

    private fun calculateInternal(input: SaleCalculationInput): SaleCalculationResult {
        if (input.items.isEmpty()) {
            throw SaleCalculationException("Selecione pelo menos um produto para calcular a venda.")
        }

        // 1. Consolida produtos duplicados pela quantidade e valida integridade dos itens
        val consolidatedLines = consolidateAndValidateItems(input.items)

        // 2. Calcula subtotal original da venda
        var originalSubtotalCents = 0L
        val lineSubtotals = LinkedHashMap<String, Long>(consolidatedLines.size)
        consolidatedLines.forEach { line ->
            val subtotal = MonetaryRounding.safeMultiply(line.product.price.rawCents, line.quantity)
            lineSubtotals[line.product.id] = subtotal
            originalSubtotalCents = MonetaryRounding.safeAdd(originalSubtotalCents, subtotal)
        }

        // 3. Valida valor final e desconto total
        val targetFinalCents = input.finalAmountOverride?.rawCents ?: originalSubtotalCents
        if (targetFinalCents <= 0L) {
            throw SaleCalculationException("O valor final da venda deve ser maior que zero.")
        }
        if (targetFinalCents > originalSubtotalCents) {
            throw SaleCalculationException(
                "O valor final (${CurrencyFormatter.formatBrl(MoneyCents(targetFinalCents))}) não pode ultrapassar o subtotal original (${CurrencyFormatter.formatBrl(MoneyCents(originalSubtotalCents))}).",
            )
        }
        val discountTotalCents = MonetaryRounding.safeSubtract(originalSubtotalCents, targetFinalCents)

        // 4. Distribui desconto entre itens conforme modo selecionado
        val (itemDiscounts, discountConfigSnapshot) = allocateDiscountsAcrossItems(
            lines = consolidatedLines,
            lineSubtotals = lineSubtotals,
            discountTotalCents = discountTotalCents,
            discountMode = input.discountMode,
        )

        // 5. Resolve taxa efetiva de maquininha / pagamento
        val resolvedRate = resolvePaymentAndMachineRate(input)

        // 6. Calcula cada item separadamente (divisão pós-desconto + tarifa do item + responsabilidade)
        val itemResults = ArrayList<SaleItemCalculationResult>(consolidatedLines.size)
        var totalFeesCents = 0L
        var totalNetCents = 0L
        var totalFinalCheckCents = 0L

        val personAccumulator = linkedMapOf<String, MutablePersonAccumulator>()

        consolidatedLines.forEach { line ->
            val product = line.product
            val lineOriginalSubtotal = lineSubtotals.getValue(product.id)
            val lineDiscount = itemDiscounts.getValue(product.id)
            val lineFinal = MonetaryRounding.safeSubtract(lineOriginalSubtotal, lineDiscount)

            // Recalcula divisão bruta dos participantes sobre o valor efetivo do item (pós-desconto)
            val originalParticipantLineAmounts = LinkedHashMap<String, Long>(product.participants.size)
            val participantWeights = product.participants.map { split ->
                val lineParticipantOrig = MonetaryRounding.safeMultiply(split.amount.rawCents, line.quantity)
                originalParticipantLineAmounts[split.personId] = lineParticipantOrig
                WeightedAllocationBucket(
                    key = split.personId,
                    weight = lineParticipantOrig,
                )
            }
            val participantGrossMap = MonetaryRounding.allocateByWeightsDeterministic(
                totalAmount = lineFinal,
                buckets = participantWeights,
            )

            // Calcula tarifa separada para este item sobre o valor efetivamente cobrado após o desconto
            val itemFeeCents = MonetaryRounding.applyBasisPointsHalfUp(
                amount = MoneyCents(lineFinal),
                basisPoints = resolvedRate.effectiveRateBps,
            ).rawCents
            val itemNetCents = MonetaryRounding.safeSubtract(lineFinal, itemFeeCents)

            // Distribui a tarifa entre os participantes do produto conforme a regra cadastrada
            val participantFeeMap = allocateItemFeeToParticipants(
                product = product,
                itemFeeCents = itemFeeCents,
                participantGrossMap = participantGrossMap,
            )

            // Ordena participantes de forma determinística e valida líquidos não negativos
            val orderedSplits = product.participants.sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )

            val participantSnapshots = orderedSplits.map { split ->
                val pOrigSubtotal = originalParticipantLineAmounts.getValue(split.personId)
                val pGross = participantGrossMap.getValue(split.personId)
                val pDiscount = MonetaryRounding.safeSubtract(pOrigSubtotal, pGross)
                val pFee = participantFeeMap.getValue(split.personId)
                val pNet = MonetaryRounding.safeSubtract(pGross, pFee)

                if (pNet < 0L) {
                    throw SaleCalculationException(
                        "A tarifa (${CurrencyFormatter.formatBrl(MoneyCents(pFee))}) atribuída a \"${split.personName}\" no produto \"${product.name}\" excede o valor recebido (${CurrencyFormatter.formatBrl(MoneyCents(pGross))}), resultando em líquido negativo.",
                    )
                }

                val acc = personAccumulator.getOrPut(split.personId) {
                    MutablePersonAccumulator(
                        personId = split.personId,
                        personName = split.personName,
                        personColorIndex = split.personColorIndex,
                    )
                }
                acc.grossCents = MonetaryRounding.safeAdd(acc.grossCents, pGross)
                acc.feeCents = MonetaryRounding.safeAdd(acc.feeCents, pFee)
                acc.netCents = MonetaryRounding.safeAdd(acc.netCents, pNet)

                SaleItemParticipantSnapshot(
                    personId = split.personId,
                    personName = split.personName,
                    personColorIndex = split.personColorIndex,
                    originalUnitAmount = split.amount,
                    originalSubtotal = MoneyCents(pOrigSubtotal),
                    discountAmount = MoneyCents(pDiscount),
                    grossAmount = MoneyCents(pGross),
                    feeAmount = MoneyCents(pFee),
                    netAmount = MoneyCents(pNet),
                )
            }

            // Conferência interna por item
            check(participantSnapshots.sumOf { it.grossAmount.rawCents } == lineFinal)
            check(participantSnapshots.sumOf { it.feeAmount.rawCents } == itemFeeCents)
            check(participantSnapshots.sumOf { it.netAmount.rawCents } == itemNetCents)

            val ownerName = product.feeOwnerPersonId?.let { ownerId ->
                product.participants.firstOrNull { it.personId == ownerId }?.personName
            }

            itemResults.add(
                SaleItemCalculationResult(
                    productId = product.id,
                    productName = product.name,
                    quantity = line.quantity,
                    unitPriceOriginal = product.price,
                    originalSubtotal = MoneyCents(lineOriginalSubtotal),
                    discountAmount = MoneyCents(lineDiscount),
                    finalAmount = MoneyCents(lineFinal),
                    appliedFeeRateBps = resolvedRate.effectiveRateBps,
                    feeAmount = MoneyCents(itemFeeCents),
                    netAmount = MoneyCents(itemNetCents),
                    feeResponsibilityMode = product.feeResponsibilityMode,
                    feeOwnerPersonId = product.feeOwnerPersonId,
                    feeOwnerPersonName = ownerName,
                    participantSplits = participantSnapshots,
                ),
            )

            totalFinalCheckCents = MonetaryRounding.safeAdd(totalFinalCheckCents, lineFinal)
            totalFeesCents = MonetaryRounding.safeAdd(totalFeesCents, itemFeeCents)
            totalNetCents = MonetaryRounding.safeAdd(totalNetCents, itemNetCents)
        }

        val personTotals = personAccumulator.values
            .sortedWith(
                Comparator { a, b ->
                    val nameCmp = a.personName.lowercase(ptBrLocale).compareTo(b.personName.lowercase(ptBrLocale))
                    if (nameCmp != 0) nameCmp else a.personId.compareTo(b.personId)
                },
            )
            .map { acc ->
                SalePersonTotalSnapshot(
                    personId = acc.personId,
                    personName = acc.personName,
                    personColorIndex = acc.personColorIndex,
                    grossAmount = MoneyCents(acc.grossCents),
                    feeAmount = MoneyCents(acc.feeCents),
                    netAmount = MoneyCents(acc.netCents),
                )
            }

        val result = SaleCalculationResult(
            items = itemResults,
            personTotals = personTotals,
            originalSubtotal = MoneyCents(originalSubtotalCents),
            discountTotal = MoneyCents(discountTotalCents),
            finalAmount = MoneyCents(targetFinalCents),
            feeTotal = MoneyCents(totalFeesCents),
            netTotal = MoneyCents(totalNetCents),
            paymentMethod = input.paymentMethod,
            machineSnapshot = resolvedRate.machineSnapshot,
            effectiveFeeRateBps = resolvedRate.effectiveRateBps,
            discountConfig = discountConfigSnapshot,
            note = input.note.trim(),
        )

        verifyMandatoryEqualities(result)
        return result
    }

    private fun consolidateAndValidateItems(items: List<SaleItemInput>): List<SaleItemInput> {
        val consolidatedByProductId = linkedMapOf<String, SaleItemInput>()
        val personNamesSeen = linkedMapOf<String, String>()

        items.forEach { rawLine ->
            val product = rawLine.product
            if (product.id.isBlank()) {
                throw SaleCalculationException("Produto sem identificador válido.")
            }
            if (product.name.isBlank()) {
                throw SaleCalculationException("Produto sem nome cadastrado.")
            }
            if (product.price.rawCents <= 0L) {
                throw SaleCalculationException("O preço do produto \"${product.name}\" deve ser maior que zero.")
            }
            if (rawLine.quantity !in MIN_ITEM_QUANTITY..MAX_ITEM_QUANTITY) {
                throw SaleCalculationException(
                    "A quantidade do produto \"${product.name}\" deve estar entre $MIN_ITEM_QUANTITY e $MAX_ITEM_QUANTITY.",
                )
            }
            validateProductParticipants(product, personNamesSeen)

            val existing = consolidatedByProductId[product.id]
            if (existing == null) {
                consolidatedByProductId[product.id] = rawLine
            } else {
                val mergedQuantity = existing.quantity + rawLine.quantity
                if (mergedQuantity > MAX_ITEM_QUANTITY) {
                    throw SaleCalculationException(
                        "A quantidade consolidada de \"${product.name}\" excede o máximo de $MAX_ITEM_QUANTITY unidades.",
                    )
                }
                consolidatedByProductId[product.id] = existing.copy(quantity = mergedQuantity)
            }
        }

        // Ordena determinísticamente por nome e depois ID para que cálculos e saídas não dependam da ordem de entrada/banco
        return consolidatedByProductId.values.sortedWith(
            Comparator { a, b ->
                val nameCmp = a.product.name.lowercase(ptBrLocale).compareTo(b.product.name.lowercase(ptBrLocale))
                if (nameCmp != 0) nameCmp else a.product.id.compareTo(b.product.id)
            },
        )
    }

    private fun validateProductParticipants(
        product: Product,
        personNamesSeen: MutableMap<String, String>,
    ) {
        if (product.participants.isEmpty()) {
            throw SaleCalculationException("O produto \"${product.name}\" não possui participantes cadastrados.")
        }
        val seenIds = HashSet<String>(product.participants.size)
        var participantSum = 0L

        product.participants.forEach { split: ProductParticipantSplit ->
            if (split.personId.isBlank() || split.personName.isBlank()) {
                throw SaleCalculationException("Produto \"${product.name}\" contém participante inválido.")
            }
            if (!seenIds.add(split.personId)) {
                throw SaleCalculationException("Produto \"${product.name}\" contém participante duplicado.")
            }
            if (split.amount.rawCents < 0L) {
                throw SaleCalculationException("Valor individual negativo no produto \"${product.name}\".")
            }
            val priorName = personNamesSeen.putIfAbsent(split.personId, split.personName)
            if (priorName != null && priorName != split.personName) {
                throw SaleCalculationException(
                    "Inconsistência de cadastro: pessoa \"${split.personId}\" aparece com nomes diferentes (\"$priorName\" e \"${split.personName}\").",
                )
            }
            participantSum = MonetaryRounding.safeAdd(participantSum, split.amount.rawCents)
        }

        if (participantSum != product.price.rawCents) {
            throw SaleCalculationException(
                "A divisão cadastrada do produto \"${product.name}\" (${CurrencyFormatter.formatBrl(MoneyCents(participantSum))}) difere do preço unitário (${CurrencyFormatter.formatBrl(product.price)}).",
            )
        }

        if (product.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL) {
            val ownerId = product.feeOwnerPersonId?.takeIf { it.isNotBlank() }
                ?: throw SaleCalculationException("Produto \"${product.name}\" exige um participante responsável pelas tarifas.")
            if (!seenIds.contains(ownerId)) {
                throw SaleCalculationException(
                    "O responsável pela tarifa do produto \"${product.name}\" não participa deste produto.",
                )
            }
        }
    }

    private fun allocateDiscountsAcrossItems(
        lines: List<SaleItemInput>,
        lineSubtotals: Map<String, Long>,
        discountTotalCents: Long,
        discountMode: SaleDiscountInput,
    ): Pair<Map<String, Long>, SaleDiscountConfigSnapshot> {
        val zeroMap = lines.associate { it.product.id to 0L }
        return when (discountMode) {
            is SaleDiscountInput.Proportional -> {
                val allocated = if (discountTotalCents == 0L) {
                    zeroMap
                } else {
                    MonetaryRounding.allocateByWeightsDeterministic(
                        totalAmount = discountTotalCents,
                        buckets = lines.map { line ->
                            WeightedAllocationBucket(
                                key = line.product.id,
                                weight = lineSubtotals.getValue(line.product.id),
                            )
                        },
                    )
                }
                allocated to SaleDiscountConfigSnapshot.Proportional
            }

            is SaleDiscountInput.SpecificProduct -> {
                val targetLine = lines.firstOrNull { it.product.id == discountMode.productId }
                    ?: throw SaleCalculationException("O produto selecionado para receber o desconto não faz parte desta venda.")
                val targetSubtotal = lineSubtotals.getValue(targetLine.product.id)
                if (discountTotalCents >= targetSubtotal) {
                    throw SaleCalculationException(
                        "O desconto (${CurrencyFormatter.formatBrl(MoneyCents(discountTotalCents))}) não pode zerar nem tornar negativo o valor do produto \"${targetLine.product.name}\" (${CurrencyFormatter.formatBrl(MoneyCents(targetSubtotal))}).",
                    )
                }
                val map = zeroMap.toMutableMap()
                map[targetLine.product.id] = discountTotalCents
                val snapshot = SaleDiscountConfigSnapshot.SpecificProduct(
                    productId = targetLine.product.id,
                    productNameSnapshot = targetLine.product.name,
                )
                map to snapshot
            }
        }
    }

    private data class ResolvedRate(
        val effectiveRateBps: Int,
        val machineSnapshot: SaleMachineSnapshot?,
    )

    private fun resolvePaymentAndMachineRate(input: SaleCalculationInput): ResolvedRate {
        return when (input.paymentMethod) {
            SalePaymentMethod.CASH,
            SalePaymentMethod.PIX -> ResolvedRate(
                effectiveRateBps = 0,
                machineSnapshot = null,
            )

            SalePaymentMethod.DEBIT,
            SalePaymentMethod.CREDIT -> {
                val machine = input.machine
                    ?: throw SaleCalculationException(
                        "Selecione uma maquininha compatível para pagamentos em ${input.paymentMethod.label}.",
                    )

                if (input.paymentMethod == SalePaymentMethod.CREDIT && !machine.creditEnabled) {
                    throw SaleCalculationException(
                        "A maquininha \"${machine.name}\" está com o crédito desativado.",
                    )
                }

                if (input.cardBrand != null && input.cardBrandKey != null && input.cardBrand.storageKey != input.cardBrandKey) {
                    throw SaleCalculationException(
                        "Bandeira informada inconsistente (${input.cardBrand.storageKey} vs ${input.cardBrandKey}).",
                    )
                }

                val requestedBrandKey = input.cardBrandKey ?: input.cardBrand?.storageKey
                val (appliedBrand, appliedBrandKey) = if (
                    input.paymentMethod == SalePaymentMethod.CREDIT &&
                    machine.brandRatesEnabled
                ) {
                    val key = requestedBrandKey?.takeIf { it.isNotBlank() }
                        ?: throw SaleCalculationException("Selecione a bandeira do cartão para a maquininha \"${machine.name}\".")
                    val standardBrand = CardBrand.fromStorageKey(key)
                    if (standardBrand != null) {
                        standardBrand to standardBrand.storageKey
                    } else if (CustomBrandKeyCodec.isValidCustomBrandKey(key)) {
                        val registeredCustomBrand = machine.findCustomBrand(key)
                            ?: throw SaleCalculationException(
                                "A bandeira personalizada \"${CustomBrandKeyCodec.decodeNameOrNull(key) ?: key}\" não está cadastrada na maquininha \"${machine.name}\".",
                            )
                        null to registeredCustomBrand.key
                    } else {
                        throw SaleCalculationException("Chave de bandeira inválida ($key).")
                    }
                } else {
                    null to null
                }

                val appliedInstallments = if (input.paymentMethod == SalePaymentMethod.CREDIT) {
                    if (machine.installmentsEnabled) {
                        val requested = input.installments ?: 1
                        if (requested !in InstallmentTiers.SUPPORTED) {
                            throw SaleCalculationException(
                                "Quantidade de parcelas (${requested}x) inválida. Permitido: ${InstallmentTiers.SUPPORTED.joinToString { "${it}x" }}.",
                            )
                        }
                        if (!machine.isInstallmentAllowed(requested)) {
                            val allowedText = machine.allowedInstallmentTiers().joinToString { "${it}x" }
                            throw SaleCalculationException(
                                "A maquininha \"${machine.name}\" não permite parcelamento em ${requested}x. Permitido: $allowedText.",
                            )
                        }
                        requested
                    } else {
                        1
                    }
                } else {
                    null
                }

                val automaticBps = machine.effectiveAutomaticRateBpsForKey(
                    method = when (input.paymentMethod) {
                        SalePaymentMethod.DEBIT -> com.mixvendas3d.app.domain.models.PreviewPaymentMethod.DEBIT
                        SalePaymentMethod.CREDIT -> com.mixvendas3d.app.domain.models.PreviewPaymentMethod.CREDIT
                        SalePaymentMethod.CASH -> com.mixvendas3d.app.domain.models.PreviewPaymentMethod.CASH
                        SalePaymentMethod.PIX -> com.mixvendas3d.app.domain.models.PreviewPaymentMethod.PIX
                    },
                    brandKey = appliedBrandKey,
                    installments = appliedInstallments ?: 1,
                ) ?: throw SaleCalculationException("Não foi possível determinar a tarifa automática da maquininha.")

                if (automaticBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                    throw SaleCalculationException("A tarifa automática calculada está fora do intervalo permitido (0% a 100%).")
                }

                val manualBps = input.manualRateOverrideBps
                if (manualBps != null && manualBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                    throw SaleCalculationException("A tarifa manual deve estar entre 0,00% e 100,00%.")
                }

                val effectiveBps = manualBps ?: automaticBps
                ResolvedRate(
                    effectiveRateBps = effectiveBps,
                    machineSnapshot = SaleMachineSnapshot(
                        machineId = machine.id,
                        machineName = machine.name,
                        debitRateBps = machine.debitRateBps,
                        creditDefaultRateBps = machine.creditDefaultRateBps,
                        brandRatesEnabled = machine.brandRatesEnabled,
                        installmentsEnabled = machine.installmentsEnabled,
                        appliedBrand = appliedBrand,
                        appliedInstallments = appliedInstallments,
                        automaticRateBps = automaticBps,
                        manualRateOverrideBps = manualBps,
                        effectiveRateBps = effectiveBps,
                        appliedBrandKey = appliedBrandKey,
                    ),
                )
            }
        }
    }

    private fun allocateItemFeeToParticipants(
        product: Product,
        itemFeeCents: Long,
        participantGrossMap: Map<String, Long>,
    ): Map<String, Long> {
        if (itemFeeCents == 0L) {
            return product.participants.associate { it.personId to 0L }
        }

        return when (product.feeResponsibilityMode) {
            FeeResponsibilityMode.INDIVIDUAL -> {
                val ownerId = product.feeOwnerPersonId
                    ?: throw SaleCalculationException("Produto \"${product.name}\" sem responsável pela tarifa.")
                val map = LinkedHashMap<String, Long>(product.participants.size)
                product.participants.forEach { split ->
                    map[split.personId] = if (split.personId == ownerId) itemFeeCents else 0L
                }
                map
            }

            FeeResponsibilityMode.EQUAL -> {
                MonetaryRounding.allocateByWeightsDeterministic(
                    totalAmount = itemFeeCents,
                    buckets = product.participants.map { split ->
                        WeightedAllocationBucket(key = split.personId, weight = 1L)
                    },
                )
            }

            FeeResponsibilityMode.PROPORTIONAL -> {
                val totalGross = participantGrossMap.values.fold(0L) { acc, value ->
                    MonetaryRounding.safeAdd(acc, value)
                }
                if (totalGross <= 0L) {
                    MonetaryRounding.allocateByWeightsDeterministic(
                        totalAmount = itemFeeCents,
                        buckets = product.participants.map { split ->
                            WeightedAllocationBucket(key = split.personId, weight = 1L)
                        },
                    )
                } else {
                    MonetaryRounding.allocateByWeightsDeterministic(
                        totalAmount = itemFeeCents,
                        buckets = product.participants.map { split ->
                            WeightedAllocationBucket(
                                key = split.personId,
                                weight = participantGrossMap.getValue(split.personId),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun verifyMandatoryEqualities(result: SaleCalculationResult) {
        val originalMinusDiscount = MonetaryRounding.safeSubtract(
            result.originalSubtotal.rawCents,
            result.discountTotal.rawCents,
        )
        check(originalMinusDiscount == result.finalAmount.rawCents) {
            "Inconsistência: subtotal original - desconto != valor final."
        }

        val itemsFinalSum = result.items.fold(0L) { acc, item ->
            MonetaryRounding.safeAdd(acc, item.finalAmount.rawCents)
        }
        check(itemsFinalSum == result.finalAmount.rawCents) {
            "Inconsistência: soma dos valores finais dos itens != valor final."
        }

        val itemsFeeSum = result.items.fold(0L) { acc, item ->
            MonetaryRounding.safeAdd(acc, item.feeAmount.rawCents)
        }
        check(itemsFeeSum == result.feeTotal.rawCents) {
            "Inconsistência: soma das tarifas dos itens != tarifa total."
        }

        val finalMinusFee = MonetaryRounding.safeSubtract(
            result.finalAmount.rawCents,
            result.feeTotal.rawCents,
        )
        check(finalMinusFee == result.netTotal.rawCents) {
            "Inconsistência: valor final - tarifa total != líquido da venda."
        }

        val personGrossSum = result.personTotals.fold(0L) { acc, person ->
            MonetaryRounding.safeAdd(acc, person.grossAmount.rawCents)
        }
        check(personGrossSum == result.finalAmount.rawCents) {
            "Inconsistência: soma dos brutos individuais != valor final."
        }

        val personFeeSum = result.personTotals.fold(0L) { acc, person ->
            MonetaryRounding.safeAdd(acc, person.feeAmount.rawCents)
        }
        check(personFeeSum == result.feeTotal.rawCents) {
            "Inconsistência: soma das tarifas individuais != tarifa total."
        }

        val personNetSum = result.personTotals.fold(0L) { acc, person ->
            MonetaryRounding.safeAdd(acc, person.netAmount.rawCents)
        }
        check(personNetSum == result.netTotal.rawCents) {
            "Inconsistência: soma dos líquidos individuais != líquido da venda."
        }
    }

    private class MutablePersonAccumulator(
        val personId: String,
        val personName: String,
        val personColorIndex: Int,
        var grossCents: Long = 0L,
        var feeCents: Long = 0L,
        var netCents: Long = 0L,
    )
}
