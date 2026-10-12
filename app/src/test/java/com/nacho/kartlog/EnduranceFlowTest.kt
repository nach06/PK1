package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Resistencia: EN PISTA → Resistencia → pit a mitad de tanda → guardar → Detalle con «Stints · 2» (B4), por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class EnduranceFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L, 59_300L, 57_650L)

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
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun waitFor(text: String, ignoreCase: Boolean = true, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) { Thread.sleep(50); compose.waitForIdle() }
        check(condition()) { "no se cumplió la condición a tiempo" }
    }

    @Test
    fun aPitStopSplitsTheSessionIntoTwoStints_andTheDetailListsThem() {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val source = ControlledSource()
        container.locationSource = source
        runBlocking { container.circuitRepository.add("Óvalo simulado", 780).also { container.circuitRepository.setFinishLine(it, oval.finishLine) } }
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }

        waitFor("Tandas")
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        waitFor("Óvalo simulado", substring = true)
        compose.onNodeWithText("Resistencia").performClick()
        compose.onNodeWithText("EMPEZAR").performClick()
        waitForTag("dash_current")
        check(compose.onAllNodesWithTag("pit_button").fetchSemanticsNodes().isNotEmpty()) { "el botón de pit aparece en Resistencia" }

        // Tres vueltas cerradas; después, «Pit / cambio».
        runBlocking { sim.fixes.filter { it.elapsedNs <= sim.crossingNs[3] + 5_000_000_000L }.forEach { source.flow.emit(it) } }
        until { container.timingSession.state.value.laps.size == 3 }
        compose.onNodeWithTag("pit_button").performClick()
        waitForTag("pit_confirm")
        compose.onNodeWithTag("pit_confirm").performClick()
        waitForTag("pit_pending")
        compose.onNodeWithTag("pit_pending").assertIsDisplayed()

        // El resto de la tanda: el 4.º cruce cierra el tiempo en boxes y el 5.º, una vuelta del stint 2.
        runBlocking { sim.fixes.filter { it.elapsedNs > sim.crossingNs[3] + 5_000_000_000L }.forEach { source.flow.emit(it) } }
        until { container.timingSession.state.value.stints.size == 2 && container.timingSession.state.value.laps.size == 4 }
        assertTrue(compose.onAllNodesWithTag("pit_pending").fetchSemanticsNodes().isEmpty())

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("finish_hold").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_700)
        compose.mainClock.autoAdvance = true
        waitFor("Tanda terminada")
        compose.onNodeWithText("GUARDAR TANDA").performTouchInput { up() }
        compose.onNodeWithText("GUARDAR TANDA").performClick()

        // Detalle: tarjeta «Stints · 2» al final de la lista, con el tiempo total en boxes.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("stints_card"))
        waitFor("Stints · 2", substring = true)
        compose.onNodeWithTag("stints_pit_total", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("stints_card").performClick() // desplegar
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("stint_row_1"))
        compose.onNodeWithTag("stint_row_0").assertIsDisplayed()
        waitFor("V1–V3 · 3 vueltas")
        waitFor("V4–V4 · 1 vueltas")

        val summary = runBlocking { container.sessionRepository.observeSummaries().first().single() }
        assertEquals(SessionType.ENDURANCE, summary.session.type)
        val stints = runBlocking { container.stintRepository.observeBySession(summary.session.id).first() }
        assertEquals(listOf(1 to 3, 4 to 4), stints.map { it.startLap to it.endLap })
        assertEquals(lapTimes[3].toDouble(), stints[0].pitMs!!.toDouble(), 30.0)
        val laps = runBlocking { container.lapRepository.observeBySession(summary.session.id).first() }
        assertEquals(listOf(stints[0].id, stints[0].id, stints[0].id, stints[1].id), laps.map { it.stintId })

        // Sprint 6, A7: en Tandas la card lleva el icono GPS y «2 stints» en el subtítulo.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("card_gps_icon", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        waitFor("2 stints", substring = true)
        waitFor("Resistencia", substring = true)
    }
}
