package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.util.SessionsCsvExporter
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.util.ShareCardData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sprint 8, C5: los datos de rivales no salen en ningún export público y «Borrar datos de rivales» vacía la pantalla Rivales. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h1400dp-xxhdpi")
class RivalPrivacyTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private val rivalNames = listOf("Carlos", "Ruiz", "Lucía", "Pérez", "Marc", "Soler")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theCsvExportAndTheShareCardNeverCarryRivalData() {
        val container = AppContainer(context)
        val csv = runBlocking {
            container.debugTools!!.seed()
            val rivals = container.participantRepository.observeAllWithLaps().first()
            assertTrue("el seed debe tener rivales para que el test valga", rivals.any { it.participant.name == "Carlos Ruiz" })
            SessionsCsvExporter(container.sessionRepository, container.lapRepository).export()
        }
        assertNotNull(csv)
        rivalNames.forEach { assertFalse("el CSV contiene «$it»", csv!!.contains(it, ignoreCase = true)) }
        // La tarjeta de resumen compartible solo admite estos campos: añadir uno (p. ej. rivales) obliga a revisar esta prueba.
        assertEquals(
            setOf("circuitName", "dateText", "subtitle", "bestMs", "cleanAverageMs", "lapCount", "trace"),
            ShareCardData::class.java.declaredFields.filter { !it.isSynthetic && it.name != "\$stable" }.map { it.name }.toSet(),
        )
    }

    @Test
    fun clearingRivalDataEmptiesTheRivalsScreen_andForgetsTheMergedAliases() {
        val container = AppContainer(context)
        runBlocking { container.debugTools!!.seed() }
        container.userSettings.setDriverAliases(mapOf("cruiz" to "carlosruiz"))
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Tandas").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Ajustes").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("BORRAR DATOS DE RIVALES").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("BORRAR DATOS DE RIVALES").performScrollTo().performClick()
        compose.onNodeWithText("BORRAR RIVALES").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("Datos de rivales borrados").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(container.userSettings.driverAliases.value.isEmpty())
        assertTrue(runBlocking { container.participantRepository.observeAllWithLaps().first() }.isEmpty())

        compose.onNodeWithText("Análisis").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("open_rivals").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("open_rivals").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("rivals_empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("rivals_empty").assertIsDisplayed()
        // Mis tandas y mis vueltas siguen ahí.
        assertTrue(runBlocking { container.lapRepository.observeAll().first() }.isNotEmpty())
    }
}
