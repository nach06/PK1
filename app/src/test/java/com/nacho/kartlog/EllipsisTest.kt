package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A8: el nombre muy largo del circuito se recorta con elipsis en una línea en «Nueva tanda». Necesita las métricas de
 * texto reales, por eso usa el modo gráfico NATIVO de Robolectric (el «legacy» mide 1 px por carácter y no recorta).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class EllipsisTest {
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

    private fun waitFor(text: String, ignoreCase: Boolean = true) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aVeryLongCircuitNameIsEllipsizedOnOneLineInNewSession() {
        val container = AppContainer(context)
        val longName = "Circuito Internacional de Karting Ciudad de la Velocidad y del Motor de Villaviciosa de Odón"
        runBlocking { container.circuitRepository.add(longName, 1_000) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Aún no hay tandas")
        compose.onAllNodesWithText("NUEVA TANDA")[0].performClick()
        waitFor("Elige circuito")
        compose.onNodeWithText("Elige circuito", ignoreCase = true).performClick()
        waitFor(longName)
        compose.onNodeWithText(longName).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Elige circuito", ignoreCase = true).fetchSemanticsNodes().isEmpty() }

        val node = compose.onAllNodesWithText(longName).fetchSemanticsNodes().first()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val layout = layouts.first()
        assertEquals("una sola línea", 1, layout.lineCount)
        assertTrue("debe estar recortado con elipsis", layout.isLineEllipsized(0))
        val rootWidth = compose.onAllNodesWithText(longName).fetchSemanticsNodes().first().boundsInRoot.right
        assertTrue("el texto no se sale de la pantalla", rootWidth <= 360f * context.resources.displayMetrics.density)
        assertTrue(node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == longName } == true)
    }
}
