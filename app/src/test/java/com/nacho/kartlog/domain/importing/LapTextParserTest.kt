package com.nacho.kartlog.domain.importing

import com.nacho.kartlog.domain.model.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LapTextParserTest {
    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/muestras/$name")) { "falta el fixture $name" }.readText()

    private val parser = LapTextParser(knownCircuits = listOf("Zuera", "Asupark"))

    private fun ParsedDocument.driver(name: String) = drivers.first { it.name == name }
    private fun ParsedDriver.times() = laps.map { it.timeMs }

    // ================= Muestra A (correo «Resumen de tu carrera») =================

    private fun assertMuestraA(doc: ParsedDocument) {
        assertEquals(14, doc.drivers.size)
        // Valores de control verificados a mano.
        val nacho = doc.driver("Nacho")
        assertEquals(listOf(83488L, 80902L, 82209L, 81767L, 79074L, 79505L), nacho.times())
        assertEquals(28, nacho.kartNumber)
        assertEquals(5, nacho.position)
        assertEquals(79074L, nacho.reportedBestMs)
        assertEquals(81157L, nacho.reportedAvgMs) // la media del documento está truncada
        assertEquals(6, nacho.reportedLapCount)
        val deyvis = doc.driver("Deyvis")
        assertEquals(listOf(80938L, 77947L, 78361L, 77091L, 78289L, 76515L), deyvis.times())
        assertEquals(1, deyvis.position)
        assertEquals(26, deyvis.kartNumber)
        assertEquals(76515L, deyvis.reportedBestMs)
        assertEquals(78190L, deyvis.reportedAvgMs)
        // Metadatos.
        assertEquals("Sesión 46 GP3 DIEGO", doc.label)
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 16), doc.dateTime)
        assertEquals(10, doc.durationMin)
        assertEquals(SessionType.RACE, doc.type) // «Resumen de tu carrera»
        // Dos bloques (8 + 6 pilotos) fusionados; los nombres partidos en la matriz («Alvarommk l», «Cortamargari tas») se
        // resuelven con el nombre completo de la tabla resumen.
        assertEquals(
            listOf(
                "Deyvis", "Diego Sanchez", "Morqui", "Asensio", "Nacho", "Alvarommkl", "KEVGT40", "F.Gascón",
                "Víctor", "LauraFC38", "Serw211", "Cortamargaritas", "J Robledo", "Mar",
            ),
            doc.drivers.map { it.name },
        )
        assertEquals(listOf(26, 2, 21, 18, 28, 9, 13, 19, 8, 6, 20, 10, 17, 25), doc.drivers.map { it.kartNumber })
        assertEquals((1..14).toList(), doc.drivers.map { it.position })
        // J Robledo y Mar tienen la V6 vacía: 5 vueltas, y su resumen dice 5.
        val sinV6 = listOf("J Robledo", "Mar").map { doc.driver(it) }
        assertTrue(sinV6.all { it.laps.size == 5 && it.reportedLapCount == 5 })
        assertEquals(listOf(88795L, 86498L, 85110L, 85696L, 85848L), doc.driver("J Robledo").times())
        assertEquals(92750L, doc.driver("Mar").reportedBestMs)
        assertTrue(doc.drivers.filter { it !in sinV6 }.all { it.laps.size == 6 })
        assertTrue(doc.drivers.all { it.reportedAvgMs != null && it.reportedBestMs != null })
        assertEquals(listOf(79263L, 78388L, 77875L, 78740L, 77884L, 77316L), doc.driver("Diego Sanchez").times())
        assertEquals(listOf(86156L, 84200L, 82965L, 82326L, 81857L, 81731L), doc.driver("Cortamargaritas").times())
    }

    @Test
    fun muestraA_conTabuladores() = assertMuestraA(parser.parse(fixture("muestra_a_correo_tabs.txt")))

    @Test
    fun muestraA_unaCeldaPorLinea() = assertMuestraA(parser.parse(fixture("muestra_a_correo_lineas.txt")))

    @Test
    fun muestraA_ambasFormasDanElMismoResultado() {
        val tabs = parser.parse(fixture("muestra_a_correo_tabs.txt")).drivers
        val lines = parser.parse(fixture("muestra_a_correo_lineas.txt")).drivers
        // Mismos datos; solo cambian las coordenadas de origen de cada celda (línea/columna del texto).
        fun strip(d: List<ParsedDriver>) = d.map { driver -> driver.copy(laps = driver.laps.map { it.copy(rawText = null, cellLine = -1, cellCol = -1) }, invalidCells = emptyList()) }
        assertEquals(strip(tabs), strip(lines))
    }

    // ================= Muestra B (hoja de Asupark) =================

    private fun assertMuestraB(doc: ParsedDocument) {
        assertEquals(listOf(26, 28, 50, 54), doc.drivers.map { it.kartNumber })
        assertTrue(doc.drivers.all { it.name == null })
        assertEquals(listOf(9, 9, 8, 8), doc.drivers.map { it.laps.size })
        val k26 = doc.drivers.first { it.kartNumber == 26 }
        assertEquals(listOf(64730L, 64028L, 65032L, 64074L, 63241L, 63870L, 63906L, 64315L, 66846L), k26.times())
        assertEquals(64449L, k26.reportedAvgMs)
        val k28 = doc.drivers.first { it.kartNumber == 28 }
        assertEquals(listOf(65572L, 65992L, 66493L, 65128L, 64913L, 64518L, 64516L, 85415L, 65419L), k28.times())
        assertEquals(67551L, k28.reportedAvgMs)
        assertEquals(LocalDateTime.of(2026, 10, 8, 19, 40), doc.dateTime)
        assertEquals(SessionType.FREE, doc.type)
        assertEquals("Asupark", doc.circuitName)
        // Las clasificaciones (Mejores de hoy/mes/absolutos) se ignoran: no crean pilotos ni vueltas.
        assertEquals(4, doc.drivers.size)
        // La tabla «Resumen» (P, Kart, V., Mejor) da posición, nº de vueltas y mejor de cada kart.
        assertEquals(listOf(1, 2, 3, 4), doc.drivers.map { it.position })
        assertEquals(listOf(9, 9, 8, 8), doc.drivers.map { it.reportedLapCount })
        assertEquals(listOf(63241L, 64516L, 67304L, 68237L), doc.drivers.map { it.reportedBestMs })
        val k50 = doc.drivers.first { it.kartNumber == 50 }
        assertEquals(listOf(69130L, 68630L, 67493L, 67875L, 67304L, 68192L, 68274L, 68551L), k50.times())
        assertEquals(68181L, k50.reportedAvgMs)
        assertEquals(69556L, doc.drivers.first { it.kartNumber == 54 }.reportedAvgMs)
    }

    @Test
    fun muestraB_laVueltaLentaDelKart28_seConservaYNoEsLimpia() {
        val k28 = parser.parse(fixture("muestra_b_asupark.txt")).drivers.first { it.kartNumber == 28 }
        val lap8 = k28.laps.first { it.number == 8 }
        assertEquals(85415L, lap8.timeMs) // no se descarta
        val best = k28.times().min()
        assertTrue("85415 > 107 % de $best", lap8.timeMs * 100 > best * 107)
        assertTrue(k28.times().filter { it != 85415L }.all { it * 100 <= best * 107 })
    }

    @Test
    fun muestraB_cabeceraConKart() = assertMuestraB(parser.parse(fixture("muestra_b_asupark.txt")))

    @Test
    fun muestraB_cabeceraSoloNumeros() = assertMuestraB(parser.parse(fixture("muestra_b_asupark_cabecera_numeros.txt")))

    // ================= Formatos de tiempo =================

    @Test
    fun cadaFormatoDeTiempoSeLeeIgual() {
        listOf("1:12.467", "01:12.467", "1:12,467", "72.467", "72,467", "1'12\"467", "1’12”467").forEach { raw ->
            val doc = parser.parse("1  $raw")
            assertEquals("formato $raw", listOf(72_467L), doc.drivers.single().times())
        }
    }

    @Test
    fun rangoValido_de15sA5min_losDemasSeListanComoIgnorados() {
        val doc = parser.parse("1 0:14.999\n2 0:15.000\n3 5:00.000\n4 5:00.001\n5 0:05.123\n6 1:12.467")
        assertEquals(listOf(15_000L, 300_000L, 72_467L), doc.drivers.single().times())
        assertEquals(setOf("0:14.999", "5:00.001", "0:05.123"), doc.ignoredTimes.toSet())
    }

    @Test
    fun diferenciasConSigno_yVelocidades_noSonVueltas() {
        val doc = parser.parse("1  1:12.467  +0.321\n2  1:13.001  -1:02.345\n3  1:12.900  72.467 km/h\n4  1:14.000  (+1.533)")
        assertEquals(listOf(72_467L, 73_001L, 72_900L, 74_000L), doc.drivers.single().times())
    }

    @Test
    fun correccionesDeOcr_soloDentroDeTokensDeTiempo() {
        val doc = parser.parse("1  1:O4,73O\n2  l:12.467\n3  S3.123\nPOSICION IS SOS")
        val laps = doc.drivers.single().laps
        assertEquals(listOf(64_730L, 72_467L, 53_123L), laps.map { it.timeMs })
        assertTrue(laps.all { it.ocrFixed })
        assertEquals(3, laps.size) // «POSICION IS SOS» no se toca ni genera nada
    }

    // ================= Lista simple de un piloto =================

    @Test
    fun listaSimple_variasFormasDeLinea() {
        val text = """
            1  1:12.467
            Vuelta 2 - 1:13.001
            Lap 3: 01:12.900
            4) 1:14.000 (+0.321)
            V5   1:12.350   45.3 km/h
        """.trimIndent()
        val driver = parser.parse(text).drivers.single()
        assertEquals(listOf(1, 2, 3, 4, 5), driver.laps.map { it.number })
        assertEquals(listOf(72_467L, 73_001L, 72_900L, 74_000L, 72_350L), driver.times())
        assertNull(driver.name)
    }

    @Test
    fun listaSimple_sinNumerosDeVuelta_seNumeraPorOrden() {
        val doc = parser.parse("1:12.467\n1:13.001\n1:12.900")
        assertEquals(listOf(1, 2, 3), doc.drivers.single().laps.map { it.number })
        assertTrue(doc.warnings.any { "numeraron" in it })
    }

    // ================= Matriz: variantes de etiquetas y celdas =================

    @Test
    fun etiquetasDeVuelta_V_Vuelta_Lap_yFilaDeMediaEnVariosIdiomas() {
        listOf("Avg.", "Media", "Promedio", "Average").forEach { avg ->
            val text = "Ana\tLuis\nV1\t1:10.000\t1:11.000\nVuelta 2\t1:12.000\t1:13.000\nLap 3\t1:14.000\t1:15.000\n$avg\t1:12.000\t1:13.000"
            val doc = parser.parse(text)
            assertEquals("$avg", listOf(70_000L, 72_000L, 74_000L), doc.driver("Ana").times())
            assertEquals(listOf(71_000L, 73_000L, 75_000L), doc.driver("Luis").times())
            assertEquals(72_000L, doc.driver("Ana").reportedAvgMs)
        }
    }

    @Test
    fun celdasVacias_conTabuladores_mantienenLaColumna() {
        val text = "Ana\tLuis\n1\t1:10.000\t1:11.000\n2\t1:12.000\t\n3\t1:14.000\t1:15.000"
        val doc = parser.parse(text)
        assertEquals(listOf(70_000L, 72_000L, 74_000L), doc.driver("Ana").times())
        assertEquals(listOf(71_000L, 75_000L), doc.driver("Luis").times())
        assertEquals(listOf(1, 3), doc.driver("Luis").laps.map { it.number })
    }

    @Test
    fun cabeceraConNombreYKartEnLaMismaCelda_ConParentesisOComa() {
        val doc = parser.parse("Ana (Kart 7)\tLuis, Kart 12\tPepe - Kart 3\n1\t1:10.000\t1:11.000\t1:12.000")
        assertEquals(listOf("Ana" to 7, "Luis" to 12, "Pepe" to 3), doc.drivers.map { it.name to it.kartNumber })
    }

    @Test
    fun nombreRotoEnDosLineas_seUneEnUnSoloPiloto() {
        val text = "Alvarommk\nl\nKart 40\nDani\nKart 15\n1\n1:10.000\n1:11.000\n2\n1:12.000\n1:13.000"
        val doc = parser.parse(text)
        assertEquals(listOf("Alvarommk l" to 40, "Dani" to 15), doc.drivers.map { it.name to it.kartNumber })
        assertEquals(listOf(70_000L, 72_000L), doc.driver("Alvarommk l").times())
    }

    @Test
    fun dosBloques_secombinanPorPiloto() {
        val text = "Ana\tLuis\n1\t1:10.000\t1:11.000\n2\t1:12.000\t1:13.000\nAvg.\t1:11.000\t1:12.000\n\n" +
            "Ana\tPepe\n3\t1:14.000\t1:15.000\n4\t1:16.000\t1:17.000\nAvg.\t1:15.000\t1:16.000"
        val doc = parser.parse(text)
        assertEquals(listOf("Ana", "Luis", "Pepe"), doc.drivers.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), doc.driver("Ana").laps.map { it.number })
        assertEquals(listOf(70_000L, 72_000L, 74_000L, 76_000L), doc.driver("Ana").times())
        assertEquals(2, doc.driver("Luis").laps.size)
        assertEquals(listOf(3, 4), doc.driver("Pepe").laps.map { it.number })
    }

    // ================= Tabla resumen =================

    @Test
    fun tablaResumen_conEspacios_aportaPosicionKartYMejor() {
        val text = """
            Pos  Piloto  Kart  V.  Mejor V.  en V.  GAP
            1  Deyvis  26  3  01:16.515  3  -
            2  Nacho  28  3  01:19.074  2  +2.559
            Resultados detallados
            Deyvis  Nacho
            1  01:20.000  01:21.000
            2  01:19.000  01:19.074
            3  01:16.515  01:20.000
        """.trimIndent()
        val doc = parser.parse(text)
        val nacho = doc.driver("Nacho")
        assertEquals(2, nacho.position)
        assertEquals(28, nacho.kartNumber)
        assertEquals(79_074L, nacho.reportedBestMs)
        assertEquals(3, nacho.reportedLapCount)
        assertEquals(3, doc.driver("Deyvis").laps.size)
    }

    @Test
    fun pilotosDelResumenSinMatriz_seAnadenSinVueltas() {
        val text = "1 Ana 7 3 01:10.000 2 -\n2 Luis 12 3 01:11.000 1 +1.000"
        val doc = parser.parse(text)
        assertEquals(listOf("Ana", "Luis"), doc.drivers.map { it.name })
        assertTrue(doc.drivers.all { it.laps.isEmpty() && it.reportedBestMs != null })
        assertTrue(doc.isEmpty)
    }

    // ================= Metadatos =================

    @Test
    fun fecha_conSeparadorGuionOEspacio_yDuracion() {
        listOf("08/10/2026 - 19:40", "08/10/2026 19:40", "8/10/2026 – 19:40").forEach { header ->
            val doc = parser.parse("$header\nDuración 12 minutos\n1 1:10.000")
            assertEquals(header, LocalDateTime.of(2026, 10, 8, 19, 40), doc.dateTime)
            assertEquals(12, doc.durationMin)
        }
    }

    @Test
    fun tipoPorPalabrasClave() {
        mapOf(
            "Resultados Tanda Libre" to SessionType.FREE,
            "Clasificación" to SessionType.QUALIFYING,
            "Carrera" to SessionType.RACE,
        ).forEach { (title, type) ->
            assertEquals(title, type, parser.parse("$title\n1 1:10.000").type)
        }
        assertNull(parser.parse("1 1:10.000").type)
    }

    @Test
    fun tipo_soloSeBuscaEnLaCabecera_noEnLasClasificacionesDeDespues() {
        val text = "Resultados Tanda Libre\nKart 26\tKart 28\n1\t1:10.000\t1:11.000\n\nClasificación mensual\n1  Kart 12  0:58,123"
        assertEquals(SessionType.FREE, parser.parse(text).type)
    }

    @Test
    fun circuito_porCoincidenciaConLosExistentes_sinAcentosNiMayusculas() {
        val p = LapTextParser(knownCircuits = listOf("Kartódromo Los Santos", "Zuera"))
        assertEquals("Kartódromo Los Santos", p.parse("KARTODROMO LOS SANTOS\n1 1:10.000").circuitName)
        assertNull(p.parse("Circuito desconocido\n1 1:10.000").circuitName)
    }

    // ================= Entradas raras =================

    @Test
    fun textoVacio_o_basura_noInventaNada() {
        assertTrue(parser.parse("").isEmpty)
        assertTrue(parser.parse("   \n\t\n").isEmpty)
        val junk = parser.parse("hola mundo\nasdf qwer\n12345\nNo hay nada aquí\n03/10/2026")
        assertTrue(junk.isEmpty)
        assertTrue(junk.drivers.isEmpty())
        assertTrue(junk.warnings.isNotEmpty())
    }

    @Test
    fun lineasBasuraEntreLasFilas_seIgnoran() {
        val text = "Ana\tLuis\nlogo.png\n1\t1:10.000\t1:11.000\n--- publicidad ---\n2\t1:12.000\t1:13.000\n"
        val doc = parser.parse(text)
        assertEquals(listOf(70_000L, 72_000L), doc.driver("Ana").times())
    }

    // ================= Perfiles extensibles =================

    @Test
    fun unPerfilNuevoSeAnadeSinTocarElResto() {
        val custom = object : LapTextProfile {
            override val name = "personalizado"
            override fun detectBlocks(text: PreparedText): List<MatrixBlock>? {
                if (text.lines.none { it.contains("FORMATO-X") }) return null
                val row = MatrixRow(false, 1, listOf(TimeTokens.parseExact("1:30.000")), 0, 0)
                return listOf(MatrixBlock(listOf(HeaderColumn("Perfil X", 99)), listOf(row), null, 0, 0))
            }
        }
        val withProfile = LapTextParser(extraProfiles = listOf(custom))
        val doc = withProfile.parse("FORMATO-X\n1 1:12.000")
        assertEquals("Perfil X", doc.drivers.single().name)
        assertEquals(listOf(90_000L), doc.drivers.single().times())
        // Sin la marca del perfil, sigue mandando el genérico.
        assertEquals(listOf(72_000L), withProfile.parse("1 1:12.000").drivers.single().times())
    }
}
