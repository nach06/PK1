package com.nacho.kartlog.data.gps

import com.nacho.kartlog.domain.gps.ReferenceProfile
import com.nacho.kartlog.domain.gps.ReferenceProvider
import com.nacho.kartlog.domain.gps.TrackAnalysis
import com.nacho.kartlog.domain.gps.TrackStore
import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Mejor vuelta GPS **con trazado guardado** de un circuito, para tener referencia del delta en vuelta desde la primera vuelta.
 * Mira las tandas GPS del circuito por orden de mejor vuelta y recalcula la vuelta con la meta actual (si la meta cambió, la
 * vuelta recalculada es la que vale). Solo prueba las [MAX_SESSIONS] mejores para no leer decenas de ficheros.
 */
class CircuitReferenceLoader(
    private val sessions: SessionRepository,
    private val laps: LapRepository,
    private val tracks: TrackStore,
) : ReferenceProvider {
    override suspend fun bestFor(circuitId: Long, finish: FinishLine): ReferenceProfile? = withContext(Dispatchers.IO) {
        val candidates = sessions.observeSummaries().first()
            .filter { it.circuit.id == circuitId }
            .mapNotNull { summary ->
                val best = laps.observeBySession(summary.session.id).first().filter { it.source == LapSource.GPS }.minOfOrNull { it.timeMs }
                best?.let { summary.session.id to it }
            }
            .sortedBy { it.second }
            .take(MAX_SESSIONS)
        var bestProfile: ReferenceProfile? = null
        for ((sessionId, _) in candidates) {
            val fixes = runCatching { tracks.load(sessionId) }.getOrDefault(emptyList())
            if (fixes.size < 3) continue
            val path = TrackAnalysis.build(fixes, finish).laps.minByOrNull { it.timeMs } ?: continue
            val profile = ReferenceProfile.from(path) ?: continue
            if (bestProfile == null || profile.lapTimeMs < bestProfile.lapTimeMs) bestProfile = profile
        }
        bestProfile
    }

    private companion object {
        const val MAX_SESSIONS = 3
    }
}
