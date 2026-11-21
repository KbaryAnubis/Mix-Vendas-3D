package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19OutlineInput
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceCard
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.ui.theme.V19SurfaceInput

enum class CsvExportMode(
    val title: String,
    val description: String,
    val testTagSuffix: String,
) {
    SUMMARY_ONLY(
        title = "Resumo das vendas",
        description = "Uma linha por venda com valores originais, descontos, maquininha, tarifas e líquido.",
        testTagSuffix = "summary",
    ),
    DETAILED_ONLY(
        title = "Detalhamento completo",
        description = "Uma linha por participante de cada item vendido, com regras de tarifa e descontos.",
        testTagSuffix = "detailed",
    ),
    BOTH(
        title = "Exportar ambos",
        description = "Salva o resumo das vendas e em seguida o detalhamento completo.",
        testTagSuffix = "both",
    ),
}

enum class CsvDocumentKind {
    SUMMARY,
    DETAILED,
}

data class PendingCsvDocumentRequest(
    val requestToken: String,
    val kind: CsvDocumentKind,
    val suggestedFileName: String,
)

data class ClosingCsvExportUiState(
    val dialogVisible: Boolean = false,
    val selectedMode: CsvExportMode = CsvExportMode.SUMMARY_ONLY,
    val isPreparing: Boolean = false,
    val isWriting: Boolean = false,
    val pendingDocumentRequest: PendingCsvDocumentRequest? = null,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
    val feedbackIsPartial: Boolean = false,
) {
    val isBusy: Boolean
        get() = isPreparing || isWriting || pendingDocumentRequest != null
}

@Composable
fun ClosingCsvExportDialog(
    selectedMode: CsvExportMode,
    isPreparing: Boolean,
    onModeSelected: (CsvExportMode) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isPreparing) {
                onDismiss()
            }
        },
        containerColor = V19SurfaceCard,
        titleContentColor = Color.White,
        textContentColor = V19InkPrimary,
        modifier = Modifier.testTag("csv_export_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = V19PurpleSoftText,
                modifier = Modifier.size(28.dp),
            )
        },
        title = {
            Text(
                text = stringResource(R.string.closing_export_csv_dialog_title),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.testTag("csv_export_dialog_title"),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.closing_export_csv_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = V19InkMuted,
                )

                CsvExportMode.entries.forEach { mode ->
                    val selected = mode == selectedMode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics(mergeDescendants = true) {}
                            .clickable(
                                enabled = !isPreparing,
                                role = Role.RadioButton,
                                onClick = { onModeSelected(mode) },
                            )
                            .testTag("csv_export_option_${mode.testTagSuffix}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            Color(0xFF28174E).copy(alpha = 0.92f)
                        } else {
                            V19SurfaceInput.copy(alpha = 0.85f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) V19PurpleGlow else V19OutlineSubtle,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                enabled = !isPreparing,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = V19PurpleGlow,
                                    unselectedColor = V19InkMuted,
                                ),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color.White else V19InkPrimary,
                                )
                                Text(
                                    text = mode.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = V19InkMuted,
                                )
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isPreparing,
                border = BorderStroke(1.dp, V19OutlineInput),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("csv_export_cancel_button"),
            ) {
                Text(
                    text = stringResource(R.string.closing_export_csv_cancel),
                    color = V19PurpleSoftText,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isPreparing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = V19PurpleButtonStart,
                    contentColor = Color.White,
                    disabledContainerColor = V19SurfaceChip,
                    disabledContentColor = V19InkMuted,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("csv_export_confirm_button"),
            ) {
                Text(
                    text = stringResource(R.string.closing_export_csv_continue),
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    )
}
