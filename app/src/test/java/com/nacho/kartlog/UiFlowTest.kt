package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    val compose = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun createSessionAndEnterLap() {
        val container = AppContainer(context)
        runBlocking { container.circuitRepository.add("Zuera", 1_000) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        // Histórico vacío.
        compose.onNodeWithText("Aún no hay tandas").assertIsDisplayed()
        compose.onAllNodesWithText("NUEVA TANDA").onFirst().performClick()

        // El circuito (creado antes vía repositorio; el diálogo de alta se cubre en SessionFlowTest)
        // aparece como chip y se selecciona.
        compose.onNodeWithText("Zuera").performScrollTo().performClick()

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
