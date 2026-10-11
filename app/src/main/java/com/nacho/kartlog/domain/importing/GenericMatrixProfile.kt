package com.nacho.kartlog.domain.importing

/**
 * Perfil genérico: matriz «vuelta × piloto» (filas = vueltas, columnas = pilotos) con fila `Avg.` final,
 * con una o varias tablas, y también la lista simple de un solo piloto (matriz de una columna).
 */
object GenericMatrixProfile : LapTextProfile {
    override val name = "genérico"

    private val noise = Regex(
        "resultado|detallad|resumen|vuelta|^lap|^pilotos?$|^pos\\.?$|^v\\.?$|carrera|sesi[oó]n|tanda|min\\.?$|^gap$|mejor|^kart$|^en v|clasificaci",
        RegexOption.IGNORE_CASE,
    )
    private val dateLike = Regex("\\d{1,2}/\\d{1,2}/\\d{2,4}")
    private val kartOnly = Regex("^(?:kart|k)\\s*#?\\s*(\\d{1,3})$", RegexOption.IGNORE_CASE)
    private val nameKart = Regex("^(.+?)[\\s,·\\-–(\\[]+kart\\s*#?\\s*(\\d{1,3})[)\\]]?$", RegexOption.IGNORE_CASE)
    private val bareInt = Regex("^\\d{1,3}$")

    override fun detectBlocks(text: PreparedText): List<MatrixBlock>? {
        val rows = scanRows(text)
        if (rows.none { !it.isAverage }) return null

        // Agrupa filas consecutivas en bloques: la fila de medias cierra el bloque; una vuelta que no
        // avanza (vuelve a 1) abre otro.
        val grouped = mutableListOf<MutableList<MatrixRow>>()
        var current = mutableListOf<MatrixRow>()
        for (row in rows) {
            val last = current.lastOrNull()
            val startsNew = last != null && (
                last.isAverage || (!row.isAverage && (row.number ?: 0) <= (last.number ?: 0))
                )
            if (startsNew) {
                grouped += current
                current = mutableListOf()
            }
            current += row
        }
        if (current.isNotEmpty()) grouped += current

        val blocks = mutableListOf<MatrixBlock>()
        var previousEnd = -1
        for (group in grouped) {
            val lapRows = group.filter { !it.isAverage }
            if (lapRows.isEmpty()) continue
            val average = group.firstOrNull { it.isAverage }
            val columns = lapRows.maxOf { it.times.size }.coerceAtLeast(average?.times?.size ?: 0)
            val firstCell = group.first().firstCell
            val headerTokens = headerTokensBefore(text, firstCell, previousEnd)
            blocks += MatrixBlock(
                header = groupHeader(headerTokens, columns),
                laps = lapRows,
                average = average,
                firstCell = firstCell,
                lastCell = group.last().lastCell,
            )
            previousEnd = group.last().lastCell
        }
        return blocks
    }

    /** Recorre el flujo de celdas buscando «etiqueta + tiempos». */
    private fun scanRows(text: PreparedText): List<MatrixRow> {
        val rows = mutableListOf<MatrixRow>()
        val cells = text.cells
        var i = 0
        while (i < cells.size) {
            val kind = text.kind(i)
            val isLabel = kind is CellKind.LapLabel
            val isAvg = kind == CellKind.AvgLabel
            if (!isLabel && !isAvg) {
                i++
                continue
            }
            val times = mutableListOf<TimeMatch?>()
            val timeCells = mutableListOf<Int>()
            var j = i + 1
            while (j < cells.size) {
                when (val k = text.kind(j)) {
                    is CellKind.Time -> { times += k.match; timeCells += j }
                    // Fuera de rango (5 s, 9 min…): se conserva como celda inválida (roja en la revisión), no se descarta en silencio.
                    is CellKind.OutOfRange -> { times += k.match; timeCells += j }
                    CellKind.Empty ->
                        // Las celdas vacías solo cuentan dentro de una fila con tabuladores.
                        if (cells[j].fromTabs && cells[j].line == cells[i].line) { times += null; timeCells += -1 } else break
                    else -> break
                }
                j++
            }
            if (times.any { it != null }) {
                rows += MatrixRow(
                    isAverage = isAvg,
                    number = (kind as? CellKind.LapLabel)?.number,
                    times = times.toList(),
                    timeCells = timeCells.toList(),
                    firstCell = i,
                    lastCell = j - 1,
                )
                i = j
            } else {
                i++
            }
        }
        return rows
    }

    /**
     * Textos de cabecera de un bloque: se recorre **hacia atrás** desde su primera fila. Se saltan las
     * celdas vacías y la basura (nombres de fichero, separadores `---`), y se para en la primera pista de
     * que ya no es cabecera (título, fecha, «Vuelta», «Resultados»…).
     */
    private fun headerTokensBefore(text: PreparedText, firstCell: Int, previousEnd: Int): List<String> {
        val tokens = ArrayDeque<String>()
        var index = firstCell - 1
        while (index > previousEnd) {
            val kind = text.kind(index)
            val token = text.cells[index].text.trim()
            if (kind == CellKind.Text || kind is CellKind.LapLabel) {
                when {
                    kartOnly.matches(token) || nameKart.matches(token) || bareInt.matches(token) -> tokens.addFirst(token)
                    isJunk(token) -> Unit
                    dateLike.containsMatchIn(token) || noise.containsMatchIn(token) -> break
                    else -> tokens.addFirst(token)
                }
            } else if (kind != CellKind.Empty) {
                break
            }
            index--
        }
        return tokens.toList()
    }

    private val junk = Regex("^[-=_*#~|.\\s]{2,}.*|.*[-=_*#~|]{3,}$|.*\\.(png|jpe?g|gif|svg|pdf|html?)$", RegexOption.IGNORE_CASE)

    private fun isJunk(token: String): Boolean = token.isBlank() || junk.matches(token) || token.none { it.isLetterOrDigit() }

    /**
     * Agrupa los textos de cabecera en [columns] columnas: `Kart NN` se pega al nombre anterior, un
     * fragmento suelto («l» tras «Alvarommk») se une a él, y una cabecera de solo números se lee como karts.
     * Si hay de más se descartan los primeros; si faltan, se completan con columnas sin nombre.
     */
    internal fun groupHeader(tokens: List<String>, columns: Int): List<HeaderColumn> {
        data class Mutable(var name: String?, var kart: Int?)

        val onlyNumbers = tokens.isNotEmpty() && tokens.all { bareInt.matches(it.trim()) }

        // Cabecera en dos filas: primero todos los nombres y después todos los «Kart NN» (mismo número en cada una).
        val kartTokens = tokens.mapIndexedNotNull { i, t -> kartOnly.matchEntire(t.trim())?.let { i to it.groupValues[1].toInt() } }
        val nameTokens = tokens.filter { kartOnly.matchEntire(it.trim()) == null }
        if (!onlyNumbers && kartTokens.isNotEmpty() && kartTokens.size == nameTokens.size &&
            kartTokens.first().first >= nameTokens.size
        ) {
            val zipped = nameTokens.mapIndexed { i, n ->
                val withKart = nameKart.matchEntire(n.trim())
                HeaderColumn(withKart?.groupValues?.get(1)?.trim() ?: n.trim(), kartTokens[i].second)
            }
            return zipped.takeLast(columns).let { it + List((columns - it.size).coerceAtLeast(0)) { HeaderColumn(null, null) } }
        }

        val groups = mutableListOf<Mutable>()
        for (raw in tokens) {
            val t = raw.trim()
            val kartOnlyValue = kartOnly.matchEntire(t)?.groupValues?.get(1)?.toInt()
                ?: if (onlyNumbers) t.toInt() else null
            if (kartOnlyValue != null) {
                val last = groups.lastOrNull()
                if (!onlyNumbers && last != null && last.kart == null) last.kart = kartOnlyValue else groups += Mutable(null, kartOnlyValue)
                continue
            }
            val last = groups.lastOrNull()
            // Fragmento de un nombre largo partido en dos líneas («Cortamargari» + «tas»): si ya hay un nombre sin
            // kart y la cabecera trae «Kart NN», un texto más antes del kart es la continuación.
            val isFragment = last?.name != null && last.kart == null && !nameKart.matches(t) &&
                (t.length <= 2 || kartTokens.isNotEmpty()) && t.all { it.isLetter() || it == '.' }
            if (isFragment && last != null) {
                last.name = last.name + " " + t
                continue
            }
            val withKart = nameKart.matchEntire(t)
            if (withKart != null) groups += Mutable(withKart.groupValues[1].trim(), withKart.groupValues[2].toInt()) else groups += Mutable(t, null)
        }
        val result = groups.map { HeaderColumn(it.name, it.kart) }
        return when {
            result.size == columns -> result
            result.size > columns -> result.takeLast(columns)
            else -> result + List(columns - result.size) { HeaderColumn(null, null) }
        }
    }
}
