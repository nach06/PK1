package com.nacho.kartlog.data.backup

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Copia de seguridad en un `.zip`: `manifest.json` (versión de esquema, fecha, versión de la app), `kartlog.db` y
 * los trazados en `tracks/`. **Incluye datos de rivales** (clasificaciones y texto importado): no es el CSV público.
 *
 * Restaurar es destructivo: valida primero todo el zip en una carpeta temporal y solo si es coherente cierra la BD y
 * reemplaza ficheros. Los nombres del zip se comprueban (sin rutas fuera de lo esperado).
 */
class ZipBackupService(
    private val databaseFile: File,
    private val tracksDir: File,
    private val cacheDir: File,
    private val schemaVersion: Int,
    private val appVersion: String,
    /** Vuelca el WAL al fichero principal antes de copiar. */
    private val checkpoint: suspend () -> Unit,
    /** Cierra la BD antes de reemplazar sus ficheros. */
    private val closeDatabase: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : BackupService {

    override suspend fun export(): File = withContext(Dispatchers.IO) {
        runCatching { checkpoint() }
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now()))
        val target = File(dir, "kartlog_backup_$stamp.zip")
        val tracks = tracksDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") && !f.name.startsWith("draft") }.orEmpty().sortedBy { it.name }
        ZipOutputStream(target.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(
                JSONObject().put("schemaVersion", schemaVersion).put("createdAtMs", now()).put("appVersion", appVersion)
                    .put("trackCount", tracks.size).toString().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(DB_ENTRY))
            databaseFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            tracks.forEach { file ->
                zip.putNextEntry(ZipEntry("$TRACKS_PREFIX${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        target
    }

    override suspend fun inspect(open: () -> InputStream?): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching { extract(open).also { it.dir.deleteRecursively() }.info }
    }

    override suspend fun restore(open: () -> InputStream?): Result<BackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val extracted = extract(open)
            try {
                closeDatabase()
                listOf("", "-wal", "-shm", "-journal").forEach { File(databaseFile.path + it).delete() }
                databaseFile.parentFile?.mkdirs()
                File(extracted.dir, DB_ENTRY).copyTo(databaseFile, overwrite = true)
                tracksDir.mkdirs()
                tracksDir.listFiles()?.forEach { it.delete() } // también el borrador de una tanda: la copia lo sustituye todo
                File(extracted.dir, "tracks").listFiles()?.forEach { it.copyTo(File(tracksDir, it.name), overwrite = true) }
            } catch (e: Exception) {
                throw BackupException(BackupError.IO, "No se pudo escribir la copia: ${e.message}")
            } finally {
                extracted.dir.deleteRecursively()
            }
            extracted.info
        }
    }

    private class Extracted(val dir: File, val info: BackupInfo)

    /** Descomprime a una carpeta temporal validando nombres, manifiesto, versión de esquema y que la BD abra. */
    private fun extract(open: () -> InputStream?): Extracted {
        val dir = File(cacheDir, "restore-${System.nanoTime()}").apply { mkdirs() }
        try {
            val input = open() ?: throw BackupException(BackupError.INVALID, "No se pudo abrir el fichero")
            var manifest: JSONObject? = null
            var hasDb = false
            ZipInputStream(input.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit
                        name == MANIFEST -> manifest = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        name == DB_ENTRY -> {
                            File(dir, DB_ENTRY).outputStream().use { zip.copyTo(it) }
                            hasDb = true
                        }
                        name.startsWith(TRACKS_PREFIX) && isSafeTrackName(name.removePrefix(TRACKS_PREFIX)) -> {
                            val target = File(File(dir, "tracks").apply { mkdirs() }, name.removePrefix(TRACKS_PREFIX))
                            target.outputStream().use { zip.copyTo(it) }
                        }
                        else -> throw BackupException(BackupError.INVALID, "Contenido inesperado en la copia: $name")
                    }
                    entry = zip.nextEntry
                }
            }
            val m = manifest ?: throw BackupException(BackupError.INVALID, "La copia no tiene manifiesto")
            if (!hasDb) throw BackupException(BackupError.INVALID, "La copia no contiene la base de datos")
            val version = m.optInt("schemaVersion", -1)
            if (version < 1) throw BackupException(BackupError.INVALID, "Versión de esquema ilegible")
            if (version > schemaVersion) {
                throw BackupException(BackupError.NEWER_SCHEMA, "La copia es de una versión más nueva de KartLog (esquema $version, esta app llega al $schemaVersion)")
            }
            // El fichero debe ser de verdad una BD y su versión interna, la del manifiesto.
            val dbVersion = runCatching {
                SQLiteDatabase.openDatabase(File(dir, DB_ENTRY).path, null, SQLiteDatabase.OPEN_READONLY).use { it.version }
            }.getOrElse { throw BackupException(BackupError.INVALID, "La base de datos de la copia está dañada") }
            if (dbVersion != version) throw BackupException(BackupError.INVALID, "El manifiesto ($version) no coincide con la base de datos ($dbVersion)")
            return Extracted(dir, BackupInfo(version, m.optLong("createdAtMs"), File(dir, "tracks").listFiles()?.size ?: 0, m.optString("appVersion")))
        } catch (e: BackupException) {
            dir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw BackupException(BackupError.INVALID, "El fichero no es una copia de KartLog válida")
        }
    }

    private fun isSafeTrackName(name: String) = name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && !name.contains("..") && name.endsWith(".csv")

    private companion object {
        const val MANIFEST = "manifest.json"
        const val DB_ENTRY = "kartlog.db"
        const val TRACKS_PREFIX = "tracks/"
    }
}
