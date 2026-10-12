package com.nacho.kartlog.domain.gps

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Calibración en pista (dominio puro): se comparan los cruces de meta que detecta el GPS con los instantes en que el piloto
 * (o su acompañante) pulsó «AQUÍ» al pasar por la línea. Ambos en el reloj monótono de los fixes (`elapsedRealtimeNanos`).
 *
 * `sesgo = cruce GPS − pulsación`: positivo = el GPS «ve» el cruce más tarde que la pulsación. Incluye el retardo del proveedor
 * de posición **y** el tiempo de reacción de quien pulsa (suele ser 0,1–0,25 s tarde, es decir, de signo contrario): por eso se
 * enseña el sesgo medio y su dispersión, no un valor «verdadero». Un sesgo **constante** (poca dispersión) es el que se
 * puede compensar con «Compensación de retardo (ms)».
 */
object FieldCalibration {
    /** Máxima separación para emparejar una pulsación con un cruce. */
    const val PAIR_WINDOW_NS = 3_000_000_000L
    const val CONSTANT_MAX_STDEV_MS = 100.0
    const val MIN_ACTIONABLE_BIAS_MS = 50.0

    data class Pair(val tapNs: Long, val crossingNs: Long) {
        val biasMs: Double get() = (crossingNs - tapNs) / 1e6
    }

    /** Una vuelta entre dos pares consecutivos: tiempo por GPS, tiempo por pulsaciones y su diferencia (GPS − manual). */
    data class LapRow(val lap: Int, val gpsMs: Long, val manualMs: Long) {
        val diffMs: Long get() = gpsMs - manualMs
    }

    data class Report(
        val pairs: List<Pair>,
        val laps: List<LapRow>,
        /** Sesgo medio de los cruces (ms), `null` sin pares. */
        val meanBiasMs: Double?,
        val stdevBiasMs: Double?,
        val unmatchedTaps: Int,
        val unmatchedCrossings: Int,
    ) {
        /** Hay un sesgo estable y de tamaño suficiente como para proponer una compensación. */
        val constantBias: Boolean
            get() = pairs.size >= 3 && meanBiasMs != null && stdevBiasMs != null &&
                abs(meanBiasMs) > MIN_ACTIONABLE_BIAS_MS && stdevBiasMs <= CONSTANT_MAX_STDEV_MS

        /** Compensación propuesta (ms, múltiplo de 50, dentro de ±500) o `null` si no hay sesgo constante. */
        val proposedDelayMs: Int?
            get() = if (!constantBias) null else (Math.round(meanBiasMs!! / 50.0) * 50).toInt().coerceIn(-500, 500)
    }

    fun compute(tapsNs: List<Long>, crossingsNs: List<Long>): Report {
        val taps = tapsNs.sorted()
        val crossings = crossingsNs.sorted()
        val usedCrossings = BooleanArray(crossings.size)
        val pairs = ArrayList<Pair>()
        var unmatchedTaps = 0
        for (tap in taps) {
            var best = -1
            for (i in crossings.indices) {
                if (usedCrossings[i]) continue
                val d = abs(crossings[i] - tap)
                if (d <= PAIR_WINDOW_NS && (best < 0 || d < abs(crossings[best] - tap))) best = i
            }
            if (best < 0) unmatchedTaps++ else { usedCrossings[best] = true; pairs += Pair(tap, crossings[best]) }
        }
        val laps = pairs.zipWithNext().mapIndexed { i, (a, b) ->
            LapRow(i + 1, Math.round((b.crossingNs - a.crossingNs) / 1e6), Math.round((b.tapNs - a.tapNs) / 1e6))
        }
        val biases = pairs.map { it.biasMs }
        val mean = if (biases.isEmpty()) null else biases.average()
        val stdev = mean?.let { m -> sqrt(biases.sumOf { (it - m) * (it - m) } / biases.size) }
        return Report(pairs, laps, mean, stdev, unmatchedTaps, usedCrossings.count { !it })
    }
}
