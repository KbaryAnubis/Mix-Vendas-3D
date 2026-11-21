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

object JsonBackupComparator {

    private val FINANCIAL_OR_RATE_FIELDS = setOf(
        "priceCents",
        "feeResponsibilityMode",
        "feeOwnerPersonId",
        "amountCents",
        "debitRateBps",
        "creditEnabled",
        "creditDefaultRateBps",
        "brandRatesEnabled",
        "installmentsEnabled",
        "maxInstallments",
        "rateBps",
        "addonBps",
        "paymentMethod",
        "originalSubtotalCents",
        "discountTotalCents",
        "finalAmountCents",
        "feeTotalCents",
        "netTotalCents",
        "discountMode",
        "discountTargetProductId",
        "machineId",
        "machineDebitRateBps",
        "machineCreditDefaultRateBps",
        "machineBrandRatesEnabled",
        "machineInstallmentsEnabled",
        "cardBrand",
        "installments",
        "automaticRateBps",
        "manualRateOverrideBps",
        "effectiveFeeRateBps",
        "quantity",
        "unitPriceOriginalCents",
        "discountAmountCents",
        "appliedFeeRateBps",
        "feeAmountCents",
        "netAmountCents",
        "originalUnitAmountCents",
        "grossAmountCents",
    )

    suspend fun compare(
        readResult: JsonBackupReadResult,
        database: AppDatabase,
    ): BackupComparisonResult {
        require(readResult.isValid && readResult.validatedData != null) {
            "A comparação exige um JsonBackupReadResult previamente validado pela Etapa 6B-1."
        }
        return compare(readResult.validatedData, database)
    }

    suspend fun compare(
        validatedBackup: ValidatedBackupData,
        database: AppDatabase,
    ): BackupComparisonResult {
        // Reutiliza a captura transacional única das 11 tabelas via DAOs Room existentes (somente SELECTs).
        val localSnapshot = JsonBackupExporter.captureConsistentSnapshot(database)
        return compareSnapshots(validatedBackup, localSnapshot)
    }

    internal fun compareSnapshots(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
    ): BackupComparisonResult {
        val preferencesSummary = compareCollection(
            collection = BackupCollectionType.PREFERENCES,
            backupItems = backup.preferences,
            localItems = local.preferences,
            backupKey = { it.key },
            localKey = { it.key },
            recordIdFormatter = { it },
            keyPartsExtractor = { mapOf("key" to it) },
            diffExtractor = ::diffPreference,
        )

        val peopleSummary = compareCollection(
            collection = BackupCollectionType.PEOPLE,
            backupItems = backup.people,
            localItems = local.people,
            backupKey = { it.id },
            localKey = { it.id },
            recordIdFormatter = { it },
            keyPartsExtractor = { mapOf("id" to it) },
            diffExtractor = ::diffPerson,
        )

        val productsSummary = compareCollection(
            collection = BackupCollectionType.PRODUCTS,
            backupItems = backup.products,
            localItems = local.products,
            backupKey = { it.id },
            localKey = { it.id },
            recordIdFormatter = { it },
            keyPartsExtractor = { mapOf("id" to it) },
            diffExtractor = ::diffProduct,
        )

        val productParticipantsSummary = compareCollection(
            collection = BackupCollectionType.PRODUCT_PARTICIPANTS,
            backupItems = backup.productParticipants,
            localItems = local.productParticipants,
            backupKey = { it.productId to it.personId },
            localKey = { it.productId to it.personId },
            recordIdFormatter = { "${it.first}:${it.second}" },
            keyPartsExtractor = { mapOf("productId" to it.first, "personId" to it.second) },
            diffExtractor = ::diffProductParticipant,
        )

        val machinesSummary = compareCollection(
            collection = BackupCollectionType.MACHINES,
            backupItems = backup.machines,
            localItems = local.machines,
            backupKey = { it.id },
            localKey = { it.id },
            recordIdFormatter = { it },
            keyPartsExtractor = { mapOf("id" to it) },
            diffExtractor = ::diffMachine,
        )

        val machineBrandRatesSummary = compareCollection(
            collection = BackupCollectionType.MACHINE_BRAND_RATES,
            backupItems = backup.machineBrandRates,
            localItems = local.machineBrandRates,
            backupKey = { it.machineId to it.brandKey },
            localKey = { it.machineId to it.brandKey },
            recordIdFormatter = { "${it.first}:${it.second}" },
            keyPartsExtractor = { mapOf("machineId" to it.first, "brandKey" to it.second) },
            diffExtractor = ::diffMachineBrandRate,
        )

        val machineInstallmentRatesSummary = compareCollection(
            collection = BackupCollectionType.MACHINE_INSTALLMENT_RATES,
            backupItems = backup.machineInstallmentRates,
            localItems = local.machineInstallmentRates,
            backupKey = { it.machineId to it.installments },
            localKey = { it.machineId to it.installments },
            recordIdFormatter = { "${it.first}:${it.second}" },
            keyPartsExtractor = { mapOf("machineId" to it.first, "installments" to it.second.toString()) },
            diffExtractor = ::diffMachineInstallmentRate,
        )

        val salesSummary = compareCollection(
            collection = BackupCollectionType.SALES,
            backupItems = backup.sales,
            localItems = local.sales,
            backupKey = { it.id },
            localKey = { it.id },
            recordIdFormatter = { it },
            keyPartsExtractor = { mapOf("id" to it) },
            diffExtractor = ::diffSale,
        )

        val saleItemsSummary = compareCollection(
            collection = BackupCollectionType.SALE_ITEMS,
            backupItems = backup.saleItems,
            localItems = local.saleItems,
            backupKey = { it.id },
            localKey = { it.id },
            recordIdFormatter = { it },
            keyPartsExtractor = { mapOf("id" to it) },
            diffExtractor = ::diffSaleItem,
        )

        val saleItemParticipantsSummary = compareCollection(
            collection = BackupCollectionType.SALE_ITEM_PARTICIPANTS,
            backupItems = backup.saleItemParticipants,
            localItems = local.saleItemParticipants,
            backupKey = { it.saleItemId to it.personId },
            localKey = { it.saleItemId to it.personId },
            recordIdFormatter = { "${it.first}:${it.second}" },
            keyPartsExtractor = { mapOf("saleItemId" to it.first, "personId" to it.second) },
            diffExtractor = ::diffSaleItemParticipant,
        )

        val salePersonTotalsSummary = compareCollection(
            collection = BackupCollectionType.SALE_PERSON_TOTALS,
            backupItems = backup.salePersonTotals,
            localItems = local.salePersonTotals,
            backupKey = { it.saleId to it.personId },
            localKey = { it.saleId to it.personId },
            recordIdFormatter = { "${it.first}:${it.second}" },
            keyPartsExtractor = { mapOf("saleId" to it.first, "personId" to it.second) },
            diffExtractor = ::diffSalePersonTotal,
        )

        val allSummaries = listOf(
            preferencesSummary,
            peopleSummary,
            productsSummary,
            productParticipantsSummary,
            machinesSummary,
            machineBrandRatesSummary,
            machineInstallmentRatesSummary,
            salesSummary,
            saleItemsSummary,
            saleItemParticipantsSummary,
            salePersonTotalsSummary,
        )

        val relationalRisks = detectRelationalRisks(
            backup = backup,
            local = local,
            productsSummary = productsSummary,
            productParticipantsSummary = productParticipantsSummary,
            machinesSummary = machinesSummary,
            machineBrandRatesSummary = machineBrandRatesSummary,
            machineInstallmentRatesSummary = machineInstallmentRatesSummary,
            salesSummary = salesSummary,
            saleItemsSummary = saleItemsSummary,
            saleItemParticipantsSummary = saleItemParticipantsSummary,
            salePersonTotalsSummary = salePersonTotalsSummary,
        )

        return BackupComparisonResult(
            backupFormat = backup.format,
            backupSchemaVersion = backup.backupSchemaVersion,
            applicationId = backup.applicationId,
            roomSchemaVersion = backup.roomSchemaVersion,
            exportedAtEpochMs = backup.exportedAtEpochMs,
            collectionSummaries = allSummaries,
            relationalRisks = relationalRisks,
        )
    }

    private fun <B, L, K> compareCollection(
        collection: BackupCollectionType,
        backupItems: List<B>,
        localItems: List<L>,
        backupKey: (B) -> K,
        localKey: (L) -> K,
        recordIdFormatter: (K) -> String,
        keyPartsExtractor: (K) -> Map<String, String>,
        diffExtractor: (B, L) -> List<String>,
    ): CollectionComparisonSummary {
        val backupMap = LinkedHashMap<K, B>(backupItems.size)
        backupItems.forEach { item ->
            backupMap[backupKey(item)] = item
        }

        val localMap = LinkedHashMap<K, L>(localItems.size)
        localItems.forEach { item ->
            localMap[localKey(item)] = item
        }

        val newIds = ArrayList<String>()
        val identicalIds = ArrayList<String>()
        val conflicts = ArrayList<ConflictRecord>()
        val localOnlyIds = ArrayList<String>()

        backupMap.entries
            .sortedBy { recordIdFormatter(it.key) }
            .forEach { (key, bItem) ->
                val formattedId = recordIdFormatter(key)
                val lItem = localMap[key]
                if (lItem == null) {
                    newIds.add(formattedId)
                } else {
                    val divergentFields = diffExtractor(bItem, lItem)
                    if (divergentFields.isEmpty()) {
                        identicalIds.add(formattedId)
                    } else {
                        conflicts.add(
                            ConflictRecord(
                                collection = collection,
                                recordId = formattedId,
                                keyParts = keyPartsExtractor(key),
                                divergentFields = divergentFields,
                                hasFinancialOrRateDivergence = divergentFields.any { it in FINANCIAL_OR_RATE_FIELDS },
                            ),
                        )
                    }
                }
            }

        localMap.keys
            .asSequence()
            .filter { !backupMap.containsKey(it) }
            .map { recordIdFormatter(it) }
            .sorted()
            .forEach { localOnlyIds.add(it) }

        return CollectionComparisonSummary(
            collection = collection,
            backupCount = backupItems.size,
            localCount = localItems.size,
            newCount = newIds.size,
            identicalCount = identicalIds.size,
            conflictingCount = conflicts.size,
            localOnlyCount = localOnlyIds.size,
            newRecordIds = newIds,
            identicalRecordIds = identicalIds,
            conflictingRecords = conflicts,
            localOnlyRecordIds = localOnlyIds,
        )
    }

    private fun detectRelationalRisks(
        backup: ValidatedBackupData,
        local: JsonBackupSnapshot,
        productsSummary: CollectionComparisonSummary,
        productParticipantsSummary: CollectionComparisonSummary,
        machinesSummary: CollectionComparisonSummary,
        machineBrandRatesSummary: CollectionComparisonSummary,
        machineInstallmentRatesSummary: CollectionComparisonSummary,
        salesSummary: CollectionComparisonSummary,
        saleItemsSummary: CollectionComparisonSummary,
        saleItemParticipantsSummary: CollectionComparisonSummary,
        salePersonTotalsSummary: CollectionComparisonSummary,
    ): List<RelationalConflictRisk> {
        val risks = ArrayList<RelationalConflictRisk>()

        // 1) Colisão de índice único normalizedName em people com IDs diferentes
        val localPeopleByNorm = local.people.associateBy { it.normalizedName }
        backup.people.sortedBy { it.id }.forEach { bPerson ->
            val lPerson = localPeopleByNorm[bPerson.normalizedName]
            if (lPerson != null && lPerson.id != bPerson.id) {
                risks.add(
                    RelationalConflictRisk(
                        type = RelationalRiskType.UNIQUE_NAME_COLLISION,
                        collection = BackupCollectionType.PEOPLE,
                        recordId = bPerson.id,
                        relatedRecordId = lPerson.id,
                        message = "Conflito de unicidade em people.normalizedName para '${bPerson.id}' (local existente: '${lPerson.id}').",
                    ),
                )
            }
        }

        // 2) Colisão de índice único normalizedName em machines com IDs diferentes
        val localMachinesByNorm = local.machines.associateBy { it.normalizedName }
        backup.machines.sortedBy { it.id }.forEach { bMachine ->
            val lMachine = localMachinesByNorm[bMachine.normalizedName]
            if (lMachine != null && lMachine.id != bMachine.id) {
                risks.add(
                    RelationalConflictRisk(
                        type = RelationalRiskType.UNIQUE_NAME_COLLISION,
                        collection = BackupCollectionType.MACHINES,
                        recordId = bMachine.id,
                        relatedRecordId = lMachine.id,
                        message = "Conflito de unicidade em machines.normalizedName para '${bMachine.id}' (local existente: '${lMachine.id}').",
                    ),
                )
            }
        }

        // 3) Risco de integridade em products + productParticipants (soma de participantes == priceCents e feeOwnerPersonId)
        val localProductIds = local.products.mapTo(HashSet()) { it.id }
        val backupProductIds = backup.products.mapTo(HashSet()) { it.id }
        val sharedProductIds = localProductIds.intersect(backupProductIds)

        val productIdsWithSplitRisk = linkedSetOf<String>()
        productsSummary.conflictingRecords.forEach { conflict ->
            productIdsWithSplitRisk.add(conflict.recordId)
        }
        val touchedParticipantProductIds = buildSet {
            backup.productParticipants.forEach { pp ->
                val formatted = "${pp.productId}:${pp.personId}"
                if (formatted in productParticipantsSummary.newRecordIds ||
                    productParticipantsSummary.conflictingRecords.any { it.recordId == formatted }
                ) {
                    add(pp.productId)
                }
            }
            local.productParticipants.forEach { pp ->
                val formatted = "${pp.productId}:${pp.personId}"
                if (formatted in productParticipantsSummary.localOnlyRecordIds) {
                    add(pp.productId)
                }
            }
        }
        touchedParticipantProductIds.filter { it in sharedProductIds }.forEach {
            productIdsWithSplitRisk.add(it)
        }
        productIdsWithSplitRisk.sorted().forEach { productId ->
            risks.add(
                RelationalConflictRisk(
                    type = RelationalRiskType.PRODUCT_SPLIT_INTEGRITY_RISK,
                    collection = BackupCollectionType.PRODUCTS,
                    relatedCollection = BackupCollectionType.PRODUCT_PARTICIPANTS,
                    recordId = productId,
                    message = "Produto '$productId' possui divergência entre cabeçalho e participantes; mesclagem parcial comprometeria a soma exata do rateio.",
                ),
            )
        }

        // 4) Risco de integridade em machines + taxas (bandeira e parcelamento <= 10.000 bps)
        val localMachineIds = local.machines.mapTo(HashSet()) { it.id }
        val backupMachineIds = backup.machines.mapTo(HashSet()) { it.id }
        val sharedMachineIds = localMachineIds.intersect(backupMachineIds)
        val machineIdsWithRateRisk = linkedSetOf<String>()
        machinesSummary.conflictingRecords.forEach { machineIdsWithRateRisk.add(it.recordId) }
        backup.machineBrandRates.forEach { br ->
            val formatted = "${br.machineId}:${br.brandKey}"
            if (br.machineId in sharedMachineIds &&
                (formatted in machineBrandRatesSummary.newRecordIds ||
                    machineBrandRatesSummary.conflictingRecords.any { it.recordId == formatted })
            ) {
                machineIdsWithRateRisk.add(br.machineId)
            }
        }
        local.machineBrandRates.forEach { br ->
            val formatted = "${br.machineId}:${br.brandKey}"
            if (br.machineId in sharedMachineIds && formatted in machineBrandRatesSummary.localOnlyRecordIds) {
                machineIdsWithRateRisk.add(br.machineId)
            }
        }
        backup.machineInstallmentRates.forEach { ir ->
            val formatted = "${ir.machineId}:${ir.installments}"
            if (ir.machineId in sharedMachineIds &&
                (formatted in machineInstallmentRatesSummary.newRecordIds ||
                    machineInstallmentRatesSummary.conflictingRecords.any { it.recordId == formatted })
            ) {
                machineIdsWithRateRisk.add(ir.machineId)
            }
        }
        local.machineInstallmentRates.forEach { ir ->
            val formatted = "${ir.machineId}:${ir.installments}"
            if (ir.machineId in sharedMachineIds && formatted in machineInstallmentRatesSummary.localOnlyRecordIds) {
                machineIdsWithRateRisk.add(ir.machineId)
            }
        }
        machineIdsWithRateRisk.sorted().forEach { machineId ->
            risks.add(
                RelationalConflictRisk(
                    type = RelationalRiskType.MACHINE_RATE_INTEGRITY_RISK,
                    collection = BackupCollectionType.MACHINES,
                    recordId = machineId,
                    message = "Maquininha '$machineId' possui conflito entre configuração base e taxas de bandeira/parcelamento.",
                ),
            )
        }

        // 5) Colisão do índice único (sale_id, product_id) em sale_items com IDs diferentes
        val localItemsBySaleProduct = local.saleItems.associateBy { it.saleId to it.productId }
        backup.saleItems.sortedBy { it.id }.forEach { bItem ->
            val lItem = localItemsBySaleProduct[bItem.saleId to bItem.productId]
            if (lItem != null && lItem.id != bItem.id) {
                risks.add(
                    RelationalConflictRisk(
                        type = RelationalRiskType.SALE_ITEM_UNIQUE_PRODUCT_COLLISION,
                        collection = BackupCollectionType.SALE_ITEMS,
                        relatedCollection = BackupCollectionType.SALES,
                        recordId = bItem.id,
                        relatedRecordId = lItem.id,
                        message = "Conflito no índice único (saleId, productId) da venda '${bItem.saleId}': item do backup '${bItem.id}' colide com item local '${lItem.id}'.",
                    ),
                )
            }
        }

        // 6) Risco de integridade financeira em sales compartilhadas (cabeçalho, itens, participantes ou totais por pessoa divergentes)
        val localSaleIds = local.sales.mapTo(HashSet()) { it.id }
        val backupSaleIds = backup.sales.mapTo(HashSet()) { it.id }
        val sharedSaleIds = localSaleIds.intersect(backupSaleIds)

        val saleIdsWithFinancialRisk = linkedSetOf<String>()
        salesSummary.conflictingRecords.forEach { saleIdsWithFinancialRisk.add(it.recordId) }

        val backupItemIdToSaleId = backup.saleItems.associate { it.id to it.saleId }
        val localItemIdToSaleId = local.saleItems.associate { it.id to it.saleId }

        val conflictingItemIds = saleItemsSummary.conflictingRecords.mapTo(HashSet()) { it.recordId }
        backup.saleItems.forEach { item ->
            if (item.saleId in sharedSaleIds &&
                (item.id in saleItemsSummary.newRecordIds || item.id in conflictingItemIds)
            ) {
                saleIdsWithFinancialRisk.add(item.saleId)
            }
        }
        local.saleItems.forEach { item ->
            if (item.saleId in sharedSaleIds && item.id in saleItemsSummary.localOnlyRecordIds) {
                saleIdsWithFinancialRisk.add(item.saleId)
            }
        }

        val conflictingSipIds = saleItemParticipantsSummary.conflictingRecords.mapTo(HashSet()) { it.recordId }
        backup.saleItemParticipants.forEach { sip ->
            val parentSaleId = backupItemIdToSaleId[sip.saleItemId]
            val formatted = "${sip.saleItemId}:${sip.personId}"
            if (parentSaleId != null && parentSaleId in sharedSaleIds &&
                (formatted in saleItemParticipantsSummary.newRecordIds || formatted in conflictingSipIds)
            ) {
                saleIdsWithFinancialRisk.add(parentSaleId)
            }
        }
        local.saleItemParticipants.forEach { sip ->
            val parentSaleId = localItemIdToSaleId[sip.saleItemId]
            val formatted = "${sip.saleItemId}:${sip.personId}"
            if (parentSaleId != null && parentSaleId in sharedSaleIds &&
                formatted in saleItemParticipantsSummary.localOnlyRecordIds
            ) {
                saleIdsWithFinancialRisk.add(parentSaleId)
            }
        }

        val conflictingSptIds = salePersonTotalsSummary.conflictingRecords.mapTo(HashSet()) { it.recordId }
        backup.salePersonTotals.forEach { spt ->
            val formatted = "${spt.saleId}:${spt.personId}"
            if (spt.saleId in sharedSaleIds &&
                (formatted in salePersonTotalsSummary.newRecordIds || formatted in conflictingSptIds)
            ) {
                saleIdsWithFinancialRisk.add(spt.saleId)
            }
        }
        local.salePersonTotals.forEach { spt ->
            val formatted = "${spt.saleId}:${spt.personId}"
            if (spt.saleId in sharedSaleIds && formatted in salePersonTotalsSummary.localOnlyRecordIds) {
                saleIdsWithFinancialRisk.add(spt.saleId)
            }
        }

        saleIdsWithFinancialRisk.sorted().forEach { saleId ->
            risks.add(
                RelationalConflictRisk(
                    type = RelationalRiskType.SALE_FINANCIAL_CONFLICT_RISK,
                    collection = BackupCollectionType.SALES,
                    relatedCollection = BackupCollectionType.SALE_ITEMS,
                    recordId = saleId,
                    message = "Venda '$saleId' apresenta divergência entre backup e banco local; mesclagem parcial comprometeria a integridade financeira e os repasses.",
                ),
            )
        }

        return risks
    }

    private fun diffPreference(b: PreferenceDto, l: PreferenceEntity): List<String> = buildList {
        if (b.value != l.value) add("value")
        if (b.updatedAtEpochMs != l.updatedAtEpochMs) add("updatedAtEpochMs")
    }

    private fun diffPerson(b: PersonDto, l: PersonEntity): List<String> = buildList {
        if (b.name != l.name) add("name")
        if (b.normalizedName != l.normalizedName) add("normalizedName")
        if (b.colorIndex != l.colorIndex) add("colorIndex")
        if (b.createdAtEpochMs != l.createdAtEpochMs) add("createdAtEpochMs")
        if (b.updatedAtEpochMs != l.updatedAtEpochMs) add("updatedAtEpochMs")
    }

    private fun diffProduct(b: ProductDto, l: ProductEntity): List<String> = buildList {
        if (b.name != l.name) add("name")
        if (b.priceCents != l.priceCents) add("priceCents")
        if (b.active != l.active) add("active")
        if (b.feeResponsibilityMode != l.feeResponsibilityMode) add("feeResponsibilityMode")
        if (b.feeOwnerPersonId != l.feeOwnerPersonId) add("feeOwnerPersonId")
        if (b.createdAtEpochMs != l.createdAtEpochMs) add("createdAtEpochMs")
        if (b.updatedAtEpochMs != l.updatedAtEpochMs) add("updatedAtEpochMs")
        if (b.categoryId != l.categoryId) add("categoryId")
        if (b.subcategoryId != l.subcategoryId) add("subcategoryId")
    }

    private fun diffProductParticipant(b: ProductParticipantDto, l: ProductParticipantEntity): List<String> = buildList {
        if (b.amountCents != l.amountCents) add("amountCents")
        if (b.sortOrder != l.sortOrder) add("sortOrder")
    }

    private fun diffMachine(b: MachineDto, l: MachineEntity): List<String> = buildList {
        if (b.name != l.name) add("name")
        if (b.normalizedName != l.normalizedName) add("normalizedName")
        if (b.debitRateBps != l.debitRateBps) add("debitRateBps")
        if (b.creditEnabled != l.creditEnabled) add("creditEnabled")
        if (b.creditDefaultRateBps != l.creditDefaultRateBps) add("creditDefaultRateBps")
        if (b.brandRatesEnabled != l.brandRatesEnabled) add("brandRatesEnabled")
        if (b.installmentsEnabled != l.installmentsEnabled) add("installmentsEnabled")
        if (b.createdAtEpochMs != l.createdAtEpochMs) add("createdAtEpochMs")
        if (b.updatedAtEpochMs != l.updatedAtEpochMs) add("updatedAtEpochMs")
        if (b.maxInstallments != l.maxInstallments) add("maxInstallments")
    }

    private fun diffMachineBrandRate(b: MachineBrandRateDto, l: MachineBrandRateEntity): List<String> = buildList {
        if (b.rateBps != l.rateBps) add("rateBps")
        if (b.debitRateBps != l.debitRateBps) add("debitRateBps")
    }

    private fun diffMachineInstallmentRate(
        b: MachineInstallmentRateDto,
        l: MachineInstallmentRateEntity,
    ): List<String> = buildList {
        if (b.addonBps != l.addonBps) add("addonBps")
    }

    private fun diffSale(b: SaleDto, l: SaleEntity): List<String> = buildList {
        if (b.soldAtEpochMs != l.soldAtEpochMs) add("soldAtEpochMs")
        if (b.updatedAtEpochMs != l.updatedAtEpochMs) add("updatedAtEpochMs")
        if (b.paymentMethod != l.paymentMethod) add("paymentMethod")
        if (b.originalSubtotalCents != l.originalSubtotalCents) add("originalSubtotalCents")
        if (b.discountTotalCents != l.discountTotalCents) add("discountTotalCents")
        if (b.finalAmountCents != l.finalAmountCents) add("finalAmountCents")
        if (b.feeTotalCents != l.feeTotalCents) add("feeTotalCents")
        if (b.netTotalCents != l.netTotalCents) add("netTotalCents")
        if (b.discountMode != l.discountMode) add("discountMode")
        if (b.discountTargetProductId != l.discountTargetProductId) add("discountTargetProductId")
        if (b.discountTargetProductName != l.discountTargetProductName) add("discountTargetProductName")
        if (b.machineId != l.machineId) add("machineId")
        if (b.machineName != l.machineName) add("machineName")
        if (b.machineDebitRateBps != l.machineDebitRateBps) add("machineDebitRateBps")
        if (b.machineCreditDefaultRateBps != l.machineCreditDefaultRateBps) add("machineCreditDefaultRateBps")
        if (b.machineBrandRatesEnabled != l.machineBrandRatesEnabled) add("machineBrandRatesEnabled")
        if (b.machineInstallmentsEnabled != l.machineInstallmentsEnabled) add("machineInstallmentsEnabled")
        if (b.cardBrand != l.cardBrand) add("cardBrand")
        if (b.installments != l.installments) add("installments")
        if (b.automaticRateBps != l.automaticRateBps) add("automaticRateBps")
        if (b.manualRateOverrideBps != l.manualRateOverrideBps) add("manualRateOverrideBps")
        if (b.effectiveFeeRateBps != l.effectiveFeeRateBps) add("effectiveFeeRateBps")
        if (b.note != l.note) add("note")
    }

    private fun diffSaleItem(b: SaleItemDto, l: SaleItemEntity): List<String> = buildList {
        if (b.saleId != l.saleId) add("saleId")
        if (b.sortOrder != l.sortOrder) add("sortOrder")
        if (b.productId != l.productId) add("productId")
        if (b.productName != l.productName) add("productName")
        if (b.quantity != l.quantity) add("quantity")
        if (b.unitPriceOriginalCents != l.unitPriceOriginalCents) add("unitPriceOriginalCents")
        if (b.originalSubtotalCents != l.originalSubtotalCents) add("originalSubtotalCents")
        if (b.discountAmountCents != l.discountAmountCents) add("discountAmountCents")
        if (b.finalAmountCents != l.finalAmountCents) add("finalAmountCents")
        if (b.appliedFeeRateBps != l.appliedFeeRateBps) add("appliedFeeRateBps")
        if (b.feeAmountCents != l.feeAmountCents) add("feeAmountCents")
        if (b.netAmountCents != l.netAmountCents) add("netAmountCents")
        if (b.feeResponsibilityMode != l.feeResponsibilityMode) add("feeResponsibilityMode")
        if (b.feeOwnerPersonId != l.feeOwnerPersonId) add("feeOwnerPersonId")
        if (b.feeOwnerPersonName != l.feeOwnerPersonName) add("feeOwnerPersonName")
    }

    private fun diffSaleItemParticipant(
        b: SaleItemParticipantDto,
        l: SaleItemParticipantEntity,
    ): List<String> = buildList {
        if (b.sortOrder != l.sortOrder) add("sortOrder")
        if (b.personName != l.personName) add("personName")
        if (b.personColorIndex != l.personColorIndex) add("personColorIndex")
        if (b.originalUnitAmountCents != l.originalUnitAmountCents) add("originalUnitAmountCents")
        if (b.originalSubtotalCents != l.originalSubtotalCents) add("originalSubtotalCents")
        if (b.discountAmountCents != l.discountAmountCents) add("discountAmountCents")
        if (b.grossAmountCents != l.grossAmountCents) add("grossAmountCents")
        if (b.feeAmountCents != l.feeAmountCents) add("feeAmountCents")
        if (b.netAmountCents != l.netAmountCents) add("netAmountCents")
    }

    private fun diffSalePersonTotal(
        b: SalePersonTotalDto,
        l: SalePersonTotalEntity,
    ): List<String> = buildList {
        if (b.sortOrder != l.sortOrder) add("sortOrder")
        if (b.personName != l.personName) add("personName")
        if (b.personColorIndex != l.personColorIndex) add("personColorIndex")
        if (b.grossAmountCents != l.grossAmountCents) add("grossAmountCents")
        if (b.feeAmountCents != l.feeAmountCents) add("feeAmountCents")
        if (b.netAmountCents != l.netAmountCents) add("netAmountCents")
    }
}
