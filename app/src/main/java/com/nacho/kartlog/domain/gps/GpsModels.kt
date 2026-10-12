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
    /** Rumbo del fix (grados, 0 = norte) si el proveedor lo da. Solo informativo: ningún cálculo depende de él. */
    val bearingDeg: Float? = null,
    /** Proveedor que entregó el fix (`fused`, `gps`…), para saber con qué se midió. */
    val provider: String? = null,
    /** `elapsedNs` interpolado del cruce de meta detectado justo antes de este fix (marca del registro bruto), si lo hubo. */
    val crossingNs: Long? = null,
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
    /** Paradas en boxes pedidas durante la tanda (resistencia). */
    val pits: List<PitRequest> = emptyList(),
)

/**
 * Borrador de la tanda en curso para sobrevivir a que el sistema mate la app: las posiciones se añaden a
 * `tracks/draft.partial.csv` cada ~30 s (y al parar) junto a `draft.partial.json` con la preparación. Las vueltas no se
 * guardan: se recalculan al recuperar pasando las posiciones por el detector, que es determinista.
 */
interface DraftStore {
    suspend fun begin(setup: GpsSessionSetup)

    suspend fun append(fixes: List<GpsFix>)

    /** Anota una parada en boxes pedida (resistencia) para poder rehacer los stints al recuperar. */
    suspend fun appendPit(request: PitRequest)

    /** El borrador pendiente, o `null` si no hay ninguno o está corrupto/vacío. */
    suspend fun load(): SessionDraft?

    suspend fun clear()
}
