package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation

@Entity(
    tableName = "product_participants",
    primaryKeys = ["product_id", "person_id"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["person_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["person_id"]),
    ],
)
data class ProductParticipantEntity(
    @ColumnInfo(name = "product_id")
    val productId: String,
    @ColumnInfo(name = "person_id")
    val personId: String,
    @ColumnInfo(name = "amount_cents")
    val amountCents: Long,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)

data class ProductParticipantWithPerson(
    @Embedded val participant: ProductParticipantEntity,
    @Relation(
        parentColumn = "person_id",
        entityColumn = "id",
    )
    val person: PersonEntity,
)

data class ProductWithParticipants(
    @Embedded val product: ProductEntity,
    @Relation(
        entity = ProductParticipantEntity::class,
        parentColumn = "id",
        entityColumn = "product_id",
    )
    val participants: List<ProductParticipantWithPerson>,
)
