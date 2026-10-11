package com.nacho.kartlog.domain.importing

/**
 * Perfil «una fila por piloto»: cada línea trae el nombre del piloto y, a continuación, sus vueltas en columnas
 * (`Nacho  1:17.263  1:16.905  1:18.020 …`, con posición o kart opcionales delante). Se traspone a una matriz
 * (cabecera = pilotos, filas = vueltas) para que el resto del analizador no cambie.
 *
 * Una fila de piloto es una línea cuya **celda inmediatamente anterior al primer tiempo** es un texto con letras (el nombre)
 * y que trae al menos [MIN_LAPS] tiempos. En la matriz vuelta × piloto esa celda es el número de vuelta, así que las dos
 * formas no se confunden. Hacen falta al menos dos pilotos.
 */
object RowPerDriverProfile : LapTextProfile {
    override val name = "una fila por piloto"
    const val MIN_LAPS = 3

    private val kartOnly = Regex("^(?:kart|k)\\s*#?\\s*(\\d{1,3})$", RegexOption.IGNORE_CASE)
    private val nameKart = Regex("^(.+?)[\\s,·\\-–(\\[]+kart\\s*#?\\s*(\\d{1,3})[)\\]]?$", RegexOption.IGNORE_CASE)

    private class DriverRow(val header: HeaderColumn, val times: List<TimeMatch?>, val cells: List<Int>, val firstCell: Int, val lastCell: Int)

    override fun detectBlocks(text: PreparedText): List<MatrixBlock>? {
        val cells = text.cells
        val rows = mutableListOf<DriverRow>()
        var i = 0
        while (i < cells.size) {
            val line = cells[i].line
            var end = i
            while (end < cells.size && cells[end].line == line) end++
            parseLine(text, i, end)?.let { rows += it }
            i = end
        }
        if (rows.size < 2) return null

        val laps = rows.maxOf { it.times.size }
        val matrixRows = (0 until laps).map { lap ->
            MatrixRow(
                isAverage = false,
                number = lap + 1,
                times = rows.map { it.times.getOrNull(lap) },
                timeCells = rows.map { it.cells.getOrNull(lap) ?: -1 },
                firstCell = rows.first().firstCell,
                lastCell = rows.last().lastCell,
            )
        }
        return listOf(
            MatrixBlock(
                header = rows.map { it.header },
                laps = matrixRows,
                average = null,
                firstCell = rows.first().firstCell,
                lastCell = rows.last().lastCell,
            ),
        )
    }

    private fun parseLine(text: PreparedText, from: Int, to: Int): DriverRow? {
        val kinds = (from until to).map { text.kind(it) }
        val firstTime = kinds.indexOfFirst { it is CellKind.Time || it is CellKind.OutOfRange }
        if (firstTime < 1) return null
        // Hacia atrás desde el primer tiempo: «Kart NN» → kart, el primer texto con letras → nombre; enteros sueltos (posición) se saltan.
        var name: String? = null
        var kart: Int? = null
        var cursor = from + firstTime - 1
        while (cursor >= from) {
            val t = text.cells[cursor].text.trim()
            val kind = text.kind(cursor)
            if (kind == CellKind.Empty) { cursor--; continue }
            val kartMatch = kartOnly.matchEntire(t)
            val nameMatch = nameKart.matchEntire(t)
            when {
                kartMatch != null -> kart = kart ?: kartMatch.groupValues[1].toInt()
                nameMatch != null && name == null -> { name = nameMatch.groupValues[1].trim(); kart = kart ?: nameMatch.groupValues[2].toInt() }
                kind == CellKind.Text && name == null && t.count { it.isLetter() } >= 2 -> name = t
                else -> break
            }
            cursor--
        }
        name = name?.trimEnd(',', '-', '–', '·')?.trim()
        if (name.isNullOrEmpty()) return null
        val firstNameCell = (cursor + 1).coerceAtLeast(from)

        // Tiempos consecutivos (con celdas vacías intercaladas solo en líneas con tabuladores).
        val times = mutableListOf<TimeMatch?>()
        val timeCells = mutableListOf<Int>()
        var j = from + firstTime
        while (j < to) {
            when (val k = text.kind(j)) {
                is CellKind.Time -> { times += k.match; timeCells += j }
                is CellKind.OutOfRange -> { times += k.match; timeCells += j }
                CellKind.Empty -> if (text.cells[j].fromTabs) { times += null; timeCells += -1 } else break
                else -> break
            }
            j++
        }
        while (times.isNotEmpty() && times.last() == null) { times.removeLast(); timeCells.removeLast() }
        if (times.count { it != null } < MIN_LAPS) return null

        return DriverRow(HeaderColumn(name, kart), times, timeCells, firstNameCell, j - 1)
    }
}
