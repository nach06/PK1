package com.nacho.kartlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.ui.detail.SessionDetailViewModel
import com.nacho.kartlog.ui.history.SessionListViewModel
import com.nacho.kartlog.ui.newsession.NewSessionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prueba funcional del flujo del Sprint 1 con ViewModels y Room reales (fichero SQLite):
 * crear circuito → crear tanda → meter 15 vueltas tecleando → "cerrar" la app (cerrar la BD)
 * → reabrir y comprobar que todo persiste.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionFlowTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun <T> withTimeoutBlocking(block: suspend () -> T): T =
        runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun fifteenLapsSurviveAppRestart() = withTimeoutBlocking {
        // --- Primera "ejecución" de la app ---
        var db = KartLogDatabase.create(context)
        val circuits = CircuitRepositoryImpl(db.circuitDao())
        val sessions = SessionRepositoryImpl(db.sessionDao())
        val laps = LapRepositoryImpl(db.lapDao())

        // Alta rápida de circuito y creación de la tanda.
        val newVm = NewSessionViewModel(circuits, sessions)
        newVm.openCircuitDialog()
        newVm.setCircuitName("  Zuera  ")
        newVm.incrementCircuitLength()
        newVm.confirmCircuitDialog()
        val form = newVm.uiState.first { it.circuitId != null && it.circuitDialog == null && it.circuits.isNotEmpty() }
        assertEquals(listOf("Zuera"), form.circuits.map { it.name })
        assertEquals(50, form.circuits.single().lengthMeters)
        newVm.incrementKart()
        newVm.incrementBallast()
        newVm.save()
        val sessionId = newVm.saved.first()

        // Teclear 15 vueltas con el teclado propio.
        val detailVm = SessionDetailViewModel(sessionId, sessions, laps)
        detailVm.uiState.first { it.detail != null }
        val times = (1..15).map { 48_000L + it * 137 }
        times.forEach { ms ->
            val digits = com.nacho.kartlog.domain.util.LapTime.msToDigits(ms)
            digits.forEach { detailVm.onDigits(it.toString()) }
            assertTrue("el tiempo $ms debe ser guardable", detailVm.uiState.first { it.digits == digits }.canSave)
            assertTrue(detailVm.saveLap())
            detailVm.uiState.first { it.digits.isEmpty() } // el campo se limpia solo
        }
        val state = detailVm.uiState.first { it.laps.size == 15 }
        assertEquals((1..15).toList(), state.laps.map { it.number })
        assertEquals(16, state.nextLapNumber) // el número se incrementa solo
        assertEquals(times.min(), state.bestLapMs)
        assertTrue(state.laps.all { it.source == LapSource.MANUAL })

        // Editar y borrar una vuelta.
        val lap3 = state.laps[2]
        detailVm.startEditing(lap3)
        detailVm.onClear()
        "47000".forEach { detailVm.onDigits(it.toString()) }
        detailVm.saveLap()
        detailVm.uiState.first { s -> s.laps.firstOrNull { it.id == lap3.id }?.timeMs == 47_000L }
        val lap15 = state.laps.last()
        detailVm.requestDeleteLap(lap15)
        detailVm.confirmDeleteLap()
        detailVm.uiState.first { it.laps.size == 14 }

        // --- "Cerrar la app" ---
        db.close()

        // --- Segunda ejecución: reabrir la misma BD ---
        db = KartLogDatabase.create(context)
        val listVm = SessionListViewModel(SessionRepositoryImpl(db.sessionDao()))
        val summary = listVm.uiState.first { !it.isLoading && it.sessions.isNotEmpty() }.sessions.single()
        assertEquals("Zuera", summary.circuit.name)
        assertEquals(14, summary.lapCount)
        assertEquals(47_000L, summary.bestLapMs)
        assertEquals(1, summary.session.kartNumber)
        assertEquals(0f, summary.session.ballastKg)

        // Borrar la sesión elimina sus vueltas (cascada).
        val reopened = SessionDetailViewModel(sessionId, SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao()))
        reopened.uiState.first { it.laps.size == 14 }
        reopened.confirmDeleteSession()
        reopened.uiState.first { it.detail == null && it.laps.isEmpty() } // cascada
        assertFalse(listVm.uiState.first { it.sessions.isEmpty() }.sessions.isNotEmpty())
        db.close()
    }

    @Test
    fun invalidSecondsCannotBeSaved() = withTimeoutBlocking {
        val db = KartLogDatabase.create(context)
        val sessions = SessionRepositoryImpl(db.sessionDao())
        val vm = SessionDetailViewModel(1, sessions, LapRepositoryImpl(db.lapDao()))
        vm.uiState.first { !it.isLoading }
        "60000".forEach { vm.onDigits(it.toString()) } // 0:60.000 -> segundos inválidos
        assertTrue(vm.uiState.first { it.digits == "60000" }.timeInvalid)
        assertFalse(vm.saveLap())
        db.close()
    }

    @Test
    fun newSession_temperaturesAreNullUnlessTouched() = withTimeoutBlocking {
        val db = KartLogDatabase.create(context)
        val circuits = CircuitRepositoryImpl(db.circuitDao())
        val sessions = SessionRepositoryImpl(db.sessionDao())
        circuits.add("Zuera", null)
        val vm = NewSessionViewModel(circuits, sessions)
        val initial = vm.uiState.first { it.circuits.isNotEmpty() }
        assertEquals(null, initial.circuitId) // sin tandas previas no se preselecciona nada
        assertEquals(null, initial.ambientTempC)
        assertEquals(null, initial.trackTempC)

        vm.selectCircuit(initial.circuits.single().id)
        vm.setTrackTemp(33)
        vm.save()
        val id = vm.saved.first()
        val detail = sessions.observeDetail(id).first { it != null }!!
        assertEquals(null, detail.session.ambientTempC)
        assertEquals(33f, detail.session.trackTempC)

        vm.clearTrackTemp()
        assertEquals(null, vm.uiState.first { it.trackTempC == null }.trackTempC)
        db.close()
    }

    @Test
    fun newSession_preselectsCircuitOfLastCreatedSession_andOrdersByRecency() = withTimeoutBlocking {
        val db = KartLogDatabase.create(context)
        val circuits = CircuitRepositoryImpl(db.circuitDao())
        val sessions = SessionRepositoryImpl(db.sessionDao())
        val a = circuits.add("Alfa", null)
        val b = circuits.add("Beta", null)
        circuits.add("Cero", null)
        fun newSession(circuitId: Long) = com.nacho.kartlog.domain.model.NewSession(
            circuitId, 1_000, com.nacho.kartlog.domain.model.SessionType.FREE, null, null, null, null,
            com.nacho.kartlog.domain.model.TrackCondition.DRY, "",
        )
        sessions.create(newSession(b))
        sessions.create(newSession(a)) // la última creada es de Alfa

        val vm = NewSessionViewModel(circuits, sessions)
        val state = vm.uiState.first { it.circuitId != null && it.circuits.size == 3 }
        assertEquals(a, state.circuitId)
        assertEquals(listOf("Alfa", "Beta", "Cero"), state.circuits.map { it.name }) // recientes y luego por nombre
        db.close()
    }

    @Test
    fun newSession_dateAndTimeAreEditable() = withTimeoutBlocking {
        val db = KartLogDatabase.create(context)
        val vm = NewSessionViewModel(
            CircuitRepositoryImpl(db.circuitDao()),
            SessionRepositoryImpl(db.sessionDao()),
            zone = java.time.ZoneOffset.UTC,
            now = { 1_700_000_000_000 },
        )
        val utcMidnight = java.time.LocalDate.of(2026, 3, 5).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        vm.setDate(utcMidnight)
        vm.setTime(18, 30)
        val expected = java.time.LocalDateTime.of(2026, 3, 5, 18, 30).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        assertEquals(expected, vm.uiState.first { it.dateTime == expected }.dateTime)
        db.close()
    }
}
