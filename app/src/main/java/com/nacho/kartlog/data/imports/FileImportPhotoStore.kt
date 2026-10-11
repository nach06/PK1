package com.nacho.kartlog.data.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.nacho.kartlog.domain.importing.ImportPhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fotos en `{dir}/{sessionId}.jpg`, reducidas a [MAX_SIDE] px por el lado largo (JPEG calidad 85). */
class FileImportPhotoStore(private val context: Context, private val dir: File) : ImportPhotoStore {
    private fun file(sessionId: Long) = File(dir, "$sessionId.jpg")

    override suspend fun save(sessionId: Long, imageUri: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(imageUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            dir.mkdirs()
            val tmp = File(dir, "$sessionId.jpg.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            check(tmp.renameTo(file(sessionId)) || (file(sessionId).delete() && tmp.renameTo(file(sessionId))))
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    override fun pathOf(sessionId: Long): String? = file(sessionId).takeIf { it.exists() }?.absolutePath

    override suspend fun delete(sessionId: Long) {
        withContext(Dispatchers.IO) { file(sessionId).delete() }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { dir.listFiles()?.forEach { it.delete() } }
    }

    companion object {
        const val MAX_SIDE = 2048
    }
}
