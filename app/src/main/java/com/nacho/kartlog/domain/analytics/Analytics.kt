package com.nacho.kartlog.domain.analytics

import com.nacho.kartlog.domain.gps.GpsAccuracy
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.TrackCondition
import kotlin.math.sqrt

/**
 * Reglas de análisis. Todo se calcula en milisegundos y sin dependencias de Android.
 *
 * - **Vuelta limpia:** `timeMs <= 1,07 × mejor vuelta de su tanda` (descarta salidas de boxes y errores).
 * - **Media limpia:** media de las vueltas limpias de una tanda; `null` si no hay vueltas.
 * - **Consistencia:** desviación típica *poblacional* de las vueltas limpias (ms). En el histórico del
 *   circuito es la media de la consistencia de cada tanda con >= 2 vueltas limpias.
 * - **Récord de circuito:** mejor vuelta de todas las tandas del circuito.
 * - **Delta de tanda:** mejor vuelta de la tanda − récord del circuito (0 = `RÉCORD`).
 * - Las tandas sin vueltas no entran en medias ni rankings.
 */
object Analytics {
    /** Una vuelta es limpia si `t × 100 <= mejor × 107` (aritmética entera, sin redondeos). */
    const val CLEAN_NUMERATOR = 107L
    const val CLEAN_DENOMINATOR = 100L

    fun cleanLaps(timesMs: List<Long>): List<Long> {
        val best = timesMs.minOrNull() ?: return emptyList()
        return timesMs.filter { it * CLEAN_DENOMINATOR <= best * CLEAN_NUMERATOR }
    }

    fun cleanAverageMs(timesMs: List<Long>): Long? {
        val clean = cleanLaps(timesMs)
        if (clean.isEmpty()) return null
        return Math.round(clean.sum().toDouble() / clean.size)
    }

    /** Desviación típica de las vueltas limpias en ms (0,0 con una sola vuelta; `null` sin vueltas). */
    fun consistencyMs(timesMs: List<Long>): Double? = standardDeviation(cleanLaps(timesMs))

    /** La consistencia solo es significativa con al menos 2 vueltas limpias. */
    fun hasMeaningfulConsistency(timesMs: List<Long>): Boolean = cleanLaps(timesMs).size >= 2

    internal fun standardDeviation(values: List<Long>): Double? {
        if (values.isEmpty()) return null
        val mean = values.sum().toDouble() / values.size
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }

    /**
     * Calcula las estadísticas de todas las tandas y circuitos a partir de los resúmenes y las vueltas.
     * Las vueltas de sesiones desconocidas se ignoran.
     */
    fun compute(summaries: List<SessionSummary>, laps: List<Lap>): AnalyticsSnapshot {
        val gpsReports = laps.groupBy { it.sessionId }.mapValues { (_, list) ->
            val sorted = list.sortedBy { it.number }
            val gps = sorted.filter { it.source == LapSource.GPS }.map { it.timeMs }
            val official = sorted.filter { it.source == LapSource.IMPORTED }.map { it.timeMs }
            if (gps.isNotEmpty() && official.isNotEmpty()) GpsAccuracy.compare(official, gps) else null
        }
        val rawBySession = laps.groupBy { it.sessionId }
        val lapsBySession = laps.groupBy { it.sessionId }.mapValues { (_, v) -> v.sortedBy { it.number }.map { it.timeMs } }

        // Récord por circuito.
        val recordByCircuit = summaries.groupBy { it.circuit.id }.mapValues { (_, list) ->
            list.mapNotNull { lapsBySession[it.session.id]?.minOrNull() }.minOrNull()
        }

        val sessionStats = summaries.map { summary ->
            val times = lapsBySession[summary.session.id].orEmpty()
            val sessionLaps = rawBySession[summary.session.id].orEmpty()
            val best = times.minOrNull()
            val record = recordByCircuit[summary.circuit.id]
            val delta = if (best != null && record != null) best - record else null
            SessionStats(
                summary = summary,
                lapTimes = times,
                bestMs = best,
                cleanAverageMs = cleanAverageMs(times),
                consistencyMs = consistencyMs(times),
                deltaToRecordMs = delta,
                isRecord = delta == 0L,
                gpsAccuracy = gpsReports[summary.session.id]?.takeIf { it.rows.isNotEmpty() },
                hasGpsLaps = sessionLaps.any { it.source == LapSource.GPS },
                stintCount = sessionLaps.mapNotNull { it.stintId }.distinct().size,
            )
        }

        val circuitStats = summaries.map { it.circuit }.distinctBy { it.id }.associate { circuit ->
            circuit.id to circuitStats(circuit, sessionStats.filter { it.summary.circuit.id == circuit.id })
        }
        return AnalyticsSnapshot(sessions = sessionStats, circuits = circuitStats)
    }

    /** Pendiente de la recta de mínimos cuadrados de [values] frente a su índice (0, 1, 2…); 0,0 con menos de 2 valores. */
    fun slope(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        val num = values.indices.sumOf { (it - meanX) * (values[it] - meanY) }
        val den = values.indices.sumOf { (it - meanX) * (it - meanX) }
        return num / den
    }

    /** Estadísticas de un circuito con circuito sin tandas (para listados). */
    fun emptyCircuitStats(circuit: Circuit) = circuitStats(circuit, emptyList())

    private fun circuitStats(circuit: Circuit, sessions: List<SessionStats>): CircuitStats {
        val withLaps = sessions.filter { it.lapTimes.isNotEmpty() }
        val pooledClean = withLaps.flatMap { cleanLaps(it.lapTimes) }

        val evolution = withLaps
            .sortedBy { it.summary.session.dateTime }
            .map { EvolutionPoint(it.summary.session.id, it.summary.session.dateTime, requireNotNull(it.bestMs)) }

        val karts = withLaps
            .filter { it.summary.session.kartNumber != null }
            .groupBy { requireNotNull(it.summary.session.kartNumber) }
            .map { (kart, list) ->
                KartStats(
                    kartNumber = kart,
                    bestMs = list.mapNotNull { it.bestMs }.min(),
                    cleanAverageMs = pooledAverage(list.flatMap { cleanLaps(it.lapTimes) }),
                    sessionCount = list.size,
                )
            }
            .sortedBy { it.bestMs }

        val conditions = TrackCondition.entries.mapNotNull { condition ->
            val group = withLaps.filter { it.summary.session.trackCondition == condition }
            if (group.isEmpty()) {
                null
            } else {
                ConditionStats(
                    condition = condition,
                    averageBestMs = Math.round(group.mapNotNull { it.bestMs }.average()),
                    sessionCount = group.size,
                )
            }
        }

        val accuracy = sessions.mapNotNull { it.gpsAccuracy?.meanAbsErrorMs }
        val consistencySeries = withLaps
            .filter { hasMeaningfulConsistency(it.lapTimes) }
            .sortedBy { it.summary.session.dateTime }
            .mapNotNull { st -> st.consistencyMs?.let { ConsistencyPoint(st.summary.session.id, st.summary.session.dateTime, it) } }
        return CircuitStats(
            consistencySeries = consistencySeries,
            consistencyTrendMsPerSession = slope(consistencySeries.map { it.consistencyMs })
                .takeIf { consistencySeries.size >= ConsistencyTrend.MIN_POINTS },
            circuit = circuit,
            gpsAccuracyMs = accuracy.takeIf { it.isNotEmpty() }?.average(),
            gpsAccuracySessions = accuracy.size,
            recordMs = withLaps.mapNotNull { it.bestMs }.minOrNull(),
            historicCleanAverageMs = pooledAverage(pooledClean),
            historicConsistencyMs = withLaps.filter { hasMeaningfulConsistency(it.lapTimes) }
                .mapNotNull { it.consistencyMs }
                .takeIf { it.isNotEmpty() }
                ?.average(),
            totalSessions = sessions.size,
            sessionsWithLaps = withLaps.size,
            evolution = evolution,
            karts = karts,
            conditions = conditions,
        )
    }

    private fun pooledAverage(values: List<Long>): Long? =
        if (values.isEmpty()) null else Math.round(values.sum().toDouble() / values.size)
}

data class SessionStats(
    val summary: SessionSummary,
    /** Tiempos ordenados por número de vuelta. */
    val lapTimes: List<Long>,
    val bestMs: Long?,
    val cleanAverageMs: Long?,
    val consistencyMs: Double?,
    /** Mejor de la tanda − récord del circuito; `null` si la tanda no tiene vueltas. */
    val deltaToRecordMs: Long?,
    val isRecord: Boolean,
    /** GPS vs oficial de la tanda (solo si tiene vueltas `GPS` e `IMPORTED`). */
    val gpsAccuracy: GpsAccuracy.Report? = null,
    /** La tanda tiene vueltas medidas por GPS (icono en la card). */
    val hasGpsLaps: Boolean = false,
    /** Nº de stints (Resistencia); 0 si la tanda no tiene. */
    val stintCount: Int = 0,
)

data class EvolutionPoint(val sessionId: Long, val dateTime: Long, val bestMs: Long)

data class KartStats(
    val kartNumber: Int,
    val bestMs: Long,
    val cleanAverageMs: Long?,
    val sessionCount: Int,
)

data class ConditionStats(
    val condition: TrackCondition,
    /** Media de la mejor vuelta de cada tanda con esa condición. */
    val averageBestMs: Long,
    val sessionCount: Int,
)

/** Consistencia (desviación típica de vueltas limpias, ms) de una tanda con al menos 2 vueltas limpias. */
data class ConsistencyPoint(val sessionId: Long, val dateTime: Long, val consistencyMs: Double)

enum class ConsistencyTrend {
    /** La desviación baja de una tanda a la siguiente: cada vez más regular. */
    IMPROVING,
    STABLE,
    WORSENING,
    ;

    companion object {
        /** Cambio medio por tanda (ms) a partir del cual se habla de tendencia. */
        const val THRESHOLD_MS = 10.0
        const val MIN_POINTS = 3

        fun of(slopeMsPerSession: Double): ConsistencyTrend = when {
            slopeMsPerSession <= -THRESHOLD_MS -> IMPROVING
            slopeMsPerSession >= THRESHOLD_MS -> WORSENING
            else -> STABLE
        }
    }
}

data class CircuitStats(
    val circuit: Circuit,
    val recordMs: Long?,
    /** Media de todas las vueltas limpias de todas las tandas (agrupadas, no media de medias). */
    val historicCleanAverageMs: Long?,
    /** Media de la consistencia de cada tanda con al menos 2 vueltas limpias (no mezcla karts distintos). */
    val historicConsistencyMs: Double?,
    /** Todas las tandas (también las vacías): es la cifra que bloquea el borrado del circuito. */
    val totalSessions: Int,
    val sessionsWithLaps: Int,
    /** Mejor vuelta de cada tanda con vueltas, por fecha ascendente. */
    val evolution: List<EvolutionPoint>,
    /** Ordenado por mejor vuelta ascendente; ignora tandas sin kart. */
    val karts: List<KartStats>,
    val conditions: List<ConditionStats>,
    /** Error medio absoluto GPS vs oficial (ms) de las tandas del circuito que lo permiten; `null` si ninguna. */
    val gpsAccuracyMs: Double? = null,
    /** Nº de tandas que entran en [gpsAccuracyMs]. */
    val gpsAccuracySessions: Int = 0,
    /** Consistencia de cada tanda con ≥ 2 vueltas limpias, por fecha ascendente. */
    val consistencySeries: List<ConsistencyPoint> = emptyList(),
    /** Pendiente de la consistencia por tanda (ms/tanda; negativa = mejora); `null` con menos de 3 tandas. */
    val consistencyTrendMsPerSession: Double? = null,
) {
    val consistencyTrend: ConsistencyTrend? get() = consistencyTrendMsPerSession?.let(ConsistencyTrend::of)

    /**
     * Hueco de mejora: media limpia histórica − récord. Es lo que separa tu ritmo habitual de tu mejor vuelta; no es un «mejor
     * teórico» por sectores (no se miden sectores).
     */
    val improvementGapMs: Long? get() = if (historicCleanAverageMs != null && recordMs != null) historicCleanAverageMs - recordMs else null
}

data class AnalyticsSnapshot(
    val sessions: List<SessionStats>,
    val circuits: Map<Long, CircuitStats>,
) {
    private val bySessionId: Map<Long, SessionStats> = sessions.associateBy { it.summary.session.id }
    fun session(id: Long): SessionStats? = bySessionId[id]
}
