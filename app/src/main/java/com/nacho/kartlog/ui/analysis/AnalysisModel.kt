package com.nacho.kartlog.ui.analysis

import com.nacho.kartlog.domain.analytics.AnalyticsSnapshot
import com.nacho.kartlog.domain.model.Circuit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Circuito elegido para el análisis, compartido entre pestañas (Circuitos → Análisis).
 * Vive en el [com.nacho.kartlog.AppContainer], así que la última elección se recuerda mientras la app
 * siga en memoria. `null` significa «aún no se ha elegido: usar el circuito por defecto».
 */
class AnalysisSelection {
    val circuitId = MutableStateFlow<Long?>(null)
}

/** Circuitos con al menos una tanda, el de la tanda más reciente primero y luego por nombre. */
fun circuitsWithSessions(snapshot: AnalyticsSnapshot): List<Circuit> {
    val lastDate = snapshot.sessions.groupBy { it.summary.circuit.id }
        .mapValues { (_, v) -> v.maxOf { it.summary.session.dateTime } }
    return snapshot.circuits.values.map { it.circuit }
        .sortedWith(compareByDescending<Circuit> { lastDate[it.id] ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() })
}

/**
 * Circuito mostrado: el seleccionado si existe; si no, el que tiene **más tandas con vueltas** y, en
 * caso de empate, el de la tanda más reciente. `null` si no hay ninguna tanda.
 */
fun resolveCircuitId(snapshot: AnalyticsSnapshot, selected: Long?): Long? {
    if (selected != null && snapshot.circuits.containsKey(selected)) return selected
    val lastDate = snapshot.sessions.groupBy { it.summary.circuit.id }
        .mapValues { (_, v) -> v.maxOf { it.summary.session.dateTime } }
    return snapshot.circuits.values
        .maxWithOrNull(
            compareBy<com.nacho.kartlog.domain.analytics.CircuitStats> { it.sessionsWithLaps }
                .thenBy { lastDate[it.circuit.id] ?: Long.MIN_VALUE },
        )?.circuit?.id
}
