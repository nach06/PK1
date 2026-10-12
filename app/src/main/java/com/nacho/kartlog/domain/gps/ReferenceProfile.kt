package com.nacho.kartlog.domain.gps

import kotlin.math.hypot

/** Punto de una vuelta en metros alrededor de la meta, con el tiempo (ms) desde que se cruzó la meta al abrirla. */
data class RefPoint(val x: Double, val y: Double, val tMs: Double)

/**
 * Vuelta de referencia para el delta en vuelta (dominio puro): el trazado de una vuelta completa, remuestreado cada ~[STEP_M]
 * metros, con el tiempo en cada punto. Dado un punto cualquiera del circuito se busca el punto más cercano **del trazado de
 * referencia** (proyección) y se lee ahí el tiempo: `delta = t_actual − t_referencia(posición)`. Al no depender de la
 * distancia recorrida por cada vuelta (que cambia con las trazadas y el ruido), al cruzar la meta el delta coincide con
 * la diferencia de tiempos de las dos vueltas.
 */
class ReferenceProfile private constructor(
    private val xs: DoubleArray,
    private val ys: DoubleArray,
    private val ts: DoubleArray,
    private val cum: DoubleArray,
) {
    val size: Int get() = xs.size

    /** Tiempo total de la vuelta de referencia (ms). */
    val lapTimeMs: Long get() = Math.round(ts.last())

    /** Longitud del trazado de referencia (m). */
    val lengthM: Double get() = cum.last()

    /** Tiempo (ms) en el punto [index] del perfil. */
    fun timeAt(index: Int): Double = ts[index]

    /** Posición proyectada sobre el perfil: tiempo de referencia (ms), distancia al trazado (m) y segmento donde cae. */
    data class Match(val timeMs: Double, val offTrackM: Double, val segment: Int)

    /**
     * Proyecta (x, y) sobre el perfil buscando solo entre los segmentos [fromSegment] − [BACK] … [fromSegment] + [AHEAD]:
     * así un tramo del circuito que pasa cerca de otro (horquillas, cruces) no se confunde con él.
     */
    fun project(x: Double, y: Double, fromSegment: Int): Match {
        val last = xs.size - 2
        val lo = (fromSegment - BACK).coerceIn(0, last)
        val hi = (fromSegment + AHEAD).coerceIn(lo, last)
        var bestD = Double.MAX_VALUE
        var bestT = 0.0
        var bestK = lo
        for (k in lo..hi) {
            val dx = xs[k + 1] - xs[k]
            val dy = ys[k + 1] - ys[k]
            val len2 = dx * dx + dy * dy
            val u = if (len2 <= 1e-9) 0.0 else (((x - xs[k]) * dx + (y - ys[k]) * dy) / len2).coerceIn(0.0, 1.0)
            val d = hypot(x - (xs[k] + u * dx), y - (ys[k] + u * dy))
            if (d < bestD) {
                bestD = d
                bestK = k
                bestT = ts[k] + u * (ts[k + 1] - ts[k])
            }
        }
        return Match(bestT, bestD, bestK)
    }

    companion object {
        /** Separación aproximada entre puntos del perfil. */
        const val STEP_M = 5.0
        private const val BACK = 4
        private const val AHEAD = 80 // ≈ 400 m por delante del último punto encontrado

        /** Perfil a partir de los puntos de una vuelta (de meta a meta). `null` si es demasiado corta o degenerada. */
        fun from(points: List<RefPoint>): ReferenceProfile? {
            if (points.size < 3) return null
            val px = DoubleArray(points.size) { points[it].x }
            val py = DoubleArray(points.size) { points[it].y }
            val pt = DoubleArray(points.size) { points[it].tMs }
            val pc = DoubleArray(points.size)
            for (i in 1 until points.size) pc[i] = pc[i - 1] + hypot(px[i] - px[i - 1], py[i] - py[i - 1])
            val total = pc.last()
            if (total < 50.0 || pt.last() <= 0.0) return null

            // Remuestreo a pasos de STEP_M (y siempre el último punto).
            val count = maxOf(2, (total / STEP_M).toInt() + 1)
            val xs = ArrayList<Double>(count + 1)
            val ys = ArrayList<Double>(count + 1)
            val ts = ArrayList<Double>(count + 1)
            val cs = ArrayList<Double>(count + 1)
            var seg = 0
            var s = 0.0
            while (true) {
                val target = minOf(s, total)
                while (seg < points.size - 2 && pc[seg + 1] < target) seg++
                val span = pc[seg + 1] - pc[seg]
                val u = if (span <= 1e-9) 0.0 else ((target - pc[seg]) / span).coerceIn(0.0, 1.0)
                xs += px[seg] + u * (px[seg + 1] - px[seg])
                ys += py[seg] + u * (py[seg + 1] - py[seg])
                ts += pt[seg] + u * (pt[seg + 1] - pt[seg])
                cs += target
                if (target >= total) break
                s += STEP_M
            }
            return ReferenceProfile(xs.toDoubleArray(), ys.toDoubleArray(), ts.toDoubleArray(), cs.toDoubleArray())
        }

        /** Perfil de una vuelta recalculada del trazado guardado ([TrackLapPath]). */
        fun from(path: TrackLapPath): ReferenceProfile? = from(path.points.map { RefPoint(it.x.toDouble(), it.y.toDouble(), it.tMs.toDouble()) })

    }
}

/** De dónde sale la vuelta de referencia. */
enum class ReferenceSource { SESSION, CIRCUIT }

/** Mejor vuelta con trazado del circuito, para tener referencia desde la primera vuelta de una tanda nueva. */
fun interface ReferenceProvider {
    suspend fun bestFor(circuitId: Long, finish: com.nacho.kartlog.domain.model.FinishLine): ReferenceProfile?
}
