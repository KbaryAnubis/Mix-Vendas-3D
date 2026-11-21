package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "sale_person_totals",
    primaryKeys = ["sale_id", "person_id"],
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sale_id"], name = "index_sale_person_totals_sale_id"),
    ],
)
data class SalePersonTotalEntity(
    @ColumnInfo(name = "sale_id")
    val saleId: String,
    @ColumnInfo(name = "person_id")
    val personId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "person_name")
    val personName: String,
    @ColumnInfo(name = "person_color_index")
    val personColorIndex: Int,
    @ColumnInfo(name = "gross_amount_cents")
    val grossAmountCents: Long,
    @ColumnInfo(name = "fee_amount_cents")
    val feeAmountCents: Long,
    @ColumnInfo(name = "net_amount_cents")
    val netAmountCents: Long,
)
