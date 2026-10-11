package com.nacho.kartlog.debug

import com.nacho.kartlog.domain.gps.LocationSource

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

    /**
     * Prepara el modo simulador: crea (si no existe) el circuito «Óvalo simulado» con su meta marcada y devuelve un
     * origen de posiciones que reproduce 12 vueltas de un óvalo sintético, 10 veces más rápido que en la realidad.
     */
    suspend fun prepareSimulation(): LocationSource
}
