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
    /** Búsqueda de texto (D3): circuito, notas, título importado y kart; todas las palabras deben aparecer. No se recuerda entre sesiones. */
    val query: String = "",
)

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
            (filters.type == null || s.summary.session.type == filters.type) &&
            matchesQuery(s, filters.query)
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

private fun searchKey(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * Búsqueda de texto en Tandas (D3): sin tildes ni mayúsculas, sobre circuito, notas, título importado, kart («kart 7» o «7») y tipo.
 * Cada palabra de la consulta debe aparecer en alguno de esos campos; la consulta vacía lo admite todo.
 */
fun matchesQuery(stats: SessionStats, query: String): Boolean {
    val words = searchKey(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val session = stats.summary.session
    val haystack = searchKey(
        listOfNotNull(
            stats.summary.circuit.name, session.notes, session.label,
            session.kartNumber?.let { "kart $it" }, session.type.name,
        ).joinToString(" \n "),
    )
    return words.all { it in haystack }
}
