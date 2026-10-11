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

/** Detalle de una tanda importada en un móvil tipo Pixel 3a (360×780 dp): la clasificación va al final y caben ≥ 7 vueltas. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class ClassificationLayoutTest {
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
        viewModel?.viewModelScope?.cancel()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun waitFor(text: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun classificationIsLast_andSevenLapsAreVisibleWithTheKeypadFolded() {
        val container = AppContainer(context)
        val sessionId = runBlocking {
            val circuit = container.circuitRepository.add("GP3", null)
            val mine = listOf(83_488L, 80_902, 82_209, 81_767, 79_074, 79_505, 80_100, 80_200, 80_300, 80_400)
            val others = (1..13).map { i ->
                ParticipantImport(i, "Piloto $i", 10 + i, 76_000L + i * 300, 78_000L + i * 300, 10, listOf(76_000L + i * 300))
            }
            container.importRepository.importSession(
                ImportRequest(
                    newSession = NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 28, null, null, null, TrackCondition.DRY, ""),
                    existingSessionId = null,
                    myLapTimesMs = mine,
                    me = ParticipantImport(5, "Nacho", 28, 79_074, 81_157, 10),
                    others = others,
                ),
            )
        }
        val vm = SessionDetailViewModel(sessionId, container.sessionRepository, container.lapRepository, container.participantRepository)
        viewModel = vm
        compose.setContent { KartLogTheme { SessionDetailScreen(viewModel = vm, onBack = {}) } }
        waitFor("ABRIR TECLADO")

        // ≥ 7 vueltas a la vista (V10 … V4) con la clasificación fuera de la pantalla, al final de la lista.
        val visible = (1..10).filter { n ->
            runCatching { compose.onNodeWithText("V$n").assertIsDisplayed() }.isSuccess
        }
        check(visible.size >= 7) { "solo se ven las vueltas $visible" }
        check(compose.onAllNodesWithTag("classification").fetchSemanticsNodes().isEmpty()) { "la clasificación va al final" }
        compose.onNodeWithTag("lap_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("classification"))
        compose.onNodeWithText("Clasificación de la sesión · 14 pilotos ▸").assertIsDisplayed()
    }
}
