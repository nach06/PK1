package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 6, D3: formatos tolerantes, detección + formato forzado, correcciones del OCR solo en celdas de tiempo y celdas inválidas. */
class ImportFormatsTest {
    private val parser = LapTextParser()
    private fun fixture(name: String) = javaClass.getResource("/muestras/$name")!!.readText()

    private val rowPerDriver = listOf(
        "Resultados Tanda Libre",
        "Nacho\t1:17.263\t1:16.905\t1:18.020\t1:17.500",
        "Deyvis\t1:15.100\t1:15.900\t1:14.800\t1:15.250",
        "Alvaro\t1:19.000\t1:18.700\t1:19.500\t1:18.900",
    ).joinToString("\n")

    // ---- Detección ----

    @Test
    fun detectsTheKnownFormats() {
        assertEquals(ImportFormat.MATRIX, parser.parse(fixture("muestra_a_correo_tabs.txt")).format)
        assertEquals(ImportFormat.MATRIX, parser.parse(fixture("muestra_b_asupark.txt")).format)
        assertEquals(ImportFormat.ROW_PER_DRIVER, parser.parse(rowPerDriver).format)
        assertEquals(ImportFormat.SINGLE_COLUMN, parser.parse("1  1:12.467\n2  1:13.102\n3  1:11.980").format)
        assertEquals(ImportFormat.SINGLE_COLUMN, parser.parse("1:12.467\n1:13.102\n1:11.980").format)
        assertNull(parser.parse("hola mundo").format)
    }

    @Test
    fun rowPerDriver_readsOneDriverPerLine_withLapsInColumns() {
        val doc = parser.parse(rowPerDriver)
        assertEquals(listOf("Nacho", "Deyvis", "Alvaro"), doc.drivers.map { it.name })
        val nacho = doc.drivers.first()
        assertEquals(listOf(77_263L, 76_905L, 78_020L, 77_500L), nacho.laps.map { it.timeMs })
        assertEquals(listOf(1, 2, 3, 4), nacho.laps.map { it.number })
        assertEquals(ImportFormat.ROW_PER_DRIVER, doc.format)
    }

    @Test
    fun rowPerDriver_acceptsPositionAndKartAroundTheName_andEmptyCellsKeepTheAlignment() {
        val text = listOf(
            "1\tNacho\tKart 28\t1:17.263\t1:16.905\t1:18.020",
            "2\tDeyvis\tKart 26\t1:15.100\t\t1:14.800\t1:15.200",
        ).joinToString("\n")
        val doc = parser.parse(text)
        assertEquals(ImportFormat.ROW_PER_DRIVER, doc.format)
        assertEquals(listOf(28, 26), doc.drivers.map { it.kartNumber })
        // El hueco de la segunda vuelta de Deyvis no desplaza la tercera.
        assertEquals(listOf(1, 3, 4), doc.drivers[1].laps.map { it.number })
    }

    @Test
    fun aMatrixIsNotMistakenForRowPerDriver() {
        val doc = parser.parse(fixture("muestra_a_correo_tabs.txt"))
        assertEquals(ImportFormat.MATRIX, doc.format)
        assertTrue(doc.drivers.size >= 3)
    }

    // ---- Formato forzado ----

    @Test
    fun aForcedFormatOverridesTheDetection() {
        val asMatrix = parser.parse(rowPerDriver, forced = ImportFormat.MATRIX)
        assertTrue("como matriz no encuentra las vueltas de la tabla por piloto", asMatrix.drivers.none { it.laps.size >= 4 })
        val single = parser.parse(rowPerDriver, forced = ImportFormat.SINGLE_COLUMN)
        assertEquals(ImportFormat.SINGLE_COLUMN, single.format)
        assertEquals(1, single.drivers.size)
        assertEquals(12, single.drivers.single().laps.size)
        val detected = parser.parse(rowPerDriver, forced = ImportFormat.ROW_PER_DRIVER)
        assertEquals(3, detected.drivers.size)
    }

    // ---- Correcciones del OCR: solo dentro de celdas de tiempo ----

    @Test
    fun ocrLettersAreFixedOnlyInsideTimeCells() {
        val text = listOf(
            "Oscar Ilsa\tKart 10",
            "1\t1:1O.467",
            "2\tl:12.S3B",
            "3\t1:13.102",
        ).joinToString("\n")
        val doc = parser.parse(text)
        val driver = doc.drivers.single()
        // Los nombres NO se tocan.
        assertEquals("Oscar Ilsa", driver.name)
        assertEquals(10, driver.kartNumber)
        // Las celdas de tiempo sí: O→0, l→1, S→5, B→8.
        assertEquals(listOf(70_467L, 72_538L, 73_102L), driver.laps.map { it.timeMs })
        assertEquals(listOf(true, true, false), driver.laps.map { it.ocrFixed })
    }

    @Test
    fun confusedSeparatorsAreFixedAndMarkedAsOcrFixed() {
        listOf("1.12.467", "1;12,467", "1:12:467").forEach { raw ->
            val match = TimeTokens.parseExact(raw)
            assertEquals(raw, 72_467L, match?.timeMs)
            assertTrue("$raw debe marcarse como corregido", match?.ocrFixed == true)
        }
        // Lo que parece un tiempo canónico no se marca.
        assertFalse(TimeTokens.parseExact("1:12.467")!!.ocrFixed)
        // Cifras con separador de miles o fechas no se toman por tiempos.
        assertNull(TimeTokens.parseExact("1.234.567"))
        assertTrue(TimeTokens.find("12.10.2025").isEmpty())
    }

    // ---- Celdas inválidas y coordenadas de origen ----

    @Test
    fun outOfRangeCellsAreKeptAsInvalid_notDroppedSilently() {
        val text = listOf("Nacho\tDeyvis", "1\t1:12.000\t1:13.000", "2\t0:05.123\t1:12.500", "3\t1:11.900\t1:13.200").joinToString("\n")
        val doc = parser.parse(text)
        val nacho = doc.drivers.first { it.name == "Nacho" }
        assertEquals(listOf(1, 3), nacho.laps.map { it.number })
        assertEquals(listOf(2), nacho.invalidCells.map { it.number })
        assertEquals("0:05.123", nacho.invalidCells.single().rawText)
        assertTrue(nacho.invalidCells.single().invalid)
        assertEquals(listOf("0:05.123"), doc.ignoredTimes)
    }

    @Test
    fun parsedLapsKnowTheirSourceCell() {
        val text = "Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t1:12.500\t1:13.500"
        val laps = parser.parse(text).drivers.first { it.name == "Nacho" }.laps
        assertEquals(listOf(1 to 1, 2 to 1), laps.map { it.cellLine to it.cellCol })
        val deyvis = parser.parse(text).drivers.first { it.name == "Deyvis" }.laps
        assertEquals(listOf(1 to 2, 2 to 2), deyvis.map { it.cellLine to it.cellCol })
    }
}
