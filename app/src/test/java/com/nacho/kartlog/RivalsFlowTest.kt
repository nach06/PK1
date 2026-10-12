package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.ParticipantImport
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

/** Sprint 8, C: Rivales, cara a cara, condiciones, «Unir pilotos» y aviso de pocas muestras en karts, por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class RivalsFlowTest {
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
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun openAnalysis() {
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Tandas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Análisis").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("open_rivals").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openRivals() {
        openAnalysis()
        compose.onNodeWithTag("open_rivals").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("conditions_card").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun rivalsListShowsEachDriverOnce_andTheHeadToHeadOpensFromARow() {
        val container = AppContainer(context)
        runBlocking { container.debugTools!!.seed() }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        openRivals()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("rival_row_carlosruiz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rival_row_carlosruiz").assertIsDisplayed()
        compose.onNodeWithTag("rival_row_luciaperez").assertIsDisplayed()
        compose.onNodeWithTag("rival_pace_carlosruiz", useUnmergedTree = true).assertTextContains("Tu ritmo", substring = true)

        compose.onNodeWithTag("rival_row_carlosruiz").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("h2h_card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("h2h_best", useUnmergedTree = true).assertTextContains("ganadas", substring = true)
        compose.onNodeWithTag("h2h_avg", useUnmergedTree = true).assertTextContains("perdidas", substring = true)
        compose.onNodeWithTag("h2h_streak", useUnmergedTree = true).assertTextContains("racha", substring = true)
        compose.onNodeWithTag("h2h_chart").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun mergingTwoSpellingsLeavesOneRival_persistsTheAlias_andCanBeUndone() {
        val container = AppContainer(context)
        runBlocking { container.debugTools!!.seed() }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        openRivals()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("rival_row_cruiz").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rivals_merge_toggle").performClick()
        compose.onNodeWithTag("rivals_merge_hint").assertIsDisplayed()
        compose.onNodeWithTag("rival_row_carlosruiz").performClick()
        compose.onNodeWithTag("rival_row_cruiz").performClick()
        compose.onNodeWithTag("merge_dialog").assertIsDisplayed()
        compose.onNodeWithTag("merge_confirm").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("rival_row_cruiz").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("rival_row_carlosruiz").assertIsDisplayed()
        check(container.userSettings.driverAliases.value == mapOf("cruiz" to "carlosruiz")) { container.userSettings.driverAliases.value.toString() }

        compose.onNodeWithTag("rivals_merge_clear").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("rival_row_cruiz").fetchSemanticsNodes().isNotEmpty() }
        check(container.userSettings.driverAliases.value.isEmpty())
    }

    @Test
    fun conditionsShowTheCorrelationWithEightSessions_andExplainWhyNotWithFewer() {
        val container = AppContainer(context)
        runBlocking { container.debugTools!!.seed() } // 13 tandas con vueltas
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        openRivals()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("cond_TRACK_TEMP").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Temperatura de pista").performScrollTo().assertIsDisplayed()
        check(compose.onAllNodesWithTag("conditions_need_more").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun conditionsAreNotShownWithFewerThanEightSessions() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 1_100)
            repeat(3) { i ->
                val id = container.sessionRepository.create(NewSession(circuit, 1_750_000_000_000 + i * 86_400_000L, SessionType.FREE, 7, null, 20f, 25f + i, TrackCondition.DRY, ""))
                listOf(58_000L, 58_500L).forEach { container.lapRepository.addManual(id, it) }
            }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        openRivals()
        compose.onNodeWithTag("conditions_need_more", useUnmergedTree = true).assertTextContains("al menos 8", substring = true)
        compose.onNodeWithTag("rivals_empty").assertIsDisplayed()
        check(compose.onAllNodesWithTag("cond_TRACK_TEMP").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun kartRankingWarnsWhenAKartHasFewerThanThreeSamples() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 1_100)
            // Kart 7: 3 tandas (sin aviso). Kart 12: 2 tandas (con aviso).
            listOf(7, 7, 7, 12, 12).forEachIndexed { i, kart ->
                val id = container.sessionRepository.create(NewSession(circuit, 1_750_000_000_000 + i * 86_400_000L, SessionType.FREE, kart, null, null, null, TrackCondition.DRY, ""))
                listOf(58_000L + i * 100, 58_500L).forEach { container.lapRepository.addManual(id, it) }
            }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        openAnalysis()
        compose.onNodeWithTag("kart_few_12", useUnmergedTree = true).performScrollTo().assertTextContains("Pocas muestras", substring = true)
        check(compose.onAllNodesWithTag("kart_few_7", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
