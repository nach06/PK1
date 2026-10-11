package com.nacho.kartlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.AllSessionsCsv
import com.nacho.kartlog.domain.util.SessionsCsvExporter
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
class SettingsViewModelTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private fun viewModel(): SettingsViewModel {
        val exporter = SessionsCsvExporter(SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao()))
        return SettingsViewModel(debugTools = null, exporter = exporter, now = { 1_760_000_000_000 })
    }

    @Test
    fun exportAll_withoutLaps_reportsNothingToExport() = run {
        val vm = viewModel()
        vm.exportAll()
        assertEquals(SettingsMessage.NOTHING_TO_EXPORT, vm.uiState.first { it.message != null }.message)
    }

    @Test
    fun exportAll_emitsOneCsvWithAllLaps() = run {
        val circuitId = CircuitRepositoryImpl(db.circuitDao()).add("Zuera", null)
        val sessions = SessionRepositoryImpl(db.sessionDao())
        val laps = LapRepositoryImpl(db.lapDao())
        val s1 = sessions.create(NewSession(circuitId, 1_000, SessionType.FREE, 3, null, null, null, TrackCondition.DRY, ""))
        val s2 = sessions.create(NewSession(circuitId, 2_000, SessionType.RACE, 5, null, null, null, TrackCondition.DAMP, ""))
        laps.addManual(s1, 50_000)
        laps.addManual(s1, 51_000)
        laps.addManual(s2, 49_000)

        val vm = viewModel()
        vm.exportAll()
        val file = vm.shareFiles.first()
        assertTrue(file.fileName.startsWith("kartlog_todo_") && file.fileName.endsWith(".csv"))
        assertEquals(AllSessionsCsv.HEADER, file.content.lines().first())
        assertEquals(4, file.content.lines().filter { it.isNotBlank() }.size) // cabecera + 3 vueltas
        assertNull(vm.uiState.value.message)
        assertTrue(!vm.uiState.value.debugToolsAvailable) // sin herramientas debug no se muestran
    }
}
