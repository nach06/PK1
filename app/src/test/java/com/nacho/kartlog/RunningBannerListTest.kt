package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.tracking.TrackingController
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.dashboard.LocalLiveClock
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A6: con el aviso «Tanda en pista» visible, el último elemento de las listas queda entero por encima de él. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class RunningBannerListTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    private class IdleSource : LocationSource {
        override fun fixes() = MutableSharedFlow<GpsFix>(extraBufferCapacity = 4)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun lastSessionCardIsFullyVisibleAboveTheBanner() {
        val container = AppContainer(context)
        container.trackingController = object : TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        val oval = OvalTrack()
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 780)
            (1..8).forEach { kart ->
                val id = container.sessionRepository.create(
                    NewSession(circuit, 1_700_000_000_000 + kart * 86_400_000L, SessionType.FREE, kart, null, null, null, TrackCondition.DRY, ""),
                )
                container.lapRepository.addManual(id, 58_000L + kart)
            }
        }
        container.timingSession.start(GpsSessionSetup(1, oval.finishLine, kartNumber = 1, type = SessionType.FREE, startedAtMs = 1L), IdleSource())
        compose.setContent { KartLogTheme { CompositionLocalProvider(LocalLiveClock provides false) { KartNavHost(container) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("session_card").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("running_banner").fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(8)
        compose.waitForIdle()
        val banner = compose.onNodeWithTag("running_banner").getBoundsInRoot()
        val cards = compose.onAllNodesWithTag("session_card").fetchSemanticsNodes().size
        val last = compose.onAllNodesWithTag("session_card")[cards - 1]
        val clipped = last.getBoundsInRoot()
        val full = last.getUnclippedBoundsInRoot()
        assertTrue("la última card entera (${clipped.bottom - clipped.top} de ${full.bottom - full.top})", (clipped.bottom - clipped.top) >= (full.bottom - full.top) - 1.dp)
        assertTrue("la última card (${clipped.bottom}) por encima del aviso (${banner.top})", clipped.bottom <= banner.top)
    }
}
