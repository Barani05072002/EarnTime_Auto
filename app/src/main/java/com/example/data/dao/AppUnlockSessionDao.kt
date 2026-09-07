package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.data.entities.AppUnlockSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUnlockSessionDao {

    /** Every session that still has budget left and has not been closed. */
    @Query("SELECT * FROM app_unlock_sessions WHERE endedAt IS NULL AND consumedSeconds < grantedSeconds")
    fun getActiveSessions(): Flow<List<AppUnlockSessionEntity>>

    @Query("SELECT * FROM app_unlock_sessions WHERE endedAt IS NULL AND consumedSeconds < grantedSeconds")
    suspend fun getActiveSessionsSync(): List<AppUnlockSessionEntity>

    @Query(
        "SELECT * FROM app_unlock_sessions " +
            "WHERE packageName = :packageName AND endedAt IS NULL AND consumedSeconds < grantedSeconds " +
            "ORDER BY startedAt ASC LIMIT 1"
    )
    suspend fun getActiveSessionFor(packageName: String): AppUnlockSessionEntity?

    @Query("SELECT * FROM app_unlock_sessions WHERE id = :id")
    suspend fun getById(id: Int): AppUnlockSessionEntity?

    @Query("SELECT * FROM app_unlock_sessions ORDER BY startedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<AppUnlockSessionEntity>>

    @Insert
    suspend fun insert(session: AppUnlockSessionEntity): Long

    @Update
    suspend fun update(session: AppUnlockSessionEntity)

    /** Persist the meter without clobbering concurrent edits to the rest of the row. */
    @Query("UPDATE app_unlock_sessions SET consumedSeconds = :consumedSeconds, lastTickAt = :lastTickAt WHERE id = :id")
    suspend fun updateProgress(id: Int, consumedSeconds: Int, lastTickAt: Long)

    @Query(
        "UPDATE app_unlock_sessions SET endedAt = :endedAt, endReason = :reason, lastTickAt = :endedAt " +
            "WHERE id = :id AND endedAt IS NULL"
    )
    suspend fun endSession(id: Int, endedAt: Long, reason: String)

    @Query(
        "UPDATE app_unlock_sessions SET endedAt = :endedAt, endReason = :reason, lastTickAt = :endedAt " +
            "WHERE packageName = :packageName AND endedAt IS NULL"
    )
    suspend fun endSessionsFor(packageName: String, endedAt: Long, reason: String)
}
