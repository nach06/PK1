package com.nacho.kartlog.ui.analysis

import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalysisModelTest {
    private val a = Circuit(1, "Alfa", null)
    private val b = Circuit(2, "Beta", null)

    private fun summary(id: Long, c: Circuit, date: Long) = SessionSummary(
        Session(id, c.id, date, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""), c, 1, 50_000,
    )

    private val snapshot = Analytics.compute(
        listOf(summary(1, a, 1_000), summary(2, b, 5_000), summary(3, a, 3_000)),
        listOf(Lap(1, 1, 1, 50_000, LapSource.MANUAL)),
    )

    @Test
    fun default_isCircuitOfMostRecentSession() {
        assertEquals(2L, resolveCircuitId(snapshot, null))
    }

    @Test
    fun explicitSelection_wins_whenCircuitStillHasSessions() {
        assertEquals(1L, resolveCircuitId(snapshot, 1))
        assertEquals(2L, resolveCircuitId(snapshot, 99)) // inexistente: vuelve al valor por defecto
    }

    @Test
    fun noSessions_meansNoCircuit() {
        assertNull(resolveCircuitId(Analytics.compute(emptyList(), emptyList()), null))
    }

    @Test
    fun picker_listsMostRecentFirst() {
        assertEquals(listOf("Beta", "Alfa"), circuitsWithSessions(snapshot).map { it.name })
    }
}
