package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.gps.FinishCalibration.Failure
import com.nacho.kartlog.domain.gps.FinishCalibration.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishCalibrationTest {
    private val track = OvalTrack(headingDeg = 87.0)

    /** Fixes a 1 Hz de un coche que pasa por la meta de [track] (la calibración se pulsa al cruzarla). */
    private fun passThrough(speed: Double = 14.0, accuracy: Float = 4f): List<GpsFix> {
        val sim = TrackSimulator.simulate(track, listOf((track.perimeterM / speed * 1000).toLong()), hz = 1.0, accuracyM = accuracy)
        // Hasta el primer fix posterior a la meta: es cuando el usuario pulsa MARCAR.
        val cross = sim.crossingNs.first()
        val cut = sim.fixes.indexOfFirst { it.elapsedNs >= cross }
        return sim.fixes.take(cut + 1)
    }

    @Test
    fun bearingAndPoint_matchTheStraightTheCarWasDriving() {
        val outcome = FinishCalibration.compute(passThrough()) as Outcome.Ok
        assertEquals(87.0, outcome.finishLine.bearingDeg, 0.5)
        // El punto es la última posición, a menos de un fix (14 m) de la meta real.
        val frame = track.frame
        val p = frame.toXY(outcome.finishLine.lat, outcome.finishLine.lon)
        assertTrue("punto a ${p.x},${p.y} m de la meta", kotlin.math.hypot(p.x, p.y) < 15.0)
        assertEquals(20, outcome.finishLine.gateWidthM)
    }

    @Test
    fun calibrationThenDetection_closesLapsOnTheMarkedLine() {
        // Se marca la meta pasando una vez y se cronometra otra tanda con ella: las vueltas salen bien.
        val marked = (FinishCalibration.compute(passThrough()) as Outcome.Ok).finishLine
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 57_900L), hz = 1.0)
        val detector = GateDetector(marked)
        val laps = sim.fixes.mapNotNull(detector::feed).mapNotNull { it.lapTimeMs }
        // La puerta quedó unos metros por delante de la meta real; como todas las vueltas pasan a la misma velocidad
        // el desfase se cancela y las vueltas siguen siendo las reales (hasta el cambio de velocidad entre ellas).
        assertEquals(2, laps.size)
        assertEquals(58_400.0, laps[0].toDouble(), 150.0)
        assertEquals(57_900.0, laps[1].toDouble(), 150.0)
    }

    @Test
    fun failsClearly_withoutFixes_tooSlow_poorAccuracy_orInACurve() {
        assertEquals(Outcome.Failed(Failure.NO_FIXES), FinishCalibration.compute(emptyList()))
        assertEquals(Outcome.Failed(Failure.TOO_SLOW), FinishCalibration.compute(passThrough().takeLast(1)))
        // Parado: varios fixes en el mismo sitio.
        val stopped = passThrough().takeLast(1).first().let { f -> (0..3).map { f.copy(elapsedNs = f.elapsedNs + it * 1_000_000_000L) } }
        assertEquals(Outcome.Failed(Failure.TOO_SLOW), FinishCalibration.compute(stopped))
        assertEquals(Outcome.Failed(Failure.POOR_ACCURACY), FinishCalibration.compute(passThrough(accuracy = 22f)))

        // En plena curva el rumbo de la primera y la segunda mitad difieren: no se acepta.
        val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
        val inCurve = sim.fixes.filter { it.elapsedNs in (sim.crossingNs[0] + 12_000_000_000L)..(sim.crossingNs[0] + 17_000_000_000L) }
        assertTrue(inCurve.size >= 4)
        assertEquals(Outcome.Failed(Failure.NOT_STRAIGHT), FinishCalibration.compute(inCurve))
    }
}
