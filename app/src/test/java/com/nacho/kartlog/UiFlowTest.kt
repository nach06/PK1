package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
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

/** Recorre las pantallas reales: histórico vacío → nueva tanda → detalle → teclear una vuelta. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class UiFlowTest {
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
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun createSessionAndEnterLap() {
        val container = AppContainer(context)
        runBlocking { container.circuitRepository.add("Zuera", 1_000) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        // Histórico vacío (la primera emisión de Room llega de forma asíncrona).
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Aún no hay tandas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Aún no hay tandas").assertIsDisplayed()
        compose.onAllNodesWithText("NUEVA TANDA").onFirst().performClick()

        // El circuito (creado antes vía repositorio; el alta se cubre en SessionFlowTest) se elige en la hoja.
        compose.onNodeWithText("Elige circuito").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Zuera").fetchSemanticsNodes().isNotEmpty() }
        // Semántica en vez de toque: Robolectric (gráficos legacy) no resuelve el hit-test de la hoja redondeada.
        compose.onNodeWithText("Zuera").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Elige circuito").fetchSemanticsNodes().isEmpty() }

        // Guardar tanda -> detalle.
        compose.onNodeWithText("GUARDAR").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("VUELTA 1").fetchSemanticsNodes().isNotEmpty()
        }

        // Teclear 48532 y guardar.
        listOf("4", "8", "5", "3", "2").forEach { compose.onNodeWithText(it).performClick() }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("0:48.532").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0:48.532").assertIsDisplayed()
        compose.onNodeWithText("GUARDAR VUELTA").performClick()

        // La vuelta aparece en lista y estadísticas; el campo se limpia y el contador avanza.
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("0:48.532").fetchSemanticsNodes().size >= 2
        }
        compose.onNodeWithText("VUELTA 2").assertExists()
        compose.onNodeWithText("0:00.000").assertExists()
    }
}
