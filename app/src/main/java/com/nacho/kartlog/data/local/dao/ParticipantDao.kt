package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.nacho.kartlog.data.local.entity.ParticipantEntity
import com.nacho.kartlog.data.local.entity.ParticipantLapEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ParticipantDao {
    @Query("SELECT * FROM participants WHERE sessionId = :sessionId ORDER BY position IS NULL, position, id")
    fun observeBySession(sessionId: Long): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participants")
    fun observeAll(): Flow<List<ParticipantEntity>>

    @Query("SELECT * FROM participant_laps ORDER BY participantId, number")
    fun observeAllLaps(): Flow<List<ParticipantLapEntity>>

    @Query("SELECT * FROM participant_laps WHERE participantId = :participantId ORDER BY number")
    suspend fun lapsOf(participantId: Long): List<ParticipantLapEntity>
}
