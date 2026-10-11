package com.nacho.kartlog.debug

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugSeederTest {
    @Test
    fun seedsRealisticDataAndWipeClearsIt() = runBlocking {
        withTimeout(30_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(KartLogDatabase.NAME)
            val db = KartLogDatabase.create(context)
            val circuits = CircuitRepositoryImpl(db.circuitDao())
            val sessions = SessionRepositoryImpl(db.sessionDao())
            val laps = LapRepositoryImpl(db.lapDao())
            val seeder = DebugSeeder(db, circuits, sessions, laps, SampleImages(context))

            assertEquals(14, seeder.seed())

            assertEquals(3, circuits.observeAll().first().size)
            val summaries = sessions.observeSummaries().first()
            assertEquals(14, summaries.size)
            assertEquals(1, summaries.count { it.lapCount == 0 }) // una tanda sin vueltas
            assertTrue(summaries.any { it.session.trackCondition != TrackCondition.DRY })
            assertTrue(summaries.all { (it.session.kartNumber ?: 0) in 3..20 })
            summaries.filter { it.lapCount > 0 }.forEach { s ->
                val times = laps.observeBySession(s.session.id).first().map { it.timeMs }
                assertTrue("tiempos fuera de rango en ${s.circuit.name}", times.all { it in 45_000..90_000 })
                // La vuelta de salida de boxes es claramente más lenta que la mejor de la tanda.
                assertTrue(times.first() > times.min() * 1.10)
            }
            assertTrue(summaries.map { it.session.dateTime }.let { it.max() - it.min() } > 60L * 24 * 3600 * 1000)

            // Un circuito con 6+ tandas y una tanda de 25 vueltas (valida las etiquetas del eje X).
            assertTrue(summaries.groupBy { it.circuit.id }.values.any { it.size >= 6 })
            assertTrue(summaries.any { it.lapCount == 25 })

            seeder.wipeAll()
            assertTrue(sessions.observeSummaries().first().isEmpty())
            assertTrue(circuits.observeAll().first().isEmpty())
            db.close()
        }
    }

    @Test
    fun simulationCreatesTheOvalWithItsFinishLine_andDrivesTwelveLaps() = runBlocking {
        withTimeout(30_000) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            context.deleteDatabase(KartLogDatabase.NAME)
            val db = KartLogDatabase.create(context)
            val circuits = CircuitRepositoryImpl(db.circuitDao())
            val seeder = DebugSeeder(db, circuits, SessionRepositoryImpl(db.sessionDao()), LapRepositoryImpl(db.lapDao()), SampleImages(context))

            val source = seeder.prepareSimulation()
            seeder.prepareSimulation() // repetirlo no duplica el circuito
            val circuit = circuits.observeAll().first().single()
            assertEquals("Óvalo simulado", circuit.name)
            val finish = circuit.finishLine
            org.junit.Assert.assertNotNull(finish)
            assertEquals(10.0, source.playbackSpeed, 0.0)

            // Cronometrado de verdad con el detector (en tiempo virtual): 12 vueltas de ~58 s, la 1.ª y la 7.ª más lentas.
            val detector = com.nacho.kartlog.domain.gps.GateDetector(finish!!)
            val laps = mutableListOf<Long>()
            kotlinx.coroutines.test.runTest { source.fixes().collect { detector.feed(it)?.lapTimeMs?.let(laps::add) } }
            assertEquals(12, laps.size)
            assertTrue("todas en torno a 58 s: $laps", laps.all { it in 56_500..62_000 })
            assertTrue(laps[0] > laps.sorted()[6] && laps[6] > laps.sorted()[6]) // las dos lentas destacan
            db.close()
        }
    }
}
