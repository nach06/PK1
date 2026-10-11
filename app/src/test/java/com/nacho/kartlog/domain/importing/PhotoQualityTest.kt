package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PhotoQualityTest {
    private val size = 96

    /** Hoja con «texto» (barras oscuras) sobre fondo claro: nítida. */
    private fun sheet(background: Int = 220, ink: Int = 30): GrayImage {
        val px = IntArray(size * size) { i ->
            val x = i % size
            val y = i / size
            if ((x / 3 + y / 6) % 2 == 0 && y % 12 < 7) ink else background
        }
        return GrayImage(size, size, px)
    }

    private fun boxBlur(image: GrayImage, radius: Int): GrayImage {
        val out = IntArray(image.pixels.size)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            var sum = 0
            var n = 0
            for (dy in -radius..radius) for (dx in -radius..radius) {
                val xx = x + dx
                val yy = y + dy
                if (xx in 0 until image.width && yy in 0 until image.height) { sum += image[xx, yy]; n++ }
            }
            out[y * image.width + x] = sum / n
        }
        return GrayImage(image.width, image.height, out)
    }

    @Test
    fun aSharpWellLitPhotoHasNoProblems() {
        val report = PhotoQuality.analyze(sheet())
        assertTrue("nitidez ${report.sharpness}", report.sharpness > PhotoQuality.MIN_SHARPNESS)
        assertTrue(report.ok)
    }

    @Test
    fun aBlurredPhotoIsFlagged_andIsLessSharpThanTheOriginal() {
        val sharp = PhotoQuality.analyze(sheet())
        val blurred = PhotoQuality.analyze(boxBlur(sheet(), 3))
        assertTrue("nitidez ${blurred.sharpness} vs ${sharp.sharpness}", blurred.sharpness < sharp.sharpness / 10)
        assertTrue("nitidez movida ${blurred.sharpness}", PhotoProblem.BLURRY in blurred.problems)
        assertFalse(PhotoProblem.BLURRY in sharp.problems)
    }

    @Test
    fun aDarkPhotoIsFlagged() {
        val dark = PhotoQuality.analyze(sheet(background = 45, ink = 5))
        assertTrue(PhotoProblem.TOO_DARK in dark.problems)
        assertTrue(dark.meanBrightness < PhotoQuality.MIN_BRIGHTNESS)
    }

    @Test
    fun glareIsFlaggedOnlyAboveTheLimit() {
        val base = sheet()
        val burnedQuarter = GrayImage(size, size, IntArray(base.pixels.size) { i -> if (i / size < size / 4) 255 else base.pixels[i] })
        assertTrue(PhotoProblem.GLARE in PhotoQuality.analyze(burnedQuarter).problems)
        val burnedFew = GrayImage(size, size, IntArray(base.pixels.size) { i -> if (i < 100) 255 else base.pixels[i] })
        assertFalse(PhotoProblem.GLARE in PhotoQuality.analyze(burnedFew).problems)
    }

    @Test
    fun localContrastEvensOutUnevenLighting_keepingTheInkDarker() {
        // Fondo con degradado fuerte (60→220) y rayas de tinta un 60 % más oscuras que su entorno.
        val w = 120
        val h = 40
        val px = IntArray(w * h) { i ->
            val x = i % w
            val bg = 60 + (160 * x / (w - 1))
            if (i / w in 18..21 && x % 8 < 4) (bg * 0.4).toInt() else bg
        }
        val flat = LocalContrast.flatten(GrayImage(w, h, px), window = 25)
        // Fondo: ya no depende de la posición (antes variaba 100+ niveles).
        val bgLeft = flat[6, 5]
        val bgRight = flat[w - 7, 5]
        assertTrue("fondo desigual tras igualar: $bgLeft vs $bgRight", abs(bgLeft - bgRight) < 25)
        assertTrue("fondo claro", bgLeft > 190 && bgRight > 190)
        // La tinta sigue siendo claramente más oscura que el fondo, a izquierda y a derecha.
        assertTrue(flat[8, 19] < bgLeft - 60)
        assertTrue(flat[w - 8, 19] < bgRight - 60)
    }

    @Test
    fun reliability_requiresBoxesParseableCellsAndAGoodRatio() {
        val parser = LapTextParser()
        val text = "Nacho\tDeyvis\n1\t1:17.263\t1:15.100\n2\t1:16.905\t1:14.800\n3\t1:18.020\t1:15.900"
        val doc = parser.parse(text)
        fun boxes(n: Int, text: String = "x") = List(n) { OcrBox(text, 0f, it * 30f, 50f, it * 30f + 20f) }
        // Pocas cajas.
        assertEquals(OcrReliability.Reason.TOO_FEW_BOXES, OcrReliability.assess(boxes(5), doc).reason)
        // Suficientes cajas y lectura buena.
        val good = OcrReliability.assess(boxes(20), doc)
        assertTrue(good.reliable)
        // Sin ninguna vuelta.
        assertEquals(OcrReliability.Reason.NO_LAPS, OcrReliability.assess(boxes(20), parser.parse("nada")).reason)
        // Muchas cajas con aspecto de tiempo que no se pudieron leer: mala lectura.
        val unreadable = boxes(40, "1:12.4") // con aspecto de tiempo pero sin las 3 cifras de milésimas
        val bad = OcrReliability.assess(unreadable, doc)
        assertEquals(OcrReliability.Reason.MOSTLY_UNREADABLE, bad.reason)
        assertFalse(bad.reliable)
    }

    @Test
    fun quad_sizeOrderingAndRotation() {
        val quad = Quad(floatArrayOf(10f, 210f, 200f, 20f), floatArrayOf(5f, 10f, 305f, 300f))
        assertEquals(190, quad.outputWidth) // media de los bordes superior (200,06) e inferior (180,07)
        assertEquals(295, quad.outputHeight)
        val ordered = Quad.ordered(floatArrayOf(200f, 20f, 210f, 10f), floatArrayOf(305f, 300f, 10f, 5f))
        assertEquals(quad, ordered)
        // Cuatro giros de 90° devuelven el recorte original; uno intercambia ancho y alto.
        val r1 = quad.rotated90()
        assertTrue(abs(r1.outputWidth - quad.outputHeight) <= 2)
        assertEquals(quad, r1.rotated90().rotated90().rotated90())
        val full = Quad.full(400, 300)
        assertEquals(400, full.outputWidth)
        assertEquals(300, full.outputHeight)
    }
}
