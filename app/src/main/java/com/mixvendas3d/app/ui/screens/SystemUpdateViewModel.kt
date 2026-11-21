package com.mixvendas3d.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.repository.SystemUpdateRepository
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupResult
import com.mixvendas3d.app.domain.update.SystemUpdateAnalysisResult
import com.mixvendas3d.app.domain.update.SystemUpdateBatchSummary
import com.mixvendas3d.app.domain.update.SystemUpdateExecutionResult
import com.mixvendas3d.app.domain.update.SystemUpdateFailureReason
import com.mixvendas3d.app.domain.update.SystemUpdateUndoResult
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SystemUpdatePhase {
    IDLE,
    ANALYZING,
    NO_CHANGES,
    CHANGES_FOUND,
    CONFLICTS_FOUND,
    APPLYING,
    UPDATE_SUCCESS,
    UPDATE_FAILED,
    UNDOING,
    UNDO_SUCCESS,
    UNDO_FAILED,
}

data class SystemUpdateUiState(
    val phase: SystemUpdatePhase = SystemUpdatePhase.IDLE,
    val analysis: SystemUpdateAnalysisResult? = null,
    val undoBatchSummary: SystemUpdateBatchSummary? = null,
    val allowPartialUpdateWithConflicts: Boolean = false,
    val isConfirmingUpdateDialog: Boolean = false,
    val isConfirmingUndoDialog: Boolean = false,
    val executionResult: SystemUpdateExecutionResult? = null,
    val undoResult: SystemUpdateUndoResult? = null,
    val feedbackErrorMessage: String? = null,
    val backupExportPhase: BackupExportPhase = BackupExportPhase.IDLE,
    val pendingBackupDocumentRequest: PendingJsonBackupDocumentRequest? = null,
    val lastBackupFileName: String? = null,
    val lastBackupRecordsCount: Int? = null,
) {
    val isPreviewAvailable: Boolean
        get() = analysis != null &&
            analysis.salesToUpdateCount > 0 &&
            (phase == SystemUpdatePhase.CHANGES_FOUND || phase == SystemUpdatePhase.CONFLICTS_FOUND)

    val canStartUpdate: Boolean
        get() {
            val currentAnalysis = analysis ?: return false
            if (isBusy) return false
            if (currentAnalysis.salesToUpdateCount <= 0) return false
            if (phase != SystemUpdatePhase.CHANGES_FOUND && phase != SystemUpdatePhase.CONFLICTS_FOUND) {
                return false
            }
            return if (currentAnalysis.requiresReviewCount > 0) {
                allowPartialUpdateWithConflicts
            } else {
                true
            }
        }

    val isBackupBusy: Boolean
        get() = backupExportPhase == BackupExportPhase.PREPARING ||
            backupExportPhase == BackupExportPhase.AWAITING_DESTINATION ||
            backupExportPhase == BackupExportPhase.WRITING ||
            pendingBackupDocumentRequest != null

    val isBusy: Boolean
        get() = phase == SystemUpdatePhase.ANALYZING ||
            phase == SystemUpdatePhase.APPLYING ||
            phase == SystemUpdatePhase.UNDOING ||
            isBackupBusy
}

class SystemUpdateViewModel(
    private val repository: SystemUpdateRepository,
    private val generateBackup: suspend () -> JsonBackupResult,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemUpdateUiState())
    val uiState: StateFlow<SystemUpdateUiState> = _uiState.asStateFlow()

    private val operationLock = AtomicBoolean(false)

    @Volatile
    private var activePendingBackup: JsonBackupResult? = null

    init {
        refreshUndoStatus()
    }

    fun refreshUndoStatus() {
        viewModelScope.launch {
            val summary = withContext(ioDispatcher) {
                runCatching { repository.getUndoBatchSummary() }.getOrNull()
            }
            _uiState.value = _uiState.value.copy(undoBatchSummary = summary)
        }
    }

    fun analyzeAdjustments() {
        val current = _uiState.value
        if (current.isBusy || !operationLock.compareAndSet(false, true)) return

        _uiState.value = current.copy(
            phase = SystemUpdatePhase.ANALYZING,
            allowPartialUpdateWithConflicts = false,
            isConfirmingUpdateDialog = false,
            isConfirmingUndoDialog = false,
            executionResult = null,
            undoResult = null,
            feedbackErrorMessage = null,
        )

        viewModelScope.launch {
            try {
                val (result, undoSummary) = withContext(ioDispatcher) {
                    val analyzed = repository.analyze()
                    val undo = repository.getUndoBatchSummary()
                    analyzed to undo
                }
                val nextPhase = when {
                    result.requiresReviewCount > 0 -> SystemUpdatePhase.CONFLICTS_FOUND
                    result.salesToUpdateCount > 0 -> SystemUpdatePhase.CHANGES_FOUND
                    else -> SystemUpdatePhase.NO_CHANGES
                }
                _uiState.value = _uiState.value.copy(
                    phase = nextPhase,
                    analysis = result,
                    undoBatchSummary = undoSummary,
                    allowPartialUpdateWithConflicts = false,
                    feedbackErrorMessage = null,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: Throwable) {
                _uiState.value = _uiState.value.copy(
                    phase = SystemUpdatePhase.UPDATE_FAILED,
                    feedbackErrorMessage = ex.message ?: "Não foi possível analisar os ajustes locais.",
                )
            } finally {
                operationLock.set(false)
            }
        }
    }

    fun setAllowPartialUpdateWithConflicts(allow: Boolean) {
        val current = _uiState.value
        if (current.isBusy) return
        _uiState.value = current.copy(allowPartialUpdateWithConflicts = allow)
    }

    fun requestConfirmUpdate() {
        val current = _uiState.value
        if (!current.canStartUpdate) return
        _uiState.value = current.copy(isConfirmingUpdateDialog = true)
    }

    fun cancelConfirmUpdate() {
        _uiState.value = _uiState.value.copy(isConfirmingUpdateDialog = false)
    }

    fun confirmAndApplyUpdate() {
        val current = _uiState.value
        val analyzed = current.analysis ?: return
        if (!current.canStartUpdate || !operationLock.compareAndSet(false, true)) return

        val allowPartial = current.allowPartialUpdateWithConflicts && analyzed.requiresReviewCount > 0

        _uiState.value = current.copy(
            phase = SystemUpdatePhase.APPLYING,
            isConfirmingUpdateDialog = false,
            feedbackErrorMessage = null,
        )

        viewModelScope.launch {
            try {
                val (execResult, undoSummary) = withContext(ioDispatcher) {
                    val result = repository.applyUpdate(
                        expectedFingerprint = analyzed.localStateFingerprint,
                        allowPartialUpdate = allowPartial,
                    )
                    val undo = repository.getUndoBatchSummary()
                    result to undo
                }

                if (execResult.success) {
                    _uiState.value = _uiState.value.copy(
                        phase = SystemUpdatePhase.UPDATE_SUCCESS,
                        analysis = null,
                        undoBatchSummary = undoSummary,
                        allowPartialUpdateWithConflicts = false,
                        executionResult = execResult,
                        undoResult = null,
                        feedbackErrorMessage = null,
                    )
                } else {
                    val refreshedAnalysis = if (execResult.failureReason == SystemUpdateFailureReason.LOCAL_STATE_CHANGED) {
                        null
                    } else {
                        analyzed
                    }
                    _uiState.value = _uiState.value.copy(
                        phase = SystemUpdatePhase.UPDATE_FAILED,
                        analysis = refreshedAnalysis,
                        undoBatchSummary = undoSummary,
                        executionResult = execResult,
                        feedbackErrorMessage = execResult.errorMessage,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: Throwable) {
                _uiState.value = _uiState.value.copy(
                    phase = SystemUpdatePhase.UPDATE_FAILED,
                    feedbackErrorMessage = ex.message ?: "Erro ao executar a atualização do histórico.",
                )
            } finally {
                operationLock.set(false)
            }
        }
    }

    fun requestConfirmUndo() {
        val current = _uiState.value
        val summary = current.undoBatchSummary ?: return
        if (current.isBusy || !summary.canUndoSafely) return
        _uiState.value = current.copy(isConfirmingUndoDialog = true)
    }

    fun cancelConfirmUndo() {
        _uiState.value = _uiState.value.copy(isConfirmingUndoDialog = false)
    }

    fun confirmAndRevertLastUpdate() {
        val current = _uiState.value
        if (current.isBusy || !operationLock.compareAndSet(false, true)) return

        _uiState.value = current.copy(
            phase = SystemUpdatePhase.UNDOING,
            isConfirmingUndoDialog = false,
            feedbackErrorMessage = null,
        )

        viewModelScope.launch {
            try {
                val (undoRes, undoSummary) = withContext(ioDispatcher) {
                    val result = repository.revertLastUpdate()
                    val undo = repository.getUndoBatchSummary()
                    result to undo
                }

                if (undoRes.success) {
                    _uiState.value = _uiState.value.copy(
                        phase = SystemUpdatePhase.UNDO_SUCCESS,
                        analysis = null,
                        undoBatchSummary = undoSummary,
                        allowPartialUpdateWithConflicts = false,
                        executionResult = null,
                        undoResult = undoRes,
                        feedbackErrorMessage = null,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        phase = SystemUpdatePhase.UNDO_FAILED,
                        undoBatchSummary = undoSummary,
                        undoResult = undoRes,
                        feedbackErrorMessage = undoRes.errorMessage,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (ex: Throwable) {
                _uiState.value = _uiState.value.copy(
                    phase = SystemUpdatePhase.UNDO_FAILED,
                    feedbackErrorMessage = ex.message ?: "Erro ao desfazer a última atualização.",
                )
            } finally {
                operationLock.set(false)
            }
        }
    }

    fun requestExportBackupJson() {
        val current = _uiState.value
        if (current.isBusy || !operationLock.compareAndSet(false, true)) return

        activePendingBackup = null
        _uiState.value = current.copy(
            backupExportPhase = BackupExportPhase.PREPARING,
            pendingBackupDocumentRequest = null,
            lastBackupFileName = null,
            lastBackupRecordsCount = null,
        )

        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) { generateBackup() }
                if (result.utf8Bytes.isEmpty() || result.suggestedFileName.isBlank()) {
                    throw IllegalStateException("Backup JSON inválido.")
                }
                activePendingBackup = result
                _uiState.value = _uiState.value.copy(
                    backupExportPhase = BackupExportPhase.AWAITING_DESTINATION,
                    pendingBackupDocumentRequest = PendingJsonBackupDocumentRequest(
                        requestToken = UUID.randomUUID().toString(),
                        suggestedFileName = result.suggestedFileName,
                    ),
                )
            } catch (ce: CancellationException) {
                activePendingBackup = null
                throw ce
            } catch (_: Throwable) {
                activePendingBackup = null
                _uiState.value = _uiState.value.copy(
                    backupExportPhase = BackupExportPhase.ERROR,
                    pendingBackupDocumentRequest = null,
                )
            } finally {
                operationLock.set(false)
            }
        }
    }

    fun onBackupDocumentDestinationSelected(
        uri: Uri?,
        writeBytes: suspend (Uri, ByteArray) -> Unit,
    ) {
        if (!operationLock.compareAndSet(false, true)) return

        val current = _uiState.value
        val pendingRequest = current.pendingBackupDocumentRequest
        val pendingBackup = activePendingBackup

        if (pendingRequest == null || pendingBackup == null) {
            activePendingBackup = null
            _uiState.value = current.copy(
                backupExportPhase = if (uri != null) BackupExportPhase.ERROR else BackupExportPhase.IDLE,
                pendingBackupDocumentRequest = null,
            )
            operationLock.set(false)
            return
        }

        if (uri == null) {
            activePendingBackup = null
            _uiState.value = current.copy(
                backupExportPhase = BackupExportPhase.CANCELLED,
                pendingBackupDocumentRequest = null,
            )
            operationLock.set(false)
            return
        }

        val bytes = pendingBackup.utf8Bytes
        val fileName = pendingBackup.suggestedFileName
        val recordsCount = pendingBackup.totalRecordsCount

        _uiState.value = current.copy(
            backupExportPhase = BackupExportPhase.WRITING,
            pendingBackupDocumentRequest = null,
        )

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    writeBytes(uri, bytes)
                }
                activePendingBackup = null
                _uiState.value = _uiState.value.copy(
                    backupExportPhase = BackupExportPhase.SUCCESS,
                    pendingBackupDocumentRequest = null,
                    lastBackupFileName = fileName,
                    lastBackupRecordsCount = recordsCount,
                )
            } catch (ce: CancellationException) {
                activePendingBackup = null
                throw ce
            } catch (_: Throwable) {
                activePendingBackup = null
                _uiState.value = _uiState.value.copy(
                    backupExportPhase = BackupExportPhase.ERROR,
                    pendingBackupDocumentRequest = null,
                )
            } finally {
                operationLock.set(false)
            }
        }
    }

    companion object {
        fun provideFactory(
            database: AppDatabase,
            repository: SystemUpdateRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SystemUpdateViewModel(
                    repository = repository,
                    generateBackup = { JsonBackupExporter.export(database) },
                ) as T
            }
        }
    }
}
