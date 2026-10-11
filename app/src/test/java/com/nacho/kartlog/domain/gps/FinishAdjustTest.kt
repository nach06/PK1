package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class FinishAdjustTest {
    private val track = OvalTrack(headingDeg = 87.0)
    private val line = track.finishLine

    @Test
    fun shiftMovesTheLineAlongItsHeading_inMetres() {
        listOf(1, 5, -3, 10, -10).forEach { shift ->
            val moved = FinishAdjust.apply(line, shift, 20)
            val along = FinishAdjust.alongTrack(line, line.lat, line.lon, moved.lat, moved.lon)
            assertEquals("desplazamiento $shift m", shift.toDouble(), along, 0.01)
            // Sin componente lateral.
            val p = LocalFrame(line.lat, line.lon).toXY(moved.lat, moved.lon)
            assertEquals(Math.abs(shift).toDouble(), hypot(p.x, p.y), 0.01)
            assertEquals(line.bearingDeg, moved.bearingDeg, 0.0)
        }
        assertEquals(line, FinishAdjust.apply(line, 0, 20))
    }

    @Test
    fun shiftAndWidthAreClamped() {
        assertEquals(10, FinishAdjust.clampShift(99))
        assertEquals(-10, FinishAdjust.clampShift(-99))
        assertEquals(10, FinishAdjust.clampGate(2))
        assertEquals(40, FinishAdjust.clampGate(80))
        assertEquals(40, FinishAdjust.apply(line, 50, 80).gateWidthM)
        val far = FinishAdjust.apply(line, 50, 80)
        assertEquals(10.0, FinishAdjust.alongTrack(line, line.lat, line.lon, far.lat, far.lon), 0.01)
    }

    @Test
    fun aShiftedLineStillTimesLapsIdentically_whenTheSpeedIsConstantPerLap() {
        // El desplazamiento mueve todos los cruces el mismo tramo: los tiempos de vuelta no cambian.
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 57_900L, 58_120L), hz = 1.0)
        fun laps(l: FinishLine): List<Long> {
            val detector = GateDetector(l)
            return sim.fixes.mapNotNull(detector::feed).mapNotNull { it.lapTimeMs }
        }
        val base = laps(line)
        listOf(-10, -4, 3, 10).forEach { shift ->
            val shifted = laps(FinishAdjust.apply(line, shift, 20))
            assertEquals(base.size, shifted.size)
            base.indices.forEach { assertEquals(base[it].toDouble(), shifted[it].toDouble(), 60.0) }
        }
    }

    @Test
    fun reversedDirectionIsDetectedBeyondNinetyDegrees() {
        assertFalse(FinishAdjust.isReversed(line, line.copy(bearingDeg = 100.0)))
        assertFalse(FinishAdjust.isReversed(line, line.copy(bearingDeg = 87.0 + 89.0)))
        assertTrue(FinishAdjust.isReversed(line, line.copy(bearingDeg = 87.0 + 180.0)))
        assertTrue(FinishAdjust.isReversed(line, line.copy(bearingDeg = 87.0 - 120.0)))
    }

    @Test
    fun verification_acceptsAGoodLine_andFlagsAMisplacedOneOrNoCrossing() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0)
        val cross = sim.crossingNs[1]
        // El usuario confirma ~0,4 s después de cruzar la línea.
        val upTo = sim.fixes.filter { it.elapsedNs <= cross + 1_000_000_000L }
        val good = FinishVerification.check(line, upTo) as FinishVerification.Outcome.Checked
        assertEquals(sim.crossingNs[1].toDouble(), good.crossingNs.toDouble(), 20e6)
        assertEquals(87.0, good.travelBearingDeg, 1.0)
        assertTrue(good.ok)
        assertTrue(good.timeDiffMs in 0..1_000)

        // La meta está 25 m por detrás: el cruce se detecta ~2 s antes de que el usuario confirme.
        val behind = FinishAdjust.apply(line.copy(gateWidthM = 20), -10, 20)
        val tooEarly = FinishVerification.check(behind.copy(lat = line.lat, lon = line.lon).let { moved ->
            val back = LocalFrame(line.lat, line.lon).toLatLon(GeoMath.unit(87.0).x * -25, GeoMath.unit(87.0).y * -25)
            moved.copy(lat = back.first, lon = back.second)
        }, upTo) as FinishVerification.Outcome.Checked
        assertFalse("más de 1 s de diferencia", tooEarly.timeOk)
        assertFalse(tooEarly.ok)

        // Sin cruce: fixes que no llegan a la meta.
        val notThere = sim.fixes.filter { it.elapsedNs <= sim.crossingNs[0] - 5_000_000_000L }
        assertEquals(FinishVerification.Outcome.NoCrossing, FinishVerification.check(line, notThere))
        assertEquals(FinishVerification.Outcome.NoCrossing, FinishVerification.check(line, emptyList()))
    }
}
