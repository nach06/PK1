package com.nacho.kartlog.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.ocr.AndroidPhotoProcessor
import com.nacho.kartlog.domain.importing.Quad
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Sprint 6, D2: cargar, girar y enderezar una foto con `Bitmap` (modo gráfico nativo de Robolectric: dibuja de verdad). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AndroidPhotoProcessorTest {
    private lateinit var context: Context
    private lateinit var processor: AndroidPhotoProcessor

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.cacheDir, "import").deleteRecursively()
        processor = AndroidPhotoProcessor(context)
    }

    /** Foto de 800×600: fondo gris y una «hoja» roja en el rectángulo (200,150)-(600,450). */
    private fun photo(): String {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(90, 90, 90))
        for (x in 200 until 600) for (y in 150 until 450) bitmap.setPixel(x, y, Color.rgb(220, 30, 30))
        val file = File(context.cacheDir, "origen.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return android.net.Uri.fromFile(file).toString()
    }

    @Test
    fun loadNormalisesThePhoto_andMeasuresItsQuality() = runBlocking {
        val loaded = processor.load(photo()).getOrThrow()
        assertEquals(800, loaded.width)
        assertEquals(600, loaded.height)
        assertTrue(File(android.net.Uri.parse(loaded.uri).path!!).exists())
        assertTrue(loaded.quality.meanBrightness > 0)
    }

    @Test
    fun loadFailsCleanlyOnAnUnreadableFile() = runBlocking {
        assertTrue(processor.load("file:///no/existe.jpg").isFailure)
    }

    @Test
    fun rotate90SwapsTheSides() = runBlocking {
        val loaded = processor.load(photo()).getOrThrow()
        val rotated = processor.rotate90(loaded).getOrThrow()
        assertEquals(600, rotated.width)
        assertEquals(800, rotated.height)
        val decoded = BitmapFactory.decodeFile(android.net.Uri.parse(rotated.uri).path)
        assertEquals(600, decoded.width)
        assertEquals(800, decoded.height)
    }

    @Test
    fun prepareStraightensTheQuadIntoARectangleOfItsSize() = runBlocking {
        val loaded = processor.load(photo()).getOrThrow()
        val quad = Quad(floatArrayOf(200f, 600f, 600f, 200f), floatArrayOf(150f, 150f, 450f, 450f))
        val prepared = processor.prepare(loaded, quad, enhance = false).getOrThrow()
        assertEquals(400, prepared.width)
        assertEquals(300, prepared.height)
        val out = BitmapFactory.decodeFile(android.net.Uri.parse(prepared.uri).path)
        assertEquals(400, out.width)
        // Dentro del recorte solo hay «hoja» (roja), nada del fondo gris.
        val c = out.getPixel(200, 150)
        assertTrue("rojo dominante: ${Color.red(c)},${Color.green(c)}", Color.red(c) > 150 && Color.green(c) < 90)
        val corner = out.getPixel(5, 5)
        assertTrue(Color.red(corner) > 150 && Color.green(corner) < 90)
    }

    @Test
    fun prepareWithEnhanceStillProducesTheSameSize_inGreys() = runBlocking {
        val loaded = processor.load(photo()).getOrThrow()
        val prepared = processor.prepare(loaded, Quad.full(800, 600), enhance = true).getOrThrow()
        assertEquals(800, prepared.width)
        val out = BitmapFactory.decodeFile(android.net.Uri.parse(prepared.uri).path)
        val c = out.getPixel(400, 300)
        assertTrue("gris (r≈g≈b): ${Color.red(c)},${Color.green(c)},${Color.blue(c)}", Math.abs(Color.red(c) - Color.green(c)) < 12)
    }
}
