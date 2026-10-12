package com.nacho.kartlog.debug

import android.content.Context
import android.net.Uri
import com.nacho.kartlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** Lo mínimo que se necesita de los assets: listar una carpeta y abrir un fichero (falsificable en tests). */
interface AssetSource {
    fun list(path: String): Array<String>?
    fun open(path: String): InputStream
}

class ContextAssetSource(private val context: Context) : AssetSource {
    override fun list(path: String): Array<String>? = runCatching { context.assets.list(path) }.getOrNull()
    override fun open(path: String): InputStream = context.assets.open(path)
}

/**
 * Imágenes de muestra del responsable (`app/src/debug/assets/muestras/`, también en subcarpetas), para probar el OCR sin cámara.
 * Se admite **cualquier** extensión de imagen (png, jpg, jpeg, webp, bmp, gif, heic; sin distinguir mayúsculas) y dobles extensiones
 * como `muestra_a.png.png`. Solo existen en builds debug.
 */
class SampleImages(private val assets: AssetSource, private val cacheDir: File, private val projectDir: String = BuildConfig.PROJECT_DIR) {
    constructor(context: Context) : this(ContextAssetSource(context), context.cacheDir)

    /** Todo lo que hay bajo `muestras/` (ficheros y subcarpetas), con rutas relativas, para el diagnóstico. */
    fun allEntries(): List<String> = walk(FOLDER, "")

    /** Rutas relativas (`muestra_a.png`, `sub/muestra_b.jpeg`) de las imágenes, ordenadas. */
    fun names(): List<String> = allEntries().filter(::isSampleImage).sorted()

    /** Qué hay (o no) en la carpeta de muestras: nombres exactos, nº de ficheros y de qué proyecto sale la build. */
    fun diagnosis(): SampleDiagnosis {
        val all = allEntries()
        return SampleDiagnosis(
            entries = all,
            imageCount = all.count(::isSampleImage),
            expectedDir = "$projectDir/app/src/debug/assets/muestras",
            projectDir = projectDir,
        )
    }

    private fun walk(path: String, prefix: String): List<String> {
        val children = assets.list(path) ?: return emptyList()
        return children.flatMap { child ->
            val full = "$path/$child"
            val sub = assets.list(full)
            if (sub != null && sub.isNotEmpty()) walk(full, "$prefix$child/") else listOf("$prefix$child")
        }
    }

    /** Contenido de un fichero de texto de los assets (las muestras de texto de los tests). */
    suspend fun readText(assetName: String): String? = withContext(Dispatchers.IO) {
        runCatching { assets.open(assetName).bufferedReader().use { it.readText() } }.getOrNull()
    }

    suspend fun prepare(name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(cacheDir, "photos").apply { mkdirs() }.resolve(name.substringAfterLast('/'))
            assets.open("$FOLDER/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    companion object {
        private const val FOLDER = "muestras"
        private val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "heic", "heif")

        /** `true` si el NOMBRE termina en una extensión de imagen (también `muestra.png.png`); el resto (p. ej. `LEEME.txt`) se ignora. */
        fun isSampleImage(name: String): Boolean = name.substringAfterLast('/').substringAfterLast('.', "").lowercase() in EXTENSIONS
    }
}
