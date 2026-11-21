package com.mixvendas3d.app.ui.screens

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.domain.export.BackupComparisonResult
import com.mixvendas3d.app.domain.export.JsonBackupComparator
import com.mixvendas3d.app.domain.export.JsonBackupErrorCode
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.JsonBackupMergeDecisionValidator
import com.mixvendas3d.app.domain.export.JsonBackupMergeExecutor
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanResult
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanner
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewBuilder
import com.mixvendas3d.app.domain.export.JsonBackupMergePreviewResult
import com.mixvendas3d.app.domain.export.JsonBackupReadResult
import com.mixvendas3d.app.domain.export.JsonBackupReader
import com.mixvendas3d.app.domain.export.JsonBackupResult
import com.mixvendas3d.app.domain.export.JsonBackupValidationError
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeDecisionValidationResult
import com.mixvendas3d.app.domain.export.MergeExecutionFailureReason
import com.mixvendas3d.app.domain.export.MergeExecutionResult
import com.mixvendas3d.app.domain.export.PreviewExecutionContract
import com.mixvendas3d.app.domain.export.ValidatedBackupData
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
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

enum class BackupExportPhase {
    IDLE,
    PREPARING,
    AWAITING_DESTINATION,
    WRITING,
    SUCCESS,
    CANCELLED,
    ERROR,
}

enum class BackupAnalysisPhase {
    IDLE,
    SELECTING_FILE,
    READING,
    VALIDATING,
    COMPARING,
    ANALYSIS_READY,
    CANCELLED,
    ERROR,
}

enum class BackupAnalysisErrorType {
    INVALID_FILE,
    INCOMPATIBLE_VERSION,
    FILE_TOO_LARGE,
    READ_FAILED,
    INCONSISTENT_RECORDS,
    LOCAL_STATE_CHANGED,
}

data class BackupAnalysisErrorUi(
    val type: BackupAnalysisErrorType,
    val detailMessage: String? = null,
)

data class PendingJsonBackupDocumentRequest(
    val requestToken: String,
    val suggestedFileName: String,
)

data class PendingOpenBackupDocumentRequest(
    val requestToken: String,
)

data class BackupAnalysisSummaryUi(
    val exportedAtEpochMs: Long,
    val format: String,
    val backupSchemaVersion: Int,
    val roomSchemaVersion: Int,
    val peopleCount: Int,
    val productsCount: Int,
    val machinesCount: Int,
    val salesCount: Int,
    val saleItemsCount: Int,
    val newRecordsCount: Int,
    val identicalRecordsCount: Int,
    val conflictingRecordsCount: Int,
    val localOnlyRecordsCount: Int,
    val relationalRisksCount: Int,
    val hasDifferencesToResolve: Boolean,
)

data class BackupComparisonOutcome(
    val comparison: BackupComparisonResult,
    val localStateFingerprint: String,
    val mergePlan: JsonBackupMergePlanResult? = null,
    val initialValidation: MergeDecisionValidationResult? = null,
    val unitDetailsByKey: Map<String, BackupConflictVisualDetails> = emptyMap(),
)

internal class BackupFileTooLargeException(
    maxBytes: Int,
) : IOException("Arquivo de backup excede o limite seguro máximo de $maxBytes bytes.")

internal fun readBoundedBackupBytes(
    input: InputStream,
    maxBytes: Int = JsonBackupReader.DEFAULT_MAX_BACKUP_BYTES,
): ByteArray {
    val buffer = ByteArray(8 * 1024)
    val output = ByteArrayOutputStream()
    var totalRead = 0
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        totalRead += read
        if (totalRead > maxBytes) {
            throw BackupFileTooLargeException(maxBytes)
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private data class BackupAnalysisSession(
    val readResult: JsonBackupReadResult,
    val comparisonResult: BackupComparisonResult,
    val localStateFingerprint: String,
    val mergePlan: JsonBackupMergePlanResult?,
    val decisionsByConflictKey: Map<String, MergeConflictResolution>,
    val lastValidationResult: MergeDecisionValidationResult?,
    val unitDetailsByKey: Map<String, BackupConflictVisualDetails>,
)

data class ImportExportUiState(
    val exportPhase: BackupExportPhase = BackupExportPhase.IDLE,
    val pendingDocumentRequest: PendingJsonBackupDocumentRequest? = null,
    val lastSavedFileName: String? = null,
    val lastExportedRecordsCount: Int? = null,
    val analysisPhase: BackupAnalysisPhase = BackupAnalysisPhase.IDLE,
    val pendingOpenDocumentRequest: PendingOpenBackupDocumentRequest? = null,
    val analysisSummary: BackupAnalysisSummaryUi? = null,
    val analysisError: BackupAnalysisErrorUi? = null,
    val conflictSection: BackupConflictSectionUi? = null,
    val isReviewReadyConfirmed: Boolean = false,
    val isValidatingDecisions: Boolean = false,
    val previewPhase: BackupPreviewPhase = BackupPreviewPhase.IDLE,
    val previewUi: BackupPreviewUi? = null,
    val previewErrorMessages: List<String> = emptyList(),
    val restorePhase: BackupRestorePhase = BackupRestorePhase.IDLE,
    val restoreConfirmationUi: BackupRestoreConfirmationUi? = null,
    val restoreResultUi: BackupRestoreResultUi? = null,
    val canTriggerRestore: Boolean = false,
) {
    val isPreparing: Boolean
        get() = exportPhase == BackupExportPhase.PREPARING

    val isWriting: Boolean
        get() = exportPhase == BackupExportPhase.WRITING

    val isExportBusy: Boolean
        get() = exportPhase == BackupExportPhase.PREPARING ||
            exportPhase == BackupExportPhase.AWAITING_DESTINATION ||
            exportPhase == BackupExportPhase.WRITING ||
            pendingDocumentRequest != null

    val isAnalysisInProgress: Boolean
        get() = analysisPhase == BackupAnalysisPhase.READING ||
            analysisPhase == BackupAnalysisPhase.VALIDATING ||
            analysisPhase == BackupAnalysisPhase.COMPARING

    val isPreparingPreview: Boolean
        get() = previewPhase == BackupPreviewPhase.PREPARING

    val isRestoring: Boolean
        get() = restorePhase == BackupRestorePhase.EXECUTING

    val isAnalysisBusy: Boolean
        get() = analysisPhase == BackupAnalysisPhase.SELECTING_FILE ||
            isAnalysisInProgress ||
            pendingOpenDocumentRequest != null ||
            isValidatingDecisions ||
            isPreparingPreview

    val isBusy: Boolean
        get() = isExportBusy || isAnalysisBusy || isRestoring
}

class ImportExportViewModel(
    private val generateBackup: suspend () -> JsonBackupResult,
    private val validateBackupBytes: (ByteArray) -> JsonBackupReadResult = { bytes ->
        JsonBackupReader.readAndValidate(bytes)
    },
    private val compareWithDatabase: (suspend (JsonBackupReadResult) -> BackupComparisonOutcome)? = null,
    private val validateMergeDecisions: (
        suspend (
            plan: JsonBackupMergePlanResult,
            validatedBackup: ValidatedBackupData,
            decisionsByConflictKey: Map<String, MergeConflictResolution>,
        ) -> MergeDecisionValidationResult
    )? = null,
    private val buildMergePreview: (
        suspend (
            validatedBackup: ValidatedBackupData,
            plan: JsonBackupMergePlanResult,
            decisionsByConflictKey: Map<String, MergeConflictResolution>,
            decisionValidation: MergeDecisionValidationResult,
        ) -> JsonBackupMergePreviewResult
    )? = null,
    private val executeMergeRestore: (
        suspend (
            readResult: JsonBackupReadResult,
            plan: JsonBackupMergePlanResult,
            decisionsByConflictKey: Map<String, MergeConflictResolution>,
            decisionValidation: MergeDecisionValidationResult,
            previewResult: JsonBackupMergePreviewResult,
            executionContract: PreviewExecutionContract,
            userConfirmed: Boolean,
        ) -> MergeExecutionResult
    )? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = _uiState.asStateFlow()

    private val isOperationLock = AtomicBoolean(false)

    @Volatile
    private var activePendingBackup: JsonBackupResult? = null

    @Volatile
    private var activeAnalysisSession: BackupAnalysisSession? = null

    @Volatile
    private var activePreviewResult: JsonBackupMergePreviewResult? = null

    @Volatile
    private var lastRestoreExecutionResult: MergeExecutionResult? = null

    fun requestExportBackupJson() {
        val current = _uiState.value
        if (current.isBusy ||
            current.restorePhase == BackupRestorePhase.CONFIRMING ||
            !isOperationLock.compareAndSet(false, true)
        ) {
            return
        }

        activePendingBackup = null
        _uiState.value = current.copy(
            exportPhase = BackupExportPhase.PREPARING,
            pendingDocumentRequest = null,
            lastSavedFileName = null,
            lastExportedRecordsCount = null,
        )

        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    generateBackup()
                }
                if (result.utf8Bytes.isEmpty() || result.suggestedFileName.isBlank()) {
                    throw IllegalStateException("Backup JSON inválido.")
                }
                activePendingBackup = result
                _uiState.value = _uiState.value.copy(
                    exportPhase = BackupExportPhase.AWAITING_DESTINATION,
                    pendingDocumentRequest = PendingJsonBackupDocumentRequest(
                        requestToken = UUID.randomUUID().toString(),
                        suggestedFileName = result.suggestedFileName,
                    ),
                    lastSavedFileName = null,
                    lastExportedRecordsCount = null,
                )
            } catch (ce: CancellationException) {
                activePendingBackup = null
                throw ce
            } catch (_: Throwable) {
                activePendingBackup = null
                _uiState.value = _uiState.value.copy(
                    exportPhase = BackupExportPhase.ERROR,
                    pendingDocumentRequest = null,
                    lastSavedFileName = null,
                    lastExportedRecordsCount = null,
                )
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    fun onDocumentDestinationSelected(
        uri: Uri?,
        writeBytes: suspend (Uri, ByteArray) -> Unit,
    ) {
        if (!isOperationLock.compareAndSet(false, true)) {
            return
        }

        val current = _uiState.value
        val pendingRequest = current.pendingDocumentRequest
        val pendingBackup = activePendingBackup

        if (pendingRequest == null || pendingBackup == null) {
            activePendingBackup = null
            _uiState.value = current.copy(
                exportPhase = if (uri != null) BackupExportPhase.ERROR else BackupExportPhase.IDLE,
                pendingDocumentRequest = null,
                lastSavedFileName = null,
                lastExportedRecordsCount = null,
            )
            isOperationLock.set(false)
            return
        }

        if (uri == null) {
            activePendingBackup = null
            _uiState.value = current.copy(
                exportPhase = BackupExportPhase.CANCELLED,
                pendingDocumentRequest = null,
                lastSavedFileName = null,
                lastExportedRecordsCount = null,
            )
            isOperationLock.set(false)
            return
        }

        val bytesToWrite = pendingBackup.utf8Bytes
        val suggestedName = pendingBackup.suggestedFileName
        val recordsCount = pendingBackup.totalRecordsCount

        _uiState.value = current.copy(
            exportPhase = BackupExportPhase.WRITING,
            pendingDocumentRequest = null,
            lastSavedFileName = null,
            lastExportedRecordsCount = null,
        )

        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    writeBytes(uri, bytesToWrite)
                }
                activePendingBackup = null
                _uiState.value = _uiState.value.copy(
                    exportPhase = BackupExportPhase.SUCCESS,
                    pendingDocumentRequest = null,
                    lastSavedFileName = suggestedName,
                    lastExportedRecordsCount = recordsCount,
                )
            } catch (ce: CancellationException) {
                activePendingBackup = null
                throw ce
            } catch (_: Throwable) {
                activePendingBackup = null
                _uiState.value = _uiState.value.copy(
                    exportPhase = BackupExportPhase.ERROR,
                    pendingDocumentRequest = null,
                    lastSavedFileName = null,
                    lastExportedRecordsCount = null,
                )
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    fun requestSelectBackupJson() {
        val current = _uiState.value
        if (current.isBusy ||
            current.restorePhase == BackupRestorePhase.CONFIRMING ||
            !isOperationLock.compareAndSet(false, true)
        ) {
            return
        }

        try {
            activeAnalysisSession = null
            activePreviewResult = null
            lastRestoreExecutionResult = null
            _uiState.value = current.copy(
                analysisPhase = BackupAnalysisPhase.SELECTING_FILE,
                pendingOpenDocumentRequest = PendingOpenBackupDocumentRequest(
                    requestToken = UUID.randomUUID().toString(),
                ),
                analysisSummary = null,
                analysisError = null,
                conflictSection = null,
                isReviewReadyConfirmed = false,
                isValidatingDecisions = false,
                previewPhase = BackupPreviewPhase.IDLE,
                previewUi = null,
                previewErrorMessages = emptyList(),
                restorePhase = BackupRestorePhase.IDLE,
                restoreConfirmationUi = null,
                restoreResultUi = null,
                canTriggerRestore = false,
            )
        } finally {
            isOperationLock.set(false)
        }
    }

    fun onImportFileSelected(
        uri: Uri?,
        readBytes: suspend (Uri) -> ByteArray,
    ) {
        if (!isOperationLock.compareAndSet(false, true)) {
            return
        }

        val current = _uiState.value
        val pendingRequest = current.pendingOpenDocumentRequest

        if (pendingRequest == null) {
            activeAnalysisSession = null
            activePreviewResult = null
            lastRestoreExecutionResult = null
            _uiState.value = current.copy(
                analysisPhase = if (uri != null) BackupAnalysisPhase.ERROR else BackupAnalysisPhase.IDLE,
                pendingOpenDocumentRequest = null,
                analysisSummary = null,
                analysisError = if (uri != null) {
                    BackupAnalysisErrorUi(type = BackupAnalysisErrorType.READ_FAILED)
                } else {
                    null
                },
                conflictSection = null,
                isReviewReadyConfirmed = false,
                isValidatingDecisions = false,
                previewPhase = BackupPreviewPhase.IDLE,
                previewUi = null,
                previewErrorMessages = emptyList(),
                restorePhase = BackupRestorePhase.IDLE,
                restoreConfirmationUi = null,
                restoreResultUi = null,
                canTriggerRestore = false,
            )
            isOperationLock.set(false)
            return
        }

        if (uri == null) {
            activeAnalysisSession = null
            activePreviewResult = null
            lastRestoreExecutionResult = null
            _uiState.value = current.copy(
                analysisPhase = BackupAnalysisPhase.CANCELLED,
                pendingOpenDocumentRequest = null,
                analysisSummary = null,
                analysisError = null,
                conflictSection = null,
                isReviewReadyConfirmed = false,
                isValidatingDecisions = false,
                previewPhase = BackupPreviewPhase.IDLE,
                previewUi = null,
                previewErrorMessages = emptyList(),
                restorePhase = BackupRestorePhase.IDLE,
                restoreConfirmationUi = null,
                restoreResultUi = null,
                canTriggerRestore = false,
            )
            isOperationLock.set(false)
            return
        }

        activeAnalysisSession = null
        activePreviewResult = null
        lastRestoreExecutionResult = null
        _uiState.value = current.copy(
            analysisPhase = BackupAnalysisPhase.READING,
            pendingOpenDocumentRequest = null,
            analysisSummary = null,
            analysisError = null,
            conflictSection = null,
            isReviewReadyConfirmed = false,
            isValidatingDecisions = false,
            previewPhase = BackupPreviewPhase.IDLE,
            previewUi = null,
            previewErrorMessages = emptyList(),
            restorePhase = BackupRestorePhase.IDLE,
            restoreConfirmationUi = null,
            restoreResultUi = null,
            canTriggerRestore = false,
        )

        viewModelScope.launch {
            try {
                val bytes = try {
                    withContext(ioDispatcher) {
                        readBytes(uri)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (tooLarge: BackupFileTooLargeException) {
                    _uiState.value = _uiState.value.copy(
                        analysisPhase = BackupAnalysisPhase.ERROR,
                        pendingOpenDocumentRequest = null,
                        analysisSummary = null,
                        analysisError = BackupAnalysisErrorUi(
                            type = BackupAnalysisErrorType.FILE_TOO_LARGE,
                            detailMessage = tooLarge.message,
                        ),
                        conflictSection = null,
                        isReviewReadyConfirmed = false,
                    )
                    return@launch
                } catch (_: Throwable) {
                    _uiState.value = _uiState.value.copy(
                        analysisPhase = BackupAnalysisPhase.ERROR,
                        pendingOpenDocumentRequest = null,
                        analysisSummary = null,
                        analysisError = BackupAnalysisErrorUi(
                            type = BackupAnalysisErrorType.READ_FAILED,
                        ),
                        conflictSection = null,
                        isReviewReadyConfirmed = false,
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    analysisPhase = BackupAnalysisPhase.VALIDATING,
                )

                val readResult = withContext(ioDispatcher) {
                    validateBackupBytes(bytes)
                }

                if (!readResult.isValid || readResult.validatedData == null) {
                    _uiState.value = _uiState.value.copy(
                        analysisPhase = BackupAnalysisPhase.ERROR,
                        pendingOpenDocumentRequest = null,
                        analysisSummary = null,
                        analysisError = mapReaderError(readResult.errors.firstOrNull()),
                        conflictSection = null,
                        isReviewReadyConfirmed = false,
                    )
                    return@launch
                }

                _uiState.value = _uiState.value.copy(
                    analysisPhase = BackupAnalysisPhase.COMPARING,
                )

                val comparisonOutcome = withContext(ioDispatcher) {
                    val comparator = compareWithDatabase
                        ?: throw IllegalStateException("Comparação com o banco local indisponível.")
                    comparator(readResult)
                }

                val validated = readResult.validatedData
                val comparison = comparisonOutcome.comparison
                val mergePlan = comparisonOutcome.mergePlan
                val initialValidation = comparisonOutcome.initialValidation
                val conflictSection = if (mergePlan != null && initialValidation != null) {
                    BackupConflictFormatter.buildConflictSectionUi(
                        plan = mergePlan,
                        validation = initialValidation,
                        unitDetailsByKey = comparisonOutcome.unitDetailsByKey,
                    )
                } else {
                    null
                }

                activeAnalysisSession = BackupAnalysisSession(
                    readResult = readResult,
                    comparisonResult = comparison,
                    localStateFingerprint = comparisonOutcome.localStateFingerprint,
                    mergePlan = mergePlan,
                    decisionsByConflictKey = emptyMap(),
                    lastValidationResult = initialValidation,
                    unitDetailsByKey = comparisonOutcome.unitDetailsByKey,
                )

                _uiState.value = _uiState.value.copy(
                    analysisPhase = BackupAnalysisPhase.ANALYSIS_READY,
                    pendingOpenDocumentRequest = null,
                    analysisSummary = BackupAnalysisSummaryUi(
                        exportedAtEpochMs = validated.exportedAtEpochMs,
                        format = validated.format,
                        backupSchemaVersion = validated.backupSchemaVersion,
                        roomSchemaVersion = validated.roomSchemaVersion,
                        peopleCount = readResult.peopleCount,
                        productsCount = readResult.productsCount,
                        machinesCount = readResult.machinesCount,
                        salesCount = readResult.salesCount,
                        saleItemsCount = readResult.saleItemsCount,
                        newRecordsCount = comparison.totalNewCount,
                        identicalRecordsCount = comparison.totalIdenticalCount,
                        conflictingRecordsCount = comparison.totalConflictingCount,
                        localOnlyRecordsCount = comparison.totalLocalOnlyCount,
                        relationalRisksCount = comparison.relationalRisks.size,
                        hasDifferencesToResolve = !comparison.isSafeForAdditiveMerge,
                    ),
                    analysisError = null,
                    conflictSection = conflictSection,
                    isReviewReadyConfirmed = false,
                    isValidatingDecisions = false,
                )
            } catch (ce: CancellationException) {
                activeAnalysisSession = null
                throw ce
            } catch (_: Throwable) {
                activeAnalysisSession = null
                _uiState.value = _uiState.value.copy(
                    analysisPhase = BackupAnalysisPhase.ERROR,
                    pendingOpenDocumentRequest = null,
                    analysisSummary = null,
                    analysisError = BackupAnalysisErrorUi(
                        type = BackupAnalysisErrorType.READ_FAILED,
                    ),
                    conflictSection = null,
                    isReviewReadyConfirmed = false,
                    isValidatingDecisions = false,
                )
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    fun onSelectConflictResolution(
        unitKey: String,
        resolution: MergeConflictResolution,
    ) {
        val current = _uiState.value
        val session = activeAnalysisSession ?: return
        val plan = session.mergePlan ?: return
        val validatedBackup = session.readResult.validatedData ?: return

        if (current.isBusy || !isOperationLock.compareAndSet(false, true)) {
            return
        }

        val unitConflicts = plan.conflictItems.filter {
            BackupConflictFormatter.unitKeyFor(it) == unitKey
        }
        if (unitConflicts.isEmpty() || unitConflicts.any { resolution !in it.allowedResolutions }) {
            isOperationLock.set(false)
            return
        }

        val updatedDecisions = LinkedHashMap(session.decisionsByConflictKey)
        unitConflicts.forEach { item ->
            updatedDecisions[BackupConflictFormatter.conflictKeyFor(item)] = resolution
        }

        // Qualquer alteração de decisão invalida imediatamente prévia anterior
        activePreviewResult = null
        revalidateAndApply(
            session = session,
            plan = plan,
            validatedBackup = validatedBackup,
            updatedDecisions = updatedDecisions,
        )
    }

    fun onPrepareReviewRestore() {
        val current = _uiState.value
        val session = activeAnalysisSession ?: return
        val plan = session.mergePlan ?: return
        val validatedBackup = session.readResult.validatedData ?: return
        val validator = validateMergeDecisions ?: return

        if (!session.readResult.isValid ||
            session.lastValidationResult?.isReadyForNextPhase != true ||
            current.isBusy ||
            current.restorePhase == BackupRestorePhase.CONFIRMING ||
            !isOperationLock.compareAndSet(false, true)
        ) {
            return
        }

        activePreviewResult = null
        _uiState.value = current.copy(
            previewPhase = BackupPreviewPhase.PREPARING,
            previewUi = null,
            previewErrorMessages = emptyList(),
            isReviewReadyConfirmed = false,
            restorePhase = BackupRestorePhase.IDLE,
            restoreConfirmationUi = null,
            restoreResultUi = null,
            canTriggerRestore = false,
        )

        viewModelScope.launch {
            try {
                val validation = withContext(ioDispatcher) {
                    validator(plan, validatedBackup, session.decisionsByConflictKey)
                }

                if (!validation.localStateMatchesPlan) {
                    invalidateStaleAnalysisState(previewTriggered = true)
                    return@launch
                }

                val updatedSection = BackupConflictFormatter.buildConflictSectionUi(
                    plan = validation.resolvedPlan,
                    validation = validation,
                    unitDetailsByKey = session.unitDetailsByKey,
                )

                activeAnalysisSession = session.copy(
                    mergePlan = validation.resolvedPlan,
                    lastValidationResult = validation,
                )

                if (!validation.isReadyForNextPhase) {
                    val messages = buildList {
                        validation.invalidDecisionInputs.forEach { add(it.message) }
                        validation.incompatibleDecisions.forEach { add(it.message) }
                        validation.blockedOperations.forEach { add(it.message) }
                        validation.pendingDependencies.forEach { add(it.reason) }
                        if (validation.unresolvedConflictsCount > 0) {
                            add("Ainda existem ${validation.unresolvedConflictsCount} conflito(s) pendente(s).")
                        }
                    }.distinct()
                    _uiState.value = _uiState.value.copy(
                        conflictSection = updatedSection,
                        previewPhase = BackupPreviewPhase.VALIDATION_ERROR,
                        previewUi = null,
                        previewErrorMessages = messages,
                        isReviewReadyConfirmed = false,
                        canTriggerRestore = false,
                    )
                    return@launch
                }

                val previewBuilder = buildMergePreview
                if (previewBuilder == null) {
                    _uiState.value = _uiState.value.copy(
                        conflictSection = updatedSection,
                        previewPhase = BackupPreviewPhase.VALIDATION_ERROR,
                        previewUi = null,
                        previewErrorMessages = listOf("Gerador de prévia da restauração não configurado."),
                        isReviewReadyConfirmed = false,
                        canTriggerRestore = false,
                    )
                    return@launch
                }

                val previewResult = withContext(ioDispatcher) {
                    previewBuilder(
                        validatedBackup,
                        validation.resolvedPlan,
                        session.decisionsByConflictKey,
                        validation,
                    )
                }

                if (!previewResult.localStateMatchesPlan) {
                    invalidateStaleAnalysisState(previewTriggered = true)
                    return@launch
                }

                if (!previewResult.isSafeToApply) {
                    activePreviewResult = null
                    _uiState.value = _uiState.value.copy(
                        conflictSection = updatedSection,
                        previewPhase = BackupPreviewPhase.INVALID,
                        previewUi = null,
                        previewErrorMessages = previewResult.issues.map { it.message }.distinct(),
                        isReviewReadyConfirmed = false,
                        canTriggerRestore = false,
                    )
                    return@launch
                }

                val previewUi = withContext(ioDispatcher) {
                    BackupPreviewFormatter.buildPreviewUi(
                        validatedBackup = validatedBackup,
                        preview = previewResult,
                    )
                }

                val canTrigger = session.readResult.isValid &&
                    previewResult.isSafeToApply &&
                    previewResult.localStateMatchesPlan &&
                    validation.isReadyForNextPhase &&
                    validation.unresolvedConflictsCount == 0 &&
                    validation.unresolvedConflicts.isEmpty() &&
                    validation.pendingDependenciesCount == 0 &&
                    validation.pendingDependencies.isEmpty() &&
                    validation.blockedOperationsCount == 0 &&
                    validation.incompatibleDecisionsCount == 0 &&
                    validation.invalidDecisionInputs.isEmpty()

                activePreviewResult = previewResult
                _uiState.value = _uiState.value.copy(
                    conflictSection = updatedSection,
                    previewPhase = BackupPreviewPhase.AVAILABLE,
                    previewUi = previewUi,
                    previewErrorMessages = emptyList(),
                    isReviewReadyConfirmed = true,
                    restorePhase = BackupRestorePhase.IDLE,
                    restoreConfirmationUi = null,
                    restoreResultUi = null,
                    canTriggerRestore = canTrigger,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                activePreviewResult = null
                _uiState.value = _uiState.value.copy(
                    previewPhase = BackupPreviewPhase.VALIDATION_ERROR,
                    previewUi = null,
                    previewErrorMessages = listOfNotNull(
                        t.message?.takeIf { it.isNotBlank() }
                            ?: "Erro ao validar e montar a prévia da restauração.",
                    ),
                    isReviewReadyConfirmed = false,
                    canTriggerRestore = false,
                )
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    fun onBackToConflicts() {
        val current = _uiState.value
        val session = activeAnalysisSession
        if (session == null ||
            current.isBusy ||
            current.restorePhase == BackupRestorePhase.CONFIRMING ||
            !isOperationLock.compareAndSet(false, true)
        ) {
            return
        }

        val plan = session.mergePlan
        val validatedBackup = session.readResult.validatedData
        val validator = validateMergeDecisions

        if (plan == null || validatedBackup == null || validator == null) {
            activePreviewResult = null
            _uiState.value = current.copy(
                previewPhase = BackupPreviewPhase.IDLE,
                previewUi = null,
                previewErrorMessages = emptyList(),
                isReviewReadyConfirmed = false,
                restorePhase = BackupRestorePhase.IDLE,
                restoreConfirmationUi = null,
                canTriggerRestore = false,
            )
            isOperationLock.set(false)
            return
        }

        viewModelScope.launch {
            try {
                val validation = withContext(ioDispatcher) {
                    validator(plan, validatedBackup, session.decisionsByConflictKey)
                }
                if (!validation.localStateMatchesPlan) {
                    invalidateStaleAnalysisState(previewTriggered = true)
                    return@launch
                }

                val updatedSection = BackupConflictFormatter.buildConflictSectionUi(
                    plan = validation.resolvedPlan,
                    validation = validation,
                    unitDetailsByKey = session.unitDetailsByKey,
                )
                activeAnalysisSession = session.copy(
                    mergePlan = validation.resolvedPlan,
                    lastValidationResult = validation,
                )
                activePreviewResult = null
                _uiState.value = _uiState.value.copy(
                    conflictSection = updatedSection,
                    previewPhase = BackupPreviewPhase.IDLE,
                    previewUi = null,
                    previewErrorMessages = emptyList(),
                    isReviewReadyConfirmed = false,
                    restorePhase = BackupRestorePhase.IDLE,
                    restoreConfirmationUi = null,
                    canTriggerRestore = false,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                invalidateStaleAnalysisState(previewTriggered = true)
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    fun requestConfirmRestore() {
        val current = _uiState.value
        val session = activeAnalysisSession ?: return
        val preview = activePreviewResult ?: return
        val previewUi = current.previewUi ?: return
        val validation = session.lastValidationResult ?: return

        if (current.isBusy ||
            current.restorePhase != BackupRestorePhase.IDLE ||
            !current.canTriggerRestore ||
            executeMergeRestore == null
        ) {
            return
        }

        if (!session.readResult.isValid ||
            current.previewPhase != BackupPreviewPhase.AVAILABLE ||
            !preview.isSafeToApply ||
            !preview.localStateMatchesPlan ||
            !validation.isReadyForNextPhase ||
            validation.unresolvedConflictsCount > 0 ||
            validation.pendingDependenciesCount > 0 ||
            validation.blockedOperationsCount > 0
        ) {
            return
        }

        _uiState.value = current.copy(
            restorePhase = BackupRestorePhase.CONFIRMING,
            restoreConfirmationUi = BackupRestoreMapper.buildConfirmationUi(previewUi),
            restoreResultUi = null,
        )
    }

    fun cancelRestoreConfirmation() {
        val current = _uiState.value
        if (current.restorePhase != BackupRestorePhase.CONFIRMING || current.isRestoring) {
            return
        }
        _uiState.value = current.copy(
            restorePhase = BackupRestorePhase.IDLE,
            restoreConfirmationUi = null,
        )
    }

    fun confirmAndExecuteRestore() {
        val current = _uiState.value
        if (current.restorePhase != BackupRestorePhase.CONFIRMING ||
            current.isBusy ||
            !isOperationLock.compareAndSet(false, true)
        ) {
            return
        }

        val session = activeAnalysisSession
        val plan = session?.mergePlan
        val validatedBackup = session?.readResult?.validatedData
        val validation = session?.lastValidationResult
        val previewResult = activePreviewResult
        val executor = executeMergeRestore

        if (session == null ||
            plan == null ||
            validatedBackup == null ||
            validation == null ||
            previewResult == null ||
            executor == null ||
            !session.readResult.isValid ||
            !validation.isReadyForNextPhase ||
            !previewResult.isSafeToApply ||
            !previewResult.localStateMatchesPlan ||
            previewResult.executionContract.backupIdentity !=
                JsonBackupMergePreviewBuilder.computeBackupIdentitySignature(validatedBackup)
        ) {
            activePreviewResult = null
            _uiState.value = current.copy(
                previewPhase = BackupPreviewPhase.IDLE,
                previewUi = null,
                isReviewReadyConfirmed = false,
                restorePhase = BackupRestorePhase.FAILED,
                restoreConfirmationUi = null,
                restoreResultUi = BackupRestoreResultUi(
                    success = false,
                    rolledBack = false,
                    addedRecordsCount = 0,
                    replacedUnitsCount = 0,
                    preservedRecordsCount = 0,
                    ignoredRecordsCount = 0,
                    finalPeopleCount = 0,
                    finalProductsCount = 0,
                    finalMachinesCount = 0,
                    finalSalesCount = 0,
                    finalSaleItemsCount = 0,
                    finalPreferencesCount = 0,
                    failureReason = MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH,
                ),
                canTriggerRestore = false,
            )
            isOperationLock.set(false)
            return
        }

        val snapshotDecisions = LinkedHashMap(session.decisionsByConflictKey)
        lastRestoreExecutionResult = null
        _uiState.value = current.copy(
            restorePhase = BackupRestorePhase.EXECUTING,
            restoreConfirmationUi = null,
            restoreResultUi = null,
            canTriggerRestore = false,
        )

        viewModelScope.launch {
            try {
                val result = withContext(ioDispatcher) {
                    executor(
                        session.readResult,
                        plan,
                        snapshotDecisions,
                        validation,
                        previewResult,
                        previewResult.executionContract,
                        true,
                    )
                }
                lastRestoreExecutionResult = result

                if (result.success) {
                    activeAnalysisSession = null
                    activePreviewResult = null
                    _uiState.value = _uiState.value.copy(
                        analysisPhase = BackupAnalysisPhase.IDLE,
                        pendingOpenDocumentRequest = null,
                        analysisSummary = null,
                        analysisError = null,
                        conflictSection = null,
                        isReviewReadyConfirmed = false,
                        isValidatingDecisions = false,
                        previewPhase = BackupPreviewPhase.IDLE,
                        previewUi = null,
                        previewErrorMessages = emptyList(),
                        restorePhase = BackupRestorePhase.SUCCESS,
                        restoreConfirmationUi = null,
                        restoreResultUi = BackupRestoreMapper.buildResultUi(result),
                        canTriggerRestore = false,
                    )
                } else if (result.failureReason == MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING) {
                    activeAnalysisSession = null
                    activePreviewResult = null
                    _uiState.value = _uiState.value.copy(
                        analysisPhase = BackupAnalysisPhase.ERROR,
                        pendingOpenDocumentRequest = null,
                        analysisSummary = null,
                        analysisError = BackupAnalysisErrorUi(
                            type = BackupAnalysisErrorType.LOCAL_STATE_CHANGED,
                        ),
                        conflictSection = null,
                        isReviewReadyConfirmed = false,
                        isValidatingDecisions = false,
                        previewPhase = BackupPreviewPhase.LOCAL_STATE_CHANGED,
                        previewUi = null,
                        previewErrorMessages = emptyList(),
                        restorePhase = BackupRestorePhase.LOCAL_STATE_CHANGED,
                        restoreConfirmationUi = null,
                        restoreResultUi = BackupRestoreMapper.buildResultUi(result),
                        canTriggerRestore = false,
                    )
                } else {
                    activePreviewResult = null
                    _uiState.value = _uiState.value.copy(
                        isReviewReadyConfirmed = false,
                        previewPhase = BackupPreviewPhase.IDLE,
                        previewUi = null,
                        previewErrorMessages = emptyList(),
                        restorePhase = BackupRestorePhase.FAILED,
                        restoreConfirmationUi = null,
                        restoreResultUi = BackupRestoreMapper.buildResultUi(result),
                        canTriggerRestore = false,
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                activePreviewResult = null
                _uiState.value = _uiState.value.copy(
                    isReviewReadyConfirmed = false,
                    previewPhase = BackupPreviewPhase.IDLE,
                    previewUi = null,
                    previewErrorMessages = emptyList(),
                    restorePhase = BackupRestorePhase.FAILED,
                    restoreConfirmationUi = null,
                    restoreResultUi = BackupRestoreResultUi(
                        success = false,
                        rolledBack = false,
                        addedRecordsCount = 0,
                        replacedUnitsCount = 0,
                        preservedRecordsCount = 0,
                        ignoredRecordsCount = 0,
                        finalPeopleCount = 0,
                        finalProductsCount = 0,
                        finalMachinesCount = 0,
                        finalSalesCount = 0,
                        finalSaleItemsCount = 0,
                        finalPreferencesCount = 0,
                        failureReason = MergeExecutionFailureReason.TRANSACTION_ABORTED_OR_FAILED,
                    ),
                    canTriggerRestore = false,
                )
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    fun verifyLocalDatabaseConsistency() {
        val current = _uiState.value
        val session = activeAnalysisSession ?: return
        val plan = session.mergePlan ?: return
        val validatedBackup = session.readResult.validatedData ?: return
        val validator = validateMergeDecisions ?: return

        if (current.isBusy || !isOperationLock.compareAndSet(false, true)) {
            return
        }

        val hadPreview = activePreviewResult != null ||
            current.previewPhase == BackupPreviewPhase.AVAILABLE

        viewModelScope.launch {
            try {
                val validation = withContext(ioDispatcher) {
                    validator(plan, validatedBackup, session.decisionsByConflictKey)
                }
                if (!validation.localStateMatchesPlan) {
                    invalidateStaleAnalysisState(previewTriggered = hadPreview)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                invalidateStaleAnalysisState(previewTriggered = hadPreview)
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    internal fun applyRawConflictDecisionsForTesting(
        rawDecisions: Map<String, MergeConflictResolution>,
    ) {
        val current = _uiState.value
        val session = activeAnalysisSession ?: return
        val plan = session.mergePlan ?: return
        val validatedBackup = session.readResult.validatedData ?: return
        if (current.isBusy || !isOperationLock.compareAndSet(false, true)) {
            return
        }
        activePreviewResult = null
        revalidateAndApply(
            session = session,
            plan = plan,
            validatedBackup = validatedBackup,
            updatedDecisions = rawDecisions,
        )
    }

    internal fun currentValidationResultForTesting(): MergeDecisionValidationResult? {
        return activeAnalysisSession?.lastValidationResult
    }

    internal fun currentExplicitDecisionsForTesting(): Map<String, MergeConflictResolution> {
        return activeAnalysisSession?.decisionsByConflictKey.orEmpty()
    }

    internal fun currentPreviewResultForTesting(): JsonBackupMergePreviewResult? {
        return activePreviewResult
    }

    internal fun currentRestoreResultForTesting(): MergeExecutionResult? {
        return lastRestoreExecutionResult
    }

    private fun revalidateAndApply(
        session: BackupAnalysisSession,
        plan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        updatedDecisions: Map<String, MergeConflictResolution>,
    ) {
        val validator = validateMergeDecisions
        if (validator == null) {
            isOperationLock.set(false)
            return
        }

        activePreviewResult = null
        _uiState.value = _uiState.value.copy(
            isValidatingDecisions = true,
            previewPhase = BackupPreviewPhase.IDLE,
            previewUi = null,
            previewErrorMessages = emptyList(),
            isReviewReadyConfirmed = false,
            restorePhase = BackupRestorePhase.IDLE,
            restoreConfirmationUi = null,
            restoreResultUi = null,
            canTriggerRestore = false,
        )

        viewModelScope.launch {
            try {
                val validation = withContext(ioDispatcher) {
                    validator(plan, validatedBackup, updatedDecisions)
                }

                if (!validation.localStateMatchesPlan) {
                    invalidateStaleAnalysisState(previewTriggered = false)
                    return@launch
                }

                val updatedSection = BackupConflictFormatter.buildConflictSectionUi(
                    plan = validation.resolvedPlan,
                    validation = validation,
                    unitDetailsByKey = session.unitDetailsByKey,
                )

                activeAnalysisSession = session.copy(
                    mergePlan = validation.resolvedPlan,
                    decisionsByConflictKey = LinkedHashMap(updatedDecisions),
                    lastValidationResult = validation,
                )

                _uiState.value = _uiState.value.copy(
                    conflictSection = updatedSection,
                    isReviewReadyConfirmed = false,
                    isValidatingDecisions = false,
                    previewPhase = BackupPreviewPhase.IDLE,
                    previewUi = null,
                    previewErrorMessages = emptyList(),
                    canTriggerRestore = false,
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isValidatingDecisions = false,
                    isReviewReadyConfirmed = false,
                    previewPhase = BackupPreviewPhase.IDLE,
                    previewUi = null,
                    canTriggerRestore = false,
                )
            } finally {
                isOperationLock.set(false)
            }
        }
    }

    private fun invalidateStaleAnalysisState(
        previewTriggered: Boolean = false,
    ) {
        val current = _uiState.value
        val wasConfirming = current.restorePhase == BackupRestorePhase.CONFIRMING
        activeAnalysisSession = null
        activePreviewResult = null
        _uiState.value = current.copy(
            analysisPhase = BackupAnalysisPhase.ERROR,
            pendingOpenDocumentRequest = null,
            analysisSummary = null,
            analysisError = BackupAnalysisErrorUi(
                type = BackupAnalysisErrorType.LOCAL_STATE_CHANGED,
            ),
            conflictSection = null,
            isReviewReadyConfirmed = false,
            isValidatingDecisions = false,
            previewPhase = if (previewTriggered || wasConfirming) {
                BackupPreviewPhase.LOCAL_STATE_CHANGED
            } else {
                BackupPreviewPhase.IDLE
            },
            previewUi = null,
            previewErrorMessages = emptyList(),
            restorePhase = if (wasConfirming) {
                BackupRestorePhase.LOCAL_STATE_CHANGED
            } else {
                BackupRestorePhase.IDLE
            },
            restoreConfirmationUi = null,
            restoreResultUi = null,
            canTriggerRestore = false,
        )
    }

    private fun mapReaderError(error: JsonBackupValidationError?): BackupAnalysisErrorUi {
        val type = when (error?.code) {
            JsonBackupErrorCode.FILE_TOO_LARGE -> BackupAnalysisErrorType.FILE_TOO_LARGE
            JsonBackupErrorCode.INCOMPATIBLE_SCHEMA_VERSION,
            JsonBackupErrorCode.INCOMPATIBLE_ROOM_VERSION -> BackupAnalysisErrorType.INCOMPATIBLE_VERSION
            JsonBackupErrorCode.RELATIONAL_OR_FINANCIAL_VALIDATION_ERROR,
            JsonBackupErrorCode.COUNT_MISMATCH -> BackupAnalysisErrorType.INCONSISTENT_RECORDS
            JsonBackupErrorCode.EMPTY_INPUT,
            JsonBackupErrorCode.INVALID_ENCODING,
            JsonBackupErrorCode.SYNTAX_ERROR,
            JsonBackupErrorCode.MISSING_REQUIRED_FIELD,
            JsonBackupErrorCode.INVALID_FIELD_TYPE,
            JsonBackupErrorCode.INVALID_LONG_PRECISION_OR_OVERFLOW,
            JsonBackupErrorCode.UNSUPPORTED_FORMAT,
            JsonBackupErrorCode.INCOMPATIBLE_APPLICATION_ID,
            JsonBackupErrorCode.INVALID_TIMESTAMP,
            JsonBackupErrorCode.MISSING_COLLECTION,
            null -> BackupAnalysisErrorType.INVALID_FILE
        }
        return BackupAnalysisErrorUi(
            type = type,
            detailMessage = error?.message?.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        fun provideFactory(
            database: AppDatabase,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ImportExportViewModel(
                    generateBackup = { JsonBackupExporter.export(database) },
                    compareWithDatabase = { readResult ->
                        val validatedBackup = requireNotNull(readResult.validatedData) {
                            "A comparação exige um JsonBackupReadResult previamente validado."
                        }
                        val localSnapshot = JsonBackupExporter.captureConsistentSnapshot(database)
                        val comparison = JsonBackupComparator.compareSnapshots(
                            backup = validatedBackup,
                            local = localSnapshot,
                        )
                        val fingerprint = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(localSnapshot)
                        val mergePlan = JsonBackupMergePlanner.planFromSnapshots(
                            validatedBackup = validatedBackup,
                            localSnapshot = localSnapshot,
                            comparison = comparison,
                        )
                        val initialValidation = JsonBackupMergeDecisionValidator.validateFromSnapshot(
                            plan = mergePlan,
                            validatedBackup = validatedBackup,
                            localSnapshot = localSnapshot,
                            decisionsByConflictKey = emptyMap(),
                        )
                        val visualDetails = BackupConflictFormatter.buildUnitVisualDetails(
                            validatedBackup = validatedBackup,
                            localSnapshot = localSnapshot,
                            plan = mergePlan,
                        )
                        BackupComparisonOutcome(
                            comparison = comparison,
                            localStateFingerprint = fingerprint,
                            mergePlan = mergePlan,
                            initialValidation = initialValidation,
                            unitDetailsByKey = visualDetails,
                        )
                    },
                    validateMergeDecisions = { plan, validatedBackup, decisions ->
                        JsonBackupMergeDecisionValidator.validate(
                            plan = plan,
                            validatedBackup = validatedBackup,
                            database = database,
                            decisionsByConflictKey = decisions,
                        )
                    },
                    buildMergePreview = { validatedBackup, plan, decisions, decisionValidation ->
                        JsonBackupMergePreviewBuilder.buildPreview(
                            validatedBackup = validatedBackup,
                            plan = plan,
                            decisionsByConflictKey = decisions,
                            decisionValidation = decisionValidation,
                            database = database,
                        )
                    },
                    executeMergeRestore = { readResult, plan, decisions, decisionValidation, previewResult, executionContract, userConfirmed ->
                        JsonBackupMergeExecutor.execute(
                            readResult = readResult,
                            plan = plan,
                            decisionsByConflictKey = decisions,
                            decisionValidation = decisionValidation,
                            previewResult = previewResult,
                            executionContract = executionContract,
                            userConfirmed = userConfirmed,
                            database = database,
                        )
                    },
                ) as T
            }
        }
    }
}
