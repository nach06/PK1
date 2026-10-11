package com.nacho.kartlog.ui.analysis

import com.nacho.kartlog.domain.analytics.AnalyticsSnapshot
import com.nacho.kartlog.domain.model.Circuit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Circuito elegido para el análisis, compartido entre pestañas (Circuitos → Análisis).
 * `null` significa «el de la tanda más reciente».
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
 * Circuito mostrado: el seleccionado si sigue teniendo tandas; si no, el de la tanda más reciente
 * (por fecha). `null` si no hay ninguna tanda.
 */
fun resolveCircuitId(snapshot: AnalyticsSnapshot, selected: Long?): Long? {
    if (selected != null && snapshot.circuits.containsKey(selected)) return selected
    return snapshot.sessions.maxByOrNull { it.summary.session.dateTime }?.summary?.circuit?.id
}
