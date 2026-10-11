package com.nacho.kartlog.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TrackAnalysisTest {
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L, 59_300L)
    private val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
    private val geometry = TrackAnalysis.build(sim.fixes, oval.finishLine)

    // ---- Proyección y vueltas ----

    @Test
    fun laps_areRecomputedWithTheSameTimesAsTheSimulation() {
        assertEquals(lapTimes.size, geometry.laps.size)
        geometry.laps.forEachIndexed { i, lap ->
            assertEquals(i + 1, lap.number)
            assertTrue("vuelta ${i + 1}: ${lap.timeMs} vs ${lapTimes[i]}", abs(lap.timeMs - lapTimes[i]) <= 60)
        }
    }

    @Test
    fun everyLapStartsAndEndsOnTheFinishLine_andItsClockRunsFromZeroToTheLapTime() {
        val heading = geometry.heading
        geometry.laps.forEach { lap ->
            val first = lap.points.first()
            val last = lap.points.last()
            // En la meta: distancia a la línea (producto escalar con el rumbo) ≈ 0 y a lo largo de la puerta ≤ medio ancho.
            assertTrue("inicio fuera de la línea: ${first.x * heading.x + first.y * heading.y}", abs(first.x * heading.x + first.y * heading.y) < 0.6)
            assertTrue("fin fuera de la línea", abs(last.x * heading.x + last.y * heading.y) < 0.6)
            assertEquals(0L, first.tMs)
            assertEquals(lap.timeMs, last.tMs)
            assertTrue("tiempo no creciente", lap.points.zipWithNext().all { (a, b) -> b.tMs >= a.tMs })
        }
    }

    @Test
    fun everyFixBetweenTheFirstAndLastCrossingBelongsToExactlyOneLap() {
        val total = geometry.laps.sumOf { it.points.size - 2 } // sin los dos puntos interpolados de cada vuelta
        val firstCross = sim.crossingNs.first()
        val lastCross = sim.crossingNs.last()
        val inside = sim.fixes.count { it.elapsedNs > firstCross + 60_000_000L && it.elapsedNs < lastCross - 60_000_000L }
        assertTrue("fixes dentro de las vueltas: $total vs $inside", abs(total - inside) <= 2)
        // Un fix concreto de mitad de la vuelta 2 cae en la vuelta 2 y no en otra.
        val midLap2 = (sim.crossingNs[1] + sim.crossingNs[2]) / 2
        val fix = sim.fixes.minBy { abs(it.elapsedNs - midLap2) }
        val p = LocalFrame(oval.finishLine.lat, oval.finishLine.lon).toXY(fix.lat, fix.lon)
        val owner = geometry.laps.filter { lap -> lap.points.any { abs(it.x - p.x) < 1e-3 && abs(it.y - p.y) < 1e-3 } }
        assertEquals(listOf(2), owner.map { it.number })
    }

    @Test
    fun projectionIsMetricAndNorthUp_boundsContainTheGate() {
        val b = geometry.bounds
        assertTrue("el óvalo mide ~290 × ~70 m: ${b.width} × ${b.height}", b.width in 200f..400f && b.height in 50f..200f)
        assertTrue(b.minX <= geometry.gateA.x && geometry.gateB.x <= b.maxX)
        // La puerta mide el ancho configurado (20 m) y es perpendicular al rumbo.
        val dx = geometry.gateB.x - geometry.gateA.x
        val dy = geometry.gateB.y - geometry.gateA.y
        assertEquals(20.0, kotlin.math.hypot(dx, dy), 1e-6)
        assertEquals(0.0, dx * geometry.heading.x + dy * geometry.heading.y, 1e-6)
    }

    // ---- Emparejamiento con las vueltas guardadas ----

    @Test
    fun storedLaps_matchByTime_keepingTheirNumbers_evenWithDeletedLaps() {
        val stored = listOf(1 to geometry.laps[0].timeMs, 3 to geometry.laps[2].timeMs) // la 2 y la 4 se borraron
        val matched = TrackAnalysis.matchStoredLaps(geometry, stored)
        assertNotNull(matched)
        assertEquals(listOf(1, 3), matched!!.map { it.storedNumber })
        assertEquals(listOf(1, 3), matched.map { it.path.number })
        assertNull("una vuelta editada a mano ya no coincide", TrackAnalysis.matchStoredLaps(geometry, listOf(1 to geometry.laps[0].timeMs + 400)))
        assertNull("más vueltas que el trazado", TrackAnalysis.matchStoredLaps(geometry, geometry.laps.map { it.number to it.timeMs } + (9 to 61_000L)))
        assertNull(TrackAnalysis.matchStoredLaps(geometry, emptyList()))
    }

    // ---- Submuestreo, escala y selección ----

    @Test
    fun decimate_keepsFirstAndLast_andNeverExceedsTheLimit_onA10000PointTrack() {
        val dense = TrackSimulator.simulate(oval, List(18) { 58_000L }, hz = 10.0)
        val g = TrackAnalysis.build(dense.fixes, oval.finishLine)
        assertTrue("trazado grande: ${g.all.size}", g.all.size >= 10_000)
        val started = System.nanoTime()
        val drawn = TrackAnalysis.decimate(g.all)
        assertTrue(drawn.size <= TrackAnalysis.MAX_DRAW_POINTS)
        assertEquals(g.all.first(), drawn.first())
        assertEquals(g.all.last(), drawn.last())
        assertTrue("el submuestreo debe ser instantáneo", (System.nanoTime() - started) / 1_000_000 < 200)
        assertEquals(g.all.size, TrackAnalysis.decimate(g.all, max = g.all.size).size)
        assertEquals(18, g.laps.size)
    }

    @Test
    fun fit_isUniform_centered_andNorthUp() {
        val b = TrackBounds(0f, 0f, 200f, 100f)
        val fit = TrackAnalysis.fit(b, widthPx = 400f, heightPx = 400f, paddingPx = 20f)
        assertEquals(1.8f, fit.scale, 1e-4f) // limitado por el ancho: (400-40)/200
        // Centrado: el trazado de 100 m (180 px) queda en el centro vertical.
        assertEquals(20f, fit.px(0f), 1e-3f)
        assertEquals(380f, fit.px(200f), 1e-3f)
        assertEquals(400f - (400f - 180f) / 2f, fit.py(0f), 1e-3f)
        assertTrue("el norte (y mayor) queda arriba", fit.py(100f) < fit.py(0f))
        // Todos los puntos del trazado caen dentro del lienzo.
        geometry.all.forEach { p ->
            val f = TrackAnalysis.fit(geometry.bounds, 360f, 240f, 12f)
            assertTrue(f.px(p.x) in 0f..360f && f.py(p.y) in 0f..240f)
        }
    }

    @Test
    fun speedFraction_isClamped_andNoRangeIsZero() {
        assertEquals(0.5f, TrackAnalysis.speedFraction(15f, 10f, 20f), 1e-6f)
        assertEquals(0f, TrackAnalysis.speedFraction(5f, 10f, 20f), 0f)
        assertEquals(1f, TrackAnalysis.speedFraction(25f, 10f, 20f), 0f)
        assertEquals(0f, TrackAnalysis.speedFraction(10f, 10f, 10f), 0f)
    }

    @Test
    fun nearest_picksTheClosestPointWithinReach() {
        val fit = TrackAnalysis.fit(TrackBounds(0f, 0f, 100f, 100f), 300f, 300f, 0f)
        val a = TrackPt(10f, 10f, 5f, 0)
        val b = TrackPt(90f, 90f, 9f, 5_000)
        val picked = TrackAnalysis.nearest(listOf(1 to a, 2 to b), fit, px = fit.px(88f), py = fit.py(91f), maxDistPx = 30f)
        assertEquals(2, picked?.first)
        assertNull(TrackAnalysis.nearest(listOf(1 to a, 2 to b), fit, px = fit.px(50f), py = fit.py(50f), maxDistPx = 30f))
    }

    // ---- Comparación de ritmo ----

    @Test
    fun paceDeltas_areZeroAgainstItself_andGrowWhenALapIsSlower() {
        val best = geometry.laps[1] // 57,9
        val slow = geometry.laps[3] // 59,3
        assertTrue(TrackAnalysis.paceDeltasMs(best, best).all { it == 0L })
        val deltas = TrackAnalysis.paceDeltasMs(slow, best)
        assertEquals(slow.points.size, deltas.size)
        assertTrue("al inicio no hay diferencia: ${deltas.first()}", abs(deltas.first()) < 50)
        val gap = slow.timeMs - best.timeMs
        assertTrue("al final la diferencia es la de las vueltas: ${deltas.last()} vs $gap", abs(deltas.last() - gap) < 150)
        // Se va acumulando (el ritmo se pierde de forma progresiva, sin saltos hacia atrás grandes).
        assertTrue(deltas.zipWithNext().all { (a, b) -> b >= a - 250 })
    }
}
