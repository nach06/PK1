package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Borrar una tanda desde el Detalle borra también su trazado `tracks/{id}.csv` (A7), por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class TrackCleanupFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        Thread.sleep(300)
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    private fun waitFor(text: String) = compose.waitUntil(8_000) {
        compose.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun deletingASessionRemovesItsTrack_andLeavesTheOthers() {
        val container = AppContainer(context)
        val (keep, drop) = runBlocking {
            val circuit = container.circuitRepository.add("Zuera", null)
            fun session(date: Long) = NewSession(circuit, date, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, "")
            val keep = container.sessionRepository.create(session(1_760_000_000_000))
            val drop = container.sessionRepository.create(session(1_770_000_000_000)) // la más reciente: primera de la lista
            val fix = GpsFix(1_000_000_000L, 41.86, -0.79, 10f, 4f)
            container.trackStore.save(keep, listOf(fix))
            container.trackStore.save(drop, listOf(fix))
            keep to drop
        }
        val trackFile = { id: Long -> File(context.filesDir, "tracks/$id.csv") }
        assertTrue(trackFile(keep).exists() && trackFile(drop).exists())
        compose.setContent { KartLogTheme { KartNavHost(container) } }

        waitFor("Zuera")
        compose.onAllNodesWithText("Zuera").onFirst().performClick() // abre la más reciente (drop)
        waitFor("Aún no hay vueltas. Teclea el tiempo y pulsa GUARDAR VUELTA.")
        compose.onAllNodesWithContentDescription("Más opciones").onFirst().performClick()
        compose.onNodeWithText("Borrar tanda").performClick()
        waitFor("¿Borrar tanda?")
        compose.onNodeWithText("BORRAR").performClick()

        // El borrado termina en un hilo de IO: se sondea con tiempo real (waitUntil no cede el hilo a IO).
        val deadline = System.currentTimeMillis() + 8_000
        while (trackFile(drop).exists() && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
            compose.waitForIdle()
        }
        assertFalse("el trazado de la tanda borrada debe desaparecer", trackFile(drop).exists())
        assertTrue("el de la otra tanda se conserva", trackFile(keep).exists())
    }
}
