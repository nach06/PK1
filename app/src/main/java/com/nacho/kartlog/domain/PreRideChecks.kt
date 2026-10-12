package com.nacho.kartlog.domain

/** Estado del móvil relevante para salir a pista (lo rellena la capa Android; aquí es solo datos). */
data class DeviceSnapshot(
    val batteryPercent: Int,
    val charging: Boolean,
    val locationEnabled: Boolean,
    val locationPermission: Boolean,
    /** Brillo de pantalla 0…255, o `null` si es automático / desconocido. */
    val brightness: Int?,
    val powerSaveOn: Boolean,
)

/** Lista «Antes de salir» (dominio puro): cada comprobación es verde (OK) o ámbar (WARN). */
object PreRideChecks {
    enum class Id { BATTERY, LOCATION, PERMISSION, BRIGHTNESS, POWER_SAVE, MOUNT }
    enum class State { OK, WARN }
    data class Check(val id: Id, val state: State)

    const val MIN_BATTERY_PERCENT = 50
    const val MIN_BRIGHTNESS = 150

    /** [mountConfirmed]: el usuario marcó que el móvil va fijado al soporte (no se puede saber por software). */
    fun evaluate(d: DeviceSnapshot, mountConfirmed: Boolean): List<Check> = listOf(
        Check(Id.BATTERY, if (d.batteryPercent >= MIN_BATTERY_PERCENT || d.charging) State.OK else State.WARN),
        Check(Id.LOCATION, if (d.locationEnabled) State.OK else State.WARN),
        Check(Id.PERMISSION, if (d.locationPermission) State.OK else State.WARN),
        Check(Id.BRIGHTNESS, if (d.brightness == null || d.brightness >= MIN_BRIGHTNESS) State.OK else State.WARN),
        Check(Id.POWER_SAVE, if (!d.powerSaveOn) State.OK else State.WARN),
        Check(Id.MOUNT, if (mountConfirmed) State.OK else State.WARN),
    )

    fun allOk(checks: List<Check>) = checks.all { it.state == State.OK }
}
