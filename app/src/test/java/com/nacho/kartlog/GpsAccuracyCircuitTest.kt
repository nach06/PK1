package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** La card del circuito muestra «Precisión GPS ±0,xxx s (N tandas)» cuando hay tandas con GPS y tiempos oficiales (B3). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class GpsAccuracyCircuitTest {
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
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun circuitCardShowsTheHistoricGpsError() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 1_100)
            val other = container.circuitRepository.add("Sin comparar", null)
            val id = container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 7, null, null, null, TrackCondition.DRY, ""))
            container.lapRepository.addAll(id, listOf(58_460L, 57_820L, 58_150L, 59_340L), LapSource.GPS)
            container.importRepository.importSession(ImportRequest(null, id, listOf(58_400L, 57_900L, 58_120L, 59_300L), null, emptyList()))
            container.sessionRepository.create(NewSession(other, 1_750_000_000_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""))
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Tandas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Circuitos").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("circuit_gps_accuracy", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("circuit_gps_accuracy", useUnmergedTree = true).assertTextEquals("Precisión GPS ±0.053 s (1 tanda)")
        // El circuito sin comparaciones no muestra la línea.
        check(compose.onAllNodesWithTag("circuit_gps_accuracy", useUnmergedTree = true).fetchSemanticsNodes().size == 1)
    }
}
