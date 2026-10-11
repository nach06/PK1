package com.nacho.kartlog.domain.importing

import com.nacho.kartlog.domain.util.LapTime
import kotlin.math.abs

/** Resultado de una comprobación cruzada. */
enum class CheckState {
    /** El documento aporta el dato y cuadra con las vueltas leídas (✓). */
    OK,

    /** El documento aporta el dato y NO cuadra (⚠). */
    WARN,

    /** El documento no trae ese dato de control: no se puede comprobar. */
    NOT_AVAILABLE,
}

/** Corrección propuesta de un dígito mal leído; nunca se aplica sola. */
data class LapSuggestion(val lapNumber: Int, val currentMs: Long, val proposedMs: Long)

data class DriverChecks(
    /** `mejor == min(vueltas)`. */
    val best: CheckState,
    /** `nº de vueltas == V.`. */
    val lapCount: CheckState,
    /** `|media del documento − floor(media de las vueltas)| <= 1 ms` (el documento trunca, no redondea). */
    val average: CheckState,
    val computedBestMs: Long?,
    val computedAvgMs: Long?,
    /** Corrección de un dígito que haría cuadrar los controles, si hay una única candidata clara. */
    val suggestion: LapSuggestion?,
    /** Vueltas sospechosas (la de [suggestion], o todas las candidatas si no hay una clara). */
    val suspectLapNumbers: Set<Int>,
) {
    val allOk: Boolean
        get() = listOf(best, lapCount, average).none { it == CheckState.WARN }
}

object ImportValidation {
    /** Tolerancia de la media: el documento la trunca y puede haber pequeñas diferencias de redondeo. */
    const val AVG_TOLERANCE_MS = 1L

    /** Parejas de dígitos que el OCR suele confundir (en ambos sentidos). */
    private val confusablePairs = setOf(
        0 to 8, 3 to 8, 1 to 7, 5 to 6, 6 to 8, 4 to 9, 0 to 6, 2 to 7, 3 to 5, 0 to 9,
    ).flatMap { listOf(it, it.second to it.first) }.toSet()

    /**
     * Comprueba las vueltas de un piloto contra los datos de control del documento.
     * @param laps pares (nº de vuelta, tiempo) tal y como están ahora (tras las ediciones del usuario).
     */
    fun check(
        laps: List<Pair<Int, Long>>,
        reportedBestMs: Long?,
        reportedLapCount: Int?,
        reportedAvgMs: Long?,
    ): DriverChecks {
        val times = laps.map { it.second }
        val best = times.minOrNull()
        val avg = if (times.isEmpty()) null else times.sum() / times.size // truncado (división entera), como el documento

        val bestState = state(reportedBestMs) { best != null && best == it }
        val countState = state(reportedLapCount) { times.size == it }
        val avgState = state(reportedAvgMs) { avg != null && abs(avg - it) <= AVG_TOLERANCE_MS }

        var suggestion: LapSuggestion? = null
        var suspects: Set<Int> = emptySet()
        if (bestState == CheckState.WARN || avgState == CheckState.WARN) {
            val found = findSingleDigitFix(laps, reportedBestMs, reportedAvgMs)
            suggestion = found.suggestion
            suspects = found.suspects
        }
        return DriverChecks(bestState, countState, avgState, best, avg, suggestion, suspects)
    }

    private inline fun <T : Any> state(reported: T?, matches: (T) -> Boolean): CheckState = when {
        reported == null -> CheckState.NOT_AVAILABLE
        matches(reported) -> CheckState.OK
        else -> CheckState.WARN
    }

    private class Candidate(
        val lapNumber: Int,
        val currentMs: Long,
        val proposedMs: Long,
        val consistentWithBest: Boolean,
        val confusable: Boolean,
        val meanDistance: Double,
    )

    private class Found(val suggestion: LapSuggestion?, val suspects: Set<Int>)

    /**
     * Prueba a cambiar **un solo dígito** de una sola celda (`m ss mmm`) y se queda con los cambios tras
     * los cuales cuadran todos los controles disponibles. Orden: que el mínimo coincida con la mejor del
     * documento, que sea una confusión típica del OCR (3↔8, 1↔7, 5↔6, 0↔8…), que la media quede lo más
     * cerca posible y, por último, el cambio más pequeño. Si las dos primeras candidatas empatan, no se
     * propone nada y solo se marcan las celdas implicadas.
     */
    private fun findSingleDigitFix(laps: List<Pair<Int, Long>>, reportedBest: Long?, reportedAvg: Long?): Found {
        if (laps.isEmpty()) return Found(null, emptySet())
        val sum = laps.sumOf { it.second }
        val n = laps.size
        val candidates = mutableListOf<Candidate>()
        laps.forEachIndexed { index, (number, ms) ->
            val digits = "%06d".format(java.util.Locale.ROOT, minutesSecondsMillisAsNumber(ms))
            digits.forEachIndexed { position, original ->
                for (d in '0'..'9') {
                    if (d == original) continue
                    val changed = digits.replaceRange(position, position + 1, d.toString())
                    val newMs = LapTime.digitsToMs(changed.trimStart('0')) ?: continue
                    if (!TimeTokens.inRange(newMs)) continue
                    val newSum = sum - ms + newMs
                    val newAvg = newSum / n
                    val newBest = laps.mapIndexed { i, l -> if (i == index) newMs else l.second }.min()
                    val avgOk = reportedAvg == null || abs(newAvg - reportedAvg) <= AVG_TOLERANCE_MS
                    val bestOk = reportedBest == null || newBest == reportedBest
                    if (!avgOk || !bestOk) continue
                    candidates += Candidate(
                        lapNumber = number,
                        currentMs = ms,
                        proposedMs = newMs,
                        consistentWithBest = reportedBest != null && newBest == reportedBest,
                        confusable = (original - '0') to (d - '0') in confusablePairs,
                        meanDistance = if (reportedAvg == null) 0.0 else abs(newSum.toDouble() / n - (reportedAvg + 0.5)),
                    )
                }
            }
        }
        if (candidates.isEmpty()) return Found(null, emptySet())

        val ranked = candidates.sortedWith(
            compareByDescending<Candidate> { it.consistentWithBest }
                .thenByDescending { it.confusable }
                .thenBy { it.meanDistance }
                .thenBy { abs(it.proposedMs - it.currentMs) },
        )
        val top = ranked.first()
        val tied = ranked.filter {
            it.consistentWithBest == top.consistentWithBest && it.confusable == top.confusable &&
                abs(it.meanDistance - top.meanDistance) < 1e-9 && abs(it.proposedMs - it.currentMs) == abs(top.proposedMs - top.currentMs)
        }
        val topGroupLaps = ranked.filter { it.consistentWithBest == top.consistentWithBest && it.confusable == top.confusable }
            .map { it.lapNumber }.toSet()
        return if (tied.size == 1) {
            Found(LapSuggestion(top.lapNumber, top.currentMs, top.proposedMs), setOf(top.lapNumber))
        } else {
            Found(null, topGroupLaps)
        }
    }

    /** El entero `mssmmm` de un tiempo (1:12.467 → 112467), cuyas 6 cifras son las que puede leer mal el OCR. */
    private fun minutesSecondsMillisAsNumber(ms: Long): Long {
        val minutes = ms / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return minutes * 100_000 + seconds * 1_000 + millis
    }
}
