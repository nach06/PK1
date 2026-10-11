package com.nacho.kartlog.ui.util

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sprint 6, E2: la tarjeta de resumen para compartir. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SessionShareImageTest {
    private val data = ShareCardData(
        circuitName = "Karting Zuera", dateText = "11 oct 2026 · 10:30", subtitle = "Libre · Kart 7",
        bestMs = 57_650, cleanAverageMs = 58_320, lapCount = 12,
        trace = (0..200).map { i -> (150f * kotlin.math.cos(i * 0.0314f)) to (60f * kotlin.math.sin(i * 0.0314f)) },
    )

    private fun isGreen(c: Int) = Color.green(c) > 180 && Color.red(c) < 80 && Color.blue(c) in 80..160

    private fun greenPixels(b: android.graphics.Bitmap, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        var n = 0
        for (x in x0 until x1 step 2) for (y in y0 until y1 step 2) if (isGreen(b.getPixel(x, y))) n++
        return n
    }

    @Test
    fun theCardHasTheExpectedSize_theBestLapInGreen_andTheTraceInsideItsBox() {
        val bitmap = SessionShareImage.render(data)
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)
        assertTrue("el mejor tiempo (zona 380–540) va en verde", greenPixels(bitmap, 40, 380, 1040, 540) > 200)
        assertTrue("el trazado (caja inferior) va en verde", greenPixels(bitmap, 60, 790, 1020, 1200) > 200)
    }

    @Test
    fun withoutATraceTheBoxStaysEmpty() {
        val bitmap = SessionShareImage.render(data.copy(trace = null))
        assertEquals(0, greenPixels(bitmap, 60, 790, 1020, 1200))
        assertTrue(greenPixels(bitmap, 40, 380, 1040, 540) > 200)
    }

    @Test
    fun aVeryLongCircuitNameIsTruncatedInsteadOfOverflowing() {
        val bitmap = SessionShareImage.render(data.copy(circuitName = "Circuito Internacional de Karting " + "muy largo ".repeat(20)))
        // Nada del nombre se sale por el borde derecho (columna final sin texto blanco en la franja del título).
        var whiteAtEdge = 0
        for (y in 80 until 150) for (x in 1060 until 1080) if (Color.red(bitmap.getPixel(x, y)) > 200) whiteAtEdge++
        assertEquals(0, whiteAtEdge)
    }
}
