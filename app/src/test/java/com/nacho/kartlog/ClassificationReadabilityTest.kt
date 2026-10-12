package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.ParticipantImport
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.detail.RankingColors
import com.nacho.kartlog.ui.detail.contrastRatio
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

/** Sprint 8, A3: la clasificación se lee (contraste ≥ 4,5:1 en tu fila) y los nombres largos no se cortan. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class ClassificationReadabilityTest {
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
    fun contrastOfYourRowIsAtLeast4_5() {
        val ratio = contrastRatio(RankingColors.highlightText, RankingColors.highlightBackground)
        assertTrue("contraste $ratio", ratio >= 4.5)
    }

    @Test
    fun longNamesWrapInsteadOfBeingCut_andYourRowIsMarked() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("GP3", null)
            container.importRepository.importSession(
                ImportRequest(
                    newSession = NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 28, null, null, null, TrackCondition.DRY, ""),
                    existingSessionId = null,
                    myLapTimesMs = listOf(83_488L, 80_902),
                    me = ParticipantImport(5, "Nacho Rodriguez", 28, 80_902, 81_157, 2),
                    others = listOf(
                        ParticipantImport(1, "Diego Sanchez Fernandez", 12, 76_000, 78_000, 10),
                        ParticipantImport(2, "Alvarommk l", 7, 77_000, 78_000, 10),
                        ParticipantImport(3, "Cortamargari tas", 21, 78_000, 79_000, 10),
                    ),
                ),
            )
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("GP3", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("GP3", substring = true)[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("classification"))
        compose.onNodeWithTag("classification").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("ranking_me").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        fun walk(n: SemanticsNode): List<SemanticsNode> = listOf(n) + n.children.flatMap(::walk)
        val ellipsized = ArrayList<String>()
        walk(compose.onNodeWithTag("classification", useUnmergedTree = true).fetchSemanticsNode()).forEach { n ->
            val results = ArrayList<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.let { l -> if ((0 until l.lineCount).any(l::isLineEllipsized)) ellipsized += l.layoutInput.text.text }
        }
        assertTrue("nombres cortados: $ellipsized", ellipsized.isEmpty())
        compose.onNodeWithText("Nacho Rodriguez", substring = true).assertExists()
        compose.onNodeWithText("K28").assertExists()
    }

    /** A6 (Sprint 8): la media oficial (truncada) que difiere 1 ms de la nuestra (redondeada) se enseña sin alarma. */
    @Test
    fun officialTruncatedAverage_isShownWhenItDiffersByOneMillisecond() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("GP3", null)
            container.importRepository.importSession(
                ImportRequest(
                    newSession = NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 28, null, null, null, TrackCondition.DRY, ""),
                    existingSessionId = null,
                    myLapTimesMs = listOf(80_000L, 82_315L), // media 81 157,5 → la app 81 158, la hoja (suelo) 81 157
                    me = ParticipantImport(5, "Nacho", 28, 80_000, 81_157, 2),
                    others = listOf(ParticipantImport(1, "Diego", 12, 76_000, 78_000, 10)),
                ),
            )
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("GP3", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("GP3", substring = true)[0].performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("classification"))
        compose.onNodeWithTag("classification").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("official_avg_note").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Media oficial 1:21.157", substring = true).assertExists()
    }
}
