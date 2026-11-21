package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "machines",
    indices = [
        Index(
            value = ["normalized_name"],
            unique = true,
            name = "index_machines_normalized_name",
        ),
    ],
)
data class MachineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String,
    @ColumnInfo(name = "debit_rate_bps")
    val debitRateBps: Int,
    @ColumnInfo(name = "credit_enabled")
    val creditEnabled: Boolean,
    @ColumnInfo(name = "credit_default_rate_bps")
    val creditDefaultRateBps: Int,
    @ColumnInfo(name = "brand_rates_enabled")
    val brandRatesEnabled: Boolean,
    @ColumnInfo(name = "installments_enabled")
    val installmentsEnabled: Boolean,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "max_installments")
    val maxInstallments: Int? = null,
)
