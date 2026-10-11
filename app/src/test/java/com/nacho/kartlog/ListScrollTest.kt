package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
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

/** La lista de Tandas conserva su posición de scroll al abrir un Detalle y volver. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class ListScrollTest {
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
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun returningFromDetailKeepsTheScrollPosition() {
        val container = AppContainer(context)
        runBlocking {
            val circuit = container.circuitRepository.add("Zuera", null)
            // 14 tandas, kart distinto en cada una (Kart 1 = la más antigua, Kart 14 = la más reciente).
            (1..14).forEach { kart ->
                val id = container.sessionRepository.create(
                    NewSession(circuit, 1_700_000_000_000 + kart * 86_400_000L, SessionType.FREE, kart, null, null, null, TrackCondition.DRY, ""),
                )
                container.lapRepository.addManual(id, 58_000L + kart)
            }
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Kart 14", substring = true).fetchSemanticsNodes().isNotEmpty() }

        // Bajar hasta una tanda antigua (la lista va de más reciente a más antigua).
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).performScrollToIndex(14)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Kart 3", substring = true).fetchSemanticsNodes().isNotEmpty() }
        check(compose.onAllNodesWithText("Kart 14", substring = true).fetchSemanticsNodes().isEmpty()) { "debería estar fuera de pantalla" }

        compose.onNodeWithText("Kart 3", substring = true).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("GRÁFICA DE VUELTAS").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithText("ABRIR TECLADO").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Volver").performClick()

        // Al volver sigue abajo: la tanda abierta está a la vista y la primera no.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Kart 3", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Kart 3", substring = true).assertIsDisplayed()
        check(compose.onAllNodesWithText("Kart 14", substring = true).fetchSemanticsNodes().isEmpty()) { "el scroll se perdió: volvió arriba" }
    }
}
