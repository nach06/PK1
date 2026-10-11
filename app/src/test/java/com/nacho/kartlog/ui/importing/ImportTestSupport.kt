package com.nacho.kartlog.ui.importing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.nacho.kartlog.domain.importing.ImageTextRecognizer
import com.nacho.kartlog.domain.importing.ImportPhotoStore
import com.nacho.kartlog.domain.importing.LoadedPhoto
import com.nacho.kartlog.domain.importing.OcrBox
import com.nacho.kartlog.domain.importing.OcrLayout
import com.nacho.kartlog.domain.importing.OcrScan
import com.nacho.kartlog.domain.importing.PhotoProblem
import com.nacho.kartlog.domain.importing.PhotoProcessor
import com.nacho.kartlog.domain.importing.PhotoQualityReport
import com.nacho.kartlog.domain.importing.PreparedPhoto
import com.nacho.kartlog.domain.importing.Quad
import java.io.File

/** Escribe un PNG liso en la caché y devuelve su URI (las pruebas no tienen cámara). */
internal fun testImage(context: Context, name: String = "hoja.png", width: Int = 1000, height: Int = 800): String {
    val file = File(context.cacheDir, name)
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(240, 240, 240)) }
        .compress(Bitmap.CompressFormat.PNG, 100, file.outputStream())
    return android.net.Uri.fromFile(file).toString()
}

/**
 * Cajas de OCR a partir de un texto con celdas separadas por tabulador (una caja por pieza), colocadas en rejilla. Sirve para
 * simular una lectura limpia de ML Kit en los tests; [noise] añade ruido realista (desplazamientos verticales pequeños).
 */
internal fun boxesOf(text: String, conf: (String) -> Float = { 1f }, jitter: Float = 0f): List<OcrBox> {
    val boxes = mutableListOf<OcrBox>()
    text.lines().forEachIndexed { row, line ->
        val pieces = if (line.contains('\t')) line.split('\t') else line.trim().split(Regex("\\s{2,}"))
        pieces.forEachIndexed { col, piece ->
            if (piece.isBlank()) return@forEachIndexed
            val dy = if (jitter != 0f) ((row * 7 + col * 3) % 5 - 2) * jitter else 0f
            val x = 30f + col * 190f
            val y = 40f + row * 44f + dy
            boxes += OcrBox(piece.trim(), x, y, x + 150f, y + 24f, 0f, conf(piece.trim()))
        }
    }
    return boxes
}

internal fun scanOf(boxes: List<OcrBox>, width: Int = 1000, height: Int = 800) = OcrScan(boxes, OcrLayout.reconstructGrid(boxes), width, height)

internal class FakeRecognizer(private val producer: (String) -> OcrScan) : ImageTextRecognizer {
    val requested = mutableListOf<String>()
    var fail = false
    override suspend fun scan(imageUri: String): Result<OcrScan> {
        requested += imageUri
        if (fail) return Result.failure(IllegalStateException("fallo"))
        return Result.success(producer(imageUri))
    }
}

internal class FakePhotoProcessor(
    private val problems: Set<PhotoProblem> = emptySet(),
    private val width: Int = 1000,
    private val height: Int = 800,
) : PhotoProcessor {
    val prepared = mutableListOf<Triple<Quad, Boolean, String>>()
    var rotations = 0
    var failLoad = false

    private fun quality() = PhotoQualityReport(sharpness = if (PhotoProblem.BLURRY in problems) 0.1 else 3.0, meanBrightness = 150.0, glareFraction = 0.0, problems = problems)

    override suspend fun load(sourceUri: String): Result<LoadedPhoto> =
        if (failLoad) Result.failure(IllegalStateException("no se puede abrir")) else Result.success(LoadedPhoto(sourceUri, width, height, quality()))

    override suspend fun rotate90(photo: LoadedPhoto): Result<LoadedPhoto> {
        rotations++
        return Result.success(LoadedPhoto(photo.uri, photo.height, photo.width, photo.quality))
    }

    override suspend fun prepare(photo: LoadedPhoto, quad: Quad, enhance: Boolean): Result<PreparedPhoto> {
        prepared += Triple(quad, enhance, photo.uri)
        return Result.success(PreparedPhoto(photo.uri, quad.outputWidth, quad.outputHeight))
    }
}

internal class FakePhotoStore : ImportPhotoStore {
    val saved = mutableMapOf<Long, String>()
    override suspend fun save(sessionId: Long, imageUri: String): Boolean { saved[sessionId] = imageUri; return true }
    override fun pathOf(sessionId: Long): String? = saved[sessionId]
    override suspend fun delete(sessionId: Long) { saved.remove(sessionId) }
    override suspend fun deleteAll() { saved.clear() }
}
