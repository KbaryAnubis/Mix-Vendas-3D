package com.mixvendas3d.app.domain.update

import com.mixvendas3d.app.domain.calculation.SaleCalculationException
import com.mixvendas3d.app.domain.calculation.SaleFinancialEngine
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.Person
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.models.SaleCalculationInput
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleDiscountInput
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import com.mixvendas3d.app.domain.models.SaleItemInput
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.toSnapshotMachine
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter
import java.security.MessageDigest

object SystemUpdateAnalyzer {

    fun analyze(
        sales: List<PersistedSale>,
        people: List<Person>,
        products: List<Product>,
        machines: List<Machine>,
        analyzedAtEpochMs: Long = System.currentTimeMillis(),
    ): SystemUpdateAnalysisResult {
        val peopleById = people.associateBy { it.id }
        val productsById = products.associateBy { it.id }
        val machinesById = machines.associateBy { it.id }

        val sortedSales = sales.sortedWith(
            compareByDescending<PersistedSale> { it.soldAtEpochMs }.thenBy { it.id },
        )

        val updatable = ArrayList<SaleUpdateCandidate>()
        val conflicts = ArrayList<SaleUpdateConflict>()
        val unchangedIds = ArrayList<String>()

        sortedSales.forEach { sale ->
            when (val outcome = evaluateSale(sale, peopleById, productsById, machinesById)) {
                is SaleEvaluationOutcome.AlreadyCorrect -> unchangedIds.add(sale.id)
                is SaleEvaluationOutcome.Candidate -> updatable.add(outcome.candidate)
                is SaleEvaluationOutcome.Conflict -> conflicts.add(outcome.conflict)
            }
        }

        val fingerprint = computeLocalStateFingerprint(
            sales = sales,
            people = people,
            products = products,
            machines = machines,
        )

        return SystemUpdateAnalysisResult(
            analyzedAtEpochMs = analyzedAtEpochMs,
            localStateFingerprint = fingerprint,
            totalSalesEvaluated = sortedSales.size,
            salesToUpdateCount = updatable.size,
            alreadyCorrectCount = unchangedIds.size,
            requiresReviewCount = conflicts.size,
            updatableItems = updatable,
            conflictItems = conflicts,
            unchangedSaleIds = unchangedIds,
        )
    }

    fun computeLocalStateFingerprint(
        sales: List<PersistedSale>,
        people: List<Person>,
        products: List<Product>,
        machines: List<Machine>,
    ): String {
        val canonical = buildString {
            people.sortedBy { it.id }.forEach { person ->
                append("P|").append(person.id)
                    .append('|').append(person.name)
                    .append('|').append(person.colorIndex)
                    .append('|').append(person.updatedAtEpochMs)
                    .append('\n')
            }
            products.sortedBy { it.id }.forEach { product ->
                append("PR|").append(product.id)
                    .append('|').append(product.name)
                    .append('|').append(product.price.rawCents)
                    .append('|').append(product.active)
                    .append('|').append(product.feeResponsibilityMode.storageKey)
                    .append('|').append(product.feeOwnerPersonId.orEmpty())
                    .append('|').append(product.updatedAtEpochMs)
                product.participants.sortedBy { it.personId }.forEach { split ->
                    append("|S:").append(split.personId)
                        .append(':').append(split.amount.rawCents)
                }
                append('\n')
            }
            machines.sortedBy { it.id }.forEach { machine ->
                append("M|").append(machine.id)
                    .append('|').append(machine.name)
                    .append('|').append(machine.debitRateBps)
                    .append('|').append(machine.creditEnabled)
                    .append('|').append(machine.creditDefaultRateBps)
                    .append('|').append(machine.brandRatesEnabled)
                    .append('|').append(machine.installmentsEnabled)
                    .append('|').append(machine.updatedAtEpochMs)
                machine.brandRatesBps.entries.sortedBy { it.key.storageKey }.forEach { (brand, bps) ->
                    append("|B:").append(brand.storageKey).append(':').append(bps)
                }
                machine.customBrandRates.sortedBy { it.key }.forEach { customBrand ->
                    append("|CB:").append(customBrand.key).append(':').append(customBrand.rateBps)
                }
                machine.installmentAddonsBps.entries.sortedBy { it.key }.forEach { (tier, bps) ->
                    append("|I:").append(tier).append(':').append(bps)
                }
                append('\n')
            }
            sales.sortedBy { it.id }.forEach { sale ->
                append("SA|").append(sale.id)
                    .append('|').append(sale.soldAtEpochMs)
                    .append('|').append(sale.updatedAtEpochMs)
                    .append('|').append(computeCalculationFingerprint(sale.calculation))
                    .append('\n')
            }
        }
        return sha256Hex(canonical)
    }

    fun computeCalculationFingerprint(calc: SaleCalculationResult): String {
        val canonical = buildString {
            append(calc.paymentMethod.storageKey)
                .append('|').append(calc.originalSubtotal.rawCents)
                .append('|').append(calc.discountTotal.rawCents)
                .append('|').append(calc.finalAmount.rawCents)
                .append('|').append(calc.feeTotal.rawCents)
                .append('|').append(calc.netTotal.rawCents)
                .append('|').append(calc.effectiveFeeRateBps)
                .append('|').append(calc.note)
            when (val d = calc.discountConfig) {
                is SaleDiscountConfigSnapshot.Proportional -> append("|DP")
                is SaleDiscountConfigSnapshot.SpecificProduct ->
                    append("|DS:").append(d.productId).append(':').append(d.productNameSnapshot)
                is SaleDiscountConfigSnapshot.SelectedProducts ->
                    append("|DM:")
                        .append(d.productIds.sorted().joinToString(","))
                        .append(':')
                        .append(d.productNameSnapshots.joinToString(","))
            }
            calc.machineSnapshot?.let { m ->
                append("|MS:").append(m.machineId)
                    .append(':').append(m.machineName)
                    .append(':').append(m.debitRateBps)
                    .append(':').append(m.creditDefaultRateBps)
                    .append(':').append(m.brandRatesEnabled)
                    .append(':').append(m.installmentsEnabled)
                    .append(':').append(m.resolvedAppliedBrandKey.orEmpty())
                    .append(':').append(m.appliedInstallments ?: -1)
                    .append(':').append(m.automaticRateBps)
                    .append(':').append(m.manualRateOverrideBps ?: -1)
                    .append(':').append(m.effectiveRateBps)
            }
            calc.items.sortedBy { it.productId }.forEach { item ->
                append("|IT:").append(item.productId)
                    .append(':').append(item.productName)
                    .append(':').append(item.quantity)
                    .append(':').append(item.unitPriceOriginal.rawCents)
                    .append(':').append(item.discountAmount.rawCents)
                    .append(':').append(item.finalAmount.rawCents)
                    .append(':').append(item.feeAmount.rawCents)
                    .append(':').append(item.netAmount.rawCents)
                    .append(':').append(item.feeResponsibilityMode.storageKey)
                    .append(':').append(item.feeOwnerPersonId.orEmpty())
                    .append(':').append(item.feeOwnerPersonName.orEmpty())
                item.participantSplits.sortedBy { it.personId }.forEach { sp ->
                    append("|SP:").append(sp.personId)
                        .append(':').append(sp.personName)
                        .append(':').append(sp.personColorIndex)
                        .append(':').append(sp.originalUnitAmount.rawCents)
                        .append(':').append(sp.grossAmount.rawCents)
                        .append(':').append(sp.feeAmount.rawCents)
                        .append(':').append(sp.netAmount.rawCents)
                }
            }
            calc.personTotals.sortedBy { it.personId }.forEach { pt ->
                append("|PT:").append(pt.personId)
                    .append(':').append(pt.personName)
                    .append(':').append(pt.personColorIndex)
                    .append(':').append(pt.grossAmount.rawCents)
                    .append(':').append(pt.feeAmount.rawCents)
                    .append(':').append(pt.netAmount.rawCents)
            }
        }
        return sha256Hex(canonical)
    }

    private sealed interface SaleEvaluationOutcome {
        data object AlreadyCorrect : SaleEvaluationOutcome
        data class Candidate(val candidate: SaleUpdateCandidate) : SaleEvaluationOutcome
        data class Conflict(val conflict: SaleUpdateConflict) : SaleEvaluationOutcome
    }

    private fun evaluateSale(
        sale: PersistedSale,
        peopleById: Map<String, Person>,
        productsById: Map<String, Product>,
        machinesById: Map<String, Machine>,
    ): SaleEvaluationOutcome {
        val oldCalc = sale.calculation
        val conflictReasons = LinkedHashSet<String>()
        val conflictCodes = LinkedHashSet<SystemUpdateConflictCode>()
        val resolvedItemInputs = ArrayList<SaleItemInput>(oldCalc.items.size)
        val reasons = LinkedHashSet<String>()

        oldCalc.items.forEach { oldItem ->
            val resolvedProduct = resolveItemProduct(
                oldItem = oldItem,
                peopleById = peopleById,
                productsById = productsById,
                conflictReasons = conflictReasons,
                conflictCodes = conflictCodes,
                reasons = reasons,
                soldAtEpochMs = sale.soldAtEpochMs,
                updatedAtEpochMs = sale.updatedAtEpochMs,
            )
            if (resolvedProduct != null) {
                resolvedItemInputs.add(
                    SaleItemInput(
                        product = resolvedProduct,
                        quantity = oldItem.quantity,
                    ),
                )
            }
        }

        val resolvedMachineResult = resolveMachineForSale(
            sale = sale,
            machinesById = machinesById,
            conflictReasons = conflictReasons,
            conflictCodes = conflictCodes,
            reasons = reasons,
        )

        if (conflictReasons.isNotEmpty() || resolvedItemInputs.size != oldCalc.items.size) {
            return SaleEvaluationOutcome.Conflict(
                buildConflict(
                    sale = sale,
                    reasons = conflictReasons.toList(),
                    codes = conflictCodes.toSet(),
                ),
            )
        }

        val discountMode = when (val cfg = oldCalc.discountConfig) {
            is SaleDiscountConfigSnapshot.Proportional -> SaleDiscountInput.Proportional
            is SaleDiscountConfigSnapshot.SpecificProduct -> SaleDiscountInput.SpecificProduct(cfg.productId)
            is SaleDiscountConfigSnapshot.SelectedProducts -> SaleDiscountInput.SelectedProducts(cfg.productIds.toSet())
        }
        val protectedDiscountPersonIds = com.mixvendas3d.app.domain.calculation.SaleDiscountPolicy
            .inferProtectedPersonIdsFromSnapshot(oldCalc)

        val calcInput = SaleCalculationInput(
            items = resolvedItemInputs,
            finalAmountOverride = oldCalc.finalAmount,
            discountMode = discountMode,
            paymentMethod = oldCalc.paymentMethod,
            machine = resolvedMachineResult.machine,
            cardBrand = resolvedMachineResult.cardBrand,
            installments = resolvedMachineResult.installments,
            manualRateOverrideBps = resolvedMachineResult.manualRateOverrideBps,
            note = oldCalc.note,
            cardBrandKey = resolvedMachineResult.cardBrandKey,
            protectedDiscountPersonIds = protectedDiscountPersonIds,
        )

        val proposedCalc = try {
            SaleFinancialEngine.calculate(calcInput)
        } catch (ex: SaleCalculationException) {
            val code = classifyCalculationError(ex.message.orEmpty())
            return SaleEvaluationOutcome.Conflict(
                buildConflict(
                    sale = sale,
                    reasons = listOf(ex.message ?: "Não foi possível recalcular a venda sem ambiguidade."),
                    codes = setOf(code),
                ),
            )
        }

        if (!preservesHistoricalInvariants(oldCalc, proposedCalc)) {
            return SaleEvaluationOutcome.Conflict(
                buildConflict(
                    sale = sale,
                    reasons = listOf("O recálculo alteraria o valor total cobrado, descontos ou quantidades da venda original."),
                    codes = setOf(SystemUpdateConflictCode.INVARIANT_VIOLATION),
                ),
            )
        }

        try {
            SalePersistenceValidator.validateForPersistence(proposedCalc)
        } catch (ex: SalePersistenceException) {
            return SaleEvaluationOutcome.Conflict(
                buildConflict(
                    sale = sale,
                    reasons = listOf(ex.message ?: "Snapshot recalculado falhou na validação financeira."),
                    codes = setOf(SystemUpdateConflictCode.CALCULATION_ERROR),
                ),
            )
        }

        if (proposedCalc == oldCalc) {
            return SaleEvaluationOutcome.AlreadyCorrect
        }

        collectDetailedChangeReasons(oldCalc, proposedCalc, reasons)
        if (reasons.isEmpty()) {
            reasons.add("Reconciliação de snapshot com o cadastro atual")
        }

        return SaleEvaluationOutcome.Candidate(
            SaleUpdateCandidate(
                saleId = sale.id,
                soldAtEpochMs = sale.soldAtEpochMs,
                productNames = proposedCalc.items.map { "${it.quantity}x ${it.productName}" },
                paymentMethodLabel = proposedCalc.paymentMethod.label,
                originalChargedAmount = oldCalc.finalAmount,
                historicalFeeRateBps = oldCalc.effectiveFeeRateBps,
                proposedFeeRateBps = proposedCalc.effectiveFeeRateBps,
                historicalFeeAmount = oldCalc.feeTotal,
                proposedFeeAmount = proposedCalc.feeTotal,
                historicalNetAmount = oldCalc.netTotal,
                proposedNetAmount = proposedCalc.netTotal,
                historicalPersonTotals = oldCalc.personTotals,
                proposedPersonTotals = proposedCalc.personTotals,
                changeReasons = reasons.toList(),
                beforeSale = sale,
                proposedCalculation = proposedCalc,
            ),
        )
    }

    private fun resolveItemProduct(
        oldItem: SaleItemCalculationResult,
        peopleById: Map<String, Person>,
        productsById: Map<String, Product>,
        conflictReasons: MutableSet<String>,
        conflictCodes: MutableSet<SystemUpdateConflictCode>,
        reasons: MutableSet<String>,
        soldAtEpochMs: Long,
        updatedAtEpochMs: Long,
    ): Product? {
        val catalogProduct = productsById[oldItem.productId]
        if (catalogProduct == null) {
            conflictCodes.add(SystemUpdateConflictCode.PRODUCT_NOT_FOUND)
            conflictReasons.add("Produto \"${oldItem.productName}\" não existe mais no cadastro atual.")
            return null
        }

        val priceMatchesHistorical = catalogProduct.price == oldItem.unitPriceOriginal

        if (priceMatchesHistorical) {
            val missingPeople = catalogProduct.participants.filter { !peopleById.containsKey(it.personId) }
            if (missingPeople.isNotEmpty()) {
                conflictCodes.add(SystemUpdateConflictCode.PERSON_NOT_FOUND)
                missingPeople.forEach { sp ->
                    conflictReasons.add("Pessoa vinculada (${sp.personName}) não encontrada no cadastro.")
                }
                return null
            }

            if (catalogProduct.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL) {
                val ownerId = catalogProduct.feeOwnerPersonId
                val ownerExists = ownerId != null &&
                    peopleById.containsKey(ownerId) &&
                    catalogProduct.participants.any { it.personId == ownerId }
                if (!ownerExists) {
                    conflictCodes.add(SystemUpdateConflictCode.FEE_OWNER_NOT_FOUND_OR_NOT_PARTICIPANT)
                    conflictReasons.add("Responsável pelas tarifas do produto \"${catalogProduct.name}\" não participa mais do produto.")
                    return null
                }
            }

            val refreshedParticipants = catalogProduct.participants.map { split ->
                val currentPerson = peopleById.getValue(split.personId)
                split.copy(
                    personName = currentPerson.name,
                    personColorIndex = currentPerson.colorIndex,
                )
            }

            if (catalogProduct.name != oldItem.productName) {
                reasons.add("Nome do produto atualizado (${oldItem.productName} → ${catalogProduct.name})")
            }

            return catalogProduct.copy(participants = refreshedParticipants)
        }

        // Preço unitário do catálogo difere do preço histórico cobrado: nunca alterar preço cobrado ao cliente
        val oldParticipantIds = oldItem.participantSplits.map { it.personId }.toSet()
        val catalogParticipantIds = catalogProduct.participants.map { it.personId }.toSet()

        if (catalogParticipantIds != oldParticipantIds) {
            conflictCodes.add(SystemUpdateConflictCode.PRICE_AND_RULES_MISMATCH)
            conflictReasons.add(
                "O produto \"${oldItem.productName}\" mudou de preço (${CurrencyFormatter.formatBrl(oldItem.unitPriceOriginal)} → ${CurrencyFormatter.formatBrl(catalogProduct.price)}) e de participantes; exige revisão manual.",
            )
            return null
        }

        val missingHistoricalPeople = oldItem.participantSplits.filter { !peopleById.containsKey(it.personId) }
        if (missingHistoricalPeople.isNotEmpty()) {
            conflictCodes.add(SystemUpdateConflictCode.PERSON_NOT_FOUND)
            missingHistoricalPeople.forEach { sp ->
                conflictReasons.add("Pessoa vinculada (${sp.personName}) não encontrada no cadastro.")
            }
            return null
        }

        if (catalogProduct.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL) {
            val ownerId = catalogProduct.feeOwnerPersonId
            val ownerValid = ownerId != null &&
                peopleById.containsKey(ownerId) &&
                oldParticipantIds.contains(ownerId)
            if (!ownerValid) {
                conflictCodes.add(SystemUpdateConflictCode.FEE_OWNER_NOT_FOUND_OR_NOT_PARTICIPANT)
                conflictReasons.add("Responsável pelas tarifas do produto \"${catalogProduct.name}\" não participa da venda original.")
                return null
            }
        }

        if (catalogProduct.name != oldItem.productName) {
            reasons.add("Nome do produto atualizado (${oldItem.productName} → ${catalogProduct.name})")
        }

        // Preserva o preço unitário e os valores unitários históricos da divisão sem converter divisão fixa em proporcional
        val preservedParticipants = oldItem.participantSplits.map { split ->
            val currentPerson = peopleById.getValue(split.personId)
            ProductParticipantSplit(
                personId = split.personId,
                personName = currentPerson.name,
                personColorIndex = currentPerson.colorIndex,
                amount = split.originalUnitAmount,
            )
        }

        return Product(
            id = catalogProduct.id,
            name = catalogProduct.name,
            price = oldItem.unitPriceOriginal,
            active = catalogProduct.active,
            feeResponsibilityMode = catalogProduct.feeResponsibilityMode,
            feeOwnerPersonId = catalogProduct.feeOwnerPersonId,
            participants = preservedParticipants,
            createdAtEpochMs = soldAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    private data class ResolvedMachineInput(
        val machine: Machine?,
        val cardBrand: CardBrand?,
        val installments: Int?,
        val manualRateOverrideBps: Int?,
        val cardBrandKey: String? = null,
    )

    private fun resolveMachineForSale(
        sale: PersistedSale,
        machinesById: Map<String, Machine>,
        conflictReasons: MutableSet<String>,
        conflictCodes: MutableSet<SystemUpdateConflictCode>,
        reasons: MutableSet<String>,
    ): ResolvedMachineInput {
        val oldCalc = sale.calculation
        if (!oldCalc.paymentMethod.usesMachine) {
            return ResolvedMachineInput(
                machine = null,
                cardBrand = null,
                installments = null,
                manualRateOverrideBps = null,
            )
        }

        val oldSnap = oldCalc.machineSnapshot
        if (oldSnap == null) {
            conflictCodes.add(SystemUpdateConflictCode.MACHINE_NOT_FOUND)
            conflictReasons.add("Venda em ${oldCalc.paymentMethod.label} sem snapshot de maquininha.")
            return ResolvedMachineInput(null, null, null, null)
        }

        val currentMachine = machinesById[oldSnap.machineId]
        if (currentMachine == null) {
            conflictCodes.add(SystemUpdateConflictCode.MACHINE_NOT_FOUND)
            conflictReasons.add("Maquininha \"${oldSnap.machineName}\" não encontrada no cadastro atual.")
            return ResolvedMachineInput(null, null, null, null)
        }

        // Tarifa manual explícita deve ser respeitada sem sobrescrita automática
        if (oldSnap.manualRateOverrideBps != null) {
            val preservedSnapshotMachine = oldSnap.toSnapshotMachine(
                paymentMethod = oldCalc.paymentMethod,
                soldAtEpochMs = sale.soldAtEpochMs,
                updatedAtEpochMs = sale.updatedAtEpochMs,
            ).copy(name = currentMachine.name)

            if (currentMachine.name != oldSnap.machineName) {
                reasons.add("Nome da maquininha atualizado (${oldSnap.machineName} → ${currentMachine.name}); tarifa manual mantida")
            }

            return ResolvedMachineInput(
                machine = preservedSnapshotMachine,
                cardBrand = oldSnap.appliedBrand,
                installments = oldSnap.appliedInstallments,
                manualRateOverrideBps = oldSnap.manualRateOverrideBps,
                cardBrandKey = oldSnap.resolvedAppliedBrandKey,
            )
        }

        var hasMachineConflict = false

        if (oldCalc.paymentMethod == SalePaymentMethod.CREDIT && !currentMachine.creditEnabled) {
            conflictCodes.add(SystemUpdateConflictCode.CREDIT_DISABLED)
            conflictReasons.add("Crédito desativado na maquininha atual \"${currentMachine.name}\".")
            hasMachineConflict = true
        }

        val resolvedOldBrandKey = oldSnap.resolvedAppliedBrandKey
        if (oldCalc.paymentMethod.usesMachine && currentMachine.brandRatesEnabled) {
            if (resolvedOldBrandKey.isNullOrBlank()) {
                if (oldCalc.paymentMethod == SalePaymentMethod.CREDIT) {
                    conflictCodes.add(SystemUpdateConflictCode.MISSING_REQUIRED_BRAND)
                    conflictReasons.add(
                        "A maquininha \"${currentMachine.name}\" passou a exigir bandeira, ausente na venda original.",
                    )
                    hasMachineConflict = true
                }
            } else if (CardBrand.fromStorageKey(resolvedOldBrandKey) == null) {
                val isRegisteredCustom = CustomBrandKeyCodec.isValidCustomBrandKey(resolvedOldBrandKey) &&
                    currentMachine.findCustomBrand(resolvedOldBrandKey) != null
                if (!isRegisteredCustom) {
                    val displayLabel = oldSnap.appliedBrandDisplayLabel ?: resolvedOldBrandKey
                    conflictCodes.add(SystemUpdateConflictCode.MISSING_REQUIRED_BRAND)
                    conflictReasons.add(
                        "A bandeira personalizada \"$displayLabel\" não está mais cadastrada na maquininha atual \"${currentMachine.name}\".",
                    )
                    hasMachineConflict = true
                }
            }
        }

        if (oldCalc.paymentMethod == SalePaymentMethod.CREDIT &&
            (oldSnap.appliedInstallments ?: 1) > 1 &&
            !currentMachine.installmentsEnabled
        ) {
            conflictCodes.add(SystemUpdateConflictCode.INSTALLMENTS_DISABLED)
            conflictReasons.add(
                "Venda registrada em ${oldSnap.appliedInstallments}x, mas o parcelamento foi desativado na maquininha \"${currentMachine.name}\".",
            )
            hasMachineConflict = true
        }

        if (hasMachineConflict) {
            return ResolvedMachineInput(null, null, null, null, null)
        }

        return ResolvedMachineInput(
            machine = currentMachine,
            cardBrand = oldSnap.appliedBrand,
            installments = oldSnap.appliedInstallments,
            manualRateOverrideBps = null,
            cardBrandKey = resolvedOldBrandKey,
        )
    }

    private fun preservesHistoricalInvariants(
        oldCalc: SaleCalculationResult,
        proposedCalc: SaleCalculationResult,
    ): Boolean {
        if (proposedCalc.finalAmount != oldCalc.finalAmount) return false
        if (proposedCalc.originalSubtotal != oldCalc.originalSubtotal) return false
        if (proposedCalc.discountTotal != oldCalc.discountTotal) return false
        if (proposedCalc.paymentMethod != oldCalc.paymentMethod) return false
        if (proposedCalc.items.size != oldCalc.items.size) return false

        val oldByProductId = oldCalc.items.associateBy { it.productId }
        return proposedCalc.items.all { newItem ->
            val oldItem = oldByProductId[newItem.productId] ?: return@all false
            newItem.quantity == oldItem.quantity &&
                newItem.unitPriceOriginal == oldItem.unitPriceOriginal &&
                newItem.originalSubtotal == oldItem.originalSubtotal &&
                newItem.discountAmount == oldItem.discountAmount &&
                newItem.finalAmount == oldItem.finalAmount
        }
    }

    private fun collectDetailedChangeReasons(
        oldCalc: SaleCalculationResult,
        proposedCalc: SaleCalculationResult,
        reasons: MutableSet<String>,
    ) {
        val oldPersonNames = oldCalc.personTotals.associate { it.personId to it.personName }
        proposedCalc.personTotals.forEach { newPt ->
            val oldName = oldPersonNames[newPt.personId]
            if (oldName != null && oldName != newPt.personName) {
                reasons.add("Nome atualizado: $oldName → ${newPt.personName}")
            }
        }

        val oldItemsByProduct = oldCalc.items.associateBy { it.productId }
        proposedCalc.items.forEach { newItem ->
            val oldItem = oldItemsByProduct[newItem.productId] ?: return@forEach
            if (oldItem.feeResponsibilityMode != newItem.feeResponsibilityMode ||
                oldItem.feeOwnerPersonId != newItem.feeOwnerPersonId
            ) {
                reasons.add("Responsabilidade pela tarifa alterada em \"${newItem.productName}\"")
            }
            val oldSplits = oldItem.participantSplits.associate { it.personId to it.originalUnitAmount }
            val newSplits = newItem.participantSplits.associate { it.personId to it.originalUnitAmount }
            if (oldSplits != newSplits) {
                reasons.add("Divisão de participantes alterada em \"${newItem.productName}\"")
            }
        }

        val oldRate = oldCalc.effectiveFeeRateBps
        val newRate = proposedCalc.effectiveFeeRateBps
        if (oldRate != newRate) {
            reasons.add(
                "Taxa efetiva alterada (${RateFormatter.formatBpsAsPercent(oldRate)} → ${RateFormatter.formatBpsAsPercent(newRate)})",
            )
        } else if (oldCalc.machineSnapshot?.machineName != proposedCalc.machineSnapshot?.machineName &&
            proposedCalc.machineSnapshot != null
        ) {
            reasons.add(
                "Nome da maquininha atualizado (${oldCalc.machineSnapshot?.machineName.orEmpty()} → ${proposedCalc.machineSnapshot.machineName})",
            )
        }
    }

    private fun classifyCalculationError(message: String): SystemUpdateConflictCode {
        return when {
            message.contains("líquido negativo", ignoreCase = true) ->
                SystemUpdateConflictCode.NEGATIVE_PARTICIPANT_NET
            message.contains("não pode zerar nem tornar negativo", ignoreCase = true) ->
                SystemUpdateConflictCode.DISCOUNT_EXCEEDS_ITEM
            message.contains("bandeira", ignoreCase = true) ->
                SystemUpdateConflictCode.MISSING_REQUIRED_BRAND
            message.contains("crédito desativado", ignoreCase = true) ->
                SystemUpdateConflictCode.CREDIT_DISABLED
            else -> SystemUpdateConflictCode.CALCULATION_ERROR
        }
    }

    private fun buildConflict(
        sale: PersistedSale,
        reasons: List<String>,
        codes: Set<SystemUpdateConflictCode>,
    ): SaleUpdateConflict {
        return SaleUpdateConflict(
            saleId = sale.id,
            soldAtEpochMs = sale.soldAtEpochMs,
            productNames = sale.calculation.items.map { "${it.quantity}x ${it.productName}" },
            paymentMethodLabel = sale.calculation.paymentMethod.label,
            originalChargedAmount = sale.calculation.finalAmount,
            reasons = reasons,
            codes = codes,
        )
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
