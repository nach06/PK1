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
}
