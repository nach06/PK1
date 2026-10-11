package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import kotlin.math.abs

/**
 * Verificación de la meta guardada con una pasada de comprobación: el detector (con la meta ya guardada) busca el
 * cruce en las posiciones recientes y se compara con el instante en que el usuario dice «aquí está la línea».
 * Si la meta está bien puesta, ambos coinciden con la diferencia de reacción humana (décimas); más de 1 s en
 * tiempo interpolado indica que la puerta no está donde el usuario la ve.
 */
object FinishVerification {
    const val MAX_TIME_DIFF_MS = 1_000L
    const val MAX_BEARING_DIFF_DEG = 20.0

    sealed interface Outcome {
        data class Checked(
            /** Instante interpolado (`elapsedNs`) del cruce detectado. */
            val crossingNs: Long,
            /** Instante en que el usuario confirmó (último fix) − cruce detectado; positivo = confirmó después. */
            val timeDiffMs: Long,
            /** Rumbo de la marcha al cruzar (entre los dos fixes que rodean el cruce). */
            val travelBearingDeg: Double,
            /** Diferencia con el rumbo de la meta guardada, en grados (con signo). */
            val bearingDiffDeg: Double,
            val lateralOffsetM: Double,
        ) : Outcome {
            val timeOk: Boolean get() = abs(timeDiffMs) <= MAX_TIME_DIFF_MS
            val bearingOk: Boolean get() = abs(bearingDiffDeg) <= MAX_BEARING_DIFF_DEG

            /** La meta se considera verificada si tiempo y rumbo cuadran. */
            val ok: Boolean get() = timeOk && bearingOk
        }

        /** No se atravesó la puerta en las posiciones recibidas. */
        data object NoCrossing : Outcome
    }

    /**
     * @param fixes posiciones de la pasada de comprobación (la última es el momento en que se confirmó)
     */
    fun check(line: FinishLine, fixes: List<GpsFix>): Outcome {
        val last = fixes.lastOrNull() ?: return Outcome.NoCrossing
        // Se ignoran el rebote y la vuelta mínima: aquí interesa cada cruce por separado.
        val detector = GateDetector(line, GateConfig(minLapMs = 0, minLapFractionOfBest = 0.0))
        var previous: GpsFix? = null
        var best: Outcome.Checked? = null
        for (fix in fixes) {
            val event = detector.feed(fix)
            if (event != null && previous != null) {
                val frame = LocalFrame(line.lat, line.lon)
                val a = frame.toXY(previous.lat, previous.lon)
                val b = frame.toXY(fix.lat, fix.lon)
                val bearing = GeoMath.bearingDeg(a, b)
                val candidate = Outcome.Checked(
                    crossingNs = event.crossing.elapsedNs,
                    timeDiffMs = Math.round((last.elapsedNs - event.crossing.elapsedNs) / 1_000_000.0),
                    travelBearingDeg = bearing,
                    bearingDiffDeg = GeoMath.angleDiffDeg(bearing, line.bearingDeg),
                    lateralOffsetM = event.crossing.lateralOffsetM,
                )
                // El cruce más cercano al momento de la confirmación.
                if (best == null || abs(candidate.timeDiffMs) < abs(best.timeDiffMs)) best = candidate
            }
            previous = fix
        }
        return best ?: Outcome.NoCrossing
    }
}
