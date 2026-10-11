package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
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
        compose.activityRule.scenario.close()
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
        // Vuelve a Circuitos y la meta queda guardada.
        waitFor("Meta marcada")
        val finish = runBlocking { container.circuitRepository.getById(circuitId)!!.finishLine }
        check(finish != null && kotlin.math.abs(finish.bearingDeg - 87.0) < 1.0) { "meta guardada: $finish" }
    }
}
