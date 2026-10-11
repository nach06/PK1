package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.settings.SharedPreferencesUserSettings
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.settings.SavedListFilter
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sprint 6, E3: el filtro de Tandas (circuito, tipo y orden) se recuerda entre sesiones. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class ListFilterPersistenceTest {
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
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun seed(container: AppContainer) = runBlocking {
        val zuera = container.circuitRepository.add("Zuera", 1_100)
        container.sessionRepository.create(NewSession(zuera, 1_760_000_000_000, SessionType.RACE, 7, null, null, null, TrackCondition.DRY, ""))
        container.sessionRepository.create(NewSession(zuera, 1_760_100_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""))
        zuera
    }

    private fun countText() =
        compose.onNodeWithTag("list_count").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()

    @Test
    fun aSavedFilterIsAppliedOnOpening() {
        // Una «sesión anterior» dejó el filtro «Carrera».
        SharedPreferencesUserSettings(context).setListFilter(SavedListFilter(type = SessionType.RACE.name))
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("list_count").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { countText() == "1 de 2 tandas" }
    }

    @Test
    fun changingTheFilterIsRemembered() {
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("list_count").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Libre").performClick()
        compose.waitUntil(10_000) { countText() == "1 de 2 tandas" }
        assertEquals(SessionType.FREE.name, SharedPreferencesUserSettings(context).listFilter.value.type)
        // Quitarlo también se recuerda.
        compose.onNodeWithText("Libre").performClick()
        compose.waitUntil(10_000) { countText() == "2 tandas" }
        assertNull(SharedPreferencesUserSettings(context).listFilter.value.type)
    }

    @Test
    fun aSavedFilterForADeletedCircuitDoesNotEmptyTheList() {
        SharedPreferencesUserSettings(context).setListFilter(SavedListFilter(circuitId = 9_999L))
        val container = AppContainer(context)
        seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("list_count").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { countText() == "2 tandas" }
        compose.onAllNodesWithText("Zuera")[0]
    }
}
