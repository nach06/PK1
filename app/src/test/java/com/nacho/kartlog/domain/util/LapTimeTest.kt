package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapTimeTest {
    private fun lap(n: Int, ms: Long) = Lap(id = n.toLong(), sessionId = 1, number = n, timeMs = ms, source = LapSource.MANUAL)

    @Test
    fun format_msToClock() {
        assertEquals("0:48.532", LapTime.format(48_532))
        assertEquals("1:05.007", LapTime.format(65_007))
        assertEquals("0:00.000", LapTime.format(0))
    }

    @Test
    fun formatDigits_fillsFromTheRight() {
        assertEquals("0:00.000", LapTime.formatDigits(""))
        assertEquals("0:00.004", LapTime.formatDigits("4"))
        assertEquals("0:04.853", LapTime.formatDigits("4853"))
        assertEquals("0:48.532", LapTime.formatDigits("48532"))
        assertEquals("1:02.345", LapTime.formatDigits("102345"))
    }

    @Test
    fun appendDigits_ignoresLeadingZerosAndOverflow() {
        assertEquals("", LapTime.appendDigits("", "0"))
        assertEquals("", LapTime.appendDigits("", "00"))
        assertEquals("485", LapTime.appendDigits("48", "5"))
        assertEquals("40", LapTime.appendDigits("4", "0"))
        assertEquals("123456", LapTime.appendDigits("12345", "6"))
        assertEquals("123456", LapTime.appendDigits("123456", "7"))
    }

    @Test
    fun digitsToMs_validatesSecondsAndZero() {
        assertEquals(48_532L, LapTime.digitsToMs("48532"))
        assertEquals(65_007L, LapTime.digitsToMs("105007"))
        assertNull(LapTime.digitsToMs(""))
        assertNull(LapTime.digitsToMs("60000")) // 60 s
        assertTrue(LapTime.isInvalid("60000"))
        assertFalse(LapTime.isInvalid(""))
    }

    @Test
    fun msToDigits_roundTrips() {
        listOf(48_532L, 65_007L, 599_999L, 1L).forEach {
            assertEquals(it, LapTime.digitsToMs(LapTime.msToDigits(it)))
        }
    }

    @Test
    fun bestAndAverage() {
        val laps = listOf(lap(1, 50_000), lap(2, 48_532), lap(3, 49_000))
        assertEquals(48_532L, LapTime.bestLapMs(laps))
        assertEquals(49_177L, LapTime.averageMs(laps))
        assertNull(LapTime.bestLapMs(emptyList()))
        assertNull(LapTime.averageMs(emptyList()))
    }

    @Test
    fun formatDelta_signAndDecimalComma() {
        assertEquals("+2,927", LapTime.formatDelta(2_927))
        assertEquals("+0,000", LapTime.formatDelta(0))
        assertEquals("-0,350", LapTime.formatDelta(-350))
        assertEquals("+1:02,345", LapTime.formatDelta(62_345))
    }
}
