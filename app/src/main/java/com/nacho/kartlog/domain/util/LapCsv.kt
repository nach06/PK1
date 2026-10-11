package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.model.Lap

/** Exportación de vueltas a CSV (separador `;`, compatible con Excel en español). */
object LapCsv {
    const val HEADER = "numero_vuelta;tiempo;tiempo_ms;origen"

    fun build(laps: List<Lap>): String = buildString {
        append(HEADER).append('\n')
        laps.sortedBy { it.number }.forEach { lap ->
            append(lap.number).append(';')
                .append(LapTime.format(lap.timeMs)).append(';')
                .append(lap.timeMs).append(';')
                .append(lap.source.name).append('\n')
        }
    }
}
