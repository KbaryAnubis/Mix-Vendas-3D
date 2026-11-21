package com.mixvendas3d.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MachineWithRates(
    @Embedded val machine: MachineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "machine_id",
    )
    val brandRates: List<MachineBrandRateEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "machine_id",
    )
    val installmentRates: List<MachineInstallmentRateEntity>,
)
