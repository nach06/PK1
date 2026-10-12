package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import kotlin.math.abs
import kotlin.math.max

/** Ajustes del detector de meta. */
data class GateConfig(
    /** Vuelta mínima absoluta; el efectivo es `max(minLapMs, minLapFractionOfBest · mejor)`. */
    val minLapMs: Long = 20_000,
    val minLapFractionOfBest: Double = 0.5,
    /** Un cruce con cualquiera de sus dos fixes peor que esto (metros) se descarta. */
    val maxAccuracyM: Float = 15f,
    /** Si entre los dos fixes del cruce pasó más de esto, el tiempo es poco fiable (se marca, no se descarta). */
    val maxReliableGapNs: Long = 2_500_000_000L,
    /**
     * Retardo (ms) con el que el proveedor de posición entrega cada posición respecto al instante real (los proveedores «fusionados»
     * filtran y publican con retraso). Un retardo **constante** no cambia los tiempos de vuelta (desplaza igual las dos pasadas);
     * solo corre el instante absoluto del cruce. Con el valor aquí puesto se adelanta el cruce interpolado ese tiempo (−500…+500 ms,
     * 0 por defecto). Solo es una ayuda de depuración para alinear el cruce con lo que se ve en pista.
     */
    val delayCompensationMs: Long = 0,
)

/** Un cruce válido de la meta, con el instante interpolado en el reloj monótono. */
data class GateCrossing(
    /** `elapsedNs` interpolado del instante exacto del cruce. */
    val elapsedNs: Long,
    /** Separación lateral respecto al centro de la puerta (m, positivo a la derecha de la marcha). */
    val lateralOffsetM: Double,
    /** `false` si los dos fixes que lo rodean están muy separados en el tiempo (fix perdido). */
    val reliable: Boolean,
)

/** Resultado de procesar un fix: el cruce (si lo hubo) y, si ya había uno anterior, la vuelta cerrada. */
data class GateEvent(
    val crossing: GateCrossing,
    /** `null` en el primer cruce (solo arranca el cronómetro); si no, la vuelta que acaba de cerrarse. */
    val lapTimeMs: Long?,
    /** Nº de la vuelta cerrada (1, 2, …); `null` en el primer cruce. */
    val lapNumber: Int?,
)

/**
 * Detector de cruce de la línea de meta (dominio puro, sin Android).
 *
 * La meta es una «puerta»: un segmento perpendicular al rumbo de paso, de [FinishLine.gateWidthM] metros, centrado
 * en el punto marcado. Para cada par de fixes consecutivos:
 * 1. Se proyecta todo a metros alrededor de la meta y se calcula la distancia con signo de cada fix a la línea
 *    (positiva = ya pasada, en el sentido del rumbo; producto escalar con el rumbo).
 * 2. Hay cruce **solo** si el primer fix está antes (d1 < 0) y el segundo después (d2 ≥ 0): los cruces hacia atrás
 *    se ignoran. El punto de cruce debe quedar dentro de la puerta (±ancho/2): pasar cerca de la meta por otro
 *    lado del circuito no cuenta.
 * 3. **Tiempo interpolado:** `t = |d1| / (|d1| + |d2|)` sobre el `elapsedNs` de los dos fixes. Con velocidad
 *    constante por la recta es exacto, así que la frecuencia del GPS (1 Hz) no limita la resolución.
 * 4. Guardas: precisión de los fixes ([GateConfig.maxAccuracyM]), vuelta mínima/anti-rebote y fixes perdidos.
 *
 * **Error esperado.** Un error de posición *a lo largo de la marcha* de ε metros desplaza el cruce ε / v segundos
 * (a 20 m/s, 3 m = 150 ms); un error *lateral* no cambia la distancia a la línea y no afecta al tiempo (mientras siga dentro de
 * la puerta). Con ruido independiente en los dos fixes el error del cruce queda acotado por `max(|ε₁|, |ε₂|) / v`; como cada vuelta
 * se mide entre dos cruces con errores independientes, el error del tiempo de vuelta es ≈ √2 veces el del cruce.
 *
 * **Retardo del proveedor fusionado.** Si cada posición llega con un retardo *constante* D, los dos cruces de una vuelta se
 * corren D y el **tiempo de vuelta no cambia**; solo se mueve el instante absoluto del cruce (+D) y, por tanto, la hora de
 * inicio de la tanda y el instante en que el Dashboard cree que has pasado por la línea. Un retardo que *varía* entre pasadas
 * (el filtro del proveedor reacciona distinto en frenada que en recta) sí afecta al tiempo: ΔD de 100 ms = 100 ms de error.
 * [GateConfig.delayCompensationMs] resta un retardo conocido al cruce (ajuste de depuración).
 */
class GateDetector(
    finish: FinishLine,
    private val config: GateConfig = GateConfig(),
) {
    private val frame = LocalFrame(finish.lat, finish.lon)
    private val heading = GeoMath.unit(finish.bearingDeg)
    private val rightOfHeading = XY(heading.y, -heading.x)
    private val halfGate = finish.gateWidthM / 2.0

    private var previous: GpsFix? = null
    private var lastCrossingNs: Long? = null
    private var lapCount = 0
    private var bestLapMs: Long? = null

    /** Vueltas cerradas hasta ahora. */
    val lapsCompleted: Int get() = lapCount

    /** Procesa el siguiente fix; devuelve el evento si cruzó la meta. */
    fun feed(fix: GpsFix): GateEvent? {
        val before = previous
        previous = fix
        if (before == null) return null
        if (fix.elapsedNs <= before.elapsedNs) return null // reloj no monótono: fix repetido o desordenado

        val p0 = frame.toXY(before.lat, before.lon)
        val p1 = frame.toXY(fix.lat, fix.lon)
        val d0 = p0.x * heading.x + p0.y * heading.y
        val d1 = p1.x * heading.x + p1.y * heading.y
        if (!(d0 < 0.0 && d1 >= 0.0)) return null // solo en el sentido del rumbo

        val t = abs(d0) / (abs(d0) + abs(d1))
        val cx = p0.x + t * (p1.x - p0.x)
        val cy = p0.y + t * (p1.y - p0.y)
        val lateral = cx * rightOfHeading.x + cy * rightOfHeading.y
        if (abs(lateral) > halfGate) return null // pasó por fuera de la puerta

        if (before.accuracyM > config.maxAccuracyM || fix.accuracyM > config.maxAccuracyM) return null

        val crossNs = before.elapsedNs + Math.round(t * (fix.elapsedNs - before.elapsedNs)) - config.delayCompensationMs * 1_000_000L
        val last = lastCrossingNs
        if (last != null && (crossNs - last) / 1_000_000L < minLapMs()) return null // rebote o corte

        val reliable = fix.elapsedNs - before.elapsedNs <= config.maxReliableGapNs
        val crossing = GateCrossing(crossNs, lateral, reliable)
        lastCrossingNs = crossNs
        if (last == null) return GateEvent(crossing, null, null)

        val lapMs = Math.round((crossNs - last) / 1_000_000.0)
        lapCount += 1
        bestLapMs = bestLapMs?.let { minOf(it, lapMs) } ?: lapMs
        return GateEvent(crossing, lapMs, lapCount)
    }

    private fun minLapMs(): Long {
        val best = bestLapMs ?: return config.minLapMs
        return max(config.minLapMs, (best * config.minLapFractionOfBest).toLong())
    }
}
