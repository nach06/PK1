package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
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

/** Sprint 7, C1: el DELTA grande del Dashboard es el delta en vuelta contra la vuelta de referencia, con su etiqueta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class InLapDeltaFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val oval = OvalTrack()

    private class ControlledSource : LocationSource {
        val flow = MutableSharedFlow<GpsFix>(extraBufferCapacity = 5_000)
        override fun fixes() = flow
    }

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

    private fun texts(node: SemanticsNode): List<String> =
        (node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()) + node.children.flatMap(::texts)

    private fun textsOf(tag: String) = texts(compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()).joinToString(" ")

    @Test
    fun theBigDeltaIsTheInLapDelta_withItsReferenceLabel() {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val source = ControlledSource()
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        container.timingSession.start(GpsSessionSetup(circuitId, oval.finishLine, kartNumber = 1, type = SessionType.FREE, startedAtMs = 1L), source)
        val sim = TrackSimulator.simulate(oval, listOf(58_000L, 57_000L, 58_000L), hz = 1.0)
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("running_banner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("running_banner").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("dash_delta").fetchSemanticsNodes().isNotEmpty() }

        // Primera vuelta, sin nada con qué comparar: etiqueta «sin referencia» y el delta de siempre (sin valor aún).
        var emitted = 0
        fun emitWhile(condition: (GpsFix) -> Boolean) {
            while (emitted < sim.fixes.size && condition(sim.fixes[emitted])) {
                runBlocking { source.flow.emit(sim.fixes[emitted]) }
                emitted++
            }
            compose.waitForIdle()
        }
        emitWhile { it.elapsedNs < sim.crossingNs[0] + 30_000_000_000L }
        assertTrue(textsOf("dash_delta_caption"), textsOf("dash_delta_caption").contains("SIN REFERENCIA", ignoreCase = true))

        // Cerrada la V1 hay referencia: la etiqueta cambia y, a mitad de la V2, el delta ya es en vuelta (V2 es 1 s más rápida: ≈ −0,5 s).
        emitWhile { it.elapsedNs < sim.crossingNs[1] + 30_000_000_000L }
        compose.waitUntil(10_000) { textsOf("dash_delta_caption").contains("MEJOR", ignoreCase = true) }
        val mid = Regex("([+-])(\\d+)\\.(\\d{3})").find(textsOf("dash_delta"))!!.groupValues
        val ms = (mid[1] + "1").toInt() * (mid[2].toInt() * 1000 + mid[3].toInt())
        assertEquals("delta a mitad de vuelta (V2 más rápida que V1)", -500.0, ms.toDouble(), 200.0)
        container.timingSession.stop()
    }
}
