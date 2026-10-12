package com.nacho.kartlog.domain.gps

import kotlin.math.exp

/** Posición (m, en el sistema local de la meta) y velocidad (m/s) calculadas en el último fix. */
data class LapPosition(val x: Double, val y: Double, val vx: Double, val vy: Double)

/**
 * Delta en vuelta (dominio puro): `t_actual − t_referencia(posición)` contra la vuelta de referencia del [TimingState].
 *
 * Entre dos posiciones del GPS (1 Hz) la posición se **extrapola** con la velocidad del último tramo (como mucho
 * [MAX_EXTRAPOLATION_S]) para poder refrescar el delta varias veces por segundo, y el resultado se suaviza con un filtro de
 * primer orden de constante [TAU_S] (retraso ≈ τ ≤ 0,3 s) para que la barra no salte con el ruido del GPS.
 *
 * Error esperado: la posición del GPS tiene unos 1–3 m de error a lo largo de la marcha, que a 15–20 m/s son 0,05–0,2 s;
 * la extrapolación añade el error de cambio de velocidad en 0,5 s de media (frenadas). Por eso el delta intermedio vale ±0,1–0,3 s
 * y no es un tiempo oficial; al cruzar la meta coincide con la diferencia real de vueltas (el cruce se interpola con ±50 ms).
 */
class LiveDeltaTracker {
    private var segment = 0
    private var lapStartNs: Long? = null
    private var reference: ReferenceProfile? = null
    private var smoothed: Double? = null
    private var lastNowNs: Long? = null

    /** Delta en ms (positivo = más lento que la referencia), o `null` sin referencia, sin vuelta en curso o fuera del trazado. */
    fun update(timing: TimingState, nowNs: Long): Long? {
        val ref = timing.reference
        val start = timing.currentLapStartNs
        if (ref == null || start == null) {
            reset(ref, start)
            return null
        }
        if (ref !== reference || start != lapStartNs) reset(ref, start)
        val pos = timing.lapPosition ?: return smoothed?.let(Math::round)
        val arrival = timing.lastFixArrivalNs ?: return null
        val current = timing.currentLapMs(nowNs) ?: return null
        val sinceFixS = (((nowNs - arrival).coerceAtLeast(0)) * timing.playbackSpeed / 1e9).coerceAtMost(MAX_EXTRAPOLATION_S)
        val match = ref.project(pos.x + pos.vx * sinceFixS, pos.y + pos.vy * sinceFixS, segment)
        if (match.offTrackM > MAX_OFF_TRACK_M) return smoothed?.let(Math::round)
        segment = match.segment
        val raw = current - match.timeMs
        val previousNs = lastNowNs
        val previous = smoothed
        val value = if (previous == null || previousNs == null || nowNs <= previousNs || (nowNs - previousNs) > RESET_AFTER_NS) {
            raw
        } else {
            val alpha = 1.0 - exp(-((nowNs - previousNs) / 1e9) / TAU_S)
            previous + alpha * (raw - previous)
        }
        smoothed = value
        lastNowNs = nowNs
        return Math.round(value)
    }

    private fun reset(ref: ReferenceProfile?, start: Long?) {
        reference = ref
        lapStartNs = start
        segment = 0
        smoothed = null
        lastNowNs = null
    }

    companion object {
        const val MAX_EXTRAPOLATION_S = 2.0
        const val MAX_OFF_TRACK_M = 60.0
        const val TAU_S = 0.12
        private const val RESET_AFTER_NS = 3_000_000_000L
    }
}
