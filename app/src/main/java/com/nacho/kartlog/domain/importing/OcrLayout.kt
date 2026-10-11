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
) {
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    val height get() = bottom - top
    val width get() = right - left
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
    private val timeLike = Regex("""\d\s?[:.,'’"]\s?\d""")

    fun reconstruct(boxes: List<OcrBox>): String {
        val usable = boxes.filter { it.text.isNotBlank() && it.height > 0f }
        if (usable.isEmpty()) return ""
        val deskewed = deskew(usable)
        val medianHeight = median(deskewed.map { it.height })
        val rows = groupRows(deskewed, medianHeight * ROW_TOLERANCE).map { mergeWords(it, medianHeight * WORD_GAP) }
        val anchors = columnAnchors(rows, medianHeight)
        val split = rows.map { splitRow(it, anchors, medianHeight) }
        // Columna de etiquetas (nº de vuelta): si alguna fila la tiene, todas reservan su hueco.
        val leadingWidth = split.maxOf { it.leading.size }
        return split.joinToString("\n") { r ->
            val leading = r.leading + List(leadingWidth - r.leading.size) { "" }
            (leading + r.columns + r.trailing).dropLastWhile { it.isEmpty() }.joinToString("\t")
        }
    }

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

    private fun groupRows(boxes: List<OcrBox>, tolerance: Float): List<List<OcrBox>> {
        val rows = mutableListOf<MutableList<OcrBox>>()
        var rowCenter = 0f
        for (b in boxes.sortedBy { it.centerY }) {
            val current = rows.lastOrNull()
            if (current != null && abs(b.centerY - rowCenter) <= tolerance) {
                current += b
                rowCenter = current.map { it.centerY }.average().toFloat()
            } else {
                rows += mutableListOf(b)
                rowCenter = b.centerY
            }
        }
        return rows.map { row -> row.sortedBy { it.left } }
    }

    /** Une palabras vecinas separadas por menos de [maxGap] (nombres con espacios, «Kart 26»). */
    private fun mergeWords(row: List<OcrBox>, maxGap: Float): List<OcrBox> {
        val out = mutableListOf<OcrBox>()
        for (b in row) {
            val last = out.lastOrNull()
            val bothTimes = last != null && isTimeLike(last.text) && isTimeLike(b.text)
            if (last != null && !bothTimes && b.left - last.right <= maxGap && !(isTimeLike(last.text) || isTimeLike(b.text))) {
                out[out.lastIndex] = last.copy(
                    text = last.text + " " + b.text,
                    right = max(last.right, b.right),
                    top = minOf(last.top, b.top),
                    bottom = max(last.bottom, b.bottom),
                )
            } else {
                out += b
            }
        }
        return out
    }

    /** Centros X de las columnas: agrupa los tiempos por cercanía horizontal. */
    private fun columnAnchors(rows: List<List<OcrBox>>, medianHeight: Float): List<Float> {
        val times = rows.flatten().filter { isTimeLike(it.text) }.sortedBy { it.centerX }
        if (times.isEmpty()) return emptyList()
        val tolerance = max(median(times.map { it.width }) * 0.5f, medianHeight)
        val clusters = mutableListOf<MutableList<Float>>()
        for (t in times) {
            val last = clusters.lastOrNull()
            if (last != null && t.centerX - last.average().toFloat() <= tolerance) last += t.centerX else clusters += mutableListOf(t.centerX)
        }
        return clusters.map { it.average().toFloat() }
    }

    private class SplitRow(val leading: List<String>, val columns: List<String>, val trailing: List<String>)

    private fun splitRow(row: List<OcrBox>, anchors: List<Float>, medianHeight: Float): SplitRow {
        if (anchors.isEmpty()) return SplitRow(emptyList(), row.map { it.text }, emptyList())
        val leading = mutableListOf<String>()
        val columns = Array(anchors.size) { mutableListOf<String>() }
        val trailing = mutableListOf<String>()
        val columnTolerance = max(anchors.zipWithNext { a, b -> b - a }.minOrNull()?.div(2f) ?: Float.MAX_VALUE, medianHeight)
        for (b in row) {
            val nearest = anchors.indices.minBy { abs(anchors[it] - b.centerX) }
            val distance = abs(anchors[nearest] - b.centerX)
            when {
                isTimeLike(b.text) || distance <= columnTolerance * 0.75f -> columns[nearest] += b.text
                b.centerX < anchors.first() -> leading += b.text
                else -> trailing += b.text
            }
        }
        return SplitRow(leading, columns.map { it.joinToString(" ") }, trailing)
    }

    private fun isTimeLike(text: String) = timeLike.containsMatchIn(text)

    internal fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    /** Ángulo (radianes) de la línea base dados dos puntos consecutivos de la caja. */
    fun angleOf(x0: Float, y0: Float, x1: Float, y1: Float): Float = atan2(y1 - y0, x1 - x0)
}
