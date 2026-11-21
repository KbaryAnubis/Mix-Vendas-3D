package com.mixvendas3d.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mixvendas3d.app.data.local.entity.SaleEntity
import com.mixvendas3d.app.data.local.entity.SaleItemEntity
import com.mixvendas3d.app.data.local.entity.SaleItemParticipantEntity
import com.mixvendas3d.app.data.local.entity.SalePersonTotalEntity
import com.mixvendas3d.app.data.local.entity.SaleWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Transaction
    @Query("SELECT * FROM sales ORDER BY sold_at_epoch_ms DESC, id ASC")
    fun observeAllWithDetails(): Flow<List<SaleWithDetails>>

    @Transaction
    @Query(
        "SELECT * FROM sales " +
            "WHERE sold_at_epoch_ms >= :fromEpochMs AND sold_at_epoch_ms <= :toEpochMs " +
            "ORDER BY sold_at_epoch_ms DESC, id ASC",
    )
    fun observeByPeriodWithDetails(fromEpochMs: Long, toEpochMs: Long): Flow<List<SaleWithDetails>>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    fun observeWithDetailsById(saleId: String): Flow<SaleWithDetails?>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getWithDetailsById(saleId: String): SaleWithDetails?

    @Query("SELECT * FROM sales WHERE id = :saleId LIMIT 1")
    suspend fun getSaleHeaderById(saleId: String): SaleEntity?

    @Query("SELECT * FROM sales ORDER BY sold_at_epoch_ms ASC, id ASC")
    suspend fun getAllSales(): List<SaleEntity>

    @Query("SELECT * FROM sale_items ORDER BY sale_id ASC, sort_order ASC, id ASC")
    suspend fun getAllSaleItems(): List<SaleItemEntity>

    @Query("SELECT * FROM sale_item_participants ORDER BY sale_item_id ASC, person_id ASC")
    suspend fun getAllSaleItemParticipants(): List<SaleItemParticipantEntity>

    @Query("SELECT * FROM sale_person_totals ORDER BY sale_id ASC, person_id ASC")
    suspend fun getAllSalePersonTotals(): List<SalePersonTotalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity)

    @Update
    suspend fun updateSale(sale: SaleEntity): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItemParticipants(participants: List<SaleItemParticipantEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSalePersonTotals(totals: List<SalePersonTotalEntity>)

    @Query(
        "DELETE FROM sale_item_participants WHERE sale_item_id IN (SELECT id FROM sale_items WHERE sale_id = :saleId)",
    )
    suspend fun deleteItemParticipantsBySaleId(saleId: String)

    @Query("DELETE FROM sale_items WHERE sale_id = :saleId")
    suspend fun deleteItemsBySaleId(saleId: String)

    @Query("DELETE FROM sale_person_totals WHERE sale_id = :saleId")
    suspend fun deletePersonTotalsBySaleId(saleId: String)

    @Query("DELETE FROM sales WHERE id = :saleId")
    suspend fun deleteById(saleId: String): Int

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun countSales(): Int

    @Query("SELECT COUNT(*) FROM sale_items")
    suspend fun countSaleItems(): Int

    @Query("SELECT COUNT(*) FROM sale_item_participants")
    suspend fun countSaleItemParticipants(): Int

    @Query("SELECT COUNT(*) FROM sale_person_totals")
    suspend fun countSalePersonTotals(): Int
}
