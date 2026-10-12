package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** Sprint 8, D3: «Buscar» en el menú de Tandas filtra la lista por texto; ✕ la cierra y quita el filtro. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class SessionSearchFlowTest {
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

    private fun present(text: String, substring: Boolean = false) = compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun seed(container: AppContainer) = runBlocking {
        val zuera = container.circuitRepository.add("Zuera", 1_100)
        val alonso = container.circuitRepository.add("Alonso", 1_400)
        container.sessionRepository.create(NewSession(zuera, 1_760_000_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, "Presión neumáticos 0,9 bar"))
        container.sessionRepository.create(NewSession(alonso, 1_760_100_000_000, SessionType.FREE, 12, null, null, null, TrackCondition.DRY, "Lluvia fuerte"))
    }

    @Test
    fun searchFiltersTheListByText_andClosingItRestoresEverything() {
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(8_000) { present("Zuera") && present("Alonso") }

        compose.onAllNodesWithContentDescription("Más opciones").onLast().performClick()
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithTag("search_field").assertIsDisplayed()
        compose.onNodeWithTag("search_field").performTextInput("lluvia")
        compose.waitUntil(8_000) { !present("Zuera") }
        check(present("Alonso"))

        // Sin coincidencias: estado vacío con «Quitar filtros», que también borra el texto.
        compose.onNodeWithTag("search_field").performTextInput("xyz")
        compose.waitUntil(8_000) { present("Sin resultados") }
        compose.onNodeWithText("QUITAR FILTROS").performClick()
        compose.waitUntil(8_000) { present("Zuera") && present("Alonso") }

        compose.onNodeWithTag("search_field").performTextInput("neumaticos")
        compose.waitUntil(8_000) { !present("Alonso") }
        check(present("Zuera"))
        compose.onNodeWithTag("search_close").performClick()
        compose.waitUntil(8_000) { present("Alonso") && present("Zuera") }
        check(compose.onAllNodesWithTag("search_field").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theDebugShortcutFillsTheQueryWithoutTyping() {
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(8_000) { present("Zuera") && present("Alonso") }
        compose.onAllNodesWithContentDescription("Más opciones").onLast().performClick()
        compose.onNodeWithText("Buscar").performClick()
        compose.onNodeWithTag("debug_search").performClick()
        compose.waitUntil(8_000) { present("Zuera") != present("Alonso") }
    }
}
