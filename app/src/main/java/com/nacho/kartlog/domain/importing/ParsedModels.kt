package com.nacho.kartlog.domain.importing

import com.nacho.kartlog.domain.model.SessionType
import java.time.LocalDateTime

/** Vuelta leída del documento. */
data class ParsedLap(
    val number: Int,
    val timeMs: Long,
    /** El OCR corrigió letras o separadores (`O→0`, `l→1`, `S→5`, `B→8`, `1.12.467`) dentro de este tiempo. */
    val ocrFixed: Boolean = false,
    /** Texto tal y como llegó del OCR (para enseñarlo en las celdas dudosas). */
    val rawText: String? = null,
    /** Tiempo con forma válida pero fuera de 15 s – 5 min: se muestra en rojo y no se guarda. */
    val invalid: Boolean = false,
    /** Línea y columna del texto reconstruido de la que sale este tiempo (`-1` si no se conoce): enlaza con la caja del OCR. */
    val cellLine: Int = -1,
    val cellCol: Int = -1,
)

/** Formato de hoja de resultados reconocido. */
enum class ImportFormat {
    /** Matriz vuelta × piloto (filas = vueltas, columnas = pilotos) con fila `Avg.` opcional. */
    MATRIX,

    /** Una fila por piloto con sus vueltas en columnas. */
    ROW_PER_DRIVER,

    /** Solo mis vueltas: una única columna o una lista de tiempos. */
    SINGLE_COLUMN,

    /** Tabla resumen (`Pos, Piloto, Kart, V., Mejor V.`): sin tiempos de vuelta. */
    SUMMARY,
}

/**
 * Un piloto (columna de la matriz) con los datos de control que traiga el documento:
 * mejor vuelta, nº de vueltas (tabla resumen) y media (fila `Avg.`).
 */
data class ParsedDriver(
    val name: String?,
    val kartNumber: Int?,
    val position: Int? = null,
    val laps: List<ParsedLap> = emptyList(),
    val reportedBestMs: Long? = null,
    val reportedLapCount: Int? = null,
    val reportedAvgMs: Long? = null,
    /** Posición de la columna en el documento (para pilotos sin nombre ni kart). */
    val columnIndex: Int = 0,
    /** Celdas de tiempo ilegibles o fuera de rango (rojas en la revisión): no cuentan como vueltas. */
    val invalidCells: List<ParsedLap> = emptyList(),
) {
    /** Texto para chips y listas: `Nacho · Kart 28`, `Kart 26` o `Piloto 3`. */
    val displayName: String
        get() = when {
            name != null && kartNumber != null -> "$name · Kart $kartNumber"
            name != null -> name
            kartNumber != null -> "Kart $kartNumber"
            else -> "Piloto ${columnIndex + 1}"
        }

    /** Identificador estable dentro del documento. */
    val key: String
        get() = "${normalizeKey(name.orEmpty())}|${kartNumber ?: ""}|$columnIndex"
}

/** Resultado de analizar un texto. */
data class ParsedDocument(
    /** Título de la sesión (p. ej. «Sesión 46 GP3 DIEGO»). */
    val label: String? = null,
    val dateTime: LocalDateTime? = null,
    val durationMin: Int? = null,
    val type: SessionType? = null,
    /** Nombre del circuito *existente* que coincide con el texto. */
    val circuitName: String? = null,
    val drivers: List<ParsedDriver> = emptyList(),
    /** Tiempos con forma válida pero fuera de 15 s – 5 min: se descartan y se listan aquí. */
    val ignoredTimes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** Formato con el que se interpretó la hoja (`null` si no se reconoció ninguno). */
    val format: ImportFormat? = null,
) {
    val isEmpty: Boolean get() = drivers.none { it.laps.isNotEmpty() }
}

internal fun normalizeKey(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .filter { it.isLetterOrDigit() }
