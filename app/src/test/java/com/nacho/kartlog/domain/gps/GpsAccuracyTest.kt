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

    @Test
    fun gpsHealthLevels_followTheAccuracyThresholds() {
        assertEquals(GpsHealth.GOOD, GpsHealth.of(3f))
        assertEquals(GpsHealth.GOOD, GpsHealth.of(8f))
        assertEquals(GpsHealth.FAIR, GpsHealth.of(8.1f))
        assertEquals(GpsHealth.FAIR, GpsHealth.of(15f))
        assertEquals(GpsHealth.POOR, GpsHealth.of(15.1f))
        assertEquals(GpsHealth.POOR, GpsHealth.of(3f, signalLost = true))
        assertNull(GpsHealth.of(null))
    }

    @Test
    fun systematicBias_isReportedOnlyWhenConsistentAndAboveFiftyMilliseconds() {
        val official = listOf(58_400L, 57_900L, 58_120L, 59_300L, 58_000L)
        // Sesgo conocido de +80 ms en todas las vueltas (con un poco de ruido): se detecta.
        val biased = GpsAccuracy.compare(official, official.mapIndexed { i, ms -> ms + 80 + (i % 2) * 6 })
        assertEquals(83.0, biased.systematicBiasMs!!, 3.0)
        // Mismo error absoluto pero alternando de signo: ruido sin dirección, no hay sesgo.
        val noise = GpsAccuracy.compare(official, official.mapIndexed { i, ms -> ms + if (i % 2 == 0) 80 else -80 })
        assertNull(noise.systematicBiasMs)
        // Sesgo pequeño (30 ms) o con menos de 3 vueltas: no se avisa.
        assertNull(GpsAccuracy.compare(official, official.map { it + 30 }).systematicBiasMs)
        assertNull(GpsAccuracy.compare(official.take(2), official.take(2).map { it + 120 }).systematicBiasMs)
        // Negativo (el GPS mide de menos).
        assertEquals(-120.0, GpsAccuracy.compare(official, official.map { it - 120 }).systematicBiasMs!!, 1e-9)
    }
}
