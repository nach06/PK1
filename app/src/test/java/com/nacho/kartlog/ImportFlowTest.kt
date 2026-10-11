package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.domain.model.LapSource
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

/**
 * Flujo de importación por el `NavHost` real: texto compartido → elegir «yo» → vista previa → guardar →
 * Detalle con vueltas importadas y la tarjeta «Clasificación de la sesión».
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h900dp-xxhdpi")
class ImportFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        compose.activityRule.scenario.close()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun waitFor(text: String, substring: Boolean = false) = try {
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText(text, substring = substring, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (e: Throwable) {
        println("TREE@$text: " + compose.onRoot().printToString(60))
        throw e
    }

    @Test
    fun importFromSharedTextEndsInDetailWithClassification() {
        val container = AppContainer(context)
        val text = javaClass.getResource("/muestras/muestra_a_correo_lineas.txt")!!.readText()
        runBlocking { container.circuitRepository.add("GP3", 900) }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        waitFor("Tandas")

        // Contenido recibido con «Compartir»: abre Importar tiempos y lo analiza.
        container.importInbox.put(SharedContent.Text(text))
        waitFor("¿Cuál eres tú?")

        // Sin nombre guardado no hay selección automática: se elige la columna propia.
        compose.onNodeWithText("Nacho · Kart 28").performScrollTo().performClick()
        waitFor("Vista previa de tus vueltas")
        compose.onNodeWithText("Otros pilotos", substring = true, ignoreCase = true).performScrollTo().assertExistsCompat()

        compose.onNodeWithText("Guardar importación", ignoreCase = true).performSemanticsAction(SemanticsActions.OnClick)

        // Detalle de la tanda nueva: vueltas importadas (icono) y clasificación plegable con mi fila.
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("lap_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lap_list").performScrollToNode(androidx.compose.ui.test.hasTestTag("classification"))
        waitFor("Clasificación de la sesión", substring = true)
        compose.waitUntil(8_000) { compose.onAllNodesWithContentDescription("Importada").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("classification").performClick()
        waitFor("Tú", substring = true)
        waitFor("Deyvis", substring = true) // el líder también está en la clasificación

        val sessions = runBlocking { container.sessionRepository.observeSummaries().first() }
        check(sessions.size == 1) { "debe haberse creado una tanda" }
        val laps = runBlocking { container.lapRepository.observeBySession(sessions.single().session.id).first() }
        check(laps.isNotEmpty() && laps.all { it.source == LapSource.IMPORTED }) { "las vueltas propias son IMPORTED" }
        val others = runBlocking { container.participantRepository.observeBySession(sessions.single().session.id).first() }
        check(others.size == 14 && others.count { it.isMe } == 1)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() = assertExists()
}
