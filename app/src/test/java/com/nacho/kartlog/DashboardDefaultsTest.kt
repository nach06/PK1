package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sprint 8, A5: el modo día arranca apagado y se explica; el atajo de depuración no tapa el cronómetro. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w640dp-h360dp-land-xxhdpi")
class DashboardDefaultsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun container() = AppContainer(context).also {
        it.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
    }

    @Test
    fun dayModeIsOffByDefault_andIsExplained() {
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val container = container()
        runBlocking { container.circuitRepository.add("Óvalo", 780).also { container.circuitRepository.setFinishLine(it, OvalTrack().finishLine) } }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_track").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("switch_day_mode").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(container.userSettings.dashboardDayMode.value)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Desactivado: fondo oscuro", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).all.keys.none { it.contains("day") })
    }

    @Test
    fun debugFinishDoesNotCoverTheStopwatch() {
        val container = container()
        val circuit = runBlocking { container.circuitRepository.add("Óvalo", 780) }
        val source = object : LocationSource {
            override fun fixes() = MutableSharedFlow<GpsFix>(extraBufferCapacity = 4)
        }
        container.timingSession.start(GpsSessionSetup(circuit, OvalTrack().finishLine, kartNumber = 1, type = SessionType.FREE, startedAtMs = 1L), source)
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("running_banner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("running_banner").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("debug_finish").fetchSemanticsNodes().isNotEmpty() }
        val d = compose.onNodeWithTag("debug_finish").getUnclippedBoundsInRoot()
        val t = compose.onNodeWithTag("dash_current").getUnclippedBoundsInRoot()
        assertTrue("debug_finish $d no debe solaparse con el cronómetro $t", d.right <= t.left || d.left >= t.right || d.bottom <= t.top || d.top >= t.bottom)
        assertTrue("pequeño y dentro de la ventana: $d", (d.right - d.left).value < 170f && (d.bottom - d.top).value < 60f && d.right.value <= 640f && d.bottom.value <= 360f)
        container.timingSession.stop()
    }
}
