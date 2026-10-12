package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
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
import org.robolectric.annotation.GraphicsMode

/** A4: en apaisado (640×360 dp) la lista de Tandas deja ver al menos una card entera por encima de la franja inferior. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w640dp-h360dp-land-xxhdpi")
class SessionListLandscapeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun atLeastOneFullCardIsVisibleInLandscape() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 780)
            (1..3).forEach { kart ->
                val id = container.sessionRepository.create(
                    NewSession(circuit, 1_700_000_000_000 + kart * 86_400_000L, SessionType.FREE, kart, null, null, null, TrackCondition.DRY, ""),
                )
                container.lapRepository.addManual(id, 58_000L + kart)
                container.lapRepository.addManual(id, 57_000L + kart)
            }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("session_card").fetchSemanticsNodes().isNotEmpty() }

        val card = compose.onAllNodesWithTag("session_card").onFirst()
        card.assertIsDisplayed()
        val b = card.getBoundsInRoot()
        val nav = compose.onNodeWithText("Ajustes").getBoundsInRoot()
        val count = compose.onNodeWithTag("list_count").getBoundsInRoot()
        // La card entera está por encima de la franja con el recuento y de la barra inferior.
        assertTrue("card (${b.top}..${b.bottom}) por encima del recuento (${count.top})", b.bottom <= count.top + 8.dp)
        assertTrue("card por encima de la barra (${nav.top})", b.bottom <= nav.top)
        val full = card.getUnclippedBoundsInRoot()
        assertTrue("card entera: visible ${b.bottom - b.top} de ${full.bottom - full.top} dp", (b.bottom - b.top) >= (full.bottom - full.top) - 1.dp)
        assertTrue("card de ${b.bottom - b.top} visible: alto útil", (b.bottom - b.top) > 60.dp)
    }
}
