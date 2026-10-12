package com.nacho.kartlog.domain.settings

import kotlinx.coroutines.flow.StateFlow

/** Orientación del Dashboard en pista (Sprint 6, B1). */
enum class DashboardOrientation { LANDSCAPE, AUTO, PORTRAIT }

/** Filtro de la lista de Tandas que se recuerda entre sesiones (circuito, tipo y orden). */
data class SavedListFilter(val circuitId: Long? = null, val type: String? = null, val sort: String? = null)

/** Ajustes del usuario que no pertenecen a ninguna tanda. */
interface UserSettings {
    /** «Mi nombre de piloto»: sirve para preseleccionar tu columna al importar tiempos. */
    val driverName: StateFlow<String>

    fun setDriverName(name: String)

    /** Solo depuración: compensación del retardo del GPS (ms, −500…+500, 0 por defecto) que se resta al instante interpolado del cruce. */
    val gpsDelayMs: StateFlow<Int>

    fun setGpsDelayMs(value: Int)

    /** Última columna marcada con «Soy yo» al importar (`nombre|kart`): se sugiere la próxima vez si no hay nombre guardado. */
    val lastImportColumn: StateFlow<String>

    fun setLastImportColumn(value: String)

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

    /** Última selección de filtros de Tandas (se recuerda al volver a abrir la app). */
    val listFilter: StateFlow<SavedListFilter>

    fun setListFilter(filter: SavedListFilter)

    /** Copia de seguridad automática semanal en la carpeta de la app. Activada por defecto. */
    val autoBackup: StateFlow<Boolean>

    fun setAutoBackup(enabled: Boolean)

    /** Cuándo se hizo la última copia automática (epoch ms; 0 = nunca). */
    val lastAutoBackupMs: StateFlow<Long>

    fun setLastAutoBackupMs(epochMs: Long)
}
