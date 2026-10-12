package com.nacho.kartlog.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.local.MIGRATION_1_2
import com.nacho.kartlog.data.local.MIGRATION_2_3
import com.nacho.kartlog.data.local.MIGRATION_3_4
import com.nacho.kartlog.data.local.MIGRATION_4_5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migraciones en un dispositivo/emulador real (los esquemas salen de `app/schemas`, ver build.gradle.kts). Para ejecutarlas:
 * `./gradlew :app:connectedDebugAndroidTest --tests "com.nacho.kartlog.data.MigrationInstrumentedTest"` con un emulador o móvil
 * conectado (en JVM, sin dispositivo, las equivalentes son `MigrationTest` y `MigrationSampleDbTest` de `src/test`).
 */
@RunWith(AndroidJUnit4::class)
class MigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), KartLogDatabase::class.java)

    @Test
    fun migrate1To2_keepsLapsAndCreatesImportTables() {
        helper.createDatabase("migration-instrumented", 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL(
                "INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'FREE', 'DRY', '')",
            )
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'MANUAL')")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-instrumented", 2, true, MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM laps").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        db.execSQL("INSERT INTO participants (sessionId, isMe) VALUES (10, 1)")
        db.query("SELECT COUNT(*) FROM participants").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun migrate2To3_addsFinishLine() {
        helper.createDatabase("migration-instrumented-3", 2).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-instrumented-3", 3, true, MIGRATION_2_3)
        db.query("SELECT gateWidthM, finishLat FROM circuits WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(20, it.getInt(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun migrate3To4_addsStints() {
        helper.createDatabase("migration-instrumented-4", 3).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'RACE', 'DRY', '')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'GPS')")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-instrumented-4", 4, true, MIGRATION_3_4)
        db.query("SELECT COUNT(*), SUM(stintId IS NULL) FROM laps").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)); assertEquals(1, it.getInt(1)) }
        db.execSQL("INSERT INTO stints (sessionId, startLap, endLap) VALUES (10, 1, 1)")
    }

    @Test
    fun migrate4To5_addsSignalScore() {
        helper.createDatabase("migration-instrumented-5", 4).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'RACE', 'DRY', '')")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-instrumented-5", 5, true, MIGRATION_4_5)
        db.query("SELECT signalScore FROM sessions WHERE id = 10").use { assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)) }
    }

    /** La cadena completa 1 → 5 con el constructor de producción (`KartLogDatabase.create`), que es el que usa la app al actualizarse. */
    @Test
    fun productionBuilder_migratesAVersion1DatabaseToTheCurrentOne_keepingLaps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(KartLogDatabase.NAME)
        helper.createDatabase(KartLogDatabase.NAME, 1).apply {
            execSQL("INSERT INTO circuits (id, name, lengthMeters) VALUES (1, 'Zuera', 1100)")
            execSQL("INSERT INTO sessions (id, circuitId, dateTime, type, trackCondition, notes) VALUES (10, 1, 1760000000000, 'FREE', 'DRY', '')")
            execSQL("INSERT INTO laps (sessionId, number, timeMs, source) VALUES (10, 1, 58400, 'MANUAL')")
            close()
        }
        val db = KartLogDatabase.create(context)
        try {
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM laps").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            assertEquals(KartLogDatabase.VERSION, db.openHelper.writableDatabase.version)
        } finally {
            db.close()
            context.deleteDatabase(KartLogDatabase.NAME)
        }
    }
}
