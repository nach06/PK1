package com.nacho.kartlog.domain.gps

/**
 * Guion de paradas de la simulación de resistencia (solo depuración): pide PIT cuando la tanda lleva [afterLaps] vueltas válidas
 * cerradas, con el piloto entrante correspondiente. Cada parada se pide una sola vez.
 */
class PitScript(private val afterLaps: List<Int>, private val drivers: List<String>) {
    private var next = 0

    /** El piloto entrante si ahora toca pedir PIT (`null` si no toca). Solo avanza cuando la petición debe hacerse de verdad. */
    fun due(validLaps: Int, started: Boolean, pitPending: Boolean): String? {
        if (!started || pitPending || next >= afterLaps.size) return null
        if (validLaps < afterLaps[next]) return null
        val driver = drivers.getOrElse(next) { "Piloto ${next + 2}" }
        next++
        return driver
    }
}
