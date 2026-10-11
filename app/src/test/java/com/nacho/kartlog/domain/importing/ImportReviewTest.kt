package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 6, D4: el modelo de la pantalla de revisión (estados de celda, qué se guarda, resumen, edición, unir/dividir). */
class ImportReviewTest {
    private val parser = LapTextParser()
    private fun fixture(name: String) = javaClass.getResource("/muestras/$name")!!.readText()

    private fun review(text: String, confidenceOf: (Int, Int) -> Float? = { _, _ -> null }) =
        ImportReview.from(parser.parse(text), confidenceOf)

    private val basic = "Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t1:12.500\t1:13.500\n3\t1:11.900\t1:13.200"

    private fun ImportReview.key(name: String) = drivers.first { it.name == name }.key
    private fun ImportReview.status(name: String, lap: Int) = rows(key(name)).first { it.first.number == lap }.second

    // ---- Estados de celda ----

    @Test
    fun cellStatuses_okDoubtfulInvalidAndEmpty() {
        val text = "Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:1O.900\t1:13.200\n5\t1:12.100\t1:13.100"
        val r = review(text)
        assertEquals(CellStatus.OK, r.status("Nacho", 1))
        assertEquals(CellStatus.INVALID, r.status("Nacho", 2)) // fuera de rango → rojo
        assertEquals(CellStatus.DOUBTFUL, r.status("Nacho", 3)) // O→0 corregida por el OCR → ámbar
        assertEquals(CellStatus.EMPTY, r.status("Nacho", 4)) // hueco entre la 3 y la 5 → gris
        assertEquals(CellStatus.OK, r.status("Nacho", 5))
        assertEquals(CellStatus.OK, r.status("Deyvis", 2))
    }

    @Test
    fun lowOcrConfidence_makesTheCellAmber() {
        // La caja de la celda (línea 2, columna 1) tiene poca confianza.
        val r = review(basic) { line, col -> if (line == 2 && col == 1) 0.4f else 0.95f }
        assertEquals(CellStatus.DOUBTFUL, r.status("Nacho", 2))
        assertEquals(CellStatus.OK, r.status("Nacho", 1))
    }

    @Test
    fun editingOrConfirmingADoubtfulCell_turnsItGreen_andEditingARedOneInRangeToo() {
        var r = review("Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:1O.900\t1:13.200")
        val nacho = r.key("Nacho")
        r = r.confirmCell(nacho, 3)
        assertEquals(CellStatus.OK, r.status("Nacho", 3))
        r = r.editCell(nacho, 2, 72_500)
        assertEquals(CellStatus.OK, r.status("Nacho", 2))
        // Pero un valor editado fuera de rango sigue en rojo.
        r = r.editCell(nacho, 2, 4_000)
        assertEquals(CellStatus.INVALID, r.status("Nacho", 2))
    }

    // ---- Qué se guarda ----

    @Test
    fun redAndEmptyAndDeletedCellsAreNeverSaved_doubtfulOnesAre() {
        val text = "Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:1O.900\t1:13.200\n4\t1:12.300\t1:13.300"
        var r = review(text)
        val nacho = r.drivers.first { it.name == "Nacho" }
        // 1 (verde) + 3 (ámbar) + 4 (verde); la 2 (roja) queda fuera.
        assertEquals(listOf(72_000L, 70_900L, 72_300L), r.savedTimes(nacho))
        r = r.deleteCell(nacho.key, 4)
        assertEquals(listOf(72_000L, 70_900L), r.savedTimes(r.driver(nacho.key)!!))
    }

    @Test
    fun summary_countsDriversLapsAndUnreviewedDoubts() {
        val text = "Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:1O.900\t1:13.200"
        val r = review(text)
        val s = r.summary()
        assertEquals(2, s.driverCount)
        assertEquals(5, s.lapCount) // Nacho: 1 (verde) y 3 (ámbar); Deyvis: 1, 2 y 3. La roja de Nacho (V2) no cuenta
        assertEquals(1, s.doubtful)
        assertEquals(1, s.invalid)
        assertTrue(s.needsConfirmation)
        // Tras revisar la dudosa y corregir la roja ya no hace falta confirmar.
        val nacho = r.key("Nacho")
        val clean = r.confirmCell(nacho, 3).editCell(nacho, 2, 72_500).summary()
        assertEquals(0, clean.doubtful)
        assertEquals(0, clean.invalid)
        assertFalse(clean.needsConfirmation)
    }

    @Test
    fun blockedReason_saysWhyYouCannotSave() {
        var r = review(basic)
        assertEquals(ImportReview.BlockedReason.NO_ME, r.blockedReason())
        r = r.setMe(r.key("Nacho"))
        assertNull(r.blockedReason())
        // Si todas mis celdas están borradas o rojas, no hay vueltas propias.
        val nacho = r.key("Nacho")
        (1..3).forEach { r = r.deleteCell(nacho, it) }
        assertEquals(ImportReview.BlockedReason.NO_VALID_LAPS, r.blockedReason())
    }

    // ---- Validación cruzada ----

    @Test
    fun checksumSuggestion_isAppliedOnlyOnRequest_andCanBeIgnored() {
        val doc = parser.parse(fixture("muestra_b_asupark.txt").replaceFirst("1:03,241", "1:03,841"))
        var r = ImportReview.from(doc)
        val kart26 = r.drivers.first { it.kartNumber == 26 }.key
        assertEquals(CheckState.WARN, r.checks(kart26)!!.best)
        val s = r.suggestion(kart26)
        assertNotNull(s)
        assertEquals(5, s!!.lapNumber)
        // La celda sospechosa sale en ámbar, pero NADA se ha aplicado solo.
        assertEquals(CellStatus.DOUBTFUL, r.rows(kart26).first { it.first.number == 5 }.second)
        assertEquals(63_841L, r.driver(kart26)!!.cells.first { it.number == 5 }.timeMs)
        // Ignorar la oculta pero deja la celda ámbar.
        val ignored = r.ignoreSuggestion(kart26)
        assertNull(ignored.suggestion(kart26))
        assertEquals(CellStatus.DOUBTFUL, ignored.rows(kart26).first { it.first.number == 5 }.second)
        // Aplicarla corrige el dígito y los controles vuelven a cuadrar.
        r = r.applySuggestion(kart26)
        assertEquals(63_241L, r.driver(kart26)!!.cells.first { it.number == 5 }.timeMs)
        assertTrue(r.checks(kart26)!!.allOk)
        assertEquals(CellStatus.OK, r.rows(kart26).first { it.first.number == 5 }.second)
    }

    // ---- Edición de la tabla ----

    @Test
    fun addAndEditCells_andNextLapNumber() {
        var r = review(basic)
        val nacho = r.key("Nacho")
        assertEquals(4, r.nextLapNumber(nacho))
        r = r.editCell(nacho, 4, 71_000)
        assertEquals(CellStatus.OK, r.status("Nacho", 4))
        assertEquals(5, r.nextLapNumber(nacho))
        // Rellenar un hueco.
        val gap = review("Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n3\t1:11.900\t1:13.200")
        val n = gap.key("Nacho")
        assertEquals(CellStatus.EMPTY, gap.status("Nacho", 2))
        assertEquals(CellStatus.OK, gap.editCell(n, 2, 72_222).status("Nacho", 2))
    }

    @Test
    fun mergeJoinsASplitName_andKeepsMeAndFillsGapsOnly() {
        val text = "Cortamargari\ttas\n1\t1:12.000\t\n2\t1:12.500\t1:13.500\n3\t1:11.900\t"
        var r = review(text).let { it.setMe(it.drivers.first().key) }
        assertEquals(2, r.drivers.size)
        val first = r.drivers[0].key
        val second = r.drivers[1].key
        r = r.merge(sourceKey = second, targetKey = first)
        assertEquals(1, r.drivers.size)
        assertEquals("Cortamargari tas", r.drivers.single().name)
        assertEquals(first, r.meKey)
        // La celda 2 de «tas» (1:13.500) NO pisa la de Cortamargari (1:12.500); las demás vueltas siguen ahí.
        assertEquals(listOf(72_000L, 72_500L, 71_900L), r.savedTimes(r.drivers.single()))
        // Unir consigo mismo no hace nada.
        assertEquals(r, r.merge(first, first))
    }

    @Test
    fun splitDividesTheNameAtTheLastSpace_firstKeepsTheLaps() {
        var r = review(basic)
        val nacho = r.key("Nacho")
        r = r.rename(nacho, "Ana Maria Lopez")
        r = r.split(nacho)
        assertEquals(listOf("Ana Maria", "Lopez", "Deyvis"), r.drivers.map { it.name })
        assertEquals(3, r.drivers[0].cells.size)
        assertTrue(r.drivers[1].cells.isEmpty())
        // Un nombre de una sola palabra no se divide.
        assertEquals(r, r.split(r.drivers[1].key))
    }

    @Test
    fun nextToReview_walksTheDoubtfulAndInvalidCellsAndWraps() {
        val text = "Nacho\tDeyvis\n1\t1:1O.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:11.900\t1:1l.200"
        val r = review(text)
        val nacho = r.key("Nacho")
        val deyvis = r.key("Deyvis")
        val first = r.nextToReview(null, null)!!
        assertEquals(nacho to 1, first)
        val second = r.nextToReview(first.first, first.second)!!
        assertEquals(nacho to 2, second)
        val third = r.nextToReview(second.first, second.second)!!
        assertEquals(deyvis to 3, third)
        assertEquals(first, r.nextToReview(third.first, third.second)) // da la vuelta
        assertNull(review("Nacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t1:12.100\t1:13.100").nextToReview(null, null))
    }
}
