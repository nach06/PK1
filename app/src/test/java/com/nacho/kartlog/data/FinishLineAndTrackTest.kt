package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.gps.FileTrackStore
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FinishLineAndTrackTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var dir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        dir = File(context.cacheDir, "tracks-test").apply { deleteRecursively() }
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun finishLine_isSaved_survivesRenames_andCanBeCleared() = run {
        val circuits = CircuitRepositoryImpl(db.circuitDao())
        val id = circuits.add("Zuera", 1100)
        assertNull(circuits.getById(id)!!.finishLine)

        circuits.setFinishLine(id, FinishLine(41.8626, -0.7931, 87.5, 25))
        assertEquals(FinishLine(41.8626, -0.7931, 87.5, 25), circuits.getById(id)!!.finishLine)

        // Editar nombre y longitud no borra la meta.
        assertTrue(circuits.update(id, "Karting Zuera", 1200))
        val edited = circuits.getById(id)!!
        assertEquals("Karting Zuera", edited.name)
        assertEquals(FinishLine(41.8626, -0.7931, 87.5, 25), edited.finishLine)

        circuits.setFinishLine(id, null)
        assertNull(circuits.getById(id)!!.finishLine)
        assertEquals(FinishLine.DEFAULT_GATE_WIDTH_M, 20)
    }

    @Test
    fun trackStore_roundTrips_andDeletingTheSessionDeletesItsFile() = run {
        val store = FileTrackStore(dir)
        val sessions = SessionRepositoryImpl(db.sessionDao(), store)
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Zuera", null)
        val id = sessions.create(NewSession(circuit, 1_000, SessionType.FREE, null, null, null, null, TrackCondition.DRY, ""))

        val fixes = listOf(
            GpsFix(1_000_000_000L, 41.8626001, -0.7931002, 12.5f, 3.2f),
            GpsFix(2_000_000_000L, 41.8626501, -0.7930502, 13.0f, 2.9f),
        )
        store.save(id, fixes)
        assertTrue(File(dir, "$id.csv").exists())
        assertEquals(
            "elapsedNs;lat;lon;speedMps;accuracyM",
            File(dir, "$id.csv").readLines().first(),
        )
        val loaded = store.load(id)
        assertEquals(2, loaded.size)
        assertEquals(fixes[0].elapsedNs, loaded[0].elapsedNs)
        assertEquals(fixes[1].lat, loaded[1].lat, 1e-7)
        assertEquals(fixes[1].speedMps, loaded[1].speedMps, 0.01f)
        assertNotNull(sessions.observeDetail(id).first())

        sessions.delete(id)
        assertFalse("el trazado se borra con la tanda", File(dir, "$id.csv").exists())
        assertTrue(store.load(id).isEmpty())
    }
}
