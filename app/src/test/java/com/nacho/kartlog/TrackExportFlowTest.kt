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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
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

/** Sprint 8, B2: el menú ⋮ del Detalle exporta el trazado a GPX (una pista por vuelta) y CSV, sin datos de rivales. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class TrackExportFlowTest {
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

    private fun waitForFile(ext: String): File {
        val dir = File(context.cacheDir, "exports")
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            dir.listFiles { f -> f.name.endsWith(ext) && f.length() > 0 }?.firstOrNull()?.let { return it }
            Thread.sleep(100); compose.waitForIdle()
        }
        error("no se creó el fichero $ext")
    }

    @Test
    fun menuExportsGpxAndCsvOfTheGpsTrack() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Óvalo simulado", 780)
            container.circuitRepository.setFinishLine(circuit, oval.finishLine)
            val id = container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""))
            container.lapRepository.addAll(id, geometry.laps.map { it.timeMs }, LapSource.GPS)
            container.trackStore.save(id, sim.fixes)
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Óvalo simulado", substring = true)[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("track_canvas").fetchSemanticsNodes().isNotEmpty() }

        compose.onAllNodesWithContentDescription("Más opciones").onLast().performClick()
        compose.onNodeWithText("Exportar GPX (trazado)").performClick()
        val gpx = waitForFile(".gpx").readText()
        assertEquals(lapTimes.size + 2, Regex("<trk>").findAll(gpx).count())
        assertTrue(gpx.contains("<name>V1 0:58."))
        assertFalse(gpx.contains("rival", ignoreCase = true))

        compose.onAllNodesWithContentDescription("Más opciones").onLast().performClick()
        compose.onNodeWithText("Exportar CSV del GPS").performClick()
        val csv = waitForFile(".gps.csv").readText()
        assertTrue(csv.startsWith("#kartlog-track v2"))
        assertEquals(sim.fixes.size, TrackFormats.parseCsv(csv).size)
    }
}
