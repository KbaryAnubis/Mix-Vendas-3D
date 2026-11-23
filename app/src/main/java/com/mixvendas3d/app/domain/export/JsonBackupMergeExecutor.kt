package com.mixvendas3d.app.domain.export

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.local.entity.ProductCategoryEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import java.util.UUID
import kotlinx.coroutines.CancellationException

private class MergeTransactionAbortException(
    val reason: MergeExecutionFailureReason,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

object JsonBackupMergeExecutor {

    suspend fun execute(
        readResult: JsonBackupReadResult,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        previewResult: JsonBackupMergePreviewResult,
        executionContract: PreviewExecutionContract,
        userConfirmed: Boolean,
        database: AppDatabase,
        operationId: String = UUID.randomUUID().toString(),
    ): MergeExecutionResult {
        if (!userConfirmed) {
            return buildRejectedResult(
                operationId = operationId,
                rolledBack = false,
                reason = MergeExecutionFailureReason.USER_CONFIRMATION_MISSING,
                message = "A restauração JSON exige confirmação explícita do usuário.",
                database = database,
            )
        }
        val validatedBackup = if (readResult.isValid && readResult.validatedData != null) {
            readResult.validatedData
        } else {
            return buildRejectedResult(
                operationId = operationId,
                rolledBack = false,
                reason = MergeExecutionFailureReason.INVALID_BACKUP,
                message = "Backup JSON inválido ou não validado pela Etapa 6B-1.",
                database = database,
            )
        }
        return executeCore(
            validatedBackup = validatedBackup,
            plan = plan,
            decisionsByConflictKey = decisionsByConflictKey,
            decisionValidation = decisionValidation,
            previewResult = previewResult,
            executionContract = executionContract,
            userConfirmed = true,
            database = database,
            operationId = operationId,
            hooks = ExecutorTestHooks(),
        )
    }

    suspend fun execute(
        validatedBackup: ValidatedBackupData,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        previewResult: JsonBackupMergePreviewResult,
        executionContract: PreviewExecutionContract,
        userConfirmed: Boolean,
        database: AppDatabase,
        operationId: String = UUID.randomUUID().toString(),
    ): MergeExecutionResult {
        return executeCore(
            validatedBackup = validatedBackup,
            plan = plan,
            decisionsByConflictKey = decisionsByConflictKey,
            decisionValidation = decisionValidation,
            previewResult = previewResult,
            executionContract = executionContract,
            userConfirmed = userConfirmed,
            database = database,
            operationId = operationId,
            hooks = ExecutorTestHooks(),
        )
    }

    internal suspend fun executeWithHooksForTest(
        readResult: JsonBackupReadResult,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        previewResult: JsonBackupMergePreviewResult,
        executionContract: PreviewExecutionContract,
        userConfirmed: Boolean,
        database: AppDatabase,
        operationId: String = UUID.randomUUID().toString(),
        hooks: ExecutorTestHooks,
    ): MergeExecutionResult {
        if (!userConfirmed) {
            return buildRejectedResult(
                operationId = operationId,
                rolledBack = false,
                reason = MergeExecutionFailureReason.USER_CONFIRMATION_MISSING,
                message = "A restauração JSON exige confirmação explícita do usuário.",
                database = database,
            )
        }
        val validatedBackup = if (readResult.isValid && readResult.validatedData != null) {
            readResult.validatedData
        } else {
            return buildRejectedResult(
                operationId = operationId,
                rolledBack = false,
                reason = MergeExecutionFailureReason.INVALID_BACKUP,
                message = "Backup JSON inválido ou não validado pela Etapa 6B-1.",
                database = database,
            )
        }
        return executeCore(
            validatedBackup = validatedBackup,
            plan = plan,
            decisionsByConflictKey = decisionsByConflictKey,
            decisionValidation = decisionValidation,
            previewResult = previewResult,
            executionContract = executionContract,
            userConfirmed = true,
            database = database,
            operationId = operationId,
            hooks = hooks,
        )
    }

    private suspend fun executeCore(
        validatedBackup: ValidatedBackupData,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        previewResult: JsonBackupMergePreviewResult,
        executionContract: PreviewExecutionContract,
        userConfirmed: Boolean,
        database: AppDatabase,
        operationId: String,
        hooks: ExecutorTestHooks,
    ): MergeExecutionResult {
        // Rejeição imediata de pré-condições de autorização/contrato antes de abrir transação de escrita
        val preGateFailure = checkAuthorizationAndContracts(
            validatedBackup = validatedBackup,
            plan = plan,
            decisionsByConflictKey = decisionsByConflictKey,
            decisionValidation = decisionValidation,
            previewResult = previewResult,
            executionContract = executionContract,
            userConfirmed = userConfirmed,
        )
        if (preGateFailure != null) {
            return buildRejectedResult(
                operationId = operationId,
                rolledBack = false,
                reason = preGateFailure.first,
                message = preGateFailure.second,
                database = database,
            )
        }

        return try {
            val committedSummary = database.withTransaction {
                // A. Capturar novamente as 11 tabelas locais dentro da MESMA transação
                val currentLocalSnapshot = readAllTablesSnapshotInTransaction(database)

                // B. Calcular e verificar fingerprint e contagens esperadas dentro da transação
                val observedFingerprint = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(currentLocalSnapshot)
                val observedCounts = JsonBackupMergePreviewBuilder.countsOfSnapshot(currentLocalSnapshot)
                if (observedFingerprint != executionContract.expectedLocalStateFingerprint ||
                    observedCounts != executionContract.expectedLocalCounts ||
                    observedFingerprint != plan.preconditions.expectedLocalStateFingerprint ||
                    observedCounts != plan.preconditions.expectedLocalCounts ||
                    observedFingerprint != decisionValidation.expectedLocalStateFingerprint
                ) {
                    throw MergeTransactionAbortException(
                        reason = MergeExecutionFailureReason.LOCAL_STATE_CHANGED_SINCE_PLANNING,
                        message = "O banco local Room mudou após o planejamento/prévia; gere nova comparação antes de restaurar.",
                    )
                }

                // C. Conferir BackupIdentitySignature, decisões aprovadas, quantidade de operações e contrato
                val inTxFailure = checkAuthorizationAndContracts(
                    validatedBackup = validatedBackup,
                    plan = plan,
                    decisionsByConflictKey = decisionsByConflictKey,
                    decisionValidation = decisionValidation,
                    previewResult = previewResult,
                    executionContract = executionContract,
                    userConfirmed = userConfirmed,
                )
                if (inTxFailure != null) {
                    throw MergeTransactionAbortException(inTxFailure.first, inTxFailure.second)
                }

                // D. Reconstruir e conferir o resultado final esperado usando o snapshot observado na própria transação
                val recomputedPreview = JsonBackupMergePreviewBuilder.buildPreviewFromSnapshot(
                    validatedBackup = validatedBackup,
                    plan = plan,
                    decisionsByConflictKey = decisionsByConflictKey,
                    decisionValidation = decisionValidation,
                    localSnapshot = currentLocalSnapshot,
                )
                if (!recomputedPreview.isSafeToApply || recomputedPreview.issues.isNotEmpty()) {
                    val detail = recomputedPreview.issues.firstOrNull()?.message
                        ?: "A reconstrução da prévia dentro da transação indicou bloqueio de segurança."
                    throw MergeTransactionAbortException(
                        reason = MergeExecutionFailureReason.FINAL_PRE_WRITE_VALIDATION_FAILED,
                        message = detail,
                    )
                }
                if (recomputedPreview.mergedCollections != previewResult.mergedCollections ||
                    recomputedPreview.finalTableCounts != executionContract.expectedFinalCounts ||
                    recomputedPreview.changedEntries != previewResult.changedEntries ||
                    recomputedPreview.executionContract != executionContract
                ) {
                    throw MergeTransactionAbortException(
                        reason = MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH,
                        message = "O estado final esperado na transação diverge do contrato de prévia autorizado.",
                    )
                }

                // E. Validar integralmente os dados finais antes de gravar qualquer registro
                val expectedEntitySnapshot = JsonBackupMergePreviewBuilder.toEntitySnapshot(
                    recomputedPreview.mergedCollections,
                )
                try {
                    JsonBackupExporter.validateSnapshot(expectedEntitySnapshot)
                } catch (ex: JsonBackupValidationException) {
                    throw MergeTransactionAbortException(
                        reason = MergeExecutionFailureReason.FINAL_PRE_WRITE_VALIDATION_FAILED,
                        message = ex.message ?: "Falha na validação integral pré-gravação.",
                        cause = ex,
                    )
                }

                // F. Executar SOMENTE as gravações autorizadas, na ordem segura de FKs e índices UNIQUE
                applyAuthorizedWrites(
                    database = database,
                    validatedBackup = validatedBackup,
                    decisionValidation = decisionValidation,
                    hooks = hooks,
                )

                hooks.beforePostWriteVerification()

                // G. Ler novamente as 11 tabelas dentro da mesma transação
                val persistedSnapshot = readAllTablesSnapshotInTransaction(database)

                // H. Validar integralmente o estado efetivamente gravado
                try {
                    JsonBackupExporter.validateSnapshot(persistedSnapshot)
                } catch (ex: JsonBackupValidationException) {
                    throw MergeTransactionAbortException(
                        reason = MergeExecutionFailureReason.POST_WRITE_VERIFICATION_MISMATCH,
                        message = ex.message ?: "Estado persistido no Room falhou na validação pós-gravação.",
                        cause = ex,
                    )
                }

                // I. Comparar o conteúdo completo das 11 coleções (não apenas contagens) com o estado final esperado
                val persistedCollections = JsonBackupMergePreviewBuilder.toPreviewMergedCollections(persistedSnapshot)
                val persistedCounts = JsonBackupMergePreviewBuilder.countsOfSnapshot(persistedSnapshot)
                if (persistedCollections != recomputedPreview.mergedCollections ||
                    persistedCounts != executionContract.expectedFinalCounts
                ) {
                    throw MergeTransactionAbortException(
                        reason = MergeExecutionFailureReason.POST_WRITE_VERIFICATION_MISMATCH,
                        message = "Conteúdo final gravado nas 11 tabelas diverge do estado final esperado; transação revertida.",
                    )
                }

                MergeExecutionResult(
                    operationId = operationId,
                    success = true,
                    rolledBack = false,
                    addedRecordsCount = recomputedPreview.newRecordsCount,
                    replacedUnitsCount = recomputedPreview.unitsToReplaceCount,
                    preservedRecordsCount = recomputedPreview.keptRecordsCount,
                    ignoredRecordsCount = recomputedPreview.ignoredRecordsCount,
                    finalTableCounts = persistedCounts,
                    failureReason = null,
                    failureMessage = null,
                )
            }
            committedSummary
        } catch (ce: CancellationException) {
            throw ce
        } catch (abort: MergeTransactionAbortException) {
            buildRejectedResult(
                operationId = operationId,
                rolledBack = true,
                reason = abort.reason,
                message = abort.message,
                database = database,
            )
        } catch (constraintEx: SQLiteConstraintException) {
            buildRejectedResult(
                operationId = operationId,
                rolledBack = true,
                reason = MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION,
                message = constraintEx.message ?: "Violação de restrição do SQLite (FK ou índice UNIQUE); rollback executado.",
                database = database,
            )
        } catch (validationEx: JsonBackupValidationException) {
            buildRejectedResult(
                operationId = operationId,
                rolledBack = true,
                reason = MergeExecutionFailureReason.FINAL_PRE_WRITE_VALIDATION_FAILED,
                message = validationEx.message ?: "Falha de validação durante a restauração; rollback executado.",
                database = database,
            )
        } catch (t: Throwable) {
            buildRejectedResult(
                operationId = operationId,
                rolledBack = true,
                reason = MergeExecutionFailureReason.TRANSACTION_ABORTED_OR_FAILED,
                message = t.message ?: "Falha inesperada na transação de restauração; rollback executado.",
                database = database,
            )
        }
    }

    private fun checkAuthorizationAndContracts(
        validatedBackup: ValidatedBackupData,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        previewResult: JsonBackupMergePreviewResult,
        executionContract: PreviewExecutionContract,
        userConfirmed: Boolean,
    ): Pair<MergeExecutionFailureReason, String>? {
        if (!userConfirmed) {
            return MergeExecutionFailureReason.USER_CONFIRMATION_MISSING to
                "Confirmação explícita do usuário não fornecida."
        }
        if (plan.backupFormat != validatedBackup.format ||
            plan.backupSchemaVersion != validatedBackup.backupSchemaVersion ||
            plan.applicationId != validatedBackup.applicationId ||
            plan.roomSchemaVersion != validatedBackup.roomSchemaVersion ||
            plan.exportedAtEpochMs != validatedBackup.exportedAtEpochMs
        ) {
            return MergeExecutionFailureReason.PLAN_BACKUP_MISMATCH to
                "O plano de mesclagem não corresponde aos metadados do backup validado."
        }
        if (decisionValidation.backupFormat != validatedBackup.format ||
            decisionValidation.backupSchemaVersion != validatedBackup.backupSchemaVersion ||
            decisionValidation.applicationId != validatedBackup.applicationId ||
            decisionValidation.roomSchemaVersion != validatedBackup.roomSchemaVersion ||
            decisionValidation.exportedAtEpochMs != validatedBackup.exportedAtEpochMs
        ) {
            return MergeExecutionFailureReason.VALIDATION_BACKUP_MISMATCH to
                "A validação de decisões não corresponde aos metadados do backup validado."
        }

        val expectedBackupSignature = JsonBackupMergePreviewBuilder.computeBackupIdentitySignature(validatedBackup)
        if (executionContract.backupIdentity != expectedBackupSignature ||
            executionContract != previewResult.executionContract
        ) {
            return MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH to
                "O contrato de execução não corresponde à identidade do backup ou à prévia gerada."
        }
        if (!executionContract.requiresSingleTransaction ||
            !executionContract.requiresFinalValidationBeforeCommit ||
            !executionContract.requiresRollbackOnAnyFailure ||
            !plan.preconditions.requiresSingleTransaction ||
            !plan.preconditions.requiresFinalValidationBeforeCommit ||
            !plan.preconditions.requiresRollbackOnAnyFailure
        ) {
            return MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH to
                "Contrato de execução incompleto: exigências transacionais de segurança ausentes."
        }

        if (decisionValidation.unresolvedConflictsCount > 0 ||
            decisionValidation.unresolvedConflicts.isNotEmpty() ||
            decisionValidation.resolvedPlan.preconditions.unresolvedConflictIds.isNotEmpty()
        ) {
            return MergeExecutionFailureReason.UNRESOLVED_CONFLICTS to
                "Existem conflitos sem resolução explícita."
        }
        if (decisionValidation.pendingDependenciesCount > 0 || decisionValidation.pendingDependencies.isNotEmpty()) {
            return MergeExecutionFailureReason.PENDING_DEPENDENCIES to
                "Existem dependências pendentes que impedem a restauração."
        }
        if (decisionValidation.blockedOperationsCount > 0 || decisionValidation.blockedOperations.isNotEmpty()) {
            return MergeExecutionFailureReason.BLOCKED_OPERATIONS to
                "Existem operações bloqueadas por conflito de identidade ou inconsistência."
        }
        if (!decisionValidation.isReadyForNextPhase) {
            return MergeExecutionFailureReason.DECISION_VALIDATION_NOT_READY to
                "A validação de decisões não está pronta para execução (isReadyForNextPhase = false)."
        }

        val planConflictsByKey = plan.conflictItems.associateBy { "${it.collection.key}:${it.recordId}" }
        decisionsByConflictKey.keys.forEach { key ->
            if (!planConflictsByKey.containsKey(key)) {
                return MergeExecutionFailureReason.DECISIONS_MISMATCH to
                    "Decisão informada para chave de conflito desconhecida: '$key'."
            }
        }

        val validatedResolutionsByKey = decisionValidation.resolvedPlan.conflictItems.associate {
            "${it.collection.key}:${it.recordId}" to it.selectedResolution
        }
        plan.conflictItems.forEach { item ->
            val key = "${item.collection.key}:${item.recordId}"
            val effectiveDecision = decisionsByConflictKey[key] ?: item.selectedResolution
            if (effectiveDecision == null) {
                return MergeExecutionFailureReason.UNRESOLVED_CONFLICTS to
                    "Conflito '$key' sem decisão explícita; ausência de decisão não assume padrão."
            }
            if (validatedResolutionsByKey[key] != effectiveDecision ||
                executionContract.approvedDecisionsByConflictKey[key] != effectiveDecision
            ) {
                return MergeExecutionFailureReason.DECISIONS_MISMATCH to
                    "Decisão divergente entre entrada, validação e contrato aprovado para '$key'."
            }
        }
        if (executionContract.approvedDecisionsByConflictKey.size != plan.conflictItems.size) {
            return MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH to
                "Quantidade de decisões aprovadas no contrato diverge dos conflitos do plano."
        }

        if (!previewResult.isSafeToApply || previewResult.issues.isNotEmpty()) {
            val previewMsg = previewResult.issues.firstOrNull()?.message
                ?: "A prévia final não está marcada como segura para aplicação (isSafeToApply = false)."
            return MergeExecutionFailureReason.PREVIEW_NOT_SAFE to previewMsg
        }
        if (previewResult.changedEntries.size != executionContract.authorizedOperationsCount) {
            return MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH to
                "Quantidade de operações alteradas diverge de authorizedOperationsCount no contrato."
        }

        return null
    }

    private suspend fun readAllTablesSnapshotInTransaction(database: AppDatabase): JsonBackupSnapshot {
        val preferenceDao = database.preferenceDao()
        val personDao = database.personDao()
        val productDao = database.productDao()
        val machineDao = database.machineDao()
        val saleDao = database.saleDao()

        return JsonBackupSnapshot(
            preferences = preferenceDao.getAllPreferences().toList(),
            people = personDao.getAllPeople().toList(),
            products = productDao.getAllProducts().toList(),
            productParticipants = productDao.getAllProductParticipants().toList(),
            machines = machineDao.getAllMachines().toList(),
            machineBrandRates = machineDao.getAllBrandRates().toList(),
            machineInstallmentRates = machineDao.getAllInstallmentRates().toList(),
            sales = saleDao.getAllSales().toList(),
            saleItems = saleDao.getAllSaleItems().toList(),
            saleItemParticipants = saleDao.getAllSaleItemParticipants().toList(),
            salePersonTotals = saleDao.getAllSalePersonTotals().toList(),
            productCategories = productDao.getAllCategories().toList(),
        )
    }

    private suspend fun applyAuthorizedWrites(
        database: AppDatabase,
        validatedBackup: ValidatedBackupData,
        decisionValidation: MergeDecisionValidationResult,
        hooks: ExecutorTestHooks,
    ) {
        val preferenceDao = database.preferenceDao()
        val personDao = database.personDao()
        val productDao = database.productDao()
        val machineDao = database.machineDao()
        val saleDao = database.saleDao()

        val backupPrefsByKey = validatedBackup.preferences.associateBy { it.key }
        val backupPeopleById = validatedBackup.people.associateBy { it.id }
        val backupCategoriesById = validatedBackup.productCategories.associateBy { it.id }

        val backupProdsById = validatedBackup.products.associateBy { it.id }
        val backupPartsByProd = validatedBackup.productParticipants.groupBy { it.productId }

        val backupMachsById = validatedBackup.machines.associateBy { it.id }
        val backupBrandsByMach = validatedBackup.machineBrandRates.groupBy { it.machineId }
        val backupInstsByMach = validatedBackup.machineInstallmentRates.groupBy { it.machineId }

        val backupSalesById = validatedBackup.sales.associateBy { it.id }
        val backupItemsBySale = validatedBackup.saleItems.groupBy { it.saleId }
        val backupSipsByItem = validatedBackup.saleItemParticipants.groupBy { it.saleItemId }
        val backupTotalsBySale = validatedBackup.salePersonTotals.groupBy { it.saleId }

        var writeOpsExecuted = 0
        var firstHookFired = false
        var multiInsertCount = 0

        suspend fun onInsertCompleted() {
            writeOpsExecuted += 1
            multiInsertCount += 1
            if (!firstHookFired) {
                firstHookFired = true
                hooks.afterFirstWrite()
            }
            if (multiInsertCount == 2) {
                hooks.afterMultipleInserts()
            }
        }

        suspend fun onUpdateCompleted() {
            writeOpsExecuted += 1
            if (!firstHookFired) {
                firstHookFired = true
                hooks.afterFirstWrite()
            }
        }

        // 1) PREFERENCES (independente, sem FKs)
        decisionValidation.eligibleStandaloneRecords
            .filter { it.collection == BackupCollectionType.PREFERENCES }
            .forEach { rec ->
                when (rec.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val dto = backupPrefsByKey.getValue(rec.recordId)
                        preferenceDao.insert(dto.toEntity())
                        onInsertCompleted()
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val dto = backupPrefsByKey.getValue(rec.recordId)
                        val updated = preferenceDao.update(dto.toEntity())
                        if (updated != 1) {
                            throw MergeTransactionAbortException(
                                reason = MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH,
                                message = "Falha ao atualizar preferência '${rec.recordId}': registro local ausente.",
                            )
                        }
                        onUpdateCompleted()
                    }
                    EligibleOperationDisposition.KEEP_EXISTING,
                    EligibleOperationDisposition.SKIP_BACKUP -> Unit
                }
            }

        // 2) PEOPLE (referenciada por products.fee_owner_person_id e product_participants.person_id com RESTRICT)
        decisionValidation.eligibleStandaloneRecords
            .filter { it.collection == BackupCollectionType.PEOPLE }
            .forEach { rec ->
                when (rec.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val dto = backupPeopleById.getValue(rec.recordId)
                        personDao.insert(dto.toEntity())
                        onInsertCompleted()
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val dto = backupPeopleById.getValue(rec.recordId)
                        val updated = personDao.update(dto.toEntity())
                        if (updated != 1) {
                            throw MergeTransactionAbortException(
                                reason = MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH,
                                message = "Falha ao atualizar pessoa '${rec.recordId}': registro local ausente.",
                            )
                        }
                        onUpdateCompleted()
                    }
                    EligibleOperationDisposition.KEEP_EXISTING,
                    EligibleOperationDisposition.SKIP_BACKUP -> Unit
                }
            }

        // 2.5) PRODUCT_CATEGORIES (categorias principais antes de subcategorias; respeita decisões e unicidade)
        val orderedCategoryRecords = decisionValidation.eligibleStandaloneRecords
            .filter { it.collection == BackupCollectionType.PRODUCT_CATEGORIES }
            .sortedWith(
                compareBy<EligibleStandaloneRecord>(
                    { backupCategoriesById[it.recordId]?.parentId != null },
                    { backupCategoriesById[it.recordId]?.parentId.orEmpty() },
                    { it.recordId },
                ),
            )
        orderedCategoryRecords.forEach { rec ->
            when (rec.disposition) {
                EligibleOperationDisposition.ADD_NEW -> {
                    val dto = backupCategoriesById.getValue(rec.recordId)
                    val inserted = productDao.insertCategoryIfUnique(dto.toEntity())
                    if (!inserted) {
                        throw MergeTransactionAbortException(
                            reason = MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION,
                            message = "Falha ao inserir categoria '${rec.recordId}': colisão de normalizedName ou restrição de integridade.",
                        )
                    }
                    onInsertCompleted()
                }
                EligibleOperationDisposition.APPLY_BACKUP -> {
                    val dto = backupCategoriesById.getValue(rec.recordId)
                    val updated = productDao.updateCategoryIfUnique(dto.toEntity())
                    if (!updated) {
                        throw MergeTransactionAbortException(
                            reason = MergeExecutionFailureReason.DATABASE_CONSTRAINT_VIOLATION,
                            message = "Falha ao atualizar categoria '${rec.recordId}': registro local ausente ou colisão de normalizedName.",
                        )
                    }
                    onUpdateCompleted()
                }
                EligibleOperationDisposition.KEEP_EXISTING,
                EligibleOperationDisposition.SKIP_BACKUP -> Unit
            }
        }

        // 3) PRODUCTS + PRODUCT_PARTICIPANTS (unidade atômica)
        decisionValidation.eligibleUnits
            .filter { it.unitType == MergeUnitType.PRODUCT }
            .forEach { unit ->
                val prodId = unit.unitId
                when (unit.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val product = backupProdsById.getValue(prodId).toEntity()
                        val participants = backupPartsByProd[prodId].orEmpty()
                            .sortedBy { it.sortOrder }
                            .map { it.toEntity() }
                        productDao.insertProduct(product)
                        if (participants.isNotEmpty()) {
                            productDao.insertParticipants(participants)
                        }
                        onInsertCompleted()
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val product = backupProdsById.getValue(prodId).toEntity()
                        val participants = backupPartsByProd[prodId].orEmpty()
                            .sortedBy { it.sortOrder }
                            .map { it.toEntity() }
                        // Ordem segura: remover somente participantes antigos deste produto -> atualizar pai -> inserir filhos do backup
                        productDao.deleteParticipantsByProductId(prodId)
                        hooks.duringProductReplacement(prodId)
                        productDao.updateProduct(product)
                        if (participants.isNotEmpty()) {
                            productDao.insertParticipants(participants)
                        }
                        onUpdateCompleted()
                    }
                    EligibleOperationDisposition.KEEP_EXISTING,
                    EligibleOperationDisposition.SKIP_BACKUP -> Unit
                }
            }

        // 4) MACHINES + MACHINE_BRAND_RATES + MACHINE_INSTALLMENT_RATES (unidade atômica)
        decisionValidation.eligibleUnits
            .filter { it.unitType == MergeUnitType.MACHINE }
            .forEach { unit ->
                val machId = unit.unitId
                when (unit.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val machine = backupMachsById.getValue(machId).toEntity()
                        val brandRates = backupBrandsByMach[machId].orEmpty()
                            .sortedBy { it.brandKey }
                            .map { it.toEntity() }
                        val installmentRates = backupInstsByMach[machId].orEmpty()
                            .sortedBy { it.installments }
                            .map { it.toEntity() }
                        machineDao.insertMachine(machine)
                        if (brandRates.isNotEmpty()) {
                            machineDao.insertBrandRatesStrict(brandRates)
                        }
                        if (installmentRates.isNotEmpty()) {
                            machineDao.insertInstallmentRatesStrict(installmentRates)
                        }
                        onInsertCompleted()
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val machine = backupMachsById.getValue(machId).toEntity()
                        val brandRates = backupBrandsByMach[machId].orEmpty()
                            .sortedBy { it.brandKey }
                            .map { it.toEntity() }
                        val installmentRates = backupInstsByMach[machId].orEmpty()
                            .sortedBy { it.installments }
                            .map { it.toEntity() }
                        // Ordem segura: remover somente taxas antigas desta maquininha -> atualizar pai -> inserir novas taxas
                        machineDao.deleteBrandRatesForMachine(machId)
                        machineDao.deleteInstallmentRatesForMachine(machId)
                        hooks.duringMachineReplacement(machId)
                        machineDao.updateMachine(machine)
                        if (brandRates.isNotEmpty()) {
                            machineDao.insertBrandRatesStrict(brandRates)
                        }
                        if (installmentRates.isNotEmpty()) {
                            machineDao.insertInstallmentRatesStrict(installmentRates)
                        }
                        onUpdateCompleted()
                    }
                    EligibleOperationDisposition.KEEP_EXISTING,
                    EligibleOperationDisposition.SKIP_BACKUP -> Unit
                }
            }

        // 5) SALES + SALE_ITEMS + SALE_ITEM_PARTICIPANTS + SALE_PERSON_TOTALS (unidade financeira indivisível)
        decisionValidation.eligibleUnits
            .filter { it.unitType == MergeUnitType.SALE }
            .forEach { unit ->
                val saleId = unit.unitId
                when (unit.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val sale = backupSalesById.getValue(saleId).toEntity()
                        val items = backupItemsBySale[saleId].orEmpty()
                            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                            .map { it.toEntity() }
                        val itemParticipants = items.flatMap { item ->
                            backupSipsByItem[item.id].orEmpty()
                                .sortedWith(compareBy({ it.sortOrder }, { it.personId }))
                                .map { it.toEntity() }
                        }
                        val personTotals = backupTotalsBySale[saleId].orEmpty()
                            .sortedWith(compareBy({ it.sortOrder }, { it.personId }))
                            .map { it.toEntity() }

                        saleDao.insertSale(sale)
                        if (items.isNotEmpty()) {
                            saleDao.insertSaleItems(items)
                        }
                        if (itemParticipants.isNotEmpty()) {
                            saleDao.insertSaleItemParticipants(itemParticipants)
                        }
                        if (personTotals.isNotEmpty()) {
                            saleDao.insertSalePersonTotals(personTotals)
                        }
                        onInsertCompleted()
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val sale = backupSalesById.getValue(saleId).toEntity()
                        val items = backupItemsBySale[saleId].orEmpty()
                            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                            .map { it.toEntity() }
                        val itemParticipants = items.flatMap { item ->
                            backupSipsByItem[item.id].orEmpty()
                                .sortedWith(compareBy({ it.sortOrder }, { it.personId }))
                                .map { it.toEntity() }
                        }
                        val personTotals = backupTotalsBySale[saleId].orEmpty()
                            .sortedWith(compareBy({ it.sortOrder }, { it.personId }))
                            .map { it.toEntity() }

                        // Ordem bottom-up segura para filhos da venda especificamente substituída
                        saleDao.deleteItemParticipantsBySaleId(saleId)
                        saleDao.deleteItemsBySaleId(saleId)
                        saleDao.deletePersonTotalsBySaleId(saleId)
                        hooks.duringSaleReplacement(saleId)
                        val updatedRows = saleDao.updateSale(sale)
                        if (updatedRows != 1) {
                            throw MergeTransactionAbortException(
                                reason = MergeExecutionFailureReason.EXECUTION_CONTRACT_MISMATCH,
                                message = "Falha ao atualizar venda '$saleId': registro local ausente.",
                            )
                        }
                        if (items.isNotEmpty()) {
                            saleDao.insertSaleItems(items)
                        }
                        if (itemParticipants.isNotEmpty()) {
                            saleDao.insertSaleItemParticipants(itemParticipants)
                        }
                        if (personTotals.isNotEmpty()) {
                            saleDao.insertSalePersonTotals(personTotals)
                        }
                        onUpdateCompleted()
                    }
                    EligibleOperationDisposition.KEEP_EXISTING,
                    EligibleOperationDisposition.SKIP_BACKUP -> Unit
                }
            }
    }

    private suspend fun buildRejectedResult(
        operationId: String,
        rolledBack: Boolean,
        reason: MergeExecutionFailureReason,
        message: String,
        database: AppDatabase,
    ): MergeExecutionResult {
        val currentCounts = safeReadTableCounts(database)
        return MergeExecutionResult(
            operationId = operationId,
            success = false,
            rolledBack = rolledBack,
            addedRecordsCount = 0,
            replacedUnitsCount = 0,
            preservedRecordsCount = 0,
            ignoredRecordsCount = 0,
            finalTableCounts = currentCounts,
            failureReason = reason,
            failureMessage = message,
        )
    }

    private suspend fun safeReadTableCounts(database: AppDatabase): Map<BackupCollectionType, Int> {
        return try {
            val snapshot = JsonBackupExporter.captureConsistentSnapshot(database)
            JsonBackupMergePreviewBuilder.countsOfSnapshot(snapshot)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            BackupCollectionType.entries.associateWith { 0 }
        }
    }

    private fun PreferenceDto.toEntity() = PreferenceEntity(
        key = key,
        value = value,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun PersonDto.toEntity() = PersonEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        colorIndex = colorIndex,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ProductCategoryDto.toEntity() = ProductCategoryEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        parentId = parentId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ProductDto.toEntity() = ProductEntity(
        id = id,
        name = name,
        priceCents = priceCents,
        active = active,
        feeResponsibilityMode = feeResponsibilityMode,
        feeOwnerPersonId = feeOwnerPersonId,
        categoryId = categoryId,
        subcategoryId = subcategoryId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ProductParticipantDto.toEntity() = ProductParticipantEntity(
        productId = productId,
        personId = personId,
        amountCents = amountCents,
        sortOrder = sortOrder,
    )

    private fun MachineDto.toEntity() = MachineEntity(
        id = id,
        name = name,
        normalizedName = normalizedName,
        debitRateBps = debitRateBps,
        creditEnabled = creditEnabled,
        creditDefaultRateBps = creditDefaultRateBps,
        brandRatesEnabled = brandRatesEnabled,
        installmentsEnabled = installmentsEnabled,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        maxInstallments = maxInstallments,
    )

    private fun MachineBrandRateDto.toEntity() = MachineBrandRateEntity(
        machineId = machineId,
        brandKey = brandKey,
        rateBps = rateBps,
        debitRateBps = debitRateBps,
    )

    private fun MachineInstallmentRateDto.toEntity() = MachineInstallmentRateEntity(
        machineId = machineId,
        installments = installments,
        addonBps = addonBps,
    )

    private fun SaleDto.toEntity() = SaleEntity(
        id = id,
        soldAtEpochMs = soldAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
        paymentMethod = paymentMethod,
        originalSubtotalCents = originalSubtotalCents,
        discountTotalCents = discountTotalCents,
        finalAmountCents = finalAmountCents,
        feeTotalCents = feeTotalCents,
        netTotalCents = netTotalCents,
        discountMode = discountMode,
        discountTargetProductId = discountTargetProductId,
        discountTargetProductName = discountTargetProductName,
        machineId = machineId,
        machineName = machineName,
        machineDebitRateBps = machineDebitRateBps,
        machineCreditDefaultRateBps = machineCreditDefaultRateBps,
        machineBrandRatesEnabled = machineBrandRatesEnabled,
        machineInstallmentsEnabled = machineInstallmentsEnabled,
        cardBrand = cardBrand,
        installments = installments,
        automaticRateBps = automaticRateBps,
        manualRateOverrideBps = manualRateOverrideBps,
        effectiveFeeRateBps = effectiveFeeRateBps,
        note = note,
    )

    private fun SaleItemDto.toEntity() = SaleItemEntity(
        id = id,
        saleId = saleId,
        sortOrder = sortOrder,
        productId = productId,
        productName = productName,
        quantity = quantity,
        unitPriceOriginalCents = unitPriceOriginalCents,
        originalSubtotalCents = originalSubtotalCents,
        discountAmountCents = discountAmountCents,
        finalAmountCents = finalAmountCents,
        appliedFeeRateBps = appliedFeeRateBps,
        feeAmountCents = feeAmountCents,
        netAmountCents = netAmountCents,
        feeResponsibilityMode = feeResponsibilityMode,
        feeOwnerPersonId = feeOwnerPersonId,
        feeOwnerPersonName = feeOwnerPersonName,
    )

    private fun SaleItemParticipantDto.toEntity() = SaleItemParticipantEntity(
        saleItemId = saleItemId,
        personId = personId,
        sortOrder = sortOrder,
        personName = personName,
        personColorIndex = personColorIndex,
        originalUnitAmountCents = originalUnitAmountCents,
        originalSubtotalCents = originalSubtotalCents,
        discountAmountCents = discountAmountCents,
        grossAmountCents = grossAmountCents,
        feeAmountCents = feeAmountCents,
        netAmountCents = netAmountCents,
    )

    private fun SalePersonTotalDto.toEntity() = SalePersonTotalEntity(
        saleId = saleId,
        personId = personId,
        sortOrder = sortOrder,
        personName = personName,
        personColorIndex = personColorIndex,
        grossAmountCents = grossAmountCents,
        feeAmountCents = feeAmountCents,
        netAmountCents = netAmountCents,
    )
}
