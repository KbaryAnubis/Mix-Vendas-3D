package com.mixvendas3d.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mixvendas3d.app.data.local.entity.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM app_preferences ORDER BY updated_at_epoch_ms DESC")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM app_preferences ORDER BY pref_key ASC")
    suspend fun getAllPreferences(): List<PreferenceEntity>

    @Query("SELECT * FROM app_preferences WHERE pref_key = :key LIMIT 1")
    fun observeByKey(key: String): Flow<PreferenceEntity?>

    @Query("SELECT * FROM app_preferences WHERE pref_key = :key LIMIT 1")
    suspend fun getByKey(key: String): PreferenceEntity?

    @Query("SELECT COUNT(*) FROM app_preferences")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PreferenceEntity)

    @Update
    suspend fun update(entity: PreferenceEntity): Int

    @Query("DELETE FROM app_preferences WHERE pref_key = :key")
    suspend fun deleteByKey(key: String)
}
