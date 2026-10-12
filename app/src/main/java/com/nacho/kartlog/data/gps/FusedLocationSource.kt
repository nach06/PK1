package com.nacho.kartlog.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * GPS interno con `FusedLocationProviderClient` en prioridad alta. Pide el intervalo más bajo (100 ms) con
 * mínimo igual: el dispositivo entrega lo que su receptor permita (lo normal son ~1 Hz; algunos llegan a 5–10 Hz).
 * Requiere el permiso `ACCESS_FINE_LOCATION` ya concedido; si no, el flujo termina con `SecurityException`.
 */
class FusedLocationSource(
    context: Context,
    private val intervalMs: Long = 100L,
) : LocationSource {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context.applicationContext) }

    @SuppressLint("MissingPermission")
    override fun fixes(): Flow<GpsFix> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs)
            .setMaxUpdateDelayMillis(0L) // sin agrupar: cada posición en cuanto llega
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.toFix() = GpsFix(
        elapsedNs = elapsedRealtimeNanos, // reloj monótono del propio fix
        lat = latitude,
        lon = longitude,
        speedMps = if (hasSpeed()) speed else 0f,
        accuracyM = if (hasAccuracy()) accuracy else UNKNOWN_ACCURACY_M,
        bearingDeg = if (hasBearing()) bearing else null,
        provider = provider,
    )

    private companion object {
        /** Un fix sin precisión declarada no se fía: queda por encima del umbral del detector. */
        const val UNKNOWN_ACCURACY_M = 99f
    }
}
