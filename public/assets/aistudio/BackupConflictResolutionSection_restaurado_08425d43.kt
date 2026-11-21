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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.ui.components.V19TranslucentCard
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19OutlineSubtle
import com.mixvendas3d.app.ui.theme.V19PurpleButtonStart
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip

@Composable
fun ResolverConflitosSection(
    section: BackupConflictSectionUi,
    isBusy: Boolean,
    isReviewReadyConfirmed: Boolean,
    onSelectConflictResolution: (String, MergeConflictResolution) -> Unit,
    onPrepareReviewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("conflict_resolution_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        V19TranslucentCard(
            modifier = Modifier.testTag("conflict_resolution_header_card"),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = V19SurfaceChip,
                    border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.40f)),
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Gavel,
                        contentDescription = null,
                        tint = V19PurpleSoftText,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_conflicts_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = V19InkPrimary,
                        modifier = Modifier.testTag("conflict_resolution_title"),
                    )
                    Text(
                        text = stringResource(R.string.backup_conflicts_section_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = V19InkMuted,
                        modifier = Modifier.testTag("conflict_resolution_description"),
                    )
                }
            }

            Spacer(modifier = Modifier.size(12.dp))
            HorizontalDivider(color = V19OutlineSubtle)
            Spacer(modifier = Modifier.size(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("conflict_resolution_counters"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CounterRow(
                    label = "Conflitos resolvidos",
                    value = section.resolvedUnitsCount.toString(),
                    testTag = "conflicts_resolved_count",
                    valueColor = if (section.resolvedUnitsCount > 0) Color(0xFFBAF7DE) else V19InkPrimary,
                )
                CounterRow(
                    label = "Conflitos pendentes",
                    value = section.pendingUnitsCount.toString(),
                    testTag = "conflicts_pending_count",
                    valueColor = if (section.pendingUnitsCount > 0) V19GoldAccent else V19InkPrimary,
                )
                CounterRow(
                    label = "Dependências pendentes",
                    value = section.pendingDependenciesCount.toString(),
                    testTag = "conflicts_pending_deps_count",
                    valueColor = if (section.pendingDependenciesCount > 0) V19GoldAccent else V19InkPrimary,
                )
                val blockedTotal = section.blockedOperationsCount + section.incompatibleDecisionsCount
                CounterRow(
                    label = "Operações bloqueadas",
                    value = blockedTotal.toString(),
                    testTag = "conflicts_blocked_count",
                    valueColor = if (blockedTotal > 0) V19ErrorSoft else V19InkPrimary,
                )
            }

            if (section.units.isEmpty()) {
                Spacer(modifier = Modifier.size(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("conflicts_empty_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF103E2E).copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color(0xFF66E2B3)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFFBAF7DE),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.backup_conflicts_none_to_resolve),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFBAF7DE),
                        )
                    }
                }
            }
        }

        section.units.forEach { unit ->
            ConflictUnitCard(
                unit = unit,
                isBusy = isBusy,
                onSelectResolution = { resolution ->
                    onSelectConflictResolution(unit.unitKey, resolution)
                },
            )
        }

        if (section.validationMessages.isNotEmpty()) {
            ValidationIssuesBanner(messages = section.validationMessages)
        }

        Button(
            onClick = onPrepareReviewClick,
            enabled = !isBusy && section.isReadyForNextPhase,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = V19PurpleButtonStart,
                contentColor = Color.White,
                disabledContainerColor = V19SurfaceChip,
                disabledContentColor = V19InkMuted,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("review_restore_button"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.backup_conflicts_review_button),
                fontWeight = FontWeight.Bold,
            )
        }

        if (isReviewReadyConfirmed && section.isReadyForNextPhase) {
            ReviewReadyStatusBanner(section = section)
        }
    }
}

@Composable
private fun ConflictUnitCard(
    unit: BackupConflictUnitUi,
    isBusy: Boolean,
    onSelectResolution: (MergeConflictResolution) -> Unit,
) {
    V19TranslucentCard(
        modifier = Modifier.testTag("conflict_unit_card_${unit.unitKey}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = V19SurfaceChip,
                border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.45f)),
            ) {
                Text(
                    text = unit.typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = V19PurpleSoftText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (unit.isAtomicUnit) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF28174E).copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, V19OutlineSubtle),
                ) {
                    Text(
                        text = "Conjunto único",
                        style = MaterialTheme.typography.labelSmall,
                        color = V19InkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = unit.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = V19InkPrimary,
            modifier = Modifier.testTag("conflict_unit_title_${unit.unitKey}"),
        )

        if (unit.hasFinancialOrRateDivergence) {
            Spacer(modifier = Modifier.size(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("conflict_unit_financial_warning_${unit.unitKey}"),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF3E2D12).copy(alpha = 0.90f),
                border = BorderStroke(1.dp, V19GoldAccent),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = V19GoldAccent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.backup_conflicts_financial_warning),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = V19GoldAccent,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(10.dp))

        ComparisonDataBox(
            header = "No celular",
            lines = unit.localDataLines,
            borderColor = V19OutlineSubtle,
            testTag = "conflict_unit_local_${unit.unitKey}",
        )

        Spacer(modifier = Modifier.size(8.dp))

        ComparisonDataBox(
            header = "No backup",
            lines = unit.backupDataLines,
            borderColor = V19PurpleGlow.copy(alpha = 0.45f),
            testTag = "conflict_unit_backup_${unit.unitKey}",
        )

        if (unit.differentFieldsLabels.isNotEmpty()) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Campos diferentes: ${unit.differentFieldsLabels.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = V19InkMuted,
                modifier = Modifier.testTag("conflict_unit_diff_${unit.unitKey}"),
            )
        }

        if (unit.isAtomicUnit) {
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.backup_conflicts_atomic_unit_note),
                style = MaterialTheme.typography.labelSmall,
                color = V19PurpleSoftText,
            )
        }

        Spacer(modifier = Modifier.size(12.dp))
        HorizontalDivider(color = V19OutlineSubtle)
        Spacer(modifier = Modifier.size(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            unit.allowedResolutions.forEach { resolution ->
                val isSelected = unit.selectedResolution == resolution
                val label = when (resolution) {
                    MergeConflictResolution.KEEP_LOCAL ->
                        stringResource(R.string.backup_conflicts_option_keep_local)
                    MergeConflictResolution.USE_BACKUP ->
                        stringResource(R.string.backup_conflicts_option_use_backup)
                    MergeConflictResolution.IGNORE_IMPORT ->
                        stringResource(R.string.backup_conflicts_option_ignore_import)
                }
                ResolutionChoiceButton(
                    label = label,
                    isSelected = isSelected,
                    enabled = !isBusy,
                    testTag = "conflict_choice_${unit.unitKey}_${resolution.name}",
                    onClick = { onSelectResolution(resolution) },
                )
            }
        }

        if (unit.unitValidationMessages.isNotEmpty()) {
            Spacer(modifier = Modifier.size(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("conflict_unit_issue_${unit.unitKey}"),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF4B1528).copy(alpha = 0.90f),
                border = BorderStroke(1.dp, V19ErrorSoft),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    unit.unitValidationMessages.forEach { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = V19ErrorSoft,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonDataBox(
    header: String,
    lines: List<String>,
    borderColor: Color,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = V19SurfaceChip.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = header,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = V19PurpleSoftText,
            )
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = V19InkPrimary,
                )
            }
        }
    }
}

@Composable
private fun ResolutionChoiceButton(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) V19PurpleGlow else V19OutlineSubtle,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) V19PurpleButtonStart.copy(alpha = 0.85f) else V19SurfaceChip.copy(alpha = 0.65f),
            contentColor = if (isSelected) Color.White else V19InkPrimary,
            disabledContainerColor = V19SurfaceChip,
            disabledContentColor = V19InkMuted,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { selected = isSelected }
            .testTag(testTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selecionado",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ValidationIssuesBanner(
    messages: List<String>,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("conflict_validation_issues_banner"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF4B1528).copy(alpha = 0.90f),
        border = BorderStroke(1.dp, V19ErrorSoft),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = V19ErrorSoft,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Atenção antes de continuar",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = V19ErrorSoft,
                )
                messages.forEach { msg ->
                    Text(
                        text = "• $msg",
                        style = MaterialTheme.typography.labelSmall,
                        color = V19ErrorSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewReadyStatusBanner(
    section: BackupConflictSectionUi,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("review_ready_banner"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF103E2E).copy(alpha = 0.92f),
        border = BorderStroke(1.dp, Color(0xFF66E2B3)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFFBAF7DE),
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_conflicts_review_ready_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBAF7DE),
                )
                Text(
                    text = stringResource(R.string.backup_conflicts_review_ready_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFBAF7DE).copy(alpha = 0.9f),
                )
                Text(
                    text = "Resumo validado: ${section.eligibleAddsCount} novo(s), " +
                        "${section.eligibleApplyBackupCount} substituído(s) pelo backup, " +
                        "${section.eligibleSkipBackupCount} mantido(s)/ignorado(s).",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBAF7DE).copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun CounterRow(
    label: String,
    value: String,
    testTag: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = V19InkMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor,
        )
    }
}
