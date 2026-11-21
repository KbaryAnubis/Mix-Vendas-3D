package com.mixvendas3d.app.domain.export

import java.util.Collections

enum class DecisionValidationIssueType {
    UNKNOWN_CONFLICT_KEY,
    DISALLOWED_RESOLUTION,
    UNRESOLVED_CONFLICT,
    INCOMPATIBLE_UNIT_DECISION,
    INCOMPLETE_UNIT_DECISION,
    IDENTITY_NAME_COLLISION_BLOCKED,
    UNRESOLVED_DEPENDENCY,
    FINANCIAL_OR_RATE_VALIDATION_ERROR,
    LOCAL_STATE_CHANGED_SINCE_PLANNING,
    PLAN_BACKUP_MISMATCH,
}

enum class EligibleOperationDisposition {
    ADD_NEW,
    KEEP_EXISTING,
    APPLY_BACKUP,
    SKIP_BACKUP,
}

data class DecisionValidationIssue(
    val type: DecisionValidationIssueType,
    val collection: BackupCollectionType? = null,
    val recordId: String? = null,
    val relatedUnitType: MergeUnitType? = null,
    val relatedUnitId: String? = null,
    val message: String,
)

class EligibleStandaloneRecord(
    val collection: BackupCollectionType,
    val recordId: String,
    val disposition: EligibleOperationDisposition,
    keyParts: Map<String, String>,
) {
    val keyParts: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(keyParts))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EligibleStandaloneRecord) return false
        return collection == other.collection &&
            recordId == other.recordId &&
            disposition == other.disposition &&
            keyParts == other.keyParts
    }

    override fun hashCode(): Int {
        var result = collection.hashCode()
        result = 31 * result + recordId.hashCode()
        result = 31 * result + disposition.hashCode()
        result = 31 * result + keyParts.hashCode()
        return result
    }
}

class EligibleMergeUnit(
    val unitType: MergeUnitType,
    val unitId: String,
    val disposition: EligibleOperationDisposition,
    memberRecordIdsByCollection: Map<BackupCollectionType, List<String>>,
) {
    val memberRecordIdsByCollection: Map<BackupCollectionType, List<String>> =
        Collections.unmodifiableMap(
            memberRecordIdsByCollection.entries.associateTo(LinkedHashMap()) { (k, v) ->
                k to Collections.unmodifiableList(v.toList())
            },
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EligibleMergeUnit) return false
        return unitType == other.unitType &&
            unitId == other.unitId &&
            disposition == other.disposition &&
            memberRecordIdsByCollection == other.memberRecordIdsByCollection
    }

    override fun hashCode(): Int {
        var result = unitType.hashCode()
        result = 31 * result + unitId.hashCode()
        result = 31 * result + disposition.hashCode()
        result = 31 * result + memberRecordIdsByCollection.hashCode()
        return result
    }
}

class MergeDecisionValidationResult(
    val backupFormat: String,
    val backupSchemaVersion: Int,
    val applicationId: String,
    val roomSchemaVersion: Int,
    val exportedAtEpochMs: Long,
    val totalConflictsCount: Int,
    val resolvedConflictsCount: Int,
    val unresolvedConflictsCount: Int,
    val pendingDependenciesCount: Int,
    val incompatibleDecisionsCount: Int,
    val blockedOperationsCount: Int,
    val resolvedPlan: JsonBackupMergePlanResult,
    resolvedConflicts: List<MergeConflictItem>,
    unresolvedConflicts: List<MergeConflictItem>,
    pendingDependencies: List<PendingDependencyDetail>,
    incompatibleDecisions: List<DecisionValidationIssue>,
    blockedOperations: List<DecisionValidationIssue>,
    invalidDecisionInputs: List<DecisionValidationIssue>,
    eligibleStandaloneRecords: List<EligibleStandaloneRecord>,
    eligibleUnits: List<EligibleMergeUnit>,
    val expectedLocalStateFingerprint: String,
    val observedLocalStateFingerprint: String,
    val localStateMatchesPlan: Boolean,
    val preconditions: RestorationPreconditions,
) {
    val resolvedConflicts: List<MergeConflictItem> =
        Collections.unmodifiableList(resolvedConflicts.toList())
    val unresolvedConflicts: List<MergeConflictItem> =
        Collections.unmodifiableList(unresolvedConflicts.toList())
    val pendingDependencies: List<PendingDependencyDetail> =
        Collections.unmodifiableList(pendingDependencies.toList())
    val incompatibleDecisions: List<DecisionValidationIssue> =
        Collections.unmodifiableList(incompatibleDecisions.toList())
    val blockedOperations: List<DecisionValidationIssue> =
        Collections.unmodifiableList(blockedOperations.toList())
    val invalidDecisionInputs: List<DecisionValidationIssue> =
        Collections.unmodifiableList(invalidDecisionInputs.toList())
    val eligibleStandaloneRecords: List<EligibleStandaloneRecord> =
        Collections.unmodifiableList(eligibleStandaloneRecords.toList())
    val eligibleUnits: List<EligibleMergeUnit> =
        Collections.unmodifiableList(eligibleUnits.toList())

    val allIssues: List<DecisionValidationIssue> = Collections.unmodifiableList(
        invalidDecisionInputs + incompatibleDecisions + blockedOperations,
    )

    val isReadyForNextPhase: Boolean
        get() = unresolvedConflictsCount == 0 &&
            pendingDependenciesCount == 0 &&
            incompatibleDecisionsCount == 0 &&
            blockedOperationsCount == 0 &&
            invalidDecisionInputs.isEmpty() &&
            localStateMatchesPlan

    val hasAnyBlocksOrIncompatibilities: Boolean
        get() = incompatibleDecisionsCount > 0 ||
            blockedOperationsCount > 0 ||
            invalidDecisionInputs.isNotEmpty() ||
            !localStateMatchesPlan

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MergeDecisionValidationResult) return false
        return backupFormat == other.backupFormat &&
            backupSchemaVersion == other.backupSchemaVersion &&
            applicationId == other.applicationId &&
            roomSchemaVersion == other.roomSchemaVersion &&
            exportedAtEpochMs == other.exportedAtEpochMs &&
            totalConflictsCount == other.totalConflictsCount &&
            resolvedConflictsCount == other.resolvedConflictsCount &&
            unresolvedConflictsCount == other.unresolvedConflictsCount &&
            pendingDependenciesCount == other.pendingDependenciesCount &&
            incompatibleDecisionsCount == other.incompatibleDecisionsCount &&
            blockedOperationsCount == other.blockedOperationsCount &&
            resolvedPlan == other.resolvedPlan &&
            resolvedConflicts == other.resolvedConflicts &&
            unresolvedConflicts == other.unresolvedConflicts &&
            pendingDependencies == other.pendingDependencies &&
            incompatibleDecisions == other.incompatibleDecisions &&
            blockedOperations == other.blockedOperations &&
            invalidDecisionInputs == other.invalidDecisionInputs &&
            eligibleStandaloneRecords == other.eligibleStandaloneRecords &&
            eligibleUnits == other.eligibleUnits &&
            expectedLocalStateFingerprint == other.expectedLocalStateFingerprint &&
            observedLocalStateFingerprint == other.observedLocalStateFingerprint &&
            localStateMatchesPlan == other.localStateMatchesPlan &&
            preconditions == other.preconditions
    }

    override fun hashCode(): Int {
        var result = backupFormat.hashCode()
        result = 31 * result + backupSchemaVersion
        result = 31 * result + applicationId.hashCode()
        result = 31 * result + roomSchemaVersion
        result = 31 * result + exportedAtEpochMs.hashCode()
        result = 31 * result + totalConflictsCount
        result = 31 * result + resolvedConflictsCount
        result = 31 * result + unresolvedConflictsCount
        result = 31 * result + pendingDependenciesCount
        result = 31 * result + incompatibleDecisionsCount
        result = 31 * result + blockedOperationsCount
        result = 31 * result + resolvedPlan.hashCode()
        result = 31 * result + resolvedConflicts.hashCode()
        result = 31 * result + unresolvedConflicts.hashCode()
        result = 31 * result + pendingDependencies.hashCode()
        result = 31 * result + incompatibleDecisions.hashCode()
        result = 31 * result + blockedOperations.hashCode()
        result = 31 * result + invalidDecisionInputs.hashCode()
        result = 31 * result + eligibleStandaloneRecords.hashCode()
        result = 31 * result + eligibleUnits.hashCode()
        result = 31 * result + expectedLocalStateFingerprint.hashCode()
        result = 31 * result + observedLocalStateFingerprint.hashCode()
        result = 31 * result + localStateMatchesPlan.hashCode()
        result = 31 * result + preconditions.hashCode()
        return result
    }
}
