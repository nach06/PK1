package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.ReplayLocationSource
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
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

/** Tandas → EN PISTA → (simulador) → pulsación larga → resumen → guardar → Detalle con vueltas GPS, por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class TrackFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L, 59_300L, 57_650L, 58_030L)

    private class FakeController : TrackingController {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
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
        Thread.sleep(300) // deja terminar las consultas Room en vuelo antes de borrar la BD
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun waitFor(text: String, ignoreCase: Boolean = true, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun simulatedSession_endsInTheDetailWithGpsLaps_andASavedTrack() {
        val container = AppContainer(context)
        val controller = FakeController()
        container.trackingController = controller
        container.locationSource = ReplayLocationSource(TrackSimulator.simulate(oval, lapTimes, hz = 1.0).fixes, speedFactor = 0.0)
        val circuitId = runBlocking {
            container.circuitRepository.add("Óvalo simulado", 780).also { container.circuitRepository.setFinishLine(it, oval.finishLine) }
        }
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }

        waitFor("Tandas")
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()

        // Preparación: circuito elegido por defecto (el primero con meta) y EMPEZAR disponible.
        waitFor("EMPEZAR")
        waitFor("Óvalo simulado", substring = true)
        compose.onNodeWithText("Óvalo simulado", substring = true, ignoreCase = true).assertIsDisplayed()
        // A6: tipo de tanda elegible (Libre por defecto) y fecha = hora de EMPEZAR.
        listOf("Libre", "Clasificación", "Carrera", "Resistencia").forEach { check(compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty()) { "falta la ficha $it" } }
        compose.onNodeWithText("Carrera").performClick()
        val startedAt = 1_760_000_000_000L
        var clock = startedAt
        container.wallClockMs = { clock }
        compose.onNodeWithText("EMPEZAR").performClick()

        // En marcha: vuelta actual, última, mejor y delta con los tiempos simulados; servicio arrancado.
        waitForTag("dash_current")
        compose.onNodeWithTag("dash_gps").assertIsDisplayed()
        compose.waitUntil(10_000) { container.timingSession.state.value.laps.size == lapTimes.size }
        assertEquals(1, controller.starts)
        waitFor("VUELTA 7", substring = true)
        val best = container.timingSession.state.value.bestLapMs!!
        assertTrue("mejor ≈ 57,650 s: $best", kotlin.math.abs(best - 57_650) <= 20)

        clock = startedAt + 47 * 60_000L // la tanda dura 47 min
        // Terminar exige mantener pulsado 1,5 s: un toque corto no hace nada.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("finish_hold").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(TimingPhase.RUNNING, container.timingSession.state.value.phase)
        compose.onNodeWithTag("finish_hold").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_700) // al cumplirse 1,5 s se para solo, sin soltar
        compose.mainClock.autoAdvance = true
        // El dedo sigue «apoyado» en el test aunque el botón ya no exista: se suelta para poder seguir pulsando.
        compose.onNodeWithText("GUARDAR TANDA").performTouchInput { up() }

        // Resumen y guardado.
        waitFor("Tanda terminada")
        compose.onNodeWithTag("summary_laps").assertIsDisplayed()
        assertEquals(1, controller.stops)
        clock = startedAt + 3 * 3_600_000L // se guarda 3 h después: la fecha sigue siendo la de salida
        compose.onNodeWithText("GUARDAR TANDA").performClick()

        // Detalle: las vueltas llevan el icono de GPS y se guardó el trazado.
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Cronometrada por GPS").fetchSemanticsNodes().isNotEmpty() }
        val sessions = runBlocking { container.sessionRepository.observeSummaries().first() }
        assertEquals(1, sessions.size)
        val sessionId = sessions.single().session.id
        assertEquals(startedAt, sessions.single().session.dateTime)
        assertEquals(com.nacho.kartlog.domain.model.SessionType.RACE, sessions.single().session.type)
        assertEquals(47, sessions.single().session.durationMin)
        val laps = runBlocking { container.lapRepository.observeBySession(sessionId).first() }
        assertEquals(lapTimes.size, laps.size)
        assertTrue(laps.all { it.source == LapSource.GPS })
        laps.forEachIndexed { i, lap -> assertEquals("V${i + 1}", lapTimes[i].toDouble(), lap.timeMs.toDouble(), 20.0) }
        assertTrue("trazado en tracks/$sessionId.csv", File(context.filesDir, "tracks/$sessionId.csv").exists())
        assertEquals(TimingPhase.IDLE, container.timingSession.state.value.phase)
        assertEquals(circuitId, sessions.single().circuit.id)
    }

    @Test
    fun circuitWithoutFinish_cannotStart_andOffersToMarkIt() {
        val container = AppContainer(context)
        container.trackingController = FakeController()
        runBlocking { container.circuitRepository.add("Sin meta", null) }
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        waitFor("Tandas")
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        waitFor("EMPEZAR")
        // Sin circuito con meta no hay ninguno elegido por defecto: se elige y aparece el aviso con acceso a Marcar meta.
        compose.onNodeWithText("Elige circuito", substring = true, ignoreCase = true).performClick()
        waitFor("Sin meta")
        compose.onAllNodesWithText("Sin meta", ignoreCase = true)[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        waitForTag("needs_finish")
        compose.onNodeWithText("MARCAR META").performClick()
        waitFor("Pasa por la línea de meta a velocidad normal y pulsa MARCAR.")
    }
}
