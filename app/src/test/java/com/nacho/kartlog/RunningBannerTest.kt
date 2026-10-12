package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** Sprint 6, A3 + A4: aviso persistente «Tanda en pista» en las pestañas y confirmación al pulsar atrás en marcha. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class RunningBannerTest {
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
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun waitFor(text: String, ignoreCase: Boolean = true) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    private fun back() = compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun backAsksForConfirmation_leavingKeepsTheSessionRunning_andABannerLetsYouReturn() {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val source = ControlledSource()
        container.locationSource = source
        runBlocking { container.circuitRepository.add("Óvalo simulado", 780).also { container.circuitRepository.setFinishLine(it, oval.finishLine) } }
        val sim = TrackSimulator.simulate(oval, listOf(58_400L, 57_900L), hz = 1.0)
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }

        waitFor("Tandas")
        assertTrue("sin tanda en marcha no hay aviso", compose.onAllNodesWithTag("running_banner").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        waitFor("EMPEZAR")
        // El circuito llega de Room de forma asíncrona: hasta entonces EMPEZAR está desactivado y pulsarlo no hace nada.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        // A7: la ficha del tipo por defecto aparece seleccionada, y sin kart (valor «–») se puede empezar igualmente.
        compose.onNodeWithText("Libre").assertIsSelected()
        compose.onNodeWithText("Carrera").assertIsNotSelected()
        compose.onNodeWithText("Carrera").performClick()
        compose.onNodeWithText("Carrera").assertIsSelected()
        compose.onNodeWithText("Libre").assertIsNotSelected()
        compose.onAllNodesWithText("EMPEZAR")[0].assertIsEnabled()
        compose.onAllNodesWithText("EMPEZAR")[0].performClick()
        waitForTag("dash_current")
        runBlocking { sim.fixes.filter { it.elapsedNs <= sim.crossingNs[2] + 2_000_000_000L }.forEach { source.flow.emit(it) } }
        compose.waitUntil(10_000) { container.timingSession.state.value.laps.size == 2 }

        // Atrás: pregunta; «Seguir en pista» se queda.
        back()
        waitForTag("leave_title")
        compose.onNodeWithTag("leave_stay").performClick()
        gone("leave_title")
        compose.onNodeWithTag("dash_current").assertIsDisplayed()

        // Atrás + «Salir sin terminar»: vuelve a Tandas con la tanda corriendo y el aviso encima de la barra.
        back()
        waitForTag("leave_title")
        compose.onNodeWithTag("leave_exit").performClick()
        waitFor("Tandas")
        waitForTag("running_banner")
        compose.onNodeWithTag("running_banner").assertIsDisplayed()
        assertEquals(TimingPhase.RUNNING, container.timingSession.state.value.phase)
        val text = compose.onNodeWithTag("running_banner_text", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString("") { it.text }
        assertTrue("el aviso dice vuelta y mejor: $text", text.contains("Tanda en pista · Vuelta 3 · mejor 0:57."))

        // También en otras pestañas; tocarlo vuelve al Dashboard.
        compose.onAllNodesWithText("Análisis")[0].performClick()
        waitForTag("running_banner")
        compose.onNodeWithTag("running_banner").performClick()
        waitForTag("dash_current")
        assertTrue(compose.onAllNodesWithTag("running_banner").fetchSemanticsNodes().isEmpty())
    }
}
