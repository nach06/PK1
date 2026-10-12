package com.nacho.kartlog.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalQualityTest {
    private val oval = OvalTrack()
    private fun sim(hz: Double, accuracy: Float, drop: Set<Int> = emptySet()) =
        TrackSimulator.simulate(oval, listOf(58_000L, 58_000L), hz = hz, accuracyM = accuracy, dropFixes = drop).fixes

    @Test
    fun cleanOneHertzTrackIsGood() {
        val q = SignalQuality.of(sim(1.0, 4f))!!
        assertEquals(100, q.goodAccuracyPercent)
        assertEquals(1.0, q.meanHz, 0.05)
        assertEquals(0, q.gaps)
        assertEquals(SignalQuality.Score.GOOD, q.score)
    }

    @Test
    fun poorAccuracyOrManyGapsIsPoor_andInBetweenIsFair() {
        assertEquals(SignalQuality.Score.POOR, SignalQuality.of(sim(1.0, 14f))!!.score)
        assertEquals(SignalQuality.Score.POOR, SignalQuality.of(sim(1.0, 4f, (10..120 step 5).flatMap { listOf(it, it + 1, it + 2) }.toSet()))!!.score)
        val twoGaps = SignalQuality.of(sim(1.0, 4f, setOf(20, 21, 22, 60, 61, 62)))!!
        assertEquals(2, twoGaps.gaps)
        assertEquals(SignalQuality.Score.FAIR, twoGaps.score)
    }

    @Test
    fun tooFewFixesGiveNoReport_andScoreCodesRoundTrip() {
        assertNull(SignalQuality.of(sim(1.0, 4f).take(2)))
        SignalQuality.Score.entries.forEach { assertEquals(it, SignalQuality.Score.fromCode(it.code)) }
        assertNull(SignalQuality.Score.fromCode(null))
    }
}
