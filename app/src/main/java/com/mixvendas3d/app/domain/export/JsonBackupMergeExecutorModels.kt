package com.mixvendas3d.app.domain.export

import java.util.Collections

enum class MergeExecutionFailureReason {
    USER_CONFIRMATION_MISSING,
    INVALID_BACKUP,
    PLAN_BACKUP_MISMATCH,
    VALIDATION_BACKUP_MISMATCH,
    PREVIEW_NOT_SAFE,
    EXECUTION_CONTRACT_MISMATCH,
    DECISION_VALIDATION_NOT_READY,
    DECISIONS_MISMATCH,
    UNRESOLVED_CONFLICTS,
    PENDING_DEPENDENCIES,
    BLOCKED_OPERATIONS,
    LOCAL_STATE_CHANGED_SINCE_PLANNING,
    FINAL_PRE_WRITE_VALIDATION_FAILED,
    DATABASE_CONSTRAINT_VIOLATION,
    POST_WRITE_VERIFICATION_MISMATCH,
    TRANSACTION_ABORTED_OR_FAILED,
}

class MergeExecutionResult(
    val operationId: String,
    val success: Boolean,
    val rolledBack: Boolean,
    val addedRecordsCount: Int,
    val replacedUnitsCount: Int,
    val preservedRecordsCount: Int,
    val ignoredRecordsCount: Int,
    finalTableCounts: Map<BackupCollectionType, Int>,
    val failureReason: MergeExecutionFailureReason? = null,
    val failureMessage: String? = null,
) {
    val finalTableCounts: Map<BackupCollectionType, Int> =
        Collections.unmodifiableMap(LinkedHashMap(finalTableCounts))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MergeExecutionResult) return false
        return operationId == other.operationId &&
            success == other.success &&
            rolledBack == other.rolledBack &&
            addedRecordsCount == other.addedRecordsCount &&
            replacedUnitsCount == other.replacedUnitsCount &&
            preservedRecordsCount == other.preservedRecordsCount &&
            ignoredRecordsCount == other.ignoredRecordsCount &&
            finalTableCounts == other.finalTableCounts &&
            failureReason == other.failureReason &&
            failureMessage == other.failureMessage
    }

    override fun hashCode(): Int {
        var result = operationId.hashCode()
        result = 31 * result + success.hashCode()
        result = 31 * result + rolledBack.hashCode()
        result = 31 * result + addedRecordsCount
        result = 31 * result + replacedUnitsCount
        result = 31 * result + preservedRecordsCount
        result = 31 * result + ignoredRecordsCount
        result = 31 * result + finalTableCounts.hashCode()
        result = 31 * result + (failureReason?.hashCode() ?: 0)
        result = 31 * result + (failureMessage?.hashCode() ?: 0)
        return result
    }
}

internal data class ExecutorTestHooks(
    val afterFirstWrite: suspend () -> Unit = {},
    val afterMultipleInserts: suspend () -> Unit = {},
    val duringProductReplacement: suspend (productId: String) -> Unit = {},
    val duringMachineReplacement: suspend (machineId: String) -> Unit = {},
    val duringSaleReplacement: suspend (saleId: String) -> Unit = {},
    val beforePostWriteVerification: suspend () -> Unit = {},
)
