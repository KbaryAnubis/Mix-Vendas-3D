package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "sale_item_participants",
    primaryKeys = ["sale_item_id", "person_id"],
    foreignKeys = [
        ForeignKey(
            entity = SaleItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sale_item_id"], name = "index_sale_item_participants_sale_item_id"),
    ],
)
data class SaleItemParticipantEntity(
    @ColumnInfo(name = "sale_item_id")
    val saleItemId: String,
    @ColumnInfo(name = "person_id")
    val personId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "person_name")
    val personName: String,
    @ColumnInfo(name = "person_color_index")
    val personColorIndex: Int,
    @ColumnInfo(name = "original_unit_amount_cents")
    val originalUnitAmountCents: Long,
    @ColumnInfo(name = "original_subtotal_cents")
    val originalSubtotalCents: Long,
    @ColumnInfo(name = "discount_amount_cents")
    val discountAmountCents: Long,
    @ColumnInfo(name = "gross_amount_cents")
    val grossAmountCents: Long,
    @ColumnInfo(name = "fee_amount_cents")
    val feeAmountCents: Long,
    @ColumnInfo(name = "net_amount_cents")
    val netAmountCents: Long,
)
