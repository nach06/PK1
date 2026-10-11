package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sprint 6, D8: una lectura de OCR con ruido realista (volcado en `src/test/resources/ocr/`) recorre OcrLayout → analizador → revisión.
 * Los volcados reales de ML Kit (Depuración) se prueban igual con [assertFixtureMatchesTheCleanSheet].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrFixtureTest {
    private val parser = LapTextParser()
    private fun resource(path: String) = javaClass.getResource(path)!!.readText()

    private val clean = parser.parse(resource("/muestras/muestra_a_correo_tabs.txt"))
    private val noisyScan = OcrDump.fromJson(resource("/ocr/muestra_a_ruidosa.json"))
    private val noisy = parser.parse(noisyScan.text)

    @Test
    fun theDumpRoundTripsThroughJson() {
        val again = OcrDump.fromJson(OcrDump.toJson(noisyScan))
        assertEquals(noisyScan.boxes, again.boxes)
        assertEquals(noisyScan.imageWidth, again.imageWidth)
        assertEquals(noisyScan.text, again.text)
    }

    @Test
    fun theNoisySheetIsReadAsTheCleanOne_exceptTheMissingCell() {
        assertEquals(ImportFormat.MATRIX, noisy.format)
        assertEquals(clean.drivers.size, noisy.drivers.size)
        clean.drivers.forEach { c ->
            val n = noisy.drivers.first { it.name == c.name || (it.name != null && c.name != null && normalizeKey(it.name) == normalizeKey(c.name)) }
            val cleanLaps = c.laps.associate { it.number to it.timeMs }
            val noisyLaps = n.laps.associate { it.number to it.timeMs }
            if (c.name == "Asensio") {
                // La celda que el OCR no vio falta; las demás coinciden.
                assertTrue(1 !in noisyLaps)
                assertEquals(cleanLaps - 1, noisyLaps)
            } else {
                assertEquals("vueltas de ${c.name}", cleanLaps, noisyLaps)
            }
        }
        // El nombre partido en dos cajas («Diego» + «Sanchez») se recompone en una celda.
        assertNotNull(noisy.drivers.firstOrNull { it.name == "Diego Sanchez" })
    }

    @Test
    fun theReviewMarksTheCorrectedCellsAmber_theMissingOneGrey_andTheRestGreen() {
        val review = ImportReview.from(noisy) { line, col -> noisyScan.grid.boxAt(line, col)?.confidence }
        val amber = mutableListOf<String>()
        review.drivers.forEach { d ->
            review.rows(d.key).forEach { (cell, status) ->
                if (status == CellStatus.DOUBTFUL) amber += "${d.name}#${cell.number}"
            }
        }
        // Las celdas de vuelta con letras corregidas (O→0 / l→1) y poca confianza son las ámbar; ninguna otra
        // (la «Avg.» estropeada de una columna no es una celda de vuelta: solo es un dato de control).
        val corruptedLapCells = noisyScan.text.lines().filter { Regex("^[1-6]\\t.*").matches(it) }
            .sumOf { line -> line.split('\t').count { c -> Regex("[0-9OlISB]{1,2}:[0-9OlISB]{2}\\.[0-9OlISB]{3}").matches(c) && c.any { ch -> ch.isLetter() } } }
        assertTrue("el fixture debe traer celdas estropeadas", corruptedLapCells >= 4)
        assertEquals("ámbar: $amber", corruptedLapCells, amber.size)
        val asensio = review.drivers.first { it.name == "Asensio" }
        assertEquals(CellStatus.EMPTY, review.rows(asensio.key).first { it.first.number == 1 }.second)
        // Los controles lo notan: a Asensio le falta una vuelta (5 de 6).
        assertEquals(CheckState.WARN, review.checks(asensio.key)!!.lapCount)
        // Las celdas ámbar están corregidas al valor correcto (no hay que editarlas, solo confirmarlas).
        review.drivers.filter { it.name != "Asensio" }.forEach { d ->
            val cleanDriver = clean.drivers.first { c -> c.name == d.name || normalizeKey(c.name.orEmpty()) == normalizeKey(d.name.orEmpty()) }
            assertEquals(cleanDriver.laps.map { it.timeMs }, review.savedTimes(d))
        }
    }

    @Test
    fun confirmingTheAmberCells_leavesNothingToConfirm() {
        var review = ImportReview.from(noisy) { line, col -> noisyScan.grid.boxAt(line, col)?.confidence }
        assertTrue(review.summary().doubtful > 0)
        while (true) {
            val next = review.nextToReview(null, null) ?: break
            val (key, number) = next
            val status = review.rows(key).first { it.first.number == number }.second
            // Las vacías no cuentan como «por revisar»; solo ámbar e inválidas.
            review = if (status == CellStatus.DOUBTFUL) review.confirmCell(key, number) else review.deleteCell(key, number)
        }
        assertEquals(0, review.summary().doubtful)
        assertFalse(review.summary().needsConfirmation)
    }

    private fun assertFalse(value: Boolean) = assertTrue(!value)
}
