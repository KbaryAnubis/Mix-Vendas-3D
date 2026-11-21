package com.mixvendas3d.app.domain.models

data class AppPreference(
    val key: String,
    val value: String,
    val updatedAtEpochMs: Long,
)
