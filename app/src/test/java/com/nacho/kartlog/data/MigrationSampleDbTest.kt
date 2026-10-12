package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.awaitRoomClosed
import com.nacho.kartlog.data.local.KartLogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Sprint 8, D4: bases de datos reales de las versiones 1, 2 y 3 (`src/test/resources/migration/kartlog_v*.db`, creadas con el esquema
 * exportado de cada versión y datos de ejemplo) se abren con el constructor **de producción** ([KartLogDatabase.create], con la cadena
 * de migraciones explícitas): Room valida el esquema final y los datos deben seguir ahí. Si falta una migración, o se pierde un dato,
 * este test falla. En dispositivo: `./gradlew :app:connectedDebugAndroidTest` (ver `MigrationInstrumentedTest`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationSampleDbTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    @After
    fun tearDown() {
        awaitRoomClosed()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun install(version: Int) {
        val target = context.getDatabasePath(KartLogDatabase.NAME)
        target.parentFile?.mkdirs()
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("migration/kartlog_v$version.db")) { "falta kartlog_v$version.db" }
        stream.use { input -> File(target.path).outputStream().use { input.copyTo(it) } }
    }

    private fun circuits(db: KartLogDatabase) = com.nacho.kartlog.data.repository.CircuitRepositoryImpl(db.circuitDao())

    private suspend fun openAndCheck(version: Int): KartLogDatabase {
        install(version)
        val db = KartLogDatabase.create(context)
        val sessions = db.sessionDao().observeSummaries().first()
        assertEquals("tandas tras migrar v$version", 4, sessions.size)
        assertEquals("vueltas tras migrar v$version", 20, db.lapDao().observeAll().first().size)
        val zuera = sessions.first { it.session.id == 10L }
        assertEquals("Karting Zuera", zuera.circuit.name)
        assertEquals(7, zuera.session.kartNumber)
        assertEquals("nota 10", zuera.session.notes)
        assertEquals(58_000L, zuera.bestLapMs)
        // Columnas añadidas en cada migración: ausentes (NULL) en los datos antiguos.
        assertNull(zuera.session.signalScore)
        return db
    }

    @Test
    fun aVersion1DatabaseOpensWithTheProductionMigrationChain() = runBlocking {
        val db = openAndCheck(1)
        assertTrue(db.stintDao().observeBySession(10).first().isEmpty())
        assertTrue(db.participantDao().observeAll().first().isEmpty())
        assertNull(circuits(db).observeAll().first().first { it.id == 1L }.finishLine)
    }

    @Test
    fun aVersion2DatabaseKeepsItsImportedSessionAndRivals() = runBlocking {
        val db = openAndCheck(2)
        val imported = db.sessionDao().observeSummaries().first().first { it.session.id == 11L }
        assertEquals("Sesión 46 GP3", imported.session.label)
        assertEquals(10, imported.session.durationMin)
        val rivals = db.participantDao().observeAll().first()
        assertEquals(setOf("Carlos Ruiz", "Nacho"), rivals.map { it.name }.toSet())
        assertEquals(5, db.participantDao().observeAllLaps().first().size)
        assertNull(circuits(db).observeAll().first().first { it.id == 1L }.finishLine)
    }

    @Test
    fun aVersion3DatabaseKeepsTheFinishLineAndGateWidth() = runBlocking {
        val db = openAndCheck(3)
        val finish = circuits(db).observeAll().first().first { it.id == 1L }.finishLine
        assertNotNull(finish)
        assertEquals(41.8617, finish!!.lat, 1e-9)
        assertEquals(87.5, finish.bearingDeg, 1e-9)
        assertEquals(22, finish.gateWidthM)
    }
}
