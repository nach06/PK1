package com.nacho.kartlog.ui.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.util.LapCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fileDate: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

/** Genera el CSV de la sesión en `cache/exports` y abre el selector de compartir de Android. */
suspend fun shareLapsCsv(context: Context, circuitName: String, dateTime: Long, laps: List<Lap>) {
    val safeName = circuitName.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "tanda" }
    val stamp = fileDate.format(Instant.ofEpochMilli(dateTime).atZone(ZoneId.systemDefault()))
    shareCsv(context, "kartlog_${safeName}_$stamp.csv", LapCsv.build(laps))
}

/** Escribe [content] en `cache/exports/[fileName]` y abre el selector de compartir de Android. */
suspend fun shareCsv(context: Context, fileName: String, content: String) {
    val intent = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    val chooser = Intent.createChooser(intent, context.getString(R.string.share_csv_title))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

/** Comparte la copia de seguridad (.zip) con el selector de Android. El fichero ya está en `cache/exports` (FileProvider). */
fun shareBackup(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.backup_share_title)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
