package com.mixvendas3d.app.data.repository

import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SaleDiscountConfigSnapshot
import com.mixvendas3d.app.domain.models.SaleItemCalculationResult
import com.mixvendas3d.app.domain.models.SaleItemParticipantSnapshot
import com.mixvendas3d.app.domain.models.SaleMachineSnapshot
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot

internal data class MappedSaleEntities(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>,
    val participants: List<SaleItemParticipantEntity>,
    val personTotals: List<SalePersonTotalEntity>,
)

internal object SaleEntityMapper {
    private const val DISCOUNT_MODE_PROPORTIONAL = "PROPORTIONAL"
    private const val DISCOUNT_MODE_SPECIFIC_PRODUCT = "SPECIFIC_PRODUCT"
    private const val DISCOUNT_MODE_SELECTED_PRODUCTS = "SELECTED_PRODUCTS"

    fun toEntities(
        saleId: String,
        soldAtEpochMs: Long,
        updatedAtEpochMs: Long,
        result: SaleCalculationResult,
        itemIdGenerator: () -> String,
    ): MappedSaleEntities {
        val (discountModeKey, discountTargetId, discountTargetName) = when (val cfg = result.discountConfig) {
            is SaleDiscountConfigSnapshot.Proportional -> Triple(DISCOUNT_MODE_PROPORTIONAL, null, null)
            is SaleDiscountConfigSnapshot.SpecificProduct -> Triple(
                DISCOUNT_MODE_SPECIFIC_PRODUCT,
                cfg.productId,
                cfg.productNameSnapshot,
            )
            is SaleDiscountConfigSnapshot.SelectedProducts -> Triple(
                DISCOUNT_MODE_SELECTED_PRODUCTS,
                cfg.productIds.joinToString(","),
                cfg.productNameSnapshots.joinToString(", "),
            )
        }
        val machine = result.machineSnapshot

        val saleEntity = SaleEntity(
            id = saleId,
            soldAtEpochMs = soldAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            paymentMethod = result.paymentMethod.storageKey,
            originalSubtotalCents = result.originalSubtotal.rawCents,
            discountTotalCents = result.discountTotal.rawCents,
            finalAmountCents = result.finalAmount.rawCents,
            feeTotalCents = result.feeTotal.rawCents,
            netTotalCents = result.netTotal.rawCents,
            discountMode = discountModeKey,
            discountTargetProductId = discountTargetId,
            discountTargetProductName = discountTargetName,
            machineId = machine?.machineId,
            machineName = machine?.machineName,
            machineDebitRateBps = machine?.debitRateBps,
            machineCreditDefaultRateBps = machine?.creditDefaultRateBps,
            machineBrandRatesEnabled = machine?.brandRatesEnabled,
            machineInstallmentsEnabled = machine?.installmentsEnabled,
            cardBrand = machine?.resolvedAppliedBrandKey,
            installments = machine?.appliedInstallments,
            automaticRateBps = machine?.automaticRateBps,
            manualRateOverrideBps = machine?.manualRateOverrideBps,
            effectiveFeeRateBps = result.effectiveFeeRateBps,
            note = result.note,
        )

        val itemEntities = ArrayList<SaleItemEntity>(result.items.size)
        val participantEntities = ArrayList<SaleItemParticipantEntity>()

        result.items.forEachIndexed { itemIndex, item ->
            val saleItemId = itemIdGenerator()
            itemEntities.add(
                SaleItemEntity(
                    id = saleItemId,
                    saleId = saleId,
                    sortOrder = itemIndex,
                    productId = item.productId,
                    productName = item.productName,
                    quantity = item.quantity,
                    unitPriceOriginalCents = item.unitPriceOriginal.rawCents,
                    originalSubtotalCents = item.originalSubtotal.rawCents,
                    discountAmountCents = item.discountAmount.rawCents,
                    finalAmountCents = item.finalAmount.rawCents,
                    appliedFeeRateBps = item.appliedFeeRateBps,
                    feeAmountCents = item.feeAmount.rawCents,
                    netAmountCents = item.netAmount.rawCents,
                    feeResponsibilityMode = item.feeResponsibilityMode.storageKey,
                    feeOwnerPersonId = item.feeOwnerPersonId,
                    feeOwnerPersonName = item.feeOwnerPersonName,
                ),
            )
            item.participantSplits.forEachIndexed { splitIndex, split ->
                participantEntities.add(
                    SaleItemParticipantEntity(
                        saleItemId = saleItemId,
                        personId = split.personId,
                        sortOrder = splitIndex,
                        personName = split.personName,
                        personColorIndex = split.personColorIndex,
                        originalUnitAmountCents = split.originalUnitAmount.rawCents,
                        originalSubtotalCents = split.originalSubtotal.rawCents,
                        discountAmountCents = split.discountAmount.rawCents,
                        grossAmountCents = split.grossAmount.rawCents,
                        feeAmountCents = split.feeAmount.rawCents,
                        netAmountCents = split.netAmount.rawCents,
                    ),
                )
            }
        }

        val personTotalEntities = result.personTotals.mapIndexed { personIndex, total ->
            SalePersonTotalEntity(
                saleId = saleId,
                personId = total.personId,
                sortOrder = personIndex,
                personName = total.personName,
                personColorIndex = total.personColorIndex,
                grossAmountCents = total.grossAmount.rawCents,
                feeAmountCents = total.feeAmount.rawCents,
                netAmountCents = total.netAmount.rawCents,
            )
        }

        return MappedSaleEntities(
            sale = saleEntity,
            items = itemEntities,
            participants = participantEntities,
            personTotals = personTotalEntities,
        )
    }

    fun toDomain(row: SaleWithDetails): PersistedSale {
        val sale = row.sale
        val paymentMethod = SalePaymentMethod.fromStorageKey(sale.paymentMethod)
            ?: error("Método de pagamento desconhecido no snapshot salvo: ${sale.paymentMethod}")

        val machineSnapshot = if (sale.machineId != null && sale.machineName != null) {
            SaleMachineSnapshot(
                machineId = sale.machineId,
                machineName = sale.machineName,
                debitRateBps = sale.machineDebitRateBps ?: 0,
                creditDefaultRateBps = sale.machineCreditDefaultRateBps ?: 0,
                brandRatesEnabled = sale.machineBrandRatesEnabled ?: false,
                installmentsEnabled = sale.machineInstallmentsEnabled ?: false,
                appliedBrand = CardBrand.fromStorageKey(sale.cardBrand),
                appliedInstallments = sale.installments,
                automaticRateBps = sale.automaticRateBps ?: sale.effectiveFeeRateBps,
                manualRateOverrideBps = sale.manualRateOverrideBps,
                effectiveRateBps = sale.effectiveFeeRateBps,
                appliedBrandKey = sale.cardBrand,
            )
        } else {
            null
        }

        val discountConfig = when (sale.discountMode) {
            DISCOUNT_MODE_SPECIFIC_PRODUCT -> SaleDiscountConfigSnapshot.SpecificProduct(
                productId = sale.discountTargetProductId.orEmpty(),
                productNameSnapshot = sale.discountTargetProductName.orEmpty(),
            )
            DISCOUNT_MODE_SELECTED_PRODUCTS -> {
                val parsedIds = sale.discountTargetProductId
                    .orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val itemNamesById = row.items.associate { it.item.productId to it.item.productName }
                val parsedNames = parsedIds.map { id ->
                    itemNamesById[id] ?: id
                }
                SaleDiscountConfigSnapshot.SelectedProducts(
                    productIds = parsedIds,
                    productNameSnapshots = parsedNames,
                )
            }
            else -> SaleDiscountConfigSnapshot.Proportional
        }

        val items = row.items
            .sortedBy { it.item.sortOrder }
            .map { relItem ->
                val itemEntity = relItem.item
                val feeResponsibilityMode = FeeResponsibilityMode.fromStorageKeyOrNull(itemEntity.feeResponsibilityMode)
                    ?: error(
                        "Modo de responsabilidade de tarifa inválido (${itemEntity.feeResponsibilityMode}) " +
                            "no item \"${itemEntity.productName}\" (id=${itemEntity.id}) da venda ${sale.id}.",
                    )
                SaleItemCalculationResult(
                    productId = itemEntity.productId,
                    productName = itemEntity.productName,
                    quantity = itemEntity.quantity,
                    unitPriceOriginal = MoneyCents(itemEntity.unitPriceOriginalCents),
                    originalSubtotal = MoneyCents(itemEntity.originalSubtotalCents),
                    discountAmount = MoneyCents(itemEntity.discountAmountCents),
                    finalAmount = MoneyCents(itemEntity.finalAmountCents),
                    appliedFeeRateBps = itemEntity.appliedFeeRateBps,
                    feeAmount = MoneyCents(itemEntity.feeAmountCents),
                    netAmount = MoneyCents(itemEntity.netAmountCents),
                    feeResponsibilityMode = feeResponsibilityMode,
                    feeOwnerPersonId = itemEntity.feeOwnerPersonId,
                    feeOwnerPersonName = itemEntity.feeOwnerPersonName,
                    participantSplits = relItem.participants
                        .sortedBy { it.sortOrder }
                        .map { part ->
                            SaleItemParticipantSnapshot(
                                personId = part.personId,
                                personName = part.personName,
                                personColorIndex = part.personColorIndex,
                                originalUnitAmount = MoneyCents(part.originalUnitAmountCents),
                                originalSubtotal = MoneyCents(part.originalSubtotalCents),
                                discountAmount = MoneyCents(part.discountAmountCents),
                                grossAmount = MoneyCents(part.grossAmountCents),
                                feeAmount = MoneyCents(part.feeAmountCents),
                                netAmount = MoneyCents(part.netAmountCents),
                            )
                        },
                )
            }

        val personTotals = row.personTotals
            .sortedBy { it.sortOrder }
            .map { total ->
                SalePersonTotalSnapshot(
                    personId = total.personId,
                    personName = total.personName,
                    personColorIndex = total.personColorIndex,
                    grossAmount = MoneyCents(total.grossAmountCents),
                    feeAmount = MoneyCents(total.feeAmountCents),
                    netAmount = MoneyCents(total.netAmountCents),
                )
            }

        return PersistedSale(
            id = sale.id,
            soldAtEpochMs = sale.soldAtEpochMs,
            updatedAtEpochMs = sale.updatedAtEpochMs,
            calculation = SaleCalculationResult(
                items = items,
                personTotals = personTotals,
                originalSubtotal = MoneyCents(sale.originalSubtotalCents),
                discountTotal = MoneyCents(sale.discountTotalCents),
                finalAmount = MoneyCents(sale.finalAmountCents),
                feeTotal = MoneyCents(sale.feeTotalCents),
                netTotal = MoneyCents(sale.netTotalCents),
                paymentMethod = paymentMethod,
                machineSnapshot = machineSnapshot,
                effectiveFeeRateBps = sale.effectiveFeeRateBps,
                discountConfig = discountConfig,
                note = sale.note,
            ),
        )
    }
}
