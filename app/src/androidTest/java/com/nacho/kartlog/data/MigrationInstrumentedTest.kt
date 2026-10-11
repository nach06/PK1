package com.nacho.kartlog.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.local.MIGRATION_1_2
import com.nacho.kartlog.data.local.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Migración v1 → v2 en un dispositivo/emulador (los esquemas salen de `app/schemas`, ver build.gradle.kts). */
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
}
