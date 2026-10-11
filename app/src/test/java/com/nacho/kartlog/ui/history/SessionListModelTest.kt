package com.nacho.kartlog.ui.history

import com.nacho.kartlog.domain.analytics.SessionStats
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class SessionListModelTest {
    private val a = Circuit(1, "Alfa", null)
    private val b = Circuit(2, "Beta", null)
    private val zone = ZoneOffset.UTC

    private fun stats(id: Long, circuit: Circuit, date: LocalDate, type: SessionType = SessionType.FREE, best: Long? = 50_000) =
        SessionStats(
            summary = SessionSummary(
                Session(id, circuit.id, date.atStartOfDay(zone).toInstant().toEpochMilli(), type, null, null, null, null, TrackCondition.DRY, ""),
                circuit, if (best == null) 0 else 1, best,
            ),
            lapTimes = listOfNotNull(best), bestMs = best, cleanAverageMs = best, consistencyMs = null,
            deltaToRecordMs = null, isRecord = false,
        )

    private val sessions = listOf(
        stats(1, a, LocalDate.of(2026, 9, 3), best = 52_000),
        stats(2, b, LocalDate.of(2026, 10, 20), SessionType.RACE, best = 49_000),
        stats(3, a, LocalDate.of(2026, 10, 2), SessionType.QUALIFYING, best = null),
        stats(4, a, LocalDate.of(2026, 10, 25), best = 51_000),
    )

    private fun ids(items: List<SessionListItem>) = items.map {
        when (it) {
            is SessionListItem.MonthHeader -> "H${it.month}"
            is SessionListItem.Entry -> it.stats.summary.session.id.toString()
        }
    }

    @Test
    fun byDate_groupsByMonth_newestFirst() {
        val items = buildSessionItems(sessions, SessionFilters(), zone)
        assertEquals(listOf("H2026-10", "4", "2", "3", "H2026-09", "1"), ids(items))
        assertEquals(YearMonth.of(2026, 10), (items[0] as SessionListItem.MonthHeader).month)
    }

    @Test
    fun byBestLap_isFlat_withEmptySessionsLast() {
        val items = buildSessionItems(sessions, SessionFilters(sort = SessionSort.BEST_LAP), zone)
        assertEquals(listOf("2", "4", "1", "3"), ids(items))
    }

    @Test
    fun filters_byCircuitAndType() {
        assertEquals(listOf("H2026-10", "4", "3", "H2026-09", "1"), ids(buildSessionItems(sessions, SessionFilters(circuitId = 1), zone)))
        assertEquals(listOf("H2026-10", "2"), ids(buildSessionItems(sessions, SessionFilters(type = SessionType.RACE), zone)))
        assertEquals(emptyList<String>(), ids(buildSessionItems(sessions, SessionFilters(circuitId = 2, type = SessionType.FREE), zone)))
    }
}
