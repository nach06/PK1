package com.nacho.kartlog.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 8, B3: GPS − manual por vuelta, sesgo medio y propuesta de compensación. */
class FieldCalibrationTest {
    private val s = 1_000_000_000L

    @Test
    fun aConstantGpsDelayGivesAConstantBias_andALapDifferenceOfZero() {
        val crossings = listOf(10 * s, 70 * s, 129 * s, 188 * s)
        val taps = crossings.map { it - 300_000_000L } // el GPS ve el cruce 300 ms después de la pulsación
        val r = FieldCalibration.compute(taps, crossings)
        assertEquals(4, r.pairs.size)
        assertEquals(300.0, r.meanBiasMs!!, 0.5)
        assertEquals(0.0, r.stdevBiasMs!!, 0.5)
        assertTrue(r.constantBias)
        assertEquals(300, r.proposedDelayMs)
        assertEquals(3, r.laps.size)
        r.laps.forEach { assertEquals(0L, it.diffMs) } // un retardo constante no cambia los tiempos de vuelta
        assertEquals(60_000L, r.laps[0].gpsMs)
    }

    @Test
    fun anErraticBiasOrASmallOneIsNotProposed() {
        val crossings = listOf(10 * s, 70 * s, 130 * s, 190 * s)
        val noisy = listOf(100L, 600L, -300L, 400L).mapIndexed { i, ms -> crossings[i] - ms * 1_000_000L }
        assertFalse(FieldCalibration.compute(noisy, crossings).constantBias)
        val tiny = crossings.map { it - 20_000_000L }
        val r = FieldCalibration.compute(tiny, crossings)
        assertFalse("|sesgo| ≤ 50 ms no se propone", r.constantBias)
        assertNull(r.proposedDelayMs)
    }

    @Test
    fun unmatchedTapsAndCrossingsAreCountedAndTheProposalIsClamped() {
        val crossings = listOf(10 * s, 70 * s, 130 * s, 190 * s)
        val taps = listOf(crossings[0] - 700_000_000L, crossings[1] - 700_000_000L, crossings[2] - 700_000_000L, 500 * s) // la última, perdida
        val r = FieldCalibration.compute(taps, crossings)
        assertEquals(3, r.pairs.size)
        assertEquals(1, r.unmatchedTaps)
        assertEquals(1, r.unmatchedCrossings)
        assertEquals(500, r.proposedDelayMs) // 700 ms → tope de ±500
    }
}
