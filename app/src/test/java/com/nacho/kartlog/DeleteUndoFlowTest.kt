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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sprint 8, D2: tras borrar una vuelta o una tanda sale «Deshacer» (8 s) y recupera el dato; sin deshacer, se borra de verdad. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class DeleteUndoFlowTest {
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

    private fun waitFor(text: String, substring: Boolean = false, timeout: Long = 8_000) = compose.waitUntil(timeout) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun present(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun seed(container: AppContainer): Long = runBlocking {
        val circuit = container.circuitRepository.add("Zuera", 1_100)
        val id = container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.RACE, 7, null, null, null, TrackCondition.DRY, ""))
        listOf(60_000L, 61_000L, 62_000L, 63_000L).forEach { container.lapRepository.addManual(id, it) }
        id
    }

    private fun deleteLap3() {
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performClick()
        waitFor("V4")
        // Filas de la más reciente a la más antigua: [0] = V4, [1] = V3.
        compose.onAllNodesWithContentDescription("Más opciones")[1].performClick()
        compose.onNodeWithText("Borrar").performClick()
        waitFor("¿Borrar vuelta 3?")
        compose.onNodeWithText("BORRAR").performClick()
    }

    @Test
    fun undoingALapDeleteBringsTheLapBack_withoutTouchingTheDatabase() {
        val container = AppContainer(context)
        val id = seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        deleteLap3()
        waitFor("Vuelta 3 borrada")
        compose.waitUntil(8_000) { !present("V4") } // oculta y renumerada al instante
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(8_000) { present("V4") }
        assertEquals(4, runBlocking { container.lapRepository.observeBySession(id).first() }.size)
        // Pasada la ventana no se borra nada: se deshizo.
        Thread.sleep(300)
        assertEquals(listOf(60_000L, 61_000L, 62_000L, 63_000L), runBlocking { container.lapRepository.observeBySession(id).first() }.map { it.timeMs })
    }

    @Test
    fun withoutUndoTheLapIsReallyDeletedAfterTheWindow() {
        val container = AppContainer(context)
        val id = seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        deleteLap3()
        waitFor("Vuelta 3 borrada")
        compose.waitUntil(15_000) { container.pendingDeletions.current.value == null && container.pendingDeletions.hiddenItems.value.isEmpty() }
        val laps = runBlocking { container.lapRepository.observeBySession(id).first() }
        assertEquals(listOf(60_000L, 61_000L, 63_000L), laps.map { it.timeMs })
        assertEquals(listOf(1, 2, 3), laps.map { it.number })
    }

    @Test
    fun undoingASessionDeleteFromTheListBringsTheSessionBack() {
        val container = AppContainer(context)
        val id = seed(container)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Zuera")
        compose.onNodeWithText("Zuera").performClick()
        waitFor("V4")
        compose.onAllNodesWithContentDescription("Más opciones").let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        compose.onNodeWithText("Borrar tanda").performClick()
        waitFor("BORRAR")
        compose.onNodeWithText("BORRAR").performClick()
        waitFor("Tanda borrada")
        compose.waitUntil(8_000) { !present("Zuera") } // vuelve a la lista y la tanda ya no se ve
        compose.onNodeWithText("Deshacer").performClick()
        compose.waitUntil(8_000) { present("Zuera") }
        assertEquals(1, runBlocking { container.sessionRepository.observeSummaries().first() }.size)
        assertEquals(4, runBlocking { container.lapRepository.observeBySession(id).first() }.size)
    }
}
