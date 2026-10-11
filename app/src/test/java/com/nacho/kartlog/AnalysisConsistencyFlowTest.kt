package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
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

/** Análisis: hueco de mejora y consistencia por tanda con su tendencia (B6), por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class AnalysisConsistencyFlowTest {
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
    fun analysisShowsTheImprovementGapAndTheConsistencyTrend() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 1_100)
            // Tandas cada vez más regulares (desviación 0,5 → 0,1 s) en fechas distintas.
            listOf(500L, 350L, 200L, 100L).forEachIndexed { i, spread ->
                val id = container.sessionRepository.create(
                    NewSession(circuit, 1_750_000_000_000 + i * 86_400_000L, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""),
                )
                listOf(-spread, spread, -spread, spread).forEach { container.lapRepository.addManual(id, 58_000 + it) }
            }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Tandas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Análisis").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("RÉCORD").fetchSemanticsNodes().isNotEmpty() }

        // Récord 57,500 (tanda 1: 58,000−500); media limpia de todas las limpias; hueco = media − récord.
        compose.onNodeWithTag("improvement_gap").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("improvement_gap_value", useUnmergedTree = true).assertTextContains("+", substring = true)
        compose.onNodeWithTag("consistency_section").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("consistency_trend", useUnmergedTree = true).assertTextContains("Cada vez más regular", substring = true)
    }
}
