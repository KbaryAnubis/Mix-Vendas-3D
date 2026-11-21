package com.mixvendas3d.app.domain.export

import java.util.Collections

enum class PreviewIssueType {
    PLAN_BACKUP_MISMATCH,
    VALIDATION_BACKUP_MISMATCH,
    DECISIONS_MISMATCH,
    UNKNOWN_CONFLICT_KEY,
    DECISION_VALIDATION_NOT_READY,
    LOCAL_STATE_CHANGED_SINCE_PLANNING,
    IDENTITY_NAME_COLLISION,
    REFERENTIAL_OR_DEPENDENCY_ERROR,
    FINANCIAL_VALIDATION_ERROR,
}

enum class PreviewChangeOperation {
    ADD,
    REPLACE,
}

enum class PreviewChangeScope {
    STANDALONE_RECORD,
    ATOMIC_UNIT,
}

data class PreviewValidationIssue(
    val type: PreviewIssueType,
    val collection: BackupCollectionType? = null,
    val unitType: MergeUnitType? = null,
    val targetId: String? = null,
    val message: String,
)

class PreviewChangedEntry(
    val scope: PreviewChangeScope,
    val operation: PreviewChangeOperation,
    val collection: BackupCollectionType? = null,
    val unitType: MergeUnitType? = null,
    val targetId: String,
    memberCountsByCollection: Map<BackupCollectionType, Int>,
) {
    val memberCountsByCollection: Map<BackupCollectionType, Int> =
        Collections.unmodifiableMap(LinkedHashMap(memberCountsByCollection))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewChangedEntry) return false
        return scope == other.scope &&
            operation == other.operation &&
            collection == other.collection &&
            unitType == other.unitType &&
            targetId == other.targetId &&
            memberCountsByCollection == other.memberCountsByCollection
    }

    override fun hashCode(): Int {
        var result = scope.hashCode()
        result = 31 * result + operation.hashCode()
        result = 31 * result + (collection?.hashCode() ?: 0)
        result = 31 * result + (unitType?.hashCode() ?: 0)
        result = 31 * result + targetId.hashCode()
        result = 31 * result + memberCountsByCollection.hashCode()
        return result
    }
}

data class PreviewFinancialSummary(
    val localSalesCount: Int,
    val localTotalFinalAmountCents: Long,
    val localTotalFeeAmountCents: Long,
    val localTotalNetAmountCents: Long,
    val finalSalesCount: Int,
    val finalTotalFinalAmountCents: Long,
    val finalTotalFeeAmountCents: Long,
    val finalTotalNetAmountCents: Long,
    val addedSalesCount: Int,
    val addedSalesFinalAmountCents: Long,
    val addedSalesFeeAmountCents: Long,
    val addedSalesNetAmountCents: Long,
    val replacedSalesCount: Int,
    val replacedSalesFinalDeltaCents: Long,
    val replacedSalesFeeDeltaCents: Long,
    val replacedSalesNetDeltaCents: Long,
    val netFinalAmountDeltaCents: Long,
    val netFeeAmountDeltaCents: Long,
    val netNetAmountDeltaCents: Long,
) {
    companion object {
        val EMPTY = PreviewFinancialSummary(
            localSalesCount = 0,
            localTotalFinalAmountCents = 0L,
            localTotalFeeAmountCents = 0L,
            localTotalNetAmountCents = 0L,
            finalSalesCount = 0,
            finalTotalFinalAmountCents = 0L,
            finalTotalFeeAmountCents = 0L,
            finalTotalNetAmountCents = 0L,
            addedSalesCount = 0,
            addedSalesFinalAmountCents = 0L,
            addedSalesFeeAmountCents = 0L,
            addedSalesNetAmountCents = 0L,
            replacedSalesCount = 0,
            replacedSalesFinalDeltaCents = 0L,
            replacedSalesFeeDeltaCents = 0L,
            replacedSalesNetDeltaCents = 0L,
            netFinalAmountDeltaCents = 0L,
            netFeeAmountDeltaCents = 0L,
            netNetAmountDeltaCents = 0L,
        )
    }
}

data class BackupIdentitySignature(
    val format: String,
    val backupSchemaVersion: Int,
    val applicationId: String,
    val roomSchemaVersion: Int,
    val exportedAtEpochMs: Long,
    val backupPayloadFingerprint: String,
)

class PreviewExecutionContract(
    val expectedLocalStateFingerprint: String,
    expectedLocalCounts: Map<BackupCollectionType, Int>,
    expectedFinalCounts: Map<BackupCollectionType, Int>,
    val backupIdentity: BackupIdentitySignature,
    approvedDecisionsByConflictKey: Map<String, MergeConflictResolution>,
    val authorizedOperationsCount: Int,
    val requiresSingleTransaction: Boolean = true,
    val requiresFinalValidationBeforeCommit: Boolean = true,
    val requiresRollbackOnAnyFailure: Boolean = true,
) {
    val expectedLocalCounts: Map<BackupCollectionType, Int> =
        Collections.unmodifiableMap(LinkedHashMap(expectedLocalCounts))
    val expectedFinalCounts: Map<BackupCollectionType, Int> =
        Collections.unmodifiableMap(LinkedHashMap(expectedFinalCounts))
    val approvedDecisionsByConflictKey: Map<String, MergeConflictResolution> =
        Collections.unmodifiableMap(LinkedHashMap(approvedDecisionsByConflictKey))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewExecutionContract) return false
        return expectedLocalStateFingerprint == other.expectedLocalStateFingerprint &&
            expectedLocalCounts == other.expectedLocalCounts &&
            expectedFinalCounts == other.expectedFinalCounts &&
            backupIdentity == other.backupIdentity &&
            approvedDecisionsByConflictKey == other.approvedDecisionsByConflictKey &&
            authorizedOperationsCount == other.authorizedOperationsCount &&
            requiresSingleTransaction == other.requiresSingleTransaction &&
            requiresFinalValidationBeforeCommit == other.requiresFinalValidationBeforeCommit &&
            requiresRollbackOnAnyFailure == other.requiresRollbackOnAnyFailure
    }

    override fun hashCode(): Int {
        var result = expectedLocalStateFingerprint.hashCode()
        result = 31 * result + expectedLocalCounts.hashCode()
        result = 31 * result + expectedFinalCounts.hashCode()
        result = 31 * result + backupIdentity.hashCode()
        result = 31 * result + approvedDecisionsByConflictKey.hashCode()
        result = 31 * result + authorizedOperationsCount
        result = 31 * result + requiresSingleTransaction.hashCode()
        result = 31 * result + requiresFinalValidationBeforeCommit.hashCode()
        result = 31 * result + requiresRollbackOnAnyFailure.hashCode()
        return result
    }
}

class PreviewMergedCollections(
    preferences: List<PreferenceDto> = emptyList(),
    people: List<PersonDto> = emptyList(),
    products: List<ProductDto> = emptyList(),
    productParticipants: List<ProductParticipantDto> = emptyList(),
    machines: List<MachineDto> = emptyList(),
    machineBrandRates: List<MachineBrandRateDto> = emptyList(),
    machineInstallmentRates: List<MachineInstallmentRateDto> = emptyList(),
    sales: List<SaleDto> = emptyList(),
    saleItems: List<SaleItemDto> = emptyList(),
    saleItemParticipants: List<SaleItemParticipantDto> = emptyList(),
    salePersonTotals: List<SalePersonTotalDto> = emptyList(),
) {
    val preferences: List<PreferenceDto> = Collections.unmodifiableList(preferences.toList())
    val people: List<PersonDto> = Collections.unmodifiableList(people.toList())
    val products: List<ProductDto> = Collections.unmodifiableList(products.toList())
    val productParticipants: List<ProductParticipantDto> = Collections.unmodifiableList(productParticipants.toList())
    val machines: List<MachineDto> = Collections.unmodifiableList(machines.toList())
    val machineBrandRates: List<MachineBrandRateDto> = Collections.unmodifiableList(machineBrandRates.toList())
    val machineInstallmentRates: List<MachineInstallmentRateDto> =
        Collections.unmodifiableList(machineInstallmentRates.toList())
    val sales: List<SaleDto> = Collections.unmodifiableList(sales.toList())
    val saleItems: List<SaleItemDto> = Collections.unmodifiableList(saleItems.toList())
    val saleItemParticipants: List<SaleItemParticipantDto> =
        Collections.unmodifiableList(saleItemParticipants.toList())
    val salePersonTotals: List<SalePersonTotalDto> =
        Collections.unmodifiableList(salePersonTotals.toList())

    fun countsByCollection(): Map<BackupCollectionType, Int> {
        return Collections.unmodifiableMap(
            linkedMapOf(
                BackupCollectionType.PREFERENCES to preferences.size,
                BackupCollectionType.PEOPLE to people.size,
                BackupCollectionType.PRODUCTS to products.size,
                BackupCollectionType.PRODUCT_PARTICIPANTS to productParticipants.size,
                BackupCollectionType.MACHINES to machines.size,
                BackupCollectionType.MACHINE_BRAND_RATES to machineBrandRates.size,
                BackupCollectionType.MACHINE_INSTALLMENT_RATES to machineInstallmentRates.size,
                BackupCollectionType.SALES to sales.size,
                BackupCollectionType.SALE_ITEMS to saleItems.size,
                BackupCollectionType.SALE_ITEM_PARTICIPANTS to saleItemParticipants.size,
                BackupCollectionType.SALE_PERSON_TOTALS to salePersonTotals.size,
            ),
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreviewMergedCollections) return false
        return preferences == other.preferences &&
            people == other.people &&
            products == other.products &&
            productParticipants == other.productParticipants &&
            machines == other.machines &&
            machineBrandRates == other.machineBrandRates &&
            machineInstallmentRates == other.machineInstallmentRates &&
            sales == other.sales &&
            saleItems == other.saleItems &&
            saleItemParticipants == other.saleItemParticipants &&
            salePersonTotals == other.salePersonTotals
    }

    override fun hashCode(): Int {
        var result = preferences.hashCode()
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
        return result
    }
}

class JsonBackupMergePreviewResult(
    val isSafeToApply: Boolean,
    val newRecordsCount: Int,
    val keptRecordsCount: Int,
    val unitsToReplaceCount: Int,
    val ignoredRecordsCount: Int,
    val localOnlyPreservedCount: Int,
    finalTableCounts: Map<BackupCollectionType, Int>,
    changedEntries: List<PreviewChangedEntry>,
    val financialSummary: PreviewFinancialSummary,
    issues: List<PreviewValidationIssue>,
    val mergedCollections: PreviewMergedCollections,
    val executionContract: PreviewExecutionContract,
    val localStateMatchesPlan: Boolean,
) {
    val finalTableCounts: Map<BackupCollectionType, Int> =
        Collections.unmodifiableMap(LinkedHashMap(finalTableCounts))
    val changedEntries: List<PreviewChangedEntry> =
        Collections.unmodifiableList(changedEntries.toList())
    val issues: List<PreviewValidationIssue> =
        Collections.unmodifiableList(issues.toList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsonBackupMergePreviewResult) return false
        return isSafeToApply == other.isSafeToApply &&
            newRecordsCount == other.newRecordsCount &&
            keptRecordsCount == other.keptRecordsCount &&
            unitsToReplaceCount == other.unitsToReplaceCount &&
            ignoredRecordsCount == other.ignoredRecordsCount &&
            localOnlyPreservedCount == other.localOnlyPreservedCount &&
            finalTableCounts == other.finalTableCounts &&
            changedEntries == other.changedEntries &&
            financialSummary == other.financialSummary &&
            issues == other.issues &&
            mergedCollections == other.mergedCollections &&
            executionContract == other.executionContract &&
            localStateMatchesPlan == other.localStateMatchesPlan
    }

    override fun hashCode(): Int {
        var result = isSafeToApply.hashCode()
        result = 31 * result + newRecordsCount
        result = 31 * result + keptRecordsCount
        result = 31 * result + unitsToReplaceCount
        result = 31 * result + ignoredRecordsCount
        result = 31 * result + localOnlyPreservedCount
        result = 31 * result + finalTableCounts.hashCode()
        result = 31 * result + changedEntries.hashCode()
        result = 31 * result + financialSummary.hashCode()
        result = 31 * result + issues.hashCode()
        result = 31 * result + mergedCollections.hashCode()
        result = 31 * result + executionContract.hashCode()
        result = 31 * result + localStateMatchesPlan.hashCode()
        return result
    }
}
