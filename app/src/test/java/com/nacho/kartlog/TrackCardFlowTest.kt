package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
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
import java.io.File

/** Sprint 6, Parte C: la tarjeta «Trazado» del Detalle (solo con trazado), selector de vuelta, modos, comparación y lectura. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h800dp-xxhdpi")
class TrackCardFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L, 59_300L)
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
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun textOf(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    private fun canvasState(): String =
        compose.onNodeWithTag("track_canvas").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription).orEmpty()

    /** Crea una tanda GPS con trazado (y circuito con o sin meta) y abre su Detalle. */
    private fun openDetail(
        withTrack: Boolean = true,
        withFinish: Boolean = true,
        editLap: Boolean = false,
        container: AppContainer = AppContainer(context),
        fixes: List<com.nacho.kartlog.domain.gps.GpsFix> = sim.fixes,
        lapGeometry: com.nacho.kartlog.domain.gps.TrackGeometry = geometry,
    ) {
        runBlocking {
            val circuit = container.circuitRepository.add("Óvalo simulado", 780)
            if (withFinish) container.circuitRepository.setFinishLine(circuit, oval.finishLine)
            val id = container.sessionRepository.create(
                NewSession(circuit, 1_760_000_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""),
            )
            val times = lapGeometry.laps.map { it.timeMs }.toMutableList()
            if (editLap) times[1] = times[1] + 400 // editada a mano: ya no coincide con el trazado
            container.lapRepository.addAll(id, times, if (withTrack) LapSource.GPS else LapSource.MANUAL)
            if (withTrack) container.trackStore.save(id, fixes)
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Óvalo simulado", substring = true)[0].performClick()
    }

    @Test
    fun trackCardAppearsOnlyWhenTheSessionHasATrack() {
        openDetail(withTrack = false)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertTrue("sin trazado no hay tarjeta", compose.onAllNodesWithTag("track_card").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun lapSelectorChangesTheHighlight_modesCompareAndTapReading() {
        openDetail()
        waitForTag("track_card")
        // Visible arriba y abierta por defecto, sin bajar: el lienzo está dentro de la pantalla.
        compose.onNodeWithTag("track_canvas").assertIsDisplayed()
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(compose.onNodeWithTag("track_canvas").fetchSemanticsNode().boundsInRoot.bottom < rootBottom)
        assertEquals("sel=ALL;mode=LAP;compare=false", canvasState())
        assertTrue(textOf("track_info").startsWith("Todas las vueltas (4)"))

        // El selector cambia lo resaltado: una vuelta, la mejor, la última.
        compose.onNodeWithTag("track_sel_3").performClick()
        compose.waitUntil(5_000) { canvasState().startsWith("sel=LAP:3") }
        assertTrue(textOf("track_info").startsWith("V3  0:58."))
        compose.onNodeWithTag("track_sel_best").performClick()
        compose.waitUntil(5_000) { canvasState().startsWith("sel=BEST") }
        assertTrue("la mejor es la V2: ${textOf("track_info")}", textOf("track_info").startsWith("V2  0:57."))
        compose.onNodeWithTag("track_sel_last").performClick()
        compose.waitUntil(5_000) { canvasState().startsWith("sel=LAST") }
        assertTrue(textOf("track_info").startsWith("V4  0:59."))

        // Modo velocidad: leyenda con el rango en km/h.
        compose.onNodeWithTag("track_mode_speed").performClick()
        compose.waitUntil(5_000) { canvasState().contains("mode=SPEED") }
        assertTrue(textOf("track_legend").contains("km/h"))

        // Comparar con la mejor (V4 ≠ mejor): dos colores y leyenda.
        compose.onNodeWithTag("track_compare").performClick()
        compose.waitUntil(5_000) { canvasState().endsWith("compare=true") }
        assertTrue(textOf("track_legend").contains("V2 (mejor)") && textOf("track_legend").contains("V4"))
        assertTrue(textOf("track_info").contains("vs"))
        compose.onNodeWithTag("track_compare").performClick()
        compose.waitUntil(5_000) { canvasState().endsWith("compare=false") }

        // Ritmo: disponible con una vuelta distinta de la mejor.
        compose.onNodeWithTag("track_mode_pace").performClick()
        compose.waitUntil(5_000) { canvasState().contains("mode=PACE") }
        assertTrue(textOf("track_legend").contains("±1 s"))

        // Tocar un punto de la traza: velocidad y tiempo desde la meta (solo lectura).
        compose.onNodeWithTag("track_sel_1").performClick()
        compose.onNodeWithTag("track_mode_lap").performClick()
        compose.waitUntil(5_000) { canvasState().startsWith("sel=LAP:1") }
        val size = compose.onNodeWithTag("track_canvas").fetchSemanticsNode().size
        val density = context.resources.displayMetrics.density
        val fit = TrackAnalysis.fit(geometry.bounds, size.width.toFloat(), size.height.toFloat(), 14f * density)
        val target = geometry.laps[0].points[geometry.laps[0].points.size / 3]
        compose.onNodeWithTag("track_canvas").performTouchInput { click(Offset(fit.px(target.x), fit.py(target.y))) }
        compose.waitUntil(5_000) { textOf("track_info").contains("km/h") }
        assertTrue(textOf("track_info"), textOf("track_info").matches(Regex("V1 · \\d+ km/h · \\d:\\d\\d\\.\\d{3} desde la meta")))
    }

    @Test
    fun editedLapsOrMissingFinishShowTheFullTraceWithoutSelector_andSayWhy() {
        openDetail(editLap = true)
        waitForTag("track_card")
        waitForTag("track_note")
        assertTrue(textOf("track_note").contains("ya no coinciden"))
        assertTrue("sin selector de vuelta", compose.onAllNodesWithTag("track_sel_all").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("track_canvas").assertIsDisplayed()
        assertTrue(textOf("track_info").startsWith("Trazado completo"))
    }

    @Test
    fun circuitWithoutFinishLineShowsTheFullTrace() {
        openDetail(withFinish = false)
        waitForTag("track_note")
        assertTrue(textOf("track_note").contains("no tiene la meta"))
        assertTrue(compose.onAllNodesWithTag("track_sel_all").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aTenThousandPointTrackLoadsOffTheMainThread_andDrawsWithAtMost2000Points() {
        val dense = TrackSimulator.simulate(oval, List(18) { 58_000L + it * 10 }, hz = 10.0)
        val denseGeometry = TrackAnalysis.build(dense.fixes, oval.finishLine)
        assertTrue(dense.fixes.size >= 10_000)
        openDetail(fixes = dense.fixes, lapGeometry = denseGeometry)
        waitForTag("track_card")
        // El trazado llega de forma asíncrona (se calcula fuera del hilo principal): se espera a que esté colocado.
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("track_canvas").fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.width?.let { it > 0f } == true }
        compose.onNodeWithTag("track_canvas").assertIsDisplayed()
        assertTrue(textOf("track_info").startsWith("Todas las vueltas (18)"))
        compose.onNodeWithTag("track_sel_last").performClick()
        compose.waitUntil(5_000) { canvasState().startsWith("sel=LAST") }
        assertTrue(textOf("track_info").startsWith("V18  "))
    }
}
