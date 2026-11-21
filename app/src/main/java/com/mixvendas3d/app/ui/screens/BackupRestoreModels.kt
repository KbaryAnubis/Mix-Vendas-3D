package com.mixvendas3d.app.ui.screens

import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.MergeExecutionFailureReason
import com.mixvendas3d.app.domain.export.MergeExecutionResult

enum class BackupRestorePhase {
    IDLE,
    CONFIRMING,
    EXECUTING,
    SUCCESS,
    FAILED,
    LOCAL_STATE_CHANGED,
}

data class BackupRestoreConfirmationUi(
    val addedRecordsCount: Int,
    val replacedUnitsCount: Int,
    val keptRecordsCount: Int,
    val affectedSalesCount: Int,
)

data class BackupRestoreResultUi(
    val success: Boolean,
    val rolledBack: Boolean,
    val addedRecordsCount: Int,
    val replacedUnitsCount: Int,
    val preservedRecordsCount: Int,
    val ignoredRecordsCount: Int,
    val finalPeopleCount: Int,
    val finalProductsCount: Int,
    val finalMachinesCount: Int,
    val finalSalesCount: Int,
    val finalSaleItemsCount: Int,
    val finalPreferencesCount: Int,
    val failureReason: MergeExecutionFailureReason? = null,
)

internal object BackupRestoreMapper {

    fun buildConfirmationUi(previewUi: BackupPreviewUi): BackupRestoreConfirmationUi {
        return BackupRestoreConfirmationUi(
            addedRecordsCount = previewUi.newRecordsCount,
            replacedUnitsCount = previewUi.unitsToReplaceCount,
            keptRecordsCount = previewUi.keptRecordsCount,
            affectedSalesCount = previewUi.financialSummary.addedSalesCount +
                previewUi.financialSummary.replacedSalesCount,
        )
    }

    fun buildResultUi(result: MergeExecutionResult): BackupRestoreResultUi {
        return BackupRestoreResultUi(
            success = result.success,
            rolledBack = result.rolledBack,
            addedRecordsCount = result.addedRecordsCount,
            replacedUnitsCount = result.replacedUnitsCount,
            preservedRecordsCount = result.preservedRecordsCount,
            ignoredRecordsCount = result.ignoredRecordsCount,
            finalPeopleCount = result.finalTableCounts[BackupCollectionType.PEOPLE] ?: 0,
            finalProductsCount = result.finalTableCounts[BackupCollectionType.PRODUCTS] ?: 0,
            finalMachinesCount = result.finalTableCounts[BackupCollectionType.MACHINES] ?: 0,
            finalSalesCount = result.finalTableCounts[BackupCollectionType.SALES] ?: 0,
            finalSaleItemsCount = result.finalTableCounts[BackupCollectionType.SALE_ITEMS] ?: 0,
            finalPreferencesCount = result.finalTableCounts[BackupCollectionType.PREFERENCES] ?: 0,
            failureReason = result.failureReason,
        )
    }
}
