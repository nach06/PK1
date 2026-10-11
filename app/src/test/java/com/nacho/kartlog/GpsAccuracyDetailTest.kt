package com.nacho.kartlog

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
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

/** Detalle de una tanda con vueltas GPS y los tiempos oficiales importados encima: tabla de precisión (B6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class GpsAccuracyDetailTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private var vm: SessionDetailViewModel? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        vm?.viewModelScope?.cancel()
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun seed(container: AppContainer, official: List<Long>): Long = runBlocking {
        val circuit = container.circuitRepository.add("Zuera", null)
        val id = container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 7, null, null, null, TrackCondition.DRY, ""))
        container.lapRepository.addAll(id, listOf(58_460L, 57_820L, 58_150L, 59_340L), LapSource.GPS)
        container.importRepository.importSession(ImportRequest(null, id, official, null, emptyList()))
        id
    }

    private fun show(container: AppContainer, id: Long) {
        val model = SessionDetailViewModel(id, container.sessionRepository, container.lapRepository, container.participantRepository)
        vm = model
        compose.setContent { KartLogTheme { SessionDetailScreen(viewModel = model, onBack = {}) } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("ABRIR TECLADO").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("gps_accuracy"))
    }

    @Test
    fun theCardShowsMeanErrorAndTheLapByLapTable() {
        val container = AppContainer(context)
        val id = seed(container, listOf(58_400L, 57_900L, 58_120L, 59_300L))
        show(container, id)

        compose.onNodeWithTag("gps_accuracy_mean", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("gps_accuracy_mean", useUnmergedTree = true).assertTextEquals("±0.053 s") // (60+80+30+40)/4 = 52,5 ms
        compose.onNodeWithTag("gps_accuracy").performClick() // desplegar
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("gps_accuracy_summary"))
        compose.onNodeWithTag("gps_accuracy_summary").assertIsDisplayed()
        check(compose.onAllNodesWithTag("gps_accuracy_mismatch").fetchSemanticsNodes().isEmpty()) { "mismo nº de vueltas: sin aviso" }
        compose.onNodeWithText("+0.060", useUnmergedTree = true).assertExists()
    }

    @Test
    fun warnsWhenTheLapCountsDiffer() {
        val container = AppContainer(context)
        val id = seed(container, listOf(58_400L, 57_900L, 58_120L, 59_300L, 58_000L))
        show(container, id)
        compose.onNodeWithTag("gps_accuracy").performClick()
        compose.onNodeWithTag("lap_list").performScrollToNode(hasTestTag("gps_accuracy_mismatch"))
        compose.onNodeWithTag("gps_accuracy_mismatch").assertIsDisplayed()
    }
}
