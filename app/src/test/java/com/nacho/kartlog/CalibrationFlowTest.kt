package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.ReplayLocationSource
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Circuitos → ⋮ → Marcar meta → permiso → MARCAR → GUARDAR, por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class CalibrationFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun waitFor(text: String, ignoreCase: Boolean = true, substring: Boolean = false) = compose.waitUntil(8_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun markTheFinishLineFromTheCircuitsList() {
        val container = AppContainer(context)
        val sim = TrackSimulator.simulate(OvalTrack(), listOf(58_400L), hz = 1.0)
        container.locationSource = ReplayLocationSource(sim.fixes, speedFactor = 0.0)
        val circuitId = runBlocking { container.circuitRepository.add("Zuera", 1_100) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        waitFor("Tandas")
        compose.onNodeWithText("Circuitos").performClick()
        waitFor("Sin meta")
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Marcar meta").performClick()

        // Sin permiso: pantalla de explicación (no se lee el GPS).
        waitFor("Necesito tu ubicación")
        compose.onNodeWithTag("permission_gate").assertIsDisplayed()
        check(compose.onAllNodesWithTag("calibration_plot").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun withPermission_markShowsValuesAndSaveStoresTheFinishLine() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val container = AppContainer(context)
        val sim = TrackSimulator.simulate(OvalTrack(), listOf(58_400L), hz = 1.0)
        container.locationSource = ReplayLocationSource(sim.fixes, speedFactor = 0.0)
        val circuitId = runBlocking { container.circuitRepository.add("Zuera", 1_100) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        waitFor("Tandas")
        compose.onNodeWithText("Circuitos").performClick()
        waitFor("Sin meta")
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Marcar meta").performClick()

        waitFor("Pasa por la línea de meta a velocidad normal y pulsa MARCAR.")
        compose.onNodeWithTag("calibration_plot").assertIsDisplayed()
        waitFor("GPS ±", substring = true)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("MARCAR").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("MARCAR").performClick()

        waitFor("Rumbo de paso")
        compose.onNodeWithText("GUARDAR").performClick()
        // Tras guardar se ofrece verificar; TERMINAR vuelve a Circuitos con la meta guardada.
        waitFor("Meta guardada")
        compose.onNodeWithText("VERIFICAR META").assertIsDisplayed()
        compose.onNodeWithText("TERMINAR").performClick()
        waitFor("Meta marcada")
        val finish = runBlocking { container.circuitRepository.getById(circuitId)!!.finishLine }
        check(finish != null && kotlin.math.abs(finish.bearingDeg - 87.0) < 1.0) { "meta guardada: $finish" }
    }

    @Test
    fun adjustTheSavedLine_thenVerifyIt_throughTheUi() {
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val container = AppContainer(context)
        val oval = OvalTrack()
        val sim = TrackSimulator.simulate(oval, listOf(58_400L, 58_400L), hz = 1.0)
        // La retransmisión acaba justo después de la 2.ª pasada: ahí «pulsa» el usuario.
        container.locationSource = ReplayLocationSource(sim.fixes.filter { it.elapsedNs <= sim.crossingNs[1] + 1_000_000_000L }, speedFactor = 0.0)
        val circuitId = runBlocking {
            container.circuitRepository.add("Zuera", 1_100).also { container.circuitRepository.setFinishLine(it, oval.finishLine) }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        waitFor("Tandas")
        compose.onNodeWithText("Circuitos").performClick()
        waitFor("Meta marcada")
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Marcar meta").performClick()
        waitFor("Este circuito ya tiene meta", substring = true)

        // Ajuste manual: +3 m hacia delante y 25 m de ancho.
        compose.onNodeWithText("AJUSTAR META").performClick()
        waitFor("Posición de la meta")
        val plus = { tag: String -> compose.onNode(hasText("+") and hasAnyAncestor(hasTestTag(tag))) }
        repeat(3) { plus("shift_stepper").performClick() }
        plus("width_stepper").performClick()
        waitFor("+3 m")
        waitFor("25 m", substring = true)
        compose.onNodeWithText("GUARDAR").performClick()
        waitFor("Meta guardada")

        // Verificación: la pasada final coincide con el cruce detectado.
        compose.onNodeWithText("VERIFICAR META").performClick()
        waitFor("Da una vuelta", substring = true) // instrucciones de la verificación
        waitFor("AQUÍ")
        compose.onNodeWithText("AQUÍ").performClick()
        waitFor("Meta verificada", substring = true)
        compose.onNodeWithTag("verification_ok").assertIsDisplayed()

        val line = runBlocking { container.circuitRepository.getById(circuitId)!!.finishLine!! }
        check(line.gateWidthM == 25) { "ancho guardado: ${line.gateWidthM}" }
        val moved = com.nacho.kartlog.domain.gps.FinishAdjust.alongTrack(oval.finishLine, oval.finishLine.lat, oval.finishLine.lon, line.lat, line.lon)
        check(kotlin.math.abs(moved - 3.0) < 0.05) { "desplazamiento guardado: $moved m" }
    }
}
