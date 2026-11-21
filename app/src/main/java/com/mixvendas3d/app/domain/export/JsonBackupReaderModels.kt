package com.mixvendas3d.app.domain.export

import java.util.Collections

enum class JsonBackupErrorCode {
    EMPTY_INPUT,
    FILE_TOO_LARGE,
    INVALID_ENCODING,
    SYNTAX_ERROR,
    MISSING_REQUIRED_FIELD,
    INVALID_FIELD_TYPE,
    INVALID_LONG_PRECISION_OR_OVERFLOW,
    UNSUPPORTED_FORMAT,
    INCOMPATIBLE_SCHEMA_VERSION,
    INCOMPATIBLE_APPLICATION_ID,
    INCOMPATIBLE_ROOM_VERSION,
    INVALID_TIMESTAMP,
    MISSING_COLLECTION,
    COUNT_MISMATCH,
    RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR,
}

data class JsonBackupValidationError(
    val code: JsonBackupErrorCode,
    val message: String,
    val entityId: String? = null,
    val field: String? = null,
)

data class JsonBackupCollectionSummary(
    val preferencesCount: Int = 0,
    val peopleCount: Int = 0,
    val productsCount: Int = 0,
    val productParticipantsCount: Int = 0,
    val machinesCount: Int = 0,
    val machineBrandRatesCount: Int = 0,
    val machineInstallmentRatesCount: Int = 0,
    val salesCount: Int = 0,
    val saleItemsCount: Int = 0,
    val saleItemParticipantsCount: Int = 0,
    val salePersonTotalsCount: Int = 0,
    val productCategoriesCount: Int = 0,
) {
    val totalParticipantsCount: Int
        get() = productParticipantsCount + saleItemParticipantsCount

    val otherCollectionsTotalCount: Int
        get() = preferencesCount +
            productParticipantsCount +
            machineBrandRatesCount +
            machineInstallmentRatesCount +
            saleItemParticipantsCount +
            salePersonTotalsCount +
            productCategoriesCount

    val otherCollectionsCounts: Map<String, Int>
        get() = Collections.unmodifiableMap(
            linkedMapOf(
                "preferences" to preferencesCount,
                "productParticipants" to productParticipantsCount,
                "machineBrandRates" to machineBrandRatesCount,
                "machineInstallmentRates" to machineInstallmentRatesCount,
                "saleItemParticipants" to saleItemParticipantsCount,
                "salePersonTotals" to salePersonTotalsCount,
                "productCategories" to productCategoriesCount,
            ),
        )

    val allCollectionsCounts: Map<String, Int>
        get() = Collections.unmodifiableMap(
            linkedMapOf(
                "preferences" to preferencesCount,
                "people" to peopleCount,
                "products" to productsCount,
                "productParticipants" to productParticipantsCount,
                "machines" to machinesCount,
                "machineBrandRates" to machineBrandRatesCount,
                "machineInstallmentRates" to machineInstallmentRatesCount,
                "sales" to salesCount,
                "saleItems" to saleItemsCount,
                "saleItemParticipants" to saleItemParticipantsCount,
                "salePersonTotals" to salePersonTotalsCount,
                "productCategories" to productCategoriesCount,
            ),
        )

    val totalRecordsCount: Int
        get() = preferencesCount +
            peopleCount +
            productsCount +
            productParticipantsCount +
            machinesCount +
            machineBrandRatesCount +
            machineInstallmentRatesCount +
            salesCount +
            saleItemsCount +
            saleItemParticipantsCount +
            salePersonTotalsCount +
            productCategoriesCount
}

class ValidatedBackupData(
    val format: String,
    val backupSchemaVersion: Int,
    val applicationId: String,
    val roomSchemaVersion: Int,
    val exportedAtEpochMs: Long,
    preferences: List<PreferenceDto>,
    people: List<PersonDto>,
    products: List<ProductDto>,
    productParticipants: List<ProductParticipantDto>,
    machines: List<MachineDto>,
    machineBrandRates: List<MachineBrandRateDto>,
    machineInstallmentRates: List<MachineInstallmentRateDto>,
    sales: List<SaleDto>,
    saleItems: List<SaleItemDto>,
    saleItemParticipants: List<SaleItemParticipantDto>,
    salePersonTotals: List<SalePersonTotalDto>,
    declaredCounts: Map<String, Int>,
    productCategories: List<ProductCategoryDto> = emptyList(),
) {
    val preferences: List<PreferenceDto> = Collections.unmodifiableList(preferences.toList())
    val people: List<PersonDto> = Collections.unmodifiableList(people.toList())
    val products: List<ProductDto> = Collections.unmodifiableList(products.toList())
    val productParticipants: List<ProductParticipantDto> = Collections.unmodifiableList(productParticipants.toList())
    val machines: List<MachineDto> = Collections.unmodifiableList(machines.toList())
    val machineBrandRates: List<MachineBrandRateDto> = Collections.unmodifiableList(machineBrandRates.toList())
    val machineInstallmentRates: List<MachineInstallmentRateDto> = Collections.unmodifiableList(machineInstallmentRates.toList())
    val sales: List<SaleDto> = Collections.unmodifiableList(sales.toList())
    val saleItems: List<SaleItemDto> = Collections.unmodifiableList(saleItems.toList())
    val saleItemParticipants: List<SaleItemParticipantDto> = Collections.unmodifiableList(saleItemParticipants.toList())
    val salePersonTotals: List<SalePersonTotalDto> = Collections.unmodifiableList(salePersonTotals.toList())
    val productCategories: List<ProductCategoryDto> = Collections.unmodifiableList(productCategories.toList())
    val declaredCounts: Map<String, Int> = Collections.unmodifiableMap(LinkedHashMap(declaredCounts))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValidatedBackupData) return false
        return format == other.format &&
            backupSchemaVersion == other.backupSchemaVersion &&
            applicationId == other.applicationId &&
            roomSchemaVersion == other.roomSchemaVersion &&
            exportedAtEpochMs == other.exportedAtEpochMs &&
            preferences == other.preferences &&
            people == other.people &&
            products == other.products &&
            productParticipants == other.productParticipants &&
            machines == other.machines &&
            machineBrandRates == other.machineBrandRates &&
            machineInstallmentRates == other.machineInstallmentRates &&
            sales == other.sales &&
            saleItems == other.saleItems &&
            saleItemParticipants == other.saleItemParticipants &&
            salePersonTotals == other.salePersonTotals &&
            declaredCounts == other.declaredCounts
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + backupSchemaVersion
        result = 31 * result + applicationId.hashCode()
        result = 31 * result + roomSchemaVersion
        result = 31 * result + exportedAtEpochMs.hashCode()
        result = 31 * result + preferences.hashCode()
        result = 31 * result + people.hashCode()
        result = 31 * result + products.hashCode()
        result = 31 * result + productParticipants.hashCode()
        result = 31 * result + machines.hashCode()
        result = 31 * result + machineBrandRates.hashCode()
        result = 31 * result + machineInstallmentRates.hashCode()
        result = 31 * result + sales.hashCode()
        result = 31 * result + saleItems.hashCode()
        result = 31 * result + saleItemParticipants.hashCode()
        result = 31 * result + salePersonTotals.hashCode()
        result = 31 * result + declaredCounts.hashCode()
        return result
    }
}

data class JsonBackupReadResult(
    val isValid: Boolean,
    val format: String? = null,
    val backupSchemaVersion: Int? = null,
    val applicationId: String? = null,
    val roomSchemaVersion: Int? = null,
    val exportedAtEpochMs: Long? = null,
    val peopleCount: Int = 0,
    val productsCount: Int = 0,
    val machinesCount: Int = 0,
    val salesCount: Int = 0,
    val saleItemsCount: Int = 0,
    val participantsCount: Int = 0,
    val otherCollectionsCounts: Map<String, Int> = emptyMap(),
    val collectionSummary: JsonBackupCollectionSummary = JsonBackupCollectionSummary(),
    val errors: List<JsonBackupValidationError> = emptyList(),
    val validatedData: ValidatedBackupData? = null,
) {
    val totalRecordsCount: Int
        get() = collectionSummary.totalRecordsCount
}
