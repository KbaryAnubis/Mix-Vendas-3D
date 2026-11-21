package com.mixvendas3d.app.data.repository

import androidx.room.withTransaction
import com.mixvendas3d.app.data.local.AppDatabase
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineWithRates
import com.mixvendas3d.app.data.local.entity.PersonEntity
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.data.local.entity.ProductEntity
import com.mixvendas3d.app.data.local.entity.ProductParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SaleItemWithParticipants
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.export.JsonBackupExporter
import com.mixvendas3d.app.domain.export.SaleDto
import com.mixvendas3d.app.domain.export.SaleItemDto
import com.mixvendas3d.app.domain.export.SaleItemParticipantDto
import com.mixvendas3d.app.domain.export.SalePersonTotalDto
import com.mixvendas3d.app.domain.models.CardBrand
import com.mixvendas3d.app.domain.models.CustomBrandKeyCodec
import com.mixvendas3d.app.domain.models.CustomCardBrandRate
import com.mixvendas3d.app.domain.models.FeeResponsibilityMode
import com.mixvendas3d.app.domain.models.InstallmentTiers
import com.mixvendas3d.app.domain.models.Machine
import com.mixvendas3d.app.domain.models.MoneyCents
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.Person
import com.mixvendas3d.app.domain.models.Product
import com.mixvendas3d.app.domain.models.ProductParticipantSplit
import com.mixvendas3d.app.domain.update.SystemUpdateAnalysisResult
import com.mixvendas3d.app.domain.update.SystemUpdateAnalyzer
import com.mixvendas3d.app.domain.update.SystemUpdateBatchSummary
import com.mixvendas3d.app.domain.update.SystemUpdateExecutionResult
import com.mixvendas3d.app.domain.update.SystemUpdateFailureReason
import com.mixvendas3d.app.domain.update.SystemUpdateUndoResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

interface SystemUpdateRepository {
    suspend fun analyze(): SystemUpdateAnalysisResult
    suspend fun getUndoBatchSummary(): SystemUpdateBatchSummary?
    suspend fun applyUpdate(
        expectedFingerprint: String,
        allowPartialUpdate: Boolean,
    ): SystemUpdateExecutionResult

    suspend fun revertLastUpdate(): SystemUpdateUndoResult
}

internal data class PersistedSystemUpdateBatchDto(
    val formatVersion: Int = 1,
    val batchId: String,
    val appliedAtEpochMs: Long,
    val appliedAsPartial: Boolean,
    val untouchedConflictSaleIds: List<String>,
    val entries: List<PersistedSystemUpdateEntryDto>,
)

internal data class PersistedSystemUpdateEntryDto(
    val saleId: String,
    val afterUpdatedAtEpochMs: Long,
    val afterCalculationHash: String,
    val beforeSale: SaleDto,
    val beforeItems: List<SaleItemDto>,
    val beforeItemParticipants: List<SaleItemParticipantDto>,
    val beforePersonTotals: List<SalePersonTotalDto>,
)

class RoomSystemUpdateRepository(
    private val database: AppDatabase,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val onAfterPartialUpdateWrite: suspend (Int) -> Unit = {},
    private val onAfterPartialUndoWrite: suspend (Int) -> Unit = {},
) : SystemUpdateRepository {

    private val moshi: Moshi = Moshi.Builder()
        .add(Long::class.javaPrimitiveType!!, JsonBackupExporter.exactLongJsonAdapter)
        .add(Long::class.javaObjectType, JsonBackupExporter.exactLongJsonAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()

    private val batchAdapter = moshi.adapter(PersistedSystemUpdateBatchDto::class.java).serializeNulls()

    override suspend fun analyze(): SystemUpdateAnalysisResult {
        return database.withTransaction {
            val snapshot = readSnapshotInsideTransaction()
            SystemUpdateAnalyzer.analyze(
                sales = snapshot.sales,
                people = snapshot.people,
                products = snapshot.products,
                machines = snapshot.machines,
                analyzedAtEpochMs = clockMillis(),
            )
        }
    }

    override suspend fun getUndoBatchSummary(): SystemUpdateBatchSummary? {
        return database.withTransaction {
            val pref = database.preferenceDao().getByKey(SYSTEM_UPDATE_LAST_BATCH_KEY) ?: return@withTransaction null
            val batch = runCatching { batchAdapter.fromJson(pref.value) }.getOrNull() ?: return@withTransaction null
            val blockReasons = inspectUndoConflictsInsideTransaction(batch)
            SystemUpdateBatchSummary(
                batchId = batch.batchId,
                appliedAtEpochMs = batch.appliedAtEpochMs,
                updatedSalesCount = batch.entries.size,
                skippedConflictCount = batch.untouchedConflictSaleIds.size,
                appliedAsPartial = batch.appliedAsPartial,
                canUndoSafely = blockReasons.isEmpty(),
                undoBlockReasons = blockReasons,
            )
        }
    }

    override suspend fun applyUpdate(
        expectedFingerprint: String,
        allowPartialUpdate: Boolean,
    ): SystemUpdateExecutionResult {
        var didBeginWrite = false
        return try {
            database.withTransaction {
                val snapshot = readSnapshotInsideTransaction()
                val currentAnalysis = SystemUpdateAnalyzer.analyze(
                    sales = snapshot.sales,
                    people = snapshot.people,
                    products = snapshot.products,
                    machines = snapshot.machines,
                    analyzedAtEpochMs = clockMillis(),
                )

                if (currentAnalysis.localStateFingerprint != expectedFingerprint) {
                    return@withTransaction SystemUpdateExecutionResult(
                        success = false,
                        failureReason = SystemUpdateFailureReason.LOCAL_STATE_CHANGED,
                        errorMessage = "Os dados locais mudaram após a análise. Analise os ajustes novamente.",
                        rolledBack = false,
                    )
                }

                if (currentAnalysis.salesToUpdateCount == 0) {
                    return@withTransaction SystemUpdateExecutionResult(
                        success = false,
                        failureReason = SystemUpdateFailureReason.NOTHING_TO_UPDATE,
                        errorMessage = "Nenhuma venda elegível para atualização.",
                        rolledBack = false,
                    )
                }

                if (currentAnalysis.requiresReviewCount > 0 && !allowPartialUpdate) {
                    return@withTransaction SystemUpdateExecutionResult(
                        success = false,
                        failureReason = SystemUpdateFailureReason.CONFLICTS_NOT_CONFIRMED_FOR_PARTIAL,
                        errorMessage = "Corrija estes conflitos antes de atualizar ou confirme explicitamente a atualização parcial.",
                        rolledBack = false,
                    )
                }

                val saleDao = database.saleDao()
                val preferenceDao = database.preferenceDao()
                val batchTimestamp = clockMillis()
                val batchId = idGenerator()
                val batchEntries = ArrayList<PersistedSystemUpdateEntryDto>(currentAnalysis.updatableItems.size)

                // Valida e captura snapshots exatos antes de qualquer escrita
                val preparedWrites = currentAnalysis.updatableItems.map { candidate ->
                    SalePersistenceValidator.validateForPersistence(candidate.proposedCalculation)
                    val existingWithDetails = saleDao.getWithDetailsById(candidate.saleId)
                        ?: throw IllegalStateException("Venda ${candidate.saleId} não encontrada durante a transação.")
                    val afterUpdatedAt = maxOf(batchTimestamp, existingWithDetails.sale.updatedAtEpochMs + 1L)
                    val afterHash = SystemUpdateAnalyzer.computeCalculationFingerprint(candidate.proposedCalculation)
                    val mappedAfter = SaleEntityMapper.toEntities(
                        saleId = candidate.saleId,
                        soldAtEpochMs = existingWithDetails.sale.soldAtEpochMs,
                        updatedAtEpochMs = afterUpdatedAt,
                        result = candidate.proposedCalculation,
                        itemIdGenerator = idGenerator,
                    )
                    batchEntries.add(
                        PersistedSystemUpdateEntryDto(
                            saleId = candidate.saleId,
                            afterUpdatedAtEpochMs = afterUpdatedAt,
                            afterCalculationHash = afterHash,
                            beforeSale = existingWithDetails.sale.toDto(),
                            beforeItems = existingWithDetails.items.map { it.item.toDto() },
                            beforeItemParticipants = existingWithDetails.items.flatMap { rel ->
                                rel.participants.map { it.toDto() }
                            },
                            beforePersonTotals = existingWithDetails.personTotals.map { it.toDto() },
                        ),
                    )
                    mappedAfter
                }

                didBeginWrite = true
                preparedWrites.forEachIndexed { index, mapped ->
                    val updatedRows = saleDao.updateSale(mapped.sale)
                    if (updatedRows == 0) {
                        throw IllegalStateException("Falha ao atualizar venda ${mapped.sale.id}.")
                    }
                    saleDao.deleteItemsBySaleId(mapped.sale.id)
                    saleDao.deletePersonTotalsBySaleId(mapped.sale.id)
                    saleDao.insertSaleItems(mapped.items)
                    saleDao.insertSaleItemParticipants(mapped.participants)
                    saleDao.insertSalePersonTotals(mapped.personTotals)
                    onAfterPartialUpdateWrite(index)
                }

                val batchRecord = PersistedSystemUpdateBatchDto(
                    batchId = batchId,
                    appliedAtEpochMs = batchTimestamp,
                    appliedAsPartial = currentAnalysis.requiresReviewCount > 0,
                    untouchedConflictSaleIds = currentAnalysis.conflictItems.map { it.saleId },
                    entries = batchEntries,
                )
                val serializedBatch = batchAdapter.toJson(batchRecord)
                    ?: throw IllegalStateException("Falha ao serializar lote reversível de atualização.")

                preferenceDao.upsert(
                    PreferenceEntity(
                        key = SYSTEM_UPDATE_LAST_BATCH_KEY,
                        value = serializedBatch,
                        updatedAtEpochMs = batchTimestamp,
                    ),
                )

                val summary = SystemUpdateBatchSummary(
                    batchId = batchId,
                    appliedAtEpochMs = batchTimestamp,
                    updatedSalesCount = batchEntries.size,
                    skippedConflictCount = currentAnalysis.requiresReviewCount,
                    appliedAsPartial = currentAnalysis.requiresReviewCount > 0,
                    canUndoSafely = true,
                    undoBlockReasons = emptyList(),
                )

                SystemUpdateExecutionResult(
                    success = true,
                    failureReason = SystemUpdateFailureReason.NONE,
                    rolledBack = false,
                    updatedSalesCount = batchEntries.size,
                    preservedConflictCount = currentAnalysis.requiresReviewCount,
                    batchSummary = summary,
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Throwable) {
            SystemUpdateExecutionResult(
                success = false,
                failureReason = SystemUpdateFailureReason.EXECUTION_FAILED_ROLLED_BACK,
                errorMessage = ex.message ?: "Erro ao atualizar vendas no Room; nenhuma alteração foi mantida.",
                rolledBack = didBeginWrite,
            )
        }
    }

    override suspend fun revertLastUpdate(): SystemUpdateUndoResult {
        var didBeginWrite = false
        return try {
            database.withTransaction {
                val preferenceDao = database.preferenceDao()
                val saleDao = database.saleDao()
                val pref = preferenceDao.getByKey(SYSTEM_UPDATE_LAST_BATCH_KEY)
                    ?: return@withTransaction SystemUpdateUndoResult(
                        success = false,
                        failureReason = SystemUpdateFailureReason.NO_UNDOABLE_BATCH,
                        errorMessage = "Não existe atualização anterior disponível para desfazer.",
                        rolledBack = false,
                    )

                val batch = runCatching { batchAdapter.fromJson(pref.value) }.getOrNull()
                    ?: return@withTransaction SystemUpdateUndoResult(
                        success = false,
                        failureReason = SystemUpdateFailureReason.NO_UNDOABLE_BATCH,
                        errorMessage = "Registro de reversão inválido ou indisponível.",
                        rolledBack = false,
                    )

                val conflicts = inspectUndoConflictsInsideTransaction(batch)
                if (conflicts.isNotEmpty()) {
                    return@withTransaction SystemUpdateUndoResult(
                        success = false,
                        failureReason = SystemUpdateFailureReason.UNDO_CONFLICT_POST_UPDATE_MODIFIED,
                        errorMessage = "Uma ou mais vendas foram editadas ou removidas após a atualização.",
                        rolledBack = false,
                        conflictMessages = conflicts,
                    )
                }

                // Valida integridade de cada snapshot anterior antes de escrever
                val itemsBySaleId = batch.beforeItemsBySaleId()
                val participantsByItemId = batch.beforeParticipantsByItemId()
                val totalsBySaleId = batch.beforePersonTotalsBySaleId()

                val restorations = batch.entries.map { entry ->
                    val saleEntity = entry.beforeSale.toEntity()
                    val itemEntities = itemsBySaleId[entry.saleId].orEmpty().map { it.toEntity() }
                    val participantEntities = itemEntities.flatMap { item ->
                        participantsByItemId[item.id].orEmpty().map { it.toEntity() }
                    }
                    val personTotalEntities = totalsBySaleId[entry.saleId].orEmpty().map { it.toEntity() }

                    val saleWithDetails = SaleWithDetails(
                        sale = saleEntity,
                        items = itemEntities.map { item ->
                            SaleItemWithParticipants(
                                item = item,
                                participants = participantEntities.filter { it.saleItemId == item.id },
                            )
                        },
                        personTotals = personTotalEntities,
                    )
                    val domainSale = SaleEntityMapper.toDomain(saleWithDetails)
                    SalePersistenceValidator.validateForPersistence(domainSale.calculation)

                    Triple(saleEntity, itemEntities, participantEntities to personTotalEntities)
                }

                didBeginWrite = true
                restorations.forEachIndexed { index, (saleEntity, itemEntities, children) ->
                    val (participantEntities, personTotalEntities) = children
                    val updatedRows = saleDao.updateSale(saleEntity)
                    if (updatedRows == 0) {
                        throw IllegalStateException("Falha ao restaurar venda ${saleEntity.id}.")
                    }
                    saleDao.deleteItemsBySaleId(saleEntity.id)
                    saleDao.deletePersonTotalsBySaleId(saleEntity.id)
                    saleDao.insertSaleItems(itemEntities)
                    saleDao.insertSaleItemParticipants(participantEntities)
                    saleDao.insertSalePersonTotals(personTotalEntities)
                    onAfterPartialUndoWrite(index)
                }

                preferenceDao.deleteByKey(SYSTEM_UPDATE_LAST_BATCH_KEY)

                SystemUpdateUndoResult(
                    success = true,
                    failureReason = SystemUpdateFailureReason.NONE,
                    rolledBack = false,
                    revertedSalesCount = restorations.size,
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (ex: Throwable) {
            SystemUpdateUndoResult(
                success = false,
                failureReason = SystemUpdateFailureReason.UNDO_FAILED_ROLLED_BACK,
                errorMessage = ex.message ?: "Falha ao desfazer a última atualização; estado preservado.",
                rolledBack = didBeginWrite,
            )
        }
    }

    private suspend fun inspectUndoConflictsInsideTransaction(
        batch: PersistedSystemUpdateBatchDto,
    ): List<String> {
        val saleDao = database.saleDao()
        val conflicts = ArrayList<String>()
        batch.entries.forEach { entry ->
            val current = saleDao.getWithDetailsById(entry.saleId)
            if (current == null) {
                conflicts.add("Venda \"${entry.beforeSale.id}\" foi removida após a atualização.")
            } else {
                val domain = SaleEntityMapper.toDomain(current)
                val currentHash = SystemUpdateAnalyzer.computeCalculationFingerprint(domain.calculation)
                if (domain.updatedAtEpochMs != entry.afterUpdatedAtEpochMs ||
                    currentHash != entry.afterCalculationHash
                ) {
                    conflicts.add("Venda \"${entry.beforeSale.id}\" foi editada ou restaurada após a atualização.")
                }
            }
        }
        return conflicts
    }

    private data class SnapshotData(
        val people: List<Person>,
        val products: List<Product>,
        val machines: List<Machine>,
        val sales: List<PersistedSale>,
    )

    private suspend fun readSnapshotInsideTransaction(): SnapshotData {
        val personDao = database.personDao()
        val productDao = database.productDao()
        val machineDao = database.machineDao()
        val saleDao = database.saleDao()

        val peopleEntities = personDao.getAllPeople()
        val peopleById = peopleEntities.associateBy { it.id }
        val people = peopleEntities.map { it.toDomain() }

        val productEntities = productDao.getAllProducts()
        val participantsByProductId = productDao.getAllProductParticipants().groupBy { it.productId }
        val products = productEntities.map { entity ->
            entity.toDomain(
                participants = participantsByProductId[entity.id].orEmpty(),
                peopleById = peopleById,
            )
        }

        val machineEntities = machineDao.getAllMachines()
        val brandsByMachine = machineDao.getAllBrandRates().groupBy { it.machineId }
        val installmentsByMachine = machineDao.getAllInstallmentRates().groupBy { it.machineId }
        val machines = machineEntities.map { entity ->
            MachineWithRates(
                machine = entity,
                brandRates = brandsByMachine[entity.id].orEmpty(),
                installmentRates = installmentsByMachine[entity.id].orEmpty(),
            ).toDomain()
        }

        val saleEntities = saleDao.getAllSales()
        val itemsBySaleId = saleDao.getAllSaleItems().groupBy { it.saleId }
        val participantsByItemId = saleDao.getAllSaleItemParticipants().groupBy { it.saleItemId }
        val personTotalsBySaleId = saleDao.getAllSalePersonTotals().groupBy { it.saleId }

        val sales = saleEntities.map { saleEntity ->
            val itemsWithParticipants = itemsBySaleId[saleEntity.id].orEmpty()
                .sortedBy { it.sortOrder }
                .map { item ->
                    SaleItemWithParticipants(
                        item = item,
                        participants = participantsByItemId[item.id].orEmpty().sortedBy { it.sortOrder },
                    )
                }
            SaleEntityMapper.toDomain(
                SaleWithDetails(
                    sale = saleEntity,
                    items = itemsWithParticipants,
                    personTotals = personTotalsBySaleId[saleEntity.id].orEmpty().sortedBy { it.sortOrder },
                ),
            )
        }

        return SnapshotData(
            people = people,
            products = products,
            machines = machines,
            sales = sales,
        )
    }

    private fun PersonEntity.toDomain(): Person = Person(
        id = id,
        name = name,
        colorIndex = colorIndex,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ProductEntity.toDomain(
        participants: List<ProductParticipantEntity>,
        peopleById: Map<String, PersonEntity>,
    ): Product = Product(
        id = id,
        name = name,
        price = MoneyCents(priceCents),
        active = active,
        feeResponsibilityMode = FeeResponsibilityMode.fromStorageKey(feeResponsibilityMode),
        feeOwnerPersonId = feeOwnerPersonId,
        participants = participants.sortedBy { it.sortOrder }.map { rel ->
            val person = peopleById[rel.personId]
            ProductParticipantSplit(
                personId = rel.personId,
                personName = person?.name ?: "",
                personColorIndex = person?.colorIndex ?: 0,
                amount = MoneyCents(rel.amountCents),
            )
        },
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun MachineWithRates.toDomain(): Machine {
        val mappedBrands = linkedMapOf<CardBrand, Int>()
        val mappedDebitBrands = linkedMapOf<CardBrand, Int>()
        val mappedCustomBrands = mutableListOf<CustomCardBrandRate>()
        brandRates.forEach { entity ->
            val stdBrand = CardBrand.fromStorageKey(entity.brandKey)
            if (stdBrand != null) {
                if (entity.rateBps != null) mappedBrands[stdBrand] = entity.rateBps
                if (entity.debitRateBps != null) mappedDebitBrands[stdBrand] = entity.debitRateBps
            } else {
                val decodedName = CustomBrandKeyCodec.decodeNameOrNull(entity.brandKey)
                if (decodedName != null) {
                    mappedCustomBrands.add(
                        CustomCardBrandRate(
                            key = entity.brandKey,
                            name = decodedName,
                            creditRateBps = entity.rateBps,
                            debitRateBps = entity.debitRateBps,
                        ),
                    )
                }
            }
        }
        val sortedCustomBrands = mappedCustomBrands.sortedWith(
            compareBy<CustomCardBrandRate> { CustomBrandKeyCodec.normalizeBrandName(it.name) }
                .thenBy { it.name }
                .thenBy { it.key },
        )
        val mappedInstallments = linkedMapOf<Int, Int>()
        InstallmentTiers.SUPPORTED.forEach { tier ->
            installmentRates.firstOrNull { it.installments == tier }?.addonBps?.let { addon ->
                mappedInstallments[tier] = addon
            }
        }
        return Machine(
            id = machine.id,
            name = machine.name,
            debitRateBps = machine.debitRateBps,
            creditEnabled = machine.creditEnabled,
            creditDefaultRateBps = machine.creditDefaultRateBps,
            brandRatesEnabled = machine.brandRatesEnabled,
            brandRatesBps = mappedBrands,
            brandDebitRatesBps = mappedDebitBrands,
            installmentsEnabled = machine.installmentsEnabled,
            installmentAddonsBps = mappedInstallments,
            createdAtEpochMs = machine.createdAtEpochMs,
            updatedAtEpochMs = machine.updatedAtEpochMs,
            maxInstallments = machine.maxInstallments,
            customBrandRates = sortedCustomBrands,
        )
    }

    private fun PersistedSystemUpdateBatchDto.beforeItemsBySaleId(): Map<String, List<SaleItemDto>> =
        entries.associate { it.saleId to it.beforeItems }

    private fun PersistedSystemUpdateBatchDto.beforeParticipantsByItemId(): Map<String, List<SaleItemParticipantDto>> =
        entries.flatMap { it.beforeItemParticipants }.groupBy { it.saleItemId }

    private fun PersistedSystemUpdateBatchDto.beforePersonTotalsBySaleId(): Map<String, List<SalePersonTotalDto>> =
        entries.associate { it.saleId to it.beforePersonTotals }

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

    companion object {
        const val SYSTEM_UPDATE_LAST_BATCH_KEY = "system_update_last_batch_v1"
    }
}
