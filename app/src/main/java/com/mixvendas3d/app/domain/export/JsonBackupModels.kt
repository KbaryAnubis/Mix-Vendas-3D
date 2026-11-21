package com.mixvendas3d.app.domain.export

data class MixVendasBackupDocument(
    val format: String = "MIX_VENDAS_3D_BACKUP",
    val backupSchemaVersion: Int = 1,
    val applicationId: String = "com.mixvendas3d.app",
    val exportedAtEpochMs: Long,
    val roomSchemaVersion: Int = 7,
    val collections: BackupCollections,
    val counts: Map<String, Int>,
)

data class BackupCollections(
    val preferences: List<PreferenceDto>,
    val people: List<PersonDto>,
    val products: List<ProductDto>,
    val productParticipants: List<ProductParticipantDto>,
    val machines: List<MachineDto>,
    val machineBrandRates: List<MachineBrandRateDto>,
    val machineInstallmentRates: List<MachineInstallmentRateDto>,
    val sales: List<SaleDto>,
    val saleItems: List<SaleItemDto>,
    val saleItemParticipants: List<SaleItemParticipantDto>,
    val salePersonTotals: List<SalePersonTotalDto>,
    val productCategories: List<ProductCategoryDto> = emptyList(),
)

data class ProductCategoryDto(
    val id: String,
    val name: String,
    val normalizedName: String,
    val parentId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class PreferenceDto(
    val key: String,
    val value: String,
    val updatedAtEpochMs: Long,
)

data class PersonDto(
    val id: String,
    val name: String,
    val normalizedName: String,
    val colorIndex: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)

data class ProductDto(
    val id: String,
    val name: String,
    val priceCents: Long,
    val active: Boolean,
    val feeResponsibilityMode: String,
    val feeOwnerPersonId: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
)

data class ProductParticipantDto(
    val productId: String,
    val personId: String,
    val amountCents: Long,
    val sortOrder: Int,
)

data class MachineDto(
    val id: String,
    val name: String,
    val normalizedName: String,
    val debitRateBps: Int,
    val creditEnabled: Boolean,
    val creditDefaultRateBps: Int,
    val brandRatesEnabled: Boolean,
    val installmentsEnabled: Boolean,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val maxInstallments: Int? = null,
)

data class MachineBrandRateDto(
    val machineId: String,
    val brandKey: String,
    val rateBps: Int? = null,
    val debitRateBps: Int? = null,
)

data class MachineInstallmentRateDto(
    val machineId: String,
    val installments: Int,
    val addonBps: Int,
)

data class SaleDto(
    val id: String,
    val soldAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val paymentMethod: String,
    val originalSubtotalCents: Long,
    val discountTotalCents: Long,
    val finalAmountCents: Long,
    val feeTotalCents: Long,
    val netTotalCents: Long,
    val discountMode: String,
    val discountTargetProductId: String?,
    val discountTargetProductName: String?,
    val machineId: String?,
    val machineName: String?,
    val machineDebitRateBps: Int?,
    val machineCreditDefaultRateBps: Int?,
    val machineBrandRatesEnabled: Boolean?,
    val machineInstallmentsEnabled: Boolean?,
    val cardBrand: String?,
    val installments: Int?,
    val automaticRateBps: Int?,
    val manualRateOverrideBps: Int?,
    val effectiveFeeRateBps: Int,
    val note: String,
)

data class SaleItemDto(
    val id: String,
    val saleId: String,
    val sortOrder: Int,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPriceOriginalCents: Long,
    val originalSubtotalCents: Long,
    val discountAmountCents: Long,
    val finalAmountCents: Long,
    val appliedFeeRateBps: Int,
    val feeAmountCents: Long,
    val netAmountCents: Long,
    val feeResponsibilityMode: String,
    val feeOwnerPersonId: String?,
    val feeOwnerPersonName: String?,
)

data class SaleItemParticipantDto(
    val saleItemId: String,
    val personId: String,
    val sortOrder: Int,
    val personName: String,
    val personColorIndex: Int,
    val originalUnitAmountCents: Long,
    val originalSubtotalCents: Long,
    val discountAmountCents: Long,
    val grossAmountCents: Long,
    val feeAmountCents: Long,
    val netAmountCents: Long,
)

data class SalePersonTotalDto(
    val saleId: String,
    val personId: String,
    val sortOrder: Int,
    val personName: String,
    val personColorIndex: Int,
    val grossAmountCents: Long,
    val feeAmountCents: Long,
    val netAmountCents: Long,
)
