package com.mixvendas3d.app.domain.models

enum class SalePaymentMethod(
    val storageKey: String,
    val label: String,
) {
    CASH("CASH", "Dinheiro"),
    PIX("PIX", "Pix"),
    DEBIT("DEBIT", "Débito"),
    CREDIT("CREDIT", "Crédito");

    val usesMachine: Boolean
        get() = this == DEBIT || this == CREDIT

    companion object {
        fun fromStorageKey(key: String?): SalePaymentMethod? {
            return entries.firstOrNull { it.storageKey == key }
        }
    }
}

sealed interface SaleDiscountInput {
    data object Proportional : SaleDiscountInput
    data class SpecificProduct(val productId: String) : SaleDiscountInput
    data class SelectedProducts(val productIds: Set<String>) : SaleDiscountInput
}

sealed interface SaleDiscountConfigSnapshot {
    data object Proportional : SaleDiscountConfigSnapshot
    data class SpecificProduct(
        val productId: String,
        val productNameSnapshot: String,
    ) : SaleDiscountConfigSnapshot
    data class SelectedProducts(
        val productIds: List<String>,
        val productNameSnapshots: List<String>,
    ) : SaleDiscountConfigSnapshot
}

data class SaleItemInput(
    val product: Product,
    val quantity: Int,
)

data class SaleCalculationInput(
    val items: List<SaleItemInput>,
    val finalAmountOverride: MoneyCents? = null,
    val discountMode: SaleDiscountInput = SaleDiscountInput.Proportional,
    val paymentMethod: SalePaymentMethod,
    val machine: Machine? = null,
    val cardBrand: CardBrand? = null,
    val installments: Int? = null,
    val manualRateOverrideBps: Int? = null,
    val note: String = "",
    val cardBrandKey: String? = null,
    val protectedDiscountPersonIds: Set<String> = emptySet(),
) {
    fun resolvedCardBrandKey(): String? = cardBrandKey ?: cardBrand?.storageKey
}

data class SaleMachineSnapshot(
    val machineId: String,
    val machineName: String,
    val debitRateBps: Int,
    val creditDefaultRateBps: Int,
    val brandRatesEnabled: Boolean,
    val installmentsEnabled: Boolean,
    val appliedBrand: CardBrand?,
    val appliedInstallments: Int?,
    val automaticRateBps: Int,
    val manualRateOverrideBps: Int?,
    val effectiveRateBps: Int,
    val appliedBrandKey: String? = appliedBrand?.storageKey,
) {
    val resolvedAppliedBrandKey: String?
        get() = appliedBrandKey ?: appliedBrand?.storageKey

    val appliedBrandDisplayLabel: String?
        get() = appliedBrand?.label ?: CustomBrandKeyCodec.resolveDisplayLabel(appliedBrandKey)
}

data class SaleItemParticipantSnapshot(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val originalUnitAmount: MoneyCents,
    val originalSubtotal: MoneyCents,
    val discountAmount: MoneyCents,
    val grossAmount: MoneyCents,
    val feeAmount: MoneyCents,
    val netAmount: MoneyCents,
)

data class SaleItemCalculationResult(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceOriginal: MoneyCents,
    val originalSubtotal: MoneyCents,
    val discountAmount: MoneyCents,
    val finalAmount: MoneyCents,
    val appliedFeeRateBps: Int,
    val feeAmount: MoneyCents,
    val netAmount: MoneyCents,
    val feeResponsibilityMode: FeeResponsibilityMode,
    val feeOwnerPersonId: String?,
    val feeOwnerPersonName: String?,
    val participantSplits: List<SaleItemParticipantSnapshot>,
)

data class SalePersonTotalSnapshot(
    val personId: String,
    val personName: String,
    val personColorIndex: Int,
    val grossAmount: MoneyCents,
    val feeAmount: MoneyCents,
    val netAmount: MoneyCents,
)

data class SaleCalculationResult(
    val items: List<SaleItemCalculationResult>,
    val personTotals: List<SalePersonTotalSnapshot>,
    val originalSubtotal: MoneyCents,
    val discountTotal: MoneyCents,
    val finalAmount: MoneyCents,
    val feeTotal: MoneyCents,
    val netTotal: MoneyCents,
    val paymentMethod: SalePaymentMethod,
    val machineSnapshot: SaleMachineSnapshot?,
    val effectiveFeeRateBps: Int,
    val discountConfig: SaleDiscountConfigSnapshot,
    val note: String,
)

/**
 * Representa uma venda persistida no Room (Etapa 3B-1), preservando o snapshot financeiro imutável
 * calculado pelo SaleFinancialEngine e as datas originais de criação e atualização.
 */
data class PersistedSale(
    val id: String,
    val soldAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val calculation: SaleCalculationResult,
) {
    /**
     * Reconstrói a lista de [Product] e [SaleItemInput] a partir dos valores unitários originais
     * salvos no snapshot (sem desconto aplicado), garantindo que uma edição posterior nunca aplique
     * o desconto em duplicidade sobre um preço já descontado.
     */
    fun toSnapshotItemInputs(
        quantityOverrides: Map<String, Int> = emptyMap(),
    ): List<SaleItemInput> {
        return calculation.items.map { item ->
            val targetQuantity = quantityOverrides[item.productId] ?: item.quantity
            SaleItemInput(
                product = item.toSnapshotProduct(soldAtEpochMs, updatedAtEpochMs),
                quantity = targetQuantity,
            )
        }
    }

    /**
     * Reconstrói o [SaleCalculationInput] preservando os preços unitários originais dos itens,
     * o valor final cobrado e os demais parâmetros para edição segura no SaleFinancialEngine.
     */
    fun toEditableCalculationInput(
        overrideItems: List<SaleItemInput>? = null,
        overrideFinalAmount: MoneyCents? = calculation.finalAmount,
        overrideDiscountMode: SaleDiscountInput? = null,
        overridePaymentMethod: SalePaymentMethod? = null,
        overrideMachine: Machine? = null,
        overrideCardBrand: CardBrand? = calculation.machineSnapshot?.appliedBrand,
        overrideInstallments: Int? = calculation.machineSnapshot?.appliedInstallments,
        overrideManualRateOverrideBps: Int? = calculation.machineSnapshot?.manualRateOverrideBps,
        overrideNote: String? = null,
        overrideCardBrandKey: String? = null,
        overrideProtectedDiscountPersonIds: Set<String>? = null,
    ): SaleCalculationInput {
        val resolvedMethod = overridePaymentMethod ?: calculation.paymentMethod
        val resolvedDiscountMode = overrideDiscountMode ?: when (val config = calculation.discountConfig) {
            is SaleDiscountConfigSnapshot.Proportional -> SaleDiscountInput.Proportional
            is SaleDiscountConfigSnapshot.SpecificProduct -> SaleDiscountInput.SpecificProduct(config.productId)
            is SaleDiscountConfigSnapshot.SelectedProducts -> SaleDiscountInput.SelectedProducts(config.productIds.toSet())
        }
        val resolvedProtectedIds = overrideProtectedDiscountPersonIds
            ?: com.mixvendas3d.app.domain.calculation.SaleDiscountPolicy.inferProtectedPersonIdsFromSnapshot(calculation)
        val resolvedMachine = if (resolvedMethod.usesMachine) {
            overrideMachine ?: calculation.machineSnapshot?.toSnapshotMachine(
                paymentMethod = resolvedMethod,
                soldAtEpochMs = soldAtEpochMs,
                updatedAtEpochMs = updatedAtEpochMs,
            )
        } else {
            null
        }
        val snapshotBrand = calculation.machineSnapshot?.appliedBrand
        val snapshotBrandKey = calculation.machineSnapshot?.resolvedAppliedBrandKey
        val resolvedBrandKey = when {
            overrideCardBrandKey != null -> overrideCardBrandKey
            overrideCardBrand != snapshotBrand -> overrideCardBrand?.storageKey
            snapshotBrandKey != null -> snapshotBrandKey
            else -> overrideCardBrand?.storageKey
        }
        val resolvedBrand = CardBrand.fromStorageKey(resolvedBrandKey)
        return SaleCalculationInput(
            items = overrideItems ?: toSnapshotItemInputs(),
            finalAmountOverride = overrideFinalAmount,
            discountMode = resolvedDiscountMode,
            paymentMethod = resolvedMethod,
            machine = resolvedMachine,
            cardBrand = resolvedBrand,
            installments = overrideInstallments,
            manualRateOverrideBps = overrideManualRateOverrideBps,
            note = overrideNote ?: calculation.note,
            cardBrandKey = resolvedBrandKey,
            protectedDiscountPersonIds = resolvedProtectedIds,
        )
    }
}

fun SaleItemCalculationResult.toSnapshotProduct(
    createdAtEpochMs: Long = 0L,
    updatedAtEpochMs: Long = 0L,
): Product = Product(
    id = productId,
    name = productName,
    price = unitPriceOriginal,
    active = true,
    feeResponsibilityMode = feeResponsibilityMode,
    feeOwnerPersonId = feeOwnerPersonId,
    participants = participantSplits.map { split ->
        ProductParticipantSplit(
            personId = split.personId,
            personName = split.personName,
            personColorIndex = split.personColorIndex,
            amount = split.originalUnitAmount,
        )
    },
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun SaleMachineSnapshot.toSnapshotMachine(
    paymentMethod: SalePaymentMethod,
    soldAtEpochMs: Long = 0L,
    updatedAtEpochMs: Long = 0L,
): Machine {
    val effectiveBrandKey = resolvedAppliedBrandKey
    val reconstructedBrands = if (paymentMethod == SalePaymentMethod.CREDIT && brandRatesEnabled && appliedBrand != null) {
        val baseBrandRate = if (installmentsEnabled && (appliedInstallments ?: 1) > 1) {
            automaticRateBps.coerceAtMost(10_000)
        } else {
            automaticRateBps
        }
        mapOf(appliedBrand to baseBrandRate)
    } else {
        emptyMap()
    }
    val reconstructedDebitBrands = if (paymentMethod == SalePaymentMethod.DEBIT && brandRatesEnabled && appliedBrand != null) {
        mapOf(appliedBrand to automaticRateBps)
    } else {
        emptyMap()
    }
    val reconstructedCustomBrands = if (
        paymentMethod.usesMachine &&
        brandRatesEnabled &&
        appliedBrand == null &&
        effectiveBrandKey != null
    ) {
        val decodedName = CustomBrandKeyCodec.decodeNameOrNull(effectiveBrandKey)
        if (decodedName != null) {
            if (paymentMethod == SalePaymentMethod.DEBIT) {
                listOf(
                    CustomCardBrandRate(
                        key = effectiveBrandKey,
                        name = decodedName,
                        creditRateBps = null,
                        debitRateBps = automaticRateBps,
                    ),
                )
            } else {
                val baseCustomRate = if (installmentsEnabled && (appliedInstallments ?: 1) > 1) {
                    automaticRateBps.coerceAtMost(10_000)
                } else {
                    automaticRateBps
                }
                listOf(
                    CustomCardBrandRate(
                        key = effectiveBrandKey,
                        name = decodedName,
                        creditRateBps = baseCustomRate,
                    ),
                )
            }
        } else {
            emptyList()
        }
    } else {
        emptyList()
    }
    val reconstructedInstallments = if (paymentMethod == SalePaymentMethod.CREDIT && installmentsEnabled && appliedInstallments != null) {
        val baseRate = if (brandRatesEnabled && effectiveBrandKey != null) {
            if (appliedBrand != null) {
                reconstructedBrands[appliedBrand] ?: creditDefaultRateBps
            } else {
                reconstructedCustomBrands.firstOrNull { it.key == effectiveBrandKey }?.rateBps ?: creditDefaultRateBps
            }
        } else {
            creditDefaultRateBps
        }
        val addon = (automaticRateBps - baseRate).coerceAtLeast(0)
        mapOf(appliedInstallments to addon)
    } else {
        emptyMap()
    }
    val reconstructedMaxInstallments = if (
        paymentMethod == SalePaymentMethod.CREDIT &&
        installmentsEnabled &&
        appliedInstallments != null
    ) {
        appliedInstallments.coerceIn(1, InstallmentTiers.SUPPORTED.max())
    } else {
        null
    }
    return Machine(
        id = machineId,
        name = machineName,
        debitRateBps = debitRateBps,
        creditEnabled = true,
        creditDefaultRateBps = creditDefaultRateBps,
        brandRatesEnabled = brandRatesEnabled,
        brandRatesBps = reconstructedBrands,
        brandDebitRatesBps = reconstructedDebitBrands,
        installmentsEnabled = installmentsEnabled,
        installmentAddonsBps = reconstructedInstallments,
        createdAtEpochMs = soldAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        maxInstallments = reconstructedMaxInstallments,
        customBrandRates = reconstructedCustomBrands,
    )
}

