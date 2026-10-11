package com.nacho.kartlog.domain.settings

import kotlinx.coroutines.flow.StateFlow

/** Orientación del Dashboard en pista (Sprint 6, B1). */
enum class DashboardOrientation { LANDSCAPE, AUTO, PORTRAIT }

/** Ajustes del usuario que no pertenecen a ninguna tanda. */
interface UserSettings {
    /** «Mi nombre de piloto»: sirve para preseleccionar tu columna al importar tiempos. */
    val driverName: StateFlow<String>

    fun setDriverName(name: String)

    /** Vibración y pitido corto al cruzar la meta en el Dashboard. Activado por defecto. */
    val lapFeedback: StateFlow<Boolean>

    fun setLapFeedback(enabled: Boolean)

    /** Orientación del Dashboard mientras se cronometra. Apaisado por defecto (móvil fijado al volante). */
    val dashboardOrientation: StateFlow<DashboardOrientation>

    fun setDashboardOrientation(value: DashboardOrientation)

    /** Modo día/sol: fondo claro y contraste máximo. Se conmuta desde la preparación. Desactivado por defecto. */
    val dashboardDayMode: StateFlow<Boolean>

    fun setDashboardDayMode(enabled: Boolean)

    /** Al cerrar vuelta solo vibra (sin pitido). Desactivado por defecto. */
    val vibrationOnly: StateFlow<Boolean>

    fun setVibrationOnly(enabled: Boolean)

    /** Brillo de pantalla al máximo mientras se cronometra. Desactivado por defecto. */
    val maxBrightness: StateFlow<Boolean>

    fun setMaxBrightness(enabled: Boolean)

    /** Conserva la foto original de la hoja al importar (en `imports/{id}.jpg`). Activado por defecto. */
    val saveImportPhoto: StateFlow<Boolean>

    fun setSaveImportPhoto(enabled: Boolean)
}
