package com.nacho.kartlog.debug

/** Herramientas solo para builds debug. En release no existe implementación ([DebugToolsFactory] devuelve `null`). */
interface DebugTools {
    /** Inserta circuitos, tandas y vueltas de ejemplo. Devuelve el nº de tandas creadas. */
    suspend fun seed(): Int

    /** Borra todos los datos de la app. */
    suspend fun wipeAll()
}
