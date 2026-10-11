package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import com.nacho.kartlog.domain.repository.StintRepository

/** Convierte una tanda cronometrada en una sesión normal: vueltas con origen `GPS` y trazado en `tracks/{id}.csv`. */
class GpsSessionSaver(
    private val sessions: SessionRepository,
    private val laps: LapRepository,
    private val tracks: TrackStore,
    private val stints: StintRepository? = null,
) {
    /** Devuelve el id de la sesión creada, o `null` si no hubo ninguna vuelta cerrada (no se crea nada). */
    suspend fun save(recording: GpsRecording): Long? {
        if (recording.laps.isEmpty()) return null
        val setup = recording.setup
        val sessionId = sessions.create(
            NewSession(
                circuitId = setup.circuitId,
                // La fecha es la de salida (EMPEZAR), no la de guardar: una tanda guardada horas después sigue en su día.
                dateTime = setup.startedAtMs,
                type = setup.type,
                kartNumber = setup.kartNumber,
                ballastKg = setup.ballastKg,
                ambientTempC = setup.ambientTempC,
                trackTempC = setup.trackTempC,
                trackCondition = setup.trackCondition,
                notes = "",
                durationMin = ((recording.stoppedAtMs - setup.startedAtMs) / 60_000.0).let { Math.round(it).toInt() }.takeIf { it > 0 },
            ),
        )
        laps.addAll(sessionId, recording.laps, LapSource.GPS, stintIdsFor(sessionId, recording))
        if (recording.fixes.isNotEmpty()) tracks.save(sessionId, recording.fixes)
        return sessionId
    }

    /**
     * Resistencia: crea un stint por cada tramo con vueltas (nº de vuelta inicial/final de la tanda guardada, piloto y la parada
     * que lo cierra) y devuelve el stint de cada vuelta. `null` en tandas sin stints.
     */
    private suspend fun stintIdsFor(sessionId: Long, recording: GpsRecording): List<Long?>? {
        val repository = stints ?: return null
        if (recording.setup.type != SessionType.ENDURANCE && recording.stints.size <= 1) return null
        val perLap = recording.laps.indices.map { recording.lapStint.getOrElse(it) { 0 } }
        val idByStint = mutableMapOf<Int, Long>()
        perLap.distinct().forEach { stint ->
            val numbers = perLap.withIndex().filter { it.value == stint }.map { it.index + 1 }
            val info = recording.stints.getOrElse(stint) { StintInfo() }
            idByStint[stint] = repository.add(sessionId, numbers.min(), numbers.max(), info.driverName, info.pitAfterMs)
        }
        return perLap.map { idByStint[it] }
    }
}
