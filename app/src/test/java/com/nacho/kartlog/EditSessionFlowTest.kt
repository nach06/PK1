package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sprint 8, D1: «Editar tanda» desde el menú del Detalle; solo cambia lo editado y conserva vueltas, título importado y calidad GPS. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class EditSessionFlowTest {
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

    @Test
    fun editingTheKartFromTheDetailUpdatesTheSession_andKeepsLapsLabelAndSignalQuality() {
        val container = AppContainer(context)
        val id = runBlocking {
            val circuit = container.circuitRepository.add("Zuera", 1_100)
            val id = container.sessionRepository.create(
                NewSession(circuit, 1_760_000_000_000, SessionType.FREE, 7, 5f, 20f, 28f, TrackCondition.DRY, "nota", label = "Sesión 46", durationMin = 10, importedRaw = "RAW", signalScore = 0),
            )
            listOf(58_000L, 59_000L).forEach { container.lapRepository.addManual(id, it) }
            id
        }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Zuera", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Zuera", substring = true).onFirst().performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithContentDescription("Más opciones").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Más opciones").onLast().performClick()
        compose.onNodeWithText("Editar tanda").performClick()

        // El formulario sale rellenado con los datos de la tanda y con título de edición.
        compose.waitUntil(8_000) { compose.onAllNodesWithContentDescription("Kart nº 7").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Editar tanda").onFirst().assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Aumentar").onFirst().performClick()
        compose.onNodeWithText("GUARDAR").performClick()

        // Vuelve al Detalle (no a uno nuevo) con el kart nuevo.
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Kart 8", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val s = runBlocking { container.sessionRepository.observeDetail(id).first() }!!.session
        assertEquals(8, s.kartNumber)
        assertEquals(5f, s.ballastKg)
        assertEquals("nota", s.notes)
        assertEquals("Sesión 46", s.label)
        assertEquals(10, s.durationMin)
        assertEquals(0, s.signalScore)
        assertEquals(2, runBlocking { container.lapRepository.observeBySession(id).first() }.size)
        assertNotNull(runBlocking { container.sessionRepository.observeSummaries().first() }.singleOrNull { it.session.id == id })
        assertEquals(1, runBlocking { container.sessionRepository.observeSummaries().first() }.size) // no se creó otra tanda
    }
}
