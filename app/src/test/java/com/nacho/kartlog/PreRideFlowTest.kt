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
import com.nacho.kartlog.domain.DeviceSnapshot
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Sprint 8, B5: pantalla «Antes de salir» con verde/ámbar y enlace a los ajustes del sistema. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class PreRideFlowTest {
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
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun state(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    @Test
    fun amberItemsOpenSystemSettings_andTheMountCheckTurnsGreen() {
        val container = AppContainer(context)
        container.deviceSnapshot = { DeviceSnapshot(batteryPercent = 30, charging = false, locationEnabled = false, locationPermission = true, brightness = 40, powerSaveOn = true) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Ajustes").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tool_preride").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tool_preride").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("preride_battery").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("WARN", state("preride_battery"))
        assertEquals("WARN", state("preride_location"))
        assertEquals("OK", state("preride_permission"))
        assertEquals("WARN", state("preride_brightness"))
        assertEquals("WARN", state("preride_power_save"))
        assertEquals("WARN", state("preride_mount"))

        compose.onNodeWithTag("preride_open_location").performClick()
        val started = shadowOf(context as android.app.Application).nextStartedActivity
        assertNotNull(started)
        assertEquals(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS, started.action)

        compose.onNodeWithTag("preride_mount_check").performClick()
        compose.waitUntil(5_000) { state("preride_mount") == "OK" }
    }
}
