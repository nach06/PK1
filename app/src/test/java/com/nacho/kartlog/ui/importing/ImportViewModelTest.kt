package com.nacho.kartlog.ui.importing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.ImportRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.ParticipantRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.importing.CheckState
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.domain.importing.CellStatus
import com.nacho.kartlog.domain.importing.ImportFormat
import com.nacho.kartlog.domain.importing.ImportInbox
import com.nacho.kartlog.domain.importing.ImportPhotoStore
import com.nacho.kartlog.domain.importing.PhotoProblem
import com.nacho.kartlog.domain.importing.Quad
import com.nacho.kartlog.domain.importing.PhotoProcessor
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

internal class FakeSettings(name: String) : UserSettings by com.nacho.kartlog.domain.settings.InMemoryUserSettings(name)

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportViewModelTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var circuits: CircuitRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var laps: LapRepositoryImpl
    private lateinit var participants: ParticipantRepositoryImpl
    private val inbox = ImportInbox()
    private var vm: ImportViewModel? = null

    private fun fixture(name: String) = requireNotNull(javaClass.getResource("/muestras/$name")).readText()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        circuits = CircuitRepositoryImpl(db.circuitDao())
        sessions = SessionRepositoryImpl(db.sessionDao())
        laps = LapRepositoryImpl(db.lapDao())
        participants = ParticipantRepositoryImpl(db.participantDao())
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        db.close()
        Dispatchers.resetMain()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private val settings = FakeSettings("")

    private fun viewModel(
        driverName: String = "",
        recognizer: ImageTextRecognizer? = null,
        processor: PhotoProcessor? = null,
        photoStore: ImportPhotoStore? = null,
    ): ImportViewModel {
        settings.setDriverName(driverName)
        return ImportViewModel(
            circuits, sessions, laps, ImportRepositoryImpl(db.importDao()), settings, inbox, recognizer,
            zone = ZoneId.of("UTC"), now = { 1_760_000_000_000 }, participantRepository = participants,
            photoProcessor = processor, photoStore = photoStore,
        ).also { vm = it }
    }

    private fun ImportUiState.myTimes() = review!!.savedTimes(me!!)
    private fun ImportUiState.cell(driver: com.nacho.kartlog.domain.importing.ReviewDriver, n: Int) = review!!.rows(driver.key).first { it.first.number == n }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun ImportViewModel.state(predicate: (ImportUiState) -> Boolean = { true }) = uiState.first(predicate)

    @Test
    fun muestraA_seleccionaPorNombre_validaYGuardaTodo() = run {
        val vm = viewModel(driverName = "nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt"))
        val s = vm.state { it.me != null }

        // «Yo» preseleccionado por «Mi nombre de piloto» (sin distinguir mayúsculas), y la revisión abierta.
        assertEquals(ImportStage.REVIEW, s.stage)
        assertEquals(ImportFormat.MATRIX, s.format)
        assertEquals("Nacho", s.me?.name)
        assertEquals(listOf(83488L, 80902L, 82209L, 81767L, 79074L, 79505L), s.myTimes())
        assertTrue(s.checks!!.allOk)
        assertEquals(CheckState.OK, s.checks!!.average)
        // Tanda nueva rellenada desde el documento.
        assertEquals(28, s.kartNumber)
        assertEquals("Sesión 46 GP3 DIEGO", s.label)
        assertEquals(10, s.durationMin)
        assertEquals(SessionType.RACE, s.type)
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 16), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.dateTime), ZoneId.of("UTC")))
        // Sin circuito reconocido no se puede guardar: hay que elegirlo, y se dice por qué.
        assertNull(s.circuitId)
        assertFalse(s.canSave)
        assertEquals(BlockedReason.NO_CIRCUIT, s.blockedReason)

        val circuit = circuits.add("Karting Zuera", null)
        vm.selectCircuit(circuit)
        assertTrue(vm.state { it.canSave }.canSave)
        vm.save()
        val sessionId = vm.saved.first()

        val detail = sessions.observeDetail(sessionId).first()!!
        assertEquals("Sesión 46 GP3 DIEGO", detail.session.label)
        assertEquals(10, detail.session.durationMin)
        assertEquals(28, detail.session.kartNumber)
        val myLaps = laps.observeBySession(sessionId).first()
        assertEquals(6, myLaps.size)
        assertTrue(myLaps.all { it.source == LapSource.IMPORTED })
        val classification = participants.observeBySession(sessionId).first()
        assertEquals(14, classification.size)
        assertEquals("Nacho", classification.single { it.isMe }.name)
        assertEquals(5, classification.single { it.isMe }.position)
        assertEquals(1, classification.first().position)
        val deyvis = classification.first { it.name == "Deyvis" }
        assertEquals(listOf(80938L, 77947L, 78361L, 77091L, 78289L, 76515L), db.participantDao().lapsOf(deyvis.id).map { it.timeMs })
        // Se guarda el texto original.
        db.sessionDao().observeById(sessionId).first()!!.session.importedRaw!!.let { assertTrue("Sesión 46 GP3 DIEGO" in it) }
    }

    @Test
    fun muestraB_sinNombres_seEligePorKart_yElCircuitoSeReconoce() = run {
        circuits.add("Asupark", null)
        val vm = viewModel(driverName = "Nacho") // el documento no trae nombres: no hay coincidencia
        vm.pasteAndAnalyze(fixture("muestra_b_asupark.txt"))
        var s = vm.state { it.parsed != null }
        assertNull(s.me)
        assertFalse(s.canSave) // nada se guarda sin elegir tu columna
        assertEquals(BlockedReason.NO_ME, s.blockedReason)
        assertEquals(listOf("Kart 26", "Kart 28", "Kart 50", "Kart 54"), s.parsed!!.drivers.map { it.displayName })

        val kart28 = s.parsed!!.drivers.first { it.kartNumber == 28 }
        vm.setMe(kart28.key)
        s = vm.state { it.me != null }
        assertEquals(9, s.myTimes().size)
        assertEquals("Asupark", s.selectedCircuit?.name) // reconocido por coincidencia de nombre
        assertEquals(SessionType.FREE, s.type)
        assertEquals(28, s.kartNumber)
        assertEquals(CheckState.OK, s.checks!!.average) // 67551: la vuelta de 1:25.415 es real
        assertTrue(s.canSave)
    }

    @Test
    fun unicoPilotoConVueltas_seSeleccionaSolo() = run {
        val vm = viewModel()
        vm.pasteAndAnalyze("1  1:12.467\n2  1:13.001\n3  1:12.900")
        val s = vm.state { it.me != null }
        assertEquals(3, s.myTimes().size)
        assertEquals("Piloto 1", s.me!!.displayName)
        assertEquals(ImportFormat.SINGLE_COLUMN, s.format)
    }

    @Test
    fun celdaAlterada_seMarcaAmbar_seProponeYSoloSeAplicaAlPedirlo() = run {
        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt").replace("01:23.488", "01:23.438"))
        var s = vm.state { it.me != null }
        assertEquals(CheckState.WARN, s.checks!!.average)
        assertEquals(83438L, s.myTimes().first()) // no se corrigió sola
        assertEquals(CellStatus.DOUBTFUL, s.cell(s.me!!, 1).second)
        assertEquals(83488L, s.suggestion?.proposedMs)

        vm.applySuggestion()
        s = vm.state { it.checks?.average == CheckState.OK }
        assertEquals(83488L, s.myTimes().first())
        assertEquals(CellStatus.OK, s.cell(s.me!!, 1).second)
        assertTrue(s.cell(s.me!!, 1).first.edited)
    }

    @Test
    fun ignorarLaPropuesta_laOcultaPeroLaCeldaSigueAmbar() = run {
        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt").replace("01:23.488", "01:23.438"))
        vm.state { it.suggestion != null }
        vm.ignoreSuggestion()
        val s = vm.state { it.suggestion == null }
        assertEquals(CellStatus.DOUBTFUL, s.cell(s.me!!, 1).second)
        assertEquals(83438L, s.myTimes().first())
    }

    @Test
    fun editarConElTeclado_quitarYRecuperarVueltas() = run {
        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt"))
        vm.state { it.me != null }

        vm.startEdit(2)
        vm.editClear()
        "121000".forEach { vm.editDigits(it.toString()) }
        assertEquals("1:21.000", vm.state { it.editing?.digits == "121000" }.editing!!.timeText)
        vm.confirmEdit()
        var s = vm.state { it.editing == null }
        assertEquals(81_000L, s.myTimes()[1])
        assertEquals(CheckState.WARN, s.checks!!.average) // al cambiar una vuelta ya no cuadra la media del documento

        vm.deleteLap(6)
        s = vm.state { it.myTimes().size == 5 }
        assertEquals(CheckState.WARN, s.checks!!.lapCount)
        // Se recupera tecleándola de nuevo.
        vm.startEdit(6)
        "79505".forEach { vm.editDigits(it.toString()) }
        vm.confirmEdit()
        assertEquals(6, vm.state { it.myTimes().size == 6 }.myTimes().size)

        // Un tiempo inválido (segundos ≥ 60) no se puede confirmar.
        vm.startEdit(3)
        vm.editClear()
        "60000".forEach { vm.editDigits(it.toString()) }
        vm.confirmEdit()
        assertTrue(vm.state().editing != null)
        vm.cancelEdit()
        assertNull(vm.state { it.editing == null }.editing)
    }

    @Test
    fun sinVueltasValidas_noSePuedeGuardar_yDiceLaRazon() = run {
        circuits.add("Asupark", null)
        val vm = viewModel()
        vm.pasteAndAnalyze("Asupark\n1  1:12.467\n2  1:13.001")
        vm.state { it.me != null }
        assertTrue(vm.state().canSave)
        vm.deleteLap(1)
        vm.deleteLap(2)
        val s = vm.state { it.myTimes().isEmpty() }
        assertFalse(s.canSave)
        assertEquals(BlockedReason.NO_VALID_LAPS, s.blockedReason)
    }

    @Test
    fun celdasRojasNoSeGuardan_yDudosasPidenConfirmacionExplicita() = run {
        circuits.add("Asupark", null)
        val vm = viewModel(driverName = "Nacho")
        // V2 fuera de rango (roja) y V3 con una «O» corregida por el OCR (ámbar).
        vm.pasteAndAnalyze("Asupark\nNacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:1O.900\t1:13.200")
        var s = vm.state { it.me != null }
        assertEquals(listOf(72_000L, 70_900L), s.myTimes()) // la roja queda fuera
        assertEquals(1, s.summary!!.doubtful)
        assertEquals(1, s.summary!!.invalid)
        assertTrue(s.canSave)

        // GUARDAR no guarda: pide confirmar antes.
        vm.save()
        s = vm.state { it.confirmDoubts }
        assertTrue(sessions.observeSummaries().first().isEmpty())
        // «Revisar» cancela y no guarda nada.
        vm.dismissDoubts()
        assertFalse(vm.state { !it.confirmDoubts }.confirmDoubts)
        assertTrue(sessions.observeSummaries().first().isEmpty())

        // Al guardar igualmente, la roja no entra y la ámbar sí.
        vm.save()
        vm.state { it.confirmDoubts }
        vm.confirmDoubts()
        val id = vm.saved.first()
        assertEquals(listOf(72_000L, 70_900L), laps.observeBySession(id).first().map { it.timeMs })
    }

    @Test
    fun revisarYCorregirLasCeldas_evitaLaConfirmacion() = run {
        circuits.add("Asupark", null)
        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze("Asupark\nNacho\tDeyvis\n1\t1:12.000\t1:13.000\n2\t0:05.123\t1:13.500\n3\t1:1O.900\t1:13.200")
        vm.state { it.me != null }
        // Corrige la roja con el teclado y confirma la ámbar.
        vm.startEdit(2)
        "112500".forEach { vm.editDigits(it.toString()) }
        vm.confirmEdit()
        vm.confirmLap(3)
        val s = vm.state { it.summary?.needsConfirmation == false }
        assertEquals(listOf(72_000L, 72_500L, 70_900L), s.myTimes())
        vm.save()
        // Sin celdas por revisar, guarda directamente (y la ámbar confirmada y la roja corregida entran).
        val id = vm.saved.first()
        assertEquals(listOf(72_000L, 72_500L, 70_900L), laps.observeBySession(id).first().map { it.timeMs })
    }

    @Test
    fun cambiarElFormatoReinterpretaLaHoja() = run {
        val vm = viewModel()
        val rowPerDriver = "Resultados\nNacho\t1:17.263\t1:16.905\t1:18.020\nDeyvis\t1:15.100\t1:15.900\t1:14.800"
        vm.pasteAndAnalyze(rowPerDriver)
        var s = vm.state { it.parsed != null }
        assertEquals(ImportFormat.ROW_PER_DRIVER, s.format)
        assertEquals(2, s.review!!.drivers.size)
        // Forzado como «solo mis vueltas»: un único piloto con todas las celdas.
        vm.changeFormat(ImportFormat.SINGLE_COLUMN)
        s = vm.state { it.format == ImportFormat.SINGLE_COLUMN }
        assertEquals(1, s.review!!.drivers.size)
        assertEquals(6, s.myTimes().size)
        // Volver a detectar.
        vm.changeFormat(null)
        assertEquals(ImportFormat.ROW_PER_DRIVER, vm.state { it.format == ImportFormat.ROW_PER_DRIVER }.format)
    }

    @Test
    fun unirYDividirPilotos_desdeLaRevision() = run {
        val vm = viewModel(driverName = "Cortamargari")
        vm.pasteAndAnalyze("Cortamargari\ttas\n1\t1:12.000\t1:13.000\n2\t1:12.500\t\n3\t1:11.900\t")
        var s = vm.state { it.me != null }
        assertEquals(2, s.review!!.drivers.size)
        val other = s.review!!.drivers.first { it.name == "tas" }
        vm.mergeInto(other.key)
        s = vm.state { it.review!!.drivers.size == 1 }
        assertEquals("Cortamargari tas", s.me!!.name)
        vm.splitDriver()
        s = vm.state { it.review!!.drivers.size == 2 }
        assertEquals(listOf("Cortamargari", "tas"), s.review!!.drivers.map { it.name })
        assertEquals(3, s.myTimes().size) // las vueltas se quedan con el primer trozo
    }

    @Test
    fun tandaExistente_anadeVueltasADespues_yAvisaDeDuplicados() = run {
        val circuit = circuits.add("Asupark", null)
        val existing = sessions.create(NewSession(circuit, 1_000, SessionType.FREE, 28, null, null, null, TrackCondition.DRY, ""))
        laps.addManual(existing, 83_488) // mismo tiempo exacto que la vuelta 1 de la muestra
        laps.addManual(existing, 90_000)

        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt"))
        vm.state { it.me != null }
        vm.setDestination(ImportDestination.EXISTING_SESSION)
        vm.selectExistingSession(existing)
        val s = vm.state { it.destination == ImportDestination.EXISTING_SESSION && it.duplicateCount > 0 }
        assertEquals(1, s.duplicateCount)
        assertTrue(s.canSave)

        vm.save()
        assertEquals(existing, vm.saved.first())
        assertEquals((1..8).toList(), laps.observeBySession(existing).first().map { it.number })
        assertEquals(listOf(LapSource.MANUAL, LapSource.MANUAL) + List(6) { LapSource.IMPORTED }, laps.observeBySession(existing).first().map { it.source })
    }

    @Test
    fun tandaExistenteConClasificacion_pideConfirmarAntesDeSustituirla() = run {
        val circuit = circuits.add("Asupark", null)
        val existing = sessions.create(NewSession(circuit, 1_000, SessionType.FREE, 28, null, null, null, TrackCondition.DRY, ""))
        // Primera importación: deja una clasificación en la tanda.
        val first = viewModel(driverName = "Nacho")
        first.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt"))
        first.state { it.me != null }
        first.setDestination(ImportDestination.EXISTING_SESSION)
        first.selectExistingSession(existing)
        first.state { it.destination == ImportDestination.EXISTING_SESSION && it.existingSessionId == existing }
        first.save() // aún sin clasificación: guarda directamente
        assertEquals(existing, first.saved.first())
        assertEquals(14, participants.observeBySession(existing).first().size)
        first.viewModelScope.cancel()

        // Segunda importación sobre la misma tanda: pide confirmación y no guarda nada hasta confirmar.
        val second = viewModel(driverName = "Deyvis")
        second.pasteAndAnalyze(fixture("muestra_a_correo_lineas.txt"))
        second.state { it.me != null }
        second.setDestination(ImportDestination.EXISTING_SESSION)
        second.selectExistingSession(existing)
        second.state { it.destination == ImportDestination.EXISTING_SESSION && it.existingSessionId == existing }
        second.save()
        assertTrue(second.state { it.confirmReplace }.confirmReplace)
        assertEquals(6, laps.observeBySession(existing).first().size)

        second.dismissReplace()
        assertEquals(false, second.state { !it.confirmReplace }.confirmReplace)
        assertEquals(6, laps.observeBySession(existing).first().size)
        assertTrue(participants.observeBySession(existing).first().first { it.isMe }.name == "Nacho")

        second.save()
        second.state { it.confirmReplace }
        second.confirmReplace()
        assertEquals(existing, second.saved.first())
        assertEquals(12, laps.observeBySession(existing).first().size)
        val classification = participants.observeBySession(existing).first()
        assertEquals(14, classification.size)
        assertEquals("Deyvis", classification.single { it.isMe }.name)
    }

    @Test
    fun contenidoCompartido_texto_seAnalizaSolo() = run {
        val vm = viewModel(driverName = "Deyvis")
        inbox.put(SharedContent.Text(fixture("muestra_a_correo_lineas.txt")))
        val s = vm.state { it.me != null }
        assertEquals("Deyvis", s.me?.name)
        assertEquals(6, s.myTimes().size)
        assertNull(inbox.content.value) // el buzón queda vacío
    }

    // ---------------- Foto ----------------

    private fun photoVm(
        scan: (String) -> com.nacho.kartlog.domain.importing.OcrScan,
        problems: Set<PhotoProblem> = emptySet(),
        driverName: String = "Deyvis",
        store: ImportPhotoStore? = null,
    ): Triple<ImportViewModel, FakeRecognizer, FakePhotoProcessor> {
        val recognizer = FakeRecognizer(scan)
        val processor = FakePhotoProcessor(problems)
        return Triple(viewModel(driverName, recognizer, processor, store), recognizer, processor)
    }

    private fun sheetScan() = scanOf(boxesOf(fixture("muestra_a_correo_tabs.txt")))

    @Test
    fun contenidoCompartido_imagen_abreElRecorte_yAnalizarLlevaALaRevision() = run {
        val (vm, recognizer, processor) = photoVm({ sheetScan() })
        inbox.put(SharedContent.Image("content://foto/1"))
        var s = vm.state { it.stage == ImportStage.CROP && it.photo != null }
        // Esquinas iniciales = la foto entera; todavía no se ha leído nada.
        assertEquals(Quad.full(1000, 800), s.photo!!.quad)
        assertTrue(recognizer.requested.isEmpty())
        // Las esquinas se mueven y se giran 90°.
        val moved = Quad(floatArrayOf(40f, 960f, 950f, 30f), floatArrayOf(30f, 40f, 770f, 760f))
        vm.setQuad(moved)
        assertEquals(moved, vm.state { it.photo?.quad == moved }.photo!!.quad)
        vm.rotatePhoto()
        vm.state { processor.rotations == 1 }

        vm.analyzePhoto()
        s = vm.state { it.stage == ImportStage.REVIEW }
        assertEquals(1, processor.prepared.size)
        assertTrue(processor.prepared.single().second) // contraste mejorado por defecto
        assertEquals(1, recognizer.requested.size)
        assertEquals("Deyvis", s.me?.name)
        assertEquals(6, s.myTimes().size)
        assertTrue(s.scan != null && s.reviewImage != null)
    }

    @Test
    fun fotoMovidaOOscura_avisaAntesDeAnalizar_yPermiteSeguirOCancelar() = run {
        val (vm, recognizer, _) = photoVm({ sheetScan() }, problems = setOf(PhotoProblem.BLURRY))
        vm.loadPhoto("content://foto/2")
        vm.state { it.stage == ImportStage.CROP && it.photo != null }
        vm.analyzePhoto()
        var s = vm.state { it.showQualityPrompt }
        assertTrue(recognizer.requested.isEmpty()) // aún no se ha leído
        vm.dismissQualityPrompt()
        assertFalse(vm.state { !it.showQualityPrompt }.showQualityPrompt)
        vm.analyzePhoto()
        vm.state { it.showQualityPrompt }
        vm.analyzeAnyway()
        s = vm.state { it.stage == ImportStage.REVIEW }
        assertEquals(1, recognizer.requested.size)
        assertTrue(s.review != null)
    }

    @Test
    fun lecturaPocoFiable_noAbreLaRevision_yNoGuardaNada() = run {
        // Pocas cajas y casi ninguna celda legible.
        val (vm, _, _) = photoVm({ scanOf(boxesOf("Resultados\n1\t1:12.4\n2\t1:13.0")) })
        vm.loadPhoto("content://foto/3")
        vm.state { it.stage == ImportStage.CROP && it.photo != null }
        vm.analyzePhoto()
        val s = vm.state { it.error == ImportError.UNRELIABLE }
        assertEquals(ImportStage.CROP, s.stage)
        assertNull(s.review)
        assertTrue(sessions.observeSummaries().first().isEmpty())
    }

    @Test
    fun errores_textoVacio_sinVueltas_fotoIlegibleYOcrFallido() = run {
        val vm = viewModel()
        vm.analyze()
        assertEquals(ImportError.EMPTY_TEXT, vm.state { it.error != null }.error)
        vm.pasteAndAnalyze("hola, esto no tiene tiempos")
        assertEquals(ImportError.NOTHING_FOUND, vm.state { it.error == ImportError.NOTHING_FOUND }.error)
        vm.loadPhoto("content://x") // sin motor de OCR configurado
        assertEquals(ImportError.OCR_FAILED, vm.state { it.error == ImportError.OCR_FAILED }.error)

        val (photoVm, recognizer, processor) = photoVm({ sheetScan() })
        processor.failLoad = true
        photoVm.loadPhoto("content://x")
        assertEquals(ImportError.PHOTO_FAILED, photoVm.state { it.error == ImportError.PHOTO_FAILED }.error)
        assertEquals(ImportStage.ENTRY, photoVm.state().stage)
        processor.failLoad = false
        recognizer.fail = true
        photoVm.loadPhoto("content://y")
        photoVm.state { it.photo != null }
        photoVm.analyzePhoto()
        assertEquals(ImportError.OCR_FAILED, photoVm.state { it.error == ImportError.OCR_FAILED }.error)
    }

    @Test
    fun laFotoOriginalSoloSeConservaSiElAjusteLoPermite() = run {
        circuits.add("Asupark", null)
        val store = FakePhotoStore()
        val (vm, _, _) = photoVm({ sheetScan() }, store = store)
        vm.loadPhoto("content://foto/9")
        vm.state { it.photo != null }
        vm.analyzePhoto()
        vm.state { it.stage == ImportStage.REVIEW }
        vm.selectCircuit(circuits.add("Zuera", null))
        vm.save()
        val id = vm.saved.first()
        assertEquals("content://foto/9", store.saved[id])

        // Con el ajuste desactivado no se guarda la foto.
        settings.setSaveImportPhoto(false)
        val store2 = FakePhotoStore()
        val (vm2, _, _) = photoVm({ sheetScan() }, store = store2)
        vm2.loadPhoto("content://foto/10")
        vm2.state { it.photo != null }
        vm2.analyzePhoto()
        vm2.state { it.stage == ImportStage.REVIEW }
        vm2.selectCircuit(circuits.add("Alonso", null))
        vm2.save()
        vm2.saved.first()
        assertTrue(store2.saved.isEmpty())
    }

    @Test
    fun crearCircuitoDesdeLaRevision_loSeleccionaYNoGuardaNadaAntesDeTiempo() = run {
        val vm = viewModel()
        vm.pasteAndAnalyze("1  1:12.467\n2  1:13.001")
        vm.state { it.me != null }
        vm.openNewCircuit()
        vm.createCircuit("Circuito Nuevo", 900)
        val s = vm.state { it.selectedCircuit != null }
        assertEquals("Circuito Nuevo", s.selectedCircuit?.name)
        assertFalse(s.showNewCircuit)
        // Aún no se ha guardado ninguna tanda.
        assertTrue(sessions.observeSummaries().first().isEmpty())
    }

    @Test
    fun volverALaEntrada_descartaLoLeido() = run {
        val vm = viewModel()
        vm.pasteAndAnalyze("1  1:12.467\n2  1:13.001")
        vm.state { it.stage == ImportStage.REVIEW }
        vm.backToEntry()
        val s = vm.state { it.stage == ImportStage.ENTRY }
        assertNull(s.review)
        assertNull(s.parsed)
    }
}
