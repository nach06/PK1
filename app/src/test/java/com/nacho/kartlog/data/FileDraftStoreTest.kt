package com.nacho.kartlog.data

import com.nacho.kartlog.data.gps.FileDraftStore
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FileDraftStoreTest {
    private lateinit var dir: File
    private val setup = GpsSessionSetup(
        circuitId = 3, finish = OvalTrack().finishLine, kartNumber = 7, ballastKg = 4f, ambientTempC = 22f, trackTempC = null,
        trackCondition = TrackCondition.DAMP, type = SessionType.RACE, startedAtMs = 1_760_000_000_000,
    )
    private fun fix(i: Int) = GpsFix(1_000_000_000L * i, 41.8626 + i * 1e-5, -0.7931 + i * 1e-5, 12.5f, 3.5f)

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "draft-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun roundTrip_keepsTheSetupAndEveryAppendedFix() = runBlocking {
        val store = FileDraftStore(dir)
        assertNull(store.load())
        store.begin(setup)
        assertNull("sin posiciones no hay nada que recuperar", store.load())
        store.append((1..3).map(::fix))
        store.append((4..6).map(::fix))

        val draft = store.load()!!
        assertEquals(setup, draft.setup)
        assertEquals((1..6).map(::fix).map { it.elapsedNs }, draft.fixes.map { it.elapsedNs })
        assertEquals(fix(6).lat, draft.fixes.last().lat, 1e-6)
    }

    @Test
    fun aLineCutInHalfByTheCrash_isIgnored() = runBlocking {
        val store = FileDraftStore(dir)
        store.begin(setup)
        store.append((1..4).map(::fix))
        File(dir, "draft.partial.csv").appendText("5000000000;41.86") // muerte a mitad de escritura
        val draft = store.load()!!
        assertEquals(4, draft.fixes.size)
    }

    @Test
    fun clearRemovesBothFiles_andCorruptDraftsLoadAsNull() = runBlocking {
        val store = FileDraftStore(dir)
        store.begin(setup)
        store.append(listOf(fix(1)))
        assertNotNull(store.load())
        store.clear()
        assertFalse(File(dir, "draft.partial.json").exists() || File(dir, "draft.partial.csv").exists())
        assertNull(store.load())

        store.begin(setup)
        store.append(listOf(fix(1)))
        File(dir, "draft.partial.json").writeText("{esto no es json")
        assertNull(store.load())
    }
}
