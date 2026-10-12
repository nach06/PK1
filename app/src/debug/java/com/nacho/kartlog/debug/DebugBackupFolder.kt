package com.nacho.kartlog.debug

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/**
 * Carpeta de copias de depuración (solo builds debug): el emulador con permisos limitados no puede usar el selector de ficheros.
 * Se escribe siempre en el almacenamiento propio de la app (sin permisos) y, en Android 10+, también en `Descargas/KartLog`.
 */
class DebugBackupFolder(private val context: Context) {
    private fun dir(): File? =
        (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "debug-backups")).also { it.mkdirs() }

    /** Copia [source] y devuelve la ruta del fichero guardado (o `null` si no se pudo). */
    fun save(source: File): String? {
        val target = File(dir() ?: return null, "$PREFIX${System.currentTimeMillis()}.zip")
        val ok = runCatching { source.copyTo(target, overwrite = true) }.isSuccess
        if (!ok) return null
        publishToDownloads(target)
        return target.absolutePath
    }

    private fun publishToDownloads(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/KartLog")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        }
    }

    /** La copia más reciente guardada con [save], o `null`. */
    fun latest(): (() -> InputStream?)? {
        val newest = dir()?.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".zip") }?.maxByOrNull { it.name } ?: return null
        return { newest.inputStream() }
    }

    private companion object {
        const val PREFIX = "kartlog-debug-backup-"
    }
}
