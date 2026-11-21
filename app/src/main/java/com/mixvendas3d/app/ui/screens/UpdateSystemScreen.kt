package com.mixvendas3d.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mixvendas3d.app.R
import com.mixvendas3d.app.ui.components.Mv3dIcon
import com.mixvendas3d.app.ui.theme.GlassInnerBoxBg
import com.mixvendas3d.app.ui.theme.GlassNeonBorder15
import com.mixvendas3d.app.ui.theme.HomeCardGlassBottom
import com.mixvendas3d.app.ui.theme.HomeCardGlassTop
import com.mixvendas3d.app.ui.theme.HomeCardLabelInk
import com.mixvendas3d.app.ui.theme.HomeCardNeonBorder
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
import com.mixvendas3d.app.ui.theme.V19PurplePrimary
import com.mixvendas3d.app.ui.theme.V19PurpleSoftText
import com.mixvendas3d.app.ui.theme.V19SurfaceChip
import com.mixvendas3d.app.ui.theme.V19SurfaceInput
import java.io.IOException

@Composable
fun UpdateSystemScreen(
    uiState: SystemUpdateUiState,
    onAnalyzeClick: () -> Unit,
    onBackupClick: () -> Unit,
    onBackupDocumentDestinationSelected: (Uri?, suspend (Uri, ByteArray) -> Unit) -> Unit,
    onAllowPartialChanged: (Boolean) -> Unit,
    onRequestApplyUpdateClick: () -> Unit,
    onDismissApplyDialog: () -> Unit,
    onConfirmApplyUpdate: () -> Unit,
    onRequestUndoClick: () -> Unit,
    onDismissUndoDialog: () -> Unit,
    onConfirmUndo: () -> Unit,
    onRefreshUndoStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        onRefreshUndoStatus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshUndoStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { selectedUri ->
        onBackupDocumentDestinationSelected(selectedUri) { uri, bytes ->
            val stream = appContext.contentResolver.openOutputStream(uri)
                ?: throw IOException("Não foi possível gravar o backup JSON no destino selecionado.")
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }

    var launchedToken by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRequest = uiState.pendingBackupDocumentRequest
    LaunchedEffect(pendingRequest?.requestToken) {
        if (pendingRequest != null && launchedToken != pendingRequest.requestToken) {
            launchedToken = pendingRequest.requestToken
            createBackupLauncher.launch(pendingRequest.suggestedFileName)
        }
    }

    val analysis = uiState.analysis
    if (uiState.isConfirmingUpdateDialog && analysis != null) {
        ConfirmSystemUpdateDialog(
            analysis = analysis,
            isPartialWithConflicts = analysis.requiresReviewCount > 0 && uiState.allowPartialUpdateWithConflicts,
            onDismiss = onDismissApplyDialog,
            onConfirm = onConfirmApplyUpdate,
        )
    }

    val undoSummary = uiState.undoBatchSummary
    if (uiState.isConfirmingUndoDialog && undoSummary != null) {
        ConfirmUndoSystemUpdateDialog(
            summary = undoSummary,
            onDismiss = onDismissUndoDialog,
            onConfirm = onConfirmUndo,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_mais_detail_update_system")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                UpdateSystemGlassContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_system_header_card"),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Mv3dIcon(
                                iconRes = R.drawable.mv3d_atualizar_sistema,
                                contentDescription = null,
                                size = 52.dp,
                                scalePercent = 180,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = V19SurfaceChip,
                                    border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.45f)),
                                ) {
                                    Text(
                                        text = stringResource(R.string.update_system_badge),
                                        fontFamily = PlusJakartaSansFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = V19PurpleSoftText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.update_system_title),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    lineHeight = 26.sp,
                                    color = V19InkPrimary,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.update_system_intro),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = HomeCardLabelInk.copy(alpha = 0.88f),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Analisar Button (Primary Neon Gradient)
                        val analyzeEnabled = !uiState.isBusy
                        val analyzeShape = RoundedCornerShape(16.dp)
                        val analyzeBrush = if (analyzeEnabled) {
                            Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    HomeCtaGradientStart.copy(alpha = 0.42f),
                                    HomeCtaGradientEnd.copy(alpha = 0.42f),
                                ),
                            )
                        }
                        val analyzeBorder = if (analyzeEnabled) {
                            HomeCtaBorderGlow.copy(alpha = 0.90f)
                        } else {
                            HomeCtaBorderGlow.copy(alpha = 0.32f)
                        }
                        val analyzeContentTint = if (analyzeEnabled) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.56f)
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .shadow(
                                    elevation = if (analyzeEnabled) 12.dp else 0.dp,
                                    shape = analyzeShape,
                                    ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                                    spotColor = HomeCtaShadowGlow.copy(alpha = 0.80f),
                                )
                                .clip(analyzeShape)
                                .background(analyzeBrush)
                                .border(1.2.dp, analyzeBorder, analyzeShape)
                                .clickable(
                                    enabled = analyzeEnabled,
                                    role = Role.Button,
                                    onClick = onAnalyzeClick,
                                )
                                .testTag("update_system_analyze_button")
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Mv3dIcon(
                                    iconRes = R.drawable.mv3d_atualizar_sistema,
                                    contentDescription = null,
                                    size = 36.dp,
                                    alpha = if (analyzeEnabled) 1f else 0.56f,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(R.string.update_system_analyze_action),
                                    fontFamily = PlusJakartaSansFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = analyzeContentTint,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onBackupClick,
                            enabled = !uiState.isBusy,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.2.dp, GlassNeonBorder15),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = GlassInnerBoxBg,
                                contentColor = V19PurpleSoftText,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("update_system_backup_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SaveAlt,
                                contentDescription = null,
                                tint = V19PurpleSoftText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(R.string.update_system_backup_action),
                                fontFamily = PlusJakartaSansFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = V19PurpleSoftText,
                            )
                        }
                    }

                    if (uiState.phase == SystemUpdatePhase.ANALYZING ||
                        uiState.phase == SystemUpdatePhase.APPLYING ||
                        uiState.phase == SystemUpdatePhase.UNDOING ||
                        uiState.isBackupBusy
                    ) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val progressText = when {
                            uiState.phase == SystemUpdatePhase.ANALYZING -> "Analisando vendas e cadastros no Room..."
                            uiState.phase == SystemUpdatePhase.APPLYING -> "Aplicando atualizações no banco local..."
                            uiState.phase == SystemUpdatePhase.UNDOING -> "Revertendo vendas para o snapshot anterior..."
                            else -> "Preparando backup JSON..."
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_system_progress_indicator"),
                            shape = RoundedCornerShape(12.dp),
                            color = V19SurfaceInput.copy(alpha = 0.90f),
                            border = BorderStroke(1.dp, HomeCardNeonBorder.copy(alpha = 0.40f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = V19PurpleSoftText,
                                )
                                Text(
                                    text = progressText,
                                    fontFamily = PlusJakartaSansFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = V19PurpleSoftText,
                                )
                            }
                        }
                    }

                    BackupInlineFeedbackBanner(uiState = uiState)
                }

                if (undoSummary != null) {
                    UndoAvailableCard(
                        summary = undoSummary,
                        isBusy = uiState.isBusy,
                        onRequestUndoClick = onRequestUndoClick,
                    )
                }

                PhaseResultBanner(uiState = uiState)

                if (analysis != null) {
                    AnalysisSummarySection(
                        analysis = analysis,
                        uiState = uiState,
                        onAllowPartialChanged = onAllowPartialChanged,
                        onRequestApplyUpdateClick = onRequestApplyUpdateClick,
                    )
                }
        }

        if (analysis != null && analysis.conflictItems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("update_system_conflicts_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xD94B1528),
                    border = BorderStroke(1.2.dp, V19ErrorSoft.copy(alpha = 0.80f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = V19ErrorSoft,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.update_system_conflicts_banner),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = V19ErrorSoft,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            analysis.conflictItems.forEach { conflict ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    SaleUpdateConflictCard(conflict = conflict)
                }
            }
        }

        if (analysis != null && analysis.updatableItems.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag("update_system_preview_banner"),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xD93E280D),
                    border = BorderStroke(1.2.dp, V19GoldAccent.copy(alpha = 0.78f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = V19GoldAccent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.update_system_preview_banner),
                            fontFamily = PlusJakartaSansFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = V19InkPrimary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            analysis.updatableItems.forEach { candidate ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                ) {
                    SaleUpdatePreviewCard(candidate = candidate)
                }
            }
        }
    }
}

@Composable
private fun BackupInlineFeedbackBanner(uiState: SystemUpdateUiState) {
    val (bg, border, textColor, icon) = when (uiState.backupExportPhase) {
        BackupExportPhase.SUCCESS -> Quad(
            Color(0xD90E3B2B),
            Color(0xFF66E2B3).copy(alpha = 0.85f),
            Color(0xFFBAF7DE),
            Icons.Filled.CheckCircle,
        )
        BackupExportPhase.CANCELLED -> Quad(
            Color(0xD93E280D),
            V19GoldAccent.copy(alpha = 0.78f),
            V19GoldAccent,
            Icons.Filled.WarningAmber,
        )
        BackupExportPhase.ERROR -> Quad(
            Color(0xD94B1528),
            V19ErrorSoft.copy(alpha = 0.80f),
            V19ErrorSoft,
            Icons.Filled.ErrorOutline,
        )
        else -> return
    }
    val msg = when (uiState.backupExportPhase) {
        BackupExportPhase.SUCCESS ->
            "Backup JSON salvo (${uiState.lastBackupFileName ?: "arquivo"} · ${uiState.lastBackupRecordsCount ?: 0} registros)."
        BackupExportPhase.CANCELLED ->
            "Exportação de backup cancelada. Nenhuma venda foi alterada."
        BackupExportPhase.ERROR ->
            "Não foi possível salvar o backup JSON."
        else -> return
    }
    Spacer(modifier = Modifier.height(10.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("update_system_backup_feedback"),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.1.dp, border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = msg,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UndoAvailableCard(
    summary: com.mixvendas3d.app.domain.update.SystemUpdateBatchSummary,
    isBusy: Boolean,
    onRequestUndoClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                ambientColor = V19GoldAccent.copy(alpha = 0.22f),
                spotColor = V19GoldAccent.copy(alpha = 0.32f),
            )
            .testTag("update_system_undo_banner"),
        shape = cardShape,
        color = Color.Transparent,
        border = BorderStroke(1.25.dp, V19GoldAccent.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xE62E193E), Color(0xDC1B0A2B)),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xD93E280D),
                    border = BorderStroke(1.dp, V19GoldAccent.copy(alpha = 0.75f)),
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = V19GoldAccent,
                        modifier = Modifier.padding(7.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_system_undo_banner),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        color = V19GoldAccent,
                    )
                    Text(
                        text = "Aplicada em ${formatUpdateEpoch(summary.appliedAtEpochMs)} · ${summary.updatedSalesCount} venda(s) modificada(s)" +
                            if (summary.appliedAsPartial) " (atualização parcial · ${summary.skippedConflictCount} conflito(s) preservados)" else "",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = HomeCardLabelInk.copy(alpha = 0.88f),
                    )
                }
            }

            if (!summary.canUndoSafely && summary.undoBlockReasons.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xD94B1528),
                    border = BorderStroke(1.dp, V19ErrorSoft.copy(alpha = 0.65f)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        summary.undoBlockReasons.forEach { reason ->
                            Text(
                                text = "• $reason",
                                fontFamily = PlusJakartaSansFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = V19ErrorSoft,
                            )
                        }
                    }
                }
            }

            val undoEnabled = !isBusy && summary.canUndoSafely
            val undoShape = RoundedCornerShape(14.dp)
            val undoBrush = if (undoEnabled) {
                Brush.horizontalGradient(listOf(V19GoldAccent, HomeCtaGradientStart))
            } else {
                Brush.horizontalGradient(
                    listOf(
                        V19GoldAccent.copy(alpha = 0.25f),
                        HomeCtaGradientStart.copy(alpha = 0.25f),
                    ),
                )
            }
            val undoBorder = if (undoEnabled) {
                V19GoldAccent.copy(alpha = 0.90f)
            } else {
                V19InkMuted.copy(alpha = 0.35f)
            }
            val undoTint = if (undoEnabled) {
                Color.White
            } else {
                V19InkMuted.copy(alpha = 0.72f)
            }

            Box(
                modifier = Modifier
                    .testTag("update_system_undo_button")
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .shadow(
                        elevation = if (undoEnabled) 8.dp else 0.dp,
                        shape = undoShape,
                        ambientColor = V19GoldAccent.copy(alpha = 0.40f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.55f),
                    )
                    .clip(undoShape)
                    .background(undoBrush)
                    .border(1.2.dp, undoBorder, undoShape)
                    .clickable(
                        enabled = undoEnabled,
                        role = Role.Button,
                        onClick = onRequestUndoClick,
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = undoTint,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.update_system_undo_action),
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = undoTint,
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseResultBanner(uiState: SystemUpdateUiState) {
    when (uiState.phase) {
        SystemUpdatePhase.NO_CHANGES -> {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("update_system_no_changes_banner"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xD90E3B2B),
                border = BorderStroke(1.2.dp, Color(0xFF66E2B3).copy(alpha = 0.85f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                        text = "Nenhuma alteração pendente: todo o histórico já corresponde aos cadastros atuais.",
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
        SystemUpdatePhase.UPDATE_SUCCESS -> {
            val exec = uiState.executionResult
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("update_system_success_banner"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xD90E3B2B),
                border = BorderStroke(1.2.dp, Color(0xFF66E2B3).copy(alpha = 0.85f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                        text = "Histórico atualizado com sucesso (${exec?.updatedSalesCount ?: 0} venda(s) recalculadas).",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFBAF7DE),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        SystemUpdatePhase.UNDO_SUCCESS -> {
            val undo = uiState.undoResult
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("update_system_undo_success_banner"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xD90E3B2B),
                border = BorderStroke(1.2.dp, Color(0xFF66E2B3).copy(alpha = 0.85f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
                        text = "Última atualização desfeita com sucesso (${undo?.revertedSalesCount ?: 0} venda(s) restauradas).",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFBAF7DE),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        SystemUpdatePhase.UPDATE_FAILED,
        SystemUpdatePhase.UNDO_FAILED -> {
            val msg = uiState.feedbackErrorMessage ?: "Não foi possível concluir a operação."
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("update_system_error_banner"),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xD94B1528),
                border = BorderStroke(1.2.dp, V19ErrorSoft.copy(alpha = 0.80f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = V19ErrorSoft,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = msg,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = V19ErrorSoft,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun AnalysisSummarySection(
    analysis: com.mixvendas3d.app.domain.update.SystemUpdateAnalysisResult,
    uiState: SystemUpdateUiState,
    onAllowPartialChanged: (Boolean) -> Unit,
    onRequestApplyUpdateClick: () -> Unit,
) {
    UpdateSystemGlassContainer {
        Text(
            text = "Resumo da análise (${analysis.totalSalesEvaluated} venda(s) avaliadas)",
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = V19InkPrimary,
            modifier = Modifier.testTag("update_system_summary_card"),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpdateMetricTile(
                label = stringResource(R.string.update_system_metric_to_update),
                count = analysis.salesToUpdateCount,
                accent = if (analysis.salesToUpdateCount > 0) V19PurpleSoftText else V19InkMuted,
                testTag = "update_system_metric_to_update",
                modifier = Modifier.fillMaxWidth(),
            )
            UpdateMetricTile(
                label = stringResource(R.string.update_system_metric_already_correct),
                count = analysis.alreadyCorrectCount,
                accent = V19MintSuccess,
                testTag = "update_system_metric_already_correct",
                modifier = Modifier.fillMaxWidth(),
            )
            UpdateMetricTile(
                label = stringResource(R.string.update_system_metric_requires_review),
                count = analysis.requiresReviewCount,
                accent = if (analysis.requiresReviewCount > 0) V19ErrorSoft else V19InkMuted,
                testTag = "update_system_metric_requires_review",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (analysis.requiresReviewCount > 0 && analysis.salesToUpdateCount > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onAllowPartialChanged(!uiState.allowPartialUpdateWithConflicts) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = uiState.allowPartialUpdateWithConflicts,
                    onCheckedChange = onAllowPartialChanged,
                    colors = CheckboxDefaults.colors(checkedColor = V19PurplePrimary),
                    modifier = Modifier.testTag("update_system_partial_opt_in_checkbox"),
                )
                Text(
                    text = "Atualizar somente as ${analysis.salesToUpdateCount} venda(s) sem conflito (atualização parcial; ${analysis.requiresReviewCount} venda(s) com conflito permanecerão intactas).",
                    fontFamily = PlusJakartaSansFontFamily,
                    style = MaterialTheme.typography.bodySmall,
                    color = V19GoldAccent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (analysis.salesToUpdateCount > 0) {
            Spacer(modifier = Modifier.height(14.dp))
            val applyEnabled = uiState.canStartUpdate
            val applyShape = RoundedCornerShape(16.dp)
            val applyBrush = if (applyEnabled) {
                Brush.horizontalGradient(listOf(HomeCtaGradientStart, HomeCtaGradientEnd))
            } else {
                Brush.horizontalGradient(
                    listOf(
                        HomeCtaGradientStart.copy(alpha = 0.42f),
                        HomeCtaGradientEnd.copy(alpha = 0.42f),
                    ),
                )
            }
            val applyBorder = if (applyEnabled) {
                HomeCtaBorderGlow.copy(alpha = 0.90f)
            } else {
                HomeCtaBorderGlow.copy(alpha = 0.32f)
            }
            val applyContentTint = if (applyEnabled) {
                Color.White
            } else {
                Color.White.copy(alpha = 0.56f)
            }

            Box(
                modifier = Modifier
                    .testTag("update_system_apply_button")
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .shadow(
                        elevation = if (applyEnabled) 12.dp else 0.dp,
                        shape = applyShape,
                        ambientColor = HomeCtaShadowGlow.copy(alpha = 0.65f),
                        spotColor = HomeCtaShadowGlow.copy(alpha = 0.80f),
                    )
                    .clip(applyShape)
                    .background(applyBrush)
                    .border(1.2.dp, applyBorder, applyShape)
                    .clickable(
                        enabled = applyEnabled,
                        role = Role.Button,
                        onClick = onRequestApplyUpdateClick,
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.update_system_apply_action),
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = applyContentTint,
                )
            }
        }
    }
}

@Composable
private fun UpdateSystemGlassContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(HomeCardGlassTop, HomeCardGlassBottom),
                ),
            )
            .border(1.25.dp, GlassNeonBorder15, shape)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        content = content,
    )
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)
