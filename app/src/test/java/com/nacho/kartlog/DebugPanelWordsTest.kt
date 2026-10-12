package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sprint 8, A1: en la rejilla de Depuración (393 dp) ninguna palabra se parte entre dos líneas. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class DebugPanelWordsTest {
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
    fun noWordIsSplitAcrossLines_andEverythingFitsOnScreen() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("debug_wipe").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val texts = compose.onAllNodesWithTag("debug_wipe_text", useUnmergedTree = true).fetchSemanticsNodes() // existe
        assertTrue(texts.isNotEmpty())
        var checked = 0
        val bad = ArrayList<String>()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            val tag = n.config.getOrNull(SemanticsProperties.TestTag)
            if (tag != null && tag.startsWith("debug_") && tag.endsWith("_text")) {
                val text = n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
                val results = ArrayList<TextLayoutResult>()
                n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.firstOrNull()?.let { l ->
                    checked++
                    for (i in 1 until l.lineCount) {
                        val start = l.getLineStart(i)
                        if (start in 1..text.length - 1 && !text[start - 1].isWhitespace()) bad += "«$text» se parte en «${text.substring(0, start)}|${text.substring(start)}»"
                    }
                }
            }
            n.children.forEach(::walk)
        }
        walk(root)
        assertTrue("botones comprobados: $checked", checked >= 10)
        assertTrue("palabras partidas:\n" + bad.joinToString("\n"), bad.isEmpty())
    }
}
