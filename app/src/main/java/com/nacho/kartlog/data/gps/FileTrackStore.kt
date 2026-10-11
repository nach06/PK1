package com.nacho.kartlog.data.gps

import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.TrackFormats
import com.nacho.kartlog.domain.gps.TrackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Guarda cada trazado en `{dir}/{sessionId}.csv` con `elapsedNs;lat;lon;speedMps;accuracyM` (una línea por fix,
 * separador `;`, punto decimal). Son ficheros pequeños: un fix por segundo son ~60 bytes.
 */
class FileTrackStore(private val dir: File) : TrackStore {
    private fun file(sessionId: Long) = File(dir, "$sessionId.csv")

    override suspend fun save(sessionId: Long, fixes: List<GpsFix>) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val tmp = File(dir, "$sessionId.csv.tmp")
        tmp.writeText(TrackFormats.toCsv(fixes))
        // Escritura atómica: o queda el fichero entero o no queda ninguno.
        check(tmp.renameTo(file(sessionId)) || (file(sessionId).delete() && tmp.renameTo(file(sessionId)))) { "no se pudo guardar el trazado" }
    }

    override suspend fun load(sessionId: Long): List<GpsFix> = withContext(Dispatchers.IO) {
        val f = file(sessionId)
        if (!f.exists()) return@withContext emptyList()
        TrackFormats.parseCsv(f.readText())
    }

    override suspend fun delete(sessionId: Long) {
        withContext(Dispatchers.IO) { file(sessionId).delete() }
    }
}
