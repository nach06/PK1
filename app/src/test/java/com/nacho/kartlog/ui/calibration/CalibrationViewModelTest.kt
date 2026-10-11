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
        assertEquals(saved.result!!.finishLine, circuits.getById(saved.currentFinish!!.let { 1L })!!.finishLine)
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
}
