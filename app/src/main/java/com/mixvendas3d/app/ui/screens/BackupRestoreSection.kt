package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.export.MergeExecutionFailureReason
import com.mixvendas3d.app.ui.components.V19TranslucentCard
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip

@Composable
fun ConfirmRestoreAlertDialog(
    confirmation: BackupRestoreConfirmationUi,
    isExecuting: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isExecuting) {
                onCancel()
            }
        },
        containerColor = Color(0xFA21123D),
        titleContentColor = V19InkPrimary,
        textContentColor = V19InkPrimary,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 12.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xD93E280D),
                border = BorderStroke(1.2.dp, V19GoldAccent),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = V19GoldAccent,
                    modifier = Modifier.padding(10.dp),
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.backup_restore_dialog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.testTag("confirm_restore_dialog_title"),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_dialog_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = V19InkPrimary.copy(alpha = 0.92f),
                    modifier = Modifier.testTag("confirm_restore_dialog_message"),
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x881A0932),
                    border = BorderStroke(1.dp, V19OutlineSubtle.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        RestoreKeyValueRow(
                            label = "Registros a adicionar",
                            value = confirmation.addedRecordsCount.toString(),
                            valueColor = if (confirmation.addedRecordsCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
                            testTag = "confirm_restore_added_count",
                        )
                        HorizontalDivider(color = V19OutlineSubtle.copy(alpha = 0.4f))
                        RestoreKeyValueRow(
                            label = "Conjuntos a substituir",
                            value = confirmation.replacedUnitsCount.toString(),
                            valueColor = if (confirmation.replacedUnitsCount > 0) V19GoldAccent else V19InkPrimary,
                            testTag = "confirm_restore_replaced_count",
                        )
                        HorizontalDivider(color = V19OutlineSubtle.copy(alpha = 0.4f))
                        RestoreKeyValueRow(
                            label = "Registros mantidos",
                            value = confirmation.keptRecordsCount.toString(),
                            testTag = "confirm_restore_kept_count",
                        )
                        HorizontalDivider(color = V19OutlineSubtle.copy(alpha = 0.4f))
                        RestoreKeyValueRow(
                            label = "Vendas que serão afetadas",
                            value = confirmation.affectedSalesCount.toString(),
                            valueColor = V19PurpleSoftText,
                            testTag = "confirm_restore_affected_sales_count",
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isExecuting,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_restore_dialog_button"),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_dialog_cancel),
                    color = V19InkMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isExecuting,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = V19PurpleButtonStart,
                    contentColor = Color.White,
                    disabledContainerColor = V19SurfaceChip,
                    disabledContentColor = V19InkMuted,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("confirm_restore_dialog_button"),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_dialog_confirm),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}

@Composable
fun BackupRestoreSuccessCard(
    result: BackupRestoreResultUi,
) {
    V19TranslucentCard(
        modifier = Modifier.testTag("restore_result_success_banner"),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xD90E3B2B),
            border = BorderStroke(1.2.dp, Color(0xFF66E2B3)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFFBAF7DE),
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.backup_restore_success_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBAF7DE),
                    modifier = Modifier.testTag("restore_success_title"),
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        RestoreKeyValueRow(
            label = "Registros adicionados",
            value = result.addedRecordsCount.toString(),
            valueColor = if (result.addedRecordsCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
            testTag = "restore_result_added_count",
        )
        HorizontalDivider(color = V19OutlineSubtle.copy(alpha = 0.4f))
        RestoreKeyValueRow(
            label = "Conjuntos substituídos",
            value = result.replacedUnitsCount.toString(),
            valueColor = if (result.replacedUnitsCount > 0) V19GoldAccent else V19InkPrimary,
            testTag = "restore_result_replaced_count",
        )
        HorizontalDivider(color = V19OutlineSubtle.copy(alpha = 0.4f))
        RestoreKeyValueRow(
            label = "Registros preservados",
            value = result.preservedRecordsCount.toString(),
            testTag = "restore_result_preserved_count",
        )
        HorizontalDivider(color = V19OutlineSubtle.copy(alpha = 0.4f))
        RestoreKeyValueRow(
            label = "Registros ignorados",
            value = result.ignoredRecordsCount.toString(),
            testTag = "restore_result_ignored_count",
        )

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = "Totais atuais no celular: ${result.finalPeopleCount} pessoa(s) · " +
                "${result.finalProductsCount} produto(s) · " +
                "${result.finalMachinesCount} maquininha(s) · " +
                "${result.finalSalesCount} venda(s) · " +
                "${result.finalSaleItemsCount} item(ns) · " +
                "${result.finalPreferencesCount} preferência(s).",
            style = MaterialTheme.typography.labelSmall,
            color = V19InkMuted,
            modifier = Modifier.testTag("restore_result_final_summary"),
        )
    }
}

@Composable
fun BackupRestoreErrorCard(
    result: BackupRestoreResultUi?,
    phase: BackupRestorePhase,
) {
    val reason = result?.failureReason
    val rolledBack = result?.rolledBack == true

    val detailMessage = when {
        phase == BackupRestorePhase.LOCAL_STATE_CHANGED ||
            reason == MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING ->
            stringResource(R.string.backup_restore_error_local_changed)
        reason == MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION ->
            stringResource(R.string.backup_restore_error_constraint)
        reason == MergeExecutionFailureReason.UNRESOLVED_CONFLICTS ->
            stringResource(R.string.backup_restore_error_unresolved)
        rolledBack ->
            stringResource(R.string.backup_restore_error_generic_rollback)
        else ->
            stringResource(R.string.backup_restore_error_generic_no_rollback)
    }

    val showExtraRollbackNote = rolledBack && (
        reason == MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING ||
            reason == MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION ||
            reason == MergeExecutionFailureReason.UNRESOLVED_CONFLICTS
        )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("restore_result_error_banner"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xD94B1528),
        border = BorderStroke(1.2.dp, V19ErrorSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = V19ErrorSoft,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_failed_title),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = V19ErrorSoft,
                    modifier = Modifier.testTag("restore_error_title"),
                )
                Text(
                    text = detailMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = V19InkPrimary,
                    modifier = Modifier.testTag("restore_error_detail"),
                )
                if (showExtraRollbackNote) {
                    Text(
                        text = stringResource(R.string.backup_restore_rolled_back_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = V19GoldAccent,
                        modifier = Modifier.testTag("restore_error_rollback_note"),
                    )
                } else if (!rolledBack && reason != null && (
                        reason == MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING ||
                            reason == MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION ||
                            reason == MergeExecutionFailureReason.UNRESOLVED_CONFLICTS
                        )
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_no_rollback_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = V19InkMuted,
                        modifier = Modifier.testTag("restore_error_rollback_note"),
                    )
                }
            }
        }
    }
}

@Composable
private fun RestoreKeyValueRow(
    label: String,
    value: String,
    testTag: String,
    valueColor: Color = V19InkPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = V19InkMuted,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}
