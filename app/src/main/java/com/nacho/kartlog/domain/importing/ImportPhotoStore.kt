package com.nacho.kartlog.domain.importing

/**
 * Foto original de la hoja de resultados de una tanda importada (`filesDir/imports/{sessionId}.jpg`). Es opcional (ajuste
 * «Guardar foto de la hoja») y se borra con la tanda y con «Borrar datos de rivales».
 */
interface ImportPhotoStore {
    /** Guarda (reducida y en JPEG) la imagen de [imageUri] como foto de la tanda. Devuelve `false` si no se pudo. */
    suspend fun save(sessionId: Long, imageUri: String): Boolean

    /** Ruta del fichero de la foto de la tanda, si existe. */
    fun pathOf(sessionId: Long): String?

    suspend fun delete(sessionId: Long)

    /** Borra todas las fotos (al borrar los datos de rivales o vaciar la base de datos). */
    suspend fun deleteAll()
}
