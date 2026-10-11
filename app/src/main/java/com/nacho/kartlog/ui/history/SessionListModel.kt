package com.nacho.kartlog.ui.history

import com.nacho.kartlog.domain.analytics.SessionStats
import com.nacho.kartlog.domain.model.SessionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class SessionSort { DATE, BEST_LAP }

data class SessionFilters(
    val circuitId: Long? = null,
    val type: SessionType? = null,
    val sort: SessionSort = SessionSort.DATE,
) {
    val hasActiveFilter: Boolean get() = circuitId != null || type != null
}

sealed interface SessionListItem {
    val key: String

    data class MonthHeader(val month: YearMonth) : SessionListItem {
        override val key: String get() = "month-$month"
    }

    data class Entry(val stats: SessionStats) : SessionListItem {
        override val key: String get() = "session-${stats.summary.session.id}"
    }
}

/**
 * Aplica filtros y orden. Por fecha (descendente) agrupa con cabeceras de mes; por mejor vuelta
 * la lista es plana, con las tandas sin vueltas al final.
 */
fun buildSessionItems(
    sessions: List<SessionStats>,
    filters: SessionFilters,
    zone: ZoneId = ZoneId.systemDefault(),
): List<SessionListItem> {
    val filtered = sessions.filter { s ->
        (filters.circuitId == null || s.summary.circuit.id == filters.circuitId) &&
            (filters.type == null || s.summary.session.type == filters.type)
    }
    return when (filters.sort) {
        SessionSort.DATE -> {
            val sorted = filtered.sortedWith(
                compareByDescending<SessionStats> { it.summary.session.dateTime }.thenByDescending { it.summary.session.id },
            )
            buildList {
                var current: YearMonth? = null
                for (s in sorted) {
                    val month = YearMonth.from(Instant.ofEpochMilli(s.summary.session.dateTime).atZone(zone))
                    if (month != current) {
                        add(SessionListItem.MonthHeader(month))
                        current = month
                    }
                    add(SessionListItem.Entry(s))
                }
            }
        }
        SessionSort.BEST_LAP -> filtered
            .sortedWith(compareBy<SessionStats> { it.bestMs ?: Long.MAX_VALUE }.thenByDescending { it.summary.session.dateTime })
            .map { SessionListItem.Entry(it) }
    }
}
