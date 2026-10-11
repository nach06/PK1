package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
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

/**
 * Cubre por el `NavHost` real los fixes del 2.1 que `ConnectedFlowTest` no tocaba: F4 (gráfica completa sin
 * scroll), F6 (MEDIA LIMPIA dentro de la pantalla), F8 (margen inferior de la lista) y F9 (botones de
 * Ajustes). La elipsis del circuito en Nueva tanda no se puede verificar aquí: Robolectric no mide el texto
 * como un dispositivo real (queda en la lista de comprobación visual).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xxhdpi")
class NavHostFixesTest {
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
        Thread.sleep(300) // deja terminar las consultas Room en vuelo antes de borrar la BD
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun waitFor(text: String, ignoreCase: Boolean = false) = compose.waitUntil(8_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()
    }

    private fun session(circuit: Long, date: Long) =
        NewSession(circuit, date, SessionType.RACE, 7, null, 20f, 30f, TrackCondition.DRY, "")

    @Test
    fun f4_f6_chartFitsTheWidthWithoutScroll_andCleanAverageIsInsideTheScreen() {
        val container = AppContainer(context)
        runBlocking {
            val zuera = container.circuitRepository.add("Zuera", 1_100)
            val id = container.sessionRepository.create(session(zuera, 1_761_000_000_000))
            listOf(86_000L, 72_900, 73_200, 72_600, 74_900, 72_800, 73_050, 72_450, 72_980, 72_858)
                .forEach { container.lapRepository.addManual(id, it) }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performClick()
        waitFor("GRÁFICA DE VUELTAS")

        // F6: MEDIA LIMPIA con su valor completo, dentro de la pantalla (sin recorte por el borde derecho).
        val rootWidth = compose.onRoot().fetchSemanticsNode().boundsInRoot.right
        compose.onNodeWithText("1:13.082").assertIsDisplayed()
        val value = compose.onNodeWithText("1:13.082").fetchSemanticsNode().boundsInRoot
        check(value.right <= rootWidth && value.left >= 0f) { "MEDIA LIMPIA se sale de la pantalla: $value (ancho $rootWidth)" }
        compose.onNodeWithText("Media limpia", ignoreCase = true).assertIsDisplayed()

        // F4: la gráfica cabe en el ancho (todas las vueltas visibles) y no es desplazable horizontalmente.
        compose.onNodeWithText("GRÁFICA DE VUELTAS").performClick()
        val chart = compose.onNodeWithTag("lap_chart").fetchSemanticsNode()
        check(chart.boundsInRoot.left >= 0f && chart.boundsInRoot.right <= rootWidth) { "la gráfica se sale del ancho: ${chart.boundsInRoot}" }
        check(chart.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) == null) { "la gráfica no debe desplazarse" }
        check(!chart.config.contains(SemanticsActions.ScrollBy)) { "la gráfica no debe tener scroll" }
    }

    @Test
    fun f8_a1_theFabNeverCoversCardText_andTheLastCardClearsIt() {
        val container = AppContainer(context)
        runBlocking {
            val zuera = container.circuitRepository.add("Zuera", 1_100)
            (0 until 8).forEach { i ->
                val id = container.sessionRepository.create(session(zuera, 1_750_000_000_000 + i * 86_400_000L))
                container.lapRepository.addManual(id, 58_000L + i * 100)
                container.lapRepository.addManual(id, 59_000L + i * 100)
            }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Zuera")
        val list = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        val density = context.resources.displayMetrics.density

        fun assertNothingUnderTheFab(where: String) {
            compose.waitForIdle()
            val fab = compose.onNodeWithTag("fab_main").fetchSemanticsNode().boundsInRoot
            val listBounds = list.fetchSemanticsNode().boundsInRoot
            check(listBounds.bottom <= fab.top) { "$where: la lista ($listBounds) llega hasta la FAB ($fab)" }
            // Ningún texto visible de las cards se solapa con la FAB.
            val covered = compose.onAllNodes(hasText("", substring = true), useUnmergedTree = true).fetchSemanticsNodes()
                .filter { it.boundsInRoot.overlaps(fab) && it.boundsInRoot.width > 0f }
                .filter { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { t -> t.text.isNotBlank() } == true }
                .filter { n -> n.config.getOrNull(SemanticsProperties.TestTag) != "fab_main" }
                .filter { n -> n.config.getOrNull(SemanticsProperties.Text)?.none { t -> t.text.contains("NUEVA TANDA") } == true }
            check(covered.isEmpty()) { "$where: la FAB tapa texto: " + covered.map { it.config.getOrNull(SemanticsProperties.Text) } }
        }

        assertNothingUnderTheFab("al abrir")
        list.performScrollToIndex(8) // última posición (8 cards + cabeceras de mes)
        assertNothingUnderTheFab("al final")
        val fabTop = compose.onNodeWithTag("fab_main").fetchSemanticsNode().boundsInRoot.top
        val lastCard = compose.onAllNodesWithText("Zuera").fetchSemanticsNodes().maxBy { it.boundsInRoot.bottom }.boundsInRoot
        check(fabTop - lastCard.bottom >= 24f * density - 1f) { "la última card debe quedar ≥ 24 dp sobre la FAB: ${fabTop - lastCard.bottom}" }

        // Desplegada, el fondo se oscurece (scrim) y tocar fuera la cierra.
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_scrim").assertIsDisplayed()
        compose.onNodeWithTag("fab_scrim").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithTag("fab_scrim").fetchSemanticsNodes().isEmpty()) { "el scrim debe desaparecer al cerrar" }
    }

    @Test
    fun f9_settingsDataButtonsAreVisible() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        // Ajustes: los botones de datos son secundarios (contorno) y están a la vista.
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        waitFor("EXPORTAR TODO A CSV")
        compose.onNodeWithText("EXPORTAR TODO A CSV").assertIsDisplayed()
        compose.onNodeWithText("BORRAR DATOS DE RIVALES").assertIsDisplayed()
        // B5: copia de seguridad completa (exportar / restaurar).
        compose.onNodeWithText("EXPORTAR COPIA").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("RESTAURAR COPIA").performScrollTo().assertIsDisplayed()
    }
}
