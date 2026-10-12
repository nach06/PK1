package com.nacho.kartlog

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.ui.navigation.KartNavHost
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** A8: en la revisión se ven ≥ 7 filas de tiempos enteras a 393×808 dp y la razón de «No se puede guardar» sigue a la vista. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h808dp-xxhdpi")
class ReviewDensityTest {
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

    @Test
    fun sevenTimeRowsAreFullyVisible_andTheBlockedReasonStaysOnScreen() {
        val container = AppContainer(context)
        val text = (1..12).joinToString("\n") { "$it\t1:${10 + it}.${100 + it}" }
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        container.importInbox.put(SharedContent.Text(text))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("review_row_1").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        // Sin «Soy yo» ni circuito guardado: GUARDAR bloqueado y su razón visible.
        compose.onNodeWithTag("save_blocked").assertIsDisplayed()

        val full = (1..20).count { n ->
            val nodes = compose.onAllNodesWithTag("review_row_$n").fetchSemanticsNodes()
            if (nodes.isEmpty()) false else {
                val node = compose.onAllNodesWithTag("review_row_$n")[0]
                val clipped = node.getBoundsInRoot()
                val unclipped = node.getUnclippedBoundsInRoot()
                (clipped.bottom - clipped.top) >= (unclipped.bottom - unclipped.top) - 1.dp
            }
        }
        assertTrue("filas de tiempos enteras visibles: $full", full >= 7)
    }

    /** A2 (Sprint 8): los controles cruzados se leen: todo su texto ≥ 12 sp y las tres fichas están dentro de la ventana. */
    @Test
    fun crossCheckChipsUseAtLeast12sp_andAreFullyOnScreen() {
        val container = AppContainer(context)
        val text = javaClass.getResource("/muestras/muestra_a_correo_lineas.txt")!!.readText()
        compose.setContent { KartLogTheme { KartNavHost(container) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Tandas", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        container.importInbox.put(SharedContent.Text(text, meName = "Nacho"))
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("check_row").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val sizes = ArrayList<Float>()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            val results = ArrayList<androidx.compose.ui.text.TextLayoutResult>()
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.firstOrNull()?.let { sizes += it.layoutInput.style.fontSize.value }
            n.children.forEach(::walk)
        }
        walk(compose.onNodeWithTag("check_row", useUnmergedTree = true).fetchSemanticsNode())
        assertTrue("textos en los controles: ${sizes.size}", sizes.size >= 9)
        assertTrue("tamaños de letra: $sizes", sizes.all { it >= 12f })
        val rootW = compose.onRoot().getUnclippedBoundsInRoot().right
        listOf("check_best", "check_laps", "check_avg").forEach {
            val b = compose.onNodeWithTag(it).getUnclippedBoundsInRoot()
            assertTrue("$it dentro: $b", b.left >= 0.dp && b.right <= rootW)
        }
    }
}
