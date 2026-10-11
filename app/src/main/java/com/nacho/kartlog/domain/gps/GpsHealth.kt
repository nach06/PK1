package com.nacho.kartlog.domain.gps

/** Salud de la señal GPS en tres niveles, por la precisión horizontal declarada del último fix. */
enum class GpsHealth {
    /** Precisión ≤ 8 m. */
    GOOD,

    /** Entre 8 y 15 m. */
    FAIR,

    /** Más de 15 m, o sin señal reciente. */
    POOR,
    ;

    companion object {
        const val GOOD_MAX_M = 8f
        const val FAIR_MAX_M = 15f

        /** `null` mientras no haya ningún fix. [signalLost] fuerza `POOR`. */
        fun of(accuracyM: Float?, signalLost: Boolean = false): GpsHealth? = when {
            signalLost -> POOR
            accuracyM == null -> null
            accuracyM <= GOOD_MAX_M -> GOOD
            accuracyM <= FAIR_MAX_M -> FAIR
            else -> POOR
        }
    }
}
