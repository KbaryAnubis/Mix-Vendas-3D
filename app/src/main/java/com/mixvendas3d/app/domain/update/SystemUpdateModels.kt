package com.mixvendas3d.app.domain.update

import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.models.SalePersonTotalSnapshot

enum class SystemUpdateConflictCode {
    PRODUCT_NOT_FOUND,
    MACHINE_NOT_FOUND,
    CREDIT_DISABLED,
    INSTALLMENTS_DISABLED,
    MISSING_REQUIRED_BRAND,
    PERSON_NOT_FOUND,
    FEE_OWNER_NOT_FOUND_OR_NOT_PARTICIPANT,
    PRICE_AND_RULES_MISMATCH,
    NEGATIVE_PARTICIPANT_NET,
    DISCOUNT_EXCEEDS_ITEM,
    CALCULATION_ERROR,
    INVARIANT_VIOLATION,
}

data class SaleUpdateConflict(
    val saleId: String,
    val soldAtEpochMs: Long,
    val productNames: List<String>,
    val paymentMethodLabel: String,
    val originalChargedAmount: MoneyCents,
    val reasons: List<String>,
    val codes: Set<SystemUpdateConflictCode>,
)

data class SaleUpdateCandidate(
    val saleId: String,
    val soldAtEpochMs: Long,
    val productNames: List<String>,
    val paymentMethodLabel: String,
    val originalChargedAmount: MoneyCents,
    val historicalFeeRateBps: Int,
    val proposedFeeRateBps: Int,
    val historicalFeeAmount: MoneyCents,
    val proposedFeeAmount: MoneyCents,
    val historicalNetAmount: MoneyCents,
    val proposedNetAmount: MoneyCents,
    val historicalPersonTotals: List<SalePersonTotalSnapshot>,
    val proposedPersonTotals: List<SalePersonTotalSnapshot>,
    val changeReasons: List<String>,
    val beforeSale: PersistedSale,
    val proposedCalculation: SaleCalculationResult,
)

data class SystemUpdateAnalysisResult(
    val analyzedAtEpochMs: Long,
    val localStateFingerprint: String,
    val totalSalesEvaluated: Int,
    val salesToUpdateCount: Int,
    val alreadyCorrectCount: Int,
    val requiresReviewCount: Int,
    val updatableItems: List<SaleUpdateCandidate>,
    val conflictItems: List<SaleUpdateConflict>,
    val unchangedSaleIds: List<String>,
) {
    val hasChangesToApply: Boolean
        get() = salesToUpdateCount > 0

    val hasConflicts: Boolean
        get() = requiresReviewCount > 0
}

data class SystemUpdateBatchEntry(
    val saleId: String,
    val beforeSale: PersistedSale,
    val afterUpdatedAtEpochMs: Long,
    val afterCalculationHash: String,
)

data class SystemUpdateBatchRecord(
    val batchId: String,
    val appliedAtEpochMs: Long,
    val appliedAsPartial: Boolean,
    val untouchedConflictSaleIds: List<String>,
    val entries: List<SystemUpdateBatchEntry>,
)

data class SystemUpdateBatchSummary(
    val batchId: String,
    val appliedAtEpochMs: Long,
    val updatedSalesCount: Int,
    val skippedConflictCount: Int,
    val appliedAsPartial: Boolean,
    val canUndoSafely: Boolean,
    val undoBlockReasons: List<String>,
)

enum class SystemUpdateFailureReason {
    NONE,
    NOTHING_TO_UPDATE,
    LOCAL_STATE_CHANGED,
    CONFLICTS_NOT_CONFIRMED_FOR_PARTIAL,
    VALIDATION_FAILED,
    EXECUTION_FAILED_ROLLED_BACK,
    NO_UNDOABLE_BATCH,
    UNDO_CONFLICT_POST_UPDATE_MODIFIED,
    UNDO_FAILED_ROLLED_BACK,
}

data class SystemUpdateExecutionResult(
    val success: Boolean,
    val failureReason: SystemUpdateFailureReason = SystemUpdateFailureReason.NONE,
    val errorMessage: String? = null,
    val rolledBack: Boolean = false,
    val updatedSalesCount: Int = 0,
    val preservedConflictCount: Int = 0,
    val batchSummary: SystemUpdateBatchSummary? = null,
)

data class SystemUpdateUndoResult(
    val success: Boolean,
    val failureReason: SystemUpdateFailureReason = SystemUpdateFailureReason.NONE,
    val errorMessage: String? = null,
    val rolledBack: Boolean = false,
    val revertedSalesCount: Int = 0,
    val conflictMessages: List<String> = emptyList(),
)
