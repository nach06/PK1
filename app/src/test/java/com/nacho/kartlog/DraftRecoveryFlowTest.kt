package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.gps.FileDraftStore
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Si la app murió a mitad de una tanda GPS, al abrirla se ofrece recuperarla o descartarla (B2), por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class DraftRecoveryFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L, 59_300L)
    private val startedAt = 1_760_000_000_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun draftFiles() = listOf("draft.partial.json", "draft.partial.csv").map { File(context.filesDir, "tracks/$it") }

    private fun waitFor(text: String, ignoreCase: Boolean = true) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()
    }

    /** Deja en disco lo que habría quedado tras morir la app: la preparación y todas las posiciones. */
    private fun writeDraft(container: AppContainer, circuitId: Long) = runBlocking {
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
        val store = FileDraftStore(File(context.filesDir, "tracks"))
        store.begin(GpsSessionSetup(circuitId, oval.finishLine, kartNumber = 9, type = SessionType.RACE, startedAtMs = startedAt))
        store.append(sim.fixes)
    }

    private fun content(container: AppContainer) {
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
    }

    @Test
    fun recoverTheInterruptedSession_andSaveItWithItsOriginalDate() {
        val container = AppContainer(context)
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        writeDraft(container, circuitId)
        content(container)

        waitFor("Tanda interrumpida")
        compose.onNodeWithText("RECUPERAR TANDA INTERRUMPIDA").performClick()

        // El resumen muestra las 4 vueltas recalculadas con las posiciones guardadas.
        waitFor("Tanda terminada")
        compose.onNodeWithTag("summary_laps").assertTextEquals("4")
        compose.onNodeWithText("GUARDAR TANDA").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Cronometrada por GPS").fetchSemanticsNodes().isNotEmpty() }

        val summary = runBlocking { container.sessionRepository.observeSummaries().first().single() }
        assertEquals(startedAt, summary.session.dateTime)
        assertEquals(SessionType.RACE, summary.session.type)
        assertEquals(9, summary.session.kartNumber)
        val laps = runBlocking { container.lapRepository.observeBySession(summary.session.id).first() }
        assertTrue(laps.all { it.source == LapSource.GPS })
        laps.forEachIndexed { i, lap -> assertEquals("V${i + 1}", lapTimes[i].toDouble(), lap.timeMs.toDouble(), 20.0) }
        // Guardada la tanda, el borrador desaparece (y no vuelve a ofrecerse).
        val deadline = System.currentTimeMillis() + 8_000
        while (draftFiles().any { it.exists() } && System.currentTimeMillis() < deadline) { Thread.sleep(100); compose.waitForIdle() }
        assertFalse(draftFiles().any { it.exists() })
        assertEquals(TimingPhase.IDLE, container.timingSession.state.value.phase)
    }

    @Test
    fun discardingTheDraft_deletesIt_andStaysOnTheList() {
        val container = AppContainer(context)
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        writeDraft(container, circuitId)
        assertTrue(draftFiles().all { it.exists() })
        content(container)

        waitFor("Tanda interrumpida")
        compose.onNodeWithText("DESCARTAR").performClick()
        val deadline = System.currentTimeMillis() + 8_000
        while (draftFiles().any { it.exists() } && System.currentTimeMillis() < deadline) { Thread.sleep(100); compose.waitForIdle() }
        assertFalse("el borrador descartado se borra", draftFiles().any { it.exists() })
        assertEquals(TimingPhase.IDLE, container.timingSession.state.value.phase)
        assertTrue(compose.onAllNodesWithText("Tanda interrumpida").fetchSemanticsNodes().isEmpty())
        assertTrue(runBlocking { container.sessionRepository.observeSummaries().first() }.isEmpty())
    }

    @Test
    fun noDraft_noDialog_andADraftOfADeletedCircuitIsDroppedSilently() {
        val container = AppContainer(context)
        writeDraft(container, circuitId = 999) // el circuito no existe
        content(container)
        waitFor("Tandas")
        compose.waitForIdle()
        Thread.sleep(500)
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Tanda interrumpida").fetchSemanticsNodes().isEmpty())
        val deadline = System.currentTimeMillis() + 8_000
        while (draftFiles().any { it.exists() } && System.currentTimeMillis() < deadline) { Thread.sleep(100); compose.waitForIdle() }
        assertFalse(draftFiles().any { it.exists() })
    }

    @Test
    fun draftWithoutLaps_isDiscardedAutomatically_withANotice_andLeavesNothingDangling() {
        val container = AppContainer(context)
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        runBlocking {
            val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
            val store = FileDraftStore(File(context.filesDir, "tracks"))
            store.begin(GpsSessionSetup(circuitId, oval.finishLine, kartNumber = 9, type = SessionType.RACE, startedAtMs = startedAt))
            store.append(sim.fixes.take(30)) // menos de una vuelta
        }
        content(container)

        waitFor("Tanda interrumpida sin vueltas: descartada")
        assertTrue(compose.onAllNodesWithText("Tanda interrumpida").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Tanda terminada", substring = true).fetchSemanticsNodes().isEmpty())
        val deadline = System.currentTimeMillis() + 8_000
        while (draftFiles().any { it.exists() } && System.currentTimeMillis() < deadline) { Thread.sleep(100); compose.waitForIdle() }
        assertFalse(draftFiles().any { it.exists() })
        assertEquals(TimingPhase.IDLE, container.timingSession.state.value.phase)
    }
}
