package com.mixvendas3d.app.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mixvendas3d.app.data.local.entity.MachineBrandRateEntity
import com.mixvendas3d.app.data.local.entity.MachineEntity
import com.mixvendas3d.app.data.local.entity.MachineInstallmentRateEntity
import com.mixvendas3d.app.data.local.entity.MachineWithRates
import kotlinx.coroutines.flow.Flow

data class MachineSalesCountRow(
    @ColumnInfo(name = "machine_id") val machineId: String,
    @ColumnInfo(name = "sales_count") val salesCount: Int,
)

sealed interface MachineDeletionOutcome {
    data object Deleted : MachineDeletionOutcome
    data object NotFound : MachineDeletionOutcome
    data class BlockedBySales(val machineName: String, val salesCount: Int) : MachineDeletionOutcome
}

@Dao
interface MachineDao {
    @Transaction
    @Query("SELECT * FROM machines ORDER BY name COLLATE NOCASE ASC")
    fun observeAllWithRates(): Flow<List<MachineWithRates>>

    @Transaction
    @Query("SELECT * FROM machines WHERE id = :machineId LIMIT 1")
    suspend fun getWithRatesById(machineId: String): MachineWithRates?

    @Query("SELECT * FROM machines WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): MachineEntity?

    @Query("SELECT * FROM machines ORDER BY created_at_epoch_ms ASC, id ASC")
    suspend fun getAllMachines(): List<MachineEntity>

    @Query("SELECT * FROM machine_brand_rates ORDER BY machine_id ASC, brand_key ASC")
    suspend fun getAllBrandRates(): List<MachineBrandRateEntity>

    @Query("SELECT * FROM machine_installment_rates ORDER BY machine_id ASC, installments ASC")
    suspend fun getAllInstallmentRates(): List<MachineInstallmentRateEntity>

    @Query("SELECT COUNT(*) FROM sales WHERE machine_id = :machineId")
    suspend fun countSalesUsingMachine(machineId: String): Int

    @Query(
        """
        SELECT machine_id, COUNT(*) AS sales_count
        FROM sales
        WHERE machine_id IS NOT NULL
        GROUP BY machine_id
        """,
    )
    fun observeSalesCountsByMachine(): Flow<List<MachineSalesCountRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMachine(machine: MachineEntity)

    @Update
    suspend fun updateMachine(machine: MachineEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBrandRates(rates: List<MachineBrandRateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBrandRatesStrict(rates: List<MachineBrandRateEntity>)

    @Query("DELETE FROM machine_brand_rates WHERE machine_id = :machineId")
    suspend fun deleteBrandRatesForMachine(machineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallmentRates(rates: List<MachineInstallmentRateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstallmentRatesStrict(rates: List<MachineInstallmentRateEntity>)

    @Query("DELETE FROM machine_installment_rates WHERE machine_id = :machineId")
    suspend fun deleteInstallmentRatesForMachine(machineId: String)

    @Query(
        "UPDATE machines SET credit_enabled = :creditEnabled, updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :machineId",
    )
    suspend fun updateCreditEnabled(
        machineId: String,
        creditEnabled: Boolean,
        updatedAtEpochMs: Long,
    )

    @Query("DELETE FROM machines WHERE id = :machineId")
    suspend fun deleteById(machineId: String): Int

    @Transaction
    suspend fun deleteMachineSafely(machineId: String): MachineDeletionOutcome {
        val existing = getWithRatesById(machineId) ?: return MachineDeletionOutcome.NotFound
        val linkedSales = countSalesUsingMachine(machineId)
        if (linkedSales > 0) {
            return MachineDeletionOutcome.BlockedBySales(existing.machine.name, linkedSales)
        }
        return if (deleteById(machineId) > 0) {
            MachineDeletionOutcome.Deleted
        } else {
            MachineDeletionOutcome.NotFound
        }
    }

    @Transaction
    suspend fun upsertMachineWithRates(
        machine: MachineEntity,
        brandRates: List<MachineBrandRateEntity>,
        installmentRates: List<MachineInstallmentRateEntity>,
        isNew: Boolean,
    ) {
        if (isNew) {
            insertMachine(machine)
        } else {
            updateMachine(machine)
            deleteBrandRatesForMachine(machine.id)
            deleteInstallmentRatesForMachine(machine.id)
        }
        if (brandRates.isNotEmpty()) {
            insertBrandRates(brandRates)
        }
        if (installmentRates.isNotEmpty()) {
            insertInstallmentRates(installmentRates)
        }
    }
}
