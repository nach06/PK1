package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A9: sin nombre guardado, la columna elegida con «Soy yo» se recuerda y la próxima vez se sugiere (☆), sin darla por tuya. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class ImportSuggestionFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        context.getSharedPreferences("kartlog_settings", Context.MODE_PRIVATE).edit().clear().commit()
        File(context.filesDir, "imports").deleteRecursively()
    }

    @After
    fun tearDown() {
        runCatching { compose.waitForIdle() }
        compose.activityRule.scenario.close()
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "imports").deleteRecursively()
    }

    private fun waitForTag(tag: String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun rememberedColumnIsSuggestedNextTime_butNotMarkedAsMine() {
        val container = AppContainer(context)
        val text = javaClass.getResource("/muestras/muestra_a_correo_lineas.txt")!!.readText()
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }

        // Primera vez: nada sugerido; se toca la columna y «Soy yo».
        container.importInbox.put(SharedContent.Text(text))
        waitForTag("who_banner")
        assertTrue("sin recuerdo no hay sugerencia", compose.onAllNodesWithText("☆", substring = true).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("driver_strip").performScrollToNodeCompat("driver_chip_Nacho")
        compose.onNodeWithTag("driver_chip_Nacho").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("action_me").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("who_banner").fetchSemanticsNodes().isEmpty() }
        assertTrue("recuerdo=[" + container.userSettings.lastImportColumn.value + "]", container.userSettings.lastImportColumn.value.startsWith("Nacho"))

        // Segunda vez (otra hoja, mismo nombre en la lista): ya llega sugerida con ☆ y el aviso lo explica, pero sin dar «Soy yo».
        container.importInbox.put(SharedContent.Text(text + "\n"))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("who_banner").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("driver_strip").performScrollToNodeCompat("driver_chip_Nacho")
        assertTrue("la ficha de Nacho lleva ☆", compose.onAllNodesWithText("☆ Nacho", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("false", compose.onNodeWithTag("action_me").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)?.toString() ?: "false")
        assertTrue(compose.onAllNodesWithText("Sugerida", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.performScrollToNodeCompat(tag: String) {
        performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
    }
}
