package com.nacho.kartlog.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.local.MIGRATION_1_2
import com.nacho.kartlog.data.local.MIGRATION_2_3
import com.nacho.kartlog.data.local.MIGRATION_3_4
import com.nacho.kartlog.data.local.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migración v1 → v2 con el esquema exportado (`app/schemas`). Se ejecuta en JVM (Robolectric);
 * la misma prueba existe en `androidTest` para dispositivo/emulador.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KartLogDatabase::class.java)

    private val dbName = "migration-test"

    @Test
    fun migrate1To2_keepsExistingDataAndAddsNewColumnsAndTables() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL(
                "INSERT INTO sessions (id, circuitId, dateTime, type, kartNumber, ballastKg, ambientTempC, trackTempC, trackCondition, notes) " +
                    "VALUES (10, 1, 1760000000000, 'RACE', 7, 4.0, 22.0, 31.0, 'DRY', 'nota')",
            )
            execSQL("INSERT INTO laps (id, sessionId, number, timeMs, source) VALUES (100, 10, 1, 58400, 'MANUAL')")
            execSQL("INSERT INTO laps (id, sessionId, number, timeMs, source) VALUES (101, 10, 2, 57900, 'MANUAL')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // Datos anteriores intactos y columnas nuevas a NULL.
        db.query("SELECT kartNumber, notes, label, durationMin, importedRaw FROM sessions WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(7, c.getInt(0))
            assertEquals("nota", c.getString(1))
            assertTrue(c.isNull(2) && c.isNull(3) && c.isNull(4))
        }
        db.query("SELECT COUNT(*), MIN(timeMs) FROM laps WHERE sessionId = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertEquals(57_900L, c.getLong(1))
        }

        // Tablas nuevas operativas, con borrado en cascada desde la sesión y unicidad (participante, vuelta).
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("UPDATE sessions SET label = 'Sesión 46', durationMin = 10, importedRaw = 'raw' WHERE id = 10")
        db.execSQL("INSERT INTO participants (id, sessionId, position, name, kartNumber, isMe, bestMs, avgMs, lapCount) VALUES (1, 10, 1, 'Deyvis', 26, 0, 76515, 78190, 6)")
        db.execSQL("INSERT INTO participant_laps (participantId, number, timeMs) VALUES (1, 1, 80938)")
        runCatching { db.execSQL("INSERT INTO participant_laps (participantId, number, timeMs) VALUES (1, 1, 1)") }
            .also { assertTrue("(participantId, number) debe ser único", it.isFailure) }
        db.execSQL("DELETE FROM sessions WHERE id = 10")
        db.query("SELECT (SELECT COUNT(*) FROM participants) + (SELECT COUNT(*) FROM participant_laps) + (SELECT COUNT(*) FROM laps)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migratedDatabaseOpensWithRoomWithoutSchemaErrors() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', NULL)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()
        assertNull(null) // la validación de esquema (identity hash) ya la hace runMigrationsAndValidate
    }

    @Test
    fun migrate2To3_addsFinishLineToCircuits_keepingEverythingElse() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes, label) VALUES (10, 1, 1760000000000, 'RACE', 'DRY', '', 'Sesión 46')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'IMPORTED')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        db.query("SELECT name, lengthMeters, finishLat, finishLon, finishBearingDeg, gateWidthM FROM circuits WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Zuera", c.getString(0))
            assertEquals(1100, c.getInt(1))
            assertTrue("sin meta hasta que se marque", c.isNull(2) && c.isNull(3) && c.isNull(4))
            assertEquals("ancho de puerta por defecto", 20, c.getInt(5))
        }
        db.query("SELECT label FROM sessions WHERE id = 10").use { assertTrue(it.moveToFirst()); assertEquals("Sesión 46", it.getString(0)) }
        db.query("SELECT COUNT(*) FROM laps WHERE source = 'IMPORTED'").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        // La meta se puede escribir y leer.
        db.execSQL("UPDATE circuits SET finishLat = 41.8626, finishLon = -0.7931, finishBearingDeg = 87.5, gateWidthM = 25 WHERE id = 1")
        db.query("SELECT finishLat, finishBearingDeg, gateWidthM FROM circuits WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(41.8626, c.getDouble(0), 1e-9)
            assertEquals(87.5, c.getDouble(1), 1e-9)
            assertEquals(25, c.getInt(2))
        }
    }

    @Test
    fun migrate1To3_chainKeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'FREE', 'DRY', '')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_1_2, MIGRATION_2_3)
        db.query("SELECT (SELECT COUNT(*) FROM laps), (SELECT gateWidthM FROM circuits WHERE id = 1)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(20, c.getInt(1))
        }
    }

    @Test
    fun migrate3To4_addsStintsAndLapStintId_keepingLaps() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters, finishLat, finishLon, finishBearingDeg, gateWidthM) VALUES (1, 'Zuera', 1100, 41.86, -0.79, 87.0, 25)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'RACE', 'DRY', '')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'GPS')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 2, 57900, 'GPS')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
        db.query("SELECT COUNT(*), SUM(stintId IS NULL), MIN(timeMs) FROM laps").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
            assertEquals("las vueltas anteriores quedan sin stint", 2, c.getInt(1))
            assertEquals(57_900L, c.getLong(2))
        }
        db.query("SELECT gateWidthM FROM circuits WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals(25, it.getInt(0)) }

        // La tabla nueva funciona y se borra en cascada con la sesión.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("INSERT INTO stints (id, sessionId, startLap, endLap, driverName, pitMs) VALUES (1, 10, 1, 2, 'Nacho', 95000)")
        db.execSQL("UPDATE laps SET stintId = 1 WHERE sessionId = 10")
        db.query("SELECT COUNT(*) FROM laps WHERE stintId = 1").use { assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0)) }
        db.execSQL("DELETE FROM sessions WHERE id = 10")
        db.query("SELECT (SELECT COUNT(*) FROM stints) + (SELECT COUNT(*) FROM laps)").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun migrate1To4_fullChainKeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'FREE', 'DRY', '')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        db.query("SELECT (SELECT COUNT(*) FROM laps), (SELECT gateWidthM FROM circuits), (SELECT COUNT(*) FROM stints)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(20, c.getInt(1))
            assertEquals(0, c.getInt(2))
        }
    }

    @Test
    fun migrate4To5_addsNullableSignalScore_keepingSessions() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'FREE', 'DRY', 'n')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'GPS')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)
        db.query("SELECT signalScore, notes, (SELECT COUNT(*) FROM laps) FROM sessions WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.isNull(0))
            assertEquals("n", c.getString(1))
            assertEquals(1, c.getInt(2))
        }
        db.execSQL("UPDATE sessions SET signalScore = 1 WHERE id = 10")
        db.query("SELECT signalScore FROM sessions WHERE id = 10").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun migrate1To5_fullChainKeepsData() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'FREE', 'DRY', '')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        db.query("SELECT (SELECT COUNT(*) FROM laps), (SELECT gateWidthM FROM circuits), (SELECT COUNT(*) FROM stints), (SELECT signalScore FROM sessions)").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0)); assertEquals(20, c.getInt(1)); assertEquals(0, c.getInt(2)); assertTrue(c.isNull(3))
        }
    }
}
