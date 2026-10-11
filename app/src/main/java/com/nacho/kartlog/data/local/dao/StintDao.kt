package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nacho.kartlog.data.local.entity.StintEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StintDao {
    @Insert
    suspend fun insert(stint: StintEntity): Long

    @Query("SELECT * FROM stints WHERE sessionId = :sessionId ORDER BY startLap, id")
    fun observeBySession(sessionId: Long): Flow<List<StintEntity>>
}
