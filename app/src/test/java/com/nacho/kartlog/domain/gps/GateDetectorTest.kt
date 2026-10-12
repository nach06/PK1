package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

class GateDetectorTest {
    private val track = OvalTrack()
    private val laps10 = listOf(58_400L, 57_900, 58_120, 59_300, 57_650, 58_030, 57_880, 58_760, 58_210, 57_990)

    private fun run(
        session: SimulatedSession,
        config: GateConfig = GateConfig(),
        finish: FinishLine = track.finishLine,
    ): List<GateEvent> {
        val detector = GateDetector(finish, config)
        return session.fixes.mapNotNull(detector::feed)
    }

    // ---------- Recta a velocidad constante ----------

    @Test
    fun straightPassAt1Hz_interpolatesTheCrossingWithin20ms() {
        // Una vuelta de 58,4 s: ~12 m/s; el cruce cae en cualquier punto entre dos fixes de 1 s.
        listOf(0.0, 0.13, 0.5, 0.87).forEach { phase ->
            val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0, leadInM = 60.0 + phase * 12.0)
            val events = run(sim)
            assertEquals(3, events.size) // arranque + 2 vueltas
            assertNull(events[0].lapTimeMs) // el primer cruce solo arranca el cronómetro
            assertEquals(1, events[1].lapNumber)
            assertEquals("fase $phase V1", 58_400.0, events[1].lapTimeMs!!.toDouble(), 20.0)
            assertEquals("fase $phase V2", 58_400.0, events[2].lapTimeMs!!.toDouble(), 20.0)
            // Y el instante absoluto del cruce coincide con el real.
            events.forEachIndexed { i, e -> assertEquals(sim.crossingNs[i].toDouble(), e.crossing.elapsedNs.toDouble(), 20e6) }
        }
    }

    @Test
    fun straightPassAt5Hz_isWithin20ms_andActuallyBetterThanAtFixedSampling() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 57_900L), hz = 5.0)
        val events = run(sim)
        assertEquals(listOf(58_400L, 57_900L).size + 1, events.size)
        assertEquals(58_400.0, events[1].lapTimeMs!!.toDouble(), 20.0)
        assertEquals(57_900.0, events[2].lapTimeMs!!.toDouble(), 20.0)
    }

    // ---------- Óvalo de 10 vueltas con tiempos conocidos ----------

    @Test
    fun ovalOfTenLaps_recoversEveryLapTime_at1Hz_and5Hz() {
        listOf(1.0, 5.0).forEach { hz ->
            val sim = TrackSimulator.simulate(track, laps10, hz = hz)
            val events = run(sim)
            assertEquals("hz=$hz", laps10.size + 1, events.size)
            assertEquals((1..10).toList(), events.mapNotNull { it.lapNumber })
            laps10.forEachIndexed { i, expected ->
                val measured = events[i + 1].lapTimeMs!!
                // El cambio de velocidad entre vueltas cae dentro de un segmento: error de interpolación de pocos ms.
                assertEquals("hz=$hz V${i + 1}", expected.toDouble(), measured.toDouble(), 20.0)
            }
            assertTrue(events.all { it.crossing.reliable })
        }
    }

    // ---------- Guardas ----------

    @Test
    fun crossingBackwards_isIgnored() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 2.0)
        // Se invierte el orden espacial conservando un reloj creciente: el coche «cruza» hacia atrás.
        val times = sim.fixes.map { it.elapsedNs }
        val reversed = sim.fixes.reversed().mapIndexed { i, f -> f.copy(elapsedNs = times[i]) }
        val events = run(SimulatedSession(reversed, emptyList(), emptyList()))
        assertTrue("un cruce en sentido contrario no cuenta", events.isEmpty())
    }

    @Test
    fun jitterAroundTheLine_doesNotCreateExtraLaps() {
        val frame = track.frame
        val h = GeoMath.unit(track.finishLine.bearingDeg)
        fun fix(tS: Double, alongM: Double) = frame.toLatLon(h.x * alongM, h.y * alongM)
            .let { (lat, lon) -> GpsFix((tS * 1e9).toLong() + 5_000_000_000L, lat, lon, 12f, 3f) }
        val detector = GateDetector(track.finishLine)
        // Entra (cruce 1), vuelve atrás y vuelve a cruzar 2 s después (rebote): solo cuenta el primero.
        val fixes = listOf(
            fix(0.0, -12.0), fix(1.0, 0.5), fix(2.0, -1.0), fix(3.0, 11.0), fix(4.0, 23.0),
        )
        val events = fixes.mapNotNull(detector::feed)
        assertEquals(1, events.size)
        assertNull(events.single().lapTimeMs)
    }

    @Test
    fun aCrossingFasterThanHalfTheBestLap_isDiscardedAsABounce() {
        val frame = track.frame
        val h = GeoMath.unit(track.finishLine.bearingDeg)
        fun fix(tS: Double, alongM: Double) = frame.toLatLon(h.x * alongM, h.y * alongM)
            .let { (lat, lon) -> GpsFix((tS * 1e9).toLong(), lat, lon, 12f, 3f) }
        val detector = GateDetector(track.finishLine)
        val events = mutableListOf<GateEvent>()
        // Cruces a t≈0,8 s, t≈60,2 s (vuelta de 59,3 s) y uno «fantasma» a t=81 s (20 s después): menor que max(20 s, 50 % de 59,3 s ≈ 29,7 s).
        listOf(
            fix(0.0, -10.0), fix(2.0, 14.0),
            fix(58.0, -400.0), fix(60.0, -2.0), fix(62.0, 22.0),
            fix(70.0, -150.0), fix(80.0, -20.0), fix(82.0, 4.0), fix(84.0, 28.0),
        ).forEach { detector.feed(it)?.let(events::add) }
        assertEquals(listOf<Long?>(null, 59_300L), events.map { it.lapTimeMs }.map { it?.let { ms -> Math.round(ms / 100.0) * 100 } })
    }

    @Test
    fun noisyLateralPosition_doesNotChangeTheCrossingTime() {
        // El error lateral (perpendicular a la marcha) no cambia la distancia a la línea: el tiempo no se mueve.
        val sim = TrackSimulator.simulate(track, laps10, hz = 1.0, lateralNoiseM = 3.0, seed = 7)
        val events = run(sim)
        assertEquals(11, events.size)
        laps10.forEachIndexed { i, expected -> assertEquals("V${i + 1}", expected.toDouble(), events[i + 1].lapTimeMs!!.toDouble(), 20.0) }
    }

    @Test
    fun alongTrackNoise_errorIsBoundedBySigmaOverSpeed() {
        // σ = 3 m a lo largo de la marcha. Error del cruce ≲ max(|ε1|,|ε2|)/v; con 3σ (99,7 %) y v ≈ P/T:
        val sigma = 3.0
        val sim = TrackSimulator.simulate(track, laps10, hz = 5.0, alongNoiseM = sigma, seed = 11)
        val events = run(sim)
        assertEquals(11, events.size)
        val speed = track.perimeterM / 58.0 // m/s
        val boundMs = 3 * sigma / speed * 1000.0 * 2 // el error de una vuelta resta dos cruces: ×2 en el peor caso
        laps10.forEachIndexed { i, expected ->
            val err = abs(events[i + 1].lapTimeMs!! - expected)
            assertTrue("V${i + 1}: error $err ms > cota ${boundMs.toInt()} ms (3σ/v ×2)", err <= boundMs)
        }
    }

    @Test
    fun aLostFixAroundTheCrossing_stillInterpolatesAlongTheStraight_andFlagsLongGaps() {
        val clean = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0)
        val cleanEvents = run(clean)
        // Se pierde el fix inmediatamente anterior al cruce (hueco de 2 s): sigue dentro de la recta.
        val crossingIndex = clean.fixes.indexOfFirst { it.elapsedNs >= cleanEvents[1].crossing.elapsedNs }
        val lost = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0, dropFixes = setOf(crossingIndex - 1))
        val events = run(lost)
        assertEquals(3, events.size)
        assertEquals(58_400.0, events[1].lapTimeMs!!.toDouble(), 20.0)
        assertTrue("hueco de 2 s: aún fiable", events[1].crossing.reliable || events[2].crossing.reliable)

        // Dos fixes perdidos seguidos (hueco de 3 s): el cruce se detecta pero se marca como poco fiable.
        val lost2 = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0, dropFixes = setOf(crossingIndex - 2, crossingIndex - 1))
        val events2 = run(lost2)
        assertTrue(events2.any { !it.crossing.reliable })
    }

    @Test
    fun crossingWithPoorAccuracy_isDiscarded() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0, accuracyM = 20f)
        assertTrue(run(sim).isEmpty())
        val ok = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0, accuracyM = 15f)
        assertEquals(3, run(ok).size)
    }

    @Test
    fun passingOutsideTheGate_isNotACrossing() {
        // Misma trayectoria, pero la meta se marcó 40 m al lado: la puerta (±10 m) no se atraviesa.
        val shifted = track.finishLine.let { f ->
            val right = GeoMath.unit(f.bearingDeg + 90.0)
            val (lat, lon) = track.frame.toLatLon(right.x * 40.0, right.y * 40.0)
            f.copy(lat = lat, lon = lon)
        }
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 5.0)
        assertTrue(run(sim, finish = shifted).isEmpty())
        // Con una puerta de 100 m sí.
        assertEquals(3, run(sim, finish = shifted.copy(gateWidthM = 100)).size)
    }

    @Test
    fun nonMonotonicOrRepeatedFixes_areIgnored() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L, 58_400L), hz = 1.0)
        val detector = GateDetector(track.finishLine)
        val noisy = sim.fixes.flatMap { listOf(it, it) } // cada fix duplicado
        val events = noisy.mapNotNull(detector::feed)
        assertEquals(3, events.size)
    }

    // ---------- Formatos y reproductor ----------

    @Test
    fun csvRoundTripsAndGpxIsParsed() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
        val parsed = TrackFormats.parse(TrackFormats.toCsv(sim.fixes))
        assertEquals(sim.fixes.size, parsed.size)
        assertEquals(sim.fixes[10].elapsedNs, parsed[10].elapsedNs)
        assertEquals(sim.fixes[10].lat, parsed[10].lat, 1e-6)

        val gpx = """
            <?xml version="1.0"?><gpx><trk><trkseg>
            <trkpt lat="41.8626000" lon="-0.7931000"><time>2026-10-08T17:40:00Z</time></trkpt>
            <trkpt lat="41.8626000" lon="-0.7929000"><time>2026-10-08T17:40:01.5Z</time></trkpt>
            <trkpt lat="41.8626000" lon="-0.7927000"><time>2026-10-08T17:40:03Z</time></trkpt>
            </trkseg></trk></gpx>
        """.trimIndent()
        val fixes = TrackFormats.parse(gpx)
        assertEquals(3, fixes.size)
        assertEquals(1_500_000_000L, fixes[1].elapsedNs - fixes[0].elapsedNs)
        assertTrue("velocidad deducida (~16,6 m en 1,5 s)", fixes[1].speedMps in 10f..12f)
        assertEquals(5f, fixes[0].accuracyM, 0f)
    }

    @Test
    fun replaySource_emitsEveryFixInOrder_withTheOriginalClock() = kotlinx.coroutines.test.runTest {
        val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
        val collected = ReplayLocationSource(sim.fixes, speedFactor = 4.0).fixes().let { flow ->
            val list = mutableListOf<GpsFix>()
            flow.collect { list += it }
            list
        }
        assertEquals(sim.fixes, collected)
        // El reloj de los fixes no se acelera: los tiempos de vuelta medidos siguen siendo los reales.
        val events = run(SimulatedSession(collected, emptyList(), emptyList()))
        assertEquals(58_400.0, events.last().lapTimeMs!!.toDouble(), 20.0)
        // La reproducción sí va 4× más rápida (tiempo virtual).
        val spanMs = (sim.fixes.last().elapsedNs - sim.fixes.first().elapsedNs) / 1_000_000
        assertTrue(testScheduler.currentTime in (spanMs / 4 - 1_000)..(spanMs / 4 + 1_000))
    }

    @Test
    fun firstAndOnlyCrossing_startsTheClockWithoutALap() {
        val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
        val detector = GateDetector(track.finishLine)
        val events = sim.fixes.mapNotNull(detector::feed)
        assertEquals(2, events.size)
        assertNull(events[0].lapTimeMs)
        assertNull(events[0].lapNumber)
        assertNotNull(events[1].lapTimeMs)
        assertEquals(1, detector.lapsCompleted)
        assertFalse(max(0, 0) > 0)
    }

    // ---------------------------------------------------------------- Sprint 7, E1: ruido a lo largo / lateral y retardo del proveedor

    private fun crossingsOf(fixes: List<GpsFix>, config: GateConfig = GateConfig()): List<Long> {
        val detector = GateDetector(track.finishLine, config)
        return fixes.mapNotNull { detector.feed(it)?.crossing?.elapsedNs }
    }

    @Test
    fun lateralNoiseDoesNotMoveTheCrossing_butAlongTrackNoiseDoes() {
        val laps = List(10) { 58_000L }
        val clean = TrackSimulator.simulate(track, laps, hz = 1.0)
        val truth = clean.crossingNs
        val lateral = crossingsOf(TrackSimulator.simulate(track, laps, hz = 1.0, lateralNoiseM = 3.0).fixes)
        val along = crossingsOf(TrackSimulator.simulate(track, laps, hz = 1.0, alongNoiseM = 3.0).fixes)
        val lateralErr = lateral.indices.maxOf { abs(lateral[it] - truth[it]) } / 1e6
        val alongErr = along.indices.map { (along[it] - truth[it]) / 1e6 }
        val speed = track.perimeterM / 58.0
        // Lateral (σ = 3 m): el cruce no se mueve (< 30 ms). A lo largo (σ = 3 m a ~13 m/s): decenas de ms a ~0,3 s.
        assertTrue("error lateral máx $lateralErr ms", lateralErr < 30.0)
        val rms = kotlin.math.sqrt(alongErr.map { it * it }.average())
        assertTrue("error a lo largo RMS $rms ms (σ/v = ${3.0 / speed * 1000} ms)", rms in 20.0..(3.0 / speed * 1000 * 1.5))
    }

    @Test
    fun aConstantProviderDelayShiftsTheCrossingButNotTheLapTimes_andTheCompensationUndoesIt() {
        val laps = listOf(58_400L, 57_900L, 58_120L, 59_300L)
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)
        val delayMs = 300L
        // El proveedor entrega cada posición con 300 ms de retraso: el mismo punto llega con un sello 300 ms posterior.
        val delayed = sim.fixes.map { it.copy(elapsedNs = it.elapsedNs + delayMs * 1_000_000L) }
        val truth = sim.crossingNs
        val late = crossingsOf(delayed)
        late.indices.forEach { assertEquals("el cruce llega ${delayMs} ms tarde", delayMs.toDouble(), (late[it] - truth[it]) / 1e6, 30.0) }
        val lapTimes = late.zipWithNext { a, b -> (b - a) / 1_000_000L }
        lapTimes.forEachIndexed { i, ms -> assertEquals("V${i + 1} no cambia con un retardo constante", laps[i].toDouble(), ms.toDouble(), 20.0) }

        val compensated = crossingsOf(delayed, GateConfig(delayCompensationMs = delayMs))
        compensated.indices.forEach { assertEquals("con la compensación vuelve a su sitio", 0.0, (compensated[it] - truth[it]) / 1e6, 30.0) }
        val negative = crossingsOf(sim.fixes, GateConfig(delayCompensationMs = -200))
        assertEquals("compensación negativa retrasa el cruce", 200.0, (negative[0] - truth[0]) / 1e6, 30.0)
    }

    @Test
    fun aDelayThatVariesBetweenPasses_doesChangeTheLapTime() {
        val laps = listOf(58_000L, 58_000L)
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)
        // El 3.er cruce llega con 100 ms más de retardo que los otros dos (el filtro del proveedor reaccionó distinto).
        val fixes = sim.fixes.map { if (it.elapsedNs >= sim.crossingNs[2] - 1_000_000_000L) it.copy(elapsedNs = it.elapsedNs + 100_000_000L) else it }
        val crossings = crossingsOf(fixes)
        val second = (crossings[2] - crossings[1]) / 1_000_000L
        assertEquals("100 ms de retardo variable = 100 ms de error en la vuelta", 58_100.0, second.toDouble(), 30.0)
    }
}
