package com.nacho.kartlog.debug

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Imágenes de muestra del responsable (`app/src/debug/assets/muestras/`: p. ej. `muestra_a.png`, `muestra_b.jpeg`; se aceptan png, jpg, jpeg y webp),
 * para probar el OCR sin cámara. Solo existen en builds debug.
 */
class SampleImages(private val context: Context) {
    fun names(): List<String> =
        (runCatching { context.assets.list(FOLDER) }.getOrNull() ?: emptyArray())
            .filter(::isSampleImage)
            .sorted()

    suspend fun prepare(name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(context.cacheDir, "photos").apply { mkdirs() }.resolve(name)
            context.assets.open("$FOLDER/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    companion object {
        private const val FOLDER = "muestras"
        private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

        /** `true` para `.png`, `.jpg`, `.jpeg` y `.webp` (sin distinguir mayúsculas); el resto (p. ej. `LEEME.txt`) se ignora. */
        fun isSampleImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS
    }
}
