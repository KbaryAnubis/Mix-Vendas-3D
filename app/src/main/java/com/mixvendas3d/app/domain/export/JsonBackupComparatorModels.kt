package com.mixvendas3d.app.domain.export

import java.util.Collections

enum class BackupCollectionType(val key: String) {
    PREFERENCES("preferences"),
    PEOPLE("people"),
    PRODUCT_CATEGORIES("productCategories"),
    PRODUCTS("products"),
    PRODUCT_PARTICIPANTS("productParticipants"),
    MACHINES("machines"),
    MACHINE_BRAND_RATES("machineBrandRates"),
    MACHINE_INSTALLMENT_RATES("machineInstallmentRates"),
    SALES("sales"),
    SALE_ITEMS("saleItems"),
    SALE_ITEM_PARTICIPANTS("saleItemParticipants"),
    SALE_PERSON_TOTALS("salePersonTotals"),
}

enum class RecordComparisonStatus {
    NEW,
    IDENTICAL,
    CONFLICTING,
    LOCAL_ONLY,
}

enum class RelationalRiskType {
    UNIQUE_NAME_COLLISION,
    PRODUCT_SPLIT_INTEGRITY_RISK,
    MACHINE_RATE_INTEGRITY_RISK,
    SALE_FINANCIAL_CONFLICT_RISK,
    SALE_ITEM_UNIQUE_PRODUCT_COLLISION,
}

class ConflictRecord(
    val collection: BackupCollectionType,
    val recordId: String,
    keyParts: Map<String, String>,
    divergentFields: List<String>,
    val hasFinancialOrRateDivergence: Boolean,
) {
    val keyParts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(keyParts))
    val divergentFields: List<String> = Collections.unmodifiableList(divergentFields.toList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConflictRecord) return false
        return collection == other.collection &&
            recordId == other.recordId &&
            keyParts == other.keyParts &&
            divergentFields == other.divergentFields &&
            hasFinancialOrRateDivergence == other.hasFinancialOrRateDivergence
    }

    override fun hashCode(): Int {
        var result = collection.hashCode()
        result = 31 * result + recordId.hashCode()
        result = 31 * result + keyParts.hashCode()
        result = 31 * result + divergentFields.hashCode()
        result = 31 * result + hasFinancialOrRateDivergence.hashCode()
        return result
    }
}

data class RelationalConflictRisk(
    val type: RelationalRiskType,
    val collection: BackupCollectionType,
    val relatedCollection: BackupCollectionType? = null,
    val recordId: String,
    val relatedRecordId: String? = null,
    val message: String,
)

class CollectionComparisonSummary(
    val collection: BackupCollectionType,
    val backupCount: Int,
    val localCount: Int,
    val newCount: Int,
    val identicalCount: Int,
    val conflictingCount: Int,
    val localOnlyCount: Int,
    newRecordIds: List<String>,
    identicalRecordIds: List<String>,
    conflictingRecords: List<ConflictRecord>,
    localOnlyRecordIds: List<String>,
) {
    val newRecordIds: List<String> = Collections.unmodifiableList(newRecordIds.toList())
    val identicalRecordIds: List<String> = Collections.unmodifiableList(identicalRecordIds.toList())
    val conflictingRecords: List<ConflictRecord> = Collections.unmodifiableList(conflictingRecords.toList())
    val localOnlyRecordIds: List<String> = Collections.unmodifiableList(localOnlyRecordIds.toList())

    val conflictingRecordIds: List<String>
        get() = Collections.unmodifiableList(conflictingRecords.map { it.recordId })

    val totalDistinctRecords: Int
        get() = newCount + identicalCount + conflictingCount + localOnlyCount

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CollectionComparisonSummary) return false
        return collection == other.collection &&
            backupCount == other.backupCount &&
            localCount == other.localCount &&
            newCount == other.newCount &&
            identicalCount == other.identicalCount &&
            conflictingCount == other.conflictingCount &&
            localOnlyCount == other.localOnlyCount &&
            newRecordIds == other.newRecordIds &&
            identicalRecordIds == other.identicalRecordIds &&
            conflictingRecords == other.conflictingRecords &&
            localOnlyRecordIds == other.localOnlyRecordIds
    }

    override fun hashCode(): Int {
        var result = collection.hashCode()
        result = 31 * result + backupCount
        result = 31 * result + localCount
        result = 31 * result + newCount
        result = 31 * result + identicalCount
        result = 31 * result + conflictingCount
        result = 31 * result + localOnlyCount
        result = 31 * result + newRecordIds.hashCode()
        result = 31 * result + identicalRecordIds.hashCode()
        result = 31 * result + conflictingRecords.hashCode()
        result = 31 * result + localOnlyRecordIds.hashCode()
        return result
    }
}

class BackupComparisonResult(
    val backupFormat: String,
    val backupSchemaVersion: Int,
    val applicationId: String,
    val roomSchemaVersion: Int,
    val exportedAtEpochMs: Long,
    collectionSummaries: List<CollectionComparisonSummary>,
    relationalRisks: List<RelationalConflictRisk>,
) {
    val collectionSummaries: List<CollectionComparisonSummary> =
        Collections.unmodifiableList(collectionSummaries.toList())

    val summariesByCollection: Map<BackupCollectionType, CollectionComparisonSummary> =
        Collections.unmodifiableMap(collectionSummaries.associateByTo(LinkedHashMap()) { it.collection })

    val relationalRisks: List<RelationalConflictRisk> =
        Collections.unmodifiableList(relationalRisks.toList())

    val totalBackupRecords: Int
        get() = collectionSummaries.sumOf { it.backupCount }

    val totalLocalRecords: Int
        get() = collectionSummaries.sumOf { it.localCount }

    val totalNewCount: Int
        get() = collectionSummaries.sumOf { it.newCount }

    val totalIdenticalCount: Int
        get() = collectionSummaries.sumOf { it.identicalCount }

    val totalConflictingCount: Int
        get() = collectionSummaries.sumOf { it.conflictingCount }

    val totalLocalOnlyCount: Int
        get() = collectionSummaries.sumOf { it.localOnlyCount }

    val hasAnyConflicts: Boolean
        get() = totalConflictingCount > 0

    val hasRelationalRisks: Boolean
        get() = relationalRisks.isNotEmpty()

    val isSafeForAdditiveMerge: Boolean
        get() = !hasAnyConflicts && !hasRelationalRisks

    fun summaryFor(collection: BackupCollectionType): CollectionComparisonSummary {
        return summariesByCollection.getValue(collection)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupComparisonResult) return false
        return backupFormat == other.backupFormat &&
            backupSchemaVersion == other.backupSchemaVersion &&
            applicationId == other.applicationId &&
            roomSchemaVersion == other.roomSchemaVersion &&
            exportedAtEpochMs == other.exportedAtEpochMs &&
            collectionSummaries == other.collectionSummaries &&
            relationalRisks == other.relationalRisks
    }

    override fun hashCode(): Int {
        var result = backupFormat.hashCode()
        result = 31 * result + backupSchemaVersion
        result = 31 * result + applicationId.hashCode()
        result = 31 * result + roomSchemaVersion
        result = 31 * result + exportedAtEpochMs.hashCode()
        result = 31 * result + collectionSummaries.hashCode()
        result = 31 * result + relationalRisks.hashCode()
        return result
    }
}
