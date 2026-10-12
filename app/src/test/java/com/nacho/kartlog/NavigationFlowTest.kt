package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Recorre las 4 pestañas con datos reales y comprueba que la barra se oculta en Nueva tanda. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class NavigationFlowTest {
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

    private fun seed(container: AppContainer) = runBlocking {
        val zuera = container.circuitRepository.add("Zuera", 1_100)
        container.circuitRepository.add("Vacío", null)
        fun session(date: Long, kart: Int) = NewSession(zuera, date, SessionType.RACE, kart, null, 20f, 30f, TrackCondition.DRY, "")
        val s1 = container.sessionRepository.create(session(1_760_000_000_000, 7))
        val s2 = container.sessionRepository.create(session(1_761_000_000_000, 9))
        listOf(58_400L, 57_900L, 58_100L).forEach { container.lapRepository.addManual(s1, it) }
        listOf(58_000L, 57_500L, 57_800L).forEach { container.lapRepository.addManual(s2, it) }
    }

    @Test
    fun tabsShowTheirContent_andBarHidesOutsideTopLevel() {
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        // Tandas: card con delta/RÉCORD (la más reciente tiene el récord 0:57.500).
        compose.waitUntil(5_000) { compose.onAllNodesWithText("RÉCORD").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0:57.500", substring = true).assertExists()
        // F8: la fila de filtros arranca sin desplazamiento (primer chip completo a la vista).
        compose.onNodeWithText("Circuito ▾").assertIsDisplayed()
        compose.onNodeWithText("Libre").assertIsDisplayed()

        // Análisis: récord y ranking de karts.
        compose.onNodeWithText("Análisis").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Ranking de karts".uppercase()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1. #9").assertExists()

        // Circuitos: lista con el circuito vacío y borrado bloqueado en el que tiene tandas.
        compose.onNodeWithText("Circuitos").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Vacío").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 tandas").assertExists()

        // Ajustes: exportar y versión.
        compose.onNodeWithText("Ajustes").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("EXPORTAR TODO A CSV").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Exportar todo a CSV".uppercase()).assertIsDisplayed()
        // T0: la línea de versión identifica la build (hash de git y hora de compilación).
        compose.onNodeWithText("build ${BuildConfig.GIT_HASH} · ${BuildConfig.BUILD_TIME}", substring = true).assertExists()

        // Nueva tanda: la barra inferior desaparece.
        compose.onNodeWithText("Tandas").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("fab_main").fetchSemanticsNodes().isNotEmpty() }
        // A4: el botón principal es desplegable: «+ NUEVA TANDA» e «IMPORTAR TIEMPOS».
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_import").assertIsDisplayed()
        compose.onNodeWithText("IMPORTAR TIEMPOS").assertIsDisplayed()
        compose.onNodeWithTag("fab_new").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Circuito".uppercase()).fetchSemanticsNodes().isNotEmpty() }
        check(compose.onAllNodesWithText("Circuitos").fetchSemanticsNodes().isEmpty()) { "la barra debe ocultarse en Nueva tanda" }

        // El mismo botón desplegable lleva a Importar tiempos.
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("fab_main").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("fab_main").performClick()
        compose.onNodeWithTag("fab_import").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("import_main").fetchSemanticsNodes().isNotEmpty() }
    }
}
