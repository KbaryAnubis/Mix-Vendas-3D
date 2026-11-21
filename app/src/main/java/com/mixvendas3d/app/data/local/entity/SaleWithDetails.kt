package com.mixvendas3d.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class SaleItemWithParticipants(
    @Embedded val item: SaleItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sale_item_id",
    )
    val participants: List<SaleItemParticipantEntity>,
)

data class SaleWithDetails(
    @Embedded val sale: SaleEntity,
    @Relation(
        entity = SaleItemEntity::class,
        parentColumn = "id",
        entityColumn = "sale_id",
    )
    val items: List<SaleItemWithParticipants>,
    @Relation(
        parentColumn = "id",
        entityColumn = "sale_id",
    )
    val personTotals: List<SalePersonTotalEntity>,
)
