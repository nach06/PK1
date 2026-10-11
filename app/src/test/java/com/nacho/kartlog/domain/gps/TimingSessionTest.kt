package com.nacho.kartlog.domain.gps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
}
