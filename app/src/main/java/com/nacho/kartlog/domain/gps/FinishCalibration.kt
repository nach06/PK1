package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import kotlin.math.hypot

/**
 * Calcula la línea de meta a partir de las últimas posiciones, en el momento en que el usuario pulsa MARCAR al
 * pasar por la línea. Dominio puro.
 *
 * - **Punto:** la última posición. Si el usuario tarda unos décimas en pulsar, la puerta queda unos metros por
 *   delante; como ese desfase es el mismo en todas las vueltas, **no cambia los tiempos de vuelta** (solo los
 *   cambia si la velocidad de paso varía mucho entre vueltas).
 * - **Rumbo:** del primer al último fix de la ventana (extremo a extremo). Exige que se haya avanzado un mínimo y
 *   que el rumbo de la primera mitad y el de la segunda coincidan (recta): en una curva el rumbo de paso sería ambiguo.
 */
object FinishCalibration {
    const val WINDOW_NS = 4_000_000_000L
    const val MIN_DISTANCE_M = 8.0
    const val MAX_ACCURACY_M = 15f
    const val MAX_BEND_DEG = 20.0

    enum class Failure { NO_FIXES, POOR_ACCURACY, TOO_SLOW, NOT_STRAIGHT }

    sealed interface Outcome {
        data class Ok(
            val finishLine: FinishLine,
            /** Posiciones usadas, para dibujar el trazado reciente. */
            val window: List<GpsFix>,
            val accuracyM: Float,
            val speedMps: Float,
        ) : Outcome

        data class Failed(val reason: Failure) : Outcome
    }

    fun compute(
        recent: List<GpsFix>,
        gateWidthM: Int = FinishLine.DEFAULT_GATE_WIDTH_M,
    ): Outcome {
        val last = recent.lastOrNull() ?: return Outcome.Failed(Failure.NO_FIXES)
        val window = recent.filter { last.elapsedNs - it.elapsedNs in 0..WINDOW_NS }
        if (window.size < 2) return Outcome.Failed(Failure.TOO_SLOW)
        if (window.any { it.accuracyM > MAX_ACCURACY_M }) return Outcome.Failed(Failure.POOR_ACCURACY)

        val frame = LocalFrame(last.lat, last.lon)
        val points = window.map { frame.toXY(it.lat, it.lon) }
        val first = points.first()
        val end = points.last()
        if (hypot(end.x - first.x, end.y - first.y) < MIN_DISTANCE_M) return Outcome.Failed(Failure.TOO_SLOW)

        val mid = points[points.size / 2]
        val bearingFirstHalf = GeoMath.bearingDeg(first, mid)
        val bearingSecondHalf = GeoMath.bearingDeg(mid, end)
        if (kotlin.math.abs(GeoMath.angleDiffDeg(bearingFirstHalf, bearingSecondHalf)) > MAX_BEND_DEG) {
            return Outcome.Failed(Failure.NOT_STRAIGHT)
        }
        val bearing = GeoMath.bearingDeg(first, end)
        return Outcome.Ok(
            finishLine = FinishLine(last.lat, last.lon, bearing, gateWidthM),
            window = window,
            accuracyM = window.maxOf { it.accuracyM },
            speedMps = last.speedMps,
        )
    }
}
