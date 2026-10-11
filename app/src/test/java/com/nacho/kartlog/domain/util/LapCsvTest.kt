package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import org.junit.Assert.assertEquals
import org.junit.Test

class LapCsvTest {
    @Test
    fun buildsHeaderAndRowsOrderedByNumber() {
        val laps = listOf(
            Lap(2, 1, 2, 49_000, LapSource.MANUAL),
            Lap(1, 1, 1, 48_532, LapSource.MANUAL),
        )
        assertEquals(
            "numero_vuelta;tiempo;tiempo_ms;origen\n1;0:48.532;48532;MANUAL\n2;0:49.000;49000;MANUAL\n",
            LapCsv.build(laps),
        )
    }

    @Test
    fun emptySessionOnlyHasHeader() {
        assertEquals("numero_vuelta;tiempo;tiempo_ms;origen\n", LapCsv.build(emptyList()))
    }
}
