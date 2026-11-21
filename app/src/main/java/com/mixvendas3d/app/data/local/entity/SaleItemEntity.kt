package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sale_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sale_id"], name = "index_sale_items_sale_id"),
        Index(value = ["sale_id", "product_id"], unique = true, name = "index_sale_items_sale_id_product_id"),
    ],
)
data class SaleItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "sale_id")
    val saleId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "product_name")
    val productName: String,
    @ColumnInfo(name = "quantity")
    val quantity: Int,
    @ColumnInfo(name = "unit_price_original_cents")
    val unitPriceOriginalCents: Long,
    @ColumnInfo(name = "original_subtotal_cents")
    val originalSubtotalCents: Long,
    @ColumnInfo(name = "discount_amount_cents")
    val discountAmountCents: Long,
    @ColumnInfo(name = "final_amount_cents")
    val finalAmountCents: Long,
    @ColumnInfo(name = "applied_fee_rate_bps")
    val appliedFeeRateBps: Int,
    @ColumnInfo(name = "fee_amount_cents")
    val feeAmountCents: Long,
    @ColumnInfo(name = "net_amount_cents")
    val netAmountCents: Long,
    @ColumnInfo(name = "fee_responsibility_mode")
    val feeResponsibilityMode: String,
    @ColumnInfo(name = "fee_owner_person_id")
    val feeOwnerPersonId: String?,
    @ColumnInfo(name = "fee_owner_person_name")
    val feeOwnerPersonName: String?,
)
