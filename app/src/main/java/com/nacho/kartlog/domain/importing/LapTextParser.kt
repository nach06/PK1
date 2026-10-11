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
    private val profiles = extraProfiles + RowPerDriverProfile + GenericMatrixProfile

    /**
     * @param forced formato elegido a mano cuando la detección automática falla; `null` = detectarlo.
     */
    fun parse(text: String, forced: ImportFormat? = null): ParsedDocument {
        if (text.isBlank()) return ParsedDocument(warnings = listOf("El texto está vacío"))
        val warnings = mutableListOf<String>()

        // 1) La tabla resumen se extrae primero para que sus celdas no se confundan con la matriz.
        val allLines = text.replace(" ", " ").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val summary = SummaryTable.extract(allLines)
        val prepared = PreparedText.from(text, skipLines = summary.consumedLines)

        // 2) Matriz (un perfil por orden; el genérico, el último). Con formato forzado solo se prueba el perfil de ese formato.
        val candidateProfiles = when (forced) {
            ImportFormat.MATRIX -> listOf(GenericMatrixProfile)
            ImportFormat.ROW_PER_DRIVER -> listOf(RowPerDriverProfile)
            ImportFormat.SINGLE_COLUMN, ImportFormat.SUMMARY -> emptyList()
            null -> profiles
        }
        var usedProfile: LapTextProfile? = null
        var blocks: List<MatrixBlock> = emptyList()
        for (profile in candidateProfiles) {
            val found = profile.detectBlocks(prepared)?.takeIf { it.isNotEmpty() } ?: continue
            usedProfile = profile
            blocks = found
            break
        }
        val detectedByRowProfile = usedProfile === RowPerDriverProfile
        if (forced == null || forced == ImportFormat.MATRIX) {
            if (blocks.any { it.header.size >= 2 }) {
                val dropped = blocks.count { it.header.size < 2 }
                if (dropped > 0) warnings += "Se ignoraron $dropped bloque(s) de una sola columna (posibles clasificaciones)"
                blocks = blocks.filter { it.header.size >= 2 }
            }
        }

        // 3) Lista de tiempos sin etiquetas de vuelta: un único piloto, vueltas numeradas por orden.
        val bare = blocks.isEmpty() && (forced == null || forced == ImportFormat.SINGLE_COLUMN)
        val drivers = if (blocks.isNotEmpty()) {
            mergeBlocks(blocks, prepared)
        } else if (!bare) {
            emptyList() // formato forzado que no encuentra nada: no se inventa otro
        } else {
            bareTimes(prepared)?.also { warnings += "Sin números de vuelta: se numeraron por orden" }.orEmpty()
        }
        val format = when {
            drivers.isEmpty() && summary.rows.isEmpty() -> null
            drivers.none { it.laps.isNotEmpty() || it.invalidCells.isNotEmpty() } -> if (summary.rows.isNotEmpty()) ImportFormat.SUMMARY else null
            detectedByRowProfile -> ImportFormat.ROW_PER_DRIVER
            bare -> ImportFormat.SINGLE_COLUMN
            blocks.all { it.header.size < 2 } -> ImportFormat.SINGLE_COLUMN
            else -> ImportFormat.MATRIX
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
            format = format,
        )
    }

    private fun PreparedText.kindOf(cell: Cell): CellKind = kind(cells.indexOf(cell))

    /** Fusiona los bloques por piloto (mismo nombre normalizado, o mismo kart si no hay nombre). */
    private fun mergeBlocks(blocks: List<MatrixBlock>, prepared: PreparedText): List<ParsedDriver> {
        class Acc(
            var name: String?, var kart: Int?, val laps: MutableMap<Int, ParsedLap>, val invalid: MutableMap<Int, ParsedLap>,
            var avg: Long?, val column: Int,
        )

        val accs = mutableListOf<Acc>()
        var columnCounter = 0
        for (block in blocks) {
            block.header.forEachIndexed { col, header ->
                val key = header.name?.let(::normalizeKey)?.takeIf { it.isNotEmpty() }
                val existing = accs.firstOrNull {
                    (key != null && it.name?.let(::normalizeKey) == key) ||
                        (key == null && header.kartNumber != null && it.name == null && it.kart == header.kartNumber)
                }
                val acc = existing ?: Acc(header.name, header.kartNumber, mutableMapOf(), mutableMapOf(), null, columnCounter).also { accs += it }
                if (existing != null && acc.kart == null) acc.kart = header.kartNumber
                columnCounter += if (existing == null) 1 else 0
                for (row in block.laps) {
                    val number = row.number ?: continue
                    val cell = row.times.getOrNull(col) ?: continue
                    val source = row.timeCells.getOrNull(col)?.takeIf { it >= 0 }?.let { prepared.cells.getOrNull(it) }
                    val lap = ParsedLap(
                        number = number, timeMs = cell.timeMs, ocrFixed = cell.ocrFixed, rawText = cell.raw, invalid = !cell.inRange,
                        cellLine = source?.line ?: -1, cellCol = source?.col ?: -1,
                    )
                    if (cell.inRange) acc.laps[number] = lap else acc.invalid[number] = lap
                }
                block.average?.times?.getOrNull(col)?.takeIf { it.inRange }?.let { acc.avg = it.timeMs }
            }
        }
        return accs.map {
            ParsedDriver(
                name = it.name,
                kartNumber = it.kart,
                laps = it.laps.values.sortedBy { lap -> lap.number },
                reportedAvgMs = it.avg,
                columnIndex = it.column,
                invalidCells = it.invalid.filterKeys { n -> n !in it.laps }.values.sortedBy { lap -> lap.number },
            )
        }
    }

    private fun bareTimes(prepared: PreparedText): List<ParsedDriver>? {
        val times = prepared.cells.indices.mapNotNull { (prepared.kind(it) as? CellKind.Time)?.match }
        if (times.size < 2) return null
        return listOf(
            ParsedDriver(null, null, laps = times.mapIndexed { i, t -> ParsedLap(i + 1, t.timeMs, t.ocrFixed, rawText = t.raw) }),
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
