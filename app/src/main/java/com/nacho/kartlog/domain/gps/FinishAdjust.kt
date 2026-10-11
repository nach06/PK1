package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import kotlin.math.abs

/** Ajuste manual de la puerta de meta: desplazarla a lo largo del rumbo y cambiar su ancho. */
object FinishAdjust {
    const val MAX_SHIFT_M = 10
    const val MIN_GATE_M = 10
    const val MAX_GATE_M = 40
    const val GATE_STEP_M = 5

    fun clampShift(meters: Int) = meters.coerceIn(-MAX_SHIFT_M, MAX_SHIFT_M)

    fun clampGate(meters: Int) = meters.coerceIn(MIN_GATE_M, MAX_GATE_M)

    /** La línea [base] movida [shiftM] metros hacia delante (positivo) o atrás (negativo) a lo largo de su rumbo, con ancho [gateWidthM]. */
    fun apply(base: FinishLine, shiftM: Int, gateWidthM: Int): FinishLine {
        val shift = clampShift(shiftM)
        val width = clampGate(gateWidthM)
        if (shift == 0) return base.copy(gateWidthM = width)
        val dir = GeoMath.unit(base.bearingDeg)
        val (lat, lon) = LocalFrame(base.lat, base.lon).toLatLon(dir.x * shift, dir.y * shift)
        return base.copy(lat = lat, lon = lon, gateWidthM = width)
    }

    /** Distancia (m) a lo largo del rumbo de [line] entre dos puntos de la misma meta; positiva si [to] va por delante de [from]. */
    fun alongTrack(line: FinishLine, fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val frame = LocalFrame(fromLat, fromLon)
        val p = frame.toXY(toLat, toLon)
        val dir = GeoMath.unit(line.bearingDeg)
        return p.x * dir.x + p.y * dir.y
    }

    /** `true` si [new] va en sentido contrario (> 90°) a [existing]: el circuito se recorre al revés. */
    fun isReversed(existing: FinishLine, new: FinishLine): Boolean = abs(GeoMath.angleDiffDeg(existing.bearingDeg, new.bearingDeg)) > 90.0
}
