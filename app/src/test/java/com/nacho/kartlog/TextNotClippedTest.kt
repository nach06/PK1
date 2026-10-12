package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.importing.SharedContent
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
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sprint 7, E4: en una pantalla pequeña (360×640 dp) y con la letra al 130 %, ningún texto de las pantallas principales queda
 * recortado (ni con puntos suspensivos ni cortado por el borde) ni se sale de la ventana. Usa el modo gráfico NATIVO para medir
 * el texto de verdad.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class TextNotClippedTest(private val screen: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): Collection<Array<Any>> = listOf("Tandas", "Detalle", "Circuitos", "Análisis", "Ajustes", "Revisión").map { arrayOf<Any>(it) }

        const val FONT_SCALE = 1.3f
    }

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

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

    /**
     * Motivo por el que el texto está recortado, o `null` si se ve entero. `hasVisualOverflow`, `didExceedMaxLines` y `getLineEnd` dan
     * falsos positivos con las métricas del modo nativo de Robolectric (marcan como cortados textos que caben de sobra), así que se
     * mira cada causa por separado: puntos suspensivos, línea más ancha que el máximo o alto mayor que el máximo.
     */
    private fun clippedReason(layout: TextLayoutResult): String? {
        val last = layout.lineCount - 1
        if ((0..last).any(layout::isLineEllipsized)) return "termina en puntos suspensivos"
        val c = layout.layoutInput.constraints
        if (c.hasBoundedWidth && (0..last).any { layout.getLineRight(it) > c.maxWidth + 1f }) return "es más ancho que su caja"
        if (c.hasBoundedHeight && layout.getLineBottom(last) > c.maxHeight + 1f) return "es más alto que su caja"
        return null
    }

    private fun allNodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::allNodes)

    @Test
    fun noMainTextIsClipped() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 1_100)
            val other = container.circuitRepository.add("Alonso", 1_400)
            listOf(circuit to 2, circuit to 5, other to 9).forEachIndexed { i, (c, kart) ->
                val id = container.sessionRepository.create(
                    NewSession(c, 1_760_000_000_000 - i * 86_400_000L, SessionType.FREE, kart, 4f, 21f, 28f, TrackCondition.DRY, ""),
                )
                repeat(8) { container.lapRepository.addManual(id, 58_000L + it * 137 + i * 1_000) }
            }
        }
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = FONT_SCALE)) {
                KartLogTheme { KartNavHost(container) }
            }
        }
        waitFor("Tandas")
        when (screen) {
            "Tandas" -> waitForTag("session_card")
            "Detalle" -> {
                waitForTag("session_card")
                compose.onAllNodesWithTag("session_card").onFirst().performClick()
                waitForTag("lap_list")
            }
            "Circuitos" -> { compose.onNodeWithText("Circuitos").performClick(); waitFor("Alonso") }
            "Análisis" -> { compose.onNodeWithText("Análisis").performClick(); compose.waitForIdle() }
            "Ajustes" -> { compose.onNodeWithText("Ajustes").performClick(); waitFor("Mi perfil") }
            "Revisión" -> {
                val text = (1..12).joinToString("\n") { "$it\t1:${10 + it}.${100 + it}" }
                container.importInbox.put(SharedContent.Text(text))
                waitForTag("review_row_1")
            }
        }
        compose.waitForIdle()
        Thread.sleep(300) // deja que acaben las animaciones de entrada
        compose.waitForIdle()

        val root = compose.onRoot().fetchSemanticsNode()
        val width = root.boundsInRoot.right
        val offenders = ArrayList<String>()
        allNodes(root).forEach { node ->
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }?.takeIf { it.isNotBlank() } ?: return@forEach
            val results = ArrayList<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.firstOrNull() ?: return@forEach
            clippedReason(layout)?.let { offenders += "«${text.take(40)}» $it" }
            val b = node.boundsInRoot
            if (b.width > 0f && (b.left < -1f || b.right > width + 1f)) offenders += "«${text.take(40)}» se sale de la ventana ($b)"
        }
        assertTrue("[$screen] textos recortados con letra ${FONT_SCALE}×:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
