package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository

/** Convierte una tanda cronometrada en una sesión normal: vueltas con origen `GPS` y trazado en `tracks/{id}.csv`. */
class GpsSessionSaver(
    private val sessions: SessionRepository,
    private val laps: LapRepository,
    private val tracks: TrackStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Devuelve el id de la sesión creada, o `null` si no hubo ninguna vuelta cerrada (no se crea nada). */
    suspend fun save(recording: GpsRecording): Long? {
        if (recording.laps.isEmpty()) return null
        val setup = recording.setup
        val sessionId = sessions.create(
            NewSession(
                circuitId = setup.circuitId,
                dateTime = now(),
                type = SessionType.FREE,
                kartNumber = setup.kartNumber,
                ballastKg = setup.ballastKg,
                ambientTempC = setup.ambientTempC,
                trackTempC = setup.trackTempC,
                trackCondition = setup.trackCondition,
                notes = "",
            ),
        )
        laps.addAll(sessionId, recording.laps, LapSource.GPS)
        if (recording.fixes.isNotEmpty()) tracks.save(sessionId, recording.fixes)
        return sessionId
    }
}
