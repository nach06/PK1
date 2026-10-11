package com.nacho.kartlog.domain.settings

import kotlinx.coroutines.flow.StateFlow

/** Ajustes del usuario que no pertenecen a ninguna tanda. */
interface UserSettings {
    /** «Mi nombre de piloto»: sirve para preseleccionar tu columna al importar tiempos. */
    val driverName: StateFlow<String>

    fun setDriverName(name: String)

    /** Vibración y pitido corto al cruzar la meta en el Dashboard. Activado por defecto. */
    val lapFeedback: StateFlow<Boolean>

    fun setLapFeedback(enabled: Boolean)
}
