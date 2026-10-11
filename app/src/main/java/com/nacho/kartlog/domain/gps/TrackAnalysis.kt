package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Punto del trazado en metros respecto a la meta (x = este, y = norte); [tMs] cuenta desde el inicio de su vuelta. */
data class TrackPt(val x: Float, val y: Float, val speedMps: Float, val tMs: Long)

/** Una vuelta recalculada del trazado: de un cruce de meta al siguiente. */
data class TrackLapPath(val number: Int, val timeMs: Long, val points: List<TrackPt>)

data class TrackBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/** Trazado proyectado a metros alrededor de la meta, con sus vueltas recalculadas y la puerta de meta. */
data class TrackGeometry(
    /** Todas las posiciones, en orden (para mostrar el trazado completo sin vueltas). */
    val all: List<TrackPt>,
    /** Vueltas cerradas por la meta, recalculadas con el detector (numeradas 1..n). */
    val laps: List<TrackLapPath>,
    /** Extremos de la puerta de meta y su sentido de marcha (vector unitario). */
    val gateA: XY,
    val gateB: XY,
    val heading: XY,
    val bounds: TrackBounds,
    val minSpeedMps: Float,
    val maxSpeedMps: Float,
    /** `false` si el circuito no tiene meta: se muestra el trazado completo, sin puerta ni vueltas. */
    val hasGate: Boolean = true,
)

/** Vuelta del trazado emparejada con una vuelta guardada (conserva el nº con el que aparece en la lista). */
data class MatchedLap(val storedNumber: Int, val path: TrackLapPath)

/** Cómo se encaja un trazado en un lienzo: norte arriba, escala uniforme y centrado. */
data class ViewFit(val scale: Float, val offsetX: Float, val offsetY: Float, val heightPx: Float, val minX: Float, val minY: Float) {
    fun px(x: Float) = offsetX + (x - minX) * scale

    /** El eje Y de pantalla crece hacia abajo: el norte queda arriba. */
    fun py(y: Float) = heightPx - (offsetY + (y - minY) * scale)
}

object TrackAnalysis {
    const val MAX_DRAW_POINTS = 2_000

    /**
     * Proyecta [fixes] a metros alrededor de [finish] y los reparte en vueltas pasándolos por el [GateDetector] (el mismo,
     * determinista, que el borrador): no se guarda nada nuevo, las vueltas se recalculan. Los puntos incluyen el
     * instante interpolado de cada cruce para que cada vuelta empiece y acabe exactamente en la meta.
     */
    fun build(fixes: List<GpsFix>, finish: FinishLine?, config: GateConfig = GateConfig()): TrackGeometry {
        val origin = finish ?: fixes.first().let { FinishLine(it.lat, it.lon, 0.0, 0) }
        val frame = LocalFrame(origin.lat, origin.lon)
        val detector = GateDetector(origin, config)
        val crossings = ArrayList<Long>()
        if (finish != null) fixes.forEach { fix -> detector.feed(fix)?.let { crossings += it.crossing.elapsedNs } }

        val all = fixes.map { f ->
            val p = frame.toXY(f.lat, f.lon)
            TrackPt(p.x.toFloat(), p.y.toFloat(), f.speedMps, (f.elapsedNs - (fixes.first().elapsedNs)) / 1_000_000L)
        }

        val laps = ArrayList<TrackLapPath>()
        var cursor = 0
        for (i in 0 until crossings.size - 1) {
            val start = crossings[i]
            val end = crossings[i + 1]
            while (cursor < fixes.size && fixes[cursor].elapsedNs < start) cursor++
            val pts = ArrayList<TrackPt>()
            pts += interpolate(fixes, start, frame, start)
            var j = cursor
            while (j < fixes.size && fixes[j].elapsedNs < end) {
                val f = fixes[j]
                val p = frame.toXY(f.lat, f.lon)
                if (f.elapsedNs > start) pts += TrackPt(p.x.toFloat(), p.y.toFloat(), f.speedMps, Math.round((f.elapsedNs - start) / 1_000_000.0))
                j++
            }
            pts += interpolate(fixes, end, frame, start)
            laps += TrackLapPath(number = i + 1, timeMs = Math.round((end - start) / 1_000_000.0), points = pts)
        }

        val heading = GeoMath.unit(origin.bearingDeg)
        val right = XY(heading.y, -heading.x)
        val half = if (finish == null) 0.0 else finish.gateWidthM / 2.0
        val gateA = XY(-right.x * half, -right.y * half)
        val gateB = XY(right.x * half, right.y * half)
        val speeds = all.map { it.speedMps }
        val xs = all.map { it.x } + listOf(gateA.x.toFloat(), gateB.x.toFloat())
        val ys = all.map { it.y } + listOf(gateA.y.toFloat(), gateB.y.toFloat())
        return TrackGeometry(
            all = all,
            laps = laps,
            gateA = gateA,
            gateB = gateB,
            heading = heading,
            bounds = TrackBounds(xs.min(), ys.min(), xs.max(), ys.max()),
            minSpeedMps = speeds.minOrNull() ?: 0f,
            maxSpeedMps = speeds.maxOrNull() ?: 0f,
            hasGate = finish != null,
        )
    }

    /** Posición interpolada entre los dos fixes que rodean [atNs]; su [TrackPt.tMs] cuenta desde [lapStartNs]. */
    private fun interpolate(fixes: List<GpsFix>, atNs: Long, frame: LocalFrame, lapStartNs: Long): TrackPt {
        var hi = fixes.indexOfFirst { it.elapsedNs >= atNs }
        if (hi < 0) hi = fixes.lastIndex
        val b = fixes[hi]
        val a = fixes[max(0, if (b.elapsedNs > atNs) hi - 1 else hi)]
        val span = (b.elapsedNs - a.elapsedNs).toDouble()
        val t = if (span <= 0.0) 0.0 else ((atNs - a.elapsedNs) / span).coerceIn(0.0, 1.0)
        val pa = frame.toXY(a.lat, a.lon)
        val pb = frame.toXY(b.lat, b.lon)
        return TrackPt(
            x = (pa.x + t * (pb.x - pa.x)).toFloat(),
            y = (pa.y + t * (pb.y - pa.y)).toFloat(),
            speedMps = (a.speedMps + t * (b.speedMps - a.speedMps)).toFloat(),
            tMs = Math.round((atNs - lapStartNs) / 1_000_000.0),
        )
    }

    /**
     * Empareja las vueltas guardadas (`número` → `tiempo`) con las recalculadas: cada tiempo guardado debe coincidir (±[toleranceMs])
     * con una vuelta recalculada, en orden. Devuelve `null` si alguna no coincide (vueltas editadas a mano, borradas con otro criterio…):
     * entonces no se puede asignar posiciones a vueltas con garantías.
     */
    fun matchStoredLaps(geometry: TrackGeometry, stored: List<Pair<Int, Long>>, toleranceMs: Long = 5): List<MatchedLap>? {
        if (stored.isEmpty()) return null
        val out = ArrayList<MatchedLap>()
        var next = 0
        for ((number, timeMs) in stored) {
            var found: TrackLapPath? = null
            while (next < geometry.laps.size) {
                val candidate = geometry.laps[next++]
                if (abs(candidate.timeMs - timeMs) <= toleranceMs) { found = candidate; break }
            }
            out += MatchedLap(number, found ?: return null)
        }
        return out
    }

    /** Submuestreo uniforme a como mucho [max] puntos, conservando el primero y el último. */
    fun decimate(points: List<TrackPt>, max: Int = MAX_DRAW_POINTS): List<TrackPt> {
        if (max < 2 || points.size <= max) return points
        val out = ArrayList<TrackPt>(max)
        val step = (points.size - 1).toDouble() / (max - 1)
        for (i in 0 until max) out += points[Math.round(i * step).toInt().coerceAtMost(points.lastIndex)]
        return out
    }

    /** Escala uniforme que encaja [bounds] en el lienzo con [paddingPx] de margen, centrado y con el norte arriba. */
    fun fit(bounds: TrackBounds, widthPx: Float, heightPx: Float, paddingPx: Float): ViewFit {
        val w = max(bounds.width, 1f)
        val h = max(bounds.height, 1f)
        val scale = min((widthPx - 2 * paddingPx) / w, (heightPx - 2 * paddingPx) / h)
        val offX = (widthPx - w * scale) / 2f
        val offY = (heightPx - h * scale) / 2f
        return ViewFit(scale, offX, offY, heightPx, bounds.minX, bounds.minY)
    }

    /** Fracción de velocidad en [0, 1] dentro de [lo, hi] (0 si no hay rango). */
    fun speedFraction(speedMps: Float, lo: Float, hi: Float): Float =
        if (hi - lo < 1e-3f) 0f else ((speedMps - lo) / (hi - lo)).coerceIn(0f, 1f)

    /**
     * Diferencia de ritmo de cada punto de [lap] respecto a [reference] en el mismo punto del recorrido (emparejados por
     * la fracción de distancia recorrida): positivo = va perdiendo tiempo, negativo = ganando.
     */
    fun paceDeltasMs(lap: TrackLapPath, reference: TrackLapPath): List<Long> {
        val lapFr = distanceFractions(lap.points)
        val refFr = distanceFractions(reference.points)
        return lap.points.indices.map { i ->
            val f = lapFr[i]
            val refT = timeAtFraction(reference.points, refFr, f)
            lap.points[i].tMs - Math.round(refT)
        }
    }

    private fun distanceFractions(points: List<TrackPt>): DoubleArray {
        val d = DoubleArray(points.size)
        for (i in 1 until points.size) d[i] = d[i - 1] + hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble())
        val total = d.last().takeIf { it > 0.0 } ?: 1.0
        for (i in d.indices) d[i] /= total
        return d
    }

    private fun timeAtFraction(points: List<TrackPt>, fractions: DoubleArray, f: Double): Double {
        val hi = fractions.indexOfFirst { it >= f }
        if (hi < 0) return points.last().tMs.toDouble()
        if (hi == 0) return points.first().tMs.toDouble()
        val lo = hi - 1
        val span = fractions[hi] - fractions[lo]
        val t = if (span <= 0.0) 0.0 else (f - fractions[lo]) / span
        return points[lo].tMs + t * (points[hi].tMs - points[lo].tMs)
    }

    /** Punto de [candidates] más cercano a ([px], [py]) en pantalla, si queda a menos de [maxDistPx]. */
    fun nearest(candidates: List<Pair<Int, TrackPt>>, fit: ViewFit, px: Float, py: Float, maxDistPx: Float): Pair<Int, TrackPt>? {
        var best: Pair<Int, TrackPt>? = null
        var bestD = maxDistPx
        for (c in candidates) {
            val d = hypot((fit.px(c.second.x) - px).toDouble(), (fit.py(c.second.y) - py).toDouble()).toFloat()
            if (d <= bestD) { bestD = d; best = c }
        }
        return best
    }
}
