package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.gps.FileTrackStore
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.data.repository.StintRepositoryImpl
import com.nacho.kartlog.domain.gps.GpsRecording
import com.nacho.kartlog.domain.gps.GpsSessionSaver
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.StintInfo
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Guardar una resistencia crea sus stints; borrar vueltas recalcula sus rangos; borrar la tanda los elimina (B4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StintsPersistenceTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var dir: File
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var laps: LapRepositoryImpl
    private lateinit var stints: StintRepositoryImpl
    private lateinit var saver: GpsSessionSaver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        dir = File(context.cacheDir, "tracks-stints").apply { deleteRecursively() }
        val store = FileTrackStore(dir)
        sessions = SessionRepositoryImpl(db.sessionDao(), store)
        laps = LapRepositoryImpl(db.lapDao())
        stints = StintRepositoryImpl(db.stintDao())
        saver = GpsSessionSaver(sessions, laps, store, stints)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun count(sql: String) = withContext(Dispatchers.IO) { db.query(sql, null).use { it.moveToFirst(); it.getInt(0) } }

    private fun recording(circuit: Long, type: SessionType = SessionType.ENDURANCE) = GpsRecording(
        setup = GpsSessionSetup(circuit, OvalTrack().finishLine, type = type, startedAtMs = 1_760_000_000_000),
        laps = listOf(73_400L, 72_900L, 73_100L, 74_200L, 73_800L, 73_500L),
        fixes = emptyList(),
        stoppedAtMs = 1_760_000_000_000 + 40 * 60_000L,
        stints = listOf(StintInfo("Nacho", 92_300L), StintInfo("Alvaro", null)),
        lapStint = listOf(0, 0, 0, 1, 1, 1),
    )

    @Test
    fun savingAnEnduranceSession_createsOneStintPerSegment_withDriversAndPitTimes() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Zuera", null)
        val id = saver.save(recording(circuit))!!
        val rows = stints.observeBySession(id).first()
        assertEquals(2, rows.size)
        assertEquals(listOf(1 to 3, 4 to 6), rows.map { it.startLap to it.endLap })
        assertEquals(listOf("Nacho", "Alvaro"), rows.map { it.driverName })
        assertEquals(listOf(92_300L, null), rows.map { it.pitMs })
        val saved = laps.observeBySession(id).first()
        assertTrue(saved.all { it.source == LapSource.GPS })
        assertEquals(listOf(rows[0].id, rows[0].id, rows[0].id, rows[1].id, rows[1].id, rows[1].id), saved.map { it.stintId })
        assertEquals(40, sessions.observeSummaries().first().single().session.durationMin)
    }

    @Test
    fun aNonEnduranceSessionWithoutPits_createsNoStints() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Zuera", null)
        val single = recording(circuit, SessionType.FREE).let { GpsRecording(it.setup, it.laps, it.fixes, it.stoppedAtMs) }
        val id = saver.save(single)!!
        assertEquals(0, count("SELECT COUNT(*) FROM stints"))
        assertTrue(laps.observeBySession(id).first().all { it.stintId == null })
    }

    @Test
    fun deletingLaps_renumbersAndRecomputesTheStintRanges_andDeletingTheSessionRemovesThem() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Zuera", null)
        val id = saver.save(recording(circuit))!!
        // Se borra la V2 (stint 1): el stint 1 pasa a V1–V2 y el 2 a V3–V5.
        laps.delete(laps.observeBySession(id).first().first { it.number == 2 }.id)
        var rows = stints.observeBySession(id).first()
        assertEquals(listOf(1 to 2, 3 to 5), rows.map { it.startLap to it.endLap })
        assertEquals("la parada se conserva", 92_300L, rows[0].pitMs)
        // Se borran las tres del stint 2: el stint queda sin vueltas y conserva su último rango (no se inventa uno).
        repeat(3) { laps.delete(laps.observeBySession(id).first().last().id) }
        rows = stints.observeBySession(id).first()
        assertEquals(1 to 2, rows[0].startLap to rows[0].endLap)

        sessions.delete(id)
        assertEquals(0, count("SELECT COUNT(*) FROM stints"))
        assertEquals(0, count("SELECT COUNT(*) FROM laps"))
        assertNull(sessions.observeDetail(id).first())
    }
}
