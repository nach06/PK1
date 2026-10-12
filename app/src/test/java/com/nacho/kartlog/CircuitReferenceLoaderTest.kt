package com.nacho.kartlog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.gps.CircuitReferenceLoader
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.model.LapSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** C1: la referencia del circuito sale de la mejor vuelta GPS con trazado guardado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CircuitReferenceLoaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    @After
    fun tearDown() {
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "tracks").deleteRecursively()
    }

    @Test
    fun bestLapWithATrackBecomesTheReference_andManualOrTracklessCircuitsHaveNone() {
        val container = AppContainer(context)
        val oval = OvalTrack()
        runBlocking {
            val sessionId = container.debugTools!!.seedGpsSession()
            val summary = container.sessionRepository.observeSummaries().first().single { it.session.id == sessionId }
            val loader = CircuitReferenceLoader(container.sessionRepository, container.lapRepository, container.trackStore)
            val profile = loader.bestFor(summary.circuit.id, oval.finishLine)
            assertNotNull("hay una vuelta GPS con trazado", profile)
            val best = container.lapRepository.observeBySession(sessionId).first().filter { it.source == LapSource.GPS }.minOf { it.timeMs }
            assertEquals(best.toDouble(), profile!!.lapTimeMs.toDouble(), 60.0)

            // Otro circuito sin tandas GPS: sin referencia.
            val other = container.circuitRepository.add("Otro", 900)
            assertNull(loader.bestFor(other, oval.finishLine))
        }
    }
}
