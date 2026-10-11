package com.nacho.kartlog.domain.importing

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Palabra reconocida por el OCR con su geometría en píxeles de imagen. [baselineAngle] (radianes) es la
 * inclinación del texto según las esquinas de la caja (0 = horizontal).
 */
data class OcrBox(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val baselineAngle: Float = 0f,
    /** Confianza del reconocimiento en [0, 1] (1 si el motor no la da). */
    val confidence: Float = 1f,
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val height get() = bottom - top
    val width get() = right - left
}

/** Una celda de la tabla reconstruida: su texto y la caja que la delimita en la imagen original (sin enderezar). */
data class OcrGridPiece(val text: String, val box: OcrBox?)

/**
 * Tabla reconstruida: una lista de filas con sus celdas. [text] es lo que consume [LapTextParser] (una línea por fila, celdas
 * separadas por tabulador) y las coordenadas `(línea, columna)` de ese texto sirven para volver a la caja de la imagen.
 */
class OcrGrid(val lines: List<List<OcrGridPiece>>) {
    val text: String
        get() = lines.joinToString("\n") { row -> row.joinToString("\t") { it.text } }

    /** Caja de la celda `(línea, columna)` del texto reconstruido, si existe. */
    fun boxAt(line: Int, col: Int): OcrBox? = lines.getOrNull(line)?.getOrNull(col)?.box
}

/**
 * Reconstruye una tabla a partir de cajas de OCR para que [LapTextParser] la entienda: ML Kit devuelve
 * bloques en orden de lectura, que en una matriz vuelta×piloto mezcla columnas. Aquí se usa la geometría:
 *
 * 1. Se corrige la inclinación con la **mediana** de los ángulos de las cajas (robusta a valores sueltos).
 * 2. Las filas se agrupan por Y con tolerancia [ROW_TOLERANCE] × la altura mediana de línea.
 * 3. Las palabras contiguas de una fila se unen en una celda («Kart» + «26»).
 * 4. Las columnas se anclan por X en torno a los tiempos (las celdas más regulares); una celda ausente
 *    queda vacía en lugar de desplazar las demás.
 *
 * Devuelve una línea por fila con las celdas separadas por tabulador.
 */
object OcrLayout {
    const val ROW_TOLERANCE = 0.6f
    private const val WORD_GAP = 0.6f
    private const val MIN_COLUMN_SUPPORT = 2
    private val timeLike = Regex("""\d\s?[:.,'’"]\s?\d""")

    /** Un tiempo de vuelta completo (`m:ss.mmm`, con las confusiones típicas del OCR): lo único que ancla las columnas. */
    private val strictTime = Regex("""[0-9OoIlSB]{1,2}\s?[:;.,'’]\s?[0-9OoIlSB]{2}\s?[.,:;"”]\s?[0-9OoIlSB]{3}""")

    fun reconstruct(boxes: List<OcrBox>): String = reconstructGrid(boxes).text

    /** Igual que [reconstruct], pero conserva la caja (en coordenadas de la imagen original) de cada celda. */
    fun reconstructGrid(boxes: List<OcrBox>): OcrGrid {
        val usable = boxes.filter { it.text.isNotBlank() && it.height > 0f }
        if (usable.isEmpty()) return OcrGrid(emptyList())
        val deskewed = deskew(usable)
        val items = deskewed.mapIndexed { i, d -> Item(d, usable[i]) }
        val medianHeight = median(deskewed.map { it.height })
        val rows = groupRows(items, medianHeight * ROW_TOLERANCE).map { mergeWords(it, medianHeight * WORD_GAP) }
        val anchors = columnAnchors(rows, medianHeight)
        val split = rows.map { splitRow(it, anchors, medianHeight) }
        // Columna de etiquetas (nº de vuelta): si alguna fila la tiene, todas reservan su hueco.
        val leadingWidth = split.maxOf { it.leading.size }
        val lines = split.map { r ->
            val leading = r.leading + List(leadingWidth - r.leading.size) { OcrGridPiece("", null) }
            (leading + r.columns + r.trailing).dropLastWhile { it.text.isEmpty() }
        }
        return OcrGrid(lines)
    }

    /** Una palabra con su caja enderezada (para agrupar) y su caja original (para señalarla en la imagen). */
    private class Item(val d: OcrBox, val o: OcrBox)

    /** Gira los centros alrededor del origen para anular la inclinación mediana. */
    internal fun deskew(boxes: List<OcrBox>): List<OcrBox> {
        val angle = median(boxes.map { it.baselineAngle })
        if (abs(angle) < 1e-4f) return boxes
        val c = cos(-angle)
        val s = sin(-angle)
        return boxes.map { b ->
            val cx = b.centerX * c - b.centerY * s
            val cy = b.centerX * s + b.centerY * c
            val hw = b.width / 2f
            val hh = b.height / 2f
            b.copy(left = cx - hw, right = cx + hw, top = cy - hh, bottom = cy + hh, baselineAngle = 0f)
        }
    }

    private fun groupRows(items: List<Item>, tolerance: Float): List<List<Item>> {
        val rows = mutableListOf<MutableList<Item>>()
        var rowCenter = 0f
        for (b in items.sortedBy { it.d.centerY }) {
            val current = rows.lastOrNull()
            if (current != null && abs(b.d.centerY - rowCenter) <= tolerance) {
                current += b
                rowCenter = current.map { it.d.centerY }.average().toFloat()
            } else {
                rows += mutableListOf(b)
                rowCenter = b.d.centerY
            }
        }
        return rows.map { row -> row.sortedBy { it.d.left } }
    }

    private fun union(a: OcrBox, b: OcrBox, text: String) = a.copy(
        text = text,
        left = minOf(a.left, b.left), right = max(a.right, b.right),
        top = minOf(a.top, b.top), bottom = max(a.bottom, b.bottom),
        confidence = minOf(a.confidence, b.confidence),
    )

    /** Une palabras vecinas separadas por menos de [maxGap] (nombres con espacios, «Kart 26»). */
    private fun mergeWords(row: List<Item>, maxGap: Float): List<Item> {
        val out = mutableListOf<Item>()
        for (b in row) {
            val last = out.lastOrNull()
            val bothTimes = last != null && isTimeLike(last.d.text) && isTimeLike(b.d.text)
            if (last != null && !bothTimes && b.d.left - last.d.right <= maxGap && !(isTimeLike(last.d.text) || isTimeLike(b.d.text))) {
                val text = last.d.text + " " + b.d.text
                out[out.lastIndex] = Item(union(last.d, b.d, text), union(last.o, b.o, text))
            } else {
                out += b
            }
        }
        return out
    }

    /** Centros X de las columnas: agrupa los tiempos por cercanía horizontal. */
    private fun columnAnchors(rows: List<List<Item>>, medianHeight: Float): List<Float> {
        val times = rows.flatten().filter { isStrictTime(it.d.text) }.sortedBy { it.d.centerX }
        if (times.isEmpty()) return emptyList()
        val tolerance = max(median(times.map { it.d.width }) * 0.5f, medianHeight)
        val clusters = mutableListOf<MutableList<Float>>()
        for (t in times) {
            val last = clusters.lastOrNull()
            if (last != null && t.d.centerX - last.average().toFloat() <= tolerance) last += t.d.centerX else clusters += mutableListOf(t.d.centerX)
        }
        // Una columna de verdad tiene varios tiempos: un tiempo suelto (una fecha, un GAP) no crea columna.
        val supported = clusters.filter { it.size >= MIN_COLUMN_SUPPORT }.ifEmpty { clusters }
        return supported.map { it.average().toFloat() }
    }

    private class SplitRow(val leading: List<OcrGridPiece>, val columns: List<OcrGridPiece>, val trailing: List<OcrGridPiece>)

    /** Une las palabras de una celda: texto separado por espacios y caja que las abarca (en la imagen original). */
    private fun piece(items: List<Item>): OcrGridPiece {
        if (items.isEmpty()) return OcrGridPiece("", null)
        val text = items.joinToString(" ") { it.o.text }
        val box = items.map { it.o }.reduce { acc, b -> union(acc, b, text) }
        return OcrGridPiece(text, box)
    }

    private fun splitRow(row: List<Item>, anchors: List<Float>, medianHeight: Float): SplitRow {
        if (anchors.isEmpty()) return SplitRow(emptyList(), row.map { piece(listOf(it)) }, emptyList())
        val leading = mutableListOf<Item>()
        val columns = Array(anchors.size) { mutableListOf<Item>() }
        val trailing = mutableListOf<Item>()
        val columnTolerance = max(anchors.zipWithNext { a, b -> b - a }.minOrNull()?.div(2f) ?: Float.MAX_VALUE, medianHeight)
        for (b in row) {
            val nearest = anchors.indices.minBy { abs(anchors[it] - b.d.centerX) }
            val distance = abs(anchors[nearest] - b.d.centerX)
            when {
                isStrictTime(b.d.text) || distance <= columnTolerance * 0.75f -> columns[nearest] += b
                b.d.centerX < anchors.first() -> leading += b
                else -> trailing += b
            }
        }
        return SplitRow(leading.map { piece(listOf(it)) }, columns.map { piece(it) }, trailing.map { piece(listOf(it)) })
    }

    private fun isTimeLike(text: String) = timeLike.containsMatchIn(text)

    private fun isStrictTime(text: String) = strictTime.containsMatchIn(text)

    internal fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /** Ángulo (radianes) de la línea base dados dos puntos consecutivos de la caja. */
    fun angleOf(x0: Float, y0: Float, x1: Float, y1: Float): Float = atan2(y1 - y0, x1 - x0)
}
