package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    private fun waitFor(text: String, ignoreCase: Boolean = true) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, ignoreCase = ignoreCase).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun sampleA_opensTheImporterWithTheEmailAnalysed() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        waitFor("Importar muestra de texto A")
        compose.onNodeWithText("Importar muestra de texto A", ignoreCase = true).performScrollTo().performClick()
        waitFor("¿Cuál eres tú?")
        waitFor("Nacho · Kart 28")
    }

    @Test
    fun sampleB_opensTheImporterWithTheKartSheetAnalysed() {
        val container = AppContainer(context)
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")
        compose.onNodeWithText("Ajustes").performClick()
        waitFor("Importar muestra de texto B")
        compose.onNodeWithText("Importar muestra de texto B", ignoreCase = true).performScrollTo().performClick()
        waitFor("¿Cuál eres tú?")
        waitFor("Kart 28")
    }
}
