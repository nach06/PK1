package com.nacho.kartlog.domain.importing

/**
 * Analiza el texto de un resultado de tanda (correo, hoja impresa, salida de OCR…) y devuelve los
 * pilotos con sus vueltas, los datos de control y los metadatos de la sesión. Es Kotlin puro.
 *
 * - **Matriz vuelta × piloto** con una o varias tablas (se fusionan por piloto) y fila final `Avg.`.
 * - **Lista simple** de un piloto (`1  1:12.467`, `Vuelta 3 - 1:12.467`, `3) 1:12.467 (+0.321)`).
 * - **Tabla resumen** `Pos, Piloto, Kart, V., Mejor V., en V., GAP` (posición, kart y mejor de cada piloto).
 * - Perfiles extensibles: [LapTextProfile]; el genérico va siempre el último.
 *
 * @param knownCircuits nombres de los circuitos existentes, para detectar el circuito por coincidencia.
 */
class LapTextParser(
    private val extraProfiles: List<LapTextProfile> = emptyList(),
    private val knownCircuits: List<String> = emptyList(),
) {
    private val profiles = extraProfiles + GenericMatrixProfile

    fun parse(text: String): ParsedDocument {
        if (text.isBlank()) return ParsedDocument(warnings = listOf("El texto está vacío"))
        val warnings = mutableListOf<String>()

        // 1) La tabla resumen se extrae primero para que sus celdas no se confundan con la matriz.
        val allLines = text.replace(" ", " ").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val summary = SummaryTable.extract(allLines)
        val prepared = PreparedText.from(text, skipLines = summary.consumedLines)

        // 2) Matriz (un perfil por orden; el genérico, el último).
        var blocks = profiles.firstNotNullOfOrNull { it.detectBlocks(prepared)?.takeIf { b -> b.isNotEmpty() } }.orEmpty()
        if (blocks.any { it.header.size >= 2 }) {
            val dropped = blocks.count { it.header.size < 2 }
            if (dropped > 0) warnings += "Se ignoraron $dropped bloque(s) de una sola columna (posibles clasificaciones)"
            blocks = blocks.filter { it.header.size >= 2 }
        }

        // 3) Lista de tiempos sin etiquetas de vuelta: un único piloto, vueltas numeradas por orden.
        val drivers = if (blocks.isNotEmpty()) {
            mergeBlocks(blocks)
        } else {
            bareTimes(prepared)?.also { warnings += "Sin números de vuelta: se numeraron por orden" }.orEmpty()
        }

        // 4) Datos de control de la tabla resumen.
        val withSummary = applySummary(drivers, summary.rows)

        // 5) Metadatos: solo en las líneas previas a los datos (así «Clasificación» de una tabla posterior no cuenta).
        val firstDataLine = (blocks.firstOrNull()?.firstCell?.let { prepared.cells[it].line }
            ?: prepared.cells.firstOrNull { prepared.kindOf(it) is CellKind.Time }?.line
            ?: summary.consumedLines.minOrNull()
            ?: allLines.size)
            .coerceAtMost(allLines.size)
        val headerLines = allLines.take(firstDataLine).filterIndexed { index, _ -> index !in summary.consumedLines }
        val meta = MetadataExtractor.extract(headerLines.ifEmpty { allLines.take(12) }, knownCircuits)

        val ignored = prepared.cells.indices.mapNotNull { (prepared.kind(it) as? CellKind.OutOfRange)?.match?.raw }.distinct()
        if (withSummary.none { it.laps.isNotEmpty() }) warnings += "No se encontraron vueltas en el texto"

        return ParsedDocument(
            label = meta.label,
            dateTime = meta.dateTime,
            durationMin = meta.durationMin,
            type = meta.type,
            circuitName = meta.circuitName,
            drivers = withSummary,
            ignoredTimes = ignored,
            warnings = warnings,
        )
    }

    private fun PreparedText.kindOf(cell: Cell): CellKind = kind(cells.indexOf(cell))

    /** Fusiona los bloques por piloto (mismo nombre normalizado, o mismo kart si no hay nombre). */
    private fun mergeBlocks(blocks: List<MatrixBlock>): List<ParsedDriver> {
        class Acc(var name: String?, var kart: Int?, val laps: MutableMap<Int, ParsedLap>, var avg: Long?, val column: Int)

        val accs = mutableListOf<Acc>()
        var columnCounter = 0
        for (block in blocks) {
            block.header.forEachIndexed { col, header ->
                val key = header.name?.let(::normalizeKey)?.takeIf { it.isNotEmpty() }
                val existing = accs.firstOrNull {
                    (key != null && it.name?.let(::normalizeKey) == key) ||
                        (key == null && header.kartNumber != null && it.name == null && it.kart == header.kartNumber)
                }
                val acc = existing ?: Acc(header.name, header.kartNumber, mutableMapOf(), null, columnCounter).also { accs += it }
                if (existing != null && acc.kart == null) acc.kart = header.kartNumber
                columnCounter += if (existing == null) 1 else 0
                for (row in block.laps) {
                    val number = row.number ?: continue
                    val cell = row.times.getOrNull(col) ?: continue
                    acc.laps[number] = ParsedLap(number, cell.timeMs, cell.ocrFixed)
                }
                block.average?.times?.getOrNull(col)?.let { acc.avg = it.timeMs }
            }
        }
        return accs.map {
            ParsedDriver(
                name = it.name,
                kartNumber = it.kart,
                laps = it.laps.values.sortedBy { lap -> lap.number },
                reportedAvgMs = it.avg,
                columnIndex = it.column,
            )
        }
    }

    private fun bareTimes(prepared: PreparedText): List<ParsedDriver>? {
        val times = prepared.cells.indices.mapNotNull { (prepared.kind(it) as? CellKind.Time)?.match }
        if (times.size < 2) return null
        return listOf(
            ParsedDriver(null, null, laps = times.mapIndexed { i, t -> ParsedLap(i + 1, t.timeMs, t.ocrFixed) }),
        )
    }

    /** Une cada fila del resumen con su piloto (por nombre y, si no, por kart); los que no estén en la matriz se añaden sin vueltas. */
    private fun applySummary(drivers: List<ParsedDriver>, rows: List<SummaryRow>): List<ParsedDriver> {
        if (rows.isEmpty()) return drivers
        val used = mutableSetOf<SummaryRow>()
        val merged = drivers.map { driver ->
            val byName = driver.name?.let(::normalizeKey)?.takeIf { it.isNotEmpty() }
                ?.let { key -> rows.firstOrNull { it !in used && normalizeKey(it.name) == key } }
            val match = byName ?: driver.kartNumber?.let { kart ->
                rows.firstOrNull {
                    it !in used && it.kartNumber == kart &&
                        (driver.name == null || it.name.isBlank() || normalizeKey(it.name) == normalizeKey(driver.name))
                }
            }
            if (match == null) {
                driver
            } else {
                used += match
                driver.copy(
                    // El nombre de la tabla resumen es el completo; en la matriz puede venir partido en dos líneas.
                    name = match.name.ifBlank { null } ?: driver.name,
                    kartNumber = driver.kartNumber ?: match.kartNumber,
                    position = match.position,
                    reportedBestMs = match.bestMs,
                    reportedLapCount = match.lapCount,
                )
            }
        }
        val extras = rows.filter { it !in used }.mapIndexed { i, row ->
            ParsedDriver(
                name = row.name.ifBlank { null },
                kartNumber = row.kartNumber,
                position = row.position,
                reportedBestMs = row.bestMs,
                reportedLapCount = row.lapCount,
                columnIndex = drivers.size + i,
            )
        }
        return (merged + extras).sortedWith(compareBy({ it.position ?: Int.MAX_VALUE }, { it.columnIndex }))
    }
}
