package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["fee_owner_person_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["fee_owner_person_id"]),
        Index(value = ["category_id"]),
        Index(value = ["subcategory_id"]),
    ],
)
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "price_cents")
    val priceCents: Long,
    @ColumnInfo(name = "active")
    val active: Boolean,
    @ColumnInfo(name = "fee_responsibility_mode")
    val feeResponsibilityMode: String,
    @ColumnInfo(name = "fee_owner_person_id")
    val feeOwnerPersonId: String?,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,
    @ColumnInfo(name = "subcategory_id")
    val subcategoryId: String? = null,
)
