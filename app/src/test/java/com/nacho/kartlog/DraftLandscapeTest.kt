package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.gps.FileDraftStore
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A2: el diálogo «Tanda interrumpida» cabe (o se desplaza) en apaisado de 640×360 dp con los dos botones accesibles. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w640dp-h360dp-land-xxhdpi")
class DraftLandscapeTest {
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
    fun dialogButtonsAreInsideTheLandscapeWindow() {
        val container = AppContainer(context)
        val oval = OvalTrack()
        val circuitId = runBlocking { container.circuitRepository.add("Óvalo simulado", 780) }
        runBlocking {
            val sim = TrackSimulator.simulate(oval, listOf(58_400L, 57_900L, 58_120L), hz = 1.0)
            val store = FileDraftStore(File(context.filesDir, "tracks"))
            store.begin(GpsSessionSetup(circuitId, oval.finishLine, kartNumber = 9, type = SessionType.RACE, startedAtMs = 1_760_000_000_000L))
            store.append(sim.fixes)
        }
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tanda interrumpida").fetchSemanticsNodes().isNotEmpty() }

        listOf("RECUPERAR TANDA INTERRUMPIDA", "DESCARTAR").forEach { label ->
            val node = compose.onNodeWithText(label)
            node.assertIsDisplayed()
            val b = node.getBoundsInRoot()
            assertTrue("$label dentro de 360 dp de alto: ${b.bottom}", b.bottom <= 360.dp)
        }
    }
}
