package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportValidationTest {
    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/muestras/$name")).readText()

    private fun laps(vararg ms: Long) = ms.mapIndexed { i, t -> (i + 1) to t }

    private val nacho = laps(83488, 80902, 82209, 81767, 79074, 79505)
    private val deyvis = laps(80938, 77947, 78361, 77091, 78289, 76515)
    private val b26 = laps(64730, 64028, 65032, 64074, 63241, 63870, 63906, 64315, 66846)
    private val b28 = laps(65572, 65992, 66493, 65128, 64913, 64518, 64516, 85415, 65419)

    @Test
    fun valoresDeControlDeLaMuestraA_cuadranTodos() {
        val n = ImportValidation.check(nacho, reportedBestMs = 79074, reportedLapCount = 6, reportedAvgMs = 81157)
        assertEquals(listOf(CheckState.OK, CheckState.OK, CheckState.OK), listOf(n.best, n.lapCount, n.average))
        assertTrue(n.allOk)
        assertEquals(79074L, n.computedBestMs)
        assertEquals(81157L, n.computedAvgMs) // 81157,5 truncado
        assertNull(n.suggestion)

        val d = ImportValidation.check(deyvis, 76515, 6, 78190)
        assertTrue(d.allOk && d.best == CheckState.OK && d.average == CheckState.OK)
    }

    @Test
    fun muestraB_soloTieneMedia_losDemasControlesNoEstanDisponibles() {
        val k26 = ImportValidation.check(b26, null, null, 64449)
        assertEquals(CheckState.NOT_AVAILABLE, k26.best)
        assertEquals(CheckState.NOT_AVAILABLE, k26.lapCount)
        assertEquals(CheckState.OK, k26.average)
        // La vuelta de 1:25.415 es real (la media del documento ya la incluye): no se marca nada.
        val k28 = ImportValidation.check(b28, null, null, 67551)
        assertEquals(CheckState.OK, k28.average)
        assertTrue(k28.allOk)
        assertNull(k28.suggestion)
    }

    @Test
    fun laMediaDelDocumentoEsTruncada_conToleranciaDeUnMs() {
        listOf(81156L, 81157L, 81158L).forEach {
            assertEquals("media $it", CheckState.OK, ImportValidation.check(nacho, null, null, it).average)
        }
        listOf(81155L, 81159L).forEach {
            assertEquals("media $it", CheckState.WARN, ImportValidation.check(nacho, null, null, it).average)
        }
    }

    @Test
    fun celdaAlterada_conConfusionTipicaDelOcr_marcaWarn_yProponeElDigito() {
        // 1:23.488 leído como 1:23.438 (un «8» tomado por «3»).
        val altered = nacho.toMutableList().also { it[0] = 1 to 83438L }
        val checks = ImportValidation.check(altered, 79074, 6, 81157)
        assertEquals(CheckState.WARN, checks.average)
        assertEquals(CheckState.OK, checks.best)
        assertEquals(LapSuggestion(lapNumber = 1, currentMs = 83438, proposedMs = 83488), checks.suggestion)
        assertEquals(setOf(1), checks.suspectLapNumbers)
        assertTrue(!checks.allOk)
    }

    @Test
    fun mejorVueltaDelDocumento_distintaDelMinimo_proponeLaCelda() {
        val altered = nacho.toMutableList().also { it[4] = 5 to 79174L } // 79074 leído como 79174
        val checks = ImportValidation.check(altered, 79074, 6, 81157)
        assertEquals(CheckState.WARN, checks.best)
        assertEquals(LapSuggestion(5, 79174, 79074), checks.suggestion)
    }

    @Test
    fun lasPropuestasNoSeAplicanSolas_lasVueltasDeEntradaNoCambian() {
        val altered = nacho.toMutableList().also { it[0] = 1 to 83438L }
        val before = altered.toList()
        ImportValidation.check(altered, 79074, 6, 81157)
        assertEquals(before, altered)
    }

    @Test
    fun siHayVariasCandidatasIgualDeBuenas_noSePropone_peroSeMarcanLasCeldas() {
        // Cuatro vueltas idénticas y una media 10 ms mayor: cualquiera de ellas podría ser la mal leída.
        val checks = ImportValidation.check(laps(60_000, 60_000, 60_000, 60_000), null, 4, 60_010)
        assertEquals(CheckState.WARN, checks.average)
        assertNull(checks.suggestion)
        assertEquals(setOf(1, 2, 3, 4), checks.suspectLapNumbers)
    }

    @Test
    fun numeroDeVueltas_distintoDelDocumento_esWarnSinPropuesta() {
        val checks = ImportValidation.check(nacho.dropLast(1), null, 6, null)
        assertEquals(CheckState.WARN, checks.lapCount)
        assertNull(checks.suggestion)
    }

    @Test
    fun sinDatosDeControl_nadaSeComprueba_yNoHayAvisos() {
        val checks = ImportValidation.check(nacho, null, null, null)
        assertEquals(listOf(CheckState.NOT_AVAILABLE, CheckState.NOT_AVAILABLE, CheckState.NOT_AVAILABLE), listOf(checks.best, checks.lapCount, checks.average))
        assertTrue(checks.allOk)
        assertNull(checks.suggestion)
    }

    @Test
    fun sinVueltas_noRevientaYLaMediaEsWarnSiElDocumentoLaTrae() {
        val checks = ImportValidation.check(emptyList(), null, null, 81157)
        assertEquals(CheckState.WARN, checks.average)
        assertNull(checks.computedBestMs)
        assertNull(checks.suggestion)
    }

    @Test
    fun todasLasMuestrasDelRepositorio_cuadranConSusDatosDeControl() {
        val parser = LapTextParser(knownCircuits = listOf("Asupark"))
        listOf("muestra_a_correo_tabs.txt", "muestra_a_correo_lineas.txt", "muestra_b_asupark.txt", "muestra_b_asupark_cabecera_numeros.txt").forEach { file ->
            parser.parse(fixture(file)).drivers.forEach { d ->
                val checks = ImportValidation.check(d.laps.map { it.number to it.timeMs }, d.reportedBestMs, d.reportedLapCount, d.reportedAvgMs)
                assertTrue("$file · ${d.displayName}: $checks", checks.allOk)
                assertEquals("$file · ${d.displayName} media comprobable", CheckState.OK, checks.average)
            }
        }
    }

    @Test
    fun unaCeldaAlteradaEnLaMuestraCompleta_seDetectaDeVerdad() {
        val parser = LapTextParser()
        val doc = parser.parse(fixture("muestra_a_correo_tabs.txt").replace("01:23.488", "01:23.438"))
        val d = doc.drivers.first { it.name == "Nacho" }
        val checks = ImportValidation.check(d.laps.map { it.number to it.timeMs }, d.reportedBestMs, d.reportedLapCount, d.reportedAvgMs)
        assertEquals(CheckState.WARN, checks.average)
        assertEquals(1, checks.suggestion?.lapNumber)
        assertEquals(83488L, checks.suggestion?.proposedMs)
    }

    @Test
    fun muestraB_unaCeldaAlterada_rompeLaMediaYProponeLaCorreccion() {
        val parser = LapTextParser()
        // Kart 28, V8: 1:25,415 → 1:25,475 (un dígito) desmiente la media 1:07,551 del documento.
        val doc = parser.parse(fixture("muestra_b_asupark.txt").replace("1:25,415", "1:25,475"))
        val d = doc.drivers.first { it.kartNumber == 28 }
        val checks = ImportValidation.check(d.laps.map { it.number to it.timeMs }, d.reportedBestMs, d.reportedLapCount, d.reportedAvgMs)
        assertEquals(CheckState.WARN, checks.average)
        assertEquals(CheckState.OK, checks.best)
        // Cambiar 1:25,415 → 1:25,475 desplaza 60 ms la suma y otras 3 celdas (V1, V2, V3) admiten un cambio de un dígito
        // con el mismo efecto: aritméticamente no se puede saber cuál es la errónea, así que NO se propone una (mejor
        // callar que acertar a medias), pero la V8 queda entre las celdas marcadas en ámbar.
        assertNull(checks.suggestion)
        assertTrue(8 in checks.suspectLapNumbers)
        // El resto de karts siguen en verde.
        doc.drivers.filter { it.kartNumber != 28 }.forEach { o ->
            assertTrue(ImportValidation.check(o.laps.map { it.number to it.timeMs }, o.reportedBestMs, o.reportedLapCount, o.reportedAvgMs).allOk)
        }
    }

    @Test
    fun muestraB_alterarLaMejorVuelta_rompeMejorYMedia_yProponeLaCorreccionUnica() {
        val parser = LapTextParser()
        // Kart 26, V5: 1:03,241 → 1:03,841. La «mejor» del documento deja de coincidir con el mínimo y la media se desvía.
        val doc = parser.parse(fixture("muestra_b_asupark.txt").replaceFirst("1:03,241", "1:03,841"))
        val d = doc.drivers.first { it.kartNumber == 26 }
        val checks = ImportValidation.check(d.laps.map { it.number to it.timeMs }, d.reportedBestMs, d.reportedLapCount, d.reportedAvgMs)
        assertEquals(CheckState.WARN, checks.best)
        assertEquals(CheckState.WARN, checks.average)
        assertEquals(5, checks.suggestion?.lapNumber)
        assertEquals(63241L, checks.suggestion?.proposedMs)
    }
}
