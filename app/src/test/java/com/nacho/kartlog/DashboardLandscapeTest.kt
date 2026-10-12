package com.nacho.kartlog

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
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
import com.nacho.kartlog.domain.settings.DashboardOrientation
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
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Sprint 6, Parte B: el Dashboard apaisado del volante (640×360 dp). Usa el modo gráfico NATIVO para tener métricas de
 * texto reales y comprobar que nada se sale de la pantalla ni se recorta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w640dp-h360dp-land-xxhdpi")
class DashboardLandscapeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()
    private val lapTimes = listOf(58_400L, 57_900L, 58_120L)

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

    private fun waitFor(text: String, ignoreCase: Boolean = true) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun gone(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

    private fun textOf(tag: String): String =
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().let { node ->
            (listOf(node) + node.children).firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.Text) }?.joinToString("") { it.text }.orEmpty()
        }

    private fun layoutOf(tag: String): TextLayoutResult {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val candidates: List<SemanticsNode> = listOf(node) + node.children
        val out = mutableListOf<TextLayoutResult>()
        candidates.forEach { it.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out) }
        return out.first()
    }

    /**
     * Etiquetas de los únicos elementos pulsables del Dashboard en marcha (la pulsación larga no es un «clic»). El atajo
     * `debug_finish` (Sprint 7, solo builds debug) no cuenta: la versión real sigue sin botones de un toque.
     */
    private fun clickableTags(): List<String> =
        compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = true)
            .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.TestTag) }.filter { it != "debug_finish" }

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition() && System.currentTimeMillis() < deadline) { Thread.sleep(30); compose.waitForIdle() }
        check(condition()) { "no se cumplió la condición a tiempo" }
    }

    /** Todo lo principal del Dashboard está dentro de la pantalla y su texto no se recorta ni desborda. */
    private fun assertNothingClipped(vararg textTags: String) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val tags = listOf("dash_current", "dash_delta", "dash_last", "dash_best", "dash_lap_number", "dash_gps", "finish_hold") + textTags
        tags.forEach { tag ->
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag fuera de pantalla: $b (pantalla $root)", b.left >= root.left && b.right <= root.right && b.top >= root.top && b.bottom <= root.bottom)
        }
        (listOf("dash_current", "dash_delta", "dash_last", "dash_best", "dash_lap_number") + textTags.filter { it != "pit_button" }).forEach { tag ->
            val layout = layoutOf(tag)
            val info = "texto='${textOf(tag)}' layout=${layout.size} fontSize=${layout.layoutInput.style.fontSize} constraints=${layout.layoutInput.constraints} para=${layout.multiParagraph.width}x${layout.multiParagraph.height} lines=${layout.lineCount} line0=${layout.getLineTop(0)}..${layout.getLineBottom(0)} lineRight=${layout.getLineRight(0)} lineHeight=${layout.layoutInput.style.lineHeight} font=${layout.layoutInput.style.fontFamily} nodo=${compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot}"
            assertTrue("$tag desborda en ancho: $info", layout.getLineRight(layout.lineCount - 1) <= layout.layoutInput.constraints.maxWidth)
            assertTrue("$tag desborda en alto: $info", layout.getLineBottom(layout.lineCount - 1) <= layout.layoutInput.constraints.maxHeight)
        }
    }

    private fun openDashboard(container: AppContainer, type: String? = null) {
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        waitFor("Tandas")
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        waitFor("EMPEZAR")
        // El circuito llega de Room de forma asíncrona: hasta entonces EMPEZAR está desactivado y pulsarlo no hace nada.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        if (type != null) compose.onNodeWithText(type).performScrollTo().performClick()
        compose.onAllNodesWithText("EMPEZAR")[0].performClick()
        waitForTag("dash_current")
    }

    private fun containerWithOval(): Pair<AppContainer, ControlledSource> {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val source = ControlledSource()
        container.locationSource = source
        runBlocking { container.circuitRepository.add("Óvalo simulado", 780).also { container.circuitRepository.setFinishLine(it, oval.finishLine) } }
        return container to source
    }

    @Test
    fun theLandscapeDashboardFitsWithoutClipping_locksTheOrientation_andShowsBigAlerts() {
        val (container, source) = containerWithOval()
        // Récord previo del circuito: 58,000 (la 1.ª vuelta, 58,400, no lo bate; la 2.ª, 57,900, sí).
        runBlocking {
            val circuitId = container.circuitRepository.observeAll().first().first().id
            val id = container.sessionRepository.create(NewSession(circuitId, 1_700_000_000_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""))
            container.lapRepository.addManual(id, 58_000)
        }
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
        // Batería baja y sin cargador.
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 15).putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_PLUGGED, 0).putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING),
        )
        openDashboard(container)

        // B1: apaisado fijo (los dos sentidos) mientras se cronometra.
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, compose.activity.requestedOrientation)

        // B2: nada recortado ni fuera de pantalla, tiempo gigante (≥ 96 sp) y controles ≥ 56 dp.
        compose.onNodeWithTag("battery_low").assertIsDisplayed()
        assertNothingClipped("battery_low")
        val density = context.resources.displayMetrics.density
        val fontScale = context.resources.configuration.fontScale
        val timeSp = layoutOf("dash_current").layoutInput.style.fontSize.value
        assertTrue("el cronómetro debe medir ≥ 96 sp: $timeSp", timeSp >= 96f)
        assertTrue("fontScale $fontScale", fontScale >= 0f)
        val hold = compose.onNodeWithTag("finish_hold").fetchSemanticsNode().boundsInRoot
        assertTrue("pulsación larga ≥ 56 dp: ${hold.height / density}", hold.height / density >= 56f)
        assertTrue("sin aparecer botones extra: solo la pulsación larga (no es Resistencia)", compose.onAllNodesWithTag("pit_button").fetchSemanticsNodes().isEmpty())
        // B4: en marcha no hay ningún elemento pulsable (terminar es una pulsación larga, no un clic).
        assertEquals("elementos pulsables en marcha: ${clickableTags()}", emptyList<String>(), clickableTags())

        // B3: vuelta 1 (58,400) — mejor de la tanda pero no récord: sin delta, tiempo y color «bueno».
        runBlocking { sim.fixes.filter { it.elapsedNs <= sim.crossingNs[1] + 1_000_000_000L }.forEach { source.flow.emit(it) } }
        waitForTag("lap_alert")
        assertEquals("VUELTA 1", textOf("lap_alert_label"))
        assertTrue("la primera vuelta muestra su tiempo: ${textOf("lap_alert_value")}", textOf("lap_alert_value").startsWith("0:58."))
        gone("lap_alert") // dura 2 s y desaparece sola

        // Vuelta 2 (57,900): bate la mejor de la tanda (−0.500) y el récord del circuito (58,000): «MEJOR» a pantalla completa.
        runBlocking { sim.fixes.filter { it.elapsedNs > sim.crossingNs[1] + 1_000_000_000L && it.elapsedNs <= sim.crossingNs[2] + 1_000_000_000L }.forEach { source.flow.emit(it) } }
        waitForTag("lap_alert")
        assertEquals("MEJOR", textOf("lap_alert_label"))
        assertTrue("delta ≈ −0.500: ${textOf("lap_alert_value")}", Regex("-0\\.(49|50)\\d").matches(textOf("lap_alert_value")))
        assertTrue(textOf("lap_alert_time").startsWith("0:57.9"))
        gone("lap_alert")

        // Vuelta 3 (58,120): más lenta que la mejor (+0.220).
        runBlocking { sim.fixes.filter { it.elapsedNs > sim.crossingNs[2] + 1_000_000_000L }.forEach { source.flow.emit(it) } }
        waitForTag("lap_alert")
        assertEquals("VUELTA 3", textOf("lap_alert_label"))
        assertTrue("delta ≈ +0.220: ${textOf("lap_alert_value")}", Regex("\\+0\\.2[1-3]\\d").matches(textOf("lap_alert_value")))
        gone("lap_alert")
        until { container.timingSession.state.value.laps.size == 3 }
        assertNothingClipped("battery_low")

        // B1: al salir del Dashboard se libera la orientación.
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag("leave_exit")
        compose.onNodeWithTag("leave_exit").performClick()
        waitFor("Tandas")
        until { compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test
    fun enduranceShowsTheStintAndABigPitButton_withoutClipping() {
        val (container, source) = containerWithOval()
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
        openDashboard(container, type = "Resistencia")
        runBlocking { sim.fixes.filter { it.elapsedNs <= sim.crossingNs[1] + 3_000_000_000L }.forEach { source.flow.emit(it) } }
        until { container.timingSession.state.value.laps.size == 1 }
        waitForTag("pit_button")
        val density = context.resources.displayMetrics.density
        val pit = compose.onNodeWithTag("pit_button").fetchSemanticsNode().boundsInRoot
        assertTrue("PIT ≥ 56 dp de alto: ${pit.height / density}", pit.height / density >= 56f)
        assertTrue("PIT ancho: ${pit.width / density}", pit.width / density >= 120f)
        assertTrue(textOf("stint_label").startsWith("Stint 1"))
        // B4: en Resistencia el único elemento pulsable es PIT / CAMBIO.
        assertEquals(listOf("pit_button"), clickableTags())
        assertNothingClipped("stint_label", "stint_time", "pit_button")
        compose.onNodeWithTag("pit_button").performClick()
        waitForTag("pit_confirm")
        compose.onNodeWithTag("pit_confirm").performClick()
        waitForTag("pit_pending")
        assertNothingClipped("pit_pending")
        assertTrue("pit visible", compose.onNodeWithTag("pit_pending").fetchSemanticsNode().boundsInRoot.height > 0f)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
    fun theOrientationSettingIsRespected_autoAndPortrait() {
        val (container, _) = containerWithOval()
        container.userSettings.setDashboardOrientation(DashboardOrientation.AUTO)
        openDashboard(container)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, compose.activity.requestedOrientation)
        container.userSettings.setDashboardOrientation(DashboardOrientation.PORTRAIT)
        until { compose.activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT }
        // Vertical: también sin recortes.
        assertNothingClipped()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
    fun settingsChipsAndSwitchesPersist_andDayModeIsSwitchedFromThePreparation() {
        val (container, _) = containerWithOval()
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        waitFor("Tandas")
        compose.onAllNodesWithText("Ajustes")[0].performClick()
        waitForTag("dashboard_settings")
        assertEquals(DashboardOrientation.LANDSCAPE, container.userSettings.dashboardOrientation.value)
        compose.onNodeWithText("Automático").performScrollTo().performClick()
        until { container.userSettings.dashboardOrientation.value == DashboardOrientation.AUTO }
        compose.onNodeWithText("Vertical").performScrollTo().performClick()
        until { container.userSettings.dashboardOrientation.value == DashboardOrientation.PORTRAIT }
        compose.onNodeWithTag("switch_vibration_only").performScrollTo().performClick()
        until { container.userSettings.vibrationOnly.value }
        compose.onNodeWithTag("switch_max_brightness").performScrollTo().performClick()
        until { container.userSettings.maxBrightness.value }

        // Modo día: se conmuta en la preparación del Dashboard.
        compose.onAllNodesWithText("Tandas")[0].performClick()
        waitFor("Tandas")
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        waitForTag("switch_day_mode")
        assertEquals(false, container.userSettings.dashboardDayMode.value)
        compose.onNodeWithTag("switch_day_mode").performScrollTo().performClick()
        until { container.userSettings.dashboardDayMode.value }
    }
}
