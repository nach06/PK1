package com.nacho.kartlog

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sprint 8, A7: en «Marcar meta» el ajuste (±1 m, ancho), GUARDAR, AJUSTAR y VERIFICAR se alcanzan sin desplazar (393×808 y 360×640). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class CalibrationReachabilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun inside(vararg tags: String) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        tags.forEach { tag ->
            val b = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag fuera de la ventana: $b / $root", b.top >= 0.dp && b.bottom <= root.bottom)
        }
    }

    private fun insideText(vararg texts: String) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        texts.forEach { t ->
            val b = compose.onNodeWithText(t, ignoreCase = true).getUnclippedBoundsInRoot()
            assertTrue("«$t» fuera de la ventana: $b / $root", b.top >= 0.dp && b.bottom <= root.bottom)
        }
    }

    @Test
    fun markAdjustSaveAndVerifyAreReachableWithoutScrolling() {
        val container = AppContainer(context)
        container.trackingController = object : com.nacho.kartlog.data.tracking.TrackingController {
            override fun start() = Unit
            override fun stop() = Unit
        }
        container.locationSource = object : LocationSource { override fun fixes() = MutableSharedFlow<GpsFix>(extraBufferCapacity = 4) }
        runBlocking { container.circuitRepository.add("Zuera", 1_100) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Circuitos").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Sin meta").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Marcar meta").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("debug_mark_sim").fetchSemanticsNodes().isNotEmpty() }

        // Con la meta simulada marcada: ajuste ±1 m, ancho y GUARDAR dentro de la ventana.
        compose.onNodeWithTag("debug_mark_sim").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("shift_stepper").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        inside("shift_stepper", "width_stepper", "calibration_save")

        // Guardada: VERIFICAR y TERMINAR a la vista.
        compose.onNodeWithTag("calibration_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("calibration_saved").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        insideText("VERIFICAR META", "TERMINAR")
        compose.onNodeWithText("TERMINAR").performClick()

        // Otra vez en la pantalla con la meta ya guardada: MARCAR, AJUSTAR y VERIFICAR a la vez.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Meta marcada").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Marcar meta").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("AJUSTAR META", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        insideText("MARCAR", "AJUSTAR META", "VERIFICAR META")
    }
}
