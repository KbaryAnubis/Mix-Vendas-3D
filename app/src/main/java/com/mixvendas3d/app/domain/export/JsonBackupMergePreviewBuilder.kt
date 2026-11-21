package com.mixvendas3d.app.domain.export

import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.domain.calculation.FinancialOverflowException
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import java.security.MessageDigest

object JsonBackupMergePreviewBuilder {

    suspend fun buildPreview(
        readResult: JsonBackupReadResult,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        database: AppDatabase,
    ): JsonBackupMergePreviewResult {
        require(readResult.isValid && readResult.validatedData != null) {
            "A prévia de mesclagem exige um JsonBackupReadResult válido da Etapa 6B-1."
        }
        return buildPreview(
            validatedBackup = readResult.validatedData,
            plan = plan,
            decisionsByConflictKey = decisionsByConflictKey,
            decisionValidation = decisionValidation,
            database = database,
        )
    }

    suspend fun buildPreview(
        validatedBackup: ValidatedBackupData,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        database: AppDatabase,
    ): JsonBackupMergePreviewResult {
        // Captura consistente somente leitura das 11 tabelas locais dentro de transação Room
        val currentLocalSnapshot = JsonBackupExporter.captureConsistentSnapshot(database)
        return buildPreviewFromSnapshot(
            validatedBackup = validatedBackup,
            plan = plan,
            decisionsByConflictKey = decisionsByConflictKey,
            decisionValidation = decisionValidation,
            localSnapshot = currentLocalSnapshot,
        )
    }

    internal fun buildPreviewFromSnapshot(
        validatedBackup: ValidatedBackupData,
        plan: JsonBackupMergePlanResult,
        decisionsByConflictKey: Map<String, MergeConflictResolution>,
        decisionValidation: MergeDecisionValidationResult,
        localSnapshot: JsonBackupSnapshot,
    ): JsonBackupMergePreviewResult {
        val issues = ArrayList<PreviewValidationIssue>()

        // 1) Coerência de backup, plano e resultado de validação de decisões
        if (plan.backupFormat != validatedBackup.format ||
            plan.backupSchemaVersion != validatedBackup.backupSchemaVersion ||
            plan.applicationId != validatedBackup.applicationId ||
            plan.roomSchemaVersion != validatedBackup.roomSchemaVersion ||
            plan.exportedAtEpochMs != validatedBackup.exportedAtEpochMs
        ) {
            issues.add(
                PreviewValidationIssue(
                    type = PreviewIssueType.PLAN_BACKUP_MISMATCH,
                    message = "O plano de mesclagem não corresponde aos metadados do backup validado.",
                ),
            )
        }

        if (decisionValidation.backupFormat != validatedBackup.format ||
            decisionValidation.backupSchemaVersion != validatedBackup.backupSchemaVersion ||
            decisionValidation.applicationId != validatedBackup.applicationId ||
            decisionValidation.roomSchemaVersion != validatedBackup.roomSchemaVersion ||
            decisionValidation.exportedAtEpochMs != validatedBackup.exportedAtEpochMs
        ) {
            issues.add(
                PreviewValidationIssue(
                    type = PreviewIssueType.VALIDATION_BACKUP_MISMATCH,
                    message = "O resultado de validação de decisões não corresponde ao backup informado.",
                ),
            )
        }

        val planConflictsByKey = plan.conflictItems.associateBy { "${it.collection.key}:${it.recordId}" }
        decisionsByConflictKey.keys.sorted().forEach { conflictKey ->
            if (!planConflictsByKey.containsKey(conflictKey)) {
                issues.add(
                    PreviewValidationIssue(
                        type = PreviewIssueType.UNKNOWN_CONFLICT_KEY,
                        targetId = conflictKey,
                        message = "Decisão informada para chave de conflito desconhecida: '$conflictKey'.",
                    ),
                )
            }
        }

        // Verifica que todas as decisões passadas coincidem com as resoluções validadas em decisionValidation
        val validatedResolutionsByKey = decisionValidation.resolvedPlan.conflictItems.associate {
            "${it.collection.key}:${it.recordId}" to it.selectedResolution
        }
        plan.conflictItems.forEach { item ->
            val key = "${item.collection.key}:${item.recordId}"
            val expectedDecision = decisionsByConflictKey[key] ?: item.selectedResolution
            val validatedDecision = validatedResolutionsByKey[key]
            if (expectedDecision != validatedDecision) {
                issues.add(
                    PreviewValidationIssue(
                        type = PreviewIssueType.DECISIONS_MISMATCH,
                        collection = item.collection,
                        unitType = item.relatedUnitType,
                        targetId = item.recordId,
                        message = "As decisões informadas divergem das decisões validadas para '$key'.",
                    ),
                )
            }
        }

        if (!decisionValidation.isReadyForNextPhase) {
            issues.add(
                PreviewValidationIssue(
                    type = PreviewIssueType.DECISION_VALIDATION_NOT_READY,
                    message = "A validação de decisões ainda não está pronta para gerar prévia segura (conflitos, pendências ou bloqueios ativos).",
                ),
            )
            decisionValidation.unresolvedConflicts.forEach { unresolved ->
                issues.add(
                    PreviewValidationIssue(
                        type = PreviewIssueType.DECISION_VALIDATION_NOT_READY,
                        collection = unresolved.collection,
                        unitType = unresolved.relatedUnitType,
                        targetId = unresolved.recordId,
                        message = "Conflito sem resolução explícita: '${unresolved.collection.key}:${unresolved.recordId}'.",
                    ),
                )
            }
            decisionValidation.pendingDependencies.forEach { pending ->
                issues.add(
                    PreviewValidationIssue(
                        type = PreviewIssueType.REFERENTIAL_OR_DEPENDENCY_ERROR,
                        collection = pending.collection,
                        targetId = pending.recordId,
                        message = pending.reason,
                    ),
                )
            }
            decisionValidation.allIssues.forEach { issue ->
                val mappedType = when (issue.type) {
                    DecisionValidationIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING ->
                        PreviewIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING
                    DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED ->
                        PreviewIssueType.IDENTITY_NAME_COLLISION
                    DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR ->
                        PreviewIssueType.FINANCIAL_VALIDATION_ERROR
                    DecisionValidationIssueType.UNRESOLVED_DEPENDENCY ->
                        PreviewIssueType.REFERENTIAL_OR_DEPENDENCY_ERROR
                    DecisionValidationIssueType.UNKNOWN_CONFLICT_KEY ->
                        PreviewIssueType.UNKNOWN_CONFLICT_KEY
                    else -> PreviewIssueType.DECISION_VALIDATION_NOT_READY
                }
                issues.add(
                    PreviewValidationIssue(
                        type = mappedType,
                        collection = issue.collection,
                        unitType = issue.relatedUnitType,
                        targetId = issue.relatedUnitId ?: issue.recordId,
                        message = issue.message,
                    ),
                )
            }
        }

        // 2) Verificação estrita do banco Room atual vs fingerprint/contagens do planejamento
        val observedFingerprint = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(localSnapshot)
        val observedCounts = countsOfSnapshot(localSnapshot)
        val localStateMatchesPlan =
            observedFingerprint == plan.preconditions.expectedLocalStateFingerprint &&
                observedCounts == plan.preconditions.expectedLocalCounts &&
                observedFingerprint == decisionValidation.expectedLocalStateFingerprint

        if (!localStateMatchesPlan) {
            issues.add(
                PreviewValidationIssue(
                    type = PreviewIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING,
                    message = "O banco local Room foi alterado após o planejamento; gere nova comparação e plano antes de aplicar.",
                ),
            )
        }

        // 3) Revalidação defensiva atual das decisões contra o snapshot local vigente
        val freshValidation = JsonBackupMergeDecisionValidator.validateFromSnapshot(
            plan = plan,
            validatedBackup = validatedBackup,
            localSnapshot = localSnapshot,
            decisionsByConflictKey = decisionsByConflictKey,
        )
        if (!freshValidation.isReadyForNextPhase && issues.none { it.type == PreviewIssueType.DECISION_VALIDATION_NOT_READY }) {
            issues.add(
                PreviewValidationIssue(
                    type = PreviewIssueType.DECISION_VALIDATION_NOT_READY,
                    message = "Revalidação defensiva indicou pendências, incompatibilidades ou bloqueios no plano atual.",
                ),
            )
            freshValidation.allIssues.forEach { issue ->
                issues.add(
                    PreviewValidationIssue(
                        type = mapIssueType(issue.type),
                        collection = issue.collection,
                        unitType = issue.relatedUnitType,
                        targetId = issue.relatedUnitId ?: issue.recordId,
                        message = issue.message,
                    ),
                )
            }
        }

        val approvedDecisionsSorted = LinkedHashMap<String, MergeConflictResolution>().apply {
            freshValidation.resolvedConflicts.sortedWith(
                compareBy<MergeConflictItem> { it.collection.ordinal }.thenBy { it.recordId },
            ).forEach { item ->
                item.selectedResolution?.let { res ->
                    put("${item.collection.key}:${item.recordId}", res)
                }
            }
        }

        val backupSignature = computeBackupIdentitySignature(validatedBackup)

        if (issues.isNotEmpty()) {
            val emptyCollections = PreviewMergedCollections()
            val emptyCounts = emptyCollections.countsByCollection()
            return JsonBackupMergePreviewResult(
                isSafeToApply = false,
                newRecordsCount = 0,
                keptRecordsCount = 0,
                unitsToReplaceCount = 0,
                ignoredRecordsCount = 0,
                localOnlyPreservedCount = 0,
                finalTableCounts = emptyCounts,
                changedEntries = emptyList(),
                financialSummary = computeFinancialSummary(
                    localSales = localSnapshot.sales,
                    finalSales = localSnapshot.sales,
                    addedSales = emptyList(),
                    replacedSalePairs = emptyList(),
                ),
                issues = issues.distinct(),
                mergedCollections = emptyCollections,
                executionContract = PreviewExecutionContract(
                    expectedLocalStateFingerprint = plan.preconditions.expectedLocalStateFingerprint,
                    expectedLocalCounts = plan.preconditions.expectedLocalCounts,
                    expectedFinalCounts = emptyCounts,
                    backupIdentity = backupSignature,
                    approvedDecisionsByConflictKey = approvedDecisionsSorted,
                    authorizedOperationsCount = 0,
                    requiresSingleTransaction = true,
                    requiresFinalValidationBeforeCommit = true,
                    requiresRollbackOnAnyFailure = true,
                ),
                localStateMatchesPlan = localStateMatchesPlan,
            )
        }

        // 4) Construção integral e determinística do estado pós-mesclagem em memória (11 tabelas)
        return constructAndValidateFinalState(
            validatedBackup = validatedBackup,
            localSnapshot = localSnapshot,
            validation = freshValidation,
            plan = plan,
            approvedDecisionsSorted = approvedDecisionsSorted,
            backupSignature = backupSignature,
            localStateMatchesPlan = localStateMatchesPlan,
        )
    }

    private fun constructAndValidateFinalState(
        validatedBackup: ValidatedBackupData,
        localSnapshot: JsonBackupSnapshot,
        validation: MergeDecisionValidationResult,
        plan: JsonBackupMergePlanResult,
        approvedDecisionsSorted: Map<String, MergeConflictResolution>,
        backupSignature: BackupIdentitySignature,
        localStateMatchesPlan: Boolean,
    ): JsonBackupMergePreviewResult {
        val issues = ArrayList<PreviewValidationIssue>()
        val changedEntries = ArrayList<PreviewChangedEntry>()

        var newRecordsCount = 0
        var keptRecordsCount = 0
        var unitsToReplaceCount = 0
        var ignoredRecordsCount = 0
        var localOnlyPreservedCount = 0

        // --- A) PREFERENCES ---
        val localPrefsByKey = localSnapshot.preferences.associateBy { it.key }
        val backupPrefsByKey = validatedBackup.preferences.associateBy { it.key }
        val finalPreferences = ArrayList<PreferenceDto>()
        val prefActionsById = plan.planFor(BackupCollectionType.PREFERENCES).records.associateBy { it.recordId }

        validation.eligibleStandaloneRecords
            .filter { it.collection == BackupCollectionType.PREFERENCES }
            .forEach { rec ->
                val planned = prefActionsById[rec.recordId]
                when (rec.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val b = backupPrefsByKey.getValue(rec.recordId)
                        finalPreferences.add(b)
                        newRecordsCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.STANDALONE_RECORD,
                                operation = PreviewChangeOperation.ADD,
                                collection = BackupCollectionType.PREFERENCES,
                                targetId = rec.recordId,
                                memberCountsByCollection = mapOf(BackupCollectionType.PREFERENCES to 1),
                            ),
                        )
                    }
                    EligibleOperationDisposition.KEEP_EXISTING -> {
                        val l = localPrefsByKey.getValue(rec.recordId)
                        finalPreferences.add(l.toDto())
                        keptRecordsCount += 1
                        if (planned?.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY) {
                            localOnlyPreservedCount += 1
                        }
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val b = backupPrefsByKey.getValue(rec.recordId)
                        finalPreferences.add(b)
                        unitsToReplaceCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.STANDALONE_RECORD,
                                operation = PreviewChangeOperation.REPLACE,
                                collection = BackupCollectionType.PREFERENCES,
                                targetId = rec.recordId,
                                memberCountsByCollection = mapOf(BackupCollectionType.PREFERENCES to 1),
                            ),
                        )
                    }
                    EligibleOperationDisposition.SKIP_BACKUP -> {
                        val l = localPrefsByKey[rec.recordId]
                        if (l != null) {
                            finalPreferences.add(l.toDto())
                            keptRecordsCount += 1
                        }
                        if (backupPrefsByKey.containsKey(rec.recordId)) {
                            ignoredRecordsCount += 1
                        }
                    }
                }
            }

        // --- B) PEOPLE ---
        val localPeopleById = localSnapshot.people.associateBy { it.id }
        val backupPeopleById = validatedBackup.people.associateBy { it.id }
        val finalPeople = ArrayList<PersonDto>()
        val peopleActionsById = plan.planFor(BackupCollectionType.PEOPLE).records.associateBy { it.recordId }

        validation.eligibleStandaloneRecords
            .filter { it.collection == BackupCollectionType.PEOPLE }
            .forEach { rec ->
                val planned = peopleActionsById[rec.recordId]
                when (rec.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        val b = backupPeopleById.getValue(rec.recordId)
                        finalPeople.add(b)
                        newRecordsCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.STANDALONE_RECORD,
                                operation = PreviewChangeOperation.ADD,
                                collection = BackupCollectionType.PEOPLE,
                                targetId = rec.recordId,
                                memberCountsByCollection = mapOf(BackupCollectionType.PEOPLE to 1),
                            ),
                        )
                    }
                    EligibleOperationDisposition.KEEP_EXISTING -> {
                        val l = localPeopleById.getValue(rec.recordId)
                        finalPeople.add(l.toDto())
                        keptRecordsCount += 1
                        if (planned?.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY) {
                            localOnlyPreservedCount += 1
                        }
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        val b = backupPeopleById.getValue(rec.recordId)
                        finalPeople.add(b)
                        unitsToReplaceCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.STANDALONE_RECORD,
                                operation = PreviewChangeOperation.REPLACE,
                                collection = BackupCollectionType.PEOPLE,
                                targetId = rec.recordId,
                                memberCountsByCollection = mapOf(BackupCollectionType.PEOPLE to 1),
                            ),
                        )
                    }
                    EligibleOperationDisposition.SKIP_BACKUP -> {
                        // Se era mesmo ID com KEEP_LOCAL/IGNORE_IMPORT -> mantém registro local;
                        // Se era colisão de nome com ID distinto -> não existe registro local com esse ID (o local correspondente já está como LOCAL_ONLY KEEP).
                        val l = localPeopleById[rec.recordId]
                        if (l != null) {
                            finalPeople.add(l.toDto())
                            keptRecordsCount += 1
                        }
                        if (backupPeopleById.containsKey(rec.recordId)) {
                            ignoredRecordsCount += 1
                        }
                    }
                }
            }

        // --- C) PRODUCTS + PRODUCT_PARTICIPANTS (Unidade Atômica) ---
        val localProdsById = localSnapshot.products.associateBy { it.id }
        val localPartsByProd = localSnapshot.productParticipants.groupBy { it.productId }
        val backupProdsById = validatedBackup.products.associateBy { it.id }
        val backupPartsByProd = validatedBackup.productParticipants.groupBy { it.productId }
        val prodActionsById = plan.planFor(BackupCollectionType.PRODUCTS).records.associateBy { it.recordId }

        val finalProducts = ArrayList<ProductDto>()
        val finalProductParticipants = ArrayList<ProductParticipantDto>()

        validation.eligibleUnits
            .filter { it.unitType == MergeUnitType.PRODUCT }
            .forEach { unit ->
                val prodId = unit.unitId
                val planned = prodActionsById[prodId]
                val lProd = localProdsById[prodId]
                val lParts = localPartsByProd[prodId].orEmpty().sortedBy { it.personId }
                val bProd = backupProdsById[prodId]
                val bParts = backupPartsByProd[prodId].orEmpty().sortedBy { it.personId }

                when (unit.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        finalProducts.add(bProd!!)
                        finalProductParticipants.addAll(bParts)
                        newRecordsCount += 1 + bParts.size
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.ATOMIC_UNIT,
                                operation = PreviewChangeOperation.ADD,
                                unitType = MergeUnitType.PRODUCT,
                                targetId = prodId,
                                memberCountsByCollection = mapOf(
                                    BackupCollectionType.PRODUCTS to 1,
                                    BackupCollectionType.PRODUCT_PARTICIPANTS to bParts.size,
                                ),
                            ),
                        )
                    }
                    EligibleOperationDisposition.KEEP_EXISTING -> {
                        finalProducts.add(lProd!!.toDto())
                        finalProductParticipants.addAll(lParts.map { it.toDto() })
                        val count = 1 + lParts.size
                        keptRecordsCount += count
                        if (planned?.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY) {
                            localOnlyPreservedCount += count
                        }
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        // Substituição integral atômica (nunca mistura participantes locais com cabeçalho do backup)
                        finalProducts.add(bProd!!)
                        finalProductParticipants.addAll(bParts)
                        unitsToReplaceCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.ATOMIC_UNIT,
                                operation = PreviewChangeOperation.REPLACE,
                                unitType = MergeUnitType.PRODUCT,
                                targetId = prodId,
                                memberCountsByCollection = mapOf(
                                    BackupCollectionType.PRODUCTS to 1,
                                    BackupCollectionType.PRODUCT_PARTICIPANTS to bParts.size,
                                ),
                            ),
                        )
                    }
                    EligibleOperationDisposition.SKIP_BACKUP -> {
                        if (lProd != null) {
                            finalProducts.add(lProd.toDto())
                            finalProductParticipants.addAll(lParts.map { it.toDto() })
                            keptRecordsCount += 1 + lParts.size
                        }
                        if (bProd != null) {
                            ignoredRecordsCount += 1 + bParts.size
                        }
                    }
                }
            }

        // --- D) MACHINES + MACHINE_BRAND_RATES + MACHINE_INSTALLMENT_RATES (Unidade Atômica) ---
        val localMachsById = localSnapshot.machines.associateBy { it.id }
        val localBrandsByMach = localSnapshot.machineBrandRates.groupBy { it.machineId }
        val localInstsByMach = localSnapshot.machineInstallmentRates.groupBy { it.machineId }
        val backupMachsById = validatedBackup.machines.associateBy { it.id }
        val backupBrandsByMach = validatedBackup.machineBrandRates.groupBy { it.machineId }
        val backupInstsByMach = validatedBackup.machineInstallmentRates.groupBy { it.machineId }
        val machActionsById = plan.planFor(BackupCollectionType.MACHINES).records.associateBy { it.recordId }

        val finalMachines = ArrayList<MachineDto>()
        val finalBrandRates = ArrayList<MachineBrandRateDto>()
        val finalInstallmentRates = ArrayList<MachineInstallmentRateDto>()

        validation.eligibleUnits
            .filter { it.unitType == MergeUnitType.MACHINE }
            .forEach { unit ->
                val machId = unit.unitId
                val planned = machActionsById[machId]
                val lMach = localMachsById[machId]
                val lBrands = localBrandsByMach[machId].orEmpty().sortedBy { it.brandKey }
                val lInsts = localInstsByMach[machId].orEmpty().sortedBy { it.installments }
                val bMach = backupMachsById[machId]
                val bBrands = backupBrandsByMach[machId].orEmpty().sortedBy { it.brandKey }
                val bInsts = backupInstsByMach[machId].orEmpty().sortedBy { it.installments }

                when (unit.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        finalMachines.add(bMach!!)
                        finalBrandRates.addAll(bBrands)
                        finalInstallmentRates.addAll(bInsts)
                        newRecordsCount += 1 + bBrands.size + bInsts.size
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.ATOMIC_UNIT,
                                operation = PreviewChangeOperation.ADD,
                                unitType = MergeUnitType.MACHINE,
                                targetId = machId,
                                memberCountsByCollection = mapOf(
                                    BackupCollectionType.MACHINES to 1,
                                    BackupCollectionType.MACHINE_BRAND_RATES to bBrands.size,
                                    BackupCollectionType.MACHINE_INSTALLMENT_RATES to bInsts.size,
                                ),
                            ),
                        )
                    }
                    EligibleOperationDisposition.KEEP_EXISTING -> {
                        finalMachines.add(lMach!!.toDto())
                        finalBrandRates.addAll(lBrands.map { it.toDto() })
                        finalInstallmentRates.addAll(lInsts.map { it.toDto() })
                        val count = 1 + lBrands.size + lInsts.size
                        keptRecordsCount += count
                        if (planned?.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY) {
                            localOnlyPreservedCount += count
                        }
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        // Substituição integral atômica da maquininha + todas as suas tarifas
                        finalMachines.add(bMach!!)
                        finalBrandRates.addAll(bBrands)
                        finalInstallmentRates.addAll(bInsts)
                        unitsToReplaceCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.ATOMIC_UNIT,
                                operation = PreviewChangeOperation.REPLACE,
                                unitType = MergeUnitType.MACHINE,
                                targetId = machId,
                                memberCountsByCollection = mapOf(
                                    BackupCollectionType.MACHINES to 1,
                                    BackupCollectionType.MACHINE_BRAND_RATES to bBrands.size,
                                    BackupCollectionType.MACHINE_INSTALLMENT_RATES to bInsts.size,
                                ),
                            ),
                        )
                    }
                    EligibleOperationDisposition.SKIP_BACKUP -> {
                        if (lMach != null) {
                            finalMachines.add(lMach.toDto())
                            finalBrandRates.addAll(lBrands.map { it.toDto() })
                            finalInstallmentRates.addAll(lInsts.map { it.toDto() })
                            keptRecordsCount += 1 + lBrands.size + lInsts.size
                        }
                        if (bMach != null) {
                            ignoredRecordsCount += 1 + bBrands.size + bInsts.size
                        }
                    }
                }
            }

        // --- E) SALES + SALE_ITEMS + SALE_ITEM_PARTICIPANTS + SALE_PERSON_TOTALS (Unidade Financeira Indivisível) ---
        val localSalesById = localSnapshot.sales.associateBy { it.id }
        val localItemsBySale = localSnapshot.saleItems.groupBy { it.saleId }
        val localSipsByItem = localSnapshot.saleItemParticipants.groupBy { it.saleItemId }
        val localTotalsBySale = localSnapshot.salePersonTotals.groupBy { it.saleId }

        val backupSalesById = validatedBackup.sales.associateBy { it.id }
        val backupItemsBySale = validatedBackup.saleItems.groupBy { it.saleId }
        val backupSipsByItem = validatedBackup.saleItemParticipants.groupBy { it.saleItemId }
        val backupTotalsBySale = validatedBackup.salePersonTotals.groupBy { it.saleId }

        val saleActionsById = plan.planFor(BackupCollectionType.SALES).records.associateBy { it.recordId }

        val finalSales = ArrayList<SaleDto>()
        val finalSaleItems = ArrayList<SaleItemDto>()
        val finalSaleItemParticipants = ArrayList<SaleItemParticipantDto>()
        val finalSalePersonTotals = ArrayList<SalePersonTotalDto>()

        val addedSales = ArrayList<SaleDto>()
        val replacedSalePairs = ArrayList<Pair<SaleEntity, SaleDto>>()

        validation.eligibleUnits
            .filter { it.unitType == MergeUnitType.SALE }
            .forEach { unit ->
                val saleId = unit.unitId
                val planned = saleActionsById[saleId]

                val lSale = localSalesById[saleId]
                val lItems = localItemsBySale[saleId].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
                val lSips = lItems.flatMap { localSipsByItem[it.id].orEmpty() }
                    .sortedWith(compareBy({ it.saleItemId }, { it.personId }))
                val lTotals = localTotalsBySale[saleId].orEmpty().sortedBy { it.personId }

                val bSale = backupSalesById[saleId]
                val bItems = backupItemsBySale[saleId].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id }))
                val bSips = bItems.flatMap { backupSipsByItem[it.id].orEmpty() }
                    .sortedWith(compareBy({ it.saleItemId }, { it.personId }))
                val bTotals = backupTotalsBySale[saleId].orEmpty().sortedBy { it.personId }

                when (unit.disposition) {
                    EligibleOperationDisposition.ADD_NEW -> {
                        finalSales.add(bSale!!)
                        finalSaleItems.addAll(bItems)
                        finalSaleItemParticipants.addAll(bSips)
                        finalSalePersonTotals.addAll(bTotals)
                        addedSales.add(bSale)
                        newRecordsCount += 1 + bItems.size + bSips.size + bTotals.size
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.ATOMIC_UNIT,
                                operation = PreviewChangeOperation.ADD,
                                unitType = MergeUnitType.SALE,
                                targetId = saleId,
                                memberCountsByCollection = mapOf(
                                    BackupCollectionType.SALES to 1,
                                    BackupCollectionType.SALE_ITEMS to bItems.size,
                                    BackupCollectionType.SALE_ITEM_PARTICIPANTS to bSips.size,
                                    BackupCollectionType.SALE_PERSON_TOTALS to bTotals.size,
                                ),
                            ),
                        )
                    }
                    EligibleOperationDisposition.KEEP_EXISTING -> {
                        finalSales.add(lSale!!.toDto())
                        finalSaleItems.addAll(lItems.map { it.toDto() })
                        finalSaleItemParticipants.addAll(lSips.map { it.toDto() })
                        finalSalePersonTotals.addAll(lTotals.map { it.toDto() })
                        val count = 1 + lItems.size + lSips.size + lTotals.size
                        keptRecordsCount += count
                        if (planned?.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY) {
                            localOnlyPreservedCount += count
                        }
                    }
                    EligibleOperationDisposition.APPLY_BACKUP -> {
                        // Substituição integral atômica da venda sem recálculo e sem misturar itens locais
                        finalSales.add(bSale!!)
                        finalSaleItems.addAll(bItems)
                        finalSaleItemParticipants.addAll(bSips)
                        finalSalePersonTotals.addAll(bTotals)
                        replacedSalePairs.add(lSale!! to bSale)
                        unitsToReplaceCount += 1
                        changedEntries.add(
                            PreviewChangedEntry(
                                scope = PreviewChangeScope.ATOMIC_UNIT,
                                operation = PreviewChangeOperation.REPLACE,
                                unitType = MergeUnitType.SALE,
                                targetId = saleId,
                                memberCountsByCollection = mapOf(
                                    BackupCollectionType.SALES to 1,
                                    BackupCollectionType.SALE_ITEMS to bItems.size,
                                    BackupCollectionType.SALE_ITEM_PARTICIPANTS to bSips.size,
                                    BackupCollectionType.SALE_PERSON_TOTALS to bTotals.size,
                                ),
                            ),
                        )
                    }
                    EligibleOperationDisposition.SKIP_BACKUP -> {
                        if (lSale != null) {
                            finalSales.add(lSale.toDto())
                            finalSaleItems.addAll(lItems.map { it.toDto() })
                            finalSaleItemParticipants.addAll(lSips.map { it.toDto() })
                            finalSalePersonTotals.addAll(lTotals.map { it.toDto() })
                            keptRecordsCount += 1 + lItems.size + lSips.size + lTotals.size
                        }
                        if (bSale != null) {
                            ignoredRecordsCount += 1 + bItems.size + bSips.size + bTotals.size
                        }
                    }
                }
            }

        // Ordenação determinística das 11 coleções construídas
        val mergedCollections = PreviewMergedCollections(
            preferences = finalPreferences.sortedBy { it.key },
            people = finalPeople.sortedWith(compareBy<PersonDto> { it.createdAtEpochMs }.thenBy { it.id }),
            products = finalProducts.sortedWith(compareBy<ProductDto> { it.createdAtEpochMs }.thenBy { it.id }),
            productParticipants = finalProductParticipants.sortedWith(
                compareBy<ProductParticipantDto> { it.productId }.thenBy { it.personId },
            ),
            machines = finalMachines.sortedWith(compareBy<MachineDto> { it.createdAtEpochMs }.thenBy { it.id }),
            machineBrandRates = finalBrandRates.sortedWith(
                compareBy<MachineBrandRateDto> { it.machineId }.thenBy { it.brandKey },
            ),
            machineInstallmentRates = finalInstallmentRates.sortedWith(
                compareBy<MachineInstallmentRateDto> { it.machineId }.thenBy { it.installments },
            ),
            sales = finalSales.sortedWith(compareBy<SaleDto> { it.soldAtEpochMs }.thenBy { it.id }),
            saleItems = finalSaleItems.sortedWith(
                compareBy<SaleItemDto> { it.saleId }.thenBy { it.sortOrder }.thenBy { it.id },
            ),
            saleItemParticipants = finalSaleItemParticipants.sortedWith(
                compareBy<SaleItemParticipantDto> { it.saleItemId }.thenBy { it.personId },
            ),
            salePersonTotals = finalSalePersonTotals.sortedWith(
                compareBy<SalePersonTotalDto> { it.saleId }.thenBy { it.personId },
            ),
        )

        // 5) Validação integral de integridade referencial e financeira do estado final em memória
        val mergedEntitySnapshot = toEntitySnapshot(mergedCollections)
        try {
            JsonBackupExporter.validateSnapshot(mergedEntitySnapshot)
        } catch (ex: JsonBackupValidationException) {
            val issueType = when {
                ex.message?.contains("Nome normalizado", ignoreCase = true) == true ->
                    PreviewIssueType.IDENTITY_NAME_COLLISION
                ex.message?.contains("inexistente", ignoreCase = true) == true ->
                    PreviewIssueType.REFERENTIAL_OR_DEPENDENCY_ERROR
                else -> PreviewIssueType.FINANCIAL_VALIDATION_ERROR
            }
            issues.add(
                PreviewValidationIssue(
                    type = issueType,
                    targetId = ex.entityId,
                    message = ex.message ?: "Falha na validação final do estado mesclado.",
                ),
            )
        }

        // Garante explicitamente que nenhum registro somente local foi removido
        verifyAllLocalOnlyPreserved(
            plan = plan,
            validation = validation,
            localSnapshot = localSnapshot,
            mergedCollections = mergedCollections,
            issues = issues,
        )

        val financialSummary = try {
            computeFinancialSummary(
                localSales = localSnapshot.sales,
                finalSales = mergedEntitySnapshot.sales,
                addedSales = addedSales,
                replacedSalePairs = replacedSalePairs,
            )
        } catch (ex: FinancialOverflowException) {
            issues.add(
                PreviewValidationIssue(
                    type = PreviewIssueType.FINANCIAL_VALIDATION_ERROR,
                    message = "Estouro numérico no resumo financeiro da prévia: ${ex.message}",
                ),
            )
            PreviewFinancialSummary.EMPTY
        }

        val sortedChanged = changedEntries.sortedWith(
            compareBy<PreviewChangedEntry> { it.scope.ordinal }
                .thenBy { it.operation.ordinal }
                .thenBy { it.collection?.ordinal ?: -1 }
                .thenBy { it.unitType?.ordinal ?: -1 }
                .thenBy { it.targetId },
        )

        val finalCounts = mergedCollections.countsByCollection()
        val isSafe = issues.isEmpty() && localStateMatchesPlan

        return JsonBackupMergePreviewResult(
            isSafeToApply = isSafe,
            newRecordsCount = newRecordsCount,
            keptRecordsCount = keptRecordsCount,
            unitsToReplaceCount = unitsToReplaceCount,
            ignoredRecordsCount = ignoredRecordsCount,
            localOnlyPreservedCount = localOnlyPreservedCount,
            finalTableCounts = finalCounts,
            changedEntries = sortedChanged,
            financialSummary = financialSummary,
            issues = issues.distinct(),
            mergedCollections = if (isSafe) mergedCollections else PreviewMergedCollections(),
            executionContract = PreviewExecutionContract(
                expectedLocalStateFingerprint = plan.preconditions.expectedLocalStateFingerprint,
                expectedLocalCounts = plan.preconditions.expectedLocalCounts,
                expectedFinalCounts = if (isSafe) finalCounts else emptyMap(),
                backupIdentity = backupSignature,
                approvedDecisionsByConflictKey = approvedDecisionsSorted,
                authorizedOperationsCount = if (isSafe) sortedChanged.size else 0,
                requiresSingleTransaction = true,
                requiresFinalValidationBeforeCommit = true,
                requiresRollbackOnAnyFailure = true,
            ),
            localStateMatchesPlan = localStateMatchesPlan,
        )
    }

    private fun verifyAllLocalOnlyPreserved(
        plan: JsonBackupMergePlanResult,
        validation: MergeDecisionValidationResult,
        localSnapshot: JsonBackupSnapshot,
        mergedCollections: PreviewMergedCollections,
        issues: MutableList<PreviewValidationIssue>,
    ) {
        val replacedProductIds = validation.eligibleUnits
            .filter { it.unitType == MergeUnitType.PRODUCT && it.disposition == EligibleOperationDisposition.APPLY_BACKUP }
            .mapTo(HashSet()) { it.unitId }
        val replacedMachineIds = validation.eligibleUnits
            .filter { it.unitType == MergeUnitType.MACHINE && it.disposition == EligibleOperationDisposition.APPLY_BACKUP }
            .mapTo(HashSet()) { it.unitId }
        val replacedSaleIds = validation.eligibleUnits
            .filter { it.unitType == MergeUnitType.SALE && it.disposition == EligibleOperationDisposition.APPLY_BACKUP }
            .mapTo(HashSet()) { it.unitId }

        val localProdPartProductByKey = localSnapshot.productParticipants.associate {
            "${it.productId}:${it.personId}" to it.productId
        }
        val localMachBrandMachineByKey = localSnapshot.machineBrandRates.associate {
            "${it.machineId}:${it.brandKey}" to it.machineId
        }
        val localMachInstMachineByKey = localSnapshot.machineInstallmentRates.associate {
            "${it.machineId}:${it.installments}" to it.machineId
        }
        val localSaleItemSaleById = localSnapshot.saleItems.associate { it.id to it.saleId }
        val localSalePartSaleByKey = localSnapshot.saleItemParticipants.associate {
            "${it.saleItemId}:${it.personId}" to localSaleItemSaleById[it.saleItemId]
        }
        val localSalePersonTotalSaleByKey = localSnapshot.salePersonTotals.associate {
            "${it.saleId}:${it.personId}" to it.saleId
        }

        val mergedIdsByCollection = mapOf(
            BackupCollectionType.PREFERENCES to mergedCollections.preferences.map { it.key }.toHashSet(),
            BackupCollectionType.PEOPLE to mergedCollections.people.map { it.id }.toHashSet(),
            BackupCollectionType.PRODUCTS to mergedCollections.products.map { it.id }.toHashSet(),
            BackupCollectionType.PRODUCT_PARTICIPANTS to mergedCollections.productParticipants.map { "${it.productId}:${it.personId}" }.toHashSet(),
            BackupCollectionType.MACHINES to mergedCollections.machines.map { it.id }.toHashSet(),
            BackupCollectionType.MACHINE_BRAND_RATES to mergedCollections.machineBrandRates.map { "${it.machineId}:${it.brandKey}" }.toHashSet(),
            BackupCollectionType.MACHINE_INSTALLMENT_RATES to mergedCollections.machineInstallmentRates.map { "${it.machineId}:${it.installments}" }.toHashSet(),
            BackupCollectionType.SALES to mergedCollections.sales.map { it.id }.toHashSet(),
            BackupCollectionType.SALE_ITEMS to mergedCollections.saleItems.map { it.id }.toHashSet(),
            BackupCollectionType.SALE_ITEM_PARTICIPANTS to mergedCollections.saleItemParticipants.map { "${it.saleItemId}:${it.personId}" }.toHashSet(),
            BackupCollectionType.SALE_PERSON_TOTALS to mergedCollections.salePersonTotals.map { "${it.saleId}:${it.personId}" }.toHashSet(),
        )

        plan.collectionPlans.forEach { cp ->
            val presentSet = mergedIdsByCollection.getValue(cp.collection)
            cp.records
                .filter { it.comparisonStatus == RecordComparisonStatus.LOCAL_ONLY }
                .forEach { localOnlyRec ->
                    val belongsToReplacedAtomicUnit = when (cp.collection) {
                        BackupCollectionType.PRODUCT_PARTICIPANTS ->
                            localProdPartProductByKey[localOnlyRec.recordId] in replacedProductIds
                        BackupCollectionType.MACHINE_BRAND_RATES ->
                            localMachBrandMachineByKey[localOnlyRec.recordId] in replacedMachineIds
                        BackupCollectionType.MACHINE_INSTALLMENT_RATES ->
                            localMachInstMachineByKey[localOnlyRec.recordId] in replacedMachineIds
                        BackupCollectionType.SALE_ITEMS ->
                            localSaleItemSaleById[localOnlyRec.recordId] in replacedSaleIds
                        BackupCollectionType.SALE_ITEM_PARTICIPANTS ->
                            localSalePartSaleByKey[localOnlyRec.recordId] in replacedSaleIds
                        BackupCollectionType.SALE_PERSON_TOTALS ->
                            localSalePersonTotalSaleByKey[localOnlyRec.recordId] in replacedSaleIds
                        else -> false
                    }
                    if (!belongsToReplacedAtomicUnit && !presentSet.contains(localOnlyRec.recordId)) {
                        issues.add(
                            PreviewValidationIssue(
                                type = PreviewIssueType.REFERENTIAL_OR_DEPENDENCY_ERROR,
                                collection = cp.collection,
                                targetId = localOnlyRec.recordId,
                                message = "Registro somente local '${cp.collection.key}:${localOnlyRec.recordId}' não foi preservado na prévia final.",
                            ),
                        )
                    }
                }
        }
    }

    private fun computeFinancialSummary(
        localSales: List<SaleEntity>,
        finalSales: List<SaleEntity>,
        addedSales: List<SaleDto>,
        replacedSalePairs: List<Pair<SaleEntity, SaleDto>>,
    ): PreviewFinancialSummary {
        var localFinal = 0L
        var localFee = 0L
        var localNet = 0L
        localSales.forEach { s ->
            localFinal = MonetaryRounding.safeAdd(localFinal, s.finalAmountCents)
            localFee = MonetaryRounding.safeAdd(localFee, s.feeTotalCents)
            localNet = MonetaryRounding.safeAdd(localNet, s.netTotalCents)
        }

        var finalFinal = 0L
        var finalFee = 0L
        var finalNet = 0L
        finalSales.forEach { s ->
            finalFinal = MonetaryRounding.safeAdd(finalFinal, s.finalAmountCents)
            finalFee = MonetaryRounding.safeAdd(finalFee, s.feeTotalCents)
            finalNet = MonetaryRounding.safeAdd(finalNet, s.netTotalCents)
        }

        var addedFinal = 0L
        var addedFee = 0L
        var addedNet = 0L
        addedSales.forEach { s ->
            addedFinal = MonetaryRounding.safeAdd(addedFinal, s.finalAmountCents)
            addedFee = MonetaryRounding.safeAdd(addedFee, s.feeTotalCents)
            addedNet = MonetaryRounding.safeAdd(addedNet, s.netTotalCents)
        }

        var repFinalDelta = 0L
        var repFeeDelta = 0L
        var repNetDelta = 0L
        replacedSalePairs.forEach { (oldLocal, newBackup) ->
            repFinalDelta = MonetaryRounding.safeAdd(
                repFinalDelta,
                MonetaryRounding.safeSubtract(newBackup.finalAmountCents, oldLocal.finalAmountCents),
            )
            repFeeDelta = MonetaryRounding.safeAdd(
                repFeeDelta,
                MonetaryRounding.safeSubtract(newBackup.feeTotalCents, oldLocal.feeTotalCents),
            )
            repNetDelta = MonetaryRounding.safeAdd(
                repNetDelta,
                MonetaryRounding.safeSubtract(newBackup.netTotalCents, oldLocal.netTotalCents),
            )
        }

        return PreviewFinancialSummary(
            localSalesCount = localSales.size,
            localTotalFinalAmountCents = localFinal,
            localTotalFeeAmountCents = localFee,
            localTotalNetAmountCents = localNet,
            finalSalesCount = finalSales.size,
            finalTotalFinalAmountCents = finalFinal,
            finalTotalFeeAmountCents = finalFee,
            finalTotalNetAmountCents = finalNet,
            addedSalesCount = addedSales.size,
            addedSalesFinalAmountCents = addedFinal,
            addedSalesFeeAmountCents = addedFee,
            addedSalesNetAmountCents = addedNet,
            replacedSalesCount = replacedSalePairs.size,
            replacedSalesFinalDeltaCents = repFinalDelta,
            replacedSalesFeeDeltaCents = repFeeDelta,
            replacedSalesNetDeltaCents = repNetDelta,
            netFinalAmountDeltaCents = MonetaryRounding.safeSubtract(finalFinal, localFinal),
            netFeeAmountDeltaCents = MonetaryRounding.safeSubtract(finalFee, localFee),
            netNetAmountDeltaCents = MonetaryRounding.safeSubtract(finalNet, localNet),
        )
    }

    internal fun computeBackupIdentitySignature(backup: ValidatedBackupData): BackupIdentitySignature {
        val dtoSnapshot = JsonBackupSnapshot(
            preferences = backup.preferences.map { PreferenceEntity(it.key, it.value, it.updatedAtEpochMs) },
            people = backup.people.map {
                PersonEntity(it.id, it.name, it.normalizedName, it.colorIndex, it.createdAtEpochMs, it.updatedAtEpochMs)
            },
            products = backup.products.map {
                ProductEntity(
                    id = it.id,
                    name = it.name,
                    priceCents = it.priceCents,
                    active = it.active,
                    feeResponsibilityMode = it.feeResponsibilityMode,
                    feeOwnerPersonId = it.feeOwnerPersonId,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                )
            },
            productParticipants = backup.productParticipants.map {
                ProductParticipantEntity(it.productId, it.personId, it.amountCents, it.sortOrder)
            },
            machines = backup.machines.map {
                MachineEntity(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    debitRateBps = it.debitRateBps,
                    creditEnabled = it.creditEnabled,
                    creditDefaultRateBps = it.creditDefaultRateBps,
                    brandRatesEnabled = it.brandRatesEnabled,
                    installmentsEnabled = it.installmentsEnabled,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    maxInstallments = it.maxInstallments,
                )
            },
            machineBrandRates = backup.machineBrandRates.map {
                MachineBrandRateEntity(it.machineId, it.brandKey, it.rateBps)
            },
            machineInstallmentRates = backup.machineInstallmentRates.map {
                MachineInstallmentRateEntity(it.machineId, it.installments, it.addonBps)
            },
            sales = backup.sales.map { it.toEntity() },
            saleItems = backup.saleItems.map { it.toEntity() },
            saleItemParticipants = backup.saleItemParticipants.map { it.toEntity() },
            salePersonTotals = backup.salePersonTotals.map { it.toEntity() },
        )
        val payloadHash = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(dtoSnapshot)
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            backup.format,
            backup.backupSchemaVersion.toString(),
            backup.applicationId,
            backup.roomSchemaVersion.toString(),
            backup.exportedAtEpochMs.toString(),
            payloadHash,
        ).forEach { part ->
            digest.update(part.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        val combinedHash = digest.digest().joinToString("") { "%02x".format(it) }
        return BackupIdentitySignature(
            format = backup.format,
            backupSchemaVersion = backup.backupSchemaVersion,
            applicationId = backup.applicationId,
            roomSchemaVersion = backup.roomSchemaVersion,
            exportedAtEpochMs = backup.exportedAtEpochMs,
            backupPayloadFingerprint = combinedHash,
        )
    }

    internal fun countsOfSnapshot(snapshot: JsonBackupSnapshot): Map<BackupCollectionType, Int> {
        return mapOf(
            BackupCollectionType.PREFERENCES to snapshot.preferences.size,
            BackupCollectionType.PEOPLE to snapshot.people.size,
            BackupCollectionType.PRODUCTS to snapshot.products.size,
            BackupCollectionType.PRODUCT_PARTICIPANTS to snapshot.productParticipants.size,
            BackupCollectionType.MACHINES to snapshot.machines.size,
            BackupCollectionType.MACHINE_BRAND_RATES to snapshot.machineBrandRates.size,
            BackupCollectionType.MACHINE_INSTALLMENT_RATES to snapshot.machineInstallmentRates.size,
            BackupCollectionType.SALES to snapshot.sales.size,
            BackupCollectionType.SALE_ITEMS to snapshot.saleItems.size,
            BackupCollectionType.SALE_ITEM_PARTICIPANTS to snapshot.saleItemParticipants.size,
            BackupCollectionType.SALE_PERSON_TOTALS to snapshot.salePersonTotals.size,
        )
    }

    internal fun toPreviewMergedCollections(snapshot: JsonBackupSnapshot): PreviewMergedCollections {
        return PreviewMergedCollections(
            preferences = snapshot.preferences.map { it.toDto() }.sortedBy { it.key },
            people = snapshot.people.map { it.toDto() }.sortedWith(
                compareBy<PersonDto> { it.createdAtEpochMs }.thenBy { it.id },
            ),
            products = snapshot.products.map { it.toDto() }.sortedWith(
                compareBy<ProductDto> { it.createdAtEpochMs }.thenBy { it.id },
            ),
            productParticipants = snapshot.productParticipants.map { it.toDto() }.sortedWith(
                compareBy<ProductParticipantDto> { it.productId }.thenBy { it.personId },
            ),
            machines = snapshot.machines.map { it.toDto() }.sortedWith(
                compareBy<MachineDto> { it.createdAtEpochMs }.thenBy { it.id },
            ),
            machineBrandRates = snapshot.machineBrandRates.map { it.toDto() }.sortedWith(
                compareBy<MachineBrandRateDto> { it.machineId }.thenBy { it.brandKey },
            ),
            machineInstallmentRates = snapshot.machineInstallmentRates.map { it.toDto() }.sortedWith(
                compareBy<MachineInstallmentRateDto> { it.machineId }.thenBy { it.installments },
            ),
            sales = snapshot.sales.map { it.toDto() }.sortedWith(
                compareBy<SaleDto> { it.soldAtEpochMs }.thenBy { it.id },
            ),
            saleItems = snapshot.saleItems.map { it.toDto() }.sortedWith(
                compareBy<SaleItemDto> { it.saleId }.thenBy { it.sortOrder }.thenBy { it.id },
            ),
            saleItemParticipants = snapshot.saleItemParticipants.map { it.toDto() }.sortedWith(
                compareBy<SaleItemParticipantDto> { it.saleItemId }.thenBy { it.personId },
            ),
            salePersonTotals = snapshot.salePersonTotals.map { it.toDto() }.sortedWith(
                compareBy<SalePersonTotalDto> { it.saleId }.thenBy { it.personId },
            ),
        )
    }

    internal fun toEntitySnapshot(collections: PreviewMergedCollections): JsonBackupSnapshot {
        return JsonBackupSnapshot(
            preferences = collections.preferences.map { PreferenceEntity(it.key, it.value, it.updatedAtEpochMs) },
            people = collections.people.map {
                PersonEntity(it.id, it.name, it.normalizedName, it.colorIndex, it.createdAtEpochMs, it.updatedAtEpochMs)
            },
            products = collections.products.map {
                ProductEntity(
                    id = it.id,
                    name = it.name,
                    priceCents = it.priceCents,
                    active = it.active,
                    feeResponsibilityMode = it.feeResponsibilityMode,
                    feeOwnerPersonId = it.feeOwnerPersonId,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                )
            },
            productParticipants = collections.productParticipants.map {
                ProductParticipantEntity(it.productId, it.personId, it.amountCents, it.sortOrder)
            },
            machines = collections.machines.map {
                MachineEntity(
                    id = it.id,
                    name = it.name,
                    normalizedName = it.normalizedName,
                    debitRateBps = it.debitRateBps,
                    creditEnabled = it.creditEnabled,
                    creditDefaultRateBps = it.creditDefaultRateBps,
                    brandRatesEnabled = it.brandRatesEnabled,
                    installmentsEnabled = it.installmentsEnabled,
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    maxInstallments = it.maxInstallments,
                )
            },
            machineBrandRates = collections.machineBrandRates.map {
                MachineBrandRateEntity(it.machineId, it.brandKey, it.rateBps)
            },
            machineInstallmentRates = collections.machineInstallmentRates.map {
                MachineInstallmentRateEntity(it.machineId, it.installments, it.addonBps)
            },
            sales = collections.sales.map { it.toEntity() },
            saleItems = collections.saleItems.map { it.toEntity() },
            saleItemParticipants = collections.saleItemParticipants.map { it.toEntity() },
            salePersonTotals = collections.salePersonTotals.map { it.toEntity() },
        )
    }

    private fun mapIssueType(type: DecisionValidationIssueType): PreviewIssueType = when (type) {
        DecisionValidationIssueType.UNKNOWN_CONFLICT_KEY -> PreviewIssueType.UNKNOWN_CONFLICT_KEY
        DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED -> PreviewIssueType.IDENTITY_NAME_COLLISION
        DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR -> PreviewIssueType.FINANCIAL_VALIDATION_ERROR
        DecisionValidationIssueType.UNRESOLVED_DEPENDENCY -> PreviewIssueType.REFERENTIAL_OR_DEPENDENCY_ERROR
        DecisionValidationIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING ->
            PreviewIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING
        DecisionValidationIssueType.PLAN_BACKUP_MISMATCH -> PreviewIssueType.PLAN_BACKUP_MISMATCH
        else -> PreviewIssueType.DECISION_VALIDATION_NOT_READY
    }

    private fun PreferenceEntity.toDto() = PreferenceDto(key, value, updatedAtEpochMs)

    private fun PersonEntity.toDto() = PersonDto(id, name, normalizedName, colorIndex, createdAtEpochMs, updatedAtEpochMs)

    private fun ProductEntity.toDto() = ProductDto(
        id = id,
        name = name,
        priceCents = priceCents,
        active = active,
        feeResponsibilityMode = feeResponsibilityMode,
        feeOwnerPersonId = feeOwnerPersonId,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ProductParticipantEntity.toDto() = ProductParticipantDto(productId, personId, amountCents, sortOrder)

    private fun MachineEntity.toDto() = MachineDto(
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

    private fun MachineBrandRateEntity.toDto() = MachineBrandRateDto(machineId, brandKey, rateBps)

    private fun MachineInstallmentRateEntity.toDto() = MachineInstallmentRateDto(machineId, installments, addonBps)

    private fun SaleEntity.toDto() = SaleDto(
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

    private fun SaleItemEntity.toDto() = SaleItemDto(
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

    private fun SaleItemParticipantEntity.toDto() = SaleItemParticipantDto(
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

    private fun SalePersonTotalEntity.toDto() = SalePersonTotalDto(
        saleId = saleId,
        personId = personId,
        sortOrder = sortOrder,
        personName = personName,
        personColorIndex = personColorIndex,
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
