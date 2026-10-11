package com.nacho.kartlog.debug

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Imágenes de muestra del responsable (`app/src/debug/assets/muestras/`: p. ej. `muestra_a.png`, `muestra_b.jpg`),
 * para probar el OCR sin cámara. Solo existen en builds debug.
 */
class SampleImages(private val context: Context) {
    private val folder = "muestras"
    private val extensions = setOf("png", "jpg", "jpeg", "webp")

    fun names(): List<String> =
        (runCatching { context.assets.list(folder) }.getOrNull() ?: emptyArray())
            .filter { it.substringAfterLast('.', "").lowercase() in extensions }
            .sorted()

    suspend fun prepare(name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(context.cacheDir, "photos").apply { mkdirs() }.resolve(name)
            context.assets.open("$folder/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }
}
