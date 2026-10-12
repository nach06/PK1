package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking
import com.nacho.kartlog.domain.gps.OvalTrack

/** Sprint 8, B3: Ajustes → Herramientas → Calibración en pista: informe GPS − manual y «Aplicar compensación». */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class FieldCalibrationFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @Test
    fun simulatedCalibrationShowsTheBiasTable_andApplyingSetsTheDelay() {
        val container = AppContainer(context)
        runBlocking { container.circuitRepository.add("Óvalo", 780).also { container.circuitRepository.setFinishLine(it, OvalTrack().finishLine) } }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tool_fieldcal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tool_fieldcal").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("fieldcal_debug").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("fieldcal_debug").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("fieldcal_report").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+300 ms", substring = true).assertExists()
        listOf(1, 2).forEach { compose.onNodeWithTag("fieldcal_lap_$it").assertExists() }
        compose.onNodeWithTag("fieldcal_apply").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("fieldcal_applied").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(300, container.userSettings.gpsDelayMs.value)
        assertTrue(compose.onAllNodesWithTag("fieldcal_apply").fetchSemanticsNodes().isEmpty())
    }
}
