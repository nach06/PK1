package com.nacho.kartlog.domain.importing

/** Una celda del texto: una pieza separada por tabulador, por 2+ espacios, por línea o un tiempo suelto. */
data class Cell(
    val text: String,
    /** Índice de la línea de origen (para saber qué celdas pertenecen a la misma fila). */
    val line: Int,
    /** `true` si la celda venía de una línea con tabuladores (las celdas vacías son significativas). */
    val fromTabs: Boolean,
)

/** Qué es una celda, a efectos de detectar la matriz vuelta × piloto. */
sealed interface CellKind {
    data class Time(val match: TimeMatch) : CellKind
    data class OutOfRange(val match: TimeMatch) : CellKind
    data class LapLabel(val number: Int) : CellKind
    data object AvgLabel : CellKind
    data object Empty : CellKind
    data object Text : CellKind
}

/** Texto ya troceado en líneas y celdas, que es lo que consumen los perfiles. */
class PreparedText(val lines: List<String>, val cells: List<Cell>) {
    private val kinds = cells.map { classify(it.text) }
    fun kind(index: Int): CellKind = kinds[index]

    companion object {
        private val lapLabel = Regex("^(?:(?:v|vuelta|lap|l)\\.?\\s*)?(\\d{1,3})\\s*[).:\\-–]*$", RegexOption.IGNORE_CASE)
        private val avgLabel = Regex("^(?:avg|average|media|promedio|prom)\\.?:?$", RegexOption.IGNORE_CASE)
        private val blankCell = Regex("^[-–—]{0,3}$")

        fun classify(text: String): CellKind {
            val t = text.trim()
            if (blankCell.matches(t)) return CellKind.Empty
            TimeTokens.parseExact(t)?.let { return if (it.inRange) CellKind.Time(it) else CellKind.OutOfRange(it) }
            if (avgLabel.matches(t)) return CellKind.AvgLabel
            lapLabel.matchEntire(t)?.let { m -> m.groupValues[1].toIntOrNull()?.let { return CellKind.LapLabel(it) } }
            return CellKind.Text
        }

        /**
         * Trocea el texto: líneas con tabuladores → una celda por pieza (se conservan las vacías);
         * líneas con tiempos → texto previo, cada tiempo y texto posterior; el resto → piezas separadas por 2+ espacios.
         * Las líneas de [skipLines] (p. ej. la tabla resumen ya consumida) no generan celdas.
         */
        fun from(text: String, skipLines: Set<Int> = emptySet()): PreparedText {
            val lines = text.replace(" ", " ").replace("\r\n", "\n").replace('\r', '\n').split('\n')
            val cells = mutableListOf<Cell>()
            lines.forEachIndexed { index, raw ->
                if (index in skipLines || raw.isBlank()) return@forEachIndexed
                if (raw.contains('\t')) {
                    val pieces = raw.split('\t').map { it.trim() }.dropLastWhile { it.isEmpty() }
                    pieces.forEach { cells += Cell(it, index, fromTabs = true) }
                    return@forEachIndexed
                }
                val line = raw.trim()
                val times = TimeTokens.find(line)
                if (times.isEmpty()) {
                    line.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
                        .forEach { cells += Cell(it, index, fromTabs = false) }
                } else {
                    val prefix = line.substring(0, times.first().start).trim().trimEnd('-', '–', '|', ':').trim()
                    if (prefix.isNotEmpty()) cells += Cell(prefix, index, fromTabs = false)
                    times.forEach { cells += Cell(it.raw, index, fromTabs = false) }
                    val suffix = line.substring(times.last().end).trim()
                    if (suffix.isNotEmpty()) cells += Cell(suffix, index, fromTabs = false)
                }
            }
            return PreparedText(lines, cells)
        }
    }
}
