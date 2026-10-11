package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.importing.OcrBox
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.ui.importing.FakePhotoProcessor
import com.nacho.kartlog.ui.importing.FakeRecognizer
import com.nacho.kartlog.ui.importing.boxesOf
import com.nacho.kartlog.ui.importing.scanOf
import com.nacho.kartlog.ui.importing.testImage
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Importar tiempos por el `NavHost` real (Sprint 6, Parte D): entrada con tres vías, pegar texto sin que el campo se coma la
 * pantalla, foto → recorte → revisión editable con colores por confianza → guardar con confirmación → Detalle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class ImportFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "imports").deleteRecursively()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        Thread.sleep(300) // deja terminar las consultas Room en vuelo antes de borrar la BD
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "imports").deleteRecursively()
    }

    private fun fixture(name: String) = javaClass.getResource("/muestras/$name")!!.readText()

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String, unmerged: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
    }

    private fun gone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }

    private fun state(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()

    private fun textOf(tag: String, unmerged: Boolean = true) =
        compose.onNodeWithTag(tag, useUnmergedTree = unmerged).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun click(tag: String) = compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)

    private fun assertInsideScreen(vararg tags: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        tags.forEach { tag ->
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag fuera de pantalla: $b (pantalla $root)", b.left >= 0f && b.right <= root.right && b.top >= 0f && b.bottom <= root.bottom && b.width > 0f)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()

    // ---------------------------------------------------------------- Texto compartido

    @Test
    fun importFromSharedTextEndsInDetailWithClassification() {
        val container = AppContainer(context)
        val text = fixture("muestra_a_correo_lineas.txt")
        runBlocking { container.circuitRepository.add("GP3", 900) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")

        // Contenido recibido con «Compartir»: abre la revisión con el texto ya analizado.
        container.importInbox.put(SharedContent.Text(text))
        waitForTag("who_banner")
        waitFor("¿Cuál eres tú?", substring = true)
        // Sin nombre guardado no hay selección automática: se toca la columna propia y «Soy yo».
        compose.onNodeWithTag("driver_chip_Nacho").performClick()
        compose.onNodeWithTag("action_me").performClick()
        gone("who_banner")
        compose.onNodeWithTag("review_summary").assertIsDisplayed()
        assertTrue(textOf("review_summary").contains("14 pilotos"))

        // El circuito (GP3) se reconoce por el título de la sesión: ya se puede guardar y no hay texto de bloqueo.
        assertTrue(textOf("meta_line").contains("GP3"))
        assertTrue(compose.onAllNodesWithTag("save_blocked").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithTag("review_save").performSemanticsAction(SemanticsActions.OnClick)

        // Detalle de la tanda nueva: vueltas importadas (icono) y clasificación plegable con mi fila.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lap_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("classification"))
        waitFor("Clasificación de la sesión", substring = true)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Importada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("classification").performClick()
        waitFor("Tú", substring = true)
        waitFor("Deyvis", substring = true) // el líder también está en la clasificación

        val sessions = runBlocking { container.sessionRepository.observeSummaries().first() }
        check(sessions.size == 1) { "debe haberse creado una tanda" }
        val laps = runBlocking { container.lapRepository.observeBySession(sessions.single().session.id).first() }
        check(laps.isNotEmpty() && laps.all { it.source == LapSource.IMPORTED }) { "las vueltas propias son IMPORTED" }
        val others = runBlocking { container.participantRepository.observeBySession(sessions.single().session.id).first() }
        check(others.size == 14 && others.count { it.isMe } == 1)
    }

    // ---------------------------------------------------------------- Entrada y pegar texto (A2)

    @Test
    fun entryOffersOneButtonWithThreeRoutes_andPastingLongTextKeepsEverythingInView() {
        val container = AppContainer(context)
        container.imageTextRecognizer = FakeRecognizer { scanOf(emptyList()) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_import").performClick()
        waitForTag("import_main")
        compose.onNodeWithTag("import_main").assertIsDisplayed()

        // Un solo botón principal; sus tres vías están en una hoja inferior.
        compose.onNodeWithTag("import_main").performClick()
        waitForTag("import_source_camera")
        listOf("import_source_camera", "import_source_gallery", "import_source_paste").forEach { compose.onNodeWithTag(it).assertExistsCompat() }
        click("import_source_paste")
        waitForTag("paste_field")

        // Con el campo vacío ANALIZAR está desactivado y el texto lo explica.
        compose.onNodeWithTag("paste_hint").assertIsDisplayed()
        // Pegar un resultado larguísimo: el campo ocupa lo que queda y los botones siguen a la vista.
        val long = (1..400).joinToString("\n") { "$it\t1:${10 + it % 40}.${100 + it}\t1:${11 + it % 40}.${200 + it}" }
        compose.onNodeWithTag("paste_field").performTextReplacement(long)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("paste_hint").fetchSemanticsNodes().isEmpty() }
        assertInsideScreen("paste_field", "paste_clipboard", "paste_analyze")
        val rootHeight = compose.onRoot().fetchSemanticsNode().boundsInRoot.height
        assertTrue("el campo no debe llenar la pantalla", compose.onNodeWithTag("paste_field").fetchSemanticsNode().boundsInRoot.height < rootHeight * 0.85f)
        compose.onNodeWithTag("paste_analyze").assertIsEnabled()
        compose.onNodeWithTag("paste_analyze").performClick()
        waitForTag("review_summary")
    }

    // ---------------------------------------------------------------- Foto → recorte → revisión → guardar

    /** Hoja de la muestra A leída por un OCR «ruidoso»: O en vez de 0, una celda con poca confianza y una fuera de rango. */
    private fun noisyBoxes(): List<OcrBox> {
        var text = fixture("muestra_a_correo_tabs.txt")
        text = text.replaceFirst("01:23.488", "O1:23.488") // V1 de Nacho: 0→O
        text = text.replaceFirst("01:21.767", "00:05.767") // V4 de Nacho: fuera de 15 s – 5 min
        return boxesOf(text, conf = { if (it == "01:20.902") 0.4f else 0.97f }, jitter = 3f)
    }

    @Test
    fun photoToReview_coloursCells_highlightsTheBoxOnTheSheet_confirmsDoubts_andKeepsThePhoto() {
        val container = AppContainer(context)
        container.userSettings.setDriverName("Nacho")
        val boxes = noisyBoxes()
        container.imageTextRecognizer = FakeRecognizer { scanOf(boxes) }
        container.photoProcessor = FakePhotoProcessor()
        val circuit = runBlocking { container.circuitRepository.add("Zuera", 900) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")

        // Foto compartida → pantalla de recorte (esquinas arrastrables) → ANALIZAR.
        container.importInbox.put(SharedContent.Image(testImage(context)))
        waitForTag("crop_canvas")
        compose.onNodeWithTag("crop_editor").assertIsDisplayed()
        compose.onNodeWithTag("crop_analyze").performClick()
        waitForTag("review_summary")

        // Revisión: la tuya (Nacho) ya elegida por el ajuste; los estados por celda.
        assertTrue(textOf("format_label").contains("matriz vuelta × piloto"))
        assertEquals("DOUBTFUL", state("review_row_1")) // O→0 corregida por el OCR
        assertEquals("DOUBTFUL", state("review_row_2")) // baja confianza (0,4)
        assertEquals("OK", state("review_row_3"))
        assertEquals("INVALID", state("review_row_4")) // fuera de rango
        assertEquals("OK", state("review_row_5"))
        assertTrue(textOf("review_summary").contains("2 celdas dudosas"))
        assertTrue(textOf("review_summary").contains("1 inválidas"))
        // Todo lo importante está en pantalla sin desplazarse.
        assertInsideScreen("meta_summary", "format_label", "driver_strip", "review_row_1", "review_row_2", "review_summary", "review_save")

        // Seleccionar una celda resalta su caja en la miniatura de la hoja.
        val cellBox = boxes.first { it.text == "01:20.902" }
        compose.onNodeWithTag("review_row_2").performClick()
        compose.waitUntil(10_000) { state("sheet_preview").startsWith("box=") }
        assertEquals("box=${cellBox.left.toInt()},${cellBox.top.toInt()},${cellBox.right.toInt()},${cellBox.bottom.toInt()}", state("sheet_preview"))

        // Sin circuito, GUARDAR no está gris a ciegas: explica por qué. Se elige el circuito.
        assertTrue(textOf("save_blocked").contains("circuito"))
        compose.onNodeWithTag("meta_summary").performClick()
        waitForTag("meta_circuit")
        click("meta_circuit")
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performSemanticsAction(SemanticsActions.OnClick)
        click("meta_done")
        gone("save_blocked")

        // GUARDAR con dudosas sin revisar: confirmación explícita; REVISAR no guarda nada.
        compose.onNodeWithTag("review_save").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("doubts_title")
        compose.onNodeWithTag("doubts_review").performClick()
        gone("doubts_title")
        assertTrue(runBlocking { container.sessionRepository.observeSummaries().first() }.isEmpty())

        // Revisar la ámbar de V2 («está bien») y guardar igualmente.
        compose.onNodeWithTag("confirm_2").performClick()
        compose.waitUntil(10_000) { state("review_row_2") == "OK" }
        compose.onNodeWithTag("review_save").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("doubts_title")
        compose.onNodeWithTag("doubts_save").performClick()

        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        val session = runBlocking { container.sessionRepository.observeSummaries().first() }.single().session
        // V4 (roja) no se guardó; V1 (ámbar corregida a 1:23.488) sí.
        val laps = runBlocking { container.lapRepository.observeBySession(session.id).first() }
        assertEquals(listOf(83_488L, 80_902L, 82_209L, 79_074L, 79_505L), laps.map { it.timeMs })
        assertEquals(circuit, session.circuitId)
        // La foto original se conservó (ajuste por defecto: sí).
        assertNotNull(container.importPhotoStore.pathOf(session.id))
    }

    @Test
    fun theSheetPhotoIsNotKeptWhenTheSettingIsOff() {
        val container = AppContainer(context)
        container.userSettings.setSaveImportPhoto(false)
        container.userSettings.setDriverName("Nacho")
        container.imageTextRecognizer = FakeRecognizer { scanOf(boxesOf(fixture("muestra_a_correo_tabs.txt"))) }
        container.photoProcessor = FakePhotoProcessor()
        runBlocking { container.circuitRepository.add("GP3", 900) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        container.importInbox.put(SharedContent.Image(testImage(context)))
        waitForTag("crop_analyze")
        compose.onNodeWithTag("crop_analyze").performClick()
        waitForTag("review_summary")
        // Sin dudosas ni inválidas y con el circuito reconocido por el título (GP3): se guarda directamente.
        compose.onNodeWithTag("review_save").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        val session = runBlocking { container.sessionRepository.observeSummaries().first() }.single().session
        assertNull(container.importPhotoStore.pathOf(session.id))
    }

    @Test
    fun anUnreliableReadingSaysSoAndSavesNothing_offeringToRetakeOrPasteText() {
        val container = AppContainer(context)
        container.imageTextRecognizer = FakeRecognizer { scanOf(boxesOf("Resultados\n1\t1:12.4\n2\t1:13.0")) }
        container.photoProcessor = FakePhotoProcessor()
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        container.importInbox.put(SharedContent.Image(testImage(context)))
        waitForTag("crop_analyze")
        compose.onNodeWithTag("crop_analyze").performClick()
        waitForTag("unreliable_card")
        waitFor("No he podido leer la hoja con fiabilidad", substring = true)
        compose.onNodeWithTag("unreliable_retake").assertExistsCompat()
        compose.onNodeWithTag("unreliable_paste").assertExistsCompat()
        assertTrue(compose.onAllNodesWithTag("review_save").fetchSemanticsNodes().isEmpty())
        assertTrue(runBlocking { container.sessionRepository.observeSummaries().first() }.isEmpty())
        // «Pegar texto» lleva al campo de texto.
        compose.onNodeWithTag("unreliable_paste").performClick()
        waitForTag("paste_field")
    }

    @Test
    fun aBlurryPhotoWarnsBeforeAnalysing() {
        val container = AppContainer(context)
        container.imageTextRecognizer = FakeRecognizer { scanOf(boxesOf(fixture("muestra_a_correo_tabs.txt"))) }
        container.photoProcessor = FakePhotoProcessor(problems = setOf(com.nacho.kartlog.domain.importing.PhotoProblem.BLURRY, com.nacho.kartlog.domain.importing.PhotoProblem.GLARE))
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        container.importInbox.put(SharedContent.Image(testImage(context)))
        waitForTag("crop_analyze")
        compose.onNodeWithTag("crop_analyze").performClick()
        waitForTag("quality_title")
        waitFor("movida o desenfocada", substring = true)
        waitFor("reflejos", substring = true)
        compose.onNodeWithTag("quality_anyway").performClick()
        waitForTag("review_summary")
    }

    @Test
    fun theFormatCanBeChangedFromTheReview() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        container.importInbox.put(SharedContent.Text("Resultados\nNacho\t1:17.263\t1:16.905\t1:18.020\nDeyvis\t1:15.100\t1:15.900\t1:14.800"))
        waitForTag("format_label")
        assertTrue(textOf("format_label").contains("una fila por piloto"))
        compose.onNodeWithTag("format_change").performClick()
        waitForTag("format_SINGLE_COLUMN")
        click("format_SINGLE_COLUMN")
        compose.waitUntil(10_000) { textOf("format_label").contains("una columna") }
    }

    @Test
    fun theCropCornersCanBeDragged_rotationResetsThem_andAnalysisUsesTheMovedQuad() {
        val container = AppContainer(context)
        val processor = FakePhotoProcessor()
        container.photoProcessor = processor
        container.imageTextRecognizer = FakeRecognizer { scanOf(boxesOf(fixture("muestra_a_correo_tabs.txt"))) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        container.importInbox.put(SharedContent.Image(testImage(context, width = 1000, height = 800)))
        waitForTag("crop_canvas")
        assertEquals("quad=0,0;1000,0;1000,800;0,800", state("crop_editor"))

        // Se arrastra la esquina superior izquierda hacia dentro: la imagen (1000×800) se encaja en el lienzo.
        val size = compose.onNodeWithTag("crop_canvas").fetchSemanticsNode().size
        val scale = minOf(size.width / 1000f, size.height / 800f)
        val left = (size.width - 1000 * scale) / 2f
        val top = (size.height - 800 * scale) / 2f
        compose.onNodeWithTag("crop_canvas").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(left, top))
            moveBy(androidx.compose.ui.geometry.Offset(100f * scale, 80f * scale))
            up()
        }
        compose.waitUntil(10_000) { !state("crop_editor").startsWith("quad=0,0;") }
        val q = state("crop_editor").removePrefix("quad=").split(";")[0].split(",").map { it.toInt() }
        assertTrue("esquina movida a ~(100, 80): $q", q[0] in 90..110 && q[1] in 72..88)
        // Las otras tres no se han movido.
        assertTrue(state("crop_editor").endsWith("1000,0;1000,800;0,800"))

        // Girar 90° devuelve las esquinas a la foto entera (con el nuevo tamaño 800×1000).
        compose.onNodeWithTag("crop_rotate").performClick()
        compose.waitUntil(10_000) { state("crop_editor") == "quad=0,0;800,0;800,1000;0,1000" }
        assertEquals(1, processor.rotations)

        // ANALIZAR usa el recorte vigente y el contraste mejorado (activado por defecto; se puede desactivar).
        compose.onNodeWithTag("crop_enhance").performClick()
        compose.onNodeWithTag("crop_analyze").performClick()
        waitForTag("review_summary")
        assertEquals(1, processor.prepared.size)
        assertEquals(com.nacho.kartlog.domain.importing.Quad.full(800, 1000), processor.prepared.single().first)
        assertFalse(processor.prepared.single().second)
    }

    @Test
    fun noisyRealisticOcrDump_endToEnd_editedByHand_confirmedAndSaved() {
        val container = AppContainer(context)
        container.userSettings.setDriverName("Nacho")
        val scan = com.nacho.kartlog.domain.importing.OcrDump.fromJson(javaClass.getResource("/ocr/muestra_a_ruidosa.json")!!.readText())
        container.imageTextRecognizer = FakeRecognizer { scan }
        container.photoProcessor = FakePhotoProcessor()
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        runBlocking { container.circuitRepository.add("GP3", 900) } // se reconoce por el título «Sesión 46 GP3 DIEGO»
        container.importInbox.put(SharedContent.Image(testImage(context)))
        waitForTag("crop_analyze")
        compose.onNodeWithTag("crop_analyze").performClick()
        waitForTag("review_summary")

        // Lo leído con ruido: Nacho (tú) tiene sus 6 vueltas; se ve su kart y el formato.
        assertTrue(textOf("format_label").contains("matriz vuelta × piloto"))
        assertEquals("OK", state("review_row_1"))
        // La columna de Asensio tiene un hueco (la celda que el OCR no vio): gris y rellenable a mano.
        compose.onNodeWithTag("driver_chip_Asensio").performClick()
        compose.waitUntil(10_000) { state("review_row_1") == "EMPTY" }
        compose.onNodeWithTag("edit_1").performClick()
        waitForTag("edit_time")
        // Se teclea 1:26.759 con el teclado propio (el valor real de la hoja).
        "126759".forEach { d -> compose.onAllNodesWithText(d.toString()).onLast().performSemanticsAction(SemanticsActions.OnClick) }
        assertEquals("1:26.759", textOf("edit_time"))
        click("edit_accept")
        compose.waitUntil(10_000) { state("review_row_1") == "OK" }

        // Vuelve a Nacho y guarda: hay celdas ámbar sin revisar en otras columnas → confirmación explícita.
        compose.onNodeWithTag("driver_chip_Nacho").performClick()
        compose.onNodeWithTag("review_save").performSemanticsAction(SemanticsActions.OnClick)
        waitForTag("doubts_title")
        compose.onNodeWithTag("doubts_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }

        val session = runBlocking { container.sessionRepository.observeSummaries().first() }.single().session
        val laps = runBlocking { container.lapRepository.observeBySession(session.id).first() }
        assertEquals(listOf(83_488L, 80_902L, 82_209L, 81_767L, 79_074L, 79_505L), laps.map { it.timeMs })
        // La clasificación guarda la columna de Asensio con la celda tecleada a mano.
        val asensio = runBlocking { container.participantRepository.observeBySession(session.id).first() }.first { it.name == "Asensio" }
        val asensioLaps = runBlocking { container.database.participantDao().lapsOf(asensio.id).map { it.timeMs } }
        assertEquals(listOf(86_759L), asensioLaps.take(1))
        assertEquals(6, asensioLaps.size)
        assertNotNull(container.importPhotoStore.pathOf(session.id))
    }
}
