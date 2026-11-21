package com.mixvendas3d.app.data.repository

import com.mixvendas3d.app.data.local.dao.PreferenceDao
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import com.mixvendas3d.app.domain.models.AppPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface PreferenceRepository {
    fun observePreference(key: String): Flow<AppPreference?>
    fun observePreferenceCount(): Flow<Int>
    suspend fun getPreference(key: String): AppPreference?
    suspend fun savePreference(key: String, value: String)
    suspend fun deletePreference(key: String)
}

class RoomPreferenceRepository(
    private val preferenceDao: PreferenceDao,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) : PreferenceRepository {

    override fun observePreference(key: String): Flow<AppPreference?> =
        preferenceDao.observeByKey(key).map { entity -> entity?.toDomain() }

    override fun observePreferenceCount(): Flow<Int> = preferenceDao.observeCount()

    override suspend fun getPreference(key: String): AppPreference? =
        preferenceDao.getByKey(key)?.toDomain()

    override suspend fun savePreference(key: String, value: String) {
        val trimmedKey = key.trim()
        require(trimmedKey.isNotEmpty()) { "A chave da preferência não pode ficar vazia." }
        preferenceDao.upsert(
            PreferenceEntity(
                key = trimmedKey,
                value = value.trim(),
                updatedAtEpochMs = clockMillis(),
            )
        )
    }

    override suspend fun deletePreference(key: String) {
        preferenceDao.deleteByKey(key.trim())
    }

    private fun PreferenceEntity.toDomain(): AppPreference = AppPreference(
        key = key,
        value = value,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
