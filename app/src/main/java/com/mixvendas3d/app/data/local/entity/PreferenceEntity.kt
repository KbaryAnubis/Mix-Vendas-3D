package com.mixvendas3d.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_preferences")
data class PreferenceEntity(
    @PrimaryKey
    @ColumnInfo(name = "pref_key")
    val key: String,
    @ColumnInfo(name = "pref_value")
    val value: String,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
)
