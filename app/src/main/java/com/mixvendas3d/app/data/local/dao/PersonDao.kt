package com.mixvendas3d.app.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mixvendas3d.app.data.local.entity.PersonEntity
import kotlinx.coroutines.flow.Flow

data class PersonWithCountRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(name = "created_at_epoch_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms") val updatedAtEpochMs: Long,
    @ColumnInfo(name = "linked_product_count") val linkedProductCount: Int,
)

sealed interface PersonDeletionResult {
    data object Deleted : PersonDeletionResult
    data object NotFound : PersonDeletionResult
    data class BlockedByLinks(val personName: String) : PersonDeletionResult
    data object DeleteFailed : PersonDeletionResult
}

internal fun isForeignKeyConstraintViolation(exception: SQLiteConstraintException): Boolean {
    val message = exception.message ?: return true
    if (message.isBlank()) return true
    return message.contains("FOREIGN KEY", ignoreCase = true) ||
        message.contains("SQLITE_CONSTRAINT_FOREIGNKEY", ignoreCase = true) ||
        message.contains("787")
}

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY created_at_epoch_ms ASC, name ASC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query(
        """
        SELECT p.*,
            (
                SELECT COUNT(DISTINCT pp.product_id)
                FROM product_participants pp
                WHERE pp.person_id = p.id
            ) AS linked_product_count
        FROM people p
        ORDER BY p.created_at_epoch_ms ASC, p.name ASC
        """,
    )
    fun observeAllWithUsageCount(): Flow<List<PersonWithCountRow>>

    @Query("SELECT * FROM people WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PersonEntity?

    @Query("SELECT * FROM people ORDER BY created_at_epoch_ms ASC, id ASC")
    suspend fun getAllPeople(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): PersonEntity?

    @Query("SELECT id FROM people")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COALESCE(MAX(color_index), -1) FROM people")
    suspend fun getMaxColorIndex(): Int

    @Query("SELECT COUNT(*) FROM product_participants WHERE person_id = :personId")
    suspend fun countProductParticipantLinks(personId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE fee_owner_person_id = :personId")
    suspend fun countProductFeeOwnerLinks(personId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PersonEntity)

    @Update
    suspend fun update(entity: PersonEntity): Int

    @Query("UPDATE people SET name = :name, normalized_name = :normalizedName, updated_at_epoch_ms = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateName(
        id: String,
        name: String,
        normalizedName: String,
        updatedAtEpochMs: Long,
    )

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Transaction
    suspend fun deletePersonSafely(personId: String): PersonDeletionResult {
        val existing = getById(personId) ?: return PersonDeletionResult.NotFound
        val participantLinks = countProductParticipantLinks(personId)
        val ownerLinks = countProductFeeOwnerLinks(personId)
        if (participantLinks > 0 || ownerLinks > 0) {
            return PersonDeletionResult.BlockedByLinks(existing.name)
        }
        val deleted = try {
            deleteById(personId)
        } catch (e: SQLiteConstraintException) {
            if (isForeignKeyConstraintViolation(e)) {
                return PersonDeletionResult.BlockedByLinks(existing.name)
            }
            throw e
        }
        return if (deleted > 0) {
            PersonDeletionResult.Deleted
        } else {
            PersonDeletionResult.DeleteFailed
        }
    }
}
