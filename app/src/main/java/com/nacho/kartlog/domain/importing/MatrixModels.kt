package com.nacho.kartlog.domain.importing

/** Fila de la matriz: una vuelta con un tiempo por columna, o la fila final de medias. */
data class MatrixRow(
    val isAverage: Boolean,
    /** Nº de vuelta (nulo en la fila de medias). */
    val number: Int?,
    /** Un valor por columna; `null` = celda vacía o ilegible. */
    val times: List<TimeMatch?>,
    val firstCell: Int,
    val lastCell: Int,
    /** Índice (en las celdas del texto) de la celda de cada tiempo de [times]; `-1` en las celdas vacías. */
    val timeCells: List<Int> = emptyList(),
)

/** Bloque de la matriz: cabecera de columnas + filas de vuelta + fila de medias opcional. */
data class MatrixBlock(
    val header: List<HeaderColumn>,
    val laps: List<MatrixRow>,
    val average: MatrixRow?,
    val firstCell: Int,
    val lastCell: Int,
)

data class HeaderColumn(val name: String?, val kartNumber: Int?)

/**
 * Perfil de formato. Detecta los bloques de la matriz en un texto ya preparado, o devuelve `null`
 * si no reconoce el formato. **Añadir un perfil nuevo** (otro cronometraje, otro idioma) es crear una
 * implementación y pasarla a [LapTextParser]; el resto del parser no se toca.
 */
interface LapTextProfile {
    val name: String
    fun detectBlocks(text: PreparedText): List<MatrixBlock>?
}
