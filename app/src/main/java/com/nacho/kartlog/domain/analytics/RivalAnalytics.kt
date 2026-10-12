package com.nacho.kartlog.domain.analytics

import com.nacho.kartlog.domain.importing.normalizeKey
import com.nacho.kartlog.domain.model.Participant
import kotlin.math.sqrt

/** Un piloto rival de una sesión importada con sus vueltas (las de los demás se guardan aparte de las propias). */
data class RivalEntry(val participant: Participant, val lapTimesMs: List<Long>)

/**
 * Análisis de rivales (dominio puro, Sprint 8, C1/C2/C4): con quién has compartido sesión, cómo vas frente a cada uno y cómo
 * influyen las condiciones en tu ritmo. Solo se usa dentro de la app: ningún export público lo incluye.
 */
object RivalAnalytics {
    /** Clave de comparación de un nombre (sin tildes ni signos) resuelta con los alias que el usuario ha unido a mano. */
    fun key(name: String, aliases: Map<String, String> = emptyMap()): String {
        val k = normalizeKey(name)
        return aliases[k] ?: k
    }

    /** Une [from] a [into] (claves ya normalizadas): todo alias que apuntaba a [from] pasa a [into]. Unir un piloto consigo mismo no hace nada. */
    fun merge(aliases: Map<String, String>, from: String, into: String): Map<String, String> {
        val target = aliases[into] ?: into
        val source = aliases[from] ?: from
        if (source == target) return aliases
        return aliases.mapValues { (_, v) -> if (v == source) target else v } + (source to target)
    }

    data class Encounter(
        val sessionId: Long,
        val dateTime: Long,
        val circuitId: Long,
        val myBestMs: Long,
        val myAvgMs: Long?,
        val theirBestMs: Long,
        val theirAvgMs: Long?,
    ) {
        /** Mejor mía − mejor suya (negativo = ganaste). */
        val bestDiffMs: Long get() = myBestMs - theirBestMs
        val avgDiffMs: Long? get() = if (myAvgMs != null && theirAvgMs != null) myAvgMs - theirAvgMs else null
    }

    data class Rival(
        val key: String,
        val displayName: String,
        /** De la más antigua a la más reciente. */
        val encounters: List<Encounter>,
        val bestMs: Long,
    ) {
        val sessions: Int get() = encounters.size
        /** Tu diferencia media de ritmo (media limpia tuya − suya) en las sesiones compartidas; `null` sin datos. */
        val meanAvgDiffMs: Long? get() = encounters.mapNotNull { it.avgDiffMs }.takeIf { it.isNotEmpty() }?.let { Math.round(it.average()) }
    }

    fun rivals(sessions: List<SessionStats>, entries: List<RivalEntry>, aliases: Map<String, String> = emptyMap()): List<Rival> {
        val bySession = sessions.filter { it.bestMs != null }.associateBy { it.summary.session.id }
        val grouped = entries.filter { !it.participant.isMe && !it.participant.name.isNullOrBlank() && it.participant.sessionId in bySession }
            .groupBy { key(it.participant.name.orEmpty(), aliases) }
        return grouped.mapNotNull { (k, list) ->
            val encounters = list.mapNotNull { e ->
                val mine = bySession[e.participant.sessionId] ?: return@mapNotNull null
                val theirBest = e.lapTimesMs.minOrNull() ?: e.participant.bestMs ?: return@mapNotNull null
                val theirAvg = if (e.lapTimesMs.isNotEmpty()) Analytics.cleanAverageMs(e.lapTimesMs) else e.participant.avgMs
                Encounter(mine.summary.session.id, mine.summary.session.dateTime, mine.summary.session.circuitId, mine.bestMs!!, mine.cleanAverageMs, theirBest, theirAvg)
            }.sortedBy { it.dateTime }
            if (encounters.isEmpty()) null else Rival(k, list.maxByOrNull { it.participant.name.orEmpty().length }!!.participant.name.orEmpty(), encounters, encounters.minOf { it.theirBestMs })
        }.sortedWith(compareByDescending<Rival> { it.sessions }.thenBy { it.displayName.lowercase() })
    }

    data class HeadToHead(
        val winsBest: Int, val lossesBest: Int, val winsAvg: Int, val lossesAvg: Int,
        /** Racha más larga de sesiones seguidas ganando por mejor vuelta. */
        val bestStreak: Int,
        /** Diferencia de media por sesión (mía − suya) en orden cronológico; las sesiones sin media se omiten. */
        val avgDiffsMs: List<Long>,
    )

    fun headToHead(rival: Rival): HeadToHead {
        var streak = 0
        var best = 0
        rival.encounters.forEach { if (it.bestDiffMs < 0) { streak++; best = maxOf(best, streak) } else streak = 0 }
        val avg = rival.encounters.mapNotNull { it.avgDiffMs }
        return HeadToHead(
            winsBest = rival.encounters.count { it.bestDiffMs < 0 }, lossesBest = rival.encounters.count { it.bestDiffMs > 0 },
            winsAvg = avg.count { it < 0 }, lossesAvg = avg.count { it > 0 },
            bestStreak = best, avgDiffsMs = avg,
        )
    }

    // ---------------------------------------------------------------- Condiciones (C4)

    enum class Variable { TRACK_TEMP, AMBIENT_TEMP, BALLAST }

    /** Puntos (valor, ritmo relativo en %: positivo = más lento que tu media del circuito) y su correlación de Pearson. */
    data class Correlation(val variable: Variable, val r: Double, val points: List<Pair<Double, Double>>) { val n: Int get() = points.size }

    const val MIN_SESSIONS_FOR_CONDITIONS = 8

    /** `null` si no hay [MIN_SESSIONS_FOR_CONDITIONS] tandas con media limpia; si no, una correlación por cada variable con datos suficientes. */
    fun conditionCorrelations(sessions: List<SessionStats>): List<Correlation>? {
        val withAvg = sessions.filter { it.cleanAverageMs != null }
        if (withAvg.size < MIN_SESSIONS_FOR_CONDITIONS) return null
        val circuitMean = withAvg.groupBy { it.summary.session.circuitId }.mapValues { (_, l) -> l.map { it.cleanAverageMs!! }.average() }
        fun relative(s: SessionStats) = (s.cleanAverageMs!! / circuitMean.getValue(s.summary.session.circuitId) - 1.0) * 100.0
        return Variable.entries.mapNotNull { v ->
            val pts = withAvg.mapNotNull { s ->
                val x = when (v) {
                    Variable.TRACK_TEMP -> s.summary.session.trackTempC
                    Variable.AMBIENT_TEMP -> s.summary.session.ambientTempC
                    Variable.BALLAST -> s.summary.session.ballastKg
                }?.toDouble() ?: return@mapNotNull null
                x to relative(s)
            }
            if (pts.size < MIN_SESSIONS_FOR_CONDITIONS) null else pearson(pts)?.let { Correlation(v, it, pts) }
        }
    }

    internal fun pearson(points: List<Pair<Double, Double>>): Double? {
        val mx = points.map { it.first }.average()
        val my = points.map { it.second }.average()
        val sxx = points.sumOf { (it.first - mx) * (it.first - mx) }
        val syy = points.sumOf { (it.second - my) * (it.second - my) }
        if (sxx < 1e-9 || syy < 1e-9) return null
        return points.sumOf { (it.first - mx) * (it.second - my) } / sqrt(sxx * syy)
    }
}
