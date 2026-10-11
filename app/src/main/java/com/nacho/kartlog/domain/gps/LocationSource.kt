package com.nacho.kartlog.domain.gps

import kotlinx.coroutines.flow.Flow

/**
 * Origen de posiciones. El resto de la app solo conoce esta interfaz: hoy hay un GPS interno
 * (`FusedLocationSource`) y un reproductor de trazados ([ReplayLocationSource]); en el futuro, un receptor
 * externo por Bluetooth (ESP32) con el mismo contrato.
 *
 * El flujo es **frío**: empieza a pedir posiciones al recogerlo y las deja de pedir al cancelarlo.
 */
interface LocationSource {
    fun fixes(): Flow<GpsFix>
}
