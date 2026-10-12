package com.nacho.kartlog.domain.gps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 7, Parte C: delta en vuelta contra la vuelta de referencia (óvalo simulado, GPS a 1 Hz). */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveDeltaTest {
    private val oval = OvalTrack()
    private val setup = GpsSessionSetup(circuitId = 1, finish = oval.finishLine, kartNumber = 7)

    private class Harness(setup: GpsSessionSetup, scope: CoroutineScope) {
        var arrival = 0L
        val flow = MutableSharedFlow<GpsFix>(extraBufferCapacity = 10_000)
        val source = object : LocationSource {
            override fun fixes() = flow
        }
        val session = TimingSession(scope, clockNs = { arrival })
        val tracker = LiveDeltaTracker()

        init { session.start(setup, source) }

        /** Entrega un fix: el reloj local pasa a ser su instante (sin retraso). */
        suspend fun feed(fix: GpsFix) {
            arrival = fix.elapsedNs
            flow.emit(fix)
        }

        /** Delta pedido [offsetMs] después del último fix (como hace el Dashboard entre posiciones). */
        fun delta(offsetMs: Long): Long? = tracker.update(session.state.value, arrival + offsetMs * 1_000_000L)
    }

    @Test
    fun noReference_noDelta_untilTheFirstLapCloses() = runTest {
        val h = Harness(setup, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val sim = TrackSimulator.simulate(oval, listOf(58_000L, 58_000L), hz = 1.0)
        val beforeFirstLapEnds = sim.fixes.filter { it.elapsedNs < sim.crossingNs[1] }
        beforeFirstLapEnds.forEach { h.feed(it); assertNull("sin referencia no hay delta", h.delta(0)) }
        assertNull(h.session.state.value.reference)
        // Tras cerrar la V1 ya hay referencia (la mejor de la tanda) y la V2 tiene delta.
        sim.fixes.filter { it.elapsedNs >= sim.crossingNs[1] }.take(5).forEach { h.feed(it) }
        assertNotNull(h.session.state.value.reference)
        assertEquals(ReferenceSource.SESSION, h.session.state.value.referenceSource)
        assertNotNull(h.delta(0))
    }

    @Test
    fun aSlowerLap_endsWithTheTimeDifference_within50ms() = runTest {
        val h = Harness(setup, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val lapTimes = listOf(58_200L, 57_000L, 58_000L) // V3 es 1,000 s más lenta que la mejor (V2)
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0)
        var atCrossing: Long? = null
        var half: Long? = null
        for (fix in sim.fixes) {
            // Antes de entregar el fix que cierra la V3, se mide el delta en el instante exacto del cruce (extrapolando).
            if (fix.elapsedNs >= sim.crossingNs[3] && atCrossing == null && h.session.state.value.laps.size == 2) {
                val offsetMs = (sim.crossingNs[3] - h.arrival) / 1_000_000L
                atCrossing = h.delta(offsetMs)
            }
            h.feed(fix)
            val s = h.session.state.value
            if (s.laps.size == 2 && half == null && s.currentLapStartNs != null && (fix.elapsedNs - s.currentLapStartNs!!) >= 29_000_000_000L) half = h.delta(0)
        }
        assertEquals("diferencia de tiempos al cruzar la meta", 1_000.0, atCrossing!!.toDouble(), 50.0)
        // A mitad de vuelta va ya por ~0,5 s (a 1 Hz el error aceptable es de décimas).
        assertEquals(500.0, half!!.toDouble(), 150.0)
        assertEquals(57_000.0, h.session.state.value.reference!!.lapTimeMs.toDouble(), 80.0)
    }

    @Test
    fun theDeltaIsRefreshedBetweenFixes_withoutJumps() = runTest {
        val h = Harness(setup, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val sim = TrackSimulator.simulate(oval, listOf(58_000L, 57_000L, 58_000L), hz = 1.0)
        val fixes = sim.fixes.filter { it.elapsedNs <= sim.crossingNs[2] + 40_000_000_000L }
        val samples = ArrayList<Long>()
        for (fix in fixes) {
            h.feed(fix)
            if (h.session.state.value.laps.size >= 2 && h.session.state.value.currentLapStartNs != null) {
                for (offset in listOf(0L, 250L, 500L, 750L)) h.delta(offset)?.let { samples += it }
            }
        }
        assertTrue("al menos 4 lecturas por segundo: ${samples.size}", samples.size >= 4 * 20)
        val maxJump = samples.zipWithNext { a, b -> kotlin.math.abs(b - a) }.max()
        assertTrue("sin saltos bruscos: $maxJump ms", maxJump < 150)
    }

    @Test
    fun aLapWithAFixGap_isNotUsedAsReference_andTheLiveDeltaSurvivesGapsInTheCurrentLap() = runTest {
        val h = Harness(setup, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        // V1 (más rápida) pierde 3 fixes seguidos a mitad de vuelta: hueco de 4 s → no vale de referencia.
        val sim = TrackSimulator.simulate(oval, listOf(56_000L, 58_000L, 58_500L), hz = 1.0, dropFixes = setOf(30, 31, 32))
        for (fix in sim.fixes) {
            h.feed(fix)
            h.delta(300)
        }
        val ref = h.session.state.value.reference
        assertNotNull(ref)
        assertEquals("la referencia es la V2 (la V1 tiene un hueco)", 58_000.0, ref!!.lapTimeMs.toDouble(), 80.0)
    }

    @Test
    fun aCircuitReference_givesADeltaFromTheFirstLap_andTheSessionLapReplacesIt() = runTest {
        val h = Harness(setup, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val old = TrackSimulator.simulate(oval, listOf(57_000L, 57_000L), hz = 1.0)
        val geometry = TrackAnalysis.build(old.fixes, oval.finishLine)
        val profile = ReferenceProfile.from(geometry.laps.minBy { it.timeMs })!!
        h.session.offerReference(profile)
        assertEquals(ReferenceSource.CIRCUIT, h.session.state.value.referenceSource)

        val sim = TrackSimulator.simulate(oval, listOf(59_000L, 58_000L), hz = 1.0)
        var firstLapDelta: Long? = null
        for (fix in sim.fixes) {
            h.feed(fix)
            val s = h.session.state.value
            if (s.started && s.laps.isEmpty() && firstLapDelta == null && fix.elapsedNs - s.currentLapStartNs!! > 30_000_000_000L) firstLapDelta = h.delta(0)
        }
        // En la V1 (59,0 s) ya se compara con el circuito (57,0 s): a mitad de vuelta va ~+1,0 s.
        assertEquals(1_000.0, firstLapDelta!!.toDouble(), 250.0)
        // Con vueltas propias, la referencia pasa a ser la mejor de la tanda aunque el circuito tenga una más rápida.
        assertEquals(ReferenceSource.SESSION, h.session.state.value.referenceSource)
        assertEquals(58_000.0, h.session.state.value.reference!!.lapTimeMs.toDouble(), 80.0)
    }

    @Test
    fun smoothingLagsLessThan300ms_afterAStep() {
        // Recta de 500 m a 20 m/s: t(d) = d / 20 s.
        val pts = (0..100).map { RefPoint(it * 5.0, 0.0, it * 250.0) }
        val ref = ReferenceProfile.from(pts)!!
        fun timing(currentMsExtra: Long): TimingState = TimingState(
            phase = TimingPhase.RUNNING, reference = ref, referenceSource = ReferenceSource.SESSION,
            currentLapStartNs = 0L, lastFixElapsedNs = 5_000_000_000L + currentMsExtra * 1_000_000L, lastFixArrivalNs = 5_000_000_000L + currentMsExtra * 1_000_000L,
            lapPosition = LapPosition(100.0, 0.0, 20.0, 0.0),
        )
        val tracker = LiveDeltaTracker()
        // Posición 100 m ↔ t_ref = 5,0 s. Al principio el kart va justo en tiempo (delta 0)...
        var now = 5_000_000_000L
        assertEquals(0L, tracker.update(timing(0), now))
        // ...y de pronto lleva 1 s de retraso: tras 300 ms de lecturas a 20 Hz el delta ya muestra ≥ 90 % del escalón.
        var last = 0L
        repeat(6) {
            now += 50_000_000L
            last = tracker.update(timing(1_000).copy(lastFixArrivalNs = now, lastFixElapsedNs = now + 1_000_000_000L), now)!!
        }
        assertTrue("delta suavizado tras 300 ms: $last", last >= 900)
    }

    /**
     * C2: error esperado del delta intermedio con GPS a 1 Hz y ruido realista (1,5 m lateral, 0,8 m a lo largo). La verdad es
     * `p · (T_vuelta − T_ref)` con p = progreso de la vuelta (velocidad constante en el simulador).
     */
    @Test
    fun expectedErrorWithNoisyOneHertzGps_isAFewTenthsOfASecondInTheMiddleOfTheLap() = runTest {
        val h = Harness(setup, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val lapTimes = listOf(58_000L, 57_000L, 58_000L)
        val sim = TrackSimulator.simulate(oval, lapTimes, hz = 1.0, lateralNoiseM = 1.5, alongNoiseM = 0.8)
        val errors = ArrayList<Double>()
        for (fix in sim.fixes) {
            h.feed(fix)
            val s = h.session.state.value
            val start = s.currentLapStartNs
            if (s.laps.size == 2 && start != null && s.reference != null) {
                val progress = (fix.elapsedNs - start) / 1e6 / lapTimes[2]
                for (offset in listOf(0L, 500L)) {
                    val atMs = (fix.elapsedNs - start) / 1e6 + offset
                    val p = atMs / lapTimes[2]
                    if (p in 0.1..0.9) h.delta(offset)?.let { errors += it - p * (lapTimes[2] - lapTimes[1]) }
                }
                check(progress >= 0)
            }
        }
        val rms = kotlin.math.sqrt(errors.map { it * it }.average())
        println("DELTA_ERROR_RMS_MS=" + rms + " MAX_MS=" + errors.maxOf { kotlin.math.abs(it) } + " N=" + errors.size)
        assertTrue("RMS del error ${rms.toInt()} ms (esperado < 250 ms)", rms < 250.0)
        assertTrue("máximo ${errors.maxOf { kotlin.math.abs(it) }} ms (esperado < 600 ms)", errors.maxOf { kotlin.math.abs(it) } < 600.0)
    }
}
