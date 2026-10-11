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

/** Tanda GPS a medio guardar: la preparación y todas las posiciones recibidas hasta el último volcado. */
data class SessionDraft(
    val setup: GpsSessionSetup,
    val fixes: List<GpsFix>,
    /** Hora (epoch ms) del último volcado a disco. */
    val savedAtMs: Long,
)

/**
 * Borrador de la tanda en curso para sobrevivir a que el sistema mate la app: las posiciones se añaden a
 * `tracks/draft.partial.csv` cada ~30 s (y al parar) junto a `draft.partial.json` con la preparación. Las vueltas no se
 * guardan: se recalculan al recuperar pasando las posiciones por el detector, que es determinista.
 */
interface DraftStore {
    suspend fun begin(setup: GpsSessionSetup)

    suspend fun append(fixes: List<GpsFix>)

    /** El borrador pendiente, o `null` si no hay ninguno o está corrupto/vacío. */
    suspend fun load(): SessionDraft?

    suspend fun clear()
}
