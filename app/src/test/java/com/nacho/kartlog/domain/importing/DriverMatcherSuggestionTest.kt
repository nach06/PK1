package com.nacho.kartlog.domain.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A9: sin nombre guardado se sugiere la columna elegida la última vez (por nombre y, si no, por kart). */
class DriverMatcherSuggestionTest {
    private fun lap(n: Int) = ParsedLap(n, 80_000L + n)
    private val deyvis = ParsedDriver(name = "Deyvis", kartNumber = 26, laps = listOf(lap(1)))
    private val nacho = ParsedDriver(name = "Nacho Kart", kartNumber = 28, columnIndex = 1, laps = listOf(lap(1)))
    private val sinNombre = ParsedDriver(name = null, kartNumber = 28, columnIndex = 2, laps = listOf(lap(1)))

    @Test
    fun encodesAndFindsByName() {
        val saved = DriverMatcher.encodeColumn(nacho.name, nacho.kartNumber)
        assertEquals("Nacho Kart|28", saved)
        assertEquals(nacho, DriverMatcher.findSuggested(listOf(deyvis, nacho), saved))
    }

    @Test
    fun fallsBackToTheKartNumberWhenTheNameIsGone() {
        assertEquals(sinNombre, DriverMatcher.findSuggested(listOf(deyvis, sinNombre), "Nacho Kart|28"))
    }

    @Test
    fun noSuggestionWithoutMemoryOrWithAnAmbiguousMatch() {
        assertNull(DriverMatcher.findSuggested(listOf(deyvis, nacho), ""))
        assertNull(DriverMatcher.findSuggested(listOf(deyvis, nacho), "Otro|99"))
        assertNull(DriverMatcher.findSuggested(listOf(nacho, sinNombre.copy(name = "Otro")), "|28")) // dos karts 28
    }
}
