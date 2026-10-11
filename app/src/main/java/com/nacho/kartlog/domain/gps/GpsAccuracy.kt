package com.nacho.kartlog.domain.gps

import kotlin.math.abs

/**
 * Compara los tiempos medidos por GPS con los oficiales de la misma tanda: es el dato que decide si el GPS del
 * móvil basta o hace falta un receptor externo. Dominio puro.
 */
object GpsAccuracy {
    data class Row(
        /** Nº de vuelta GPS (1, 2, …) de la comparación. */
        val number: Int,
        val officialMs: Long,
        val gpsMs: Long,
    ) {
        /** GPS − oficial: positivo = el GPS midió de más. */
        val diffMs: Long get() = gpsMs - officialMs
    }

    data class Report(
        val rows: List<Row>,
        val gpsCount: Int,
        val officialCount: Int,
        val offset: Int,
    ) {
        /** Error medio absoluto, en milisegundos; `null` sin vueltas comparables. */
        val meanAbsErrorMs: Double? get() = rows.takeIf { it.isNotEmpty() }?.map { abs(it.diffMs).toDouble() }?.average()
        val maxAbsErrorMs: Long? get() = rows.maxOfOrNull { abs(it.diffMs) }

        /** Sesgo medio con signo (si es sistemático, se puede compensar desplazando la meta). */
        val meanSignedErrorMs: Double? get() = rows.takeIf { it.isNotEmpty() }?.map { it.diffMs.toDouble() }?.average()

        /** El nº de vueltas de uno y otro no coincide (tras aplicar el desplazamiento). */
        val countMismatch: Boolean get() = gpsCount != officialCount
    }

    /**
     * Empareja la vuelta GPS n con la oficial n (por nº de vuelta). [offset] desplaza el emparejamiento por si el
     * cronometraje oficial cuenta una vuelta más al principio (salida): la GPS i se compara con la oficial i + offset.
     */
    fun compare(officialMs: List<Long>, gpsMs: List<Long>, offset: Int = 0): Report {
        val rows = gpsMs.mapIndexedNotNull { i, gps ->
            officialMs.getOrNull(i + offset)?.let { Row(number = i + 1, officialMs = it, gpsMs = gps) }
        }
        return Report(rows, gpsCount = gpsMs.size, officialCount = officialMs.size - offset.coerceAtLeast(0), offset = offset)
    }
}
