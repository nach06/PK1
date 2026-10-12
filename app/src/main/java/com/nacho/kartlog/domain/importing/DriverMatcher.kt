package com.nacho.kartlog.domain.importing

/** Elige al usuario entre las columnas detectadas a partir del ajuste «Mi nombre de piloto». */
object DriverMatcher {
    /**
     * Coincidencia sin distinguir mayúsculas ni acentos: primero igualdad exacta y, si no, «contiene»
     * en cualquiera de los dos sentidos. Si hay varias coincidencias de «contiene» devuelve `null`
     * (el usuario toca su columna): es mejor preguntar que elegir mal.
     */
    fun findMe(drivers: List<ParsedDriver>, myName: String): ParsedDriver? {
        val mine = normalizeKey(myName)
        if (mine.length < 2) return null
        val named = drivers.filter { !it.name.isNullOrBlank() }
        named.singleOrNull { normalizeKey(it.name.orEmpty()) == mine }?.let { return it }
        val containing = named.filter {
            val other = normalizeKey(it.name.orEmpty())
            other.contains(mine) || mine.contains(other)
        }
        return containing.singleOrNull()
    }

    /** Codifica la columna elegida (`nombre|kart`) para recordarla. */
    fun encodeColumn(name: String?, kartNumber: Int?): String = "${name.orEmpty().replace('|', ' ')}|${kartNumber ?: ""}"

    /**
     * La columna que se eligió la última vez ([encoded] = `nombre|kart`): por nombre si es único y, si no, por número de kart.
     * Solo es una **sugerencia** (no se marca como «tuya»): `null` si no hay recuerdo o no hay una coincidencia única.
     */
    fun findSuggested(drivers: List<ParsedDriver>, encoded: String): ParsedDriver? {
        if (encoded.isBlank()) return null
        val name = encoded.substringBefore('|')
        val kart = encoded.substringAfter('|', "").toIntOrNull()
        val withLaps = drivers.filter { it.laps.isNotEmpty() }
        if (name.isNotBlank()) {
            val key = normalizeKey(name)
            withLaps.filter { !it.name.isNullOrBlank() && normalizeKey(it.name.orEmpty()) == key }.singleOrNull()?.let { return it }
        }
        return if (kart != null) withLaps.filter { it.kartNumber == kart }.singleOrNull() else null
    }
}
