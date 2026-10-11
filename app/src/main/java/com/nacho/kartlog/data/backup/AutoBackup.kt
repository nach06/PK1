package com.nacho.kartlog.data.backup

import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copia de seguridad automática semanal: al abrir la app, si ha pasado una semana desde la última y hay datos, deja un `.zip` en
 * `filesDir/backups/` (se conservan las últimas [keep]). Es la misma copia que la manual (incluye datos de rivales y trazados) y
 * nunca sale de la app por sí sola: para guardarla fuera hay que compartirla desde Ajustes.
 */
class AutoBackup(
    private val backup: BackupService,
    private val dir: File,
    private val settings: UserSettings,
    private val hasData: suspend () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val keep: Int = 4,
) {
    /** Hace la copia si toca; devuelve el fichero creado o `null` (desactivada, no toca todavía, sin datos o fallo). */
    suspend fun runIfDue(): File? = withContext(Dispatchers.IO) {
        if (!settings.autoBackup.value) return@withContext null
        val last = settings.lastAutoBackupMs.value
        if (last != 0L && now() - last < INTERVAL_MS) return@withContext null
        if (!hasData()) return@withContext null
        runCatching {
            val exported = backup.export()
            dir.mkdirs()
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now()))
            val target = File(dir, "kartlog_auto_$stamp.zip")
            exported.copyTo(target, overwrite = true)
            exported.delete()
            // Solo se conservan las últimas copias.
            dir.listFiles { f -> f.name.startsWith("kartlog_auto_") && f.name.endsWith(".zip") }.orEmpty()
                .sortedByDescending { it.name }.drop(keep).forEach { it.delete() }
            settings.setLastAutoBackupMs(now())
            target
        }.getOrNull()
    }

    companion object {
        const val INTERVAL_MS = 7L * 24 * 3600 * 1000
    }
}
