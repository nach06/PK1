package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * Prueba de **conexión**: parte del `NavHost` real (no de composables sueltos) y comprueba por la ruta
 * que llega el usuario que los fixes de los Sprints 2.1/2.2 están a la vista. Si un composable vuelve a
 * quedar desconectado de su pantalla, este test falla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h720dp-xxhdpi")
class ConnectedFlowTest {
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
        // Cerrar la actividad cancela los ViewModels y sus consultas Room antes de borrar la BD.
        compose.activityRule.scenario.close()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    /** Zuera: 1 tanda reciente con 10 vueltas (V1 = salida de boxes). Beta: 2 tandas antiguas con vueltas. */
    private fun seed(container: AppContainer) = runBlocking {
        fun session(circuit: Long, date: Long, kart: Int) =
            NewSession(circuit, date, SessionType.RACE, kart, null, 20f, 30f, TrackCondition.DRY, "")
        val zuera = container.circuitRepository.add("Zuera", 1_100)
        val beta = container.circuitRepository.add("Beta", 900)
        val long = container.sessionRepository.create(session(zuera, 1_761_000_000_000, 7))
        listOf(86_000L, 72_900, 73_200, 72_600, 74_900, 72_800, 73_050, 72_450, 72_980, 72_858)
            .forEach { container.lapRepository.addManual(long, it) }
        listOf(1_750_000_000_000 to 58_400L, 1_751_000_000_000 to 57_900L).forEach { (date, ms) ->
            val id = container.sessionRepository.create(session(beta, date, 9))
            container.lapRepository.addManual(id, ms)
        }
    }

    @Test
    fun fixesAreReachableThroughTheRealNavHost() {
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        // --- Tandas → Detalle ---
        waitFor("Circuito ▾") // F8: chip con flecha, arranca a la vista
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performClick()
        waitFor("GRÁFICA DE VUELTAS")

        // F5/F3/F4: gráfica con leyenda fuera del área de dibujo, media limpia sin la salida de boxes.
        compose.onNodeWithText("GRÁFICA DE VUELTAS").performClick()
        compose.onNodeWithTag("lap_chart").assertIsDisplayed()
        compose.onNodeWithText("Media limpia 1:13.082").assertIsDisplayed()
        compose.onNodeWithText("±0,", substring = true).assertExists() // consistencia junto a MEDIA LIMPIA
        compose.onNodeWithText("Mejor").assertIsDisplayed()
        compose.onNodeWithText("Fuera de escala").assertIsDisplayed()

        // F1/F2: abrir el teclado pliega la gráfica; teclado completo y franja de las 2 últimas vueltas.
        compose.onNodeWithText("ABRIR TECLADO").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithTag("lap_chart").fetchSemanticsNodes().isEmpty()) { "la gráfica debe plegarse" }
        compose.onNodeWithText("V10 1:12.858 · V9 1:12.980").assertIsDisplayed()
        // P1: con el teclado abierto la lista conserva sitio (la última vuelta se ve) y termina antes de la franja.
        val lastRow = compose.onNodeWithText("V10").fetchSemanticsNode().boundsInRoot
        val strip = compose.onNodeWithText("V10 1:12.858 · V9 1:12.980").fetchSemanticsNode().boundsInRoot
        check(lastRow.height > 0f) { "la lista de vueltas no puede quedarse sin altura con el teclado abierto" }
        check(lastRow.bottom <= strip.top) { "la lista no debe pasar por debajo de la franja de las 2 últimas vueltas" }
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫", "GUARDAR VUELTA")
            .forEach { compose.onNodeWithText(it).assertIsDisplayed() }

        // --- Detalle → Análisis ---
        compose.onNodeWithContentDescription("Volver").performClick()
        waitFor("Análisis")
        compose.onNodeWithText("Análisis").performClick()

        // F7: por defecto, el circuito con más tandas con vueltas (Beta: 2), no el de la tanda más reciente (Zuera).
        waitFor("Beta")
        compose.onNodeWithText("RÉCORD").assertIsDisplayed()
        compose.onNodeWithText("MEDIA LIMPIA").assertIsDisplayed()
        compose.onNodeWithText("TANDAS").assertIsDisplayed()
        compose.onNodeWithText("CONSISTENCIA").assertIsDisplayed() // cuarta cifra

        // F7: la elección se recuerda al cambiar de pestaña.
        compose.onNodeWithText("Beta").performClick() // abre el selector
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Beta").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Circuitos").performClick()
        waitFor("Beta") // ahora es una card de la lista de circuitos
        compose.onNodeWithText("Análisis").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithText("Beta").fetchSemanticsNodes().isEmpty()) { "debe seguir en Zuera" }
        compose.onNodeWithText("Zuera").assertIsDisplayed()

        // --- Ajustes: T0 (build) y F9 (botones) ---
        compose.onNodeWithText("Ajustes").performClick()
        waitFor("EXPORTAR TODO A CSV")
        compose.onNodeWithText("build ${BuildConfig.GIT_HASH} · ${BuildConfig.BUILD_TIME}", substring = true).assertExists()
    }
}
