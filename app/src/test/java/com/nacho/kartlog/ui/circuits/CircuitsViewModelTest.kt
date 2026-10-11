package com.nacho.kartlog.ui.circuits

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.analysis.AnalysisSelection
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
class CircuitsViewModelTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var circuits: CircuitRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var selection: AnalysisSelection
    private lateinit var vm: CircuitsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        circuits = CircuitRepositoryImpl(db.circuitDao())
        sessions = SessionRepositoryImpl(db.sessionDao())
        selection = AnalysisSelection()
        vm = CircuitsViewModel(circuits, AnalyticsSource(sessions, LapRepositoryImpl(db.lapDao())), selection)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun newSession(circuitId: Long) = sessions.create(
        NewSession(circuitId, 1_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""),
    )

    @Test
    fun deleteWithSessions_isBlockedAndNothingIsDeleted() = run {
        val zuera = circuits.add("Zuera", 1100)
        newSession(zuera)
        newSession(zuera)
        val row = vm.uiState.first { it.rows.isNotEmpty() && it.rows.single().totalSessions == 2 }.rows.single()

        vm.requestDelete(row)
        val state = vm.uiState.first { it.deleteBlocked != null }
        assertEquals(DeleteBlocked("Zuera", 2), state.deleteBlocked)
        assertNull(state.pendingDelete)
        assertFalse(circuits.delete(zuera)) // el repositorio también lo impide
        assertEquals(1, circuits.observeAll().first().size)
    }

    @Test
    fun deleteWithoutSessions_asksConfirmationThenDeletes() = run {
        circuits.add("Vacío", null)
        val row = vm.uiState.first { it.rows.isNotEmpty() }.rows.single()
        assertEquals(0, row.totalSessions)

        vm.requestDelete(row)
        assertEquals("Vacío", vm.uiState.first { it.pendingDelete != null }.pendingDelete!!.name)
        vm.confirmDelete()
        assertTrue(vm.uiState.first { it.rows.isEmpty() }.rows.isEmpty())
    }

    @Test
    fun edit_changesNameAndLength_andRejectsDuplicates() = run {
        circuits.add("Alfa", null)
        val beta = circuits.add("Beta", 800)
        val rows = vm.uiState.first { it.rows.size == 2 }.rows
        val betaCircuit = rows.first { it.circuit.id == beta }.circuit

        vm.startEdit(betaCircuit)
        vm.setName("Alfa")
        vm.confirmEdit()
        assertEquals(CircuitNameError.DUPLICATE, vm.uiState.first { it.editing?.error != null }.editing!!.error)

        vm.setName("   ")
        vm.confirmEdit()
        assertEquals(CircuitNameError.BLANK, vm.uiState.first { it.editing?.error == CircuitNameError.BLANK }.editing!!.error)

        vm.setName("Gamma")
        vm.incrementLength() // 800 -> 850
        vm.confirmEdit()
        val updated = vm.uiState.first { it.editing == null && it.rows.any { r -> r.circuit.name == "Gamma" } }
        assertEquals(850, updated.rows.first { it.circuit.id == beta }.circuit.lengthMeters)
    }

    @Test
    fun tappingCircuit_selectsItForAnalysis() = run {
        val id = circuits.add("Zuera", null)
        assertNull(selection.circuitId.value)
        vm.selectForAnalysis(id)
        assertNotNull(selection.circuitId.value)
        assertEquals(id, selection.circuitId.value)
    }
}
