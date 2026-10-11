package com.nacho.kartlog.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsAccuracyTest {
    private val official = listOf(58_400L, 57_900L, 58_120L, 59_300L)

    @Test
    fun pairsByLapNumber_andReportsMeanMaxAndBias() {
        val gps = listOf(58_460L, 57_820L, 58_150L, 59_340L) // +60, -80, +30, +40
        val r = GpsAccuracy.compare(official, gps)
        assertEquals(listOf(60L, -80L, 30L, 40L), r.rows.map { it.diffMs })
        assertEquals(52.5, r.meanAbsErrorMs!!, 1e-9)
        assertEquals(80L, r.maxAbsErrorMs)
        assertEquals(12.5, r.meanSignedErrorMs!!, 1e-9)
        assertFalse(r.countMismatch)
    }

    @Test
    fun warnsWhenTheNumberOfLapsDiffers_andComparesTheCommonOnes() {
        val r = GpsAccuracy.compare(official + 58_000L, listOf(58_400L, 57_900L, 58_120L))
        assertTrue(r.countMismatch)
        assertEquals(5, r.officialCount)
        assertEquals(3, r.gpsCount)
        assertEquals(3, r.rows.size)
    }

    @Test
    fun offsetAlignsAnOfficialOutLap() {
        // El oficial cuenta una vuelta de salida (lenta) que el GPS no ve: con offset 1 se alinean.
        val withOutLap = listOf(75_000L) + official
        val gps = official.map { it + 20 }
        val misaligned = GpsAccuracy.compare(withOutLap, gps)
        val aligned = GpsAccuracy.compare(withOutLap, gps, offset = 1)
        assertTrue(misaligned.meanAbsErrorMs!! > 1_000)
        assertEquals(20.0, aligned.meanAbsErrorMs!!, 1e-9)
        assertFalse("con el desplazamiento correcto el recuento coincide", aligned.countMismatch)
    }

    @Test
    fun noComparableLaps_givesNoStatistics() {
        val r = GpsAccuracy.compare(emptyList(), listOf(58_000L))
        assertTrue(r.rows.isEmpty())
        assertNull(r.meanAbsErrorMs)
        assertNull(r.maxAbsErrorMs)
    }
}
