package com.nacho.kartlog.domain.importing

/** Un token con forma de tiempo encontrado en un texto. */
data class TimeMatch(
    val start: Int,
    val end: Int,
    val raw: String,
    /** Milisegundos (puede estar fuera del rango válido: ver [inRange]). */
    val timeMs: Long,
    val inRange: Boolean,
    /** `true` si hubo que corregir letras del OCR (`O→0`, `l/I→1`, `S→5`) dentro del token. */
    val ocrFixed: Boolean,
)

/**
 * Reconocimiento de tiempos de vuelta en texto libre.
 *
 * Formatos: `1:12.467`, `01:12.467`, `1:12,467`, `72.467`, `72,467`, `1'12"467` (también con comillas tipográficas).
 * Siempre con **tres decimales**: así `45.3` (km/h) o `0.32` no se toman por tiempos. Quedan fuera:
 * los precedidos de signo pegado (`+0.321`, `-1:02.345`: diferencias), los seguidos de unidades de
 * velocidad y los que no estén entre 15 s y 5 min (se listan como ignorados).
 *
 * Las correcciones de OCR (`O→0`, `l/I→1`, `S→5`) solo se aplican **dentro de estos tokens**.
 */
object TimeTokens {
    const val MIN_MS = 15_000L
    const val MAX_MS = 300_000L

    /** Dígito o letra que el OCR suele confundir con un dígito. */
    private const val D = "[0-9OoIlS]"
    private const val NOT_BEFORE = "(?<![0-9A-Za-z+\\-])"

    private val minSecMs = Regex("$NOT_BEFORE($D{1,2}):($D{2})[.,]($D{3})(?![0-9A-Za-z])")
    // Sin «:» ni separadores antes: así no se parte `-1:02.345` ni `1.234.567` en trozos que parezcan tiempos.
    private val secMs = Regex("(?<![0-9A-Za-z+\\-:.,])($D{1,3})[.,]($D{3})(?![0-9A-Za-z]|[.,][0-9])")
    private val tick = Regex("$NOT_BEFORE($D{1,2})['’′]($D{2})[\"”″]\\s?($D{3})(?![0-9A-Za-z])")
    private val speedUnit = Regex("^\\s*(km/h|kph|mph|km|m/s)", RegexOption.IGNORE_CASE)

    fun inRange(ms: Long): Boolean = ms in MIN_MS..MAX_MS

    /** Todos los tiempos del texto, en orden de aparición. */
    fun find(text: String): List<TimeMatch> {
        val found = mutableListOf<TimeMatch>()
        val taken = mutableListOf<IntRange>()

        fun overlaps(r: IntRange) = taken.any { it.first <= r.last && r.first <= it.last }

        fun add(m: MatchResult, toMs: (MatchResult) -> Long?) {
            val range = m.range
            if (overlaps(range)) return
            if (speedUnit.containsMatchIn(text.substring(range.last + 1))) return
            val ms = toMs(m) ?: return
            val raw = m.value
            found += TimeMatch(range.first, range.last + 1, raw, ms, inRange(ms), raw.any { it in OCR_LETTERS })
            taken += range
        }

        minSecMs.findAll(text).forEach { m ->
            add(m) { r ->
                val min = digits(r.groupValues[1]).toLongOrNull()
                val sec = digits(r.groupValues[2]).toLongOrNull()
                val millis = digits(r.groupValues[3]).toLongOrNull()
                if (min == null || sec == null || millis == null || sec >= 60) null else min * 60_000 + sec * 1_000 + millis
            }
        }
        tick.findAll(text).forEach { m ->
            add(m) { r ->
                val min = digits(r.groupValues[1]).toLongOrNull()
                val sec = digits(r.groupValues[2]).toLongOrNull()
                val millis = digits(r.groupValues[3]).toLongOrNull()
                if (min == null || sec == null || millis == null || sec >= 60) null else min * 60_000 + sec * 1_000 + millis
            }
        }
        secMs.findAll(text).forEach { m ->
            add(m) { r ->
                val sec = digits(r.groupValues[1]).toLongOrNull()
                val millis = digits(r.groupValues[2]).toLongOrNull()
                if (sec == null || millis == null) null else sec * 1_000 + millis
            }
        }
        return found.sortedBy { it.start }
    }

    /** Interpreta un token completo (sin texto alrededor). `null` si no es un tiempo con forma válida. */
    fun parseExact(token: String): TimeMatch? {
        val trimmed = token.trim()
        val match = find(trimmed).singleOrNull() ?: return null
        return match.takeIf { it.start == 0 && it.end == trimmed.length }
    }

    private val OCR_LETTERS = setOf('O', 'o', 'I', 'l', 'S')

    private fun digits(s: String): String = s.map {
        when (it) {
            'O', 'o' -> '0'
            'I', 'l' -> '1'
            'S' -> '5'
            else -> it
        }
    }.joinToString("")
}
