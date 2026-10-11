package com.nacho.kartlog.data.backup

import java.io.File
import java.io.InputStream

/** Motivos por los que una copia no se puede restaurar (mensajes claros en la UI). */
enum class BackupError {
    /** No es un `.zip` de KartLog (corrupto, sin base de datos o sin manifiesto). */
    INVALID,

    /** Hecha con una versión más nueva de la app: su esquema no se puede leer. */
    NEWER_SCHEMA,

    /** Fallo al leer o escribir ficheros. */
    IO,
}

class BackupException(val error: BackupError, message: String) : Exception(message)

/** Qué contiene una copia, para enseñarlo antes de sustituir los datos. */
data class BackupInfo(val schemaVersion: Int, val createdAtMs: Long, val trackCount: Int, val appVersion: String)

interface BackupService {
    /** Crea `kartlog_backup_*.zip` (BD con el WAL volcado + trazados) en la caché y devuelve el fichero. */
    suspend fun export(): File

    /** Lee y valida el manifiesto de una copia sin tocar nada. */
    suspend fun inspect(open: () -> InputStream?): Result<BackupInfo>

    /** Sustituye BD y trazados por los de la copia. Cierra la BD: la app debe reiniciarse después. */
    suspend fun restore(open: () -> InputStream?): Result<BackupInfo>
}
