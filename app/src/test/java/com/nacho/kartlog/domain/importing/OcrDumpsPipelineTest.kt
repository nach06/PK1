package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Sprint 7, D3: recorre **todos** los volcados de OCR de los `.json` de `src/test/resources/ocr` (los reales de «Dump OCR (debug)» se
 * añaden copiándolos ahí) por layout → analizador → revisión y comprueba lo que debe cumplir cualquier lectura sana: se reconoce un
 * formato, hay pilotos con vueltas, ningún tiempo válido queda fuera de 15 s – 5 min y la revisión se puede construir.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OcrDumpsPipelineTest {
    private val dumps: List<File> = File(javaClass.getResource("/ocr/")!!.toURI()).listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }

    @Test
    fun thereIsAtLeastOneDump() {
        assertTrue("sin volcados en src/test/resources/ocr", dumps.isNotEmpty())
    }

    @Test
    fun everyDumpGoesThroughTheWholePipeline() {
        dumps.forEach { file ->
            val scan = OcrDump.fromJson(file.readText())
            val doc = LapTextParser().parse(scan.text)
            val name = file.name
            assertFalse("$name: no se reconoció nada", doc.isEmpty)
            assertTrue("$name: formato no reconocido", doc.format != null)
            val review = ImportReview.from(doc) { line, col -> scan.grid?.boxAt(line, col)?.confidence }
            val driversWithLaps = doc.drivers.filter { it.laps.isNotEmpty() }
            assertTrue("$name: ningún piloto con vueltas", driversWithLaps.isNotEmpty())
            driversWithLaps.forEach { d ->
                d.laps.forEach { lap -> assertTrue("$name: ${d.name} V${lap.number} fuera de rango (${lap.timeMs} ms)", lap.timeMs in 15_000L..300_000L) }
                assertEquals("$name: filas de la revisión de ${d.name}", d.laps.size, review.rows(d.key).count { it.second != CellStatus.EMPTY && it.second != CellStatus.INVALID })
            }
            println("OCR_DUMP $name: formato=${doc.format} pilotos=${doc.drivers.size} vueltas=${driversWithLaps.sumOf { it.laps.size }} cajas=${scan.boxes.size}")
        }
    }
}
