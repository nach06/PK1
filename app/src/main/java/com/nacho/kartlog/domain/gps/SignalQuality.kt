package com.nacho.kartlog.domain.gps

/**
 * Informe de calidad de la señal GPS de una tanda (dominio puro): % de tiempo con precisión ≤ [GOOD_ACCURACY_M], frecuencia media,
 * huecos mayores de [GAP_NS] y una puntuación simple. Se calcula al guardar la tanda y se guarda en `sessions.signalScore`.
 */
data class SignalQuality(
    val goodAccuracyPercent: Int,
    val meanHz: Double,
    val gaps: Int,
    val score: Score,
) {
    enum class Score { GOOD, FAIR, POOR;
        /** Valor guardado en la base de datos. */
        val code: Int get() = ordinal
        companion object { fun fromCode(code: Int?): Score? = entries.getOrNull(code ?: -1) }
    }

    companion object {
        const val GOOD_ACCURACY_M = 8f
        const val GAP_NS = 2_500_000_000L

        /** Buena: ≥ 90 % con precisión ≤ 8 m, ≥ 0,8 Hz y como mucho 1 hueco. Mala: < 60 % o > 5 huecos o < 0,5 Hz. Si no, regular. */
        fun of(fixes: List<GpsFix>): SignalQuality? {
            if (fixes.size < 3) return null
            var goodNs = 0L
            var totalNs = 0L
            var gaps = 0
            for (i in 1 until fixes.size) {
                val dt = fixes[i].elapsedNs - fixes[i - 1].elapsedNs
                if (dt <= 0) continue
                totalNs += dt
                if (dt > GAP_NS) gaps++
                if (fixes[i].accuracyM <= GOOD_ACCURACY_M && fixes[i - 1].accuracyM <= GOOD_ACCURACY_M) goodNs += dt
            }
            if (totalNs <= 0) return null
            val percent = Math.round(goodNs * 100.0 / totalNs).toInt()
            val hz = (fixes.size - 1) / (totalNs / 1e9)
            val score = when {
                percent < 60 || gaps > 5 || hz < 0.5 -> Score.POOR
                percent >= 90 && hz >= 0.8 && gaps <= 1 -> Score.GOOD
                else -> Score.FAIR
            }
            return SignalQuality(percent, hz, gaps, score)
        }
    }
}
