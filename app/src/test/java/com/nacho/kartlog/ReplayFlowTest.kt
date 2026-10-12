package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.runtime.CompositionLocalProvider
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.data.tracking.TrackingController
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackAnalysis
import com.nacho.kartlog.domain.gps.TrackFormats
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
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

/** Sprint 8, B6: «Ensayo en casa» reproduce una tanda guardada como GPS en vivo en el Dashboard y no se guarda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class ReplayFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L)
    private val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
    private val geometry = TrackAnalysis.build(sim.fixes, oval.finishLine)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }


    @Test
    fun rehearsalPlaysTheSavedTrackOnTheDashboard_andIsNotSaved() {
        val container = AppContainer(context)
        container.replaySpeed = 0.0
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        runBlocking {
            val circuit = container.circuitRepository.add("Óvalo simulado", 780)
            container.circuitRepository.setFinishLine(circuit, oval.finishLine)
            val id = container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""))
            container.lapRepository.addAll(id, geometry.laps.map { it.timeMs }, LapSource.GPS)
            container.trackStore.save(id, sim.fixes)
        }
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Óvalo simulado", substring = true)[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("track_canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Más opciones").onLast().performClick()
        compose.onNodeWithText("Ensayo en casa (reproducir)").performClick()

        // El Dashboard se abre ya en marcha, con las vueltas de la tanda reproducidas.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dash_current").fetchSemanticsNodes().isNotEmpty() }
        val deadline = System.currentTimeMillis() + 8_000
        while (container.timingSession.state.value.laps.size < lapTimes.size && System.currentTimeMillis() < deadline) { Thread.sleep(50); compose.waitForIdle() }
        assertEquals(lapTimes.size, container.timingSession.state.value.laps.size)
        assertTrue(container.timingSession.state.value.setup!!.rehearsal)

        compose.onNodeWithTag("debug_finish").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("rehearsal_notice").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("GUARDAR TANDA").assertIsNotEnabled()
        assertEquals(1, runBlocking { container.sessionRepository.observeSummaries().first() }.size)
    }
}
