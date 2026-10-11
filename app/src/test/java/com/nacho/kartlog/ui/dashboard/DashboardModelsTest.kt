package com.nacho.kartlog.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardModelsTest {
    @Test
    fun deltaBar_isProportionalClampedAndNullIsZero() {
        assertEquals(0f, DeltaBarModel.fraction(null), 0f)
        assertEquals(0.25f, DeltaBarModel.fraction(500), 1e-6f)
        assertEquals(-0.5f, DeltaBarModel.fraction(-1_000), 1e-6f)
        assertEquals(1f, DeltaBarModel.fraction(9_000), 0f)
        assertEquals(-1f, DeltaBarModel.fraction(-9_000), 0f)
    }

    @Test
    fun lapAlert_isRecordOnlyWhenItBeatsBothTheSessionBestAndTheCircuitRecord() {
        val record = LapAlert.of(5, 57_000, previousBestMs = 58_000, isBest = true, circuitRecordMs = 57_500)
        assertEquals(LapAlertKind.RECORD, record.kind)
        assertEquals(-1_000L, record.deltaMs)
        // Mejor de la tanda pero no del circuito: verde.
        assertEquals(LapAlertKind.BEST, LapAlert.of(5, 58_000, 59_000, true, circuitRecordMs = 57_500).kind)
        // Igualar el récord no lo bate.
        assertEquals(LapAlertKind.BEST, LapAlert.of(5, 57_500, 59_000, true, circuitRecordMs = 57_500).kind)
        // Sin historial en el circuito no hay récord que batir.
        assertEquals(LapAlertKind.BEST, LapAlert.of(5, 57_000, 58_000, true, circuitRecordMs = null).kind)
        // Más lenta: rojo y delta positivo respecto a la mejor.
        val slower = LapAlert.of(6, 58_800, 58_000, false, circuitRecordMs = 57_500)
        assertEquals(LapAlertKind.SLOWER, slower.kind)
        assertEquals(800L, slower.deltaMs)
        // Primera vuelta: no hay delta.
        val first = LapAlert.of(1, 60_000, null, true, circuitRecordMs = null)
        assertNull(first.deltaMs)
        assertEquals(LapAlertKind.BEST, first.kind)
    }

    @Test
    fun stintElapsed_sumsOnlyTheCurrentStintPlusTheLapInProgress() {
        val laps = listOf(60_000L, 61_000, 59_000, 62_000)
        val lapStint = listOf(0, 0, 1, 1)
        assertEquals(121_000L + 5_000, stintElapsedMs(laps, lapStint, currentStintIndex = 0, currentLapMs = 5_000))
        assertEquals(121_000L, stintElapsedMs(laps, lapStint, currentStintIndex = 1, currentLapMs = null))
    }

    @Test
    fun battery_isLowBelow20PercentOnlyWhenNotCharging() {
        assertTrue(BatteryStatus(19, charging = false).low)
        assertFalse(BatteryStatus(20, charging = false).low)
        assertFalse(BatteryStatus(10, charging = true).low)
    }
}
