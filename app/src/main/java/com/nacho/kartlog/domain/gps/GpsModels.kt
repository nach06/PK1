package com.nacho.kartlog.domain.gps

/**
 * Una posición del GPS. [elapsedNs] es un reloj **monótono** (`elapsedRealtimeNanos` del fix), inmune a cambios de
 * hora del sistema; todos los tiempos de vuelta se calculan con él.
 */
data class GpsFix(
    val elapsedNs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Float,
    /** Radio de error horizontal estimado, en metros. */
    val accuracyM: Float,
)

/** Trazado de una tanda GPS, guardado en `tracks/{sessionId}.csv` (no en Room). */
interface TrackStore {
    suspend fun save(sessionId: Long, fixes: List<GpsFix>)

    suspend fun load(sessionId: Long): List<GpsFix>

    suspend fun delete(sessionId: Long)

    /** Borra todos los trazados (al vaciar la base de datos). */
    suspend fun deleteAll()
}
