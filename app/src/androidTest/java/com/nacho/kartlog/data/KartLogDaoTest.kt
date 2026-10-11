package com.nacho.kartlog.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.local.entity.CircuitEntity
import com.nacho.kartlog.data.local.entity.SessionEntity
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KartLogDaoTest {
    private lateinit var db: KartLogDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KartLogDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun newSession(): Long {
        val circuitId = db.circuitDao().insert(CircuitEntity(name = "Circuito ${System.nanoTime()}"))
        return db.sessionDao().insert(
            SessionEntity(
                circuitId = circuitId,
                dateTime = 1_000,
                type = SessionType.FREE,
                trackCondition = TrackCondition.DRY,
            ),
        )
    }

    @Test
    fun duplicateCircuitNameIsIgnored() = runBlocking {
        val dao = db.circuitDao()
        assertTrue(dao.insert(CircuitEntity(name = "Zuera")) > 0)
        assertEquals(-1L, dao.insert(CircuitEntity(name = "Zuera")))
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun lapsGetConsecutiveNumbersAndSummaryComputesStats() = runBlocking {
        val sessionId = newSession()
        val laps = db.lapDao()
        laps.insertNext(sessionId, 50_000, LapSource.MANUAL)
        laps.insertNext(sessionId, 48_532, LapSource.MANUAL)
        laps.insertNext(sessionId, 49_000, LapSource.MANUAL)

        assertEquals(listOf(1, 2, 3), laps.observeBySession(sessionId).first().map { it.number })
        assertEquals(4, laps.nextNumber(sessionId))

        val summary = db.sessionDao().observeSummaries().first().single()
        assertEquals(3, summary.lapCount)
        assertEquals(48_532L, summary.bestLapMs)
    }

    @Test
    fun deletingSessionCascadesToLaps() = runBlocking {
        val sessionId = newSession()
        db.lapDao().insertNext(sessionId, 50_000, LapSource.MANUAL)
        db.sessionDao().deleteById(sessionId)
        assertTrue(db.lapDao().observeBySession(sessionId).first().isEmpty())
        assertEquals(null, db.sessionDao().observeById(sessionId).first())
    }
}
