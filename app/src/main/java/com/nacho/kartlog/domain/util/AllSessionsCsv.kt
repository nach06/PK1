package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.SessionSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Exportación de todos los datos: una fila por vuelta, separador `;`.
 * Columnas: `fecha;circuito;tipo;kart;lastre_kg;temp_ambiente;temp_pista;estado_pista;vuelta;tiempo;tiempo_ms;origen`.
 * Los campos de texto se entrecomillan si contienen `;`, comillas o saltos de línea.
 * Las tandas sin vueltas no generan filas.
 */
object AllSessionsCsv {
    const val HEADER =
        "fecha;circuito;tipo;kart;lastre_kg;temp_ambiente;temp_pista;estado_pista;vuelta;tiempo;tiempo_ms;origen"

    private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun build(
        summaries: List<SessionSummary>,
        laps: List<Lap>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = buildString {
        append(HEADER).append('\n')
        val lapsBySession = laps.groupBy { it.sessionId }
        summaries.sortedWith(compareBy({ it.session.dateTime }, { it.session.id })).forEach { summary ->
            val session = summary.session
            val prefix = listOf(
                dateFormat.format(Instant.ofEpochMilli(session.dateTime).atZone(zone)),
                escape(summary.circuit.name),
                session.type.name,
                session.kartNumber?.toString().orEmpty(),
                number(session.ballastKg),
                number(session.ambientTempC),
                number(session.trackTempC),
                session.trackCondition.name,
            )
            lapsBySession[session.id].orEmpty().sortedBy { it.number }.forEach { lap ->
                append(prefix.joinToString(";")).append(';')
                    .append(lap.number).append(';')
                    .append(LapTime.format(lap.timeMs)).append(';')
                    .append(lap.timeMs).append(';')
                    .append(lap.source.name).append('\n')
            }
        }
    }

    /** `22.0` -> `22`; `22.5` -> `22.5`; nulo -> vacío. Siempre con punto decimal. */
    private fun number(value: Float?): String = when {
        value == null -> ""
        value % 1f == 0f -> value.toInt().toString()
        else -> String.format(Locale.ROOT, "%.1f", value)
    }

    private fun escape(text: String): String =
        if (text.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + text.replace("\"", "\"\"") + "\""
        } else {
            text
        }
}
