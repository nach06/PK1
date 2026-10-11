package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Ajustes → Depuración → «Importar muestra de texto A/B» abre Importar tiempos con el texto ya analizado (A4). */
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
        Thread.sleep(300)
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
        waitFor("Importar muestra de texto A")
        compose.onNodeWithText("Importar muestra de texto A", ignoreCase = true).performScrollTo().performClick()
        waitForTag("who_banner")
        waitFor("Nacho · Kart 28", substring = true)
    }

    @Test
    fun sampleB_opensTheImporterWithTheKartSheetAnalysed() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        compose.onNodeWithTag("debug_header").performClick()
        waitFor("Importar muestra de texto B")
        compose.onNodeWithText("Importar muestra de texto B", ignoreCase = true).performScrollTo().performClick()
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
        check(compose.onAllNodesWithText("Simular tanda en pista", ignoreCase = true).fetchSemanticsNodes().isEmpty()) { "debe arrancar plegada" }

        compose.onNodeWithTag("debug_header").performClick()
        listOf("Simular tanda en pista", "Probar importación con muestra A/B (imagen)", "Importar muestra de texto A", "Importar muestra de texto B", "Cargar datos de prueba", "Borrar todos los datos")
            .forEach { waitFor(it) }
        compose.onNodeWithTag("debug_header").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Simular tanda en pista", ignoreCase = true).fetchSemanticsNodes().isEmpty() }
    }
}
