package com.nacho.kartlog.ui.history

import com.nacho.kartlog.domain.analytics.SessionStats
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 8, D3: búsqueda de texto en Tandas (circuito, notas, título importado, kart y tipo; sin tildes ni mayúsculas). */
class SessionSearchTest {
    private val zuera = Circuit(1, "Karting Zuera", null)
    private val alonso = Circuit(2, "Fernando Alonso", null)

    private fun stats(id: Long, circuit: Circuit, kart: Int? = null, notes: String = "", label: String? = null, type: SessionType = SessionType.FREE) =
        SessionStats(
            summary = SessionSummary(Session(id, circuit.id, id * 1000, type, kart, null, null, null, TrackCondition.DRY, notes, label), circuit, 0, null),
            lapTimes = emptyList(), bestMs = null, cleanAverageMs = null, consistencyMs = null, deltaToRecordMs = null, isRecord = false,
        )

    private val all = listOf(
        stats(1, zuera, kart = 7, notes = "Presión neumáticos 0,9 bar"),
        stats(2, alonso, kart = 12, label = "Sesión 46 GP3 DIEGO"),
        stats(3, zuera, kart = 12, notes = "Lluvia fuerte", type = SessionType.RACE),
    )

    private fun ids(query: String) = buildSessionItems(all, SessionFilters(query = query))
        .filterIsInstance<SessionListItem.Entry>().map { it.stats.summary.session.id }.sorted()

    @Test
    fun emptyOrBlankQueryKeepsEverything() {
        assertEquals(listOf(1L, 2L, 3L), ids(""))
        assertEquals(listOf(1L, 2L, 3L), ids("   "))
    }

    @Test
    fun matchesCircuitNotesLabelAndKart_ignoringCaseAndAccents() {
        assertEquals(listOf(1L, 3L), ids("zuera"))
        assertEquals(listOf(1L), ids("NEUMATICOS")) // «neumáticos» con tilde
        assertEquals(listOf(3L), ids("lluvia"))
        assertEquals(listOf(2L), ids("diego"))
        assertEquals(listOf(2L, 3L), ids("kart 12"))
        assertEquals(listOf(1L), ids("kart 7"))
        assertEquals(listOf(3L), ids("race"))
    }

    @Test
    fun everyWordMustMatch_inAnyOrder() {
        assertEquals(listOf(3L), ids("zuera 12"))
        assertEquals(listOf(3L), ids("lluvia zuera"))
        assertTrue(ids("zuera diego").isEmpty())
    }

    @Test
    fun theQueryCombinesWithTheOtherFilters() {
        val items = buildSessionItems(all, SessionFilters(circuitId = 1, query = "kart 12"))
        assertEquals(listOf(3L), items.filterIsInstance<SessionListItem.Entry>().map { it.stats.summary.session.id })
    }

    @Test
    fun aSessionWithoutKartOrLabelNeverMatchesTheWordNull() {
        assertFalse(matchesQuery(stats(9, zuera), "null"))
    }
}
