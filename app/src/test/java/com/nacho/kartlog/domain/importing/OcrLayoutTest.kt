package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OcrLayoutTest {
    private val names = listOf("Deyvis" to 26, "Alvaro" to 31, "Nacho" to 28)
    private val laps = listOf(
        listOf("01:20.938", "01:20.304", "01:23.488"),
        listOf("01:17.947", "01:18.829", "01:20.902"),
        listOf("01:18.361", "01:17.310", "01:22.209"),
    )
    private val colX = floatArrayOf(60f, 260f, 460f, 660f)
    private val rowH = 40f
    private val wordH = 22f

    /** Genera las cajas de la matriz. [skew] gira todo alrededor del origen; [drop] omite una celda (fila, columna). */
    private fun grid(skew: Float = 0f, drop: Pair<Int, Int>? = null): List<OcrBox> {
        val out = mutableListOf<OcrBox>()
        fun add(text: String, col: Int, row: Int, width: Float, xOffset: Float = 0f) {
            if (drop == row to col) return
            val cx = colX[col] + xOffset
            val cy = 40f + row * rowH
            val rx = cx * cos(skew) - cy * sin(skew)
            val ry = cx * sin(skew) + cy * cos(skew)
            out += OcrBox(text, rx - width / 2, ry - wordH / 2, rx + width / 2, ry + wordH / 2, skew)
        }
        add("Vuelta", 0, 0, 60f)
        names.forEachIndexed { i, (n, _) -> add(n, i + 1, 0, 70f) }
        names.forEachIndexed { i, (_, kart) ->
            add("Kart", i + 1, 1, 40f, -26f)
            add("$kart", i + 1, 1, 24f, 22f)
        }
        for (lap in 0 until 3) {
            add("${lap + 1}", 0, 2 + lap, 14f)
            names.indices.forEach { c -> add(laps[c][lap], c + 1, 2 + lap, 110f) }
        }
        return out
    }

    private val parser = LapTextParser(knownCircuits = emptyList())

    private val nachoLaps = listOf(78361L, 77310L, 82209L)
    private val deyvisLaps = listOf(80938L, 80304L, 83488L)

    @Test
    fun rebuildsRowsAndColumnsInReadingOrderRegardlessOfInputOrder() {
        val lines = OcrLayout.reconstruct(grid().shuffled(java.util.Random(7))).lines()
        assertEquals("Vuelta\tDeyvis\tAlvaro\tNacho", lines[0])
        assertEquals("\tKart 26\tKart 31\tKart 28", lines[1])
        assertEquals("1\t01:20.938\t01:17.947\t01:18.361", lines[2])
    }

    @Test
    fun parserReadsTheReconstructedMatrix() {
        val doc = parser.parse(OcrLayout.reconstruct(grid()))
        assertEquals(3, doc.drivers.size)
        val nacho = doc.drivers.first { it.name == "Nacho" }
        assertEquals(nachoLaps, nacho.laps.map { it.timeMs })
        assertEquals(28, nacho.kartNumber)
    }

    @Test
    fun correctsSkewOfThreeDegrees() {
        // Sin corrección, la columna de la derecha (x≈660) subiría 660·sin(3°) ≈ 35 px, casi una fila entera.
        val skew = Math.toRadians(3.0).toFloat()
        val doc = parser.parse(OcrLayout.reconstruct(grid(skew = skew)))
        assertEquals(nachoLaps, doc.drivers.first { it.name == "Nacho" }.laps.map { it.timeMs })
        assertEquals(deyvisLaps, doc.drivers.first { it.name == "Deyvis" }.laps.map { it.timeMs })
    }

    @Test
    fun missingCellStaysEmptyInsteadOfShiftingColumns() {
        // Se pierde el tiempo de Alvaro en la vuelta 2 (fila 3, columna 2).
        val lines = OcrLayout.reconstruct(grid(drop = 3 to 2)).lines()
        assertEquals("2\t01:20.304\t\t01:17.310", lines[3])
    }

    @Test
    fun medianAngleIgnoresAFewWildBoxes() {
        val skew = Math.toRadians(2.0).toFloat()
        val boxes = grid(skew = skew).toMutableList()
        boxes[0] = boxes[0].copy(baselineAngle = 1.2f) // una caja con un ángulo disparatado
        val doc = parser.parse(OcrLayout.reconstruct(boxes))
        assertEquals(3, doc.drivers.size)
    }

    @Test
    fun emptyInputGivesEmptyText() {
        assertEquals("", OcrLayout.reconstruct(emptyList()))
        assertTrue(OcrLayout.reconstruct(listOf(OcrBox("  ", 0f, 0f, 10f, 10f))).isEmpty())
    }
}

class OcrGridTest {
    private fun box(text: String, x: Float, y: Float, w: Float = 100f, conf: Float = 1f) = OcrBox(text, x, y, x + w, y + 20f, 0f, conf)

    @Test
    fun everyCellKeepsItsBoxInOriginalImageCoordinates() {
        val boxes = listOf(
            box("Vuelta", 10f, 10f, 60f), box("Nacho", 200f, 10f, 70f), box("Deyvis", 400f, 10f, 70f),
            box("1", 10f, 50f, 14f), box("1:17.263", 200f, 50f, conf = 0.55f), box("1:15.100", 400f, 50f),
            box("2", 10f, 90f, 14f), box("1:16.905", 200f, 90f), box("1:14.800", 400f, 90f),
        )
        val grid = OcrLayout.reconstructGrid(boxes)
        assertEquals(OcrLayout.reconstruct(boxes), grid.text)
        // Línea 1 = primera vuelta; columna 1 = Nacho.
        val cell = grid.boxAt(1, 1)!!
        assertEquals("1:17.263", cell.text)
        assertEquals(200f, cell.left, 0.01f)
        assertEquals(50f, cell.top, 0.01f)
        assertEquals(0.55f, cell.confidence, 1e-6f)
        assertEquals("1:14.800", grid.boxAt(2, 2)!!.text)
        assertEquals(null, grid.boxAt(9, 9))
    }

    @Test
    fun theCellBoxIsInOriginalCoordinates_evenWhenThePhotoIsTilted() {
        val skew = 0.05f
        fun tilted(text: String, cx: Float, cy: Float, w: Float): OcrBox {
            val rx = cx * kotlin.math.cos(skew) - cy * kotlin.math.sin(skew)
            val ry = cx * kotlin.math.sin(skew) + cy * kotlin.math.cos(skew)
            return OcrBox(text, rx - w / 2, ry - 10f, rx + w / 2, ry + 10f, skew)
        }
        val boxes = listOf(
            tilted("1", 60f, 100f, 14f), tilted("1:17.263", 260f, 100f, 110f), tilted("1:15.100", 460f, 100f, 110f),
            tilted("2", 60f, 140f, 14f), tilted("1:16.905", 260f, 140f, 110f), tilted("1:14.800", 460f, 140f, 110f),
        )
        val grid = OcrLayout.reconstructGrid(boxes)
        val original = boxes.first { it.text == "1:16.905" }
        val found = grid.boxAt(1, 1)!!
        assertEquals(original.left, found.left, 0.01f)
        assertEquals(original.top, found.top, 0.01f)
    }
}
