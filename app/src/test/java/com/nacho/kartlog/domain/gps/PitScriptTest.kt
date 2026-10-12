package com.nacho.kartlog.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PitScriptTest {
    @Test
    fun asksForEachPitOnce_inOrder_withTheIncomingDriver() {
        val script = PitScript(listOf(3, 6), listOf("B", "C"))
        assertNull(script.due(validLaps = 2, started = true, pitPending = false))
        assertEquals("B", script.due(3, started = true, pitPending = false))
        assertNull("no se repite la misma parada", script.due(3, started = true, pitPending = false))
        assertNull(script.due(5, started = true, pitPending = false))
        assertEquals("C", script.due(6, started = true, pitPending = false))
        assertNull("ya no quedan paradas", script.due(9, started = true, pitPending = false))
    }

    @Test
    fun waitsWhileAPitIsPendingOrTheRunHasNotStarted() {
        val script = PitScript(listOf(1), listOf("B"))
        assertNull(script.due(1, started = false, pitPending = false))
        assertNull(script.due(1, started = true, pitPending = true))
        assertEquals("B", script.due(1, started = true, pitPending = false))
    }
}
