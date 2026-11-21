package com.mixvendas3d.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mixvendas3d.app.R
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.ui.components.ComparisonResultCardV2
import com.mixvendas3d.app.ui.components.ImportExportMainCard
import com.mixvendas3d.app.ui.components.ImportExportProgressBanner
import com.mixvendas3d.app.ui.components.SelectedBackupSummaryCardV2
import com.mixvendas3d.app.ui.theme.V19ErrorSoft
import java.io.IOException

@Composable
fun ImportExportScreen(
    uiState: ImportExportUiState,
    onExportBackupClick: () -> Unit,
    onDocumentDestinationSelected: (Uri?, suspend (Uri, ByteArray) -> Unit) -> Unit,
    onSelectBackupClick: () -> Unit = {},
    onImportFileSelected: (Uri?, suspend (Uri) -> ByteArray) -> Unit = { _, _ -> },
    onSelectConflictResolution: (String, MergeConflictResolution) -> Unit = { _, _ -> },
    onPrepareReviewRestore: () -> Unit = {},
    onBackToConflicts: () -> Unit = {},
    onRequestRestoreClick: () -> Unit = {},
    onCancelRestoreConfirmation: () -> Unit = {},
    onConfirmRestoreExecute: () -> Unit = {},
    onVerifyLocalDatabaseConsistency: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        onVerifyLocalDatabaseConsistency()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onVerifyLocalDatabaseConsistency()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { selectedUri ->
        onDocumentDestinationSelected(selectedUri) { uri, bytes ->
            val stream = appContext.contentResolver.openOutputStream(uri)
                ?: throw IOException("Não foi possível abrir o destino selecionado para gravação.")
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { selectedUri ->
        onImportFileSelected(selectedUri) { uri ->
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("Não foi possível abrir o arquivo selecionado para leitura.")
            stream.use { input ->
                readBoundedBackupBytes(input, JsonBackupReader.DEFAULT_MAX_BACKUP_BYTES)
            }
        }
    }

    var launchedRequestToken by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingRequest = uiState.pendingDocumentRequest
    LaunchedEffect(pendingRequest?.requestToken) {
        if (pendingRequest != null && launchedRequestToken != pendingRequest.requestToken) {
            launchedRequestToken = pendingRequest.requestToken
            createDocumentLauncher.launch(pendingRequest.suggestedFileName)
        }
    }

    var launchedImportRequestToken by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingImportRequest = uiState.pendingOpenDocumentRequest
    LaunchedEffect(pendingImportRequest?.requestToken) {
        if (pendingImportRequest != null && launchedImportRequestToken != pendingImportRequest.requestToken) {
            launchedImportRequestToken = pendingImportRequest.requestToken
            openDocumentLauncher.launch(
                arrayOf(
                    "application/json",
                    "text/plain",
                    "application/octet-stream",
                    "*/*",
                ),
            )
        }
    }

    val confirmationUi = uiState.restoreConfirmationUi
    if (uiState.restorePhase == BackupRestorePhase.CONFIRMING && confirmationUi != null) {
        ConfirmRestoreAlertDialog(
            confirmation = confirmationUi,
            isExecuting = uiState.isRestoring,
            onCancel = onCancelRestoreConfirmation,
            onConfirm = onConfirmRestoreExecute,
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_mais_detail_import_export"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "backup_restore_section") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ImportExportMainCard(
                    uiState = uiState,
                    onExportBackupClick = onExportBackupClick,
                    onSelectBackupClick = onSelectBackupClick,
                )

                if (uiState.isRestoring) {
                    ImportExportProgressBanner(
                        message = stringResource(R.string.backup_restore_executing),
                        testTag = "backup_restore_progress_banner",
                    )
                }

                val restoreResult = uiState.restoreResultUi
                if (uiState.restorePhase == BackupRestorePhase.SUCCESS && restoreResult != null) {
                    BackupRestoreSuccessCard(result = restoreResult)
                } else if (uiState.restorePhase == BackupRestorePhase.FAILED ||
                    uiState.restorePhase == BackupRestorePhase.LOCAL_STATE_CHANGED
                ) {
                    BackupRestoreErrorCard(
                        result = restoreResult,
                        phase = uiState.restorePhase,
                    )
                }

                val summary = uiState.analysisSummary
                if (uiState.analysisPhase == BackupAnalysisPhase.ANALYSIS_READY && summary != null) {
                    SelectedBackupSummaryCardV2(summary = summary)
                    ComparisonResultCardV2(summary = summary)

                    if (uiState.previewPhase == BackupPreviewPhase.PREPARING) {
                        ImportExportProgressBanner(
                            message = stringResource(R.string.backup_preview_preparing),
                            testTag = "backup_preview_progress_banner",
                        )
                    }

                    if (uiState.previewPhase == BackupPreviewPhase.INVALID ||
                        uiState.previewPhase == BackupPreviewPhase.VALIDATION_ERROR
                    ) {
                        BackupPreviewErrorBanner(messages = uiState.previewErrorMessages)
                    }

                    val activePreview = uiState.previewUi
                    if (uiState.previewPhase == BackupPreviewPhase.AVAILABLE && activePreview != null) {
                        RevisarRestauracaoSection(
                            preview = activePreview,
                            isBusy = uiState.isBusy,
                            canTriggerRestore = uiState.canTriggerRestore,
                            onRequestRestoreClick = onRequestRestoreClick,
                            onBackToConflicts = onBackToConflicts,
                        )
                    } else {
                        val conflictSection = uiState.conflictSection
                        if (conflictSection != null) {
                            ResolverConflitosSection(
                                section = conflictSection,
                                isBusy = uiState.isBusy,
                                isReviewReadyConfirmed = uiState.isReviewReadyConfirmed,
                                onSelectConflictResolution = onSelectConflictResolution,
                                onPrepareReviewClick = onPrepareReviewRestore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupPreviewErrorBanner(
    messages: List<String>,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("backup_preview_error_banner"),
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
                    text = stringResource(R.string.backup_preview_invalid_title),
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

