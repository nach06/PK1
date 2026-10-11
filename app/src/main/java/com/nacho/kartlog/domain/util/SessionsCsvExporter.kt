package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first

/** Lee todas las tandas y vueltas y las convierte a CSV. */
class SessionsCsvExporter(
    private val sessions: SessionRepository,
    private val laps: LapRepository,
) {
    /** Devuelve `null` si no hay ninguna vuelta que exportar. */
    suspend fun export(): String? {
        val allLaps = laps.observeAll().first()
        if (allLaps.isEmpty()) return null
        return AllSessionsCsv.build(sessions.observeSummaries().first(), allLaps)
    }
}
