package com.nacho.kartlog.data.gps

import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.TrackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Guarda cada trazado en `{dir}/{sessionId}.csv` con `elapsedNs;lat;lon;speedMps;accuracyM` (una línea por fix,
 * separador `;`, punto decimal). Son ficheros pequeños: un fix por segundo son ~60 bytes.
 */
class FileTrackStore(private val dir: File) : TrackStore {
    private fun file(sessionId: Long) = File(dir, "$sessionId.csv")

    override suspend fun save(sessionId: Long, fixes: List<GpsFix>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val tmp = File(dir, "$sessionId.csv.tmp")
        tmp.bufferedWriter().use { out ->
            out.appendLine(HEADER)
            fixes.forEach { f ->
                out.appendLine(String.format(Locale.ROOT, "%d;%.7f;%.7f;%.2f;%.1f", f.elapsedNs, f.lat, f.lon, f.speedMps, f.accuracyM))
            }
        }
        // Escritura atómica: o queda el fichero entero o no queda ninguno.
        check(tmp.renameTo(file(sessionId)) || (file(sessionId).delete() && tmp.renameTo(file(sessionId)))) { "no se pudo guardar el trazado" }
    }

    override suspend fun load(sessionId: Long): List<GpsFix> = withContext(Dispatchers.IO) {
        val f = file(sessionId)
        if (!f.exists()) return@withContext emptyList()
        f.useLines { lines ->
            lines.drop(1).mapNotNull { line ->
                val p = line.split(';')
                if (p.size < 5) return@mapNotNull null
                runCatching { GpsFix(p[0].toLong(), p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[4].toFloat()) }.getOrNull()
            }.toList()
        }
    }

    override suspend fun delete(sessionId: Long) {
        withContext(Dispatchers.IO) { file(sessionId).delete() }
    }

    private companion object {
        const val HEADER = "elapsedNs;lat;lon;speedMps;accuracyM"
    }
}
