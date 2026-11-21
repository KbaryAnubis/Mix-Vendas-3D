package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "machine_installment_rates",
    primaryKeys = ["machine_id", "installments"],
    foreignKeys = [
        ForeignKey(
            entity = MachineEntity::class,
            parentColumns = ["id"],
            childColumns = ["machine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["machine_id"], name = "index_machine_installment_rates_machine_id"),
    ],
)
data class MachineInstallmentRateEntity(
    @ColumnInfo(name = "machine_id")
    val machineId: String,
    @ColumnInfo(name = "installments")
    val installments: Int,
    @ColumnInfo(name = "addon_bps")
    val addonBps: Int,
)
