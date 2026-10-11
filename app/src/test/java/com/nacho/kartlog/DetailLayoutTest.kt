package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.ParticipantImport
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.detail.SessionDetailScreen
import com.nacho.kartlog.ui.detail.SessionDetailViewModel
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Detalle en una pantalla baja (360×640 dp) con 10 vueltas: reglas de F1 (gráfica/teclado). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class DetailLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private var viewModel: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        // El ViewModel se creó a mano: sin cancelar su scope seguiría consultando una BD ya cerrada.
        viewModel?.viewModelScope?.cancel()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun chartAndKeypadAreMutuallyExclusive_andSaveButtonStaysVisible() {
        val container = AppContainer(context)
        val sessionId = runBlocking {
            val circuit = container.circuitRepository.add("Zuera", null)
            val id = container.sessionRepository.create(
                NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 7, null, null, null, TrackCondition.DRY, ""),
            )
            listOf(86_000L, 72_900, 73_200, 72_600, 74_900, 72_800, 73_050, 72_450, 72_980, 72_858)
                .forEach { container.lapRepository.addManual(id, it) }
            id
        }
        val vm = SessionDetailViewModel(sessionId, container.sessionRepository, container.lapRepository)
        viewModel = vm
        compose.setContent { KartLogTheme { SessionDetailScreen(viewModel = vm, onBack = {}) } }

        // Con vueltas guardadas la tanda abre con el teclado plegado.
        waitFor("ABRIR TECLADO")

        // Abrir la gráfica: aparece y el teclado sigue plegado.
        compose.onNodeWithText("GRÁFICA DE VUELTAS").performClick()
        compose.onNodeWithTag("lap_chart").assertIsDisplayed()
        compose.onNodeWithText("ABRIR TECLADO").assertIsDisplayed()
        // Leyenda fuera del gráfico (F5): media limpia sin la salida de boxes (F3), mejor y fuera de escala.
        compose.onNodeWithText("Media limpia 1:13.082").assertIsDisplayed()
        compose.onNodeWithText("Mejor").assertIsDisplayed()
        compose.onNodeWithText("Fuera de escala").assertIsDisplayed()

        // Abrir el teclado: la gráfica se pliega y el teclado cabe entero, con GUARDAR VUELTA a la vista.
        compose.onNodeWithText("ABRIR TECLADO").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithText("GUARDAR VUELTA").fetchSemanticsNodes().isNotEmpty())
        check(compose.onAllNodesWithTag("lap_chart").fetchSemanticsNodes().isEmpty()) { "la gráfica debe plegarse" }
        compose.onNodeWithText("GUARDAR VUELTA").assertIsDisplayed()
        compose.onNodeWithText("V10 1:12.858 · V9 1:12.980").assertIsDisplayed() // últimas 2 vueltas guardadas
        listOf("1", "5", "9", "00", "0", "⌫").forEach { compose.onNodeWithText(it).assertIsDisplayed() }

        // Abrir la gráfica de nuevo: pliega el teclado.
        compose.onNodeWithText("GRÁFICA DE VUELTAS").performClick()
        compose.waitForIdle()
        check(compose.onAllNodesWithText("GUARDAR VUELTA").fetchSemanticsNodes().isEmpty()) { "el teclado debe plegarse" }
        compose.onNodeWithTag("lap_chart").assertIsDisplayed()
    }
}
