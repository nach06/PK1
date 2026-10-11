package com.nacho.kartlog.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.ParticipantRepositoryImpl
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

    private class FakeDebugTools(val samples: List<String>) : com.nacho.kartlog.debug.DebugTools {
        override suspend fun seed() = 0
        override suspend fun wipeAll() = Unit
        override fun sampleNames() = samples
        override suspend fun prepareSimulation(): com.nacho.kartlog.domain.gps.LocationSource =
            com.nacho.kartlog.domain.gps.ReplayLocationSource(emptyList())
        override suspend fun prepareSample(name: String) = "file:///cache/photos/$name"
    }

    private fun viewModelWithSamples(samples: List<String>, inbox: com.nacho.kartlog.domain.importing.ImportInbox): SettingsViewModel {
        val exporter = SessionsCsvExporter(SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao()))
        return SettingsViewModel(FakeDebugTools(samples), exporter, com.nacho.kartlog.ui.importing.FakeSettings(""), inbox)
    }

    @Test
    fun testOcr_withoutSamples_tellsWhereToPutThem() = run {
        val inbox = com.nacho.kartlog.domain.importing.ImportInbox()
        val vm = viewModelWithSamples(emptyList(), inbox)
        vm.testOcrWithSample()
        assertEquals(SettingsMessage.SAMPLES_MISSING, vm.uiState.first { it.message != null }.message)
        assertNull(inbox.content.value)
    }

    @Test
    fun testOcr_withOneSample_sendsItThroughTheRealScanFlow() = run {
        val inbox = com.nacho.kartlog.domain.importing.ImportInbox()
        viewModelWithSamples(listOf("muestra_a.png"), inbox).testOcrWithSample()
        assertEquals(
            com.nacho.kartlog.domain.importing.SharedContent.Image("file:///cache/photos/muestra_a.png"),
            inbox.content.first { it != null },
        )
    }

    @Test
    fun testOcr_withSeveralSamples_asksWhichOne() = run {
        val inbox = com.nacho.kartlog.domain.importing.ImportInbox()
        val vm = viewModelWithSamples(listOf("muestra_a.png", "muestra_b.jpg"), inbox)
        vm.testOcrWithSample()
        assertEquals(listOf("muestra_a.png", "muestra_b.jpg"), vm.uiState.first { it.sampleChoices.isNotEmpty() }.sampleChoices)
        assertNull(inbox.content.value)
        vm.runSample("muestra_b.jpg")
        assertEquals(
            com.nacho.kartlog.domain.importing.SharedContent.Image("file:///cache/photos/muestra_b.jpg"),
            inbox.content.first { it != null },
        )
        assertTrue(vm.uiState.value.sampleChoices.isEmpty())
    }

    @Test
    fun exportAll_neverIncludesRivalData_andClearRivalDataKeepsMyLaps() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Asupark", null)
        val importer = com.nacho.kartlog.data.repository.ImportRepositoryImpl(db.importDao())
        val sessionId = importer.importSession(
            com.nacho.kartlog.domain.model.ImportRequest(
                newSession = NewSession(circuit, 1_000, SessionType.FREE, 28, null, null, null, TrackCondition.DRY, "", importedRaw = "RIVAL_SECRETO"),
                existingSessionId = null,
                myLapTimesMs = listOf(80_000, 81_000),
                me = com.nacho.kartlog.domain.model.ParticipantImport(2, "Nacho", 28, 80_000, 80_500, 2),
                others = listOf(com.nacho.kartlog.domain.model.ParticipantImport(1, "Rival Uno", 7, 70_123, 71_000, 2, listOf(70_123, 71_877))),
            ),
        )
        val vm = SettingsViewModel(
            null, SessionsCsvExporter(SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao())),
            com.nacho.kartlog.ui.importing.FakeSettings(""), importRepository = importer, now = { 1_760_000_000_000 },
        )
        vm.exportAll()
        val csv = withTimeout(10_000) { vm.shareFiles.first() }.content
        assertTrue(csv.contains("80.000") || csv.contains("1:20.000") || csv.contains("80000"))
        listOf("Rival Uno", "RIVAL_SECRETO", "70.123", "1:10.123", "70123").forEach { assertTrue("el CSV no debe contener «$it»", !csv.contains(it)) }

        vm.clearRivalData()
        assertEquals(SettingsMessage.RIVALS_CLEARED, vm.uiState.first { it.message != null }.message)
        assertEquals(0, ParticipantRepositoryImpl(db.participantDao()).observeBySession(sessionId).first().size)
        assertEquals(2, LapRepositoryImpl(db.lapDao()).observeBySession(sessionId).first().size)
    }

    private fun viewModel(): SettingsViewModel {
        val exporter = SessionsCsvExporter(SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao()))
        return SettingsViewModel(debugTools = null, exporter = exporter, userSettings = com.nacho.kartlog.ui.importing.FakeSettings(""), now = { 1_760_000_000_000 })
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
