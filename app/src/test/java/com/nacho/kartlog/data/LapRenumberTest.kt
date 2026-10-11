package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tras borrar una vuelta intermedia la numeración sigue siendo V1…Vn y los cálculos se rehacen (A2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LapRenumberTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var laps: LapRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        laps = LapRepositoryImpl(db.lapDao())
        sessions = SessionRepositoryImpl(db.sessionDao())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun newSession(): Long {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Zuera", null)
        return sessions.create(NewSession(circuit, 1_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""))
    }

    @Test
    fun deletingAMiddleLap_keepsNumbersConsecutive_andTimesInOrder() = run {
        val id = newSession()
        listOf(80_000L, 73_000L, 72_500L, 72_800L, 73_100L, 72_600L, 73_400L, 72_900L).forEach { laps.addManual(id, it) }
        val before = laps.observeBySession(id).first()
        laps.delete(before.first { it.number == 6 }.id) // la V6

        val after = laps.observeBySession(id).first()
        assertEquals((1..7).toList(), after.map { it.number })
        assertEquals(listOf(80_000L, 73_000L, 72_500L, 72_800L, 73_100L, 73_400L, 72_900L), after.map { it.timeMs })
        // La siguiente vuelta nueva continúa sin huecos.
        laps.addManual(id, 72_000L)
        assertEquals((1..8).toList(), laps.observeBySession(id).first().map { it.number })
    }

    @Test
    fun deletingFirstAndLast_andSeveralInARow_stayConsecutive() = run {
        val id = newSession()
        repeat(6) { laps.addManual(id, 70_000L + it) }
        fun idOf(n: Int) = runBlocking { laps.observeBySession(id).first().first { it.number == n }.id }
        laps.delete(idOf(1))
        laps.delete(idOf(5)) // era la V6 original (70_005)
        laps.delete(idOf(2))
        val after = laps.observeBySession(id).first()
        assertEquals((1..3).toList(), after.map { it.number })
        assertEquals(listOf(70_001L, 70_003L, 70_004L), after.map { it.timeMs })
        laps.delete(idOf(1)); laps.delete(idOf(1)); laps.delete(idOf(1))
        assertTrue(laps.observeBySession(id).first().isEmpty())
    }

    @Test
    fun renumberingKeepsTheSourceOfEachLap() = run {
        val id = newSession()
        laps.addAll(id, listOf(60_000L, 61_000L, 62_000L), LapSource.GPS)
        laps.addManual(id, 63_000L)
        laps.delete(laps.observeBySession(id).first().first { it.number == 2 }.id)
        val after = laps.observeBySession(id).first()
        assertEquals(listOf(LapSource.GPS, LapSource.GPS, LapSource.MANUAL), after.map { it.source })
        assertEquals(listOf(1, 2, 3), after.map { it.number })
    }

    @Test
    fun averagesAndRecordAreRecomputedAfterDeletingTheBestLap() = run {
        val id = newSession()
        listOf(72_000L, 70_000L, 72_500L, 72_200L).forEach { laps.addManual(id, it) }
        suspend fun snapshot() = Analytics.compute(sessions.observeSummaries().first(), laps.observeAll().first())
        val before = snapshot()
        val circuitId = before.sessions.single().summary.circuit.id
        assertEquals(70_000L, before.circuits.getValue(circuitId).recordMs)
        assertEquals(Analytics.cleanAverageMs(listOf(72_000L, 70_000L, 72_500L, 72_200L)), before.sessions.single().cleanAverageMs)

        laps.delete(laps.observeBySession(id).first().first { it.timeMs == 70_000L }.id) // se borra la mejor
        assertEquals((1..3).toList(), laps.observeBySession(id).first().map { it.number })
        val after = snapshot()
        assertEquals(72_000L, after.sessions.single().bestMs)
        assertEquals(72_000L, after.circuits.getValue(circuitId).recordMs)
        assertEquals(Analytics.cleanAverageMs(listOf(72_000L, 72_500L, 72_200L)), after.sessions.single().cleanAverageMs)
        assertEquals(listOf(72_000L, 72_500L, 72_200L), after.sessions.single().lapTimes)
    }
}
