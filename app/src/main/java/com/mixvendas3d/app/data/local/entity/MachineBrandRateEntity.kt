package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "machine_brand_rates",
    primaryKeys = ["machine_id", "brand_key"],
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["machine_id"], name = "index_machine_brand_rates_machine_id"),
    ],
)
data class MachineBrandRateEntity(
    @ColumnInfo(name = "machine_id")
    val machineId: String,
    @ColumnInfo(name = "brand_key")
    val brandKey: String,
    @ColumnInfo(name = "rate_bps")
    val rateBps: Int? = null,
    @ColumnInfo(name = "debit_rate_bps")
    val debitRateBps: Int? = null,
)
