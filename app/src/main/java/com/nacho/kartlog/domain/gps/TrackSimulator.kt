package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Circuito ovalado sintético («estadio»: dos rectas unidas por dos semicírculos) con la meta en mitad de la
 * primera recta. Sirve para los tests del detector y para el modo simulador de la build debug.
 *
 * @param straightM longitud de cada recta; @param radiusM radio de las curvas; @param headingDeg rumbo de la
 * recta de meta; el resto de parámetros sitúan el circuito en el mapa.
 */
class OvalTrack(
    val straightM: Double = 220.0,
    val radiusM: Double = 35.0,
    val headingDeg: Double = 87.0,
    val originLat: Double = 41.8626,
    val originLon: Double = -0.7931,
    val gateWidthM: Int = 20,
) {
    val perimeterM: Double = 2 * straightM + 2 * PI * radiusM
    val frame = LocalFrame(originLat, originLon)
    val finishLine = FinishLine(originLat, originLon, headingDeg, gateWidthM)

    /** Posición (m, respecto a la meta) tras recorrer [s] metros desde la meta. */
    fun pointAt(s: Double): XY {
        var d = ((s % perimeterM) + perimeterM) % perimeterM
        val l = straightM
        val r = radiusM
        val arc = PI * r
        // Sistema de la pista: +X' en el sentido de la marcha en la recta de meta, +Y' a la izquierda.
        val (xp, yp) = when {
            d < l / 2 -> d to 0.0
            d < l / 2 + arc -> {
                val a = (d - l / 2) / r - PI / 2
                (l / 2 + r * cos(a)) to (r + r * sin(a))
            }
            d < l / 2 + arc + l -> {
                val e = d - l / 2 - arc
                (l / 2 - e) to 2 * r
            }
            d < l / 2 + 2 * arc + l -> {
                val a = (d - l / 2 - arc - l) / r + PI / 2
                (-l / 2 + r * cos(a)) to (r + r * sin(a))
            }
            else -> {
                d -= l / 2 + 2 * arc + l
                (-l / 2 + d) to 0.0
            }
        }
        val f = GeoMath.unit(headingDeg)
        val left = XY(-f.y, f.x)
        return XY(xp * f.x + yp * left.x, xp * f.y + yp * left.y)
    }

    /** Dirección de la marcha (vector unitario) en [s]. */
    fun tangentAt(s: Double): XY {
        val a = pointAt(s)
        val b = pointAt(s + 0.5)
        val len = sqrt((b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y))
        return XY((b.x - a.x) / len, (b.y - a.y) / len)
    }
}

/** Trazado simulado y los instantes **reales** en que cruzó la meta (para comparar con lo que mide el detector). */
class SimulatedSession(val fixes: List<GpsFix>, val crossingNs: List<Long>, val lapTimesMs: List<Long>)

object TrackSimulator {
    private const val BASE_NS = 1_234_567_000_000L

    /**
     * Genera fixes a [hz] para un coche que da una vuelta por cada elemento de [lapTimesMs] (velocidad constante
     * dentro de cada vuelta). Empieza [leadInM] metros antes de la meta, así que el primer cruce solo arranca el
     * cronómetro, y sigue [leadOutM] metros tras la última.
     *
     * @param lateralNoiseM / alongNoiseM desviación típica (gaussiana, con semilla) del error perpendicular y
     * paralelo a la marcha.
     * @param dropFixes índices de fix que se pierden (no se emiten).
     */
    fun simulate(
        track: OvalTrack,
        lapTimesMs: List<Long>,
        hz: Double = 1.0,
        leadInM: Double = 60.0,
        leadOutM: Double = 60.0,
        accuracyM: Float = 4f,
        lateralNoiseM: Double = 0.0,
        alongNoiseM: Double = 0.0,
        dropFixes: Set<Int> = emptySet(),
        seed: Long = 42,
    ): SimulatedSession {
        require(lapTimesMs.isNotEmpty())
        val p = track.perimeterM
        val speeds = lapTimesMs.map { p / (it / 1000.0) }
        val firstCross = leadInM / speeds.first()
        val crossings = mutableListOf(firstCross)
        lapTimesMs.forEach { crossings += crossings.last() + it / 1000.0 }
        val end = crossings.last() + leadOutM / speeds.last()

        // s(t): metros recorridos desde la meta (negativo antes de la primera pasada).
        fun s(t: Double): Double = when {
            t < firstCross -> -(leadInM - speeds.first() * t)
            else -> {
                var k = 0
                while (k < lapTimesMs.size - 1 && t >= crossings[k + 1]) k++
                p * k + speeds[k] * (t - crossings[k])
            }
        }
        fun v(t: Double): Double = if (t < firstCross) speeds.first() else {
            var k = 0
            while (k < lapTimesMs.size - 1 && t >= crossings[k + 1]) k++
            speeds[k]
        }

        val rnd = Random(seed)
        val fixes = mutableListOf<GpsFix>()
        var i = 0
        while (i / hz <= end) {
            val t = i / hz
            if (i !in dropFixes) {
                val sPos = s(t)
                var pos = track.pointAt(sPos)
                if (lateralNoiseM > 0.0 || alongNoiseM > 0.0) {
                    val tan = track.tangentAt(sPos)
                    val along = rnd.nextGaussian() * alongNoiseM
                    val lateral = rnd.nextGaussian() * lateralNoiseM
                    pos = XY(pos.x + tan.x * along - tan.y * lateral, pos.y + tan.y * along + tan.x * lateral)
                }
                val (lat, lon) = track.frame.toLatLon(pos.x, pos.y)
                fixes += GpsFix(BASE_NS + Math.round(t * 1e9), lat, lon, v(t).toFloat(), accuracyM)
            }
            i++
        }
        return SimulatedSession(
            fixes = fixes,
            crossingNs = crossings.map { BASE_NS + Math.round(it * 1e9) },
            lapTimesMs = lapTimesMs,
        )
    }
}
