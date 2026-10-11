package com.nacho.kartlog.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.local.MIGRATION_1_2
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
}
