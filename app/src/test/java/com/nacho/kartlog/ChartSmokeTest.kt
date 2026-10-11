package com.nacho.kartlog

import androidx.compose.ui.test.junit4.createComposeRule
import com.nacho.kartlog.ui.components.TimeLineChart
import com.nacho.kartlog.ui.theme.KartLogTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Comprueba que la gráfica (Vico) se compone y se mide sin excepciones, con y sin referencia. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ChartSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lapChart_composesWithReferenceAndHighlight() {
        val times = listOf(84_100L, 72_900, 73_200, 72_600, 74_900, 72_800)
        compose.setContent {
            KartLogTheme {
                TimeLineChart(
                    x = times.indices.map { it + 1.0 },
                    timesMs = times,
                    xLabel = { it.toInt().toString() },
                    referenceMs = 73_100,
                    referenceLabel = "Media limpia 1:13.100",
                    highlightIndex = 3,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun evolutionChart_composesWithTwoPointsAndNoReference() {
        compose.setContent {
            KartLogTheme {
                TimeLineChart(
                    x = listOf(0.0, 1.0),
                    timesMs = listOf(58_400L, 58_400L), // valores iguales: el rango no puede ser cero
                    xLabel = { "d${it.toInt()}" },
                )
            }
        }
        compose.waitForIdle()
    }
}
