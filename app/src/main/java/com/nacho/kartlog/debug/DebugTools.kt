package com.nacho.kartlog.debug

import com.nacho.kartlog.domain.gps.LocationSource

/** Herramientas solo para builds debug. En release no existe implementación ([DebugToolsFactory] devuelve `null`). */
/** Las dos muestras de texto de los tests (`app/src/test/resources/muestras/`) disponibles en la build debug. */
enum class TextSample(val assetName: String) {
    /** Correo «Resumen de tu carrera» (una celda por línea). */
    A("muestra_a_correo_lineas.txt"),

    /** Hoja impresa de Asupark (coma decimal, tabla resumen por kart). */
    B("muestra_b_asupark.txt"),
}

/** Qué hay en `assets/muestras/` de esta build (para entender por qué «no hay muestras»). */
data class SampleDiagnosis(val entries: List<String>, val imageCount: Int, val expectedDir: String, val projectDir: String)

/** Una tanda de resistencia lista para simular: origen de posiciones, circuito con meta y guion de paradas. */
class EnduranceSimulation(
    val source: LocationSource,
    val circuitId: Long,
    val finish: com.nacho.kartlog.domain.model.FinishLine,
    val pitAfterLaps: List<Int>,
    val drivers: List<String>,
)

interface DebugTools {
    /** Crea el circuito [name] si no existe y devuelve su id (para que una importación de muestra pueda guardarse sin teclear). */
    suspend fun ensureCircuit(name: String, lengthM: Int): Long

    /**
     * Guarda una copia (.zip) donde se ve sin selector de ficheros: `Android/data/<app>/files/Download/` (siempre) y, en Android 10+,
     * también en `Descargas/` (mejor esfuerzo). Devuelve la ruta legible o `null` si falla.
     */
    suspend fun saveBackupCopy(file: java.io.File): String?

    /** Abre la copia guardada con [saveBackupCopy] más reciente, o `null` si no hay ninguna. */
    fun latestBackupOpener(): (() -> java.io.InputStream?)?

    /** Prepara una resistencia simulada (óvalo, 12 vueltas aceleradas) con 2 paradas en boxes automáticas. */
    suspend fun prepareEnduranceSimulation(): EnduranceSimulation

    /** Diagnóstico de la carpeta de muestras. */
    fun sampleDiagnosis(): SampleDiagnosis

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

    /**
     * Crea una tanda GPS de ejemplo (8 vueltas de un óvalo sintético con su trazado en `tracks/`) para ver la tarjeta «Trazado» y las cards
     * de Tandas con icono GPS sin salir a pista. Devuelve el id de la tanda.
     */
    suspend fun seedGpsSession(): Long

    /** Texto de una muestra de los fixtures de test, o `null` si no está en los assets. */
    suspend fun loadTextSample(sample: TextSample): String?
}
