package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Detalle → borrar una vuelta intermedia: la lista sigue V8…V1 sin huecos (A2), por el `NavHost` real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1200dp-xxhdpi")
class LapDeleteFlowTest {
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

    private fun waitFor(text: String) = compose.waitUntil(8_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun present(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun deletingV6_leavesV1ToV7_andRecomputesTheBest() {
        val container = AppContainer(context)
        val sessionId = runBlocking {
            val circuit = container.circuitRepository.add("Zuera", null)
            val id = container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 7, null, null, null, TrackCondition.DRY, ""))
            // V6 = 1:10.000 es la mejor: al borrarla el mejor pasa a 1:12.500.
            listOf(80_000L, 73_000L, 72_500L, 72_800L, 73_100L, 70_000L, 73_400L, 72_900L).forEach { container.lapRepository.addManual(id, it) }
            id
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performClick()
        waitFor("V8")
        (1..8).forEach { check(present("V$it")) { "falta V$it al abrir" } }
        waitFor("1:10.000")

        // Menú ⋮ de las filas, la más reciente primero: [0] = V8, [1] = V7, [2] = V6 (el de la barra superior va aparte).
        compose.onAllNodesWithContentDescription("Más opciones")[2].performClick()
        compose.onNodeWithText("Borrar").performClick()
        waitFor("¿Borrar vuelta 6?")
        compose.onNodeWithText("BORRAR").performClick()

        compose.waitUntil(8_000) { !present("V8") }
        (1..7).forEach { check(present("V$it")) { "falta V$it tras borrar" } }
        check(!present("V8")) { "no debe quedar V8" }
        check(!present("1:10.000")) { "la vuelta borrada no debe seguir en la lista" }
        // Mejor vuelta recalculada (1:12.500) y numeración consecutiva en la BD.
        waitFor("1:12.500")
        // Con el borrado ya confirmado de verdad (sin esperar los 8 s del «Deshacer») la BD tiene la numeración consecutiva.
        container.pendingDeletions.flush()
        val deadline = System.currentTimeMillis() + 8_000
        while (container.pendingDeletions.hiddenItems.value.isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        check(container.pendingDeletions.hiddenItems.value.isEmpty()) { "el borrado no se confirmó" }
        val laps = runBlocking { container.lapRepository.observeBySession(sessionId).first() }
        check(laps.map { it.number } == (1..7).toList()) { "numeración: ${laps.map { it.number }}" }
    }
}
