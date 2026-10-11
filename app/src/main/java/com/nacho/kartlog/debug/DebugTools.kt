package com.nacho.kartlog.debug

/** Herramientas solo para builds debug. En release no existe implementación ([DebugToolsFactory] devuelve `null`). */
interface DebugTools {
    /** Inserta circuitos, tandas y vueltas de ejemplo. Devuelve el nº de tandas creadas. */
    suspend fun seed(): Int

    /** Borra todos los datos de la app. */
    suspend fun wipeAll()

    /** Nombres de las imágenes de muestra presentes en `app/src/debug/assets/muestras/` (vacío si no hay ninguna). */
    fun sampleNames(): List<String>

    /** Copia la muestra a la caché y devuelve su `Uri` (como cadena) lista para pasar por el OCR. */
    suspend fun prepareSample(name: String): String?
}
