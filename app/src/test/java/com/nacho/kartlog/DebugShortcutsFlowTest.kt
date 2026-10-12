package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Sprint 7, Parte B: atajos de depuración de un solo toque (para el emulador con permisos limitados: sin mantener pulsado,
 * arrastrar, teclear ni desplazar). Solo existen si `container.debugTools != null` (builds debug).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class DebugShortcutsFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()

    private class IdleSource : LocationSource {
        override fun fixes() = MutableSharedFlow<GpsFix>(extraBufferCapacity = 4)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
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

    /** Cada etiqueta está entera dentro de la ventana (sin tener que desplazar). */
    private fun assertInsideScreen(vararg tags: String) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        tags.forEach { tag ->
            val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag fuera de pantalla: $b (ventana $root)", b.top >= 0.dp && b.bottom <= root.bottom)
        }
    }

    private fun container(): AppContainer = AppContainer(context).also {
        it.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
    }

    @Test
    fun b1_terminarDebug_endsTheRunWithASingleTap() {
        val container = container()
        assertNotNull("en debug existen las herramientas", container.debugTools)
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        container.timingSession.start(GpsSessionSetup(circuitId, oval.finishLine, kartNumber = 1, type = SessionType.FREE, startedAtMs = 1L), IdleSource())
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        waitForTag("running_banner")
        compose.onNodeWithTag("running_banner").performClick()
        waitForTag("debug_finish")

        compose.onNodeWithTag("debug_finish").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tanda terminada", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(TimingPhase.STOPPED, container.timingSession.state.value.phase)
    }

    @Test
    fun b2_marcarMetaSimulada_reachesAdjustAndVerifyWithoutDriving() {
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val container = container()
        container.locationSource = IdleSource()
        val circuitId = runBlocking { container.circuitRepository.add("Zuera", 1_100) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Circuitos").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Sin meta").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Marcar meta").performClick()
        waitForTag("debug_mark_sim")

        // Un toque: pase simulado + marcado. Salen el rumbo, el ajuste y GUARDAR.
        compose.onNodeWithTag("debug_mark_sim").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Rumbo de paso", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        // Sin desplazarse: el ajuste (desplazar ±, ancho) y GUARDAR están dentro de la pantalla.
        assertInsideScreen("shift_stepper", "width_stepper", "calibration_save")
        compose.onNodeWithText("GUARDAR").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Meta guardada", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        val finish = runBlocking { container.circuitRepository.getById(circuitId)!!.finishLine }
        assertNotNull("la meta simulada queda guardada", finish)

        // Un toque más: verificación simulada con la meta guardada → resultado con tiempo y rumbo.
        compose.onNodeWithTag("debug_verify_sim").performClick()
        waitForTag("verification_values")
    }

    @Test
    fun b4_exportAndRestoreLatestBackup_withoutAnyFilePicker() {
        val container = container()
        var restarts = 0
        container.restartApp = { restarts++ }
        runBlocking { container.circuitRepository.add("Zuera", 1_100) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()

        // Sin copia todavía: lo dice, no abre ningún selector.
        waitForTag("debug_backup_restore")
        compose.onNodeWithTag("debug_backup_restore").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No hay ninguna copia debug", substring = true).fetchSemanticsNodes().isNotEmpty() }

        // Exportar: aparece la ruta donde quedó (visible en pantalla) y el fichero existe.
        compose.onNodeWithTag("debug_backup_save").performClick()
        waitForTag("debug_note")
        val dir = java.io.File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "")
        assertTrue("hay un .zip de copia en ${dir.path}", dir.listFiles { f -> f.name.startsWith("kartlog-debug-backup-") && f.name.endsWith(".zip") }!!.isNotEmpty())

        // Restaurar la última: pide la confirmación habitual y reinicia.
        compose.onNodeWithTag("debug_backup_restore").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("restore_confirm").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("restore_confirm").performClick()
        val deadline = System.currentTimeMillis() + 10_000
        while (restarts == 0 && System.currentTimeMillis() < deadline) { Thread.sleep(100); compose.waitForIdle() }
        assertEquals("se reinicia tras restaurar", 1, restarts)
    }

    @Test
    fun b5_enduranceSimulation_startsFromSettingsWithOneClick() {
        val container = container()
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        waitForTag("debug_endurance")
        compose.onNodeWithTag("debug_endurance").performClick()
        // El Dashboard se abre ya en marcha, en resistencia (Stint 1), sin pasar por «Nueva tanda».
        waitForTag("stint_label")
        assertEquals(SessionType.ENDURANCE, container.timingSession.state.value.setup?.type)
        assertEquals(TimingPhase.RUNNING, container.timingSession.state.value.phase)
        container.timingSession.stop()
    }

    @Test
    fun b5_theScriptAsksForTwoPitStops_andTheRunEndsWithThreeStints() {
        val container = container()
        val oval = OvalTrack()
        val source = ControlledSource()
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        val sim = com.nacho.kartlog.domain.gps.TrackSimulator.simulate(oval, List(12) { 58_000L }, hz = 1.0)
        container.startEnduranceSimulation(
            com.nacho.kartlog.debug.EnduranceSimulation(source, circuitId, oval.finishLine, listOf(3, 6), listOf("Piloto B", "Piloto C")),
        )
        runBlocking { sim.fixes.forEach { source.flow.emit(it); kotlinx.coroutines.delay(2) } }
        val deadline = System.currentTimeMillis() + 10_000
        while (container.timingSession.state.value.stints.size < 3 && System.currentTimeMillis() < deadline) Thread.sleep(50)
        val stints = container.timingSession.state.value.stints
        assertEquals("tres stints tras dos paradas", 3, stints.size)
        assertEquals(listOf(null, "Piloto B", "Piloto C"), stints.map { it.driverName })
        container.timingSession.stop()
    }

    private class ControlledSource : LocationSource {
        val flow = MutableSharedFlow<GpsFix>(extraBufferCapacity = 5_000)
        override fun fixes() = flow
    }
}
