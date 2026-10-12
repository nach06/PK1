package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.GpsRecording
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
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
import org.robolectric.annotation.Config
import java.io.File

/** Sprint 8, B4: al guardar una tanda GPS se puntúa la señal; la card de Tandas y el Detalle la muestran. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class SignalQualityFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context

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

    @Test
    fun savedGpsSessionCarriesItsSignalScore_inTheCardAndTheDetail() {
        val container = AppContainer(context)
        val oval = OvalTrack()
        val sim = TrackSimulator.simulate(oval, listOf(58_400L, 57_900L), hz = 1.0)
        val circuit = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        val id = runBlocking {
            container.gpsSessionSaver.save(GpsRecording(GpsSessionSetup(circuit, oval.finishLine, kartNumber = 7, startedAtMs = 1_760_000_000_000), listOf(58_400L, 57_900L), sim.fixes))
        }!!
        assertEquals(0, runBlocking { container.sessionRepository.observeSummaries().first() }.single { it.session.id == id }.session.signalScore)

        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("card_signal", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Señal GPS buena", substring = true, useUnmergedTree = true)[0].assertExistsCompat()
        compose.onAllNodesWithText("Óvalo simulado", substring = true)[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("signal_quality", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("100 % del tiempo", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("0 huecos", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
