package com.mixvendas3d.app.data.repository

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.mixvendas3d.app.data.local.dao.SaleDao
import com.mixvendas3d.app.domain.calculation.SalePersistenceException
import com.mixvendas3d.app.domain.calculation.SalePersistenceValidator
import com.mixvendas3d.app.domain.models.PersistedSale
import com.mixvendas3d.app.domain.models.SaleCalculationResult
import com.mixvendas3d.app.domain.reporting.DailySalesAggregator
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SaleRepository {
    fun observeSales(): Flow<List<PersistedSale>>
    fun observeSalesByPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<PersistedSale>>
    fun observeSalesByDateRange(
        fromDate: LocalDate,
        toDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<PersistedSale>>

    fun observeSaleById(saleId: String): Flow<PersistedSale?>
    suspend fun getSaleById(saleId: String): PersistedSale?
    suspend fun saveSale(result: SaleCalculationResult): PersistedSale
    suspend fun saveSale(
        result: SaleCalculationResult,
        saleDate: LocalDate?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PersistedSale = saveSale(result)

    suspend fun updateSale(saleId: String, result: SaleCalculationResult): PersistedSale
    suspend fun updateSale(
        saleId: String,
        result: SaleCalculationResult,
        saleDate: LocalDate?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PersistedSale = updateSale(saleId, result)

    suspend fun deleteSale(saleId: String)
}

class RoomSaleRepository(
    private val database: RoomDatabase,
    private val saleDao: SaleDao,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val onBeforeInsertChildren: suspend (String) -> Unit = {},
) : SaleRepository {

    override fun observeSales(): Flow<List<PersistedSale>> {
        return saleDao.observeAllWithDetails().map { rows ->
            rows.map(SaleEntityMapper::toDomain)
        }
    }

    override fun observeSalesByPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<PersistedSale>> {
        if (fromEpochMs > toEpochMs) {
            throw SalePersistenceException("Período inválido: a data inicial não pode ser posterior à final.")
        }
        return saleDao.observeByPeriodWithDetails(fromEpochMs, toEpochMs).map { rows ->
            rows.map(SaleEntityMapper::toDomain)
        }
    }

    override fun observeSalesByDateRange(
        fromDate: LocalDate,
        toDate: LocalDate,
        zoneId: ZoneId,
    ): Flow<List<PersistedSale>> {
        if (fromDate.isAfter(toDate)) {
            throw SalePersistenceException("Período inválido: a data inicial não pode ser posterior à final.")
        }
        val fromEpochMs = fromDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val toEpochMs = toDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L
        return observeSalesByPeriod(fromEpochMs, toEpochMs)
    }

    override fun observeSaleById(saleId: String): Flow<PersistedSale?> {
        if (saleId.isBlank()) {
            throw SalePersistenceException("Identificador da venda inválido.")
        }
        return saleDao.observeWithDetailsById(saleId).map { row ->
            row?.let(SaleEntityMapper::toDomain)
        }
    }

    override suspend fun getSaleById(saleId: String): PersistedSale? {
        if (saleId.isBlank()) {
            throw SalePersistenceException("Identificador da venda inválido.")
        }
        return saleDao.getWithDetailsById(saleId)?.let(SaleEntityMapper::toDomain)
    }

    override suspend fun saveSale(result: SaleCalculationResult): PersistedSale {
        return saveSale(result = result, saleDate = null, zoneId = ZoneId.systemDefault())
    }

    override suspend fun saveSale(
        result: SaleCalculationResult,
        saleDate: LocalDate?,
        zoneId: ZoneId,
    ): PersistedSale {
        // Validação estrita antes de abrir transação no banco
        SalePersistenceValidator.validateForPersistence(result)

        val saleId = idGenerator()
        val now = clockMillis()
        val resolvedSoldAt = if (saleDate != null) {
            DailySalesAggregator.combineLocalDateWithTimeOf(
                saleDate = saleDate,
                referenceEpochMs = now,
                zoneId = zoneId,
            )
        } else {
            now
        }
        if (resolvedSoldAt < 0L) {
            throw SalePersistenceException("Data da venda inválida.")
        }
        val resolvedUpdatedAt = maxOf(now, resolvedSoldAt)
        val mapped = SaleEntityMapper.toEntities(
            saleId = saleId,
            soldAtEpochMs = resolvedSoldAt,
            updatedAtEpochMs = resolvedUpdatedAt,
            result = result,
            itemIdGenerator = idGenerator,
        )

        database.withTransaction {
            saleDao.insertSale(mapped.sale)
            onBeforeInsertChildren(saleId)
            saleDao.insertSaleItems(mapped.items)
            saleDao.insertSaleItemParticipants(mapped.participants)
            saleDao.insertSalePersonTotals(mapped.personTotals)
        }

        return saleDao.getWithDetailsById(saleId)?.let(SaleEntityMapper::toDomain)
            ?: error("Venda recém-gravada não encontrada no banco após transação.")
    }

    override suspend fun updateSale(saleId: String, result: SaleCalculationResult): PersistedSale {
        return updateSale(saleId = saleId, result = result, saleDate = null, zoneId = ZoneId.systemDefault())
    }

    override suspend fun updateSale(
        saleId: String,
        result: SaleCalculationResult,
        saleDate: LocalDate?,
        zoneId: ZoneId,
    ): PersistedSale {
        if (saleId.isBlank()) {
            throw SalePersistenceException("Identificador da venda para atualização inválido.")
        }
        // Validação estrita antes de abrir transação no banco
        SalePersistenceValidator.validateForPersistence(result)

        database.withTransaction {
            val existingHeader = saleDao.getSaleHeaderById(saleId)
                ?: throw SalePersistenceException("Venda não encontrada para atualização.")

            val resolvedSoldAt = if (saleDate != null) {
                DailySalesAggregator.combineLocalDateWithTimeOf(
                    saleDate = saleDate,
                    referenceEpochMs = existingHeader.soldAtEpochMs,
                    zoneId = zoneId,
                )
            } else {
                existingHeader.soldAtEpochMs
            }
            if (resolvedSoldAt < 0L) {
                throw SalePersistenceException("Data da venda inválida.")
            }

            val updatedAt = maxOf(clockMillis(), resolvedSoldAt)
            val mapped = SaleEntityMapper.toEntities(
                saleId = existingHeader.id,
                soldAtEpochMs = resolvedSoldAt,
                updatedAtEpochMs = updatedAt,
                result = result,
                itemIdGenerator = idGenerator,
            )

            val updatedRows = saleDao.updateSale(mapped.sale)
            if (updatedRows == 0) {
                throw SalePersistenceException("Não foi possível atualizar o cabeçalho da venda.")
            }
            // CASCADE apaga automaticamente os registros antigos de sale_item_participants
            saleDao.deleteItemsBySaleId(saleId)
            saleDao.deletePersonTotalsBySaleId(saleId)
            onBeforeInsertChildren(saleId)
            saleDao.insertSaleItems(mapped.items)
            saleDao.insertSaleItemParticipants(mapped.participants)
            saleDao.insertSalePersonTotals(mapped.personTotals)
        }

        return saleDao.getWithDetailsById(saleId)?.let(SaleEntityMapper::toDomain)
            ?: error("Venda atualizada não encontrada no banco após transação.")
    }

    override suspend fun deleteSale(saleId: String) {
        if (saleId.isBlank()) {
            throw SalePersistenceException("Identificador da venda para exclusão inválido.")
        }
        database.withTransaction {
            val deleted = saleDao.deleteById(saleId)
            if (deleted == 0) {
                throw SalePersistenceException("Venda não encontrada para exclusão.")
            }
        }
    }
}
