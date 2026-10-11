package com.nacho.kartlog.domain.gps

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Reproduce un trazado ya grabado. [speedFactor] acelera la espera entre posiciones (2.0 = el doble de rápido),
 * pero cada [GpsFix] conserva su `elapsedNs` original: los tiempos de vuelta que se calculen son **los reales**
 * del trazado, no los de la reproducción. Con [speedFactor] <= 0 se emite todo sin esperar (útil en tests).
 */
class ReplayLocationSource(
    private val fixes: List<GpsFix>,
    private val speedFactor: Double = 1.0,
) : LocationSource {
    override val playbackSpeed: Double get() = if (speedFactor > 0.0) speedFactor else 1.0

    override fun fixes(): Flow<GpsFix> = flow {
        var previous: GpsFix? = null
        for (fix in fixes) {
            val last = previous
            if (last != null && speedFactor > 0.0) {
                val waitMs = ((fix.elapsedNs - last.elapsedNs) / 1_000_000.0 / speedFactor).toLong()
                if (waitMs > 0) delay(waitMs)
            }
            emit(fix)
            previous = fix
        }
    }

    companion object {
        /** Desde un CSV `elapsedNs;lat;lon;speedMps;accuracyM` (el formato de `tracks/{id}.csv`) o un GPX. */
        fun fromText(text: String, speedFactor: Double = 1.0): ReplayLocationSource =
            ReplayLocationSource(TrackFormats.parse(text), speedFactor)
    }
}
