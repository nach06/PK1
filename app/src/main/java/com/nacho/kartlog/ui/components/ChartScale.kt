package com.nacho.kartlog.ui.components

import com.nacho.kartlog.domain.analytics.Analytics

/** Escala del eje Y de una gráfica de tiempos y qué puntos quedan recortados en el borde superior. */
data class ChartScale(
    val minY: Double,
    val maxY: Double,
    /** Valores a dibujar: los que superan [maxY] se recortan a [maxY]. */
    val plottedMs: List<Long>,
    /** Índices de los puntos recortados (se dibujan como punto hueco ámbar). */
    val clippedIndexes: Set<Int>,
)

/** Margen del eje respecto a la mejor vuelta y a la mayor vuelta limpia: 0,3 s. */
const val CHART_MARGIN_MS = 300L

/**
 * Calcula el rango del eje Y.
 *
 * - Con [clipOutliers] (gráfica de vueltas): el rango sale solo de las vueltas limpias
 *   (`<= 107 %` de la mejor): `mejor − 0,3 s` … `mayor limpia + 0,3 s`. Las vueltas por encima
 *   (salidas de boxes, errores) no deforman la escala: se recortan en el borde superior.
 * - Sin recorte (evolución entre tandas): rango de todos los datos con un 12 % de holgura.
 *
 * [referenceMs] (p. ej. la media limpia) se incluye siempre en el rango.
 */
fun computeChartScale(timesMs: List<Long>, referenceMs: Long? = null, clipOutliers: Boolean = false): ChartScale {
    require(timesMs.isNotEmpty()) { "Se necesita al menos un valor" }
    if (!clipOutliers) {
        val all = timesMs + listOfNotNull(referenceMs)
        val min = all.min().toDouble()
        val max = all.max().toDouble()
        val pad = ((max - min) * 0.12).coerceAtLeast(CHART_MARGIN_MS.toDouble())
        return ChartScale(min - pad, max + pad, timesMs, emptySet())
    }
    val clean = Analytics.cleanLaps(timesMs)
    val inScale = clean + listOfNotNull(referenceMs)
    val minY = (inScale.min() - CHART_MARGIN_MS).toDouble()
    val maxY = (inScale.max() + CHART_MARGIN_MS).toDouble()
    val clipped = timesMs.indices.filter { timesMs[it] > maxY }.toSet()
    val plotted = timesMs.map { if (it > maxY) maxY.toLong() else it }
    return ChartScale(minY, maxY, plotted, clipped)
}
