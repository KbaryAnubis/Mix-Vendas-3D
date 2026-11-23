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
import java.security.MessageDigest

object JsonBackupMergePlanner {

    private val STANDARD_RESOLUTIONS = listOf(
        MergeConflictResolution.KEEP_LOCAL,
        MergeConflictResolution.USE_BACKUP,
        MergeConflictResolution.IGNORE_IMPORT,
    )

    suspend fun plan(
        readResult: JsonBackupReadResult,
        database: AppDatabase,
    ): JsonBackupMergePlanResult {
        require(readResult.isValid && readResult.validatedData != null) {
            "O planejamento de mesclagem exige um JsonBackupReadResult válido da Etapa 6B-1."
        }
        return plan(readResult.validatedData, database)
    }

    suspend fun plan(
        validatedBackup: ValidatedBackupData,
        database: AppDatabase,
    ): JsonBackupMergePlanResult {
        val localSnapshot = JsonBackupExporter.captureConsistentSnapshot(database)
        val comparison = JsonBackupComparator.compareSnapshots(validatedBackup, localSnapshot)
        return planFromSnapshots(
            validatedBackup = validatedBackup,
            localSnapshot = localSnapshot,
            comparison = comparison,
        )
    }

    internal fun planFromSnapshots(
        validatedBackup: ValidatedBackupData,
        localSnapshot: JsonBackupSnapshot,
        comparison: BackupComparisonResult = JsonBackupComparator.compareSnapshots(validatedBackup, localSnapshot),
    ): JsonBackupMergePlanResult {
        val conflictItems = ArrayList<MergeConflictItem>()
        val pendingDependencies = ArrayList<PendingDependencyDetail>()
        val blockReasons = ArrayList<MergeBlockReason>()
        val relatedUnits = ArrayList<RelatedMergeUnit>()

        // 1) Preferences
        val preferencesPlan = planPreferences(
            comparison = comparison.summaryFor(BackupCollectionType.PREFERENCES),
            conflictItems = conflictItems,
        )

        // 2) People (com detecção de colisão de normalizedName)
        val peoplePlan = planPeople(
            backup = validatedBackup,
            local = localSnapshot,
            comparison = comparison.summaryFor(BackupCollectionType.PEOPLE),
            conflictItems = conflictItems,
        )

        // 2.5) ProductCategories (categorias principais antes de subcategorias + colisão de normalizedName)
        val productCategoriesPlan = planProductCategories(
            backup = validatedBackup,
            local = localSnapshot,
            comparison = comparison.summaryFor(BackupCollectionType.PRODUCT_CATEGORIES),
            conflictItems = conflictItems,
            pendingDependencies = pendingDependencies,
            blockReasons = blockReasons,
        )

        // 3) Products + ProductParticipants (unidade relacionada + dependência de people e productCategories)
        val (productsPlan, productParticipantsPlan) = planProductsAndParticipants(
            backup = validatedBackup,
            local = localSnapshot,
            productsComp = comparison.summaryFor(BackupCollectionType.PRODUCTS),
            participantsComp = comparison.summaryFor(BackupCollectionType.PRODUCT_PARTICIPANTS),
            peoplePlan = peoplePlan,
            productCategoriesPlan = productCategoriesPlan,
            conflictItems = conflictItems,
            pendingDependencies = pendingDependencies,
            blockReasons = blockReasons,
            relatedUnits = relatedUnits,
        )

        // 4) Machines + MachineBrandRates + MachineInstallmentRates (unidade relacionada)
        val (machinesPlan, brandRatesPlan, installmentRatesPlan) = planMachinesAndRates(
            backup = validatedBackup,
            local = localSnapshot,
            machinesComp = comparison.summaryFor(BackupCollectionType.MACHINES),
            brandRatesComp = comparison.summaryFor(BackupCollectionType.MACHINE_BRAND_RATES),
            installmentRatesComp = comparison.summaryFor(BackupCollectionType.MACHINE_INSTALLMENT_RATES),
            conflictItems = conflictItems,
            pendingDependencies = pendingDependencies,
            blockReasons = blockReasons,
            relatedUnits = relatedUnits,
        )

        // 5) Sales + SaleItems + SaleItemParticipants + SalePersonTotals (unidade financeira indivisível)
        val (salesPlan, saleItemsPlan, saleItemParticipantsPlan, salePersonTotalsPlan) = planSalesUnits(
            backup = validatedBackup,
            local = localSnapshot,
            salesComp = comparison.summaryFor(BackupCollectionType.SALES),
            itemsComp = comparison.summaryFor(BackupCollectionType.SALE_ITEMS),
            sipsComp = comparison.summaryFor(BackupCollectionType.SALE_ITEM_PARTICIPANTS),
            totalsComp = comparison.summaryFor(BackupCollectionType.SALE_PERSON_TOTALS),
            conflictItems = conflictItems,
            blockReasons = blockReasons,
            relatedUnits = relatedUnits,
        )

        val collectionPlans = listOf(
            preferencesPlan,
            peoplePlan,
            productCategoriesPlan,
            productsPlan,
            productParticipantsPlan,
            machinesPlan,
            brandRatesPlan,
            installmentRatesPlan,
            salesPlan,
            saleItemsPlan,
            saleItemParticipantsPlan,
            salePersonTotalsPlan,
        )

        val sortedUnits = relatedUnits.sortedWith(
            compareBy<RelatedMergeUnit> { it.unitType.ordinal }.thenBy { it.unitId },
        )
        val sortedConflicts = conflictItems.sortedWith(
            compareBy<MergeConflictItem> { it.collection.ordinal }.thenBy { it.recordId },
        )
        val sortedPending = pendingDependencies.sortedWith(
            compareBy<PendingDependencyDetail> { it.collection.ordinal }.thenBy { it.recordId },
        )
        val sortedBlocks = blockReasons.sortedWith(
            compareBy<MergeBlockReason> { it.collection.ordinal }.thenBy { it.recordId },
        )

        val expectedLocalCounts = BackupCollectionType.entries.associateWith { type ->
            comparison.summaryFor(type).localCount
        }
        val unresolvedConflictIds = sortedConflicts.map { "${it.collection.key}:${it.recordId}" }
        val preconditions = RestorationPreconditions(
            expectedLocalStateFingerprint = computeLocalSnapshotFingerprint(localSnapshot),
            expectedLocalCounts = expectedLocalCounts,
            unresolvedConflictIds = unresolvedConflictIds,
            requiresSingleTransaction = true,
            requiresFinalValidationBeforeCommit = true,
            requiresRollbackOnAnyFailure = true,
            canExecuteAdditiveWithoutUserDecisions = unresolvedConflictIds.isEmpty() &&
                sortedPending.isEmpty() &&
                sortedBlocks.isEmpty(),
        )

        return JsonBackupMergePlanResult(
            backupFormat = validatedBackup.format,
            backupSchemaVersion = validatedBackup.backupSchemaVersion,
            applicationId = validatedBackup.applicationId,
            roomSchemaVersion = validatedBackup.roomSchemaVersion,
            exportedAtEpochMs = validatedBackup.exportedAtEpochMs,
            collectionPlans = collectionPlans,
            relatedUnits = sortedUnits,
            conflictItems = sortedConflicts,
            pendingDependencies = sortedPending,
            blockReasons = sortedBlocks,
            preconditions = preconditions,
        )
    }

    private fun planPreferences(
        comparison: CollectionComparisonSummary,
        conflictItems: MutableList<MergeConflictItem>,
    ): CollectionMergePlan {
        val entries = ArrayList<PlannedRecordAction>(comparison.totalDistinctRecords)

        comparison.newRecordIds.forEach { key ->
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PREFERENCES,
                    recordId = key,
                    keyParts = mapOf("key" to key),
                    comparisonStatus = RecordComparisonStatus.NEW,
                    action = MergePlanAction.ADD,
                ),
            )
        }
        comparison.identicalRecordIds.forEach { key ->
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PREFERENCES,
                    recordId = key,
                    keyParts = mapOf("key" to key),
                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                    action = MergePlanAction.KEEP,
                ),
            )
        }
        comparison.conflictingRecords.forEach { conflict ->
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PREFERENCES,
                    recordId = conflict.recordId,
                    keyParts = conflict.keyParts,
                    comparisonStatus = RecordComparisonStatus.CONFLICTING,
                    action = MergePlanAction.CONFLICT,
                    reason = "Preferência existente diverge nos campos: ${conflict.divergentFields.joinToString()}",
                ),
            )
            conflictItems.add(
                MergeConflictItem(
                    collection = BackupCollectionType.PREFERENCES,
                    recordId = conflict.recordId,
                    keyParts = conflict.keyParts,
                    kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                    divergentFields = conflict.divergentFields,
                    hasFinancialOrRateDivergence = false,
                    allowedResolutions = STANDARD_RESOLUTIONS,
                ),
            )
        }
        comparison.localOnlyRecordIds.forEach { key ->
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PREFERENCES,
                    recordId = key,
                    keyParts = mapOf("key" to key),
                    comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                    action = MergePlanAction.KEEP,
                ),
            )
        }

        // Mantém ordenação determinística por recordId
        return CollectionMergePlan(
            collection = BackupCollectionType.PREFERENCES,
            records = entries.sortedBy { it.recordId },
        )
    }

    private fun planPeople(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
        comparison: CollectionComparisonSummary,
        conflictItems: MutableList<MergeConflictItem>,
    ): CollectionMergePlan {
        val backupById = backup.people.associateBy { it.id }
        val localByNormalizedName = local.people.associateBy { it.normalizedName }
        val entries = ArrayList<PlannedRecordAction>(comparison.totalDistinctRecords)

        comparison.newRecordIds.forEach { id ->
            val person = backupById.getValue(id)
            val collidingLocal = localByNormalizedName[person.normalizedName]
            if (collidingLocal != null && collidingLocal.id != person.id) {
                entries.add(
                    PlannedRecordAction(
                        collection = BackupCollectionType.PEOPLE,
                        recordId = id,
                        keyParts = mapOf("id" to id),
                        comparisonStatus = RecordComparisonStatus.NEW,
                        action = MergePlanAction.CONFLICT,
                        reason = "Conflito de identidade: normalizedName já utilizado localmente por '${collidingLocal.id}'.",
                    ),
                )
                conflictItems.add(
                    MergeConflictItem(
                        collection = BackupCollectionType.PEOPLE,
                        recordId = id,
                        keyParts = mapOf("id" to id),
                        kind = MergeConflictKind.IDENTITY_NAME_COLLISION,
                        divergentFields = listOf("id", "normalizedName"),
                        collidingLocalRecordId = collidingLocal.id,
                        hasFinancialOrRateDivergence = false,
                        allowedResolutions = STANDARD_RESOLUTIONS,
                    ),
                )
            } else {
                entries.add(
                    PlannedRecordAction(
                        collection = BackupCollectionType.PEOPLE,
                        recordId = id,
                        keyParts = mapOf("id" to id),
                        comparisonStatus = RecordComparisonStatus.NEW,
                        action = MergePlanAction.ADD,
                    ),
                )
            }
        }

        comparison.identicalRecordIds.forEach { id ->
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PEOPLE,
                    recordId = id,
                    keyParts = mapOf("id" to id),
                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                    action = MergePlanAction.KEEP,
                ),
            )
        }

        comparison.conflictingRecords.forEach { conflict ->
            val person = backupById.getValue(conflict.recordId)
            val collidingLocal = localByNormalizedName[person.normalizedName]?.takeIf { it.id != person.id }
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PEOPLE,
                    recordId = conflict.recordId,
                    keyParts = conflict.keyParts,
                    comparisonStatus = RecordComparisonStatus.CONFLICTING,
                    action = MergePlanAction.CONFLICT,
                    reason = "Pessoa com mesmo ID diverge nos campos: ${conflict.divergentFields.joinToString()}",
                ),
            )
            conflictItems.add(
                MergeConflictItem(
                    collection = BackupCollectionType.PEOPLE,
                    recordId = conflict.recordId,
                    keyParts = conflict.keyParts,
                    kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                    divergentFields = conflict.divergentFields,
                    collidingLocalRecordId = collidingLocal?.id,
                    hasFinancialOrRateDivergence = false,
                    allowedResolutions = STANDARD_RESOLUTIONS,
                ),
            )
        }

        comparison.localOnlyRecordIds.forEach { id ->
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PEOPLE,
                    recordId = id,
                    keyParts = mapOf("id" to id),
                    comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                    action = MergePlanAction.KEEP,
                ),
            )
        }

        return CollectionMergePlan(
            collection = BackupCollectionType.PEOPLE,
            records = entries.sortedBy { it.recordId },
        )
    }

    private fun planProductCategories(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
        comparison: CollectionComparisonSummary,
        conflictItems: MutableList<MergeConflictItem>,
        pendingDependencies: MutableList<PendingDependencyDetail>,
        blockReasons: MutableList<MergeBlockReason>,
    ): CollectionMergePlan {
        val backupById = backup.productCategories.associateBy { it.id }
        val localById = local.productCategories.associateBy { it.id }
        val localByScopeAndNorm = local.productCategories.associateBy { (it.parentId ?: "") to it.normalizedName }
        val entries = ArrayList<PlannedRecordAction>(comparison.totalDistinctRecords)
        val plannedActionById = HashMap<String, MergePlanAction>()

        // 1) Registros idênticos e somente locais são sempre preservados (e ficam disponíveis como pais)
        comparison.identicalRecordIds.forEach { id ->
            plannedActionById[id] = MergePlanAction.KEEP
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PRODUCT_CATEGORIES,
                    recordId = id,
                    keyParts = mapOf("id" to id),
                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                    action = MergePlanAction.KEEP,
                ),
            )
        }
        comparison.localOnlyRecordIds.forEach { id ->
            plannedActionById[id] = MergePlanAction.KEEP
            entries.add(
                PlannedRecordAction(
                    collection = BackupCollectionType.PRODUCT_CATEGORIES,
                    recordId = id,
                    keyParts = mapOf("id" to id),
                    comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                    action = MergePlanAction.KEEP,
                ),
            )
        }

        // 2) Processar categorias conflitantes e novas com categorias principais (parentId == null) ANTES das subcategorias
        val conflictsById = comparison.conflictingRecords.associateBy { it.recordId }
        val newAndConflictingIds = (comparison.newRecordIds + comparison.conflictingRecordIds).sortedWith(
            compareBy<String>({ backupById[it]?.parentId != null }, { backupById[it]?.parentId.orEmpty() }, { it }),
        )

        newAndConflictingIds.forEach { id ->
            val conflict = conflictsById[id]
            val cat = backupById.getValue(id)
            val scopeKey = (cat.parentId ?: "") to cat.normalizedName
            val collidingLocal = localByScopeAndNorm[scopeKey]?.takeIf { it.id != cat.id }

            if (conflict != null) {
                plannedActionById[id] = MergePlanAction.CONFLICT
                entries.add(
                    PlannedRecordAction(
                        collection = BackupCollectionType.PRODUCT_CATEGORIES,
                        recordId = id,
                        keyParts = conflict.keyParts,
                        comparisonStatus = RecordComparisonStatus.CONFLICTING,
                        action = MergePlanAction.CONFLICT,
                        reason = "Categoria com mesmo ID diverge nos campos: ${conflict.divergentFields.joinToString()}",
                    ),
                )
                conflictItems.add(
                    MergeConflictItem(
                        collection = BackupCollectionType.PRODUCT_CATEGORIES,
                        recordId = id,
                        keyParts = conflict.keyParts,
                        kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                        divergentFields = conflict.divergentFields,
                        collidingLocalRecordId = collidingLocal?.id,
                        hasFinancialOrRateDivergence = false,
                        allowedResolutions = STANDARD_RESOLUTIONS,
                    ),
                )
            } else {
                // Registro NEW
                val parentId = cat.parentId
                val parentBlockMsg = when {
                    parentId == null -> null
                    parentId == cat.id -> "Categoria '${cat.id}' não pode referenciar a si mesma como categoria principal."
                    else -> {
                        val bParent = backupById[parentId]
                        val lParent = localById[parentId]
                        when {
                            bParent == null && lParent == null ->
                                "Subcategoria '${cat.id}' referencia categoria principal inexistente '$parentId'."
                            bParent != null && bParent.parentId != null ->
                                "Subcategoria '${cat.id}' referencia '$parentId' que não é categoria principal."
                            bParent == null && lParent != null && lParent.parentId != null ->
                                "Subcategoria '${cat.id}' referencia categoria local '$parentId' que não é categoria principal."
                            else -> null
                        }
                    }
                }

                when {
                    parentBlockMsg != null -> {
                        plannedActionById[id] = MergePlanAction.BLOCKED
                        blockReasons.add(
                            MergeBlockReason(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                reason = parentBlockMsg,
                            ),
                        )
                        entries.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                keyParts = mapOf("id" to id),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = MergePlanAction.BLOCKED,
                                reason = parentBlockMsg,
                            ),
                        )
                    }
                    collidingLocal != null -> {
                        plannedActionById[id] = MergePlanAction.CONFLICT
                        entries.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                keyParts = mapOf("id" to id),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = MergePlanAction.CONFLICT,
                                reason = "Conflito de identidade: categoria '${cat.name}' colide em normalizedName com '${collidingLocal.id}'.",
                            ),
                        )
                        conflictItems.add(
                            MergeConflictItem(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                keyParts = mapOf("id" to id),
                                kind = MergeConflictKind.IDENTITY_NAME_COLLISION,
                                divergentFields = listOf("id", "normalizedName"),
                                collidingLocalRecordId = collidingLocal.id,
                                hasFinancialOrRateDivergence = false,
                                allowedResolutions = STANDARD_RESOLUTIONS,
                            ),
                        )
                    }
                    parentId != null && plannedActionById[parentId] == MergePlanAction.BLOCKED -> {
                        val msg = "Subcategoria '${cat.id}' bloqueada porque a categoria principal '$parentId' está bloqueada."
                        plannedActionById[id] = MergePlanAction.BLOCKED
                        blockReasons.add(
                            MergeBlockReason(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                reason = msg,
                            ),
                        )
                        entries.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                keyParts = mapOf("id" to id),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = MergePlanAction.BLOCKED,
                                reason = msg,
                            ),
                        )
                    }
                    parentId != null &&
                        plannedActionById[parentId] != MergePlanAction.ADD &&
                        plannedActionById[parentId] != MergePlanAction.KEEP -> {
                        val msg = "Subcategoria '${cat.id}' aguarda resolução da categoria principal '$parentId'."
                        plannedActionById[id] = MergePlanAction.PENDING_DEPENDENCY
                        pendingDependencies.add(
                            PendingDependencyDetail(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                dependsOnCollection = BackupCollectionType.PRODUCT_CATEGORIES,
                                dependsOnRecordId = parentId,
                                reason = msg,
                            ),
                        )
                        entries.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                keyParts = mapOf("id" to id),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = MergePlanAction.PENDING_DEPENDENCY,
                                reason = msg,
                            ),
                        )
                    }
                    else -> {
                        plannedActionById[id] = MergePlanAction.ADD
                        entries.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_CATEGORIES,
                                recordId = id,
                                keyParts = mapOf("id" to id),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = MergePlanAction.ADD,
                            ),
                        )
                    }
                }
            }
        }

        return CollectionMergePlan(
            collection = BackupCollectionType.PRODUCT_CATEGORIES,
            records = entries.sortedBy { it.recordId },
        )
    }

    private fun planProductsAndParticipants(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
        productsComp: CollectionComparisonSummary,
        participantsComp: CollectionComparisonSummary,
        peoplePlan: CollectionMergePlan,
        productCategoriesPlan: CollectionMergePlan,
        conflictItems: MutableList<MergeConflictItem>,
        pendingDependencies: MutableList<PendingDependencyDetail>,
        blockReasons: MutableList<MergeBlockReason>,
        relatedUnits: MutableList<RelatedMergeUnit>,
    ): Pair<CollectionMergePlan, CollectionMergePlan> {
        val backupCategoriesById = backup.productCategories.associateBy { it.id }
        val localCategoriesById = local.productCategories.associateBy { it.id }
        val backupProducts = backup.products.associateBy { it.id }
        val localProducts = local.products.associateBy { it.id }
        val backupPartsByProd = backup.productParticipants.groupBy { it.productId }
        val localPartsByProd = local.productParticipants.groupBy { it.productId }

        val prodConflictsById = productsComp.conflictingRecords.associateBy { it.recordId }
        val partConflictsById = participantsComp.conflictingRecords.associateBy { it.recordId }
        val partNewSet = participantsComp.newRecordIds.toHashSet()
        val partIdenticalSet = participantsComp.identicalRecordIds.toHashSet()
        val partLocalOnlySet = participantsComp.localOnlyRecordIds.toHashSet()

        val productActions = ArrayList<PlannedRecordAction>(productsComp.totalDistinctRecords)
        val participantActions = ArrayList<PlannedRecordAction>(participantsComp.totalDistinctRecords)

        val allProductIds = (backupProducts.keys + localProducts.keys).sorted()
        allProductIds.forEach { productId ->
            val bProd = backupProducts[productId]
            val lProd = localProducts[productId]
            val bParts = backupPartsByProd[productId].orEmpty().sortedBy { it.personId }
            val lParts = localPartsByProd[productId].orEmpty().sortedBy { it.personId }

            when {
                // Caso A: Somente local -> preservar integralmente
                bProd == null && lProd != null -> {
                    productActions.add(
                        PlannedRecordAction(
                            collection = BackupCollectionType.PRODUCTS,
                            recordId = productId,
                            keyParts = mapOf("id" to productId),
                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                            action = MergePlanAction.KEEP,
                            relatedUnitType = MergeUnitType.PRODUCT,
                            relatedUnitId = productId,
                        ),
                    )
                    lParts.forEach { lp ->
                        val pid = "${lp.productId}:${lp.personId}"
                        participantActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                recordId = pid,
                                keyParts = mapOf("productId" to lp.productId, "personId" to lp.personId),
                                comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.PRODUCT,
                                relatedUnitId = productId,
                            ),
                        )
                    }
                    relatedUnits.add(
                        RelatedMergeUnit(
                            unitType = MergeUnitType.PRODUCT,
                            unitId = productId,
                            action = MergePlanAction.KEEP,
                            memberRecordIdsByCollection = mapOf(
                                BackupCollectionType.PRODUCTS to listOf(productId),
                                BackupCollectionType.PRODUCT_PARTICIPANTS to lParts.map { "${it.productId}:${it.personId}" },
                            ),
                        ),
                    )
                }

                // Caso B: Produto novo no backup -> validar conjunto, pessoas e categorias
                bProd != null && lProd == null -> {
                    val splitValidationError = validateBackupProductSplit(bProd, bParts)
                    val referencedPersonIds = buildSet {
                        bParts.forEach { add(it.personId) }
                        if (bProd.feeResponsibilityMode == FeeResponsibilityMode.INDIVIDUAL.storageKey &&
                            !bProd.feeOwnerPersonId.isNullOrBlank()
                        ) {
                            add(bProd.feeOwnerPersonId)
                        }
                    }.sorted()

                    val unresolvedPeople = referencedPersonIds.filter { pid ->
                        val act = peoplePlan.actionForRecord(pid)
                        act != MergePlanAction.ADD && act != MergePlanAction.KEEP
                    }
                    val missingPeople = referencedPersonIds.filter { pid ->
                        peoplePlan.actionForRecord(pid) == null
                    }

                    val categoryBlockError = validateBackupProductCategoryRefsForPlan(
                        product = bProd,
                        backupCategoriesById = backupCategoriesById,
                        localCategoriesById = localCategoriesById,
                        productCategoriesPlan = productCategoriesPlan,
                    )
                    val unresolvedCategories = buildList {
                        bProd.categoryId?.let { cid ->
                            val act = productCategoriesPlan.actionForRecord(cid)
                            if (act != null && act != MergePlanAction.ADD && act != MergePlanAction.KEEP && act != MergePlanAction.BLOCKED) {
                                add(cid)
                            }
                        }
                        bProd.subcategoryId?.let { sid ->
                            val act = productCategoriesPlan.actionForRecord(sid)
                            if (act != null && act != MergePlanAction.ADD && act != MergePlanAction.KEEP && act != MergePlanAction.BLOCKED) {
                                add(sid)
                            }
                        }
                    }.distinct().sorted()

                    val unitAction: MergePlanAction
                    val unitReasons = ArrayList<String>()
                    when {
                        splitValidationError != null -> {
                            unitAction = MergePlanAction.BLOCKED
                            unitReasons.add(splitValidationError)
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    reason = splitValidationError,
                                ),
                            )
                        }
                        missingPeople.isNotEmpty() -> {
                            unitAction = MergePlanAction.BLOCKED
                            val msg = "Produto '$productId' referencia pessoa inexistente: ${missingPeople.joinToString()}"
                            unitReasons.add(msg)
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    reason = msg,
                                ),
                            )
                        }
                        categoryBlockError != null -> {
                            unitAction = MergePlanAction.BLOCKED
                            unitReasons.add(categoryBlockError)
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    reason = categoryBlockError,
                                ),
                            )
                        }
                        unresolvedPeople.isNotEmpty() || unresolvedCategories.isNotEmpty() -> {
                            unitAction = MergePlanAction.PENDING_DEPENDENCY
                            unresolvedPeople.forEach { depPersonId ->
                                val msg = "Produto '$productId' aguarda resolução de conflito na pessoa '$depPersonId'."
                                unitReasons.add(msg)
                                pendingDependencies.add(
                                    PendingDependencyDetail(
                                        collection = BackupCollectionType.PRODUCTS,
                                        recordId = productId,
                                        dependsOnCollection = BackupCollectionType.PEOPLE,
                                        dependsOnRecordId = depPersonId,
                                        reason = msg,
                                    ),
                                )
                            }
                            unresolvedCategories.forEach { depCategoryId ->
                                val msg = "Produto '$productId' aguarda resolução de conflito na categoria '$depCategoryId'."
                                unitReasons.add(msg)
                                pendingDependencies.add(
                                    PendingDependencyDetail(
                                        collection = BackupCollectionType.PRODUCTS,
                                        recordId = productId,
                                        dependsOnCollection = BackupCollectionType.PRODUCT_CATEGORIES,
                                        dependsOnRecordId = depCategoryId,
                                        reason = msg,
                                    ),
                                )
                            }
                        }
                        else -> unitAction = MergePlanAction.ADD
                    }

                    productActions.add(
                        PlannedRecordAction(
                            collection = BackupCollectionType.PRODUCTS,
                            recordId = productId,
                            keyParts = mapOf("id" to productId),
                            comparisonStatus = RecordComparisonStatus.NEW,
                            action = unitAction,
                            relatedUnitType = MergeUnitType.PRODUCT,
                            relatedUnitId = productId,
                            reason = unitReasons.firstOrNull(),
                        ),
                    )
                    bParts.forEach { bp ->
                        val pid = "${bp.productId}:${bp.personId}"
                        if (unitAction == MergePlanAction.BLOCKED) {
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                    recordId = pid,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    reason = unitReasons.first(),
                                ),
                            )
                        } else if (unitAction == MergePlanAction.PENDING_DEPENDENCY) {
                            val depCollection: BackupCollectionType
                            val depRecordId: String
                            if (unresolvedPeople.isNotEmpty()) {
                                depCollection = BackupCollectionType.PEOPLE
                                depRecordId = if (bp.personId in unresolvedPeople) bp.personId else unresolvedPeople.first()
                            } else {
                                depCollection = BackupCollectionType.PRODUCT_CATEGORIES
                                depRecordId = unresolvedCategories.first()
                            }
                            pendingDependencies.add(
                                PendingDependencyDetail(
                                    collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                    recordId = pid,
                                    dependsOnCollection = depCollection,
                                    dependsOnRecordId = depRecordId,
                                    reason = "Participante '$pid' aguarda resolução do conjunto do produto '$productId'.",
                                ),
                            )
                        }
                        participantActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                recordId = pid,
                                keyParts = mapOf("productId" to bp.productId, "personId" to bp.personId),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = unitAction,
                                relatedUnitType = MergeUnitType.PRODUCT,
                                relatedUnitId = productId,
                                reason = unitReasons.firstOrNull(),
                            ),
                        )
                    }
                    relatedUnits.add(
                        RelatedMergeUnit(
                            unitType = MergeUnitType.PRODUCT,
                            unitId = productId,
                            action = unitAction,
                            memberRecordIdsByCollection = mapOf(
                                BackupCollectionType.PRODUCTS to listOf(productId),
                                BackupCollectionType.PRODUCT_PARTICIPANTS to bParts.map { "${it.productId}:${it.personId}" },
                            ),
                            dependencyUnitOrRecordIds = unresolvedPeople + unresolvedCategories,
                            blockReasons = if (unitAction == MergePlanAction.BLOCKED) unitReasons else emptyList(),
                        ),
                    )
                }

                // Caso C: Produto presente nos dois lados
                bProd != null && lProd != null -> {
                    val headerConflict = prodConflictsById[productId]
                    val allPartKeys = ((bParts.map { "${it.productId}:${it.personId}" }) +
                        (lParts.map { "${it.productId}:${it.personId}" })).distinct().sorted()
                    val anyPartDiverges = allPartKeys.any { key ->
                        key in partNewSet || key in partLocalOnlySet || partConflictsById.containsKey(key)
                    }

                    if (headerConflict == null && !anyPartDiverges) {
                        // Conjunto 100% idêntico -> MANTER
                        productActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.PRODUCTS,
                                recordId = productId,
                                keyParts = mapOf("id" to productId),
                                comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.PRODUCT,
                                relatedUnitId = productId,
                            ),
                        )
                        allPartKeys.forEach { pk ->
                            val split = pk.split(":")
                            participantActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                    recordId = pk,
                                    keyParts = mapOf("productId" to split[0], "personId" to split[1]),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                ),
                            )
                        }
                        relatedUnits.add(
                            RelatedMergeUnit(
                                unitType = MergeUnitType.PRODUCT,
                                unitId = productId,
                                action = MergePlanAction.KEEP,
                                memberRecordIdsByCollection = mapOf(
                                    BackupCollectionType.PRODUCTS to listOf(productId),
                                    BackupCollectionType.PRODUCT_PARTICIPANTS to allPartKeys,
                                ),
                            ),
                        )
                    } else {
                        // Conjunto divergente: proíbe mesclagem parcial que quebre soma == preço
                        val splitValidationError = validateBackupProductSplit(bProd, bParts)
                        val unitBlockReasons = ArrayList<String>()
                        if (splitValidationError != null) {
                            unitBlockReasons.add(splitValidationError)
                        }

                        if (headerConflict != null) {
                            productActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    keyParts = headerConflict.keyParts,
                                    comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                    action = MergePlanAction.CONFLICT,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    reason = "Produto diverge nos campos: ${headerConflict.divergentFields.joinToString()}",
                                ),
                            )
                            conflictItems.add(
                                MergeConflictItem(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    keyParts = headerConflict.keyParts,
                                    kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                    divergentFields = headerConflict.divergentFields,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    hasFinancialOrRateDivergence = headerConflict.hasFinancialOrRateDivergence,
                                    allowedResolutions = STANDARD_RESOLUTIONS,
                                ),
                            )
                        } else {
                            // Cabeçalho igual, mas participantes divergem -> cabeçalho fica pendente do conjunto
                            val msg = "Produto '$productId' possui divisão de participantes divergente; exige decisão atômica do conjunto."
                            productActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    keyParts = mapOf("id" to productId),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.PENDING_DEPENDENCY,
                                    relatedUnitType = MergeUnitType.PRODUCT,
                                    relatedUnitId = productId,
                                    reason = msg,
                                ),
                            )
                            pendingDependencies.add(
                                PendingDependencyDetail(
                                    collection = BackupCollectionType.PRODUCTS,
                                    recordId = productId,
                                    dependsOnCollection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                    dependsOnRecordId = productId,
                                    reason = msg,
                                ),
                            )
                        }

                        allPartKeys.forEach { pk ->
                            val split = pk.split(":")
                            val partsMap = mapOf("productId" to split[0], "personId" to split[1])
                            val partConflict = partConflictsById[pk]
                            when {
                                partConflict != null -> {
                                    participantActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            keyParts = partsMap,
                                            comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                            action = MergePlanAction.CONFLICT,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            reason = "Divisão do participante diverge no produto '$productId'.",
                                        ),
                                    )
                                    conflictItems.add(
                                        MergeConflictItem(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            keyParts = partsMap,
                                            kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                            divergentFields = partConflict.divergentFields,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            hasFinancialOrRateDivergence = partConflict.hasFinancialOrRateDivergence,
                                            allowedResolutions = STANDARD_RESOLUTIONS,
                                        ),
                                    )
                                }
                                pk in partNewSet -> {
                                    // Adicionar apenas o novo participante ao produto local quebraria soma == preço!
                                    val msg = "Inclusão isolada de '$pk' bloqueada: mesclagem parcial deixaria a soma dos participantes diferente do preço de '$productId'."
                                    unitBlockReasons.add(msg)
                                    blockReasons.add(
                                        MergeBlockReason(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            reason = msg,
                                        ),
                                    )
                                    participantActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            keyParts = partsMap,
                                            comparisonStatus = RecordComparisonStatus.NEW,
                                            action = MergePlanAction.BLOCKED,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            reason = msg,
                                        ),
                                    )
                                }
                                pk in partLocalOnlySet -> {
                                    participantActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            keyParts = partsMap,
                                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            reason = "Participante somente local preservado enquanto o conflito do produto '$productId' não for resolvido.",
                                        ),
                                    )
                                }
                                pk in partIdenticalSet -> {
                                    participantActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            keyParts = partsMap,
                                            comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                            action = MergePlanAction.PENDING_DEPENDENCY,
                                            relatedUnitType = MergeUnitType.PRODUCT,
                                            relatedUnitId = productId,
                                            reason = "Participante vinculado ao conjunto conflitante do produto '$productId'.",
                                        ),
                                    )
                                    pendingDependencies.add(
                                        PendingDependencyDetail(
                                            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                                            recordId = pk,
                                            dependsOnCollection = BackupCollectionType.PRODUCTS,
                                            dependsOnRecordId = productId,
                                            reason = "Aguarda decisão atômica do produto '$productId'.",
                                        ),
                                    )
                                }
                            }
                        }

                        relatedUnits.add(
                            RelatedMergeUnit(
                                unitType = MergeUnitType.PRODUCT,
                                unitId = productId,
                                action = MergePlanAction.CONFLICT,
                                memberRecordIdsByCollection = mapOf(
                                    BackupCollectionType.PRODUCTS to listOf(productId),
                                    BackupCollectionType.PRODUCT_PARTICIPANTS to allPartKeys,
                                ),
                                blockReasons = unitBlockReasons.distinct(),
                                allowedResolutions = STANDARD_RESOLUTIONS,
                            ),
                        )
                    }
                }
            }
        }

        // Captura participantes órfãos (caso algum teste injete participante sem produto correspondente)
        val coveredPartIds = participantActions.mapTo(HashSet()) { it.recordId }
        (participantsComp.newRecordIds + participantsComp.identicalRecordIds +
            participantsComp.conflictingRecordIds + participantsComp.localOnlyRecordIds)
            .filter { it !in coveredPartIds }
            .sorted()
            .forEach { orphanId ->
                val split = orphanId.split(":")
                val msg = "Participante de produto '$orphanId' sem produto pai correspondente."
                blockReasons.add(
                    MergeBlockReason(
                        collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                        recordId = orphanId,
                        reason = msg,
                    ),
                )
                participantActions.add(
                    PlannedRecordAction(
                        collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                        recordId = orphanId,
                        keyParts = mapOf("productId" to split[0], "personId" to split[1]),
                        comparisonStatus = RecordComparisonStatus.NEW,
                        action = MergePlanAction.BLOCKED,
                        reason = msg,
                    ),
                )
            }

        return CollectionMergePlan(
            collection = BackupCollectionType.PRODUCTS,
            records = productActions.sortedBy { it.recordId },
        ) to CollectionMergePlan(
            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
            records = participantActions.sortedBy { it.recordId },
        )
    }

    private fun planMachinesAndRates(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
        machinesComp: CollectionComparisonSummary,
        brandRatesComp: CollectionComparisonSummary,
        installmentRatesComp: CollectionComparisonSummary,
        conflictItems: MutableList<MergeConflictItem>,
        pendingDependencies: MutableList<PendingDependencyDetail>,
        blockReasons: MutableList<MergeBlockReason>,
        relatedUnits: MutableList<RelatedMergeUnit>,
    ): Triple<CollectionMergePlan, CollectionMergePlan, CollectionMergePlan> {
        val backupMachines = backup.machines.associateBy { it.id }
        val localMachines = local.machines.associateBy { it.id }
        val localMachinesByNorm = local.machines.associateBy { it.normalizedName }

        val backupBrandByMachine = backup.machineBrandRates.groupBy { it.machineId }
        val localBrandByMachine = local.machineBrandRates.groupBy { it.machineId }
        val backupInstByMachine = backup.machineInstallmentRates.groupBy { it.machineId }
        val localInstByMachine = local.machineInstallmentRates.groupBy { it.machineId }

        val machConflictById = machinesComp.conflictingRecords.associateBy { it.recordId }
        val brConflictById = brandRatesComp.conflictingRecords.associateBy { it.recordId }
        val brNewSet = brandRatesComp.newRecordIds.toHashSet()
        val brLocalOnlySet = brandRatesComp.localOnlyRecordIds.toHashSet()

        val irConflictById = installmentRatesComp.conflictingRecords.associateBy { it.recordId }
        val irNewSet = installmentRatesComp.newRecordIds.toHashSet()
        val irLocalOnlySet = installmentRatesComp.localOnlyRecordIds.toHashSet()

        val machineActions = ArrayList<PlannedRecordAction>(machinesComp.totalDistinctRecords)
        val brandActions = ArrayList<PlannedRecordAction>(brandRatesComp.totalDistinctRecords)
        val installmentActions = ArrayList<PlannedRecordAction>(installmentRatesComp.totalDistinctRecords)

        val allMachineIds = (backupMachines.keys + localMachines.keys).sorted()
        allMachineIds.forEach { machineId ->
            val bMach = backupMachines[machineId]
            val lMach = localMachines[machineId]
            val bBrands = backupBrandByMachine[machineId].orEmpty().sortedBy { it.brandKey }
            val lBrands = localBrandByMachine[machineId].orEmpty().sortedBy { it.brandKey }
            val bInsts = backupInstByMachine[machineId].orEmpty().sortedBy { it.installments }
            val lInsts = localInstByMachine[machineId].orEmpty().sortedBy { it.installments }

            when {
                // Somente local -> MANTER
                bMach == null && lMach != null -> {
                    machineActions.add(
                        PlannedRecordAction(
                            collection = BackupCollectionType.MACHINES,
                            recordId = machineId,
                            keyParts = mapOf("id" to machineId),
                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                            action = MergePlanAction.KEEP,
                            relatedUnitType = MergeUnitType.MACHINE,
                            relatedUnitId = machineId,
                        ),
                    )
                    lBrands.forEach { br ->
                        val brId = "${br.machineId}:${br.brandKey}"
                        brandActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                recordId = brId,
                                keyParts = mapOf("machineId" to br.machineId, "brandKey" to br.brandKey),
                                comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.MACHINE,
                                relatedUnitId = machineId,
                            ),
                        )
                    }
                    lInsts.forEach { ir ->
                        val irId = "${ir.machineId}:${ir.installments}"
                        installmentActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                recordId = irId,
                                keyParts = mapOf("machineId" to ir.machineId, "installments" to ir.installments.toString()),
                                comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.MACHINE,
                                relatedUnitId = machineId,
                            ),
                        )
                    }
                    relatedUnits.add(
                        RelatedMergeUnit(
                            unitType = MergeUnitType.MACHINE,
                            unitId = machineId,
                            action = MergePlanAction.KEEP,
                            memberRecordIdsByCollection = mapOf(
                                BackupCollectionType.MACHINES to listOf(machineId),
                                BackupCollectionType.MACHINE_BRAND_RATES to lBrands.map { "${it.machineId}:${it.brandKey}" },
                                BackupCollectionType.MACHINE_INSTALLMENT_RATES to lInsts.map { "${it.machineId}:${it.installments}" },
                            ),
                        ),
                    )
                }

                // Maquininha nova no backup
                bMach != null && lMach == null -> {
                    val rateValidationError = validateBackupMachineRates(bMach, bBrands, bInsts)
                    val collidingLocal = localMachinesByNorm[bMach.normalizedName]?.takeIf { it.id != bMach.id }

                    val machineAction: MergePlanAction
                    val childAction: MergePlanAction
                    val reasonText: String?
                    when {
                        rateValidationError != null -> {
                            machineAction = MergePlanAction.BLOCKED
                            childAction = MergePlanAction.BLOCKED
                            reasonText = rateValidationError
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    reason = rateValidationError,
                                ),
                            )
                        }
                        collidingLocal != null -> {
                            machineAction = MergePlanAction.CONFLICT
                            childAction = MergePlanAction.PENDING_DEPENDENCY
                            reasonText = "Conflito de identidade em maquininha: normalizedName já utilizado localmente por '${collidingLocal.id}'."
                            conflictItems.add(
                                MergeConflictItem(
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    keyParts = mapOf("id" to machineId),
                                    kind = MergeConflictKind.IDENTITY_NAME_COLLISION,
                                    divergentFields = listOf("id", "normalizedName"),
                                    collidingLocalRecordId = collidingLocal.id,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    hasFinancialOrRateDivergence = false,
                                    allowedResolutions = STANDARD_RESOLUTIONS,
                                ),
                            )
                        }
                        else -> {
                            machineAction = MergePlanAction.ADD
                            childAction = MergePlanAction.ADD
                            reasonText = null
                        }
                    }

                    machineActions.add(
                        PlannedRecordAction(
                            collection = BackupCollectionType.MACHINES,
                            recordId = machineId,
                            keyParts = mapOf("id" to machineId),
                            comparisonStatus = RecordComparisonStatus.NEW,
                            action = machineAction,
                            relatedUnitType = MergeUnitType.MACHINE,
                            relatedUnitId = machineId,
                            reason = reasonText,
                        ),
                    )
                    bBrands.forEach { br ->
                        val brId = "${br.machineId}:${br.brandKey}"
                        if (childAction == MergePlanAction.PENDING_DEPENDENCY) {
                            pendingDependencies.add(
                                PendingDependencyDetail(
                                    collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                    recordId = brId,
                                    dependsOnCollection = BackupCollectionType.MACHINES,
                                    dependsOnRecordId = machineId,
                                    reason = "Taxa por bandeira '$brId' aguarda resolução de identidade da maquininha '$machineId'.",
                                ),
                            )
                        }
                        brandActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                recordId = brId,
                                keyParts = mapOf("machineId" to br.machineId, "brandKey" to br.brandKey),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = childAction,
                                relatedUnitType = MergeUnitType.MACHINE,
                                relatedUnitId = machineId,
                                reason = reasonText,
                            ),
                        )
                    }
                    bInsts.forEach { ir ->
                        val irId = "${ir.machineId}:${ir.installments}"
                        if (childAction == MergePlanAction.PENDING_DEPENDENCY) {
                            pendingDependencies.add(
                                PendingDependencyDetail(
                                    collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                    recordId = irId,
                                    dependsOnCollection = BackupCollectionType.MACHINES,
                                    dependsOnRecordId = machineId,
                                    reason = "Adicional de parcelamento '$irId' aguarda resolução de identidade da maquininha '$machineId'.",
                                ),
                            )
                        }
                        installmentActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                recordId = irId,
                                keyParts = mapOf("machineId" to ir.machineId, "installments" to ir.installments.toString()),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = childAction,
                                relatedUnitType = MergeUnitType.MACHINE,
                                relatedUnitId = machineId,
                                reason = reasonText,
                            ),
                        )
                    }
                    relatedUnits.add(
                        RelatedMergeUnit(
                            unitType = MergeUnitType.MACHINE,
                            unitId = machineId,
                            action = machineAction,
                            memberRecordIdsByCollection = mapOf(
                                BackupCollectionType.MACHINES to listOf(machineId),
                                BackupCollectionType.MACHINE_BRAND_RATES to bBrands.map { "${it.machineId}:${it.brandKey}" },
                                BackupCollectionType.MACHINE_INSTALLMENT_RATES to bInsts.map { "${it.machineId}:${it.installments}" },
                            ),
                            blockReasons = if (machineAction == MergePlanAction.BLOCKED && reasonText != null) listOf(reasonText) else emptyList(),
                            allowedResolutions = if (machineAction == MergePlanAction.CONFLICT) STANDARD_RESOLUTIONS else emptyList(),
                        ),
                    )
                }

                // Maquininha presente nos dois lados
                bMach != null && lMach != null -> {
                    val machConflict = machConflictById[machineId]
                    val allBrandKeys = (bBrands.map { "${it.machineId}:${it.brandKey}" } +
                        lBrands.map { "${it.machineId}:${it.brandKey}" }).distinct().sorted()
                    val allInstKeys = (bInsts.map { "${it.machineId}:${it.installments}" } +
                        lInsts.map { "${it.machineId}:${it.installments}" }).distinct().sorted()

                    val anyBrandDiverges = allBrandKeys.any {
                        it in brNewSet || it in brLocalOnlySet || brConflictById.containsKey(it)
                    }
                    val anyInstDiverges = allInstKeys.any {
                        it in irNewSet || it in irLocalOnlySet || irConflictById.containsKey(it)
                    }

                    if (machConflict == null && !anyBrandDiverges && !anyInstDiverges) {
                        machineActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.MACHINES,
                                recordId = machineId,
                                keyParts = mapOf("id" to machineId),
                                comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.MACHINE,
                                relatedUnitId = machineId,
                            ),
                        )
                        allBrandKeys.forEach { id ->
                            val p = id.split(":")
                            brandActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                    recordId = id,
                                    keyParts = mapOf("machineId" to p[0], "brandKey" to p[1]),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                ),
                            )
                        }
                        allInstKeys.forEach { id ->
                            val p = id.split(":")
                            installmentActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                    recordId = id,
                                    keyParts = mapOf("machineId" to p[0], "installments" to p[1]),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                ),
                            )
                        }
                        relatedUnits.add(
                            RelatedMergeUnit(
                                unitType = MergeUnitType.MACHINE,
                                unitId = machineId,
                                action = MergePlanAction.KEEP,
                                memberRecordIdsByCollection = mapOf(
                                    BackupCollectionType.MACHINES to listOf(machineId),
                                    BackupCollectionType.MACHINE_BRAND_RATES to allBrandKeys,
                                    BackupCollectionType.MACHINE_INSTALLMENT_RATES to allInstKeys,
                                ),
                            ),
                        )
                    } else {
                        val unitBlockReasons = ArrayList<String>()
                        val rateValidationError = validateBackupMachineRates(bMach, bBrands, bInsts)
                        if (rateValidationError != null) unitBlockReasons.add(rateValidationError)

                        if (machConflict != null) {
                            val collidingLocal = localMachinesByNorm[bMach.normalizedName]?.takeIf { it.id != bMach.id }
                            machineActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    keyParts = machConflict.keyParts,
                                    comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                    action = MergePlanAction.CONFLICT,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    reason = "Maquininha diverge nos campos: ${machConflict.divergentFields.joinToString()}",
                                ),
                            )
                            conflictItems.add(
                                MergeConflictItem(
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    keyParts = machConflict.keyParts,
                                    kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                    divergentFields = machConflict.divergentFields,
                                    collidingLocalRecordId = collidingLocal?.id,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    hasFinancialOrRateDivergence = machConflict.hasFinancialOrRateDivergence,
                                    allowedResolutions = STANDARD_RESOLUTIONS,
                                ),
                            )
                        } else {
                            val msg = "Maquininha '$machineId' possui taxas de bandeira ou parcelamento divergentes; exige decisão atômica."
                            machineActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    keyParts = mapOf("id" to machineId),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.PENDING_DEPENDENCY,
                                    relatedUnitType = MergeUnitType.MACHINE,
                                    relatedUnitId = machineId,
                                    reason = msg,
                                ),
                            )
                            pendingDependencies.add(
                                PendingDependencyDetail(
                                    collection = BackupCollectionType.MACHINES,
                                    recordId = machineId,
                                    dependsOnCollection = BackupCollectionType.MACHINE_BRAND_RATES,
                                    dependsOnRecordId = machineId,
                                    reason = msg,
                                ),
                            )
                        }

                        val bBrandMap = bBrands.associateBy { "${it.machineId}:${it.brandKey}" }
                        allBrandKeys.forEach { brId ->
                            val parts = brId.split(":")
                            val keyParts = mapOf("machineId" to parts[0], "brandKey" to parts[1])
                            val brConflict = brConflictById[brId]
                            when {
                                brConflict != null -> {
                                    brandActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                            recordId = brId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                            action = MergePlanAction.CONFLICT,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                            reason = "Taxa por bandeira diverge na maquininha '$machineId'.",
                                        ),
                                    )
                                    conflictItems.add(
                                        MergeConflictItem(
                                            collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                            recordId = brId,
                                            keyParts = keyParts,
                                            kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                            divergentFields = brConflict.divergentFields,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                            hasFinancialOrRateDivergence = true,
                                            allowedResolutions = STANDARD_RESOLUTIONS,
                                        ),
                                    )
                                }
                                brId in brNewSet -> {
                                    val bRate = bBrandMap.getValue(brId)
                                    val bRateCredit = bRate.rateBps ?: lMach.creditDefaultRateBps
                                    val wouldExceed100 = lInsts.any {
                                        ((lMach.creditEnabled && lMach.installmentsEnabled) || it.addonBps > 0) &&
                                            bRateCredit + it.addonBps > RateFormatter.MAX_RATE_BPS
                                    }
                                    if (wouldExceed100) {
                                        val msg = "Taxa de bandeira '$brId' bloqueada para mesclagem parcial: soma com parcelamento local ultrapassaria 100,00%."
                                        unitBlockReasons.add(msg)
                                        blockReasons.add(
                                            MergeBlockReason(
                                                collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                                recordId = brId,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                        brandActions.add(
                                            PlannedRecordAction(
                                                collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                                recordId = brId,
                                                keyParts = keyParts,
                                                comparisonStatus = RecordComparisonStatus.NEW,
                                                action = MergePlanAction.BLOCKED,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                    } else {
                                        val msg = "Taxa '$brId' depende da resolução da maquininha '$machineId'."
                                        pendingDependencies.add(
                                            PendingDependencyDetail(
                                                collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                                recordId = brId,
                                                dependsOnCollection = BackupCollectionType.MACHINES,
                                                dependsOnRecordId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                        brandActions.add(
                                            PlannedRecordAction(
                                                collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                                recordId = brId,
                                                keyParts = keyParts,
                                                comparisonStatus = RecordComparisonStatus.NEW,
                                                action = MergePlanAction.PENDING_DEPENDENCY,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                    }
                                }
                                brId in brLocalOnlySet -> {
                                    brandActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                            recordId = brId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                        ),
                                    )
                                }
                                else -> {
                                    brandActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.MACHINE_BRAND_RATES,
                                            recordId = brId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                        ),
                                    )
                                }
                            }
                        }

                        val bInstMap = bInsts.associateBy { "${it.machineId}:${it.installments}" }
                        allInstKeys.forEach { irId ->
                            val parts = irId.split(":")
                            val keyParts = mapOf("machineId" to parts[0], "installments" to parts[1])
                            val irConflict = irConflictById[irId]
                            when {
                                irConflict != null -> {
                                    installmentActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                            recordId = irId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                            action = MergePlanAction.CONFLICT,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                            reason = "Adicional de parcelamento diverge na maquininha '$machineId'.",
                                        ),
                                    )
                                    conflictItems.add(
                                        MergeConflictItem(
                                            collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                            recordId = irId,
                                            keyParts = keyParts,
                                            kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                            divergentFields = irConflict.divergentFields,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                            hasFinancialOrRateDivergence = true,
                                            allowedResolutions = STANDARD_RESOLUTIONS,
                                        ),
                                    )
                                }
                                irId in irNewSet -> {
                                    val bInst = bInstMap.getValue(irId)
                                    val enforceLimit = (lMach.creditEnabled && lMach.installmentsEnabled) || bInst.addonBps > 0
                                    val wouldExceed100 = enforceLimit && (
                                        lMach.creditDefaultRateBps + bInst.addonBps > RateFormatter.MAX_RATE_BPS ||
                                            lBrands.any { ((it.rateBps ?: lMach.creditDefaultRateBps) + bInst.addonBps) > RateFormatter.MAX_RATE_BPS }
                                        )
                                    if (wouldExceed100) {
                                        val msg = "Parcelamento '$irId' bloqueado para mesclagem parcial: soma com taxa local ultrapassaria 100,00%."
                                        unitBlockReasons.add(msg)
                                        blockReasons.add(
                                            MergeBlockReason(
                                                collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                                recordId = irId,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                        installmentActions.add(
                                            PlannedRecordAction(
                                                collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                                recordId = irId,
                                                keyParts = keyParts,
                                                comparisonStatus = RecordComparisonStatus.NEW,
                                                action = MergePlanAction.BLOCKED,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                    } else {
                                        val msg = "Parcelamento '$irId' depende da resolução da maquininha '$machineId'."
                                        pendingDependencies.add(
                                            PendingDependencyDetail(
                                                collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                                recordId = irId,
                                                dependsOnCollection = BackupCollectionType.MACHINES,
                                                dependsOnRecordId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                        installmentActions.add(
                                            PlannedRecordAction(
                                                collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                                recordId = irId,
                                                keyParts = keyParts,
                                                comparisonStatus = RecordComparisonStatus.NEW,
                                                action = MergePlanAction.PENDING_DEPENDENCY,
                                                relatedUnitType = MergeUnitType.MACHINE,
                                                relatedUnitId = machineId,
                                                reason = msg,
                                            ),
                                        )
                                    }
                                }
                                irId in irLocalOnlySet -> {
                                    installmentActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                            recordId = irId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                        ),
                                    )
                                }
                                else -> {
                                    installmentActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
                                            recordId = irId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.MACHINE,
                                            relatedUnitId = machineId,
                                        ),
                                    )
                                }
                            }
                        }

                        relatedUnits.add(
                            RelatedMergeUnit(
                                unitType = MergeUnitType.MACHINE,
                                unitId = machineId,
                                action = MergePlanAction.CONFLICT,
                                memberRecordIdsByCollection = mapOf(
                                    BackupCollectionType.MACHINES to listOf(machineId),
                                    BackupCollectionType.MACHINE_BRAND_RATES to allBrandKeys,
                                    BackupCollectionType.MACHINE_INSTALLMENT_RATES to allInstKeys,
                                ),
                                blockReasons = unitBlockReasons.distinct(),
                                allowedResolutions = STANDARD_RESOLUTIONS,
                            ),
                        )
                    }
                }
            }
        }

        return Triple(
            CollectionMergePlan(BackupCollectionType.MACHINES, machineActions.sortedBy { it.recordId }),
            CollectionMergePlan(BackupCollectionType.MACHINE_BRAND_RATES, brandActions.sortedBy { it.recordId }),
            CollectionMergePlan(BackupCollectionType.MACHINE_INSTALLMENT_RATES, installmentActions.sortedBy { it.recordId }),
        )
    }

    private data class SalePlanQuad(
        val sales: CollectionMergePlan,
        val saleItems: CollectionMergePlan,
        val saleItemParticipants: CollectionMergePlan,
        val salePersonTotals: CollectionMergePlan,
    )

    private fun planSalesUnits(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
        salesComp: CollectionComparisonSummary,
        itemsComp: CollectionComparisonSummary,
        sipsComp: CollectionComparisonSummary,
        totalsComp: CollectionComparisonSummary,
        conflictItems: MutableList<MergeConflictItem>,
        blockReasons: MutableList<MergeBlockReason>,
        relatedUnits: MutableList<RelatedMergeUnit>,
    ): SalePlanQuad {
        val backupSales = backup.sales.associateBy { it.id }
        val localSales = local.sales.associateBy { it.id }

        val backupItemsBySale = backup.saleItems.groupBy { it.saleId }
        val localItemsBySale = local.saleItems.groupBy { it.saleId }
        val backupSipsByItem = backup.saleItemParticipants.groupBy { it.saleItemId }
        val localSipsByItem = local.saleItemParticipants.groupBy { it.saleItemId }
        val backupTotalsBySale = backup.salePersonTotals.groupBy { it.saleId }
        val localTotalsBySale = local.salePersonTotals.groupBy { it.saleId }

        val saleConflictById = salesComp.conflictingRecords.associateBy { it.recordId }
        val itemConflictById = itemsComp.conflictingRecords.associateBy { it.recordId }
        val itemNewSet = itemsComp.newRecordIds.toHashSet()
        val itemLocalOnlySet = itemsComp.localOnlyRecordIds.toHashSet()

        val sipConflictById = sipsComp.conflictingRecords.associateBy { it.recordId }
        val sipNewSet = sipsComp.newRecordIds.toHashSet()
        val sipLocalOnlySet = sipsComp.localOnlyRecordIds.toHashSet()

        val totalConflictById = totalsComp.conflictingRecords.associateBy { it.recordId }
        val totalNewSet = totalsComp.newRecordIds.toHashSet()
        val totalLocalOnlySet = totalsComp.localOnlyRecordIds.toHashSet()

        val saleActions = ArrayList<PlannedRecordAction>(salesComp.totalDistinctRecords)
        val itemActions = ArrayList<PlannedRecordAction>(itemsComp.totalDistinctRecords)
        val sipActions = ArrayList<PlannedRecordAction>(sipsComp.totalDistinctRecords)
        val totalActions = ArrayList<PlannedRecordAction>(totalsComp.totalDistinctRecords)

        val allSaleIds = (backupSales.keys + localSales.keys).sorted()
        allSaleIds.forEach { saleId ->
            val bSale = backupSales[saleId]
            val lSale = localSales[saleId]
            val bItems = backupItemsBySale[saleId].orEmpty().sortedBy { it.id }
            val lItems = localItemsBySale[saleId].orEmpty().sortedBy { it.id }
            val bTotals = backupTotalsBySale[saleId].orEmpty().sortedBy { it.personId }
            val lTotals = localTotalsBySale[saleId].orEmpty().sortedBy { it.personId }

            when {
                // Caso 1: Venda somente local -> MANTER intacta
                bSale == null && lSale != null -> {
                    saleActions.add(
                        PlannedRecordAction(
                            collection = BackupCollectionType.SALES,
                            recordId = saleId,
                            keyParts = mapOf("id" to saleId),
                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                            action = MergePlanAction.KEEP,
                            relatedUnitType = MergeUnitType.SALE,
                            relatedUnitId = saleId,
                        ),
                    )
                    val unitSipIds = ArrayList<String>()
                    lItems.forEach { item ->
                        itemActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.SALE_ITEMS,
                                recordId = item.id,
                                keyParts = mapOf("id" to item.id),
                                comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                            ),
                        )
                        localSipsByItem[item.id].orEmpty().sortedBy { it.personId }.forEach { sip ->
                            val sipId = "${sip.saleItemId}:${sip.personId}"
                            unitSipIds.add(sipId)
                            sipActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                    recordId = sipId,
                                    keyParts = mapOf("saleItemId" to sip.saleItemId, "personId" to sip.personId),
                                    comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                ),
                            )
                        }
                    }
                    lTotals.forEach { total ->
                        val totId = "${total.saleId}:${total.personId}"
                        totalActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                recordId = totId,
                                keyParts = mapOf("saleId" to total.saleId, "personId" to total.personId),
                                comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                            ),
                        )
                    }
                    relatedUnits.add(
                        RelatedMergeUnit(
                            unitType = MergeUnitType.SALE,
                            unitId = saleId,
                            action = MergePlanAction.KEEP,
                            memberRecordIdsByCollection = mapOf(
                                BackupCollectionType.SALES to listOf(saleId),
                                BackupCollectionType.SALE_ITEMS to lItems.map { it.id },
                                BackupCollectionType.SALE_ITEM_PARTICIPANTS to unitSipIds.sorted(),
                                BackupCollectionType.SALE_PERSON_TOTALS to lTotals.map { "${it.saleId}:${it.personId}" },
                            ),
                        ),
                    )
                }

                // Caso 2: Venda nova (inclui vendas históricas com catálogo excluído)
                bSale != null && lSale == null -> {
                    val bSipsForSale = bItems.flatMap { backupSipsByItem[it.id].orEmpty() }
                    val validationError = validateBackupSaleUnit(bSale, bItems, bSipsForSale, bTotals)
                    val unitAction = if (validationError == null) MergePlanAction.ADD else MergePlanAction.BLOCKED
                    if (validationError != null) {
                        blockReasons.add(
                            MergeBlockReason(
                                collection = BackupCollectionType.SALES,
                                recordId = saleId,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                                reason = validationError,
                            ),
                        )
                    }

                    saleActions.add(
                        PlannedRecordAction(
                            collection = BackupCollectionType.SALES,
                            recordId = saleId,
                            keyParts = mapOf("id" to saleId),
                            comparisonStatus = RecordComparisonStatus.NEW,
                            action = unitAction,
                            relatedUnitType = MergeUnitType.SALE,
                            relatedUnitId = saleId,
                            reason = validationError,
                        ),
                    )
                    bItems.forEach { item ->
                        if (validationError != null) {
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.SALE_ITEMS,
                                    recordId = item.id,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    reason = validationError,
                                ),
                            )
                        }
                        itemActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.SALE_ITEMS,
                                recordId = item.id,
                                keyParts = mapOf("id" to item.id),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = unitAction,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                                reason = validationError,
                            ),
                        )
                    }
                    bSipsForSale.sortedBy { "${it.saleItemId}:${it.personId}" }.forEach { sip ->
                        val sipId = "${sip.saleItemId}:${sip.personId}"
                        if (validationError != null) {
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                    recordId = sipId,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    reason = validationError,
                                ),
                            )
                        }
                        sipActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                recordId = sipId,
                                keyParts = mapOf("saleItemId" to sip.saleItemId, "personId" to sip.personId),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = unitAction,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                                reason = validationError,
                            ),
                        )
                    }
                    bTotals.forEach { total ->
                        val totId = "${total.saleId}:${total.personId}"
                        if (validationError != null) {
                            blockReasons.add(
                                MergeBlockReason(
                                    collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                    recordId = totId,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    reason = validationError,
                                ),
                            )
                        }
                        totalActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                recordId = totId,
                                keyParts = mapOf("saleId" to total.saleId, "personId" to total.personId),
                                comparisonStatus = RecordComparisonStatus.NEW,
                                action = unitAction,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                                reason = validationError,
                            ),
                        )
                    }
                    relatedUnits.add(
                        RelatedMergeUnit(
                            unitType = MergeUnitType.SALE,
                            unitId = saleId,
                            action = unitAction,
                            memberRecordIdsByCollection = mapOf(
                                BackupCollectionType.SALES to listOf(saleId),
                                BackupCollectionType.SALE_ITEMS to bItems.map { it.id },
                                BackupCollectionType.SALE_ITEM_PARTICIPANTS to bSipsForSale.map { "${it.saleItemId}:${it.personId}" }.sorted(),
                                BackupCollectionType.SALE_PERSON_TOTALS to bTotals.map { "${it.saleId}:${it.personId}" },
                            ),
                            blockReasons = if (validationError != null) listOf(validationError) else emptyList(),
                        ),
                    )
                }

                // Caso 3: Venda presente nos dois locais
                bSale != null && lSale != null -> {
                    val bSipsForSale = bItems.flatMap { backupSipsByItem[it.id].orEmpty() }
                    val lSipsForSale = lItems.flatMap { localSipsByItem[it.id].orEmpty() }

                    val allItemIds = (bItems.map { it.id } + lItems.map { it.id }).distinct().sorted()
                    val allSipIds = (bSipsForSale.map { "${it.saleItemId}:${it.personId}" } +
                        lSipsForSale.map { "${it.saleItemId}:${it.personId}" }).distinct().sorted()
                    val allTotalIds = (bTotals.map { "${it.saleId}:${it.personId}" } +
                        lTotals.map { "${it.saleId}:${it.personId}" }).distinct().sorted()

                    val saleHeaderConflict = saleConflictById[saleId]
                    val anyChildDiverges = allItemIds.any {
                        it in itemNewSet || it in itemLocalOnlySet || itemConflictById.containsKey(it)
                    } || allSipIds.any {
                        it in sipNewSet || it in sipLocalOnlySet || sipConflictById.containsKey(it)
                    } || allTotalIds.any {
                        it in totalNewSet || it in totalLocalOnlySet || totalConflictById.containsKey(it)
                    }

                    if (saleHeaderConflict == null && !anyChildDiverges) {
                        // Venda 100% idêntica -> MANTER sem duplicação
                        saleActions.add(
                            PlannedRecordAction(
                                collection = BackupCollectionType.SALES,
                                recordId = saleId,
                                keyParts = mapOf("id" to saleId),
                                comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                action = MergePlanAction.KEEP,
                                relatedUnitType = MergeUnitType.SALE,
                                relatedUnitId = saleId,
                            ),
                        )
                        allItemIds.forEach { itemId ->
                            itemActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.SALE_ITEMS,
                                    recordId = itemId,
                                    keyParts = mapOf("id" to itemId),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                ),
                            )
                        }
                        allSipIds.forEach { sipId ->
                            val parts = sipId.split(":")
                            sipActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                    recordId = sipId,
                                    keyParts = mapOf("saleItemId" to parts[0], "personId" to parts[1]),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                ),
                            )
                        }
                        allTotalIds.forEach { totId ->
                            val parts = totId.split(":")
                            totalActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                    recordId = totId,
                                    keyParts = mapOf("saleId" to parts[0], "personId" to parts[1]),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.KEEP,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                ),
                            )
                        }
                        relatedUnits.add(
                            RelatedMergeUnit(
                                unitType = MergeUnitType.SALE,
                                unitId = saleId,
                                action = MergePlanAction.KEEP,
                                memberRecordIdsByCollection = mapOf(
                                    BackupCollectionType.SALES to listOf(saleId),
                                    BackupCollectionType.SALE_ITEMS to allItemIds,
                                    BackupCollectionType.SALE_ITEM_PARTICIPANTS to allSipIds,
                                    BackupCollectionType.SALE_PERSON_TOTALS to allTotalIds,
                                ),
                            ),
                        )
                    } else {
                        // Venda conflitante: unidade financeira indivisível, proíbe mesclagem parcial
                        val unitBlockReasons = ArrayList<String>()
                        val backupSaleValidation = validateBackupSaleUnit(bSale, bItems, bSipsForSale, bTotals)
                        if (backupSaleValidation != null) unitBlockReasons.add(backupSaleValidation)

                        if (saleHeaderConflict != null) {
                            saleActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.SALES,
                                    recordId = saleId,
                                    keyParts = saleHeaderConflict.keyParts,
                                    comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                    action = MergePlanAction.CONFLICT,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    reason = "Venda '$saleId' diverge nos campos: ${saleHeaderConflict.divergentFields.joinToString()}",
                                ),
                            )
                            conflictItems.add(
                                MergeConflictItem(
                                    collection = BackupCollectionType.SALES,
                                    recordId = saleId,
                                    keyParts = saleHeaderConflict.keyParts,
                                    kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                    divergentFields = saleHeaderConflict.divergentFields,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    hasFinancialOrRateDivergence = saleHeaderConflict.hasFinancialOrRateDivergence,
                                    allowedResolutions = STANDARD_RESOLUTIONS,
                                ),
                            )
                        } else {
                            // Cabeçalho igual mas itens/participantes/totais divergem -> unidade conflita
                            saleActions.add(
                                PlannedRecordAction(
                                    collection = BackupCollectionType.SALES,
                                    recordId = saleId,
                                    keyParts = mapOf("id" to saleId),
                                    comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                    action = MergePlanAction.CONFLICT,
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    reason = "Venda '$saleId' possui itens, participantes ou totais divergentes e exige decisão atômica.",
                                ),
                            )
                            conflictItems.add(
                                MergeConflictItem(
                                    collection = BackupCollectionType.SALES,
                                    recordId = saleId,
                                    keyParts = mapOf("id" to saleId),
                                    kind = MergeConflictKind.RELATED_UNIT_DIVERGENCE,
                                    divergentFields = listOf("saleItems/participants/totals"),
                                    relatedUnitType = MergeUnitType.SALE,
                                    relatedUnitId = saleId,
                                    hasFinancialOrRateDivergence = true,
                                    allowedResolutions = STANDARD_RESOLUTIONS,
                                ),
                            )
                        }

                        val localItemsByProduct = lItems.associateBy { it.productId }
                        val backupItemsMap = bItems.associateBy { it.id }

                        allItemIds.forEach { itemId ->
                            val itemConflict = itemConflictById[itemId]
                            when {
                                itemConflict != null -> {
                                    itemActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEMS,
                                            recordId = itemId,
                                            keyParts = mapOf("id" to itemId),
                                            comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                            action = MergePlanAction.CONFLICT,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = "Item '$itemId' diverge na venda '$saleId'.",
                                        ),
                                    )
                                    conflictItems.add(
                                        MergeConflictItem(
                                            collection = BackupCollectionType.SALE_ITEMS,
                                            recordId = itemId,
                                            keyParts = mapOf("id" to itemId),
                                            kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                            divergentFields = itemConflict.divergentFields,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            hasFinancialOrRateDivergence = itemConflict.hasFinancialOrRateDivergence,
                                            allowedResolutions = STANDARD_RESOLUTIONS,
                                        ),
                                    )
                                }
                                itemId in itemNewSet -> {
                                    val bItem = backupItemsMap.getValue(itemId)
                                    val localSameProduct = localItemsByProduct[bItem.productId]
                                    val msg = if (localSameProduct != null) {
                                        "Item '$itemId' bloqueado para mesclagem parcial: colisão no índice único (saleId, productId) com item local '${localSameProduct.id}' e mistura de cabeçalho/itens proibida."
                                    } else {
                                        "Item '$itemId' bloqueado para mesclagem parcial: proibido misturar cabeçalho da venda local '$saleId' com itens importados."
                                    }
                                    unitBlockReasons.add(msg)
                                    blockReasons.add(
                                        MergeBlockReason(
                                            collection = BackupCollectionType.SALE_ITEMS,
                                            recordId = itemId,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = msg,
                                        ),
                                    )
                                    itemActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEMS,
                                            recordId = itemId,
                                            keyParts = mapOf("id" to itemId),
                                            comparisonStatus = RecordComparisonStatus.NEW,
                                            action = MergePlanAction.BLOCKED,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = msg,
                                        ),
                                    )
                                }
                                itemId in itemLocalOnlySet -> {
                                    itemActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEMS,
                                            recordId = itemId,
                                            keyParts = mapOf("id" to itemId),
                                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                        ),
                                    )
                                }
                                else -> {
                                    itemActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEMS,
                                            recordId = itemId,
                                            keyParts = mapOf("id" to itemId),
                                            comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                        ),
                                    )
                                }
                            }
                        }

                        allSipIds.forEach { sipId ->
                            val parts = sipId.split(":")
                            val keyParts = mapOf("saleItemId" to parts[0], "personId" to parts[1])
                            val sipConflict = sipConflictById[sipId]
                            when {
                                sipConflict != null -> {
                                    sipActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                            recordId = sipId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                            action = MergePlanAction.CONFLICT,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = "Participante '$sipId' diverge na venda '$saleId'.",
                                        ),
                                    )
                                    conflictItems.add(
                                        MergeConflictItem(
                                            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                            recordId = sipId,
                                            keyParts = keyParts,
                                            kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                            divergentFields = sipConflict.divergentFields,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            hasFinancialOrRateDivergence = sipConflict.hasFinancialOrRateDivergence,
                                            allowedResolutions = STANDARD_RESOLUTIONS,
                                        ),
                                    )
                                }
                                sipId in sipNewSet -> {
                                    val msg = "Participante de item '$sipId' bloqueado para mesclagem parcial na venda conflitante '$saleId'."
                                    unitBlockReasons.add(msg)
                                    blockReasons.add(
                                        MergeBlockReason(
                                            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                            recordId = sipId,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = msg,
                                        ),
                                    )
                                    sipActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                            recordId = sipId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.NEW,
                                            action = MergePlanAction.BLOCKED,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = msg,
                                        ),
                                    )
                                }
                                sipId in sipLocalOnlySet -> {
                                    sipActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                            recordId = sipId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                        ),
                                    )
                                }
                                else -> {
                                    sipActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                                            recordId = sipId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                        ),
                                    )
                                }
                            }
                        }

                        allTotalIds.forEach { totId ->
                            val parts = totId.split(":")
                            val keyParts = mapOf("saleId" to parts[0], "personId" to parts[1])
                            val totalConflict = totalConflictById[totId]
                            when {
                                totalConflict != null -> {
                                    totalActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                            recordId = totId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.CONFLICTING,
                                            action = MergePlanAction.CONFLICT,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = "Total por pessoa '$totId' diverge na venda '$saleId'.",
                                        ),
                                    )
                                    conflictItems.add(
                                        MergeConflictItem(
                                            collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                            recordId = totId,
                                            keyParts = keyParts,
                                            kind = MergeConflictKind.SAME_KEY_DATA_DIVERGENCE,
                                            divergentFields = totalConflict.divergentFields,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            hasFinancialOrRateDivergence = totalConflict.hasFinancialOrRateDivergence,
                                            allowedResolutions = STANDARD_RESOLUTIONS,
                                        ),
                                    )
                                }
                                totId in totalNewSet -> {
                                    val msg = "Total por pessoa '$totId' bloqueado para mesclagem parcial na venda conflitante '$saleId'."
                                    unitBlockReasons.add(msg)
                                    blockReasons.add(
                                        MergeBlockReason(
                                            collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                            recordId = totId,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = msg,
                                        ),
                                    )
                                    totalActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                            recordId = totId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.NEW,
                                            action = MergePlanAction.BLOCKED,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                            reason = msg,
                                        ),
                                    )
                                }
                                totId in totalLocalOnlySet -> {
                                    totalActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                            recordId = totId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.LOCAL_ONLY,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                        ),
                                    )
                                }
                                else -> {
                                    totalActions.add(
                                        PlannedRecordAction(
                                            collection = BackupCollectionType.SALE_PERSON_TOTALS,
                                            recordId = totId,
                                            keyParts = keyParts,
                                            comparisonStatus = RecordComparisonStatus.IDENTICAL,
                                            action = MergePlanAction.KEEP,
                                            relatedUnitType = MergeUnitType.SALE,
                                            relatedUnitId = saleId,
                                        ),
                                    )
                                }
                            }
                        }

                        relatedUnits.add(
                            RelatedMergeUnit(
                                unitType = MergeUnitType.SALE,
                                unitId = saleId,
                                action = MergePlanAction.CONFLICT,
                                memberRecordIdsByCollection = mapOf(
                                    BackupCollectionType.SALES to listOf(saleId),
                                    BackupCollectionType.SALE_ITEMS to allItemIds,
                                    BackupCollectionType.SALE_ITEM_PARTICIPANTS to allSipIds,
                                    BackupCollectionType.SALE_PERSON_TOTALS to allTotalIds,
                                ),
                                blockReasons = unitBlockReasons.distinct(),
                                allowedResolutions = STANDARD_RESOLUTIONS,
                            ),
                        )
                    }
                }
            }
        }

        return SalePlanQuad(
            sales = CollectionMergePlan(BackupCollectionType.SALES, saleActions.sortedBy { it.recordId }),
            saleItems = CollectionMergePlan(BackupCollectionType.SALE_ITEMS, itemActions.sortedBy { it.recordId }),
            saleItemParticipants = CollectionMergePlan(BackupCollectionType.SALE_ITEM_PARTICIPANTS, sipActions.sortedBy { it.recordId }),
            salePersonTotals = CollectionMergePlan(BackupCollectionType.SALE_PERSON_TOTALS, totalActions.sortedBy { it.recordId }),
        )
    }

    private fun validateBackupProductSplit(
        product: ProductDto,
        participants: List<ProductParticipantDto>,
    ): String? {
        if (product.priceCents <= 0L) {
            return "Produto '${product.id}' possui preço inválido (${product.priceCents})."
        }
        if (participants.isEmpty()) {
            return "Produto '${product.id}' não possui participantes vinculados."
        }
        var sum = 0L
        try {
            participants.forEach { p ->
                if (p.amountCents < 0L) {
                    return "Participante '${p.personId}' do produto '${product.id}' possui valor negativo."
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
            if (participants.none { it.personId == ownerId }) {
                return "Responsável '$ownerId' não pertence aos participantes do produto '${product.id}'."
            }
        } else if (product.feeOwnerPersonId != null) {
            return "Produto '${product.id}' fora do modo INDIVIDUAL não pode definir feeOwnerPersonId."
        }
        return null
    }

    private fun validateBackupProductCategoryRefsForPlan(
        product: ProductDto,
        backupCategoriesById: Map<String, ProductCategoryDto>,
        localCategoriesById: Map<String, com.mixvendas3d.app.data.local.entity.ProductCategoryEntity>,
        productCategoriesPlan: CollectionMergePlan,
    ): String? {
        val catId = product.categoryId
        val subId = product.subcategoryId
        if (subId != null && catId == null) {
            return "Produto '${product.id}' define subcategoryId '$subId' sem categoryId."
        }
        if (catId != null) {
            val act = productCategoriesPlan.actionForRecord(catId)
            if (act == null) {
                return "Produto '${product.id}' referencia categoria inexistente '$catId'."
            }
            if (act == MergePlanAction.BLOCKED) {
                return "Produto '${product.id}' referencia categoria bloqueada '$catId'."
            }
            val effectiveParent = backupCategoriesById[catId]?.parentId ?: localCategoriesById[catId]?.parentId
            if (effectiveParent != null) {
                return "Produto '${product.id}' referencia '$catId' em categoryId, mas ela é uma subcategoria."
            }
        }
        if (subId != null) {
            val act = productCategoriesPlan.actionForRecord(subId)
            if (act == null) {
                return "Produto '${product.id}' referencia subcategoria inexistente '$subId'."
            }
            if (act == MergePlanAction.BLOCKED) {
                return "Produto '${product.id}' referencia subcategoria bloqueada '$subId'."
            }
            val effectiveParent = backupCategoriesById[subId]?.parentId ?: localCategoriesById[subId]?.parentId
            if (effectiveParent != catId) {
                return "Subcategoria '$subId' do produto '${product.id}' não pertence à categoria '$catId'."
            }
        }
        return null
    }

    private fun validateBackupMachineRates(
        machine: MachineDto,
        brandRates: List<MachineBrandRateDto>,
        installmentRates: List<MachineInstallmentRateDto>,
    ): String? {
        if (machine.debitRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS ||
            machine.creditDefaultRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS
        ) {
            return "Maquininha '${machine.id}' possui taxa base fora do intervalo permitido."
        }
        if (machine.maxInstallments != null && machine.maxInstallments !in 1..24) {
            return "Maquininha '${machine.id}' possui limite máximo de parcelas inválido (${machine.maxInstallments})."
        }
        val seenNormalizedCustomNames = HashSet<String>()
        brandRates.forEach { br ->
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
            if (br.rateBps != null && br.rateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                return "Taxa da bandeira '${br.brandKey}' fora do intervalo na maquininha '${machine.id}'."
            }
            if (br.debitRateBps != null && br.debitRateBps !in RateFormatter.MIN_RATE_BPS..RateFormatter.MAX_RATE_BPS) {
                return "Taxa de débito da bandeira '${br.brandKey}' fora do intervalo na maquininha '${machine.id}'."
            }
        }
        installmentRates.forEach { ir ->
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
        if (sale.discountMode != "PROPORTIONAL" &&
            sale.discountMode != "SPECIFIC_PRODUCT" &&
            sale.discountMode != "SELECTED_PRODUCTS"
        ) {
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

    internal fun computeLocalSnapshotFingerprint(snapshot: JsonBackupSnapshot): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun feed(value: String) {
            digest.update(value.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }

        feed("preferences:${snapshot.preferences.size}")
        snapshot.preferences.sortedBy { it.key }.forEach {
            feed("${it.key}|${it.value}|${it.updatedAtEpochMs}")
        }
        feed("people:${snapshot.people.size}")
        snapshot.people.sortedBy { it.id }.forEach {
            feed("${it.id}|${it.name}|${it.normalizedName}|${it.colorIndex}|${it.createdAtEpochMs}|${it.updatedAtEpochMs}")
        }
        feed("productCategories:${snapshot.productCategories.size}")
        snapshot.productCategories.sortedBy { it.id }.forEach {
            feed("${it.id}|${it.name}|${it.normalizedName}|${it.parentId}|${it.createdAtEpochMs}|${it.updatedAtEpochMs}")
        }
        feed("products:${snapshot.products.size}")
        snapshot.products.sortedBy { it.id }.forEach {
            feed("${it.id}|${it.name}|${it.priceCents}|${it.active}|${it.feeResponsibilityMode}|${it.feeOwnerPersonId}|${it.categoryId}|${it.subcategoryId}|${it.createdAtEpochMs}|${it.updatedAtEpochMs}")
        }
        feed("productParticipants:${snapshot.productParticipants.size}")
        snapshot.productParticipants.sortedWith(compareBy({ it.productId }, { it.personId })).forEach {
            feed("${it.productId}|${it.personId}|${it.amountCents}|${it.sortOrder}")
        }
        feed("machines:${snapshot.machines.size}")
        snapshot.machines.sortedBy { it.id }.forEach {
            feed("${it.id}|${it.name}|${it.normalizedName}|${it.debitRateBps}|${it.creditEnabled}|${it.creditDefaultRateBps}|${it.brandRatesEnabled}|${it.installmentsEnabled}|${it.maxInstallments?.toString() ?: "null"}|${it.createdAtEpochMs}|${it.updatedAtEpochMs}")
        }
        feed("machineBrandRates:${snapshot.machineBrandRates.size}")
        snapshot.machineBrandRates.sortedWith(compareBy({ it.machineId }, { it.brandKey })).forEach {
            feed("${it.machineId}|${it.brandKey}|${it.rateBps}|${it.debitRateBps}")
        }
        feed("machineInstallmentRates:${snapshot.machineInstallmentRates.size}")
        snapshot.machineInstallmentRates.sortedWith(compareBy({ it.machineId }, { it.installments })).forEach {
            feed("${it.machineId}|${it.installments}|${it.addonBps}")
        }
        feed("sales:${snapshot.sales.size}")
        snapshot.sales.sortedBy { it.id }.forEach {
            feed(
                listOf(
                    it.id, it.soldAtEpochMs, it.updatedAtEpochMs, it.paymentMethod,
                    it.originalSubtotalCents, it.discountTotalCents, it.finalAmountCents,
                    it.feeTotalCents, it.netTotalCents, it.discountMode,
                    it.discountTargetProductId, it.discountTargetProductName,
                    it.machineId, it.machineName, it.machineDebitRateBps,
                    it.machineCreditDefaultRateBps, it.machineBrandRatesEnabled,
                    it.machineInstallmentsEnabled, it.cardBrand, it.installments,
                    it.automaticRateBps, it.manualRateOverrideBps, it.effectiveFeeRateBps, it.note,
                ).joinToString("|"),
            )
        }
        feed("saleItems:${snapshot.saleItems.size}")
        snapshot.saleItems.sortedBy { it.id }.forEach {
            feed(
                listOf(
                    it.id, it.saleId, it.sortOrder, it.productId, it.productName, it.quantity,
                    it.unitPriceOriginalCents, it.originalSubtotalCents, it.discountAmountCents,
                    it.finalAmountCents, it.appliedFeeRateBps, it.feeAmountCents, it.netAmountCents,
                    it.feeResponsibilityMode, it.feeOwnerPersonId, it.feeOwnerPersonName,
                ).joinToString("|"),
            )
        }
        feed("saleItemParticipants:${snapshot.saleItemParticipants.size}")
        snapshot.saleItemParticipants.sortedWith(compareBy({ it.saleItemId }, { it.personId })).forEach {
            feed(
                listOf(
                    it.saleItemId, it.personId, it.sortOrder, it.personName, it.personColorIndex,
                    it.originalUnitAmountCents, it.originalSubtotalCents, it.discountAmountCents,
                    it.grossAmountCents, it.feeAmountCents, it.netAmountCents,
                ).joinToString("|"),
            )
        }
        feed("salePersonTotals:${snapshot.salePersonTotals.size}")
        snapshot.salePersonTotals.sortedWith(compareBy({ it.saleId }, { it.personId })).forEach {
            feed(
                listOf(
                    it.saleId, it.personId, it.sortOrder, it.personName, it.personColorIndex,
                    it.grossAmountCents, it.feeAmountCents, it.netAmountCents,
                ).joinToString("|"),
            )
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
