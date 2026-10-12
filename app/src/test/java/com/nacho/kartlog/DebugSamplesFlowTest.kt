package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Ajustes → Depuración → «Muestra A (texto)/B» abre Importar tiempos con el texto ya analizado (A4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class DebugSamplesFlowTest {
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

    private fun waitFor(text: String, ignoreCase: Boolean = true, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun sampleA_opensTheImporterWithTheEmailAnalysed() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick() // la sección Depuración arranca plegada
        waitFor("Muestra A (texto)")
        compose.onNodeWithText("Muestra A (texto)", ignoreCase = true).performScrollTo().performClick()
        waitForTag("who_banner")
        waitFor("Nacho · Kart 28", substring = true)
    }

    @Test
    fun b3_sampleAAsNacho_reachesSaveWithJustClicks() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        waitForTag("debug_text_a_me")
        compose.onNodeWithTag("debug_text_a_me").performClick()

        // Sin «¿Cuál eres tú?», con el circuito GP3 creado y GUARDAR activo: un toque más y se guarda.
        waitForTag("review_save")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("who_banner").fetchSemanticsNodes().isEmpty() }
        check(compose.onAllNodesWithTag("save_blocked").fetchSemanticsNodes().isEmpty()) { "GUARDAR no debe estar bloqueado" }
        compose.onNodeWithTag("review_save").assertIsEnabled()
        compose.onNodeWithTag("review_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun sampleB_opensTheImporterWithTheKartSheetAnalysed() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        waitFor("Muestra B (texto)")
        compose.onNodeWithText("Muestra B (texto)", ignoreCase = true).performScrollTo().performClick()
        waitForTag("who_banner")
        waitFor("Kart 28", substring = true)
    }

    @Test
    fun debugSectionIsTheFirstCardOfSettings_andFolds() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        waitFor("Mi perfil")

        // Al principio de Ajustes (encima de «Mi perfil» y de los datos) y plegada: sin botones a la vista.
        val debugTop = compose.onNodeWithTag("debug_section").fetchSemanticsNode().boundsInRoot.top
        val profileTop = compose.onNodeWithText("Mi perfil", ignoreCase = true).fetchSemanticsNode().boundsInRoot.top
        check(debugTop < profileTop) { "Depuración ($debugTop) debe ir antes de Mi perfil ($profileTop)" }
        check(compose.onAllNodesWithText("Simular tanda", ignoreCase = true).fetchSemanticsNodes().isEmpty()) { "debe arrancar plegada" }

        compose.onNodeWithTag("debug_header").performClick()
        listOf("Simular tanda", "Muestra A/B (imagen)", "Muestra A (texto)", "Muestra B (texto)", "Datos de prueba", "Borrar todo")
            .forEach { waitFor(it) }
        compose.onNodeWithTag("debug_header").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Simular tanda", ignoreCase = true).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun missingSamples_showADiagnosisWithTheExactContentsAndTheBuildFolder() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        waitForTag("debug_ocr_sample")
        compose.onNodeWithTag("debug_ocr_sample").performClick()
        // En los tests solo está LEEME.txt (ninguna imagen): el diálogo lo dice con el nombre exacto y la ruta de la build.
        waitForTag("samples_diag")
        waitFor("LEEME.txt", substring = true)
        waitFor("app/src/debug/assets/muestras", substring = true)
        waitFor("0 son imágenes", substring = true)
    }

    @Test
    fun debugPanelIsATwoColumnGridThatFitsWithoutScrolling() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        waitForTag("debug_wipe")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag("debug_simulate") or androidx.compose.ui.test.hasTestTag("debug_wipe") or androidx.compose.ui.test.hasTestTag("debug_to_end"))
            .fetchSemanticsNodes().forEach { check(it.boundsInRoot.bottom <= root.bottom && it.boundsInRoot.top >= 0f) { "botón de Depuración fuera de pantalla: ${it.boundsInRoot}" } }
    }
}
