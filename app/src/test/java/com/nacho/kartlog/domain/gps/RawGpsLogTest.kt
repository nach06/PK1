package com.nacho.kartlog.domain.gps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 8, B1: registro bruto del GPS (rumbo, proveedor, marca de cruce) con CSV versionado y compatible con los ficheros viejos. */
@OptIn(ExperimentalCoroutinesApi::class)
class RawGpsLogTest {
    private val oldCsv = """
        elapsedNs;lat;lon;speedMps;accuracyM
        1000000000;40.4168000;-3.7038000;12.50;4.0
        2000000000;40.4169000;-3.7037000;12.60;3.5
        3000000000;40.41
    """.trimIndent()

    @Test
    fun oldFilesWithoutTheNewColumnsStillLoad_andATruncatedLastLineIsDropped() {
        val fixes = TrackFormats.parseCsv(oldCsv)
        assertEquals(2, fixes.size)
        assertNull(fixes[0].bearingDeg); assertNull(fixes[0].provider); assertNull(fixes[0].crossingNs)
        assertEquals(12.6f, fixes[1].speedMps, 0.001f)
    }

    @Test
    fun v2RoundTripKeepsBearingProviderAndCrossingMark() {
        val fixes = listOf(
            GpsFix(1_000_000_000L, 40.4168, -3.7038, 12.5f, 4f, bearingDeg = 87.5f, provider = "fused"),
            GpsFix(2_000_000_000L, 40.4169, -3.7037, 12.6f, 3.5f, bearingDeg = null, provider = null, crossingNs = 1_500_000_000L),
        )
        val csv = TrackFormats.toCsv(fixes)
        assertTrue(csv.startsWith(TrackFormats.CSV_VERSION_LINE))
        val back = TrackFormats.parseCsv(csv)
        assertEquals(87.5f, back[0].bearingDeg!!, 0.01f)
        assertEquals("fused", back[0].provider)
        assertNull(back[0].crossingNs)
        assertEquals(1_500_000_000L, back[1].crossingNs)
        assertNull(back[1].bearingDeg)
    }

    @Test
    fun theRecordingMarksEveryDetectedCrossingOnTheFixThatFollowsIt() = runTest {
        val oval = OvalTrack()
        val sim = TrackSimulator.simulate(oval, listOf(58_000L, 58_500L), hz = 1.0)
        val session = TimingSession(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        session.start(GpsSessionSetup(1, oval.finishLine), ReplayLocationSource(sim.fixes, speedFactor = 0.0))
        session.stop()
        val marks = session.recording()!!.fixes.mapNotNull { it.crossingNs }
        assertEquals(sim.crossingNs.size, marks.size)
        marks.forEachIndexed { i, ns -> assertEquals(sim.crossingNs[i].toDouble(), ns.toDouble(), 20_000_000.0) }
    }
}
