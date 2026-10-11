package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.model.Lap
import java.util.Locale
import kotlin.math.abs

/** Utilidades de tiempos de vuelta. Todo se calcula en milisegundos. */
object LapTime {
    /** Máximo de dígitos tecleables: `m` + `ss` + `mmm`. */
    const val MAX_DIGITS = 6

    /** Formatea milisegundos como `m:ss.mmm`, p. ej. 48532 -> `0:48.532`. */
    fun format(timeMs: Long): String {
        val total = timeMs.coerceAtLeast(0)
        val minutes = total / 60_000
        val seconds = (total % 60_000) / 1_000
        val millis = total % 1_000
        return String.format(Locale.ROOT, "%d:%02d.%03d", minutes, seconds, millis)
    }

    /**
     * Diferencia con otro tiempo, con signo y coma decimal: 2927 -> `+2,927`, -350 -> `-0,350`,
     * 62345 -> `+1:02,345`.
     */
    fun formatDelta(deltaMs: Long): String {
        val sign = if (deltaMs < 0) "-" else "+"
        val total = abs(deltaMs)
        val minutes = total / 60_000
        val seconds = (total % 60_000) / 1_000
        val millis = total % 1_000
        return if (minutes > 0) {
            String.format(Locale.ROOT, "%s%d:%02d,%03d", sign, minutes, seconds, millis)
        } else {
            String.format(Locale.ROOT, "%s%d,%03d", sign, seconds, millis)
        }
    }

    /** Consistencia (desviación típica en ms) como `±0,214` (segundos con coma). */
    fun formatConsistency(stdDevMs: Double): String {
        val ms = Math.round(stdDevMs)
        return String.format(Locale.ROOT, "±%d,%03d", ms / 1_000, ms % 1_000)
    }

    /** Muestra los dígitos tecleados rellenando de derecha a izquierda: `48532` -> `0:48.532`. */
    fun formatDigits(digits: String): String {
        val padded = digits.padStart(MAX_DIGITS, '0')
        return "${padded[0]}:${padded.substring(1, 3)}.${padded.substring(3, 6)}"
    }

    /** Añade dígitos ignorando ceros a la izquierda y respetando el máximo. */
    fun appendDigits(current: String, input: String): String {
        val next = (current + input).trimStart('0')
        return if (next.length > MAX_DIGITS) current else next
    }

    /**
     * Convierte los dígitos tecleados a milisegundos.
     * Devuelve `null` si están vacíos, el tiempo es cero o los segundos son >= 60.
     */
    fun digitsToMs(digits: String): Long? {
        if (digits.isEmpty()) return null
        val padded = digits.padStart(MAX_DIGITS, '0')
        val minutes = padded.substring(0, 1).toLong()
        val seconds = padded.substring(1, 3).toLong()
        val millis = padded.substring(3, 6).toLong()
        if (seconds >= 60) return null
        val total = minutes * 60_000 + seconds * 1_000 + millis
        return total.takeIf { it > 0 }
    }

    /** `true` si hay dígitos pero no forman un tiempo válido. */
    fun isInvalid(digits: String): Boolean = digits.isNotEmpty() && digitsToMs(digits) == null

    /** Dígitos equivalentes a un tiempo, para precargar la edición de una vuelta. */
    fun msToDigits(timeMs: Long): String {
        val minutes = timeMs / 60_000
        val seconds = (timeMs % 60_000) / 1_000
        val millis = timeMs % 1_000
        return String.format(Locale.ROOT, "%d%02d%03d", minutes, seconds, millis).trimStart('0')
    }

    fun bestLapMs(laps: List<Lap>): Long? = laps.minOfOrNull { it.timeMs }


    /** Segundos con tres decimales y coma («0,053»), para errores y desviaciones; los tiempos de vuelta siguen `m:ss.mmm`. */
    fun formatSeconds(ms: Double): String = String.format(Locale("es", "ES"), "%.3f", ms / 1000.0)
}
