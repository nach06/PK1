package com.nacho.kartlog.domain.importing

import com.nacho.kartlog.domain.model.SessionType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Metadatos de la sesión: fecha/hora, duración, título, tipo y circuito conocido. */
internal object MetadataExtractor {
    private val date = Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:\\s*[-–—,]?\\s*(\\d{1,2}):(\\d{2}))?")
    private val duration = Regex("(\\d{1,3})\\s*min(?:utos?|\\.|\\b)", RegexOption.IGNORE_CASE)
    private val title = Regex("^\\s*sesi[oó]n\\s+\\S.*$", RegexOption.IGNORE_CASE)

    class Result(
        val label: String?,
        val dateTime: LocalDateTime?,
        val durationMin: Int?,
        val type: SessionType?,
        val circuitName: String?,
    )

    /** [headerLines]: las líneas previas a los datos (aquí sí aparecen el título, la fecha y el tipo). */
    fun extract(headerLines: List<String>, knownCircuits: List<String>): Result {
        val text = headerLines.joinToString("\n")
        val label = headerLines.firstOrNull { title.matches(it) }?.trim()

        val dateTime = date.find(text)?.let { m ->
            val (d, mo, y) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            val h = m.groupValues[4].toIntOrNull()
            val min = m.groupValues[5].toIntOrNull()
            runCatching {
                LocalDateTime.of(LocalDate.of(y, mo, d), if (h != null && min != null) LocalTime.of(h, min) else LocalTime.MIDNIGHT)
            }.getOrNull()
        }

        val durationMin = duration.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..600 }

        val plain = normalizeKey(text)
        val type = when {
            "clasificacion" in plain || "qualifying" in plain || "qualy" in plain -> SessionType.QUALIFYING
            "carrera" in plain || "race" in plain -> SessionType.RACE
            "tandalibre" in plain || "libre" in plain || "freepractice" in plain || "practice" in plain -> SessionType.FREE
            else -> null
        }

        val circuit = knownCircuits
            .filter { normalizeKey(it).length >= 3 && normalizeKey(it) in plain }
            .maxByOrNull { normalizeKey(it).length }

        return Result(label, dateTime, durationMin, type, circuit)
    }
}
