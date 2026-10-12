package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
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
 * Sprint 6, A1: el cronómetro de la vuelta en curso avanza con el reloj en vivo y SE DIBUJA. La causa era `AutoSizeText`,
 * que ocultaba el texto en cada cambio (cada 50 ms): los tests con el reloj apagado no lo veían.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class DashboardClockTest {
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
        compose.mainClock.autoAdvance = true
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun text(tag: String) = compose.onNodeWithTag(tag).onChild().fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    @Test
    fun theCurrentLapClockAdvancesWithTheInjectedClock_andIsActuallyDrawn() {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val source = ControlledSource()
        container.locationSource = source
        var nowNs = 1_000_000_000_000L
        container.nanoClock = { nowNs }
        runBlocking { container.circuitRepository.add("Óvalo simulado", 780).also { container.circuitRepository.setFinishLine(it, oval.finishLine) } }
        val sim = TrackSimulator.simulate(oval, listOf(58_400L, 57_900L, 58_120L), hz = 1.0)
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("EMPEZAR").fetchSemanticsNodes().isNotEmpty() }
        // El circuito llega de Room de forma asíncrona: hasta entonces EMPEZAR está desactivado y pulsarlo no hace nada.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Óvalo simulado", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("EMPEZAR").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dash_current").fetchSemanticsNodes().isNotEmpty() }
        // A partir de aquí el reloj en vivo (bucle de 50 ms) está activo: el tiempo de Compose se avanza a mano.
        compose.mainClock.autoAdvance = false

        runBlocking { sim.fixes.filter { it.elapsedNs <= sim.crossingNs[0] + 10_000_000_000L }.forEach { source.flow.emit(it) } }
        val deadline = System.currentTimeMillis() + 10_000
        while (container.timingSession.state.value.currentLapStartNs == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("la vuelta debe haber empezado", container.timingSession.state.value.currentLapStartNs != null)
        nowNs += 1_000_000L
        var spins = 0
        while (text("dash_current")?.contains("–") != false && spins++ < 50) { Thread.sleep(20); compose.mainClock.advanceTimeBy(100) }
        val first = text("dash_current")
        assertTrue("debe mostrar un tiempo, no el hueco: $first", first != null && !first.contains("–"))

        nowNs += 2_000_000_000L
        compose.mainClock.advanceTimeBy(100)
        val second = text("dash_current")
        assertNotEquals("el cronómetro debe avanzar con el reloj", first, second)

        // Y se dibuja: hay píxeles claros (texto blanco sobre fondo negro) en la zona del cronómetro.
        // (captureToImage espera a que Compose esté en reposo, y con el reloj en vivo nunca lo está: se dibuja la vista a mano.)
        val bounds = compose.onNodeWithTag("dash_current").fetchSemanticsNode().boundsInRoot
        val decor = compose.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bitmap))
        var bright = 0
        for (x in bounds.left.toInt() until bounds.right.toInt() step 2) for (y in bounds.top.toInt() until bounds.bottom.toInt() step 2) {
            val c = bitmap.getPixel(x, y)
            if ((c and 0xFF) > 200 && ((c shr 8) and 0xFF) > 200) bright++
        }
        assertTrue("el cronómetro debe estar dibujado (píxeles claros: $bright)", bright > 20)
    }
}
