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
import com.nacho.kartlog.domain.importing.ImportInbox
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

internal class FakeSettings(name: String) : UserSettings {
    private val state = MutableStateFlow(name)
    override val driverName: StateFlow<String> = state
    override fun setDriverName(name: String) {
        state.value = name
    }
}

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

    private fun viewModel(driverName: String = "", recognizer: ImageTextRecognizer? = null): ImportViewModel =
        ImportViewModel(
            circuits, sessions, laps, ImportRepositoryImpl(db.importDao()), FakeSettings(driverName), inbox, recognizer,
            zone = ZoneId.of("UTC"), now = { 1_760_000_000_000 }, participantRepository = participants,
        ).also { vm = it }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun ImportViewModel.state(predicate: (ImportUiState) -> Boolean = { true }) = uiState.first(predicate)

    @Test
    fun muestraA_seleccionaPorNombre_validaYGuardaTodo() = run {
        val vm = viewModel(driverName = "nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt"))
        val s = vm.state { it.me != null }

        // «Yo» preseleccionado por «Mi nombre de piloto» (sin distinguir mayúsculas).
        assertEquals("Nacho", s.me?.name)
        assertEquals(listOf(83488L, 80902L, 82209L, 81767L, 79074L, 79505L), s.laps.map { it.timeMs })
        assertTrue(s.checks!!.allOk)
        assertEquals(CheckState.OK, s.checks!!.average)
        // Tanda nueva rellenada desde el documento.
        assertEquals(28, s.kartNumber)
        assertEquals("Sesión 46 GP3 DIEGO", s.label)
        assertEquals(10, s.durationMin)
        assertEquals(SessionType.RACE, s.type)
        assertEquals(LocalDateTime.of(2026, 10, 3, 20, 16), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(s.dateTime), ZoneId.of("UTC")))
        // Sin circuito reconocido no se puede guardar: hay que elegirlo en la vista previa.
        assertNull(s.circuitId)
        assertFalse(s.canSave)

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
        // Se guarda el texto original (nunca la foto).
        db.sessionDao().observeById(sessionId).first()!!.session.importedRaw!!.let { assertTrue("Sesión 46 GP3 DIEGO" in it) }
    }

    @Test
    fun muestraB_sinNombres_seEligePorKart_yElCircuitoSeReconoce() = run {
        circuits.add("Asupark", null)
        val vm = viewModel(driverName = "Nacho") // el documento no trae nombres: no hay coincidencia
        vm.pasteAndAnalyze(fixture("muestra_b_asupark.txt"))
        var s = vm.state { it.parsed != null }
        assertNull(s.me)
        assertFalse(s.canSave) // nada se guarda sin pasar por la vista previa
        assertEquals(listOf("Kart 26", "Kart 28", "Kart 50", "Kart 54"), s.parsed!!.drivers.map { it.displayName })

        val kart28 = s.parsed!!.drivers.first { it.kartNumber == 28 }
        vm.selectDriver(kart28.key)
        s = vm.state { it.me != null }
        assertEquals(9, s.laps.size)
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
        assertEquals(3, s.laps.size)
        assertEquals("Piloto 1", s.me!!.displayName)
    }

    @Test
    fun celdaAlterada_seMarcaAmbar_seProponeYSoloSeAplicaAlPedirlo() = run {
        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt").replace("01:23.488", "01:23.438"))
        var s = vm.state { it.me != null }
        assertEquals(CheckState.WARN, s.checks!!.average)
        assertEquals(83438L, s.laps.first { it.number == 1 }.timeMs) // no se corrigió sola
        assertTrue(s.laps.first { it.number == 1 }.suspect)
        assertEquals(83488L, s.checks!!.suggestion?.proposedMs)

        vm.applySuggestion()
        s = vm.state { it.checks?.average == CheckState.OK }
        assertEquals(83488L, s.laps.first { it.number == 1 }.timeMs)
        assertTrue(s.laps.first { it.number == 1 }.edited)
        assertFalse(s.laps.any { it.suspect })
    }

    @Test
    fun editarConElTeclado_descartarYRecuperarVueltas() = run {
        val vm = viewModel(driverName = "Nacho")
        vm.pasteAndAnalyze(fixture("muestra_a_correo_tabs.txt"))
        vm.state { it.me != null }

        vm.startEdit(2)
        vm.editClear()
        "121000".forEach { vm.editDigits(it.toString()) }
        assertEquals("1:21.000", vm.state { it.editing?.digits == "121000" }.editing!!.timeText)
        vm.confirmEdit()
        var s = vm.state { it.editing == null }
        assertEquals(81_000L, s.laps.first { it.number == 2 }.timeMs)
        assertEquals(CheckState.WARN, s.checks!!.average) // al cambiar una vuelta ya no cuadra la media del documento

        vm.toggleLap(6)
        s = vm.state { !it.laps.first { l -> l.number == 6 }.included }
        assertEquals(5, s.includedLaps.size)
        assertEquals(CheckState.WARN, s.checks!!.lapCount)
        vm.toggleLap(6)
        assertEquals(6, vm.state { it.laps.all { l -> l.included } }.includedLaps.size)

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
    fun sinVueltasIncluidas_noSePuedeGuardar() = run {
        circuits.add("Asupark", null)
        val vm = viewModel()
        vm.pasteAndAnalyze("Asupark\n1  1:12.467\n2  1:13.001")
        vm.state { it.me != null }
        assertTrue(vm.state().canSave)
        vm.toggleLap(1)
        vm.toggleLap(2)
        assertFalse(vm.state { it.includedLaps.isEmpty() }.canSave)
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
        val s = vm.state { it.destination == ImportDestination.EXISTING_SESSION && it.laps.any { l -> l.possibleDuplicate } }
        assertEquals(listOf(1), s.laps.filter { it.possibleDuplicate }.map { it.number })
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
        assertEquals(6, s.laps.size)
        assertNull(inbox.content.value) // el buzón queda vacío
    }

    @Test
    fun contenidoCompartido_imagen_pasaPorElOcr() = run {
        val ocr = object : ImageTextRecognizer {
            var requested: String? = null
            override suspend fun recognize(imageUri: String): Result<String> {
                requested = imageUri
                return Result.success("1  1:12.467\n2  1:13.001")
            }
        }
        val vm = viewModel(recognizer = ocr)
        inbox.put(SharedContent.Image("content://foto/1"))
        val s = vm.state { it.me != null }
        assertEquals("content://foto/1", ocr.requested)
        assertEquals(2, s.laps.size)
    }

    @Test
    fun errores_textoVacio_sinVueltas_yOcrFallido() = run {
        val vm = viewModel()
        vm.analyze()
        assertEquals(ImportError.EMPTY_TEXT, vm.state { it.error != null }.error)
        vm.pasteAndAnalyze("hola, esto no tiene tiempos")
        assertEquals(ImportError.NOTHING_FOUND, vm.state { it.error == ImportError.NOTHING_FOUND }.error)
        vm.recognizeImage("content://x") // sin motor de OCR configurado
        assertEquals(ImportError.OCR_FAILED, vm.state { it.error == ImportError.OCR_FAILED }.error)
    }

    @Test
    fun crearCircuitoDesdeLaVistaPrevia_loSeleccionaYNoGuardaNadaAntesDeTiempo() = run {
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
}
