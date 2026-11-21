package com.mixvendas3d.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.ui.components.ImportExportGlassCard
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassInnerBoxHighlightBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeBadgeBgEnd
import com.mixvendas3d.app.ui.theme.HomeBadgeBgStart
import com.mixvendas3d.app.ui.theme.HomeBadgeBorder
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
import com.mixvendas3d.app.ui.theme.HomeCardSubtleDivider
import com.mixvendas3d.app.ui.theme.HomeCtaBorderGlow
import com.mixvendas3d.app.ui.theme.HomeCtaGradientEnd
import com.mixvendas3d.app.ui.theme.HomeCtaGradientStart
import com.mixvendas3d.app.ui.theme.HomeCtaShadowGlow
import com.mixvendas3d.app.ui.theme.PlusJakartaSansFontFamily
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import com.mixvendas3d.app.ui.theme.V19GoldAccent
import com.mixvendas3d.app.ui.theme.V19InkMuted
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText

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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ImportExportGlassCard(
            modifier = Modifier.testTag("conflict_resolution_header_card"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConflictV2IconBadge(
                    icon = Icons.Filled.Gavel,
                    badgeSize = 46.dp,
                    iconSize = 23.dp,
                    cornerRadius = 14.dp,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(38.dp)
                        .background(HomeCardNeonBorder.copy(alpha = 0.34f)),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_conflicts_section_title),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        lineHeight = 23.sp,
                        color = Color.White,
                        modifier = Modifier
                            .semantics { heading() }
                            .testTag("conflict_resolution_title"),
                    )
                    Text(
                        text = stringResource(R.string.backup_conflicts_section_description),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.88f),
                        modifier = Modifier.testTag("conflict_resolution_description"),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.32f))
            Spacer(modifier = Modifier.height(14.dp))

            val blockedTotal = section.blockedOperationsCount + section.incompatibleDecisionsCount
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("conflict_resolution_counters"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConflictCounterRowV2(
                    icon = Icons.Filled.CheckCircle,
                    label = "Conflitos resolvidos",
                    value = section.resolvedUnitsCount.toString(),
                    accentColor = if (section.resolvedUnitsCount > 0) V19MintSuccess else V19PurpleSoftText,
                    containerColor = GlassInnerBoxBg,
                    borderColor = if (section.resolvedUnitsCount > 0) {
                        V19MintSuccess.copy(alpha = 0.45f)
                    } else {
                        GlassNeonBorder15
                    },
                    valueColor = if (section.resolvedUnitsCount > 0) Color(0xFFBAF7DE) else Color.White,
                    testTag = "conflicts_resolved_count",
                )
                ConflictCounterRowV2(
                    icon = Icons.Filled.WarningAmber,
                    label = "Conflitos pendentes",
                    value = section.pendingUnitsCount.toString(),
                    accentColor = if (section.pendingUnitsCount > 0) V19GoldAccent else V19PurpleSoftText,
                    containerColor = if (section.pendingUnitsCount > 0) Color(0x9938240E) else GlassInnerBoxBg,
                    borderColor = if (section.pendingUnitsCount > 0) {
                        V19GoldAccent.copy(alpha = 0.72f)
                    } else {
                        GlassNeonBorder15
                    },
                    valueColor = if (section.pendingUnitsCount > 0) V19GoldAccent else Color.White,
                    testTag = "conflicts_pending_count",
                )
                ConflictCounterRowV2(
                    icon = Icons.Filled.Link,
                    label = "Dependências pendentes",
                    value = section.pendingDependenciesCount.toString(),
                    accentColor = if (section.pendingDependenciesCount > 0) V19GoldAccent else V19PurpleSoftText,
                    containerColor = if (section.pendingDependenciesCount > 0) Color(0x9938240E) else GlassInnerBoxBg,
                    borderColor = if (section.pendingDependenciesCount > 0) {
                        V19GoldAccent.copy(alpha = 0.72f)
                    } else {
                        GlassNeonBorder15
                    },
                    valueColor = if (section.pendingDependenciesCount > 0) V19GoldAccent else Color.White,
                    testTag = "conflicts_pending_deps_count",
                )
                ConflictCounterRowV2(
                    icon = Icons.Filled.Block,
                    label = "Operações bloqueadas",
                    value = blockedTotal.toString(),
                    accentColor = if (blockedTotal > 0) V19ErrorSoft else V19PurpleSoftText,
                    containerColor = if (blockedTotal > 0) Color(0x994B1528) else GlassInnerBoxBg,
                    borderColor = if (blockedTotal > 0) {
                        V19ErrorSoft.copy(alpha = 0.78f)
                    } else {
                        GlassNeonBorder15
                    },
                    valueColor = if (blockedTotal > 0) V19ErrorSoft else Color.White,
                    testTag = "conflicts_blocked_count",
                )
            }

            if (section.units.isEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("conflicts_empty_banner"),
                    shape = RoundedCornerShape(16.dp),
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
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.backup_conflicts_none_to_resolve),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFBAF7DE),
                            modifier = Modifier.weight(1f),
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

        ReviewRestoreNeonButton(
            text = stringResource(R.string.backup_conflicts_review_button),
            enabled = !isBusy && section.isReadyForNextPhase,
            onClick = onPrepareReviewClick,
        )

        if (isReviewReadyConfirmed && section.isReadyForNextPhase) {
            ReviewReadyStatusBanner(section = section)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictUnitCard(
    unit: BackupConflictUnitUi,
    isBusy: Boolean,
    onSelectResolution: (MergeConflictResolution) -> Unit,
) {
    ImportExportGlassCard(
        modifier = Modifier.testTag("conflict_unit_card_${unit.unitKey}"),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xCC35195E),
                border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.65f)),
            ) {
                Text(
                    text = unit.typeLabel,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = V19PurpleSoftText,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                )
            }
            if (unit.isAtomicUnit) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = GlassInnerBoxBg,
                    border = BorderStroke(1.dp, GlassNeonBorder15),
                ) {
                    Text(
                        text = "Conjunto único",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.88f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (unit.isIdentityCollision) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xD93E280D),
                    border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.85f)),
                ) {
                    Text(
                        text = "Colisão de identidade",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = V19GoldAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = unit.title,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 17.5.sp,
            lineHeight = 22.sp,
            color = Color.White,
            modifier = Modifier.testTag("conflict_unit_title_${unit.unitKey}"),
        )

        if (unit.hasFinancialOrRateDivergence) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("conflict_unit_financial_warning_${unit.unitKey}"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xD93E280D),
                border = BorderStroke(1.2.dp, V19GoldAccent),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = V19GoldAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.backup_conflicts_financial_warning),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = V19GoldAccent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ComparisonDataBoxV2(
            header = "No celular",
            icon = Icons.Filled.Smartphone,
            lines = unit.localDataLines,
            containerColor = GlassInnerBoxBg,
            borderColor = GlassNeonBorder15,
            testTag = "conflict_unit_local_${unit.unitKey}",
        )

        Spacer(modifier = Modifier.height(10.dp))

        ComparisonDataBoxV2(
            header = "No backup",
            icon = Icons.Filled.Inventory2,
            lines = unit.backupDataLines,
            containerColor = GlassInnerBoxHighlightBg,
            borderColor = HomeCardNeonBorder.copy(alpha = 0.45f),
            testTag = "conflict_unit_backup_${unit.unitKey}",
        )

        if (unit.differentFieldsLabels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("conflict_unit_diff_${unit.unitKey}"),
                shape = RoundedCornerShape(12.dp),
                color = GlassInnerBoxBg,
                border = BorderStroke(1.dp, GlassNeonBorder15),
            ) {
                Text(
                    text = "Campos diferentes: ${unit.differentFieldsLabels.joinToString(", ")}",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.88f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        if (unit.isAtomicUnit) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = V19PurpleSoftText.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .size(15.dp),
                )
                Text(
                    text = stringResource(R.string.backup_conflicts_atomic_unit_note),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = V19PurpleSoftText.copy(alpha = 0.90f),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.30f))
        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                ResolutionChoiceButtonV2(
                    label = label,
                    isSelected = isSelected,
                    enabled = !isBusy,
                    testTag = "conflict_choice_${unit.unitKey}_${resolution.name}",
                    onClick = { onSelectResolution(resolution) },
                )
            }
        }

        if (unit.unitValidationMessages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag("conflict_unit_issue_${unit.unitKey}"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xD94B1528),
                border = BorderStroke(1.2.dp, V19ErrorSoft),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = V19ErrorSoft,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(17.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        unit.unitValidationMessages.forEach { msg ->
                            Text(
                                text = msg,
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                color = V19ErrorSoft,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictV2IconBadge(
    icon: ImageVector,
    badgeSize: Dp = 46.dp,
    iconSize: Dp = 23.dp,
    cornerRadius: Dp = 14.dp,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = Modifier
            .size(badgeSize)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(HomeBadgeBgStart.copy(alpha = 0.72f), HomeBadgeBgEnd.copy(alpha = 0.85f)),
                ),
            )
            .border(1.dp, HomeBadgeBorder.copy(alpha = 0.50f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun ConflictCounterRowV2(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    containerColor: Color,
    borderColor: Color,
    valueColor: Color,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.5.sp,
                color = HomeCardLabelInk.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.5.sp,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun ComparisonDataBoxV2(
    header: String,
    icon: ImageVector,
    lines: List<String>,
    containerColor: Color,
    borderColor: Color,
    testTag: String,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = V19PurpleSoftText,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = header,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = V19PurpleSoftText,
                )
            }
            HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color.White.copy(alpha = 0.94f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolutionChoiceButtonV2(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val bgBrush = when {
        !enabled -> Brush.horizontalGradient(
            listOf(GlassInnerBoxBg.copy(alpha = 0.06f), GlassInnerBoxBg.copy(alpha = 0.06f)),
        )
        isSelected -> Brush.horizontalGradient(
            listOf(HomeCtaGradientStart, HomeCtaGradientEnd),
        )
        else -> Brush.horizontalGradient(
            listOf(GlassInnerBoxBg, GlassInnerBoxBg),
        )
    }
    val borderColor = when {
        !enabled -> GlassNeonBorder15.copy(alpha = 0.12f)
        isSelected -> HomeCtaBorderGlow.copy(alpha = 0.95f)
        else -> GlassNeonBorder15
    }
    val borderWidth = if (isSelected && enabled) 1.6.dp else 1.1.dp
    val textColor = when {
        !enabled -> V19InkMuted
        isSelected -> Color.White
        else -> HomeCardLabelInk
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .shadow(
                elevation = if (isSelected && enabled) 10.dp else 0.dp,
                shape = shape,
                ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                spotColor = HomeCtaShadowGlow.copy(alpha = 0.78f),
            )
            .clip(shape)
            .background(bgBrush)
            .border(borderWidth, borderColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { selected = isSelected }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                fontSize = 13.5.sp,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selecionado",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = HomeCardNeonBorder.copy(alpha = if (enabled) 0.55f else 0.28f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewRestoreNeonButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgBrush = if (enabled) {
        Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
    } else {
        Brush.horizontalGradient(
            listOf(
                HomeCtaGradientStart.copy(alpha = 0.36f),
                HomeCtaGradientEnd.copy(alpha = 0.36f),
            ),
        )
    }
    val borderColor = if (enabled) {
        HomeCtaBorderGlow.copy(alpha = 0.90f)
    } else {
        HomeCtaBorderGlow.copy(alpha = 0.28f)
    }
    val contentTint = if (enabled) Color.White else Color.White.copy(alpha = 0.52f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .shadow(
                elevation = if (enabled) 14.dp else 0.dp,
                shape = shape,
                ambientColor = HomeCtaShadowGlow.copy(alpha = 0.72f),
                spotColor = HomeCtaShadowGlow.copy(alpha = 0.85f),
            )
            .clip(shape)
            .background(bgBrush)
            .border(1.4.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("review_restore_button"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(contentTint.copy(alpha = 0.45f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = contentTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    text = "Atenção antes de continuar",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = V19ErrorSoft,
                )
                messages.forEach { msg ->
                    Text(
                        text = "• $msg",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
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
        shape = RoundedCornerShape(16.dp),
        color = Color(0xD90E3B2B),
        border = BorderStroke(1.2.dp, Color(0xFF66E2B3)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFFBAF7DE),
                )
                Text(
                    text = stringResource(R.string.backup_conflicts_review_ready_description),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFBAF7DE).copy(alpha = 0.92f),
                )
                Text(
                    text = "Resumo validado: ${section.eligibleAddsCount} novo(s), " +
                        "${section.eligibleApplyBackupCount} substituído(s) pelo backup, " +
                        "${section.eligibleSkipBackupCount} mantido(s)/ignorado(s).",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = Color(0xFFBAF7DE).copy(alpha = 0.88f),
                )
            }
        }
    }
}
