package com.mixvendas3d.app.ui.screens

import com.mixvendas3d.app.domain.export.BackupCollectionType
import com.mixvendas3d.app.domain.export.JsonBackupMergePlanResult
import com.mixvendas3d.app.domain.export.JsonBackupSnapshot
import com.mixvendas3d.app.domain.export.MergeConflictItem
import com.mixvendas3d.app.domain.export.MergeConflictKind
import com.mixvendas3d.app.domain.export.MergeConflictResolution
import com.mixvendas3d.app.domain.export.MergeDecisionValidationResult
import com.mixvendas3d.app.domain.export.MergeUnitType
import com.mixvendas3d.app.domain.export.ValidatedBackupData
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.SalePaymentMethod
import com.mixvendas3d.app.util.CurrencyFormatter
import com.mixvendas3d.app.util.RateFormatter

data class BackupConflictVisualDetails(
    val typeLabel: String,
    val title: String,
    val localDataLines: List<String>,
    val backupDataLines: List<String>,
    val isAtomicUnit: Boolean,
)

data class BackupConflictUnitUi(
    val unitKey: String,
    val conflictKeys: List<String>,
    val typeLabel: String,
    val title: String,
    val localDataLines: List<String>,
    val backupDataLines: List<String>,
    val differentFieldsLabels: List<String>,
    val hasFinancialOrRateDivergence: Boolean,
    val isAtomicUnit: Boolean,
    val isIdentityCollision: Boolean,
    val allowedResolutions: List<MergeConflictResolution>,
    val selectedResolution: MergeConflictResolution?,
    val unitValidationMessages: List<String>,
)

data class BackupConflictSectionUi(
    val units: List<BackupConflictUnitUi>,
    val totalUnitsCount: Int,
    val resolvedUnitsCount: Int,
    val pendingUnitsCount: Int,
    val totalConflictItemsCount: Int,
    val resolvedConflictItemsCount: Int,
    val unresolvedConflictItemsCount: Int,
    val pendingDependenciesCount: Int,
    val incompatibleDecisionsCount: Int,
    val blockedOperationsCount: Int,
    val validationMessages: List<String>,
    val isReadyForNextPhase: Boolean,
    val eligibleAddsCount: Int,
    val eligibleKeepsCount: Int,
    val eligibleApplyBackupCount: Int,
    val eligibleSkipBackupCount: Int,
)

internal object BackupConflictFormatter {

    fun unitKeyFor(item: MergeConflictItem): String {
        return if (item.relatedUnitType != null && item.relatedUnitId != null) {
            "${item.relatedUnitType.name}:${item.relatedUnitId}"
        } else {
            "${item.collection.key}:${item.recordId}"
        }
    }

    fun conflictKeyFor(item: MergeConflictItem): String {
        return "${item.collection.key}:${item.recordId}"
    }

    fun buildUnitVisualDetails(
        validatedBackup: ValidatedBackupData,
        localSnapshot: JsonBackupSnapshot,
        plan: JsonBackupMergePlanResult,
    ): Map<String, BackupConflictVisualDetails> {
        val backupPeopleById = validatedBackup.people.associateBy { it.id }
        val localPeopleById = localSnapshot.people.associateBy { it.id }
        val localPrefsByKey = localSnapshot.preferences.associateBy { it.key }
        val backupPrefsByKey = validatedBackup.preferences.associateBy { it.key }

        val localProductsById = localSnapshot.products.associateBy { it.id }
        val backupProductsById = validatedBackup.products.associateBy { it.id }
        val localProdPartsByProd = localSnapshot.productParticipants.groupBy { it.productId }
        val backupProdPartsByProd = validatedBackup.productParticipants.groupBy { it.productId }

        val localMachinesById = localSnapshot.machines.associateBy { it.id }
        val backupMachinesById = validatedBackup.machines.associateBy { it.id }
        val localBrandsByMach = localSnapshot.machineBrandRates.groupBy { it.machineId }
        val backupBrandsByMach = validatedBackup.machineBrandRates.groupBy { it.machineId }
        val localInstByMach = localSnapshot.machineInstallmentRates.groupBy { it.machineId }
        val backupInstByMach = validatedBackup.machineInstallmentRates.groupBy { it.machineId }

        val localSalesById = localSnapshot.sales.associateBy { it.id }
        val backupSalesById = validatedBackup.sales.associateBy { it.id }
        val localItemsBySale = localSnapshot.saleItems.groupBy { it.saleId }
        val backupItemsBySale = validatedBackup.saleItems.groupBy { it.saleId }
        val localTotalsBySale = localSnapshot.salePersonTotals.groupBy { it.saleId }
        val backupTotalsBySale = validatedBackup.salePersonTotals.groupBy { it.saleId }

        val result = LinkedHashMap<String, BackupConflictVisualDetails>()
        val grouped = plan.conflictItems.groupBy { unitKeyFor(it) }

        grouped.forEach { (unitKey, items) ->
            val primary = items.first()
            val details = when {
                primary.relatedUnitType == MergeUnitType.PRODUCT && primary.relatedUnitId != null -> {
                    val prodId = primary.relatedUnitId
                    val lProd = localProductsById[prodId]
                    val bProd = backupProductsById[prodId]
                    val lParts = localProdPartsByProd[prodId].orEmpty().sortedBy { it.sortOrder }
                    val bParts = backupProdPartsByProd[prodId].orEmpty().sortedBy { it.sortOrder }

                    val lLines = if (lProd == null) {
                        listOf("Não cadastrado no celular.")
                    } else {
                        buildList {
                            add("Nome: ${lProd.name}")
                            add("Preço: ${formatMoney(lProd.priceCents)}")
                            add("Status: ${if (lProd.active) "Ativo" else "Inativo"}")
                            add("Divisão por pessoa: ${formatProductSplits(lParts, localPeopleById.mapValues { it.value.name }, backupPeopleById.mapValues { it.value.name })}")
                            add("Tarifa: ${formatFeeMode(lProd.feeResponsibilityMode, lProd.feeOwnerPersonId, localPeopleById.mapValues { it.value.name }, backupPeopleById.mapValues { it.value.name })}")
                        }
                    }

                    val bLines = if (bProd == null) {
                        listOf("Não presente no backup.")
                    } else {
                        buildList {
                            add("Nome: ${bProd.name}")
                            add("Preço: ${formatMoney(bProd.priceCents)}")
                            add("Status: ${if (bProd.active) "Ativo" else "Inativo"}")
                            add("Divisão por pessoa: ${formatProductSplits(bParts, backupPeopleById.mapValues { it.value.name }, localPeopleById.mapValues { it.value.name })}")
                            add("Tarifa: ${formatFeeMode(bProd.feeResponsibilityMode, bProd.feeOwnerPersonId, backupPeopleById.mapValues { it.value.name }, localPeopleById.mapValues { it.value.name })}")
                        }
                    }

                    BackupConflictVisualDetails(
                        typeLabel = "Produto",
                        title = lProd?.name ?: bProd?.name ?: "Produto",
                        localDataLines = lLines,
                        backupDataLines = bLines,
                        isAtomicUnit = true,
                    )
                }

                primary.relatedUnitType == MergeUnitType.MACHINE && primary.relatedUnitId != null -> {
                    val machineId = primary.relatedUnitId
                    val lMach = localMachinesById[machineId]
                        ?: primary.collidingLocalRecordId?.let { localMachinesById[it] }
                    val bMach = backupMachinesById[machineId]
                    val effectiveLocalId = lMach?.id ?: machineId
                    val lBrands = localBrandsByMach[effectiveLocalId].orEmpty().sortedBy { it.brandKey }
                    val bBrands = backupBrandsByMach[machineId].orEmpty().sortedBy { it.brandKey }
                    val lInst = localInstByMach[effectiveLocalId].orEmpty().sortedBy { it.installments }
                    val bInst = backupInstByMach[machineId].orEmpty().sortedBy { it.installments }

                    val lLines = if (lMach == null) {
                        listOf("Não cadastrada no celular.")
                    } else {
                        buildList {
                            add("Nome: ${lMach.name}")
                            add("Débito: ${RateFormatter.formatBpsAsPercent(lMach.debitRateBps)}")
                            add(
                                "Crédito à vista: ${
                                    if (lMach.creditEnabled) RateFormatter.formatBpsAsPercent(lMach.creditDefaultRateBps) else "Desativado"
                                }",
                            )
                            if (lBrands.isNotEmpty()) {
                                add(
                                    "Taxas por bandeira: " + lBrands.joinToString(" · ") {
                                        val creditStr = it.rateBps?.let { r -> RateFormatter.formatBpsAsPercent(r) } ?: "padrão"
                                        val debitStr = it.debitRateBps?.let { d -> " (débito ${RateFormatter.formatBpsAsPercent(d)})" }.orEmpty()
                                        "${brandLabel(it.brandKey)} $creditStr$debitStr"
                                    },
                                )
                            }
                            if (lInst.isNotEmpty()) {
                                add(
                                    "Parcelamento: " + lInst.joinToString(" · ") {
                                        "${it.installments}x +${RateFormatter.formatBpsAsPercent(it.addonBps)}"
                                    },
                                )
                            }
                        }
                    }

                    val bLines = if (bMach == null) {
                        listOf("Não presente no backup.")
                    } else {
                        buildList {
                            add("Nome: ${bMach.name}")
                            add("Débito: ${RateFormatter.formatBpsAsPercent(bMach.debitRateBps)}")
                            add(
                                "Crédito à vista: ${
                                    if (bMach.creditEnabled) RateFormatter.formatBpsAsPercent(bMach.creditDefaultRateBps) else "Desativado"
                                }",
                            )
                            if (bBrands.isNotEmpty()) {
                                add(
                                    "Taxas por bandeira: " + bBrands.joinToString(" · ") {
                                        val creditStr = it.rateBps?.let { r -> RateFormatter.formatBpsAsPercent(r) } ?: "padrão"
                                        val debitStr = it.debitRateBps?.let { d -> " (débito ${RateFormatter.formatBpsAsPercent(d)})" }.orEmpty()
                                        "${brandLabel(it.brandKey)} $creditStr$debitStr"
                                    },
                                )
                            }
                            if (bInst.isNotEmpty()) {
                                add(
                                    "Parcelamento: " + bInst.joinToString(" · ") {
                                        "${it.installments}x +${RateFormatter.formatBpsAsPercent(it.addonBps)}"
                                    },
                                )
                            }
                            if (primary.kind == MergeConflictKind.IDENTITY_NAME_COLLISION) {
                                add("Já existe outra maquininha com esse mesmo nome no celular.")
                            }
                        }
                    }

                    BackupConflictVisualDetails(
                        typeLabel = "Maquininha",
                        title = lMach?.name ?: bMach?.name ?: "Maquininha",
                        localDataLines = lLines,
                        backupDataLines = bLines,
                        isAtomicUnit = true,
                    )
                }

                primary.relatedUnitType == MergeUnitType.SALE && primary.relatedUnitId != null -> {
                    val saleId = primary.relatedUnitId
                    val lSale = localSalesById[saleId]
                    val bSale = backupSalesById[saleId]
                    val lItems = localItemsBySale[saleId].orEmpty().sortedBy { it.sortOrder }
                    val bItems = backupItemsBySale[saleId].orEmpty().sortedBy { it.sortOrder }
                    val lTotals = localTotalsBySale[saleId].orEmpty().sortedBy { it.sortOrder }
                    val bTotals = backupTotalsBySale[saleId].orEmpty().sortedBy { it.sortOrder }

                    val lLines = if (lSale == null) {
                        listOf("Não registrada no celular.")
                    } else {
                        buildList {
                            add("Valor da venda: ${formatMoney(lSale.finalAmountCents)}")
                            if (lSale.discountTotalCents > 0L || (bSale != null && bSale.discountTotalCents > 0L)) {
                                add("Desconto: ${formatMoney(lSale.discountTotalCents)}")
                            }
                            add("Tarifa: ${formatMoney(lSale.feeTotalCents)} (${RateFormatter.formatBpsAsPercent(lSale.effectiveFeeRateBps)})")
                            add("Líquido: ${formatMoney(lSale.netTotalCents)}")
                            add("Pagamento: ${paymentMethodLabel(lSale.paymentMethod, lSale.machineName, lSale.installments)}")
                            if (lItems.isNotEmpty()) {
                                add("Itens: " + lItems.joinToString(", ") { "${it.quantity}x ${it.productName} (${formatMoney(it.finalAmountCents)})" })
                            }
                            if (lTotals.isNotEmpty()) {
                                add("Líquido por pessoa: " + lTotals.joinToString(" · ") { "${it.personName}: ${formatMoney(it.netAmountCents)}" })
                            }
                            if (lSale.note.isNotBlank()) {
                                add("Observação: ${lSale.note}")
                            }
                        }
                    }

                    val bLines = if (bSale == null) {
                        listOf("Não presente no backup.")
                    } else {
                        buildList {
                            add("Valor da venda: ${formatMoney(bSale.finalAmountCents)}")
                            if (bSale.discountTotalCents > 0L || (lSale != null && lSale.discountTotalCents > 0L)) {
                                add("Desconto: ${formatMoney(bSale.discountTotalCents)}")
                            }
                            add("Tarifa: ${formatMoney(bSale.feeTotalCents)} (${RateFormatter.formatBpsAsPercent(bSale.effectiveFeeRateBps)})")
                            add("Líquido: ${formatMoney(bSale.netTotalCents)}")
                            add("Pagamento: ${paymentMethodLabel(bSale.paymentMethod, bSale.machineName, bSale.installments)}")
                            if (bItems.isNotEmpty()) {
                                add("Itens: " + bItems.joinToString(", ") { "${it.quantity}x ${it.productName} (${formatMoney(it.finalAmountCents)})" })
                            }
                            if (bTotals.isNotEmpty()) {
                                add("Líquido por pessoa: " + bTotals.joinToString(" · ") { "${it.personName}: ${formatMoney(it.netAmountCents)}" })
                            }
                            if (bSale.note.isNotBlank()) {
                                add("Observação: ${bSale.note}")
                            }
                        }
                    }

                    val soldEpoch = lSale?.soldAtEpochMs ?: bSale?.soldAtEpochMs
                    val saleTitle = if (soldEpoch != null) {
                        "Venda de ${CurrencyFormatter.formatEpochMillis(soldEpoch)}"
                    } else {
                        "Venda histórica"
                    }

                    BackupConflictVisualDetails(
                        typeLabel = "Venda",
                        title = saleTitle,
                        localDataLines = lLines,
                        backupDataLines = bLines,
                        isAtomicUnit = true,
                    )
                }

                primary.collection == BackupCollectionType.PEOPLE -> {
                    val personId = primary.recordId
                    val lPerson = localPeopleById[personId]
                        ?: primary.collidingLocalRecordId?.let { localPeopleById[it] }
                    val bPerson = backupPeopleById[personId]

                    val lLines = if (lPerson == null) {
                        listOf("Não cadastrada no celular.")
                    } else {
                        buildList {
                            add("Nome: ${lPerson.name}")
                            if (primary.kind == MergeConflictKind.IDENTITY_NAME_COLLISION) {
                                add("Cadastro existente no celular com este mesmo nome.")
                            }
                        }
                    }

                    val bLines = if (bPerson == null) {
                        listOf("Não presente no backup.")
                    } else {
                        buildList {
                            add("Nome: ${bPerson.name}")
                            if (primary.kind == MergeConflictKind.IDENTITY_NAME_COLLISION) {
                                add("Registro do backup com outro identificador para o mesmo nome.")
                            }
                        }
                    }

                    BackupConflictVisualDetails(
                        typeLabel = "Pessoa",
                        title = lPerson?.name ?: bPerson?.name ?: "Pessoa",
                        localDataLines = lLines,
                        backupDataLines = bLines,
                        isAtomicUnit = false,
                    )
                }

                else -> {
                    val key = primary.recordId
                    val lPref = localPrefsByKey[key]
                    val bPref = backupPrefsByKey[key]
                    BackupConflictVisualDetails(
                        typeLabel = "Configuração",
                        title = friendlyPreferenceTitle(key),
                        localDataLines = listOf("Valor no celular: ${lPref?.value ?: "Não definido"}"),
                        backupDataLines = listOf("Valor no backup: ${bPref?.value ?: "Não definido"}"),
                        isAtomicUnit = false,
                    )
                }
            }
            result[unitKey] = details
        }

        return result
    }

    fun buildConflictSectionUi(
        plan: JsonBackupMergePlanResult,
        validation: MergeDecisionValidationResult,
        unitDetailsByKey: Map<String, BackupConflictVisualDetails>,
    ): BackupConflictSectionUi {
        val resolvedPlan = validation.resolvedPlan
        val grouped = resolvedPlan.conflictItems.groupBy { unitKeyFor(it) }

        val units = grouped.entries.map { (unitKey, items) ->
            val primary = items.first()
            val details = unitDetailsByKey[unitKey] ?: fallbackDetails(primary)
            val conflictKeys = items.map { conflictKeyFor(it) }

            val distinctSelected = items.mapNotNull { it.selectedResolution }.distinct()
            val effectiveSelected = if (distinctSelected.size == 1 && items.all { it.selectedResolution != null }) {
                distinctSelected.single()
            } else {
                null
            }

            val allowedIntersection = if (items.isEmpty()) {
                emptyList()
            } else {
                MergeConflictResolution.entries.filter { candidate ->
                    items.all { candidate in it.allowedResolutions }
                }
            }

            val differentFields = items
                .flatMap { item -> item.divergentFields.map { field -> friendlyFieldLabel(item.collection, field) } }
                .distinct()

            val unitMessages = buildList {
                validation.blockedOperations
                    .filter { issueMatchesUnit(it.collection, it.recordId, it.relatedUnitType, it.relatedUnitId, items) }
                    .forEach { add(it.message) }
                validation.incompatibleDecisions
                    .filter { issueMatchesUnit(it.collection, it.recordId, it.relatedUnitType, it.relatedUnitId, items) }
                    .forEach { add(it.message) }
                validation.pendingDependencies
                    .filter { dep ->
                        items.any {
                            (dep.dependsOnCollection == it.collection && dep.dependsOnRecordId == it.recordId) ||
                                (dep.collection == it.collection && dep.recordId == it.recordId)
                        }
                    }
                    .forEach { add(it.reason) }
            }.distinct()

            BackupConflictUnitUi(
                unitKey = unitKey,
                conflictKeys = conflictKeys,
                typeLabel = details.typeLabel,
                title = details.title,
                localDataLines = details.localDataLines,
                backupDataLines = details.backupDataLines,
                differentFieldsLabels = differentFields,
                hasFinancialOrRateDivergence = items.any { it.hasFinancialOrRateDivergence },
                isAtomicUnit = details.isAtomicUnit || items.size > 1,
                isIdentityCollision = items.any { it.kind == MergeConflictKind.IDENTITY_NAME_COLLISION },
                allowedResolutions = allowedIntersection,
                selectedResolution = effectiveSelected,
                unitValidationMessages = unitMessages,
            )
        }

        val resolvedUnits = units.count { it.selectedResolution != null }
        val pendingUnits = units.size - resolvedUnits

        val globalMessages = buildList {
            validation.invalidDecisionInputs.forEach { add(it.message) }
            validation.incompatibleDecisions.forEach { add(it.message) }
            validation.blockedOperations.forEach { add(it.message) }
            validation.pendingDependencies.forEach { add(it.reason) }
        }.distinct()

        val eligibleUnits = validation.eligibleUnits
        val eligibleStandalone = validation.eligibleStandaloneRecords

        return BackupConflictSectionUi(
            units = units,
            totalUnitsCount = units.size,
            resolvedUnitsCount = resolvedUnits,
            pendingUnitsCount = pendingUnits,
            totalConflictItemsCount = validation.totalConflictsCount,
            resolvedConflictItemsCount = validation.resolvedConflictsCount,
            unresolvedConflictItemsCount = validation.unresolvedConflictsCount,
            pendingDependenciesCount = validation.pendingDependenciesCount,
            incompatibleDecisionsCount = validation.incompatibleDecisionsCount,
            blockedOperationsCount = validation.blockedOperationsCount,
            validationMessages = globalMessages,
            isReadyForNextPhase = validation.isReadyForNextPhase,
            eligibleAddsCount = eligibleUnits.count { it.disposition.name == "ADD_NEW" } +
                eligibleStandalone.count { it.disposition.name == "ADD_NEW" },
            eligibleKeepsCount = eligibleUnits.count { it.disposition.name == "KEEP_EXISTING" } +
                eligibleStandalone.count { it.disposition.name == "KEEP_EXISTING" },
            eligibleApplyBackupCount = eligibleUnits.count { it.disposition.name == "APPLY_BACKUP" } +
                eligibleStandalone.count { it.disposition.name == "APPLY_BACKUP" },
            eligibleSkipBackupCount = eligibleUnits.count { it.disposition.name == "SKIP_BACKUP" } +
                eligibleStandalone.count { it.disposition.name == "SKIP_BACKUP" },
        )
    }

    private fun issueMatchesUnit(
        collection: BackupCollectionType?,
        recordId: String?,
        relatedUnitType: MergeUnitType?,
        relatedUnitId: String?,
        unitItems: List<MergeConflictItem>,
    ): Boolean {
        return unitItems.any { item ->
            (relatedUnitType != null && relatedUnitId != null &&
                relatedUnitType == item.relatedUnitType && relatedUnitId == item.relatedUnitId) ||
                (collection != null && recordId != null &&
                    collection == item.collection && recordId == item.recordId)
        }
    }

    private fun fallbackDetails(primary: MergeConflictItem): BackupConflictVisualDetails {
        val typeLabel = when (primary.relatedUnitType) {
            MergeUnitType.PRODUCT -> "Produto"
            MergeUnitType.MACHINE -> "Maquininha"
            MergeUnitType.SALE -> "Venda"
            null -> when (primary.collection) {
                BackupCollectionType.PEOPLE -> "Pessoa"
                BackupCollectionType.PREFERENCES -> "Configuração"
                else -> "Registro"
            }
        }
        return BackupConflictVisualDetails(
            typeLabel = typeLabel,
            title = primary.recordId,
            localDataLines = listOf("Registro existente no celular."),
            backupDataLines = listOf("Registro presente no backup JSON."),
            isAtomicUnit = primary.relatedUnitType != null,
        )
    }

    private fun formatMoney(cents: Long): String = CurrencyFormatter.formatBrl(MoneyCents(cents))

    private fun formatProductSplits(
        parts: List<com.mixvendas3d.app.data.local.entity.ProductParticipantEntity>,
        primaryNames: Map<String, String>,
        secondaryNames: Map<String, String>,
    ): String {
        if (parts.isEmpty()) return "Sem participantes"
        return parts.joinToString(" · ") { part ->
            val personName = primaryNames[part.personId] ?: secondaryNames[part.personId] ?: part.personId
            "$personName ${formatMoney(part.amountCents)}"
        }
    }

    @JvmName("formatProductSplitsDto")
    private fun formatProductSplits(
        parts: List<com.mixvendas3d.app.domain.export.ProductParticipantDto>,
        primaryNames: Map<String, String>,
        secondaryNames: Map<String, String>,
    ): String {
        if (parts.isEmpty()) return "Sem participantes"
        return parts.joinToString(" · ") { part ->
            val personName = primaryNames[part.personId] ?: secondaryNames[part.personId] ?: part.personId
            "$personName ${formatMoney(part.amountCents)}"
        }
    }

    private fun formatFeeMode(
        modeKey: String,
        ownerId: String?,
        primaryNames: Map<String, String>,
        secondaryNames: Map<String, String>,
    ): String {
        val mode = FeeResponsibilityMode.fromStorageKey(modeKey)
        return when (mode) {
            FeeResponsibilityMode.EQUAL -> "Divisão igualitária"
            FeeResponsibilityMode.PROPORTIONAL -> "Proporcional entre participantes"
            FeeResponsibilityMode.INDIVIDUAL -> {
                val ownerName = ownerId?.let { primaryNames[it] ?: secondaryNames[it] } ?: "Individual"
                "Individual ($ownerName)"
            }
        }
    }

    private fun brandLabel(brandKey: String): String {
        return CustomBrandKeyCodec.resolveDisplayLabel(brandKey) ?: brandKey
    }

    private fun paymentMethodLabel(
        methodKey: String,
        machineName: String?,
        installments: Int?,
    ): String {
        val label = SalePaymentMethod.fromStorageKey(methodKey)?.label ?: methodKey
        return buildString {
            append(label)
            if (!machineName.isNullOrBlank()) {
                append(" · ")
                append(machineName)
            }
            if (installments != null && installments > 1) {
                append(" (${installments}x)")
            }
        }
    }

    private fun friendlyPreferenceTitle(key: String): String {
        return when (key) {
            "app_theme_mode" -> "Tema do aplicativo"
            else -> key
        }
    }

    private fun friendlyFieldLabel(collection: BackupCollectionType, field: String): String {
        return when (field) {
            "name" -> "Nome"
            "normalizedName" -> "Nome de identificação"
            "colorIndex" -> "Cor de identificação"
            "value" -> "Valor"
            "priceCents" -> "Preço"
            "active" -> "Status ativo"
            "feeResponsibilityMode", "feeOwnerPersonId" -> "Responsável pela tarifa"
            "amountCents" -> "Divisão por pessoa"
            "sortOrder" -> "Ordem dos participantes"
            "debitRateBps" -> "Taxa no débito"
            "creditEnabled" -> "Crédito habilitado"
            "creditDefaultRateBps" -> "Taxa padrão no crédito"
            "brandRatesEnabled" -> "Taxas por bandeira"
            "installmentsEnabled" -> "Parcelamento habilitado"
            "rateBps" -> "Taxa da bandeira"
            "addonBps" -> "Adicional de parcelamento"
            "paymentMethod" -> "Forma de pagamento"
            "originalSubtotalCents" -> "Subtotal dos produtos"
            "discountTotalCents", "discountAmountCents", "discountMode", "discountTargetProductId" -> "Desconto"
            "finalAmountCents", "grossAmountCents" -> "Valor da venda"
            "feeTotalCents", "feeAmountCents" -> "Tarifa"
            "netTotalCents", "netAmountCents" -> "Líquido"
            "effectiveFeeRateBps", "appliedFeeRateBps", "manualRateOverrideBps", "automaticRateBps" -> "Taxa aplicada"
            "cardBrand" -> "Bandeira"
            "installments" -> "Parcelas"
            "note" -> "Observação"
            "quantity" -> "Quantidade"
            "unitPriceOriginalCents" -> "Preço unitário"
            "updatedAtEpochMs", "createdAtEpochMs", "soldAtEpochMs" -> "Data de registro"
            "id" -> "Identificador"
            else -> when (collection) {
                BackupCollectionType.PRODUCT_PARTICIPANTS -> "Divisão por pessoa"
                BackupCollectionType.MACHINE_BRAND_RATES -> "Taxas por bandeira"
                BackupCollectionType.MACHINE_INSTALLMENT_RATES -> "Adicionais de parcelamento"
                BackupCollectionType.SALE_ITEMS -> "Itens da venda"
                BackupCollectionType.SALE_ITEM_PARTICIPANTS,
                BackupCollectionType.SALE_PERSON_TOTALS -> "Divisão financeira"
                else -> field
            }
        }
    }
}
