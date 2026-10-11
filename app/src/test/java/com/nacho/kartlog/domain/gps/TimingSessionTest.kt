package com.nacho.kartlog.domain.gps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimingSessionTest {
    private val track = OvalTrack()
    private val laps = listOf(58_400L, 57_900L, 58_120L, 59_300L, 57_650L)
    private val setup = GpsSessionSetup(circuitId = 1, finish = track.finishLine, kartNumber = 7)

    @Test
    fun fullSession_tracksLapsBestLastAndDelta_andKeepsTheRecordingWhenStopped() = runTest {
        var arrival = 0L
        val session = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), clockNs = { arrival += 1_000_000L; arrival })
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)

        assertTrue(session.start(setup, ReplayLocationSource(sim.fixes, speedFactor = 0.0)))
        // Todo se emite de golpe (speedFactor 0): se recogen los eventos tras el hecho desde el estado.
        val s = session.state.value
        assertEquals(TimingPhase.RUNNING, s.phase)
        assertEquals(laps.size, s.laps.size)
        laps.forEachIndexed { i, ms -> assertEquals("V${i + 1}", ms.toDouble(), s.laps[i].toDouble(), 20.0) }
        assertEquals(s.laps.last(), s.lastLapMs)
        assertEquals(s.laps.min(), s.bestLapMs)
        assertEquals(6, s.currentLapNumber)
        // Delta de la última (57,650) contra la mejor anterior (57,900): mejoró ~250 ms.
        assertEquals(-250.0, s.deltaMs!!.toDouble(), 30.0)
        assertTrue(s.started && s.rateHz != null && s.accuracyM == 4f)
        assertFalse(s.hasUnreliableLap)

        session.stop()
        assertEquals(TimingPhase.STOPPED, session.state.value.phase)
        val rec = session.recording()
        assertNotNull(rec)
        assertEquals(sim.fixes.size, rec!!.fixes.size)
        assertEquals(laps.size, rec.laps.size)

        assertFalse("no se puede empezar otra con una sin guardar", session.start(setup, ReplayLocationSource(sim.fixes, 0.0)))
        session.discard()
        assertEquals(TimingPhase.IDLE, session.state.value.phase)
        assertNull(session.recording())
    }

    @Test
    fun events_announceTheStartAndEveryLap_flaggingTheBest() = runTest {
        val session = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val events = mutableListOf<TimingEvent>()
        val job = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch { session.events.collect { events += it } }
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)
        session.start(setup, ReplayLocationSource(sim.fixes, speedFactor = 0.0))
        job.cancel()

        assertEquals(TimingEvent.Started, events.first())
        val closed = events.filterIsInstance<TimingEvent.LapClosed>()
        assertEquals((1..laps.size).toList(), closed.map { it.number })
        // Mejor vuelta acumulada: V1 lo es por ser la primera, V2 mejora, V5 mejora otra vez; V3 y V4 no.
        assertEquals(listOf(true, true, false, false, true), closed.map { it.isBest })
    }

    @Test
    fun theLapClockIsInterpolatedBetweenFixes_andScaledByPlaybackSpeed() {
        val s = TimingState(
            phase = TimingPhase.RUNNING,
            currentLapStartNs = 10_000_000_000L,
            lastFixElapsedNs = 15_000_000_000L,
            lastFixArrivalNs = 1_000_000_000L,
            playbackSpeed = 1.0,
        )
        assertEquals(5_000L, s.currentLapMs(1_000_000_000L)) // justo al llegar el fix
        assertEquals(5_400L, s.currentLapMs(1_400_000_000L)) // 0,4 s después sigue contando
        assertEquals(7_000L, s.copy(playbackSpeed = 5.0).currentLapMs(1_400_000_000L)) // simulador a 5×
        assertNull(TimingState().currentLapMs(0L)) // antes del primer cruce no hay cronómetro
    }

    @Test
    fun aGapOfFixesAroundACrossing_flagsTheTwoLapsThatShareIt() = runTest {
        val session = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val clean = TrackSimulator.simulate(track, laps, hz = 1.0)
        val idx = clean.fixes.indexOfFirst { it.elapsedNs >= clean.crossingNs[3] }
        // Se pierden los dos fixes anteriores al 4.º cruce: hueco de 3 s alrededor de él.
        val gappy = TrackSimulator.simulate(track, laps, hz = 1.0, dropFixes = setOf(idx - 2, idx - 1))
        session.start(setup, ReplayLocationSource(gappy.fixes, speedFactor = 0.0))
        val s = session.state.value
        assertEquals(laps.size, s.laps.size)
        // La V3 se cierra en ese cruce y la V4 se abre en él: ambas poco fiables; el resto, no.
        assertEquals(listOf(true, true, false, false, true), s.lapReliable)
        assertTrue(s.hasUnreliableLap)
        // Y la V3 + V4 siguen midiéndose bien (la recta es larga): el aviso es de fiabilidad, no de error.
        assertEquals(laps[2].toDouble(), s.laps[2].toDouble(), 30.0)
    }

    /** Borrador en memoria que anota el orden de las operaciones. */
    private class MemoryDraftStore : DraftStore {
        val log = java.util.Collections.synchronizedList(mutableListOf<String>())
        var setup: GpsSessionSetup? = null
        val fixes = java.util.Collections.synchronizedList(mutableListOf<GpsFix>())
        override suspend fun begin(setup: GpsSessionSetup) { log += "begin"; this.setup = setup; fixes.clear() }
        override suspend fun append(fixes: List<GpsFix>) { log += "append(${fixes.size})"; this.fixes += fixes }
        override suspend fun appendPit(request: PitRequest) { log += "pit" }
        override suspend fun load(): SessionDraft? = setup?.let { SessionDraft(it, fixes.toList(), 0L) }?.takeIf { it.fixes.isNotEmpty() }
        override suspend fun clear() { log += "clear"; setup = null; fixes.clear() }
    }

    @Test
    fun theDraftIsWrittenEvery30Seconds_andCompletedOnStop_thenClearedOnDiscard() = runBlocking {
        val store = MemoryDraftStore()
        var arrival = 0L
        // Cada fix «llega» 1 s después del anterior en el reloj local: 30 fixes = 30 s.
        val session = TimingSession(
            CoroutineScope(Dispatchers.Default), clockNs = { arrival += 1_000_000_000L; arrival }, draftStore = store,
        )
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)
        session.start(setup, ReplayLocationSource(sim.fixes, speedFactor = 0.0))
        withTimeout(10_000) { while (session.state.value.laps.size < laps.size) delay(20) }
        session.stop()
        withTimeout(10_000) { while (store.fixes.size < sim.fixes.size) delay(20) }

        assertEquals("begin", store.log.first())
        val appends = store.log.filter { it.startsWith("append") }
        assertTrue("hay varios volcados periódicos y el final: $appends", appends.size >= 3)
        assertEquals(sim.fixes.map { it.elapsedNs }, store.fixes.map { it.elapsedNs }) // completo y en orden

        session.discard()
        withTimeout(10_000) { while (store.setup != null) delay(20) }
        assertEquals("clear", store.log.last())
        assertNull(store.load())
    }

    @Test
    fun restoringADraft_recomputesTheSameLaps_andLeavesTheSessionStopped() = runTest {
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)
        val original = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        original.start(setup, ReplayLocationSource(sim.fixes, speedFactor = 0.0))

        val restored = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertTrue(restored.restore(SessionDraft(setup, sim.fixes, savedAtMs = 1_760_000_900_000)))
        assertEquals(TimingPhase.STOPPED, restored.state.value.phase)
        assertEquals(original.state.value.laps, restored.state.value.laps) // determinista: mismas vueltas, al milisegundo
        assertEquals(1_760_000_900_000, restored.state.value.stoppedAtMs)
        val rec = restored.recording()!!
        assertEquals(sim.fixes.size, rec.fixes.size)
        assertEquals(laps.size, rec.laps.size)
        assertFalse("no se puede restaurar con una tanda ya abierta", restored.restore(SessionDraft(setup, sim.fixes, 0L)))
    }

    /** Origen que emite los fixes que el test decide, cuando los decide. */
    private class ControlledSource : LocationSource {
        val flow = kotlinx.coroutines.flow.MutableSharedFlow<GpsFix>(extraBufferCapacity = 1_000)
        override fun fixes() = flow
    }

    @Test
    fun aPitStop_discardsTheInProgressLap_recordsThePitTime_andOpensANewStint() = runTest {
        val session = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val enduranceSetup = setup.copy(type = com.nacho.kartlog.domain.model.SessionType.ENDURANCE)
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0) // 5 vueltas → 6 cruces
        val source = ControlledSource()
        val events = mutableListOf<TimingEvent>()
        val collector = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch { session.events.collect { events += it } }

        assertFalse("sin tanda en marcha no hay pit", session.requestPit("X"))
        session.start(enduranceSetup, source)
        sim.fixes.filter { it.elapsedNs <= sim.crossingNs[0] - 1_000_000_000L }.forEach { source.flow.emit(it) }
        assertFalse("antes del primer cruce tampoco", session.requestPit("X"))

        // Tres vueltas cerradas (cruces 1, 2 y 3) y se pide el pit durante la 4.ª.
        sim.fixes.filter { it.elapsedNs > sim.crossingNs[0] - 1_000_000_000L && it.elapsedNs <= sim.crossingNs[3] + 5_000_000_000L }.forEach { source.flow.emit(it) }
        assertEquals(3, session.state.value.laps.size)
        assertTrue(session.requestPit("Alvaro"))
        assertFalse("no se puede pedir dos veces", session.requestPit("Otro"))
        assertTrue(session.state.value.pitPending)

        // El cruce siguiente (el 4.º) cierra el tiempo en boxes; el 5.º cierra la primera vuelta del stint nuevo.
        sim.fixes.filter { it.elapsedNs > sim.crossingNs[3] + 5_000_000_000L }.forEach { source.flow.emit(it) }
        val s = session.state.value
        assertFalse(s.pitPending)
        assertEquals("la vuelta del pit no cuenta: 3 + 1", 4, s.laps.size)
        assertEquals(listOf(0, 0, 0, 1), s.lapStint)
        assertEquals(2, s.stints.size)
        assertEquals(laps[3].toDouble(), s.stints[0].pitAfterMs!!.toDouble(), 30.0) // de un cruce al siguiente
        assertNull(s.stints[1].pitAfterMs)
        assertEquals("Alvaro", s.stints[1].driverName)
        assertEquals(2, s.currentStintNumber)
        assertEquals(laps[4].toDouble(), s.laps[3].toDouble(), 30.0) // la primera del stint 2 es la 5.ª simulada
        val pit = events.filterIsInstance<TimingEvent.PitCompleted>().single()
        assertEquals(2, pit.newStintNumber)
        assertEquals(listOf(1, 2, 3, 4), events.filterIsInstance<TimingEvent.LapClosed>().map { it.number })
        collector.cancel()

        session.stop()
        val rec = session.recording()!!
        assertEquals(listOf(0, 0, 0, 1), rec.lapStint)
        assertEquals(2, rec.stints.size)
    }

    @Test
    fun restoringADraftWithPitRequests_rebuildsTheStints() = runTest {
        val sim = TrackSimulator.simulate(track, laps, hz = 1.0)
        val enduranceSetup = setup.copy(type = com.nacho.kartlog.domain.model.SessionType.ENDURANCE)
        // La parada se pidió poco después del 3.er cruce (reloj de los fixes).
        val pit = PitRequest(atElapsedNs = sim.crossingNs[3] + 4_000_000_000L, driverName = "Alvaro")
        val restored = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        assertTrue(restored.restore(SessionDraft(enduranceSetup, sim.fixes, 0L, pits = listOf(pit))))
        val s = restored.state.value
        assertEquals(listOf(0, 0, 0, 1), s.lapStint)
        assertEquals("Alvaro", s.stints[1].driverName)
        assertEquals(laps[3].toDouble(), s.stints[0].pitAfterMs!!.toDouble(), 30.0)
    }
}
