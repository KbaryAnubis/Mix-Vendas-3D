package com.mixvendas3d.app.domain.export

import java.util.Collections

enum class MergePlanAction {
    ADD,
    KEEP,
    CONFLICT,
    PENDING_DEPENDENCY,
    BLOCKED,
}

enum class MergeConflictResolution {
    KEEP_LOCAL,
    USE_BACKUP,
    IGNORE_IMPORT,
}

enum class MergeConflictKind {
    SAME_KEY_DATA_DIVERGENCE,
    IDENTITY_NAME_COLLISION,
    RELATED_UNIT_DIVERGENCE,
}

enum class MergeUnitType {
    PRODUCT,
    MACHINE,
    SALE,
}

class PlannedRecordAction(
    val collection: BackupCollectionType,
    val recordId: String,
    keyParts: Map<String, String>,
    val comparisonStatus: RecordComparisonStatus,
    val action: MergePlanAction,
    val relatedUnitType: MergeUnitType? = null,
    val relatedUnitId: String? = null,
    val reason: String? = null,
) {
    val keyParts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(keyParts))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlannedRecordAction) return false
        return collection == other.collection &&
            recordId == other.recordId &&
            keyParts == other.keyParts &&
            comparisonStatus == other.comparisonStatus &&
            action == other.action &&
            relatedUnitType == other.relatedUnitType &&
            relatedUnitId == other.relatedUnitId &&
            reason == other.reason
    }

    override fun hashCode(): Int {
        var result = collection.hashCode()
        result = 31 * result + recordId.hashCode()
        result = 31 * result + keyParts.hashCode()
        result = 31 * result + comparisonStatus.hashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + (relatedUnitType?.hashCode() ?: 0)
        result = 31 * result + (relatedUnitId?.hashCode() ?: 0)
        result = 31 * result + (reason?.hashCode() ?: 0)
        return result
    }
}

class MergeConflictItem(
    val collection: BackupCollectionType,
    val recordId: String,
    keyParts: Map<String, String>,
    val kind: MergeConflictKind,
    divergentFields: List<String>,
    val collidingLocalRecordId: String? = null,
    val relatedUnitType: MergeUnitType? = null,
    val relatedUnitId: String? = null,
    val hasFinancialOrRateDivergence: Boolean = false,
    allowedResolutions: List<MergeConflictResolution> = listOf(
        MergeConflictResolution.KEEP_LOCAL,
        MergeConflictResolution.USE_BACKUP,
        MergeConflictResolution.IGNORE_IMPORT,
    ),
    val selectedResolution: MergeConflictResolution? = null,
) {
    val keyParts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(keyParts))
    val divergentFields: List<String> = Collections.unmodifiableList(divergentFields.toList())
    val allowedResolutions: List<MergeConflictResolution> =
        Collections.unmodifiableList(allowedResolutions.toList())

    fun withResolution(resolution: MergeConflictResolution): MergeConflictItem {
        require(resolution in allowedResolutions) {
            "Resolução $resolution não é permitida para o conflito $collection/$recordId."
        }
        return MergeConflictItem(
            collection = collection,
            recordId = recordId,
            keyParts = keyParts,
            kind = kind,
            divergentFields = divergentFields,
            collidingLocalRecordId = collidingLocalRecordId,
            relatedUnitType = relatedUnitType,
            relatedUnitId = relatedUnitId,
            hasFinancialOrRateDivergence = hasFinancialOrRateDivergence,
            allowedResolutions = allowedResolutions,
            selectedResolution = resolution,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MergeConflictItem) return false
        return collection == other.collection &&
            recordId == other.recordId &&
            keyParts == other.keyParts &&
            kind == other.kind &&
            divergentFields == other.divergentFields &&
            collidingLocalRecordId == other.collidingLocalRecordId &&
            relatedUnitType == other.relatedUnitType &&
            relatedUnitId == other.relatedUnitId &&
            hasFinancialOrRateDivergence == other.hasFinancialOrRateDivergence &&
            allowedResolutions == other.allowedResolutions &&
            selectedResolution == other.selectedResolution
    }

    override fun hashCode(): Int {
        var result = collection.hashCode()
        result = 31 * result + recordId.hashCode()
        result = 31 * result + keyParts.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + divergentFields.hashCode()
        result = 31 * result + (collidingLocalRecordId?.hashCode() ?: 0)
        result = 31 * result + (relatedUnitType?.hashCode() ?: 0)
        result = 31 * result + (relatedUnitId?.hashCode() ?: 0)
        result = 31 * result + hasFinancialOrRateDivergence.hashCode()
        result = 31 * result + allowedResolutions.hashCode()
        result = 31 * result + (selectedResolution?.hashCode() ?: 0)
        return result
    }
}

data class PendingDependencyDetail(
    val collection: BackupCollectionType,
    val recordId: String,
    val dependsOnCollection: BackupCollectionType,
    val dependsOnRecordId: String,
    val reason: String,
)

data class MergeBlockReason(
    val collection: BackupCollectionType,
    val recordId: String,
    val relatedUnitType: MergeUnitType? = null,
    val relatedUnitId: String? = null,
    val reason: String,
)

class RelatedMergeUnit(
    val unitType: MergeUnitType,
    val unitId: String,
    val action: MergePlanAction,
    val isAtomicUnit: Boolean = true,
    memberRecordIdsByCollection: Map<BackupCollectionType, List<String>>,
    dependencyUnitOrRecordIds: List<String> = emptyList(),
    blockReasons: List<String> = emptyList(),
    allowedResolutions: List<MergeConflictResolution> = emptyList(),
) {
    val memberRecordIdsByCollection: Map<BackupCollectionType, List<String>> =
        Collections.unmodifiableMap(
            memberRecordIdsByCollection.entries.associateTo(LinkedHashMap()) { (k, v) ->
                k to Collections.unmodifiableList(v.toList())
            },
        )
    val dependencyUnitOrRecordIds: List<String> =
        Collections.unmodifiableList(dependencyUnitOrRecordIds.toList())
    val blockReasons: List<String> = Collections.unmodifiableList(blockReasons.toList())
    val allowedResolutions: List<MergeConflictResolution> =
        Collections.unmodifiableList(allowedResolutions.toList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelatedMergeUnit) return false
        return unitType == other.unitType &&
            unitId == other.unitId &&
            action == other.action &&
            isAtomicUnit == other.isAtomicUnit &&
            memberRecordIdsByCollection == other.memberRecordIdsByCollection &&
            dependencyUnitOrRecordIds == other.dependencyUnitOrRecordIds &&
            blockReasons == other.blockReasons &&
            allowedResolutions == other.allowedResolutions
    }

    override fun hashCode(): Int {
        var result = unitType.hashCode()
        result = 31 * result + unitId.hashCode()
        result = 31 * result + action.hashCode()
        result = 31 * result + isAtomicUnit.hashCode()
        result = 31 * result + memberRecordIdsByCollection.hashCode()
        result = 31 * result + dependencyUnitOrRecordIds.hashCode()
        result = 31 * result + blockReasons.hashCode()
        result = 31 * result + allowedResolutions.hashCode()
        return result
    }
}

class CollectionMergePlan(
    val collection: BackupCollectionType,
    records: List<PlannedRecordAction>,
) {
    val records: List<PlannedRecordAction> = Collections.unmodifiableList(records.toList())

    val totalRecords: Int
        get() = records.size

    val addCount: Int
        get() = records.count { it.action == MergePlanAction.ADD }

    val keepCount: Int
        get() = records.count { it.action == MergePlanAction.KEEP }

    val conflictCount: Int
        get() = records.count { it.action == MergePlanAction.CONFLICT }

    val pendingDependencyCount: Int
        get() = records.count { it.action == MergePlanAction.PENDING_DEPENDENCY }

    val blockedCount: Int
        get() = records.count { it.action == MergePlanAction.BLOCKED }

    val localOnlyPreservedCount: Int
        get() = records.count {
            it.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY && it.action == MergePlanAction.KEEP
        }

    fun actionForRecord(recordId: String): MergePlanAction? {
        return records.firstOrNull { it.recordId == recordId }?.action
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CollectionMergePlan) return false
        return collection == other.collection && records == other.records
    }

    override fun hashCode(): Int {
        var result = collection.hashCode()
        result = 31 * result + records.hashCode()
        return result
    }
}

class RestorationPreconditions(
    val expectedLocalStateFingerprint: String,
    expectedLocalCounts: Map<BackupCollectionType, Int>,
    unresolvedConflictIds: List<String>,
    val requiresSingleTransaction: Boolean = true,
    val requiresFinalValidationBeforeCommit: Boolean = true,
    val requiresRollbackOnAnyFailure: Boolean = true,
    val canExecuteAdditiveWithoutUserDecisions: Boolean,
) {
    val expectedLocalCounts: Map<BackupCollectionType, Int> =
        Collections.unmodifiableMap(LinkedHashMap(expectedLocalCounts))
    val unresolvedConflictIds: List<String> =
        Collections.unmodifiableList(unresolvedConflictIds.toList())

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RestorationPreconditions) return false
        return expectedLocalStateFingerprint == other.expectedLocalStateFingerprint &&
            expectedLocalCounts == other.expectedLocalCounts &&
            unresolvedConflictIds == other.unresolvedConflictIds &&
            requiresSingleTransaction == other.requiresSingleTransaction &&
            requiresFinalValidationBeforeCommit == other.requiresFinalValidationBeforeCommit &&
            requiresRollbackOnAnyFailure == other.requiresRollbackOnAnyFailure &&
            canExecuteAdditiveWithoutUserDecisions == other.canExecuteAdditiveWithoutUserDecisions
    }

    override fun hashCode(): Int {
        var result = expectedLocalStateFingerprint.hashCode()
        result = 31 * result + expectedLocalCounts.hashCode()
        result = 31 * result + unresolvedConflictIds.hashCode()
        result = 31 * result + requiresSingleTransaction.hashCode()
        result = 31 * result + requiresFinalValidationBeforeCommit.hashCode()
        result = 31 * result + requiresRollbackOnAnyFailure.hashCode()
        result = 31 * result + canExecuteAdditiveWithoutUserDecisions.hashCode()
        return result
    }
}

class JsonBackupMergePlanResult(
    val backupFormat: String,
    val backupSchemaVersion: Int,
    val applicationId: String,
    val roomSchemaVersion: Int,
    val exportedAtEpochMs: Long,
    collectionPlans: List<CollectionMergePlan>,
    relatedUnits: List<RelatedMergeUnit>,
    conflictItems: List<MergeConflictItem>,
    pendingDependencies: List<PendingDependencyDetail>,
    blockReasons: List<MergeBlockReason>,
    val preconditions: RestorationPreconditions,
) {
    val collectionPlans: List<CollectionMergePlan> = Collections.unmodifiableList(collectionPlans.toList())
    val plansByCollection: Map<BackupCollectionType, CollectionMergePlan> =
        Collections.unmodifiableMap(collectionPlans.associateByTo(LinkedHashMap()) { it.collection })
    val relatedUnits: List<RelatedMergeUnit> = Collections.unmodifiableList(relatedUnits.toList())
    val conflictItems: List<MergeConflictItem> = Collections.unmodifiableList(conflictItems.toList())
    val pendingDependencies: List<PendingDependencyDetail> =
        Collections.unmodifiableList(pendingDependencies.toList())
    val blockReasons: List<MergeBlockReason> = Collections.unmodifiableList(blockReasons.toList())

    val totalAnalyzedRecords: Int
        get() = collectionPlans.sumOf { it.totalRecords }

    val candidateAdditionsCount: Int
        get() = collectionPlans.sumOf { it.addCount }

    val keptRecordsCount: Int
        get() = collectionPlans.sumOf { it.keepCount }

    val conflictsCount: Int
        get() = collectionPlans.sumOf { it.conflictCount }

    val pendingDependenciesCount: Int
        get() = collectionPlans.sumOf { it.pendingDependencyCount }

    val blockedOperationsCount: Int
        get() = collectionPlans.sumOf { it.blockedCount }

    val localOnlyPreservedCount: Int
        get() = collectionPlans.sumOf { it.localOnlyPreservedCount }

    val hasAnyConflictsOrBlocks: Boolean
        get() = conflictsCount > 0 || pendingDependenciesCount > 0 || blockedOperationsCount > 0

    fun planFor(collection: BackupCollectionType): CollectionMergePlan {
        return plansByCollection.getValue(collection)
    }

    fun withExplicitDecisions(decisionsByConflictKey: Map<String, MergeConflictResolution>): JsonBackupMergePlanResult {
        val updatedConflicts = conflictItems.map { item ->
            val lookupKey = "${item.collection.key}:${item.recordId}"
            val decision = decisionsByConflictKey[lookupKey]
            if (decision != null) item.withResolution(decision) else item
        }
        val stillUnresolved = updatedConflicts
            .filter { it.selectedResolution == null }
            .map { "${it.collection.key}:${it.recordId}" }
        val updatedPreconditions = RestorationPreconditions(
            expectedLocalStateFingerprint = preconditions.expectedLocalStateFingerprint,
            expectedLocalCounts = preconditions.expectedLocalCounts,
            unresolvedConflictIds = stillUnresolved,
            requiresSingleTransaction = preconditions.requiresSingleTransaction,
            requiresFinalValidationBeforeCommit = preconditions.requiresFinalValidationBeforeCommit,
            requiresRollbackOnAnyFailure = preconditions.requiresRollbackOnAnyFailure,
            canExecuteAdditiveWithoutUserDecisions = stillUnresolved.isEmpty() &&
                pendingDependenciesCount == 0 &&
                blockedOperationsCount == 0,
        )
        return JsonBackupMergePlanResult(
            backupFormat = backupFormat,
            backupSchemaVersion = backupSchemaVersion,
            applicationId = applicationId,
            roomSchemaVersion = roomSchemaVersion,
            exportedAtEpochMs = exportedAtEpochMs,
            collectionPlans = collectionPlans,
            relatedUnits = relatedUnits,
            conflictItems = updatedConflicts,
            pendingDependencies = pendingDependencies,
            blockReasons = blockReasons,
            preconditions = updatedPreconditions,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JsonBackupMergePlanResult) return false
        return backupFormat == other.backupFormat &&
            backupSchemaVersion == other.backupSchemaVersion &&
            applicationId == other.applicationId &&
            roomSchemaVersion == other.roomSchemaVersion &&
            exportedAtEpochMs == other.exportedAtEpochMs &&
            collectionPlans == other.collectionPlans &&
            relatedUnits == other.relatedUnits &&
            conflictItems == other.conflictItems &&
            pendingDependencies == other.pendingDependencies &&
            blockReasons == other.blockReasons &&
            preconditions == other.preconditions
    }

    override fun hashCode(): Int {
        var result = backupFormat.hashCode()
        result = 31 * result + backupSchemaVersion
        result = 31 * result + applicationId.hashCode()
        result = 31 * result + roomSchemaVersion
        result = 31 * result + exportedAtEpochMs.hashCode()
        result = 31 * result + collectionPlans.hashCode()
        result = 31 * result + relatedUnits.hashCode()
        result = 31 * result + conflictItems.hashCode()
        result = 31 * result + pendingDependencies.hashCode()
        result = 31 * result + blockReasons.hashCode()
        result = 31 * result + preconditions.hashCode()
        return result
    }
}
