package com.nacho.kartlog.domain.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackExportTest {
    private val oval = OvalTrack()
    private val sim = TrackSimulator.simulate(oval, listOf(58_400L, 57_900L, 58_120L), hz = 1.0)

    @Test
    fun gpxHasOneTrackPerLapWithTheLapTimeInItsName_andNoRivalData() {
        val crossings = TrackExport.crossingsOf(sim.fixes, oval.finishLine)
        assertEquals(4, crossings.size)
        val gpx = TrackExport.gpx("Óvalo & co · 12 oct", 1_760_000_000_000L, sim.fixes, crossings)
        assertTrue(gpx.contains("<name>V1 0:58.400</name>") || gpx.contains("<name>V1 0:58.39"))
        assertTrue(gpx.contains("<name>V2 0:57.9"))
        assertTrue(gpx.contains("<name>V3 0:58.1"))
        assertEquals(3 + 2, Regex("<trk>").findAll(gpx).count()) // 3 vueltas + salida + entrada
        assertTrue(gpx.contains("Óvalo &amp; co")) // escapado
        assertTrue(gpx.contains("<time>2025-10-09T")) // hora absoluta
        assertFalse(gpx.contains("rival", ignoreCase = true))
        // Se puede volver a leer con el lector de GPX de la app: mismos puntos que posiciones (los segmentos no se solapan).
        assertEquals(sim.fixes.size, TrackFormats.parseGpx(gpx).size)
    }

    @Test
    fun withoutCrossingsTheWholeTrackIsOneTrk() {
        val gpx = TrackExport.gpx("Tanda", 1_760_000_000_000L, sim.fixes, emptyList())
        assertEquals(1, Regex("<trk>").findAll(gpx).count())
    }

    @Test
    fun crossingsUseTheRawLogMarksWhenPresent_andTheFinishLineWhenTheFileIsOld() {
        val marks = sim.crossingNs
        val marked = sim.fixes.map { f -> marks.firstOrNull { it > f.elapsedNs - 1_000_000_000L && it <= f.elapsedNs }?.let { f.copy(crossingNs = it) } ?: f }
        assertEquals(marks.size, TrackExport.crossingsOf(marked, null).size)
        assertEquals(marks.size, TrackExport.crossingsOf(sim.fixes, oval.finishLine).size)
        assertTrue(TrackExport.crossingsOf(sim.fixes, null).isEmpty())
    }
}
