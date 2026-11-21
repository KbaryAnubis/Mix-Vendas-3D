package com.mixvendas3d.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mixvendas3d.app.R
import com.mixvendas3d.app.ui.screens.BackupAnalysisErrorType
import com.mixvendas3d.app.ui.screens.BackupAnalysisPhase
import com.mixvendas3d.app.ui.screens.BackupAnalysisSummaryUi
import com.mixvendas3d.app.ui.screens.BackupExportPhase
import com.mixvendas3d.app.ui.screens.ImportExportUiState
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeBadgeBgEnd
import com.mixvendas3d.app.ui.theme.HomeBadgeBgStart
import com.mixvendas3d.app.ui.theme.HomeBadgeBorder
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
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
import com.mixvendas3d.app.ui.theme.V19InkPrimary
import com.mixvendas3d.app.ui.theme.V19MintSuccess
import com.mixvendas3d.app.ui.theme.V19PurpleGlow
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ImportExportGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.25.dp, GlassNeonBorder15),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(HomeCardGlassTop, HomeCardGlassBottom),
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp),
            content = content,
        )
    }
}

@Composable
fun ImportExportMainCard(
    uiState: ImportExportUiState,
    onExportBackupClick: () -> Unit,
    onSelectBackupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ImportExportGlassCard(
        modifier = modifier.testTag("backup_restore_card"),
    ) {
        // Cabeçalho principal do cartão
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Mv3dIcon(
                iconRes = R.drawable.mv3d_importar_exportar,
                contentDescription = null,
                size = 52.dp,
                scalePercent = 180,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.backup_restore_title),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 19.sp,
                    lineHeight = 24.sp,
                    color = Color.White,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag("backup_restore_title"),
                )
                Text(
                    text = stringResource(R.string.backup_restore_subtitle),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.86f),
                    modifier = Modifier.testTag("backup_restore_subtitle"),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.32f))
        Spacer(modifier = Modifier.height(16.dp))

        // Bloco 1 — Exportar backup
        ImportExportSectionHeader(
            icon = Icons.Filled.CloudUpload,
            title = stringResource(R.string.backup_export_section_title),
            description = stringResource(R.string.backup_export_json_description),
            descriptionTag = "backup_export_description",
        )

        if (uiState.isPreparing || uiState.isWriting) {
            Spacer(modifier = Modifier.height(12.dp))
            val progressLabel = if (uiState.isPreparing) {
                stringResource(R.string.backup_export_preparing)
            } else {
                stringResource(R.string.backup_export_saving)
            }
            ImportExportProgressBanner(
                message = progressLabel,
                testTag = "backup_export_progress_banner",
            )
        }

        when (uiState.exportPhase) {
            BackupExportPhase.SUCCESS,
            BackupExportPhase.CANCELLED,
            BackupExportPhase.ERROR -> {
                Spacer(modifier = Modifier.height(12.dp))
                ImportExportExportFeedbackBanner(uiState = uiState)
            }
            BackupExportPhase.IDLE,
            BackupExportPhase.PREPARING,
            BackupExportPhase.AWAITING_DESTINATION,
            BackupExportPhase.WRITING -> Unit
        }

        Spacer(modifier = Modifier.height(14.dp))

        ImportExportPrimaryNeonButton(
            text = stringResource(R.string.backup_export_json_button),
            icon = Icons.Filled.SaveAlt,
            enabled = !uiState.isBusy,
            onClick = onExportBackupClick,
            testTag = "export_json_backup_button",
        )

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.32f))
        Spacer(modifier = Modifier.height(18.dp))

        // Bloco 2 — Importar / Restaurar backup
        ImportExportSectionHeader(
            icon = Icons.Filled.CloudDownload,
            title = stringResource(R.string.backup_import_section_title),
            description = stringResource(R.string.backup_select_json_description),
            descriptionTag = "backup_select_description",
        )

        if (uiState.isAnalysisInProgress) {
            Spacer(modifier = Modifier.height(12.dp))
            val analysisProgressLabel = when (uiState.analysisPhase) {
                BackupAnalysisPhase.READING -> stringResource(R.string.backup_analysis_reading)
                BackupAnalysisPhase.VALIDATING -> stringResource(R.string.backup_analysis_validating)
                BackupAnalysisPhase.COMPARING -> stringResource(R.string.backup_analysis_comparing)
                else -> ""
            }
            ImportExportProgressBanner(
                message = analysisProgressLabel,
                testTag = "backup_analysis_progress_banner",
            )
        }

        if (uiState.analysisPhase == BackupAnalysisPhase.CANCELLED ||
            uiState.analysisPhase == BackupAnalysisPhase.ERROR
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            ImportExportAnalysisFeedbackBanner(uiState = uiState)
        }

        Spacer(modifier = Modifier.height(14.dp))

        ImportExportSecondaryGlassButton(
            text = stringResource(R.string.backup_select_json_button),
            icon = Icons.Filled.FileOpen,
            enabled = !uiState.isBusy,
            onClick = onSelectBackupClick,
            testTag = "select_json_backup_button",
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Fluxo explicativo em 3 passos (somente informativo)
        ImportExportThreeStepsGuide()

        Spacer(modifier = Modifier.height(16.dp))

        // Nota discreta de segurança e operação offline
        ImportExportSecurityNoteBox()
    }
}

@Composable
private fun ImportExportIconBadge(
    icon: ImageVector,
    badgeSize: Dp = 50.dp,
    iconSize: Dp = 25.dp,
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
private fun ImportExportSectionHeader(
    icon: ImageVector,
    title: String,
    description: String,
    descriptionTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImportExportIconBadge(
            icon = icon,
            badgeSize = 52.dp,
            iconSize = 26.dp,
            cornerRadius = 15.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(42.dp)
                .background(HomeCardNeonBorder.copy(alpha = 0.34f)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                lineHeight = 22.sp,
                color = Color.White,
            )
            Text(
                text = description,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = HomeCardLabelInk.copy(alpha = 0.88f),
                modifier = Modifier.testTag(descriptionTag),
            )
        }
    }
}

@Composable
private fun ImportExportPrimaryNeonButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgBrush = if (enabled) {
        Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
    } else {
        Brush.horizontalGradient(
            listOf(
                HomeCtaGradientStart.copy(alpha = 0.42f),
                HomeCtaGradientEnd.copy(alpha = 0.42f),
            ),
        )
    }
    val borderColor = if (enabled) {
        HomeCtaBorderGlow.copy(alpha = 0.90f)
    } else {
        HomeCtaBorderGlow.copy(alpha = 0.32f)
    }
    val contentTint = if (enabled) Color.White else Color.White.copy(alpha = 0.56f)

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
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
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
private fun ImportExportSecondaryGlassButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    val shape = RoundedCornerShape(16.dp)
    val bgBrush = if (enabled) {
        Brush.horizontalGradient(listOf(GlassInnerBoxBg, GlassInnerBoxBg))
    } else {
        Brush.horizontalGradient(
            listOf(GlassInnerBoxBg.copy(alpha = 0.06f), GlassInnerBoxBg.copy(alpha = 0.06f)),
        )
    }
    val borderColor = if (enabled) {
        GlassNeonBorder15
    } else {
        GlassNeonBorder15.copy(alpha = 0.12f)
    }
    val contentTint = if (enabled) Color.White else Color.White.copy(alpha = 0.54f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(bgBrush)
            .border(1.25.dp, borderColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(22.dp)
                    .background(contentTint.copy(alpha = 0.38f)),
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
private fun ImportExportThreeStepsGuide() {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_steps_guide"),
    ) {
        if (maxWidth >= 300.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ImportExportStepColumn(
                    iconRes = R.drawable.mv3d_buscar,
                    title = stringResource(R.string.backup_step_1_title),
                    description = stringResource(R.string.backup_step_1_desc),
                    testTag = "backup_step_1",
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = V19PurpleSoftText.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(20.dp),
                )
                ImportExportStepColumn(
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    title = stringResource(R.string.backup_step_2_title),
                    description = stringResource(R.string.backup_step_2_desc),
                    testTag = "backup_step_2",
                    modifier = Modifier.weight(1.18f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = V19PurpleSoftText.copy(alpha = 0.85f),
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(20.dp),
                )
                ImportExportStepColumn(
                    icon = Icons.Filled.Restore,
                    title = stringResource(R.string.backup_step_3_title),
                    description = stringResource(R.string.backup_step_3_desc),
                    testTag = "backup_step_3",
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ImportExportStepCompactRow(
                    iconRes = R.drawable.mv3d_buscar,
                    title = stringResource(R.string.backup_step_1_title),
                    description = stringResource(R.string.backup_step_1_desc),
                    testTag = "backup_step_1",
                )
                ImportExportStepCompactRow(
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    title = stringResource(R.string.backup_step_2_title),
                    description = stringResource(R.string.backup_step_2_desc),
                    testTag = "backup_step_2",
                )
                ImportExportStepCompactRow(
                    icon = Icons.Filled.Restore,
                    title = stringResource(R.string.backup_step_3_title),
                    description = stringResource(R.string.backup_step_3_desc),
                    testTag = "backup_step_3",
                )
            }
        }
    }
}

@Composable
private fun ImportExportStepColumn(
    title: String,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
) {
    Column(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (iconRes != null) {
            Mv3dIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 42.dp,
            )
        } else if (icon != null) {
            ImportExportIconBadge(
                icon = icon,
                badgeSize = 40.dp,
                iconSize = 20.dp,
                cornerRadius = 12.dp,
            )
        }
        Text(
            text = title,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = V19InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ImportExportStepCompactRow(
    title: String,
    description: String,
    testTag: String,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (iconRes != null) {
            Mv3dIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 38.dp,
            )
        } else if (icon != null) {
            ImportExportIconBadge(
                icon = icon,
                badgeSize = 36.dp,
                iconSize = 18.dp,
                cornerRadius = 10.dp,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = Color.White,
            )
            Text(
                text = description,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = V19InkMuted,
            )
        }
    }
}

@Composable
private fun ImportExportSecurityNoteBox() {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("backup_security_note"),
        shape = shape,
        color = GlassInnerBoxBg,
        border = BorderStroke(1.dp, GlassNeonBorder15),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = V19PurpleSoftText,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(HomeCardNeonBorder.copy(alpha = 0.30f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_security_offline_note),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = HomeCardLabelInk.copy(alpha = 0.90f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ImportExportProgressBanner(
    message: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xE628144D),
        border = BorderStroke(1.dp, V19PurpleGlow.copy(alpha = 0.70f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = V19PurpleSoftText,
            )
            Text(
                text = message,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = V19InkPrimary,
            )
        }
    }
}

@Composable
private fun ImportExportExportFeedbackBanner(
    uiState: ImportExportUiState,
) {
    val isSuccess = uiState.exportPhase == BackupExportPhase.SUCCESS
    val isError = uiState.exportPhase == BackupExportPhase.ERROR

    val containerColor = when {
        isError -> Color(0xFF4B1528).copy(alpha = 0.92f)
        isSuccess -> Color(0xFF103E2E).copy(alpha = 0.92f)
        else -> Color(0xFF28174E).copy(alpha = 0.92f)
    }
    val borderColor = when {
        isError -> V19ErrorSoft
        isSuccess -> Color(0xFF66E2B3)
        else -> V19PurpleGlow
    }
    val textColor = when {
        isError -> V19ErrorSoft
        isSuccess -> Color(0xFFBAF7DE)
        else -> V19InkPrimary
    }
    val icon = when {
        isError -> Icons.Filled.ErrorOutline
        isSuccess -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Info
    }
    val message = when (uiState.exportPhase) {
        BackupExportPhase.SUCCESS -> stringResource(R.string.backup_export_success)
        BackupExportPhase.CANCELLED -> stringResource(R.string.backup_export_cancelled)
        BackupExportPhase.ERROR -> stringResource(R.string.backup_export_error)
        else -> ""
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("backup_export_feedback_banner"),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = message,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = textColor,
                )
                if (isSuccess && !uiState.lastSavedFileName.isNullOrBlank()) {
                    val count = uiState.lastExportedRecordsCount ?: 0
                    val countLabel = if (count == 1) "1 registro" else "$count registros"
                    Text(
                        text = "$countLabel · ${uiState.lastSavedFileName}",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.5.sp,
                        color = Color(0xFFBAF7DE).copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportExportAnalysisFeedbackBanner(
    uiState: ImportExportUiState,
) {
    val isError = uiState.analysisPhase == BackupAnalysisPhase.ERROR
    val containerColor = if (isError) {
        Color(0xFF4B1528).copy(alpha = 0.92f)
    } else {
        Color(0xFF28174E).copy(alpha = 0.92f)
    }
    val borderColor = if (isError) V19ErrorSoft else V19PurpleGlow
    val textColor = if (isError) V19ErrorSoft else V19InkPrimary
    val icon = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.Info

    val primaryMessage = if (isError) {
        when (uiState.analysisError?.type) {
            BackupAnalysisErrorType.INCOMPATIBLE_VERSION ->
                stringResource(R.string.backup_analysis_error_version)
            BackupAnalysisErrorType.FILE_TOO_LARGE ->
                stringResource(R.string.backup_analysis_error_too_large)
            BackupAnalysisErrorType.READ_FAILED ->
                stringResource(R.string.backup_analysis_error_read)
            BackupAnalysisErrorType.INCONSISTENT_RECORDS ->
                stringResource(R.string.backup_analysis_error_inconsistent)
            BackupAnalysisErrorType.LOCAL_STATE_CHANGED ->
                stringResource(R.string.backup_analysis_error_local_changed)
            BackupAnalysisErrorType.INVALID_FILE,
            null ->
                stringResource(R.string.backup_analysis_error_invalid)
        }
    } else {
        stringResource(R.string.backup_analysis_cancelled)
    }
    val detailMessage = if (isError) uiState.analysisError?.detailMessage else null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("backup_analysis_feedback_banner"),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(width = 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = primaryMessage,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = textColor,
                )
                if (!detailMessage.isNullOrBlank()) {
                    Text(
                        text = detailMessage,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 11.5.sp,
                        color = textColor.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedBackupSummaryCardV2(
    summary: BackupAnalysisSummaryUi,
    modifier: Modifier = Modifier,
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("pt-BR"))
    }
    val formattedDate = remember(summary.exportedAtEpochMs) {
        Instant.ofEpochMilli(summary.exportedAtEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }

    ImportExportGlassCard(
        modifier = modifier.testTag("selected_backup_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImportExportIconBadge(
                icon = Icons.Filled.Inventory2,
                badgeSize = 46.dp,
                iconSize = 23.dp,
                cornerRadius = 14.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(HomeCardNeonBorder.copy(alpha = 0.34f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_selected_card_title),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag("selected_backup_card_title"),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.32f))
        Spacer(modifier = Modifier.height(14.dp))

        // Bloco de metadados do arquivo
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = GlassInnerBoxBg,
            border = BorderStroke(1.dp, GlassNeonBorder15),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                SummaryV2MetadataRow(
                    label = "Data de criação do backup",
                    value = formattedDate,
                    testTag = "backup_summary_created_at",
                )
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                SummaryV2MetadataRow(
                    label = "Versão do formato",
                    value = "v${summary.backupSchemaVersion} · ${summary.format} (Room v${summary.roomSchemaVersion})",
                    testTag = "backup_summary_format_version",
                )
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                SummaryV2MetadataRow(
                    label = "Versão Room de origem",
                    value = "Room v${summary.roomSchemaVersion}",
                    testTag = "backup_summary_room_version",
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bloco de quantidades reais contidas no backup
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = GlassInnerBoxBg,
            border = BorderStroke(1.dp, GlassNeonBorder15),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                SummaryV2CountRow(
                    iconRes = R.drawable.mv3d_pessoas,
                    label = "Total de pessoas",
                    value = summary.peopleCount.toString(),
                    testTag = "backup_summary_people_count",
                )
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                SummaryV2CountRow(
                    iconRes = R.drawable.mv3d_produtos,
                    label = "Total de produtos",
                    value = summary.productsCount.toString(),
                    testTag = "backup_summary_products_count",
                )
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                SummaryV2CountRow(
                    iconRes = R.drawable.mv3d_maquininhas,
                    label = "Total de maquininhas",
                    value = summary.machinesCount.toString(),
                    testTag = "backup_summary_machines_count",
                )
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                SummaryV2CountRow(
                    iconRes = R.drawable.mv3d_vendas,
                    label = "Total de vendas",
                    value = summary.salesCount.toString(),
                    testTag = "backup_summary_sales_count",
                )
                HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.24f))
                SummaryV2CountRow(
                    icon = Icons.Filled.Storage,
                    label = "Total de itens de venda",
                    value = summary.saleItemsCount.toString(),
                    testTag = "backup_summary_sale_items_count",
                )
            }
        }
    }
}

@Composable
fun ComparisonResultCardV2(
    summary: BackupAnalysisSummaryUi,
    modifier: Modifier = Modifier,
) {
    ImportExportGlassCard(
        modifier = modifier.testTag("comparison_result_card"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ImportExportIconBadge(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                badgeSize = 46.dp,
                iconSize = 23.dp,
                cornerRadius = 14.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(HomeCardNeonBorder.copy(alpha = 0.34f)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.backup_comparison_card_title),
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                color = Color.White,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag("comparison_result_card_title"),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = HomeCardSubtleDivider.copy(alpha = 0.32f))
        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComparisonMetricRowV2(
                icon = Icons.Filled.AddCircleOutline,
                label = "Novos",
                value = summary.newRecordsCount.toString(),
                accentColor = if (summary.newRecordsCount > 0) V19MintSuccess else V19PurpleSoftText,
                isHighlighted = false,
                testTag = "comparison_count_new",
            )
            ComparisonMetricRowV2(
                icon = Icons.Filled.CheckCircle,
                label = "Idênticos",
                value = summary.identicalRecordsCount.toString(),
                accentColor = V19PurpleSoftText,
                isHighlighted = false,
                testTag = "comparison_count_identical",
            )
            ComparisonMetricRowV2(
                icon = Icons.Filled.WarningAmber,
                label = "Conflitantes",
                value = summary.conflictingRecordsCount.toString(),
                accentColor = if (summary.conflictingRecordsCount > 0) V19GoldAccent else V19PurpleSoftText,
                isHighlighted = summary.conflictingRecordsCount > 0,
                testTag = "comparison_count_conflicting",
            )
            ComparisonMetricRowV2(
                icon = Icons.Filled.Smartphone,
                label = "Somente no celular",
                value = summary.localOnlyRecordsCount.toString(),
                accentColor = V19PurpleSoftText,
                isHighlighted = false,
                testTag = "comparison_count_local_only",
            )
            ComparisonMetricRowV2(
                icon = Icons.Filled.ErrorOutline,
                label = "Riscos relacionais",
                value = summary.relationalRisksCount.toString(),
                accentColor = if (summary.relationalRisksCount > 0) V19GoldAccent else V19PurpleSoftText,
                isHighlighted = summary.relationalRisksCount > 0,
                testTag = "comparison_count_relational_risks",
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        val hasDifferences = summary.hasDifferencesToResolve
        val statusContainer = if (hasDifferences) {
            Color(0xD93E280D)
        } else {
            Color(0xD90E3B2B)
        }
        val statusBorder = if (hasDifferences) V19GoldAccent else Color(0xFF66E2B3)
        val statusTextColor = if (hasDifferences) V19GoldAccent else Color(0xFFBAF7DE)
        val statusIcon = if (hasDifferences) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle
        val statusMessage = if (hasDifferences) {
            stringResource(R.string.backup_comparison_has_conflicts)
        } else {
            stringResource(R.string.backup_comparison_no_conflicts)
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .testTag("comparison_status_banner"),
            shape = RoundedCornerShape(16.dp),
            color = statusContainer,
            border = BorderStroke(1.2.dp, statusBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusTextColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = statusMessage,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = statusTextColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .testTag("comparison_readonly_note"),
            shape = RoundedCornerShape(14.dp),
            color = GlassInnerBoxBg,
            border = BorderStroke(1.dp, GlassNeonBorder15),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = V19PurpleSoftText,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(HomeCardNeonBorder.copy(alpha = 0.30f)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.backup_analysis_readonly_note),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = HomeCardLabelInk.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SummaryV2MetadataRow(
    label: String,
    value: String,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = V19InkMuted,
        )
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun SummaryV2CountRow(
    label: String,
    value: String,
    testTag: String,
    icon: ImageVector? = null,
    @DrawableRes iconRes: Int? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (iconRes != null) {
            Mv3dIcon(
                iconRes = iconRes,
                contentDescription = null,
                size = 36.dp,
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = V19PurpleSoftText,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp,
            color = HomeCardLabelInk.copy(alpha = 0.90f),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun ComparisonMetricRowV2(
    icon: ImageVector,
    label: String,
    value: String,
    accentColor: Color,
    isHighlighted: Boolean,
    testTag: String,
) {
    val containerColor = if (isHighlighted) {
        Color(0x9938240E)
    } else {
        GlassInnerBoxBg
    }
    val borderColor = if (isHighlighted) {
        V19GoldAccent.copy(alpha = 0.72f)
    } else {
        GlassNeonBorder15
    }

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
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = if (isHighlighted) Color.White else HomeCardLabelInk.copy(alpha = 0.92f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (isHighlighted) V19GoldAccent else Color.White,
            )
        }
    }
}

