package com.nacho.kartlog.domain.importing

/** Estado de una celda de tiempo en la pantalla de revisión. */
enum class CellStatus {
    /** Leída con confianza (o confirmada/editada por el usuario). */
    OK,

    /** Ámbar: baja confianza del OCR, corregida por el OCR o señalada por la validación cruzada. Se guarda, pero conviene mirarla. */
    DOUBTFUL,

    /** Rojo: no parseable o fuera de 15 s – 5 min. No se guarda. */
    INVALID,

    /** Gris: sin dato (hueco entre vueltas, vuelta borrada). */
    EMPTY,
}

/** Una celda editable de la tabla de revisión. */
data class ReviewCell(
    val number: Int,
    /** `null` = sin valor (vacía, o ilegible: ver [rawText]). */
    val timeMs: Long?,
    /** Texto original del OCR, para enseñarlo en las celdas dudosas o inválidas. */
    val rawText: String? = null,
    /** El OCR corrigió letras/separadores dentro del tiempo. */
    val ocrFixed: Boolean = false,
    /** La caja del OCR tenía poca confianza. */
    val lowConfidence: Boolean = false,
    /** El usuario cambió el valor a mano. */
    val edited: Boolean = false,
    /** El usuario marcó la celda como correcta. */
    val confirmed: Boolean = false,
    /** El usuario quitó la vuelta. */
    val deleted: Boolean = false,
    /** De dónde sale en el texto reconstruido (para localizar su caja en la foto); `-1` si no se conoce. */
    val line: Int = -1,
    val col: Int = -1,
    /** Ilegible en origen (sin valor) aunque haya texto. */
    val unreadable: Boolean = false,
)

/** Un piloto de la hoja con sus celdas editables y los datos de control que trajera el documento. */
data class ReviewDriver(
    val key: String,
    val name: String?,
    val kartNumber: Int?,
    val position: Int?,
    val reportedBestMs: Long?,
    val reportedLapCount: Int?,
    val reportedAvgMs: Long?,
    val cells: List<ReviewCell>,
) {
    val displayName: String
        get() = when {
            name != null && kartNumber != null -> "$name · Kart $kartNumber"
            name != null -> name
            kartNumber != null -> "Kart $kartNumber"
            else -> "Piloto"
        }
}

/** Resumen previo al guardado. */
data class ReviewSummary(
    val driverCount: Int,
    val lapCount: Int,
    /** Celdas ámbar que no se han revisado. */
    val doubtful: Int,
    /** Celdas rojas: no se guardan. */
    val invalid: Int,
) {
    val needsConfirmation: Boolean get() = doubtful > 0 || invalid > 0
}

/**
 * Documento en revisión: todas las celdas de todos los pilotos, editables, y quién eres tú. Es inmutable; cada operación
 * devuelve una copia. La lógica de qué se guarda vive aquí (no en la pantalla) para poder probarla.
 */
data class ImportReview(
    val drivers: List<ReviewDriver>,
    val meKey: String? = null,
    /** Propuestas de corrección que el usuario descartó con «Ignorar» (`piloto|vuelta`). */
    val ignoredSuggestions: Set<String> = emptySet(),
) {
    val me: ReviewDriver? get() = drivers.firstOrNull { it.key == meKey }
    fun driver(key: String?): ReviewDriver? = drivers.firstOrNull { it.key == key }

    // ---- Estado de las celdas ----

    /** Vueltas del piloto que cuentan para la validación: con valor en rango y no borradas. */
    private fun countedLaps(driver: ReviewDriver): List<Pair<Int, Long>> =
        driver.cells.filter { !it.deleted && it.timeMs != null && TimeTokens.inRange(it.timeMs) }.sortedBy { it.number }.map { it.number to it.timeMs!! }

    /** Comprobaciones cruzadas (mejor = mínimo, nº de vueltas, media) de un piloto con sus vueltas actuales. */
    fun checks(driverKey: String): DriverChecks? {
        val d = driver(driverKey) ?: return null
        return ImportValidation.check(countedLaps(d), d.reportedBestMs, d.reportedLapCount, d.reportedAvgMs)
    }

    /** Vueltas sospechosas de un piloto según la validación cruzada, sin contar las que el usuario ya tocó. */
    private fun suspects(driver: ReviewDriver): Set<Int> {
        val checks = checks(driver.key) ?: return emptySet()
        val touched = driver.cells.filter { it.edited || it.confirmed }.map { it.number }.toSet()
        return checks.suspectLapNumbers - touched
    }

    fun statusOf(driver: ReviewDriver, cell: ReviewCell): CellStatus {
        val ms = cell.timeMs
        return when {
            cell.deleted -> CellStatus.EMPTY
            ms == null -> if (cell.unreadable) CellStatus.INVALID else CellStatus.EMPTY
            !TimeTokens.inRange(ms) -> CellStatus.INVALID
            cell.edited || cell.confirmed -> CellStatus.OK
            cell.ocrFixed || cell.lowConfidence || cell.number in suspects(driver) -> CellStatus.DOUBTFUL
            else -> CellStatus.OK
        }
    }

    /**
     * Filas para mostrar de un piloto: todas las vueltas de 1 a la última (los huecos salen como celdas vacías para poder
     * rellenarlos a mano) con su estado.
     */
    fun rows(driverKey: String): List<Pair<ReviewCell, CellStatus>> {
        val d = driver(driverKey) ?: return emptyList()
        val last = maxOf(d.cells.maxOfOrNull { it.number } ?: 0, d.reportedLapCount ?: 0)
        val byNumber = d.cells.associateBy { it.number }
        return (1..last).map { n ->
            val cell = byNumber[n] ?: ReviewCell(n, null)
            cell to statusOf(d, cell)
        }
    }

    /** Tiempos que se guardarán de un piloto: las celdas ámbar y verdes, en orden; las rojas, vacías y borradas se excluyen. */
    fun savedTimes(driver: ReviewDriver): List<Long> =
        driver.cells.sortedBy { it.number }.filter { statusOf(driver, it).let { s -> s == CellStatus.OK || s == CellStatus.DOUBTFUL } }.map { it.timeMs!! }

    fun summary(): ReviewSummary {
        var doubtful = 0
        var invalid = 0
        var laps = 0
        drivers.forEach { d ->
            d.cells.forEach { c ->
                when (statusOf(d, c)) {
                    CellStatus.DOUBTFUL -> { doubtful++; laps++ }
                    CellStatus.OK -> laps++
                    CellStatus.INVALID -> invalid++
                    CellStatus.EMPTY -> Unit
                }
            }
        }
        val withLaps = drivers.count { savedTimes(it).isNotEmpty() }
        return ReviewSummary(driverCount = withLaps, lapCount = laps, doubtful = doubtful, invalid = invalid)
    }

    /** Por qué no se puede guardar (texto para mostrar bajo el botón), o `null` si se puede. */
    fun blockedReason(): BlockedReason? {
        val me = me ?: return BlockedReason.NO_ME
        return if (savedTimes(me).isEmpty()) BlockedReason.NO_VALID_LAPS else null
    }

    enum class BlockedReason { NO_ME, NO_VALID_LAPS }

    /** Primera celda dudosa o inválida a partir de ([fromDriver], [fromNumber]) en orden de lectura, para el botón «Siguiente». */
    fun nextToReview(fromDriver: String?, fromNumber: Int?): Pair<String, Int>? {
        val all = drivers.flatMap { d -> rows(d.key).filter { (_, s) -> s == CellStatus.DOUBTFUL || s == CellStatus.INVALID }.map { (c, _) -> d.key to c.number } }
        if (all.isEmpty()) return null
        val idx = all.indexOfFirst { it.first == fromDriver && it.second == fromNumber }
        return all[(idx + 1) % all.size]
    }

    // ---- Edición ----

    private fun updateDriver(key: String, change: (ReviewDriver) -> ReviewDriver) =
        copy(drivers = drivers.map { if (it.key == key) change(it) else it })

    private fun ReviewDriver.upsert(number: Int, change: (ReviewCell) -> ReviewCell): ReviewDriver {
        val existing = cells.firstOrNull { it.number == number }
        val updated = change(existing ?: ReviewCell(number, null))
        return copy(cells = (cells.filter { it.number != number } + updated).sortedBy { it.number })
    }

    /** El usuario teclea un tiempo: la celda pasa a estar editada y revisada (verde si está en rango). */
    fun editCell(driverKey: String, number: Int, timeMs: Long): ImportReview = updateDriver(driverKey) { d ->
        d.upsert(number) { it.copy(timeMs = timeMs, edited = true, deleted = false, unreadable = false) }
    }

    /** El usuario dice que la celda dudosa está bien. */
    fun confirmCell(driverKey: String, number: Int): ImportReview = updateDriver(driverKey) { d ->
        d.upsert(number) { it.copy(confirmed = true) }
    }

    /** Quita la vuelta (no se guardará). */
    fun deleteCell(driverKey: String, number: Int): ImportReview = updateDriver(driverKey) { d ->
        d.upsert(number) { it.copy(deleted = true) }
    }

    /** Número de la siguiente vuelta que se puede añadir a un piloto (la primera libre al final). */
    fun nextLapNumber(driverKey: String): Int = (driver(driverKey)?.cells?.maxOfOrNull { it.number } ?: 0) + 1

    /** Aplica la corrección de un dígito propuesta por la validación cruzada. Nunca se aplica sola. */
    fun applySuggestion(driverKey: String): ImportReview {
        val s = checks(driverKey)?.suggestion ?: return this
        return editCell(driverKey, s.lapNumber, s.proposedMs)
    }

    /** Propuesta vigente de un piloto (no ignorada). */
    fun suggestion(driverKey: String): LapSuggestion? =
        checks(driverKey)?.suggestion?.takeIf { "$driverKey|${it.lapNumber}" !in ignoredSuggestions }

    fun ignoreSuggestion(driverKey: String): ImportReview {
        val s = checks(driverKey)?.suggestion ?: return this
        return copy(ignoredSuggestions = ignoredSuggestions + "$driverKey|${s.lapNumber}")
    }

    fun setMe(key: String?): ImportReview = copy(meKey = key)

    fun rename(driverKey: String, name: String?): ImportReview = updateDriver(driverKey) { it.copy(name = name?.trim()?.ifEmpty { null }) }

    fun setKart(driverKey: String, kart: Int?): ImportReview = updateDriver(driverKey) { it.copy(kartNumber = kart) }

    /**
     * Une dos pilotos cuando el OCR partió un nombre en dos columnas: el resultado conserva la clave de [target], el nombre
     * («Cortamargari» + «tas» → «Cortamargari tas»), el kart y las posiciones del primero; las celdas de [source] solo rellenan huecos.
     */
    fun merge(sourceKey: String, targetKey: String): ImportReview {
        if (sourceKey == targetKey) return this
        val source = driver(sourceKey) ?: return this
        val target = driver(targetKey) ?: return this
        val name = listOfNotNull(target.name, source.name).joinToString(" ").ifEmpty { null }
        val taken = target.cells.map { it.number }.toSet()
        val merged = target.copy(
            name = name,
            kartNumber = target.kartNumber ?: source.kartNumber,
            position = target.position ?: source.position,
            reportedBestMs = target.reportedBestMs ?: source.reportedBestMs,
            reportedLapCount = target.reportedLapCount ?: source.reportedLapCount,
            reportedAvgMs = target.reportedAvgMs ?: source.reportedAvgMs,
            cells = (target.cells + source.cells.filter { it.number !in taken }).sortedBy { it.number },
        )
        return copy(
            drivers = drivers.filter { it.key != sourceKey }.map { if (it.key == targetKey) merged else it },
            meKey = if (meKey == sourceKey) targetKey else meKey,
        )
    }

    /** Divide el nombre de un piloto en la última palabra: el primer trozo conserva las vueltas, el segundo es un piloto nuevo sin vueltas. */
    fun split(driverKey: String): ImportReview {
        val d = driver(driverKey) ?: return this
        val name = d.name ?: return this
        val at = name.lastIndexOf(' ')
        if (at <= 0) return this
        val first = name.substring(0, at).trim()
        val second = name.substring(at + 1).trim()
        if (first.isEmpty() || second.isEmpty()) return this
        val newKey = uniqueKey(second)
        val updated = d.copy(name = first)
        val extra = ReviewDriver(newKey, second, null, null, null, null, null, emptyList())
        val index = drivers.indexOfFirst { it.key == driverKey }
        return copy(drivers = drivers.toMutableList().also { it[index] = updated; it.add(index + 1, extra) })
    }

    private fun uniqueKey(seed: String): String {
        var key = "split|${normalizeKey(seed)}"
        var i = 2
        while (drivers.any { it.key == key }) key = "split|${normalizeKey(seed)}|${i++}"
        return key
    }

    companion object {
        /**
         * Construye la revisión desde el documento analizado. [confidenceOf] devuelve la confianza (0..1) de la caja de OCR
         * de una celda `(línea, columna)`, si se conoce; por debajo de [LOW_CONFIDENCE] la celda sale en ámbar.
         */
        fun from(doc: ParsedDocument, confidenceOf: (line: Int, col: Int) -> Float? = { _, _ -> null }): ImportReview {
            val drivers = doc.drivers.map { d ->
                fun cell(lap: ParsedLap) = ReviewCell(
                    number = lap.number,
                    timeMs = lap.timeMs,
                    rawText = lap.rawText,
                    ocrFixed = lap.ocrFixed,
                    lowConfidence = (confidenceOf(lap.cellLine, lap.cellCol) ?: 1f) < LOW_CONFIDENCE,
                    line = lap.cellLine,
                    col = lap.cellCol,
                )
                ReviewDriver(
                    key = d.key,
                    name = d.name,
                    kartNumber = d.kartNumber,
                    position = d.position,
                    reportedBestMs = d.reportedBestMs,
                    reportedLapCount = d.reportedLapCount,
                    reportedAvgMs = d.reportedAvgMs,
                    cells = (d.laps.map(::cell) + d.invalidCells.map { cell(it) }).sortedBy { it.number },
                )
            }
            return ImportReview(drivers)
        }

        const val LOW_CONFIDENCE = 0.7f
    }
}
