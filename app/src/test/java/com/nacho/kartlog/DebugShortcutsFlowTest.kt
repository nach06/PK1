package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
}
