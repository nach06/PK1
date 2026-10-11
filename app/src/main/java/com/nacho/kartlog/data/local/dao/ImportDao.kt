package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.nacho.kartlog.data.local.entity.LapEntity
import com.nacho.kartlog.data.local.entity.ParticipantEntity
import com.nacho.kartlog.data.local.entity.ParticipantLapEntity
import com.nacho.kartlog.data.local.entity.SessionEntity
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.ParticipantImport
import com.nacho.kartlog.data.mapper.toEntity

/** Importación atómica: o se guarda todo o no se guarda nada. */
@Dao
abstract class ImportDao {
    @Insert
    protected abstract suspend fun insertSession(session: SessionEntity): Long

    @Insert
    protected abstract suspend fun insertLap(lap: LapEntity): Long

    @Insert
    protected abstract suspend fun insertParticipant(participant: ParticipantEntity): Long

    @Insert
    protected abstract suspend fun insertParticipantLaps(laps: List<ParticipantLapEntity>)

    @Query("SELECT COALESCE(MAX(number), 0) FROM laps WHERE sessionId = :sessionId")
    protected abstract suspend fun lastLapNumber(sessionId: Long): Int

    @Query("DELETE FROM participants WHERE sessionId = :sessionId")
    protected abstract suspend fun deleteParticipants(sessionId: Long)

    @Transaction
    open suspend fun importSession(request: ImportRequest): Long {
        val sessionId = request.newSession?.let { insertSession(it.toEntity()) } ?: requireNotNull(request.existingSessionId)
        // En una sesión existente la clasificación importada sustituye a la anterior.
        if (request.existingSessionId != null) deleteParticipants(sessionId)

        var number = lastLapNumber(sessionId)
        request.myLapTimesMs.forEach { timeMs ->
            number += 1
            insertLap(LapEntity(sessionId = sessionId, number = number, timeMs = timeMs, source = LapSource.IMPORTED))
        }
        request.me?.let { insertParticipantWithLaps(sessionId, it, isMe = true) }
        request.others.forEach { insertParticipantWithLaps(sessionId, it, isMe = false) }
        return sessionId
    }

    private suspend fun insertParticipantWithLaps(sessionId: Long, p: ParticipantImport, isMe: Boolean) {
        val id = insertParticipant(
            ParticipantEntity(
                sessionId = sessionId,
                position = p.position,
                name = p.name,
                kartNumber = p.kartNumber,
                isMe = isMe,
                bestMs = p.bestMs,
                avgMs = p.avgMs,
                lapCount = p.lapCount,
            ),
        )
        // Las vueltas del propio usuario van a `laps`, no a `participant_laps`.
        if (!isMe && p.lapTimesMs.isNotEmpty()) {
            insertParticipantLaps(
                p.lapTimesMs.mapIndexed { index, ms -> ParticipantLapEntity(participantId = id, number = index + 1, timeMs = ms) },
            )
        }
    }
}
