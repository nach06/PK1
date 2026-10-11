package com.nacho.kartlog.domain.gps

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Punto en metros respecto al origen de un [LocalFrame]: [x] hacia el este, [y] hacia el norte. */
data class XY(val x: Double, val y: Double)

/**
 * Proyección local plana (equirectangular) alrededor de un punto. A las distancias de un circuito (< 2 km) el
 * error frente a la esfera es de milímetros, y evita trabajar con grados en el detector de meta.
 */
class LocalFrame(val originLat: Double, val originLon: Double) {
    private val cosLat = cos(Math.toRadians(originLat))

    fun toXY(lat: Double, lon: Double) = XY(
        x = Math.toRadians(lon - originLon) * EARTH_RADIUS_M * cosLat,
        y = Math.toRadians(lat - originLat) * EARTH_RADIUS_M,
    )

    fun toLatLon(x: Double, y: Double): Pair<Double, Double> = Pair(
        originLat + Math.toDegrees(y / EARTH_RADIUS_M),
        originLon + Math.toDegrees(x / (EARTH_RADIUS_M * cosLat)),
    )

    companion object {
        const val EARTH_RADIUS_M = 6_371_008.8
    }
}

object GeoMath {
    /** Rumbo en grados [0, 360) desde el norte en sentido horario, del punto 0 al 1 (en el plano local). */
    fun bearingDeg(from: XY, to: XY): Double {
        val deg = Math.toDegrees(atan2(to.x - from.x, to.y - from.y))
        return (deg + 360.0) % 360.0
    }

    /** Vector unitario (este, norte) de un rumbo en grados. */
    fun unit(bearingDeg: Double): XY {
        val r = Math.toRadians(bearingDeg)
        return XY(sin(r), cos(r))
    }

    /** Diferencia angular con signo en (-180, 180]. */
    fun angleDiffDeg(a: Double, b: Double): Double {
        var d = (a - b) % 360.0
        if (d > 180.0) d -= 360.0
        if (d <= -180.0) d += 360.0
        return d
    }

    const val PI_D = PI
}
