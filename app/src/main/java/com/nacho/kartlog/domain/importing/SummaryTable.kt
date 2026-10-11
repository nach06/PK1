package com.nacho.kartlog.domain.importing

/** Fila de la tabla resumen: `Pos, Piloto, Kart, V., Mejor V., en V., GAP`. */
data class SummaryRow(
    val position: Int,
    val name: String,
    val kartNumber: Int?,
    val lapCount: Int?,
    val bestMs: Long,
    /** Vuelta en la que se hizo la mejor (`en V.`). */
    val bestLapNumber: Int?,
    val gap: String?,
)

/** Extrae la tabla resumen de un texto y dice qué líneas ha consumido (para que no se confundan con la matriz). */
internal object SummaryTable {
    private val headerWords = setOf(
        "p", "pos", "pos.", "piloto", "pilotos", "driver", "kart", "v", "v.", "vueltas", "laps",
        "mejor", "mejor v", "mejor v.", "mejor vuelta", "best", "en v", "en v.", "gap", "dif", "dif.", "tiempo",
    )
    private val int = Regex("^\\d{1,3}$")

    private var hasKartOnlyHeader = false

    class Result(val rows: List<SummaryRow>, val consumedLines: Set<Int>)

    fun extract(lines: List<String>): Result {
        val consumed = mutableSetOf<Int>()
        val rows = mutableListOf<SummaryRow>()
        hasKartOnlyHeader = lines.any { it.isNotBlank() && isHeaderLine(it) }

        // 1) Filas completas en una sola línea (tabuladores o espacios).
        lines.forEachIndexed { index, raw ->
            if (raw.isBlank()) return@forEachIndexed
            if (isHeaderLine(raw)) {
                consumed += index
                return@forEachIndexed
            }
            parseRowLine(raw)?.let {
                rows += it
                consumed += index
            }
        }
        // 2) Tabla con una celda por línea: cabecera de palabras y grupos de K líneas.
        if (rows.isEmpty()) {
            val stream = extractOneCellPerLine(lines)
            rows += stream.rows
            consumed += stream.consumedLines
        }
        return Result(rows, consumed)
    }

    private fun isHeaderLine(raw: String): Boolean {
        val tokens = raw.split('\t', '|').flatMap { it.split(Regex("\\s{2,}")) }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (tokens.size < 3 || !tokens.all { it in headerWords }) return false
        // Con nombre (`Piloto`) o solo por kart (`P Kart V. Mejor`).
        return "piloto" in tokens.map { it.removeSuffix(".") } ||
            ("kart" in tokens && tokens.any { it.startsWith("mejor") || it == "best" })
    }

    private fun parseRowLine(raw: String): SummaryRow? {
        val line = raw.trim()
        val first = TimeTokens.find(line).firstOrNull { it.inRange } ?: return null
        val prefix = line.substring(0, first.start).trim().split(Regex("[\\t ]+")).filter { it.isNotEmpty() }
        if (prefix.size < 3) return null
        val position = prefix.first().takeIf(int::matches)?.toInt() ?: return null
        val laps = prefix.last().takeIf(int::matches)?.toInt() ?: return null
        val kart = prefix[prefix.size - 2].takeIf(int::matches)?.toInt() ?: return null
        // Sin columna de piloto (hojas por kart): `Pos Kart V. Mejor` → nombre vacío.
        val name = prefix.subList(1, prefix.size - 2).joinToString(" ").trim()
        if (prefix.size == 3) {
            // Tres enteros seguidos de un tiempo: solo se acepta si hay una cabecera de resumen en el texto.
            if (!hasKartOnlyHeader) return null
        } else if (name.isEmpty() || !name.any { it.isLetter() }) {
            return null
        }
        val suffix = line.substring(first.end).trim().split(Regex("[\\t ]+")).filter { it.isNotEmpty() }
        val bestLap = suffix.firstOrNull()?.takeIf(int::matches)?.toInt()
        val gap = suffix.drop(if (bestLap != null) 1 else 0).joinToString(" ").ifEmpty { null }
        return SummaryRow(position, name, kart, laps, first.timeMs, bestLap, gap)
    }

    private fun extractOneCellPerLine(lines: List<String>): Result {
        val consumed = mutableSetOf<Int>()
        val rows = mutableListOf<SummaryRow>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].trim().lowercase().removeSuffix(".") != "piloto" && lines[i].trim().lowercase() != "pilotos") {
                i++
                continue
            }
            // Cabecera: líneas consecutivas (no vacías) formadas solo por palabras de cabecera.
            var start = i
            while (start > 0 && lines[start - 1].isNotBlank() && lines[start - 1].trim().lowercase() in headerWords) start--
            var end = i
            while (end + 1 < lines.size && lines[end + 1].isNotBlank() && lines[end + 1].trim().lowercase() in headerWords) end++
            val header = (start..end).map { lines[it].trim().lowercase() }
            if (header.size < 4) {
                i = end + 1
                continue
            }
            val k = header.size
            val posIdx = header.indexOfFirst { it.startsWith("pos") }.takeIf { it >= 0 } ?: 0
            val nameIdx = header.indexOfFirst { it.startsWith("pilot") || it == "driver" }
            val kartIdx = header.indexOfFirst { it == "kart" }
            val lapsIdx = header.indexOfFirst { it == "v" || it == "v." || it == "vueltas" || it == "laps" }
            val bestIdx = header.indexOfFirst { it.startsWith("mejor") || it == "best" }
            val bestLapIdx = header.indexOfFirst { it.startsWith("en v") }
            val gapIdx = header.indexOfFirst { it == "gap" || it.startsWith("dif") }
            if (nameIdx < 0 || bestIdx < 0) {
                i = end + 1
                continue
            }
            consumed += (start..end)
            var cursor = end + 1
            while (true) {
                val group = mutableListOf<Int>()
                var c = cursor
                while (group.size < k && c < lines.size) {
                    if (lines[c].isNotBlank()) group += c
                    c++
                }
                if (group.size < k) break
                fun cell(idx: Int) = if (idx >= 0) lines[group[idx]].trim() else ""
                val position = cell(posIdx).takeIf(int::matches)?.toInt() ?: break
                val best = TimeTokens.parseExact(cell(bestIdx))?.takeIf { it.inRange } ?: break
                rows += SummaryRow(
                    position = position,
                    name = cell(nameIdx),
                    kartNumber = cell(kartIdx).takeIf(int::matches)?.toInt(),
                    lapCount = cell(lapsIdx).takeIf(int::matches)?.toInt(),
                    bestMs = best.timeMs,
                    bestLapNumber = cell(bestLapIdx).takeIf(int::matches)?.toInt(),
                    gap = cell(gapIdx).ifEmpty { null },
                )
                consumed += group
                cursor = c
            }
            i = cursor.coerceAtLeast(end + 1)
        }
        return Result(rows, consumed)
    }
}
