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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackAnalysis
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A5: tarjeta «Trazado» con lienzo contenido, hueco de leyenda fijo, selector en una fila y ≥3 vueltas visibles debajo (393×808 dp). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class TrackCardLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L, 59_300L, 58_700L, 58_050L, 57_950L)
    private val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
    private val geometry = TrackAnalysis.build(sim.fixes, oval.finishLine)

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

    private fun open() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Óvalo simulado", 780)
            container.circuitRepository.setFinishLine(circuit, oval.finishLine)
            val id = container.sessionRepository.create(
                NewSession(circuit, 1_760_000_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""),
            )
            container.lapRepository.addAll(id, geometry.laps.map { it.timeMs }, LapSource.GPS)
            container.trackStore.save(id, sim.fixes)
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Óvalo simulado", substring = true)[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("track_canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_row").fetchSemanticsNodes().isNotEmpty() }
        // El trazado se calcula fuera del hilo principal: se espera a que el lienzo tenga tamaño.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("track_canvas").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.width?.let { it > 0f } == true }
        compose.waitForIdle()
    }

    @Test
    fun legendSlotKeepsChipsStill_canvasIsBounded_andThreeLapsAreVisibleBelow() {
        open()
        compose.waitForIdle()
        val canvas = compose.onNodeWithTag("track_canvas").getBoundsInRoot()
        val usable = 808.dp - 56.dp - 80.dp
        assertTrue("lienzo ${canvas.bottom - canvas.top} ≤ 40 % de $usable", (canvas.bottom - canvas.top) <= usable * 0.4f)

        // La fila de chips no se mueve al cambiar de modo (la leyenda aparece o desaparece dentro de su hueco).
        val before = compose.onNodeWithTag("track_sel_all").getBoundsInRoot().top
        compose.onNodeWithTag("track_mode_speed").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("track_legend").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(before.value, compose.onNodeWithTag("track_sel_all").getBoundsInRoot().top.value, 0.5f)
        compose.onNodeWithTag("track_mode_lap").performClick()
        compose.waitForIdle()
        assertEquals(before.value, compose.onNodeWithTag("track_sel_all").getBoundsInRoot().top.value, 0.5f)

        // Debajo del trazado se ven enteras al menos 3 vueltas, sin desplazar.
        val full = compose.onAllNodesWithTag("lap_row").fetchSemanticsNodes().indices.count { i ->
            val node = compose.onAllNodesWithTag("lap_row")[i]
            val clipped = node.getBoundsInRoot()
            val unclipped = node.getUnclippedBoundsInRoot()
            (clipped.bottom - clipped.top) >= (unclipped.bottom - unclipped.top) - 1.dp
        }
        assertTrue("vueltas enteras visibles: $full", full >= 3)
    }

    @Test
    fun chosenLapChipStaysVisibleInASingleRow() {
        open()
        compose.onNodeWithTag("track_lap_selector").performScrollToNode(hasTestTag("track_sel_7"))
        compose.onNodeWithTag("track_sel_7").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("track_sel_7").assertIsDisplayed()
        val row = compose.onNodeWithTag("track_lap_selector").getBoundsInRoot()
        val chip = compose.onNodeWithTag("track_sel_7").getBoundsInRoot()
        assertTrue("el chip está dentro de la única fila del selector", chip.top >= row.top && chip.bottom <= row.bottom + 0.5.dp && chip.right <= row.right)
    }
}
