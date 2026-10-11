package com.nacho.kartlog.ui.dashboard

import androidx.compose.ui.graphics.Color

/** Paleta del Dashboard en pista: noche (negro puro) o día/sol (fondo claro, máximo contraste). */
data class DashPalette(
    val background: Color,
    val foreground: Color,
    val muted: Color,
    val good: Color,
    val bad: Color,
    val record: Color,
    val warning: Color,
    val surface: Color,
    val onAccent: Color,
) {
    companion object {
        val Night = DashPalette(
            background = Color.Black, foreground = Color.White, muted = Color(0xFFB8B8B8),
            good = Color(0xFF00E676), bad = Color(0xFFFF4D4D), record = Color(0xFFC77DFF), warning = Color(0xFFFFB300),
            surface = Color(0xFF1C1C1C), onAccent = Color.Black,
        )
        val Day = DashPalette(
            background = Color.White, foreground = Color.Black, muted = Color(0xFF3F3F3F),
            good = Color(0xFF007A33), bad = Color(0xFFC00000), record = Color(0xFF6A1B9A), warning = Color(0xFF8A5A00),
            surface = Color(0xFFE3E3E3), onAccent = Color.White,
        )

        fun of(dayMode: Boolean) = if (dayMode) Day else Night
    }
}

/** Barra de delta: relación entre el delta y el fondo de escala, en [-1, 1] (negativo = ganando, a la izquierda). */
object DeltaBarModel {
    const val FULL_SCALE_MS = 2_000L

    fun fraction(deltaMs: Long?, fullScaleMs: Long = FULL_SCALE_MS): Float {
        if (deltaMs == null) return 0f
        return (deltaMs.toFloat() / fullScaleMs).coerceIn(-1f, 1f)
    }
}

/** Qué se muestra a pantalla completa al cerrar una vuelta. */
enum class LapAlertKind {
    /** Mejor vuelta de la tanda y además mejor que el récord del circuito. */
    RECORD,

    /** Mejor vuelta de la tanda (o la primera). */
    BEST,

    /** Más lenta que la mejor de la tanda. */
    SLOWER,
}

data class LapAlert(val lapNumber: Int, val lapMs: Long, val deltaMs: Long?, val kind: LapAlertKind) {
    companion object {
        const val DURATION_MS = 2_000L

        /**
         * [previousBestMs]: mejor vuelta de la tanda ANTES de esta (`null` en la primera). [circuitRecordMs]: récord guardado
         * del circuito (sin esta tanda); sin historial no hay récord que batir, así que la primera vez en un circuito sale `BEST`.
         */
        fun of(lapNumber: Int, lapMs: Long, previousBestMs: Long?, isBest: Boolean, circuitRecordMs: Long?): LapAlert {
            val delta = previousBestMs?.let { lapMs - it }
            val kind = when {
                isBest && circuitRecordMs != null && lapMs < circuitRecordMs -> LapAlertKind.RECORD
                isBest -> LapAlertKind.BEST
                else -> LapAlertKind.SLOWER
            }
            return LapAlert(lapNumber, lapMs, delta, kind)
        }
    }
}

/** Tiempo del stint en curso: vueltas cerradas del stint + la vuelta en curso. */
fun stintElapsedMs(laps: List<Long>, lapStint: List<Int>, currentStintIndex: Int, currentLapMs: Long?): Long =
    laps.filterIndexed { i, _ -> lapStint.getOrNull(i) == currentStintIndex }.sum() + (currentLapMs ?: 0L)

/** Batería: aviso cuando queda menos del 20 % y no está cargando. */
data class BatteryStatus(val levelPercent: Int, val charging: Boolean) {
    val low: Boolean get() = levelPercent in 0 until LOW_PERCENT && !charging

    companion object {
        const val LOW_PERCENT = 20
    }
}
