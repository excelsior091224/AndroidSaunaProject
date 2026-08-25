package com.totonoi.sauna.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SaunaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SaunaSessionEntity)

    @Query("SELECT * FROM sauna_sessions ORDER BY startMs DESC")
    fun observeAll(): Flow<List<SaunaSessionEntity>>

    @Query("SELECT * FROM sauna_sessions WHERE id = :id")
    suspend fun getById(id: String): SaunaSessionEntity?
}
