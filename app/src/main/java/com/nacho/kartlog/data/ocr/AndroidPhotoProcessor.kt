package com.nacho.kartlog.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import com.nacho.kartlog.domain.importing.GrayImage
import com.nacho.kartlog.domain.importing.LoadedPhoto
import com.nacho.kartlog.domain.importing.LocalContrast
import com.nacho.kartlog.domain.importing.PhotoProcessor
import com.nacho.kartlog.domain.importing.PhotoQuality
import com.nacho.kartlog.domain.importing.PreparedPhoto
import com.nacho.kartlog.domain.importing.Quad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Prepara las fotos de las hojas con `Bitmap`: carga y normaliza (EXIF, tamaño), mide la calidad sobre una miniatura en grises y
 * endereza el recorte con una transformación de perspectiva (`Matrix.setPolyToPoly`). Todo en un hilo de E/S; los ficheros
 * viven en `cache/import/` (la foto solo se conserva si el usuario lo pide al guardar).
 */
class AndroidPhotoProcessor(private val context: Context) : PhotoProcessor {
    private val dir get() = File(context.cacheDir, "import").apply { mkdirs() }

    override suspend fun load(sourceUri: String): Result<LoadedPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(sourceUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            check(bounds.outWidth > 0 && bounds.outHeight > 0) { "no se pudo leer la imagen" }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            var bitmap = context.contentResolver.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }!!
            val orientation = context.contentResolver.openInputStream(uri)?.use {
                runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull()
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees != 0f) bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
            val file = File(dir, "foto.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            val quality = PhotoQuality.analyze(gray(bitmap, QUALITY_SIDE))
            LoadedPhoto(Uri.fromFile(file).toString(), bitmap.width, bitmap.height, quality)
        }
    }

    override suspend fun rotate90(photo: LoadedPhoto): Result<LoadedPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            val path = Uri.parse(photo.uri).path
            val source = BitmapFactory.decodeFile(path) ?: error("no se pudo leer la foto")
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)
            val file = File(dir, "foto.jpg")
            file.outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            // Se cambia el nombre de la URI para que la pantalla recargue la imagen (misma ruta, otro contenido).
            LoadedPhoto(Uri.fromFile(file).buildUpon().appendQueryParameter("r", System.nanoTime().toString()).build().toString(), rotated.width, rotated.height, photo.quality)
        }
    }

    override suspend fun prepare(photo: LoadedPhoto, quad: Quad, enhance: Boolean): Result<PreparedPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            val source = BitmapFactory.decodeFile(Uri.parse(photo.uri).path) ?: error("no se pudo leer la foto")
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(quad.outputWidth, quad.outputHeight))
            val w = (quad.outputWidth * scale).toInt().coerceAtLeast(1)
            val h = (quad.outputHeight * scale).toInt().coerceAtLeast(1)
            // Perspectiva: el cuadrilátero de la foto pasa a ser el rectángulo de salida.
            val src = floatArrayOf(quad.x[0], quad.y[0], quad.x[1], quad.y[1], quad.x[2], quad.y[2], quad.x[3], quad.y[3])
            val dst = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
            val matrix = Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
            var out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            if (enhance) out = flatten(out)
            val file = File(dir, "procesada.jpg")
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            PreparedPhoto(Uri.fromFile(file).toString(), out.width, out.height)
        }
    }

    private fun gray(bitmap: Bitmap, maxSide: Int): GrayImage {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val px = IntArray(small.width * small.height)
        small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
        return GrayImage(small.width, small.height, IntArray(px.size) { i ->
            val c = px[i]
            ((0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF))).toInt()
        })
    }

    private fun flatten(bitmap: Bitmap): Bitmap {
        val g = gray(bitmap, Int.MAX_VALUE)
        val flat = LocalContrast.flatten(g, window = (maxOf(bitmap.width, bitmap.height) / 40).coerceIn(15, 61) or 1)
        val out = IntArray(flat.pixels.size) { i -> val v = flat.pixels[i]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(out, flat.width, flat.height, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val MAX_SIDE = 2400
        const val QUALITY_SIDE = 480
    }
}
