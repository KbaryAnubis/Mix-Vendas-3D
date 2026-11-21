package com.mixvendas3d.app.domain.export

import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleItemWithParticipants
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import com.mixvendas3d.app.data.repository.SaleEntityMapper
import com.mixvendas3d.app.domain.calculation.FinancialOverflowException
import com.mixvendas3d.app.domain.calculation.MonetaryRounding
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.util.RateFormatter

object JsonBackupMergeDecisionValidator {

    suspend fun validate(
        plan: JsonBackupMergePlanResult,
        readResult: JsonBackupReadResult,
        database: AppDatabase,
        decisionsByConflictKey: Map<String, MergeConflictResolution> = emptyMap(),
    ): MergeDecisionValidationResult {
        require(readResult.isValid && readResult.validatedData != null) {
            "A validação das decisões exige um JsonBackupReadResult válido da Etapa 6B-1."
        }
        return validate(
            plan = plan,
            validatedBackup = readResult.validatedData,
            database = database,
            decisionsByConflictKey = decisionsByConflictKey,
        )
    }

    suspend fun validate(
        plan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        database: AppDatabase,
        decisionsByConflictKey: Map<String, MergeConflictResolution> = emptyMap(),
    ): MergeDecisionValidationResult {
        val localSnapshot = JsonBackupExporter.captureConsistentSnapshot(database)
        return validateFromSnapshot(
            plan = plan,
            validatedBackup = validatedBackup,
            localSnapshot = localSnapshot,
            decisionsByConflictKey = decisionsByConflictKey,
        )
    }

    internal fun validateFromSnapshot(
        plan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        localSnapshot: JsonBackupSnapshot,
        decisionsByConflictKey: Map<String, MergeConflictResolution> = emptyMap(),
    ): MergeDecisionValidationResult {
        val invalidInputs = ArrayList<DecisionValidationIssue>()
        val incompatibleDecisions = ArrayList<DecisionValidationIssue>()
        val blockedOperations = ArrayList<DecisionValidationIssue>()
        val pendingDependencies = ArrayList<PendingDependencyDetail>()
        val eligibleStandalone = ArrayList<EligibleStandaloneRecord>()
        val eligibleUnits = ArrayList<EligibleMergeUnit>()

        // 1) Coerência entre plano e backup validado
        if (plan.backupFormat != validatedBackup.format ||
            plan.backupSchemaVersion != validatedBackup.backupSchemaVersion ||
            plan.applicationId != validatedBackup.applicationId ||
            plan.roomSchemaVersion != validatedBackup.roomSchemaVersion ||
            plan.exportedAtEpochMs != validatedBackup.exportedAtEpochMs
        ) {
            blockedOperations.add(
                DecisionValidationIssue(
                    type = DecisionValidationIssueType.PLAN_BACKUP_MISMATCH,
                    message = "Metadados do plano de mesclagem não correspondem ao backup validado informado.",
                ),
            )
        }

        // 2) Proteção contra alterações locais no Room após o planejamento (SHA-256)
        val observedFingerprint = JsonBackupMergePlanner.computeLocalSnapshotFingerprint(localSnapshot)
        val observedCounts = mapOf(
            BackupCollectionType.PREFERENCES to localSnapshot.preferences.size,
            BackupCollectionType.PEOPLE to localSnapshot.people.size,
            BackupCollectionType.PRODUCTS to localSnapshot.products.size,
            BackupCollectionType.PRODUCT_PARTICIPANTS to localSnapshot.productParticipants.size,
            BackupCollectionType.MACHINES to localSnapshot.machines.size,
            BackupCollectionType.MACHINE_BRAND_RATES to localSnapshot.machineBrandRates.size,
            BackupCollectionType.MACHINE_INSTALLMENT_RATES to localSnapshot.machineInstallmentRates.size,
            BackupCollectionType.SALES to localSnapshot.sales.size,
            BackupCollectionType.SALE_ITEMS to localSnapshot.saleItems.size,
            BackupCollectionType.SALE_ITEM_PARTICIPANTS to localSnapshot.saleItemParticipants.size,
            BackupCollectionType.SALE_PERSON_TOTALS to localSnapshot.salePersonTotals.size,
        )
        val localStateMatchesPlan =
            observedFingerprint == plan.preconditions.expectedLocalStateFingerprint &&
                observedCounts == plan.preconditions.expectedLocalCounts

        if (!localStateMatchesPlan) {
            blockedOperations.add(
                DecisionValidationIssue(
                    type = DecisionValidationIssueType.LOCAL_STATE_CHANGED_SINCE_PLANNING,
                    message = "O banco local Room foi modificado após o planejamento; gere um novo plano antes de restaurar.",
                ),
            )
        }

        // 3) Validação sintática e de escopo das decisões explícitas informadas
        val planConflictsByKey = plan.conflictItems.associateBy { "${it.collection.key}:${it.recordId}" }
        val acceptedDecisions = LinkedHashMap<String, MergeConflictResolution>()
        decisionsByConflictKey.keys.sorted().forEach { conflictKey ->
            val resolution = decisionsByConflictKey.getValue(conflictKey)
            val conflictItem = planConflictsByKey[conflictKey]
            when {
                conflictItem == null -> {
                    invalidInputs.add(
                        DecisionValidationIssue(
                            type = DecisionValidationIssueType.UNKNOWN_CONFLICT_KEY,
                            message = "Chave de conflito desconhecida ou inexistente no plano: '$conflictKey'.",
                        ),
                    )
                }
                resolution !in conflictItem.allowedResolutions -> {
                    invalidInputs.add(
                        DecisionValidationIssue(
                            type = DecisionValidationIssueType.DISALLOWED_RESOLUTION,
                            collection = conflictItem.collection,
                            recordId = conflictItem.recordId,
                            relatedUnitType = conflictItem.relatedUnitType,
                            relatedUnitId = conflictItem.relatedUnitId,
                            message = "Resolução '$resolution' não permitida para '$conflictKey'.",
                        ),
                    )
                }
                else -> {
                    acceptedDecisions[conflictKey] = resolution
                }
            }
        }

        // Preserva escolhas explícitas já anexadas ao plano quando não sobrescritas neste lote
        val mergedValidDecisions = LinkedHashMap<String, MergeConflictResolution>()
        plan.conflictItems.forEach { item ->
            val key = "${item.collection.key}:${item.recordId}"
            val effective = acceptedDecisions[key] ?: item.selectedResolution
            if (effective != null) {
                mergedValidDecisions[key] = effective
            }
        }
        val resolvedPlan = plan.withExplicitDecisions(mergedValidDecisions)

        val resolvedConflictsByKey = resolvedPlan.conflictItems.associateBy { "${it.collection.key}:${it.recordId}" }
        val resolvedConflicts = resolvedPlan.conflictItems.filter { it.selectedResolution != null }
        val unresolvedConflicts = resolvedPlan.conflictItems.filter { it.selectedResolution == null }

        // 4) Preferências (coleção independente)
        validatePreferences(
            resolvedPlan = resolvedPlan,
            resolvedConflictsByKey = resolvedConflictsByKey,
            blockedOperations = blockedOperations,
            eligibleStandalone = eligibleStandalone,
        )

        // 5) Pessoas (coleção independente + colisão de identidade + mapa de presença para produtos)
        val personAvailability = validatePeople(
            resolvedPlan = resolvedPlan,
            validatedBackup = validatedBackup,
            localSnapshot = localSnapshot,
            resolvedConflictsByKey = resolvedConflictsByKey,
            blockedOperations = blockedOperations,
            eligibleStandalone = eligibleStandalone,
        )

        // 6) Produtos + Participantes (unidade atômica + dependência de pessoas)
        validateProductUnits(
            resolvedPlan = resolvedPlan,
            validatedBackup = validatedBackup,
            resolvedConflictsByKey = resolvedConflictsByKey,
            personAvailability = personAvailability,
            pendingDependencies = pendingDependencies,
            incompatibleDecisions = incompatibleDecisions,
            blockedOperations = blockedOperations,
            eligibleUnits = eligibleUnits,
        )

        // 7) Maquininhas + Taxas por Bandeira + Adicionais de Parcelamento (unidade atômica + colisão de nome)
        validateMachineUnits(
            resolvedPlan = resolvedPlan,
            validatedBackup = validatedBackup,
            localSnapshot = localSnapshot,
            resolvedConflictsByKey = resolvedConflictsByKey,
            incompatibleDecisions = incompatibleDecisions,
            blockedOperations = blockedOperations,
            eligibleUnits = eligibleUnits,
        )

        // 8) Vendas + Itens + Participantes do Item + Totais por Pessoa (unidade financeira indivisível)
        validateSaleUnits(
            resolvedPlan = resolvedPlan,
            validatedBackup = validatedBackup,
            resolvedConflictsByKey = resolvedConflictsByKey,
            incompatibleDecisions = incompatibleDecisions,
            blockedOperations = blockedOperations,
            eligibleUnits = eligibleUnits,
        )

        val sortedPending = pendingDependencies.sortedWith(
            compareBy<PendingDependencyDetail> { it.collection.ordinal }
                .thenBy { it.recordId }
                .thenBy { it.dependsOnRecordId },
        )
        val sortedIncompatible = incompatibleDecisions.sortedWith(
            compareBy<DecisionValidationIssue> { it.relatedUnitType?.ordinal ?: -1 }
                .thenBy { it.relatedUnitId.orEmpty() }
                .thenBy { it.recordId.orEmpty() },
        )
        val sortedBlocked = blockedOperations.sortedWith(
            compareBy<DecisionValidationIssue> { it.type.ordinal }
                .thenBy { it.collection?.ordinal ?: -1 }
                .thenBy { it.recordId.orEmpty() },
        )
        val sortedInvalid = invalidInputs.sortedBy { it.message }
        val sortedEligibleStandalone = eligibleStandalone.sortedWith(
            compareBy<EligibleStandaloneRecord> { it.collection.ordinal }.thenBy { it.recordId },
        )
        val sortedEligibleUnits = eligibleUnits.sortedWith(
            compareBy<EligibleMergeUnit> { it.unitType.ordinal }.thenBy { it.unitId },
        )

        val isReady = unresolvedConflicts.isEmpty() &&
            sortedPending.isEmpty() &&
            sortedIncompatible.isEmpty() &&
            sortedBlocked.isEmpty() &&
            sortedInvalid.isEmpty() &&
            localStateMatchesPlan

        val preservedPreconditions = RestorationPreconditions(
            expectedLocalStateFingerprint = plan.preconditions.expectedLocalStateFingerprint,
            expectedLocalCounts = plan.preconditions.expectedLocalCounts,
            unresolvedConflictIds = unresolvedConflicts.map { "${it.collection.key}:${it.recordId}" },
            requiresSingleTransaction = true,
            requiresFinalValidationBeforeCommit = true,
            requiresRollbackOnAnyFailure = true,
            canExecuteAdditiveWithoutUserDecisions = isReady,
        )

        return MergeDecisionValidationResult(
            backupFormat = validatedBackup.format,
            backupSchemaVersion = validatedBackup.backupSchemaVersion,
            applicationId = validatedBackup.applicationId,
            roomSchemaVersion = validatedBackup.roomSchemaVersion,
            exportedAtEpochMs = validatedBackup.exportedAtEpochMs,
            totalConflictsCount = resolvedPlan.conflictItems.size,
            resolvedConflictsCount = resolvedConflicts.size,
            unresolvedConflictsCount = unresolvedConflicts.size,
            pendingDependenciesCount = sortedPending.size,
            incompatibleDecisionsCount = sortedIncompatible.size,
            blockedOperationsCount = sortedBlocked.size,
            resolvedPlan = resolvedPlan,
            resolvedConflicts = resolvedConflicts,
            unresolvedConflicts = unresolvedConflicts,
            pendingDependencies = sortedPending,
            incompatibleDecisions = sortedIncompatible,
            blockedOperations = sortedBlocked,
            invalidDecisionInputs = sortedInvalid,
            eligibleStandaloneRecords = sortedEligibleStandalone,
            eligibleUnits = sortedEligibleUnits,
            expectedLocalStateFingerprint = plan.preconditions.expectedLocalStateFingerprint,
            observedLocalStateFingerprint = observedFingerprint,
            localStateMatchesPlan = localStateMatchesPlan,
            preconditions = preservedPreconditions,
        )
    }

    private fun validatePreferences(
        resolvedPlan: JsonBackupMergePlanResult,
        resolvedConflictsByKey: Map<String, MergeConflictItem>,
        blockedOperations: MutableList<DecisionValidationIssue>,
        eligibleStandalone: MutableList<EligibleStandaloneRecord>,
    ) {
        resolvedPlan.planFor(BackupCollectionType.PREFERENCES).records.forEach { rec ->
            when (rec.action) {
                MergePlanAction.ADD -> eligibleStandalone.add(
                    EligibleStandaloneRecord(
                        collection = BackupCollectionType.PREFERENCES,
                        recordId = rec.recordId,
                        disposition = EligibleOperationDisposition.ADD_NEW,
                        keyParts = rec.keyParts,
                    ),
                )
                MergePlanAction.KEEP -> eligibleStandalone.add(
                    EligibleStandaloneRecord(
                        collection = BackupCollectionType.PREFERENCES,
                        recordId = rec.recordId,
                        disposition = EligibleOperationDisposition.KEEP_EXISTING,
                        keyParts = rec.keyParts,
                    ),
                )
                MergePlanAction.CONFLICT -> {
                    val conflict = resolvedConflictsByKey["${BackupCollectionType.PREFERENCES.key}:${rec.recordId}"]
                    when (conflict?.selectedResolution) {
                        null -> Unit
                        MergeConflictResolution.USE_BACKUP -> eligibleStandalone.add(
                            EligibleStandaloneRecord(
                                collection = BackupCollectionType.PREFERENCES,
                                recordId = rec.recordId,
                                disposition = EligibleOperationDisposition.APPLY_BACKUP,
                                keyParts = rec.keyParts,
                            ),
                        )
                        MergeConflictResolution.KEEP_LOCAL,
                        MergeConflictResolution.IGNORE_IMPORT -> eligibleStandalone.add(
                            EligibleStandaloneRecord(
                                collection = BackupCollectionType.PREFERENCES,
                                recordId = rec.recordId,
                                disposition = EligibleOperationDisposition.SKIP_BACKUP,
                                keyParts = rec.keyParts,
                            ),
                        )
                    }
                }
                MergePlanAction.BLOCKED -> blockedOperations.add(
                    DecisionValidationIssue(
                        type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                        collection = BackupCollectionType.PREFERENCES,
                        recordId = rec.recordId,
                        message = rec.reason ?: "Preferência '${rec.recordId}' bloqueada no plano.",
                    ),
                )
                MergePlanAction.PENDING_DEPENDENCY -> Unit
            }
        }
    }

    private enum class PersonAvailabilityStatus {
        AVAILABLE,
        UNRESOLVED_CONFLICT,
        BLOCKED_COLLISION,
        SKIPPED_DISTINCT_ID,
    }

    private fun validatePeople(
        resolvedPlan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        localSnapshot: JsonBackupSnapshot,
        resolvedConflictsByKey: Map<String, MergeConflictItem>,
        blockedOperations: MutableList<DecisionValidationIssue>,
        eligibleStandalone: MutableList<EligibleStandaloneRecord>,
    ): Map<String, PersonAvailabilityStatus> {
        val backupPeopleById = validatedBackup.people.associateBy { it.id }
        val localPeopleByNormalized = localSnapshot.people.associateBy { it.normalizedName }
        val availability = HashMap<String, PersonAvailabilityStatus>()

        resolvedPlan.planFor(BackupCollectionType.PEOPLE).records.forEach { rec ->
            when (rec.action) {
                MergePlanAction.ADD -> {
                    val backupPerson = backupPeopleById[rec.recordId]
                    val colliding = backupPerson?.let { localPeopleByNormalized[it.normalizedName] }
                        ?.takeIf { it.id != rec.recordId }
                    if (backupPerson == null || backupPerson.name.isBlank() || backupPerson.normalizedName.isBlank()) {
                        blockedOperations.add(
                            DecisionValidationIssue(
                                type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                collection = BackupCollectionType.PEOPLE,
                                recordId = rec.recordId,
                                message = "Pessoa '${rec.recordId}' possui dados inválidos no backup.",
                            ),
                        )
                    } else if (colliding != null) {
                        blockedOperations.add(
                            DecisionValidationIssue(
                                type = DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED,
                                collection = BackupCollectionType.PEOPLE,
                                recordId = rec.recordId,
                                message = "Pessoa '${rec.recordId}' colide em normalizedName com registro local '${colliding.id}'.",
                            ),
                        )
                        availability[rec.recordId] = PersonAvailabilityStatus.BLOCKED_COLLISION
                    } else {
                        availability[rec.recordId] = PersonAvailabilityStatus.AVAILABLE
                        eligibleStandalone.add(
                            EligibleStandaloneRecord(
                                collection = BackupCollectionType.PEOPLE,
                                recordId = rec.recordId,
                                disposition = EligibleOperationDisposition.ADD_NEW,
                                keyParts = rec.keyParts,
                            ),
                        )
                    }
                }
                MergePlanAction.KEEP -> {
                    availability[rec.recordId] = PersonAvailabilityStatus.AVAILABLE
                    eligibleStandalone.add(
                        EligibleStandaloneRecord(
                            collection = BackupCollectionType.PEOPLE,
                            recordId = rec.recordId,
                            disposition = EligibleOperationDisposition.KEEP_EXISTING,
                            keyParts = rec.keyParts,
                        ),
                    )
                }
                MergePlanAction.CONFLICT -> {
                    val conflict = resolvedConflictsByKey["${BackupCollectionType.PEOPLE.key}:${rec.recordId}"]
                    val resolution = conflict?.selectedResolution
                    when (resolution) {
                        null -> {
                            availability[rec.recordId] = PersonAvailabilityStatus.UNRESOLVED_CONFLICT
                        }
                        MergeConflictResolution.USE_BACKUP -> {
                            val backupPerson = backupPeopleById[rec.recordId]
                            val colliding = backupPerson?.let { localPeopleByNormalized[it.normalizedName] }
                                ?.takeIf { it.id != rec.recordId }
                            if (conflict.kind == MergeConflictKind.IDENTITY_NAME_COLLISION ||
                                conflict.collidingLocalRecordId != null ||
                                colliding != null
                            ) {
                                val targetCollision = conflict.collidingLocalRecordId ?: colliding?.id
                                blockedOperations.add(
                                    DecisionValidationIssue(
                                        type = DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED,
                                        collection = BackupCollectionType.PEOPLE,
                                        recordId = rec.recordId,
                                        message = "Decisão USE_BACKUP para pessoa '${rec.recordId}' bloqueada: colisão de normalizedName com '${targetCollision}' exigiria remapeamento de ID não permitido.",
                                    ),
                                )
                                availability[rec.recordId] = PersonAvailabilityStatus.BLOCKED_COLLISION
                            } else if (backupPerson == null || backupPerson.name.isBlank() || backupPerson.normalizedName.isBlank()) {
                                blockedOperations.add(
                                    DecisionValidationIssue(
                                        type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                        collection = BackupCollectionType.PEOPLE,
                                        recordId = rec.recordId,
                                        message = "Pessoa '${rec.recordId}' do backup possui nome inválido.",
                                    ),
                                )
                                availability[rec.recordId] = PersonAvailabilityStatus.BLOCKED_COLLISION
                            } else {
                                availability[rec.recordId] = PersonAvailabilityStatus.AVAILABLE
                                eligibleStandalone.add(
                                    EligibleStandaloneRecord(
                                        collection = BackupCollectionType.PEOPLE,
                                        recordId = rec.recordId,
                                        disposition = EligibleOperationDisposition.APPLY_BACKUP,
                                        keyParts = rec.keyParts,
                                    ),
                                )
                            }
                        }
                        MergeConflictResolution.KEEP_LOCAL,
                        MergeConflictResolution.IGNORE_IMPORT -> {
                            if (conflict.kind == MergeConflictKind.IDENTITY_NAME_COLLISION) {
                                // Registro do backup com ID distinto não será inserido (nem remapeado)
                                availability[rec.recordId] = PersonAvailabilityStatus.SKIPPED_DISTINCT_ID
                            } else {
                                // Mesmo ID já existe no Room local -> ID permanece disponível localmente
                                availability[rec.recordId] = PersonAvailabilityStatus.AVAILABLE
                            }
                            eligibleStandalone.add(
                                EligibleStandaloneRecord(
                                    collection = BackupCollectionType.PEOPLE,
                                    recordId = rec.recordId,
                                    disposition = EligibleOperationDisposition.SKIP_BACKUP,
                                    keyParts = rec.keyParts,
                                ),
                            )
                        }
                    }
                }
                MergePlanAction.BLOCKED -> {
                    availability[rec.recordId] = PersonAvailabilityStatus.BLOCKED_COLLISION
                    blockedOperations.add(
                        DecisionValidationIssue(
                            type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                            collection = BackupCollectionType.PEOPLE,
                            recordId = rec.recordId,
                            message = rec.reason ?: "Pessoa '${rec.recordId}' bloqueada no plano.",
                        ),
                    )
                }
                MergePlanAction.PENDING_DEPENDENCY -> {
                    availability[rec.recordId] = PersonAvailabilityStatus.UNRESOLVED_CONFLICT
                }
            }
        }

        return availability
    }

    private fun validateProductUnits(
        resolvedPlan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        resolvedConflictsByKey: Map<String, MergeConflictItem>,
        personAvailability: Map<String, PersonAvailabilityStatus>,
        pendingDependencies: MutableList<PendingDependencyDetail>,
        incompatibleDecisions: MutableList<DecisionValidationIssue>,
        blockedOperations: MutableList<DecisionValidationIssue>,
        eligibleUnits: MutableList<EligibleMergeUnit>,
    ) {
        val backupProductsById = validatedBackup.products.associateBy { it.id }
        val backupPartsByProd = validatedBackup.productParticipants.groupBy { it.productId }
        val unitConflictsMap = resolvedPlan.conflictItems
            .filter { it.relatedUnitType == MergeUnitType.PRODUCT && it.relatedUnitId != null }
            .groupBy { it.relatedUnitId!! }

        resolvedPlan.relatedUnits
            .filter { it.unitType == MergeUnitType.PRODUCT }
            .forEach { unit ->
                val productId = unit.unitId
                when (unit.action) {
                    MergePlanAction.KEEP -> {
                        eligibleUnits.add(
                            EligibleMergeUnit(
                                unitType = MergeUnitType.PRODUCT,
                                unitId = productId,
                                disposition = EligibleOperationDisposition.KEEP_EXISTING,
                                memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                            ),
                        )
                    }
                    MergePlanAction.BLOCKED -> {
                        val reasons = unit.blockReasons.ifEmpty {
                            listOf("Produto '$productId' bloqueado por inconsistência no plano.")
                        }
                        reasons.forEach { msg ->
                            blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    message = msg,
                                ),
                            )
                        }
                    }
                    MergePlanAction.ADD,
                    MergePlanAction.PENDING_DEPENDENCY -> {
                        val bProd = backupProductsById[productId]
                        val bParts = backupPartsByProd[productId].orEmpty().sortedBy { it.personId }
                        val splitError = if (bProd == null) {
                            "Produto '$productId' ausente no backup validado."
                        } else {
                            validateBackupProductSplit(bProd, bParts)
                        }
                        if (splitError != null) {
                            blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    message = splitError,
                                ),
                            )
                        } else {
                            val refOk = checkProductPersonDependencies(
                                product = bProd!!,
                                participants = bParts,
                                personAvailability = personAvailability,
                                pendingDependencies = pendingDependencies,
                                blockedOperations = blockedOperations,
                            )
                            if (refOk) {
                                eligibleUnits.add(
                                    EligibleMergeUnit(
                                        unitType = MergeUnitType.PRODUCT,
                                        unitId = productId,
                                        disposition = EligibleOperationDisposition.ADD_NEW,
                                        memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                                    ),
                                )
                            }
                        }
                    }
                    MergePlanAction.CONFLICT -> {
                        val unitConflicts = unitConflictsMap[productId].orEmpty()
                        val unitResolution = evaluateAtomicUnitResolution(
                            unitType = MergeUnitType.PRODUCT,
                            unitId = productId,
                            unitConflicts = unitConflicts,
                            resolvedConflictsByKey = resolvedConflictsByKey,
                            incompatibleDecisions = incompatibleDecisions,
                        )
                        when (unitResolution) {
                            null -> Unit
                            MergeConflictResolution.KEEP_LOCAL,
                            MergeConflictResolution.IGNORE_IMPORT -> {
                                // Unidade atômica preservada localmente (nenhuma inclusão parcial de participante)
                                eligibleUnits.add(
                                    EligibleMergeUnit(
                                        unitType = MergeUnitType.PRODUCT,
                                        unitId = productId,
                                        disposition = EligibleOperationDisposition.SKIP_BACKUP,
                                        memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                                    ),
                                )
                            }
                            MergeConflictResolution.USE_BACKUP -> {
                                val bProd = backupProductsById[productId]
                                val bParts = backupPartsByProd[productId].orEmpty().sortedBy { it.personId }
                                val splitError = if (bProd == null) {
                                    "Produto '$productId' ausente no backup validado."
                                } else {
                                    validateBackupProductSplit(bProd, bParts)
                                }
                                if (splitError != null) {
                                    blockedOperations.add(
                                        DecisionValidationIssue(
                                            type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                            collection = BackupCollectionType.PRODUCTS,
                                            recordId = productId,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            message = splitError,
                                        ),
                                    )
                                } else {
                                    val refOk = checkProductPersonDependencies(
                                        product = bProd!!,
                                        participants = bParts,
                                        personAvailability = personAvailability,
                                        pendingDependencies = pendingDependencies,
                                        blockedOperations = blockedOperations,
                                    )
                                    if (refOk) {
                                        eligibleUnits.add(
                                            EligibleMergeUnit(
                                                unitType = MergeUnitType.PRODUCT,
                                                unitId = productId,
                                                disposition = EligibleOperationDisposition.APPLY_BACKUP,
                                                memberRecordIdsByCollection = mapOf(
                                                    BackupCollectionType.PRODUCTS to listOf(productId),
                                                    BackupCollectionType.PRODUCT_PARTICIPANTS to bParts.map { "${it.productId}:${it.personId}" },
                                                ),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // Captura eventuais participantes órfãos bloqueados fora de unidades de produto
        resolvedPlan.planFor(BackupCollectionType.PRODUCT_PARTICIPANTS).records
            .filter { it.action == MergePlanAction.BLOCKED && it.relatedUnitId == null }
            .forEach { orphan ->
                blockedOperations.add(
                    DecisionValidationIssue(
                        type = DecisionValidationIssueType.UNRESOLVED_DEPENDENCY,
                        collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                        recordId = orphan.recordId,
                        message = orphan.reason ?: "Participante de produto órfão '${orphan.recordId}'.",
                    ),
                )
            }
    }

    private fun checkProductPersonDependencies(
        product: ProductDto,
        participants: List<ProductParticipantDto>,
        personAvailability: Map<String, PersonAvailabilityStatus>,
        pendingDependencies: MutableList<PendingDependencyDetail>,
        blockedOperations: MutableList<DecisionValidationIssue>,
    ): Boolean {
        val referencedIds = buildSet {
            participants.forEach { add(it.personId) }
            if (product.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL.storageKey &&
                !product.feeOwnerPersonId.isNullOrBlank()
            ) {
                add(product.feeOwnerPersonId)
            }
        }.sorted()

        var allResolvedAndAvailable = true
        referencedIds.forEach { personId ->
            when (personAvailability[personId]) {
                PersonAvailabilityStatus.AVAILABLE -> Unit
                PersonAvailabilityStatus.UNRESOLVED_CONFLICT -> {
                    allResolvedAndAvailable = false
                    pendingDependencies.add(
                        PendingDependencyDetail(
                            collection = BackupCollectionType.PRODUCTS,
                            recordId = product.id,
                            dependsOnCollection = BackupCollectionType.PEOPLE,
                            dependsOnRecordId = personId,
                            reason = "Produto '${product.id}' aguarda resolução explícita da pessoa '$personId'.",
                        ),
                    )
                }
                PersonAvailabilityStatus.BLOCKED_COLLISION,
                PersonAvailabilityStatus.SKIPPED_DISTINCT_ID,
                null -> {
                    allResolvedAndAvailable = false
                    blockedOperations.add(
                        DecisionValidationIssue(
                            type = DecisionValidationIssueType.UNRESOLVED_DEPENDENCY,
                            collection = BackupCollectionType.PRODUCTS,
                            recordId = product.id,
                            relatedUnitType = MergeUnitType.PRODUCT,
                            relatedUnitId = product.id,
                            message = "Produto '${product.id}' referencia pessoa '$personId' que não estará disponível sem remapeamento.",
                        ),
                    )
                }
            }
        }
        return allResolvedAndAvailable
    }

    private fun validateMachineUnits(
        resolvedPlan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        localSnapshot: JsonBackupSnapshot,
        resolvedConflictsByKey: Map<String, MergeConflictItem>,
        incompatibleDecisions: MutableList<DecisionValidationIssue>,
        blockedOperations: MutableList<DecisionValidationIssue>,
        eligibleUnits: MutableList<EligibleMergeUnit>,
    ) {
        val backupMachinesById = validatedBackup.machines.associateBy { it.id }
        val backupBrandsByMach = validatedBackup.machineBrandRates.groupBy { it.machineId }
        val backupInstsByMach = validatedBackup.machineInstallmentRates.groupBy { it.machineId }
        val localMachinesByNorm = localSnapshot.machines.associateBy { it.normalizedName }
        val unitConflictsMap = resolvedPlan.conflictItems
            .filter { it.relatedUnitType == MergeUnitType.MACHINE && it.relatedUnitId != null }
            .groupBy { it.relatedUnitId!! }

        resolvedPlan.relatedUnits
            .filter { it.unitType == MergeUnitType.MACHINE }
            .forEach { unit ->
                val machineId = unit.unitId
                when (unit.action) {
                    MergePlanAction.KEEP -> {
                        eligibleUnits.add(
                            EligibleMergeUnit(
                                unitType = MergeUnitType.MACHINE,
                                unitId = machineId,
                                disposition = EligibleOperationDisposition.KEEP_EXISTING,
                                memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                            ),
                        )
                    }
                    MergePlanAction.BLOCKED -> {
                        val reasons = unit.blockReasons.ifEmpty {
                            listOf("Maquininha '$machineId' bloqueada por inconsistência de taxas no plano.")
                        }
                        reasons.forEach { msg ->
                            blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    message = msg,
                                ),
                            )
                        }
                    }
                    MergePlanAction.ADD,
                    MergePlanAction.PENDING_DEPENDENCY -> {
                        val bMach = backupMachinesById[machineId]
                        val bBrands = backupBrandsByMach[machineId].orEmpty().sortedBy { it.brandKey }
                        val bInsts = backupInstsByMach[machineId].orEmpty().sortedBy { it.installments }
                        val err = if (bMach == null) {
                            "Maquininha '$machineId' ausente no backup validado."
                        } else {
                            validateBackupMachineRates(bMach, bBrands, bInsts)
                        }
                        val colliding = bMach?.let { localMachinesByNorm[it.normalizedName] }?.takeIf { it.id != machineId }
                        when {
                            err != null -> blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    message = err,
                                ),
                            )
                            colliding != null -> blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED,
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    message = "Maquininha '$machineId' colide em normalizedName com '${colliding.id}'.",
                                ),
                            )
                            else -> eligibleUnits.add(
                                EligibleMergeUnit(
                                    unitType = MergeUnitType.MACHINE,
                                    unitId = machineId,
                                    disposition = EligibleOperationDisposition.ADD_NEW,
                                    memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                                ),
                            )
                        }
                    }
                    MergePlanAction.CONFLICT -> {
                        val unitConflicts = unitConflictsMap[machineId].orEmpty()
                        val unitResolution = evaluateAtomicUnitResolution(
                            unitType = MergeUnitType.MACHINE,
                            unitId = machineId,
                            unitConflicts = unitConflicts,
                            resolvedConflictsByKey = resolvedConflictsByKey,
                            incompatibleDecisions = incompatibleDecisions,
                        )
                        when (unitResolution) {
                            null -> Unit
                            MergeConflictResolution.KEEP_LOCAL,
                            MergeConflictResolution.IGNORE_IMPORT -> {
                                eligibleUnits.add(
                                    EligibleMergeUnit(
                                        unitType = MergeUnitType.MACHINE,
                                        unitId = machineId,
                                        disposition = EligibleOperationDisposition.SKIP_BACKUP,
                                        memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                                    ),
                                )
                            }
                            MergeConflictResolution.USE_BACKUP -> {
                                val machConflict = unitConflicts.firstOrNull {
                                    it.collection == BackupCollectionType.MACHINES && it.recordId == machineId
                                }
                                val bMach = backupMachinesById[machineId]
                                val bBrands = backupBrandsByMach[machineId].orEmpty().sortedBy { it.brandKey }
                                val bInsts = backupInstsByMach[machineId].orEmpty().sortedBy { it.installments }
                                val colliding = bMach?.let { localMachinesByNorm[it.normalizedName] }
                                    ?.takeIf { it.id != machineId }

                                if (machConflict?.kind == MergeConflictKind.IDENTITY_NAME_COLLISION ||
                                    machConflict?.collidingLocalRecordId != null ||
                                    colliding != null
                                ) {
                                    val targetId = machConflict?.collidingLocalRecordId ?: colliding?.id
                                    blockedOperations.add(
                                        DecisionValidationIssue(
                                            type = DecisionValidationIssueType.IDENTITY_NAME_COLLISION_BLOCKED,
                                            collection = BackupCollectionType.MACHINES,
                                            recordId = machineId,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                            message = "Decisão USE_BACKUP para maquininha '$machineId' bloqueada: colisão de normalizedName com '$targetId' exigiria remapeamento não permitido.",
                                        ),
                                    )
                                } else {
                                    val rateErr = if (bMach == null) {
                                        "Maquininha '$machineId' ausente no backup validado."
                                    } else {
                                        validateBackupMachineRates(bMach, bBrands, bInsts)
                                    }
                                    if (rateErr != null) {
                                        blockedOperations.add(
                                            DecisionValidationIssue(
                                                type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                                collection = BackupCollectionType.MACHINES,
                                                recordId = machineId,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                message = rateErr,
                                            ),
                                        )
                                    } else {
                                        eligibleUnits.add(
                                            EligibleMergeUnit(
                                                unitType = MergeUnitType.MACHINE,
                                                unitId = machineId,
                                                disposition = EligibleOperationDisposition.APPLY_BACKUP,
                                                memberRecordIdsByCollection = mapOf(
                                                    BackupCollectionType.MACHINES to listOf(machineId),
                                                    BackupCollectionType.MACHINE_BRAND_RATES to bBrands.map { "${it.machineId}:${it.brandKey}" },
                                                    BackupCollectionType.MACHINE_INSTALLMENT_RATES to bInsts.map { "${it.machineId}:${it.installments}" },
                                                ),
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun validateSaleUnits(
        resolvedPlan: JsonBackupMergePlanResult,
        validatedBackup: ValidatedBackupData,
        resolvedConflictsByKey: Map<String, MergeConflictItem>,
        incompatibleDecisions: MutableList<DecisionValidationIssue>,
        blockedOperations: MutableList<DecisionValidationIssue>,
        eligibleUnits: MutableList<EligibleMergeUnit>,
    ) {
        val backupSalesById = validatedBackup.sales.associateBy { it.id }
        val backupItemsBySale = validatedBackup.saleItems.groupBy { it.saleId }
        val backupSipsByItem = validatedBackup.saleItemParticipants.groupBy { it.saleItemId }
        val backupTotalsBySale = validatedBackup.salePersonTotals.groupBy { it.saleId }
        val unitConflictsMap = resolvedPlan.conflictItems
            .filter { it.relatedUnitType == MergeUnitType.SALE && it.relatedUnitId != null }
            .groupBy { it.relatedUnitId!! }

        resolvedPlan.relatedUnits
            .filter { it.unitType == MergeUnitType.SALE }
            .forEach { unit ->
                val saleId = unit.unitId
                when (unit.action) {
                    MergePlanAction.KEEP -> {
                        eligibleUnits.add(
                            EligibleMergeUnit(
                                unitType = MergeUnitType.SALE,
                                unitId = saleId,
                                disposition = EligibleOperationDisposition.KEEP_EXISTING,
                                memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                            ),
                        )
                    }
                    MergePlanAction.BLOCKED -> {
                        val reasons = unit.blockReasons.ifEmpty {
                            listOf("Venda '$saleId' bloqueada por inconsistência financeira no plano.")
                        }
                        reasons.forEach { msg ->
                            blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                    collection = BackupCollectionType.SALES,
                                    recordId = saleId,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    message = msg,
                                ),
                            )
                        }
                    }
                    MergePlanAction.ADD,
                    MergePlanAction.PENDING_DEPENDENCY -> {
                        val bSale = backupSalesById[saleId]
                        val bItems = backupItemsBySale[saleId].orEmpty().sortedBy { it.id }
                        val bSips = bItems.flatMap { backupSipsByItem[it.id].orEmpty() }
                        val bTotals = backupTotalsBySale[saleId].orEmpty().sortedBy { it.personId }
                        val err = if (bSale == null) {
                            "Venda '$saleId' ausente no backup validado."
                        } else {
                            validateBackupSaleUnit(bSale, bItems, bSips, bTotals)
                        }
                        if (err != null) {
                            blockedOperations.add(
                                DecisionValidationIssue(
                                    type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                    collection = BackupCollectionType.SALES,
                                    recordId = saleId,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    message = err,
                                ),
                            )
                        } else {
                            // Vendas históricas válidas não dependem da presença atual dos cadastros
                            eligibleUnits.add(
                                EligibleMergeUnit(
                                    unitType = MergeUnitType.SALE,
                                    unitId = saleId,
                                    disposition = EligibleOperationDisposition.ADD_NEW,
                                    memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                                ),
                            )
                        }
                    }
                    MergePlanAction.CONFLICT -> {
                        val unitConflicts = unitConflictsMap[saleId].orEmpty()
                        val unitResolution = evaluateAtomicUnitResolution(
                            unitType = MergeUnitType.SALE,
                            unitId = saleId,
                            unitConflicts = unitConflicts,
                            resolvedConflictsByKey = resolvedConflictsByKey,
                            incompatibleDecisions = incompatibleDecisions,
                        )
                        when (unitResolution) {
                            null -> Unit
                            MergeConflictResolution.KEEP_LOCAL,
                            MergeConflictResolution.IGNORE_IMPORT -> {
                                eligibleUnits.add(
                                    EligibleMergeUnit(
                                        unitType = MergeUnitType.SALE,
                                        unitId = saleId,
                                        disposition = EligibleOperationDisposition.SKIP_BACKUP,
                                        memberRecordIdsByCollection = unit.memberRecordIdsByCollection,
                                    ),
                                )
                            }
                            MergeConflictResolution.USE_BACKUP -> {
                                val bSale = backupSalesById[saleId]
                                val bItems = backupItemsBySale[saleId].orEmpty().sortedBy { it.id }
                                val bSips = bItems.flatMap { backupSipsByItem[it.id].orEmpty() }
                                val bTotals = backupTotalsBySale[saleId].orEmpty().sortedBy { it.personId }
                                val err = if (bSale == null) {
                                    "Venda '$saleId' ausente no backup validado."
                                } else {
                                    validateBackupSaleUnit(bSale, bItems, bSips, bTotals)
                                }
                                if (err != null) {
                                    blockedOperations.add(
                                        DecisionValidationIssue(
                                            type = DecisionValidationIssueType.FINANCIAL_OR_RATE_VALIDATION_ERROR,
                                            collection = BackupCollectionType.SALES,
                                            recordId = saleId,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            message = err,
                                        ),
                                    )
                                } else {
                                    eligibleUnits.add(
                                        EligibleMergeUnit(
                                            unitType = MergeUnitType.SALE,
                                            unitId = saleId,
                                            disposition = EligibleOperationDisposition.APPLY_BACKUP,
                                            memberRecordIdsByCollection = mapOf(
                                                BackupCollectionType.SALES to listOf(saleId),
                                                BackupCollectionType.SALE_ITEMS to bItems.map { it.id },
                                                BackupCollectionType.SALE_ITEM_PARTICIPANTS to bSips.map { "${it.saleItemId}:${it.personId}" }.sorted(),
                                                BackupCollectionType.SALE_PERSON_TOTALS to bTotals.map { "${it.saleId}:${it.personId}" },
                                            ),
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }

    private fun evaluateAtomicUnitResolution(
        unitType: MergeUnitType,
        unitId: String,
        unitConflicts: List<MergeConflictItem>,
        resolvedConflictsByKey: Map<String, MergeConflictItem>,
        incompatibleDecisions: MutableList<DecisionValidationIssue>,
    ): MergeConflictResolution? {
        if (unitConflicts.isEmpty()) return null

        val items = unitConflicts.map { conflict ->
            resolvedConflictsByKey["${conflict.collection.key}:${conflict.recordId}"] ?: conflict
        }
        val decided = items.filter { it.selectedResolution != null }
        val undecided = items.filter { it.selectedResolution == null }
        val distinctResolutions = decided.mapNotNull { it.selectedResolution }.distinct()

        return when {
            distinctResolutions.size > 1 -> {
                incompatibleDecisions.add(
                    DecisionValidationIssue(
                        type = DecisionValidationIssueType.INCOMPATIBLE_UNIT_DECISION,
                        relatedUnitType = unitType,
                        relatedUnitId = unitId,
                        message = "Decisões incompatíveis dentro da unidade atômica $unitType '$unitId': ${distinctResolutions.joinToString()}.",
                    ),
                )
                null
            }
            decided.isNotEmpty() && undecided.isNotEmpty() -> {
                incompatibleDecisions.add(
                    DecisionValidationIssue(
                        type = DecisionValidationIssueType.INCOMPLETE_UNIT_DECISION,
                        relatedUnitType = unitType,
                        relatedUnitId = unitId,
                        message = "Decisão incompleta na unidade atômica $unitType '$unitId': todos os membros em conflito exigem a mesma escolha.",
                    ),
                )
                null
            }
            undecided.isNotEmpty() -> null
            else -> distinctResolutions.single()
        }
    }

    private fun validateBackupProductSplit(
        product: ProductDto,
        participants: List<ProductParticipantDto>,
    ): String? {
        if (product.name.isBlank()) {
            return "Produto '${product.id}' possui nome em branco."
        }
        if (product.priceCents <= 0L) {
            return "Produto '${product.id}' possui preço inválido (${product.priceCents})."
        }
        if (participants.isEmpty()) {
            return "Produto '${product.id}' não possui participantes vinculados."
        }
        val seenPeople = HashSet<String>(participants.size)
        var sum = 0L
        try {
            participants.forEach { p ->
                if (!seenPeople.add(p.personId)) {
                    return "Produto '${product.id}' possui participante duplicado '${p.personId}'."
                }
                if (p.amountCents < 0L || p.sortOrder < 0) {
                    return "Participante '${p.personId}' do produto '${product.id}' possui valor ou ordem inválida."
                }
                sum = MonetaryRounding.safeAdd(sum, p.amountCents)
            }
        } catch (ex: FinancialOverflowException) {
            return "Estouro numérico na soma dos participantes do produto '${product.id}': ${ex.message}"
        }
        if (sum != product.priceCents) {
            return "Soma dos participantes ($sum) difere do preço (${product.priceCents}) do produto '${product.id}'."
        }
        if (product.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL.storageKey) {
            val ownerId = product.feeOwnerPersonId?.takeIf { it.isNotBlank() }
                ?: return "Produto '${product.id}' em modo INDIVIDUAL exige responsável pela tarifa."
            if (!seenPeople.contains(ownerId)) {
                return "Responsável '$ownerId' não pertence aos participantes do produto '${product.id}'."
            }
        } else if (product.feeOwnerPersonId != null) {
            return "Produto '${product.id}' fora do modo INDIVIDUAL não pode definir feeOwnerPersonId."
        }
        return null
    }

    private fun validateBackupMachineRates(
        machine: MachineDto,
        brandRates: List<MachineBrandRateDto>,
        installmentRates: List<MachineInstallmentRateDto>,
    ): String? {
        if (machine.name.isBlank() || machine.normalizedName.isBlank()) {
            return "Maquininha '${machine.id}' possui nome inválido."
        }
        if (machine.debitRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS ||
            machine.creditDefaultRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS
        ) {
            return "Maquininha '${machine.id}' possui taxa base fora do intervalo permitido."
        }
        if (machine.maxInstallments != null && machine.maxInstallments !in 1..24) {
            return "Maquininha '${machine.id}' possui limite máximo de parcelas inválido (${machine.maxInstallments})."
        }
        val seenBrands = HashSet<String>(brandRates.size)
        val seenNormalizedCustomNames = HashSet<String>()
        brandRates.forEach { br ->
            if (!seenBrands.add(br.brandKey)) {
                return "Bandeira duplicada '${br.brandKey}' na maquininha '${machine.id}'."
            }
            if (!CustomBrandKeyCodec.isKnownBrandKey(br.brandKey)) {
                return "Bandeira inválida '${br.brandKey}' na maquininha '${machine.id}'."
            }
            if (CardBrand.fromStorageKey(br.brandKey) == null) {
                val decodedName = CustomBrandKeyCodec.decodeNameOrNull(br.brandKey)
                    ?: return "Bandeira personalizada inválida '${br.brandKey}' na maquininha '${machine.id}'."
                val normalizedCustom = CustomBrandKeyCodec.normalizeBrandName(decodedName)
                val collidesWithStandard = CardBrand.entries.any {
                    CustomBrandKeyCodec.normalizeBrandName(it.label) == normalizedCustom ||
                        CustomBrandKeyCodec.normalizeBrandName(it.storageKey) == normalizedCustom
                }
                if (collidesWithStandard) {
                    return "Bandeira personalizada '$decodedName' colide com bandeira padrão na maquininha '${machine.id}'."
                }
                if (!seenNormalizedCustomNames.add(normalizedCustom)) {
                    return "Bandeira personalizada duplicada '$decodedName' na maquininha '${machine.id}'."
                }
            }
            if (br.rateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                return "Taxa da bandeira '${br.brandKey}' fora do intervalo na maquininha '${machine.id}'."
            }
        }
        val seenInstallments = HashSet<Int>(installmentRates.size)
        installmentRates.forEach { ir ->
            if (!seenInstallments.add(ir.installments)) {
                return "Parcelamento duplicado (${ir.installments}x) na maquininha '${machine.id}'."
            }
            if (ir.installments !in InstallmentTiers.SUPPORTED) {
                return "Parcelamento não suportado (${ir.installments}x) na maquininha '${machine.id}'."
            }
            if (ir.addonBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                return "Adicional de ${ir.installments}x fora do intervalo na maquininha '${machine.id}'."
            }
            val enforceSumLimit = (machine.creditEnabled && machine.installmentsEnabled) || ir.addonBps > 0
            if (enforceSumLimit) {
                if (machine.creditDefaultRateBps + ir.addonBps > RateFormatter.MAX_RATE_BPS) {
                    return "Soma de crédito padrão + adicional ${ir.installments}x ultrapassa 100,00% na maquininha '${machine.id}'."
                }
                brandRates.forEach { br ->
                    val creditRate = br.rateBps ?: machine.creditDefaultRateBps
                    if (creditRate + ir.addonBps > RateFormatter.MAX_RATE_BPS) {
                        return "Soma de bandeira '${br.brandKey}' + adicional ${ir.installments}x ultrapassa 100,00% na maquininha '${machine.id}'."
                    }
                }
            }
        }
        return null
    }

    private fun validateBackupSaleUnit(
        sale: SaleDto,
        items: List<SaleItemDto>,
        sips: List<SaleItemParticipantDto>,
        personTotals: List<SalePersonTotalDto>,
    ): String? {
        val method = SalePaymentMethod.fromStorageKey(sale.paymentMethod)
            ?: return "Forma de pagamento inválida na venda '${sale.id}': ${sale.paymentMethod}."
        if (sale.discountMode != "PROPORTIONAL" && sale.discountMode != "SPECIFIC_PRODUCT") {
            return "Modo de desconto inválido na venda '${sale.id}': ${sale.discountMode}."
        }
        if (sale.discountMode == "PROPORTIONAL" &&
            (sale.discountTargetProductId != null || sale.discountTargetProductName != null)
        ) {
            return "Venda '${sale.id}' em modo PROPORTIONAL não pode definir produto alvo."
        }
        if (!method.usesMachine) {
            val hasAnyMachineField = sale.machineId != null ||
                sale.machineName != null ||
                sale.machineDebitRateBps != null ||
                sale.machineCreditDefaultRateBps != null ||
                sale.machineBrandRatesEnabled != null ||
                sale.machineInstallmentsEnabled != null ||
                sale.cardBrand != null ||
                sale.installments != null ||
                sale.automaticRateBps != null ||
                sale.manualRateOverrideBps != null
            if (hasAnyMachineField) {
                return "Venda '${sale.id}' em ${method.label} não pode conter metadados de maquininha."
            }
        } else {
            if (sale.machineId.isNullOrBlank() ||
                sale.machineName.isNullOrBlank() ||
                sale.machineDebitRateBps == null ||
                sale.machineCreditDefaultRateBps == null ||
                sale.machineBrandRatesEnabled == null ||
                sale.machineInstallmentsEnabled == null ||
                sale.automaticRateBps == null
            ) {
                return "Venda '${sale.id}' em ${method.label} possui snapshot de maquininha incompleto."
            }
            if (sale.cardBrand != null && !CustomBrandKeyCodec.isKnownBrandKey(sale.cardBrand)) {
                return "Bandeira inválida '${sale.cardBrand}' na venda '${sale.id}'."
            }
        }

        val seenProductIds = HashSet<String>(items.size)
        items.forEach { item ->
            if (!seenProductIds.add(item.productId)) {
                return "Produto '${item.productId}' duplicado na venda '${sale.id}'."
            }
            if (FeeResponsibilityMode.entries.none { it.storageKey == item.feeResponsibilityMode }) {
                return "Modo de tarifa inválido no item '${item.id}' da venda '${sale.id}'."
            }
        }

        val sipsByItem = sips.groupBy { it.saleItemId }
        val saleEntity = SaleEntity(
            id = sale.id,
            soldAtEpochMs = sale.soldAtEpochMs,
            updatedAtEpochMs = sale.updatedAtEpochMs,
            paymentMethod = sale.paymentMethod,
            originalSubtotalCents = sale.originalSubtotalCents,
            discountTotalCents = sale.discountTotalCents,
            finalAmountCents = sale.finalAmountCents,
            feeTotalCents = sale.feeTotalCents,
            netTotalCents = sale.netTotalCents,
            discountMode = sale.discountMode,
            discountTargetProductId = sale.discountTargetProductId,
            discountTargetProductName = sale.discountTargetProductName,
            machineId = sale.machineId,
            machineName = sale.machineName,
            machineDebitRateBps = sale.machineDebitRateBps,
            machineCreditDefaultRateBps = sale.machineCreditDefaultRateBps,
            machineBrandRatesEnabled = sale.machineBrandRatesEnabled,
            machineInstallmentsEnabled = sale.machineInstallmentsEnabled,
            cardBrand = sale.cardBrand,
            installments = sale.installments,
            automaticRateBps = sale.automaticRateBps,
            manualRateOverrideBps = sale.manualRateOverrideBps,
            effectiveFeeRateBps = sale.effectiveFeeRateBps,
            note = sale.note,
        )
        val details = SaleWithDetails(
            sale = saleEntity,
            items = items.map { item ->
                SaleItemWithParticipants(
                    item = SaleItemEntity(
                        id = item.id,
                        saleId = item.saleId,
                        sortOrder = item.sortOrder,
                        productId = item.productId,
                        productName = item.productName,
                        quantity = item.quantity,
                        unitPriceOriginalCents = item.unitPriceOriginalCents,
                        originalSubtotalCents = item.originalSubtotalCents,
                        discountAmountCents = item.discountAmountCents,
                        finalAmountCents = item.finalAmountCents,
                        appliedFeeRateBps = item.appliedFeeRateBps,
                        feeAmountCents = item.feeAmountCents,
                        netAmountCents = item.netAmountCents,
                        feeResponsibilityMode = item.feeResponsibilityMode,
                        feeOwnerPersonId = item.feeOwnerPersonId,
                        feeOwnerPersonName = item.feeOwnerPersonName,
                    ),
                    participants = sipsByItem[item.id].orEmpty().map { sip ->
                        SaleItemParticipantEntity(
                            saleItemId = sip.saleItemId,
                            personId = sip.personId,
                            sortOrder = sip.sortOrder,
                            personName = sip.personName,
                            personColorIndex = sip.personColorIndex,
                            originalUnitAmountCents = sip.originalUnitAmountCents,
                            originalSubtotalCents = sip.originalSubtotalCents,
                            discountAmountCents = sip.discountAmountCents,
                            grossAmountCents = sip.grossAmountCents,
                            feeAmountCents = sip.feeAmountCents,
                            netAmountCents = sip.netAmountCents,
                        )
                    },
                )
            },
            personTotals = personTotals.map { t ->
                SalePersonTotalEntity(
                    saleId = t.saleId,
                    personId = t.personId,
                    sortOrder = t.sortOrder,
                    personName = t.personName,
                    personColorIndex = t.personColorIndex,
                    grossAmountCents = t.grossAmountCents,
                    feeAmountCents = t.feeAmountCents,
                    netAmountCents = t.netAmountCents,
                )
            },
        )
        return try {
            val domainSale = SaleEntityMapper.toDomain(details)
            SalePersistenceValidator.validateForPersistence(domainSale.calculation)
            null
        } catch (ex: SalePersistenceException) {
            "Inconsistência financeira na venda '${sale.id}': ${ex.message}"
        } catch (ex: IllegalArgumentException) {
            "Inconsistência na venda '${sale.id}': ${ex.message}"
        } catch (ex: IllegalStateException) {
            "Inconsistência na venda '${sale.id}': ${ex.message}"
        }
    }
}
