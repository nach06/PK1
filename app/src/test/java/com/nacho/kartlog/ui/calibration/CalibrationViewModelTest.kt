package com.nacho.kartlog.ui.calibration

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.domain.gps.FinishCalibration
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.ReplayLocationSource
import com.nacho.kartlog.domain.gps.TrackSimulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CalibrationViewModelTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var circuits: CircuitRepositoryImpl
    private var vm: CalibrationViewModel? = null
    private val track = OvalTrack()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        circuits = CircuitRepositoryImpl(db.circuitDao())
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        db.close()
        Dispatchers.resetMain()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun newVm(): CalibrationViewModel {
        val id = circuits.add("Zuera", 1100)
        val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
        return CalibrationViewModel(id, circuits, ReplayLocationSource(sim.fixes, speedFactor = 0.0)).also { vm = it }
    }

    @Test
    fun markShowsTheResult_retryDiscardsIt_andSaveStoresTheFinishInTheCircuit() = run {
        val vm = newVm()
        assertEquals("Zuera", vm.uiState.first { it.circuitName.isNotEmpty() }.circuitName)
        vm.mark() // sin posiciones todavía
        assertEquals(FinishCalibration.Failure.NO_FIXES, vm.uiState.value.failure)

        vm.startListening()
        val live = vm.uiState.first { it.fixCount > 50 }
        assertTrue(live.accuracyM != null && live.rateHz != null)
        assertEquals(1.0, live.rateHz!!, 0.05)

        vm.mark()
        val marked = vm.uiState.value
        assertNotNull(marked.result)
        assertNull(marked.failure)
        assertEquals(87.0, marked.result!!.finishLine.bearingDeg, 1.0)

        vm.retry()
        assertNull(vm.uiState.value.result)

        vm.mark()
        vm.save()
        val saved = vm.uiState.first { it.saved }
        assertNull(saved.result)
        assertEquals(saved.currentFinish, circuits.getById(1L)!!.finishLine)
        assertEquals(87.0, saved.currentFinish!!.bearingDeg, 1.0)
    }

    @Test
    fun withoutPermission_theGpsErrorIsReportedInsteadOfCrashing() = run {
        val id = circuits.add("Zuera", null)
        val denied = object : com.nacho.kartlog.domain.gps.LocationSource {
            override fun fixes() = kotlinx.coroutines.flow.flow<com.nacho.kartlog.domain.gps.GpsFix> { throw SecurityException("sin permiso") }
        }
        val vm = CalibrationViewModel(id, circuits, denied).also { vm = it }
        vm.startListening()
        assertTrue(vm.uiState.first { it.locationError }.locationError)
    }

    private suspend fun savedVm(): Pair<CalibrationViewModel, Long> {
        val id = circuits.add("Zuera", 1100)
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0)
        // La retransmisión termina justo después de la 2.ª pasada por meta: el usuario «marca» ahí.
        val upTo = sim.fixes.filter { it.elapsedNs <= sim.crossingNs[1] + 1_000_000_000L }
        val vm = CalibrationViewModel(id, circuits, ReplayLocationSource(upTo, speedFactor = 0.0)).also { vm = it }
        vm.startListening()
        vm.uiState.first { it.fixCount >= upTo.size }
        vm.mark()
        vm.save()
        vm.uiState.first { it.saved }
        return vm to id
    }

    @Test
    fun manualAdjustment_shiftsAndWidensTheGate_clamped_andPersists() = run {
        val (vm, id) = savedVm()
        val original = circuits.getById(id)!!.finishLine!!
        vm.editSaved()
        assertEquals(original, vm.uiState.value.displayLine)

        repeat(14) { vm.shiftForward() }
        assertEquals("tope de +10 m", 10, vm.uiState.value.shiftM)
        repeat(3) { vm.shiftBackward() }
        assertEquals(7, vm.uiState.value.shiftM)
        repeat(10) { vm.widen() }
        assertEquals("ancho máximo 40 m", 40, vm.uiState.value.gateWidthM)
        repeat(10) { vm.narrow() }
        assertEquals("ancho mínimo 10 m", 10, vm.uiState.value.gateWidthM)
        vm.widen() // 15 m
        assertEquals(15, vm.uiState.value.gateWidthM)

        vm.save()
        vm.uiState.first { it.saved }
        val after = circuits.getById(id)!!.finishLine!!
        assertEquals(15, after.gateWidthM)
        assertEquals(7.0, com.nacho.kartlog.domain.gps.FinishAdjust.alongTrack(original, original.lat, original.lon, after.lat, after.lon), 0.01)
        assertEquals(original.bearingDeg, after.bearingDeg, 0.0)
    }

    @Test
    fun cancellingTheAdjustment_keepsTheSavedLine() = run {
        val (vm, id) = savedVm()
        val original = circuits.getById(id)!!.finishLine
        vm.editSaved()
        repeat(5) { vm.shiftForward() }
        vm.retry() // «Cancelar»
        assertEquals(original, circuits.getById(id)!!.finishLine)
        assertNull(vm.uiState.value.adjustedLine)
        assertEquals(0, vm.uiState.value.shiftM)
    }

    @Test
    fun markingInTheOppositeDirection_warnsThatTheCircuitIsRunInReverse() = run {
        val id = circuits.add("Zuera", 1100)
        // Meta guardada con el rumbo contrario al del paso que se va a marcar.
        circuits.setFinishLine(id, track.finishLine.copy(bearingDeg = 87.0 + 180.0))
        val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
        val upTo = sim.fixes.filter { it.elapsedNs <= sim.crossingNs[1] + 1_000_000_000L }
        val vm = CalibrationViewModel(id, circuits, ReplayLocationSource(upTo, speedFactor = 0.0)).also { vm = it }
        vm.uiState.first { it.currentFinish != null }
        vm.startListening()
        vm.uiState.first { it.fixCount >= upTo.size }
        vm.mark()
        assertTrue("debe avisar del sentido contrario", vm.uiState.value.reversed)
        vm.save()
        vm.uiState.first { it.saved }
        assertEquals(87.0, circuits.getById(id)!!.finishLine!!.bearingDeg, 1.0) // se sustituye, sin columnas nuevas
    }

    @Test
    fun verification_confirmsAGoodLine_andReportsNoCrossingOtherwise() = run {
        val (vm, id) = savedVm()
        vm.startVerification()
        vm.uiState.first { it.verifying && it.fixCount > 50 }
        vm.confirmVerification()
        val ok = vm.uiState.value.verification as com.nacho.kartlog.domain.gps.FinishVerification.Outcome.Checked
        assertTrue("meta bien puesta: ${ok}", ok.ok)
        assertEquals(87.0, ok.travelBearingDeg, 1.0)

        // Con la puerta lejos del circuito no hay cruce.
        circuits.setFinishLine(id, circuits.getById(id)!!.finishLine!!.copy(lat = 42.5))
        val far = CalibrationViewModel(id, circuits, ReplayLocationSource(TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0).fixes, 0.0)).also { vm.viewModelScope.cancel() ; this@CalibrationViewModelTest.vm = it }
        far.uiState.first { it.currentFinish != null }
        far.startVerification()
        far.uiState.first { it.verifying && it.fixCount > 50 }
        far.confirmVerification()
        assertEquals(com.nacho.kartlog.domain.gps.FinishVerification.Outcome.NoCrossing, far.uiState.value.verification)
    }
}
