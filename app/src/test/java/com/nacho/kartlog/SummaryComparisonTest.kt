package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Sprint 6, E1: el resumen al terminar la tanda compara con tu mejor del circuito y trae la gráfica de vueltas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class SummaryComparisonTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()

    private class ControlledSource : LocationSource {
        val flow = MutableSharedFlow<GpsFix>(extraBufferCapacity = 2_000)
        override fun fixes() = flow
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        compose.mainClock.autoAdvance = true
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(30_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun textOf(tag: String) =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    /** Hace una tanda de 3 vueltas (~58 s) en el óvalo y llega al resumen; [previousBestMs] es el récord previo del circuito. */
    private fun runToSummary(previousBestMs: Long?) {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val source = ControlledSource()
        container.locationSource = source
        runBlocking {
            val circuit = container.circuitRepository.add("Óvalo simulado", 780)
            container.circuitRepository.setFinishLine(circuit, oval.finishLine)
            if (previousBestMs != null) {
                val id = container.sessionRepository.create(NewSession(circuit, 1_700_000_000_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""))
                container.lapRepository.addManual(id, previousBestMs)
            }
        }
        val sim = TrackSimulator.simulate(oval, listOf(58_400L, 57_900L, 58_120L), hz = 1.0)
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Tandas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("EMPEZAR").fetchSemanticsNodes().isNotEmpty() }
        // El circuito llega de Room de forma asíncrona: hasta entonces EMPEZAR está desactivado y pulsarlo no hace nada.
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("EMPEZAR")[0].performClick()
        waitForTag("dash_current")
        runBlocking { sim.fixes.filter { it.elapsedNs <= sim.crossingNs[3] + 2_000_000_000L }.forEach { source.flow.emit(it) } }
        compose.waitUntil(30_000) { container.timingSession.state.value.laps.size == 3 }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("finish_hold").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_700)
        // La pulsación larga se anima con el reloj de Compose: si aún no ha dado tiempo a empezar, se sigue avanzando.
        var extra = 0
        while (container.timingSession.state.value.phase != com.nacho.kartlog.domain.gps.TimingPhase.STOPPED && extra++ < 10) {
            Thread.sleep(50)
            compose.mainClock.advanceTimeBy(500)
        }
        compose.mainClock.autoAdvance = true
        compose.onAllNodesWithText("GUARDAR TANDA")[0].performTouchInput { up() }
        waitForTag("summary_compare")
    }

    @Test
    fun withoutHistory_saysItIsTheFirstTime_andShowsTheLapChart() {
        runToSummary(previousBestMs = null)
        assertTrue(textOf("summary_compare").contains("Primera tanda en este circuito"))
        compose.onNodeWithTag("summary_chart").assertIsDisplayed()
    }

    @Test
    fun slowerThanYourBest_showsTheGap() {
        runToSummary(previousBestMs = 57_000)
        val text = textOf("summary_compare")
        assertTrue(text, text.startsWith("Tu mejor del circuito: 0:57.000 · esta tanda +0.9"))
        compose.onNodeWithTag("summary_chart").assertIsDisplayed()
    }

    @Test
    fun fasterThanYourBest_announcesANewRecord() {
        runToSummary(previousBestMs = 59_500)
        val text = textOf("summary_compare")
        assertTrue(text, text.startsWith("¡Nuevo récord del circuito! -1.") && text.contains("antes 0:59.500"))
    }
}
