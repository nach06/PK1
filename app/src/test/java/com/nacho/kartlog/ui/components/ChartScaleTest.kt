package com.nacho.kartlog.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartScaleTest {
    // 10 vueltas: salida de boxes de 1:26.000 y el resto entre 1:12.450 y 1:14.900.
    private val laps = listOf(86_000L, 72_900, 73_200, 72_600, 74_900, 72_800, 73_050, 72_450, 72_980, 72_858)

    @Test
    fun lapScale_usesCleanLapsPlusMinus300ms_andClipsTheOutLap() {
        val scale = computeChartScale(laps, clipOutliers = true)
        assertEquals(72_450.0 - 300, scale.minY, 0.0) // mejor − 0,3 s
        assertEquals(74_900.0 + 300, scale.maxY, 0.0) // mayor limpia + 0,3 s
        assertEquals(setOf(0), scale.clippedIndexes) // la salida de boxes (>107 %)
        assertEquals(75_200L, scale.plottedMs[0]) // recortada en el borde superior
        assertEquals(laps.drop(1), scale.plottedMs.drop(1)) // el resto, intacto
    }

    @Test
    fun lapScale_withoutOutliers_clipsNothing() {
        val scale = computeChartScale(listOf(50_000, 51_000, 52_000), clipOutliers = true)
        assertTrue(scale.clippedIndexes.isEmpty())
        assertEquals(49_700.0, scale.minY, 0.0)
        assertEquals(52_300.0, scale.maxY, 0.0)
    }

    @Test
    fun lapScale_includesTheReferenceLine() {
        // Si la media (referencia) cayera fuera, el rango la incluye.
        val scale = computeChartScale(listOf(50_000, 51_000), referenceMs = 53_000, clipOutliers = true)
        assertEquals(53_300.0, scale.maxY, 0.0)
    }

    @Test
    fun singleLap_hasAUsableRange() {
        val scale = computeChartScale(listOf(48_532), clipOutliers = true)
        assertEquals(600.0, scale.maxY - scale.minY, 0.0)
    }

    @Test
    fun evolutionScale_neverClips_andAddsPadding() {
        val scale = computeChartScale(listOf(58_400, 57_900, 60_000))
        assertTrue(scale.clippedIndexes.isEmpty())
        assertTrue(scale.minY < 57_900 && scale.maxY > 60_000)
    }
}
