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

    // Alfa: 2 tandas con vueltas (fechas 1.000 y 3.000). Beta: 1 tanda con vueltas, la más reciente (5.000).
    private val snapshot = Analytics.compute(
        listOf(summary(1, a, 1_000), summary(2, b, 5_000), summary(3, a, 3_000)),
        listOf(
            Lap(1, 1, 1, 50_000, LapSource.MANUAL),
            Lap(2, 2, 1, 49_000, LapSource.MANUAL),
            Lap(3, 3, 1, 51_000, LapSource.MANUAL),
        ),
    )

    @Test
    fun default_isCircuitWithMostSessionsWithLaps_notTheMostRecent() {
        assertEquals(1L, resolveCircuitId(snapshot, null)) // Alfa tiene 2 tandas; Beta solo 1 aunque es más reciente
    }

    @Test
    fun default_tieIsBrokenByMostRecentSession() {
        val tied = Analytics.compute(
            listOf(summary(1, a, 1_000), summary(2, b, 5_000)),
            listOf(Lap(1, 1, 1, 50_000, LapSource.MANUAL), Lap(2, 2, 1, 49_000, LapSource.MANUAL)),
        )
        assertEquals(2L, resolveCircuitId(tied, null))
    }

    @Test
    fun sessionsWithoutLaps_doNotCountForTheDefault() {
        val s = Analytics.compute(
            listOf(summary(1, a, 1_000), summary(2, a, 2_000), summary(3, b, 3_000)),
            listOf(Lap(1, 3, 1, 49_000, LapSource.MANUAL)), // Alfa tiene 2 tandas pero vacías
        )
        assertEquals(2L, resolveCircuitId(s, null))
    }

    @Test
    fun explicitSelection_wins_whenCircuitStillExists() {
        assertEquals(2L, resolveCircuitId(snapshot, 2))
        assertEquals(1L, resolveCircuitId(snapshot, 99)) // inexistente: vuelve al valor por defecto
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
