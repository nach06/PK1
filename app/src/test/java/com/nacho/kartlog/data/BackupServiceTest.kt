package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.backup.BackupError
import com.nacho.kartlog.data.backup.BackupException
import com.nacho.kartlog.data.backup.ZipBackupService
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.ImportRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.data.repository.StintRepositoryImpl
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.ParticipantImport
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Copia de seguridad: exportar → borrar → restaurar devuelve los mismos datos; las copias inválidas o más nuevas se rechazan (B5). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupServiceTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var tracksDir: File
    private lateinit var service: ZipBackupService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        tracksDir = File(context.filesDir, "tracks-backup-test").apply { deleteRecursively(); mkdirs() }
        service = newService()
    }

    private fun newService(schemaVersion: Int = KartLogDatabase.VERSION) = ZipBackupService(
        databaseFile = context.getDatabasePath(KartLogDatabase.NAME),
        tracksDir = tracksDir,
        cacheDir = context.cacheDir,
        schemaVersion = schemaVersion,
        appVersion = "test",
        checkpoint = { withContext(Dispatchers.IO) { db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() } } },
        closeDatabase = { db.close() },
        now = { 1_760_000_000_000 },
    )

    @After
    fun tearDown() {
        runCatching { db.close() }
        tracksDir.deleteRecursively()
        File(context.cacheDir, "exports").deleteRecursively()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(30_000) { block() } }

    private suspend fun count(database: KartLogDatabase, table: String): Int = withContext(Dispatchers.IO) {
        database.query("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }
    }

    private suspend fun populate() {
        val circuits = CircuitRepositoryImpl(db.circuitDao())
        val sessions = SessionRepositoryImpl(db.sessionDao())
        val laps = LapRepositoryImpl(db.lapDao())
        val circuit = circuits.add("Zuera", 1_100)
        val s1 = sessions.create(NewSession(circuit, 1_760_000_000_000, SessionType.ENDURANCE, 7, null, null, null, TrackCondition.DRY, ""))
        val stint = StintRepositoryImpl(db.stintDao()).add(s1, 1, 3, "Nacho", 90_000)
        laps.addAll(s1, listOf(58_000L, 58_500L, 57_900L), LapSource.GPS, listOf(stint, stint, stint))
        // Una importación con rivales y texto original.
        ImportRepositoryImpl(db.importDao()).importSession(
            ImportRequest(
                newSession = NewSession(circuit, 1_750_000_000_000, SessionType.RACE, 28, null, null, null, TrackCondition.DRY, "", importedRaw = "TEXTO ORIGINAL"),
                existingSessionId = null,
                myLapTimesMs = listOf(79_074L, 80_000L),
                me = ParticipantImport(5, "Nacho", 28, 79_074, 79_500, 2),
                others = listOf(ParticipantImport(1, "Rival", 7, 76_515, 78_190, 2, listOf(76_515L, 80_000L))),
            ),
        )
        File(tracksDir, "$s1.csv").writeText("elapsedNs;lat;lon;speedMps;accuracyM\n1000;41.0;-0.7;10.0;3.0\n")
        File(tracksDir, "draft.partial.csv").writeText("borrador")
    }

    private val tables = listOf("circuits", "sessions", "laps", "participants", "participant_laps", "stints")

    @Test
    fun exportWipeRestore_bringsBackExactlyTheSameData_andTracks() = run {
        populate()
        val before = tables.associateWith { count(db, it) }
        assertTrue(before.getValue("participants") > 0 && before.getValue("stints") == 1 && before.getValue("laps") == 5)

        val zip = service.export()
        assertTrue(zip.name.startsWith("kartlog_backup_") && zip.length() > 0)
        // Dentro: manifiesto, BD y el trazado (el borrador de una tanda no se incluye).
        ZipInputStream(zip.inputStream()).use { z ->
            val names = generateSequence { z.nextEntry }.map { it.name }.toList()
            assertEquals(listOf("manifest.json", "kartlog.db", "tracks/1.csv"), names)
        }

        // Se borra todo (datos y trazados)...
        withContext(Dispatchers.IO) { db.clearAllTables() }
        tracksDir.listFiles()!!.forEach { it.delete() }
        assertEquals(0, count(db, "laps"))

        // ...y se restaura: la BD se cierra, se sustituyen los ficheros y se vuelve a abrir como haría la app reiniciada.
        val info = service.restore { zip.inputStream() }.getOrThrow()
        assertEquals(KartLogDatabase.VERSION, info.schemaVersion)
        assertEquals(1, info.trackCount)
        val reopened = KartLogDatabase.create(context)
        try {
            tables.forEach { assertEquals("tabla $it", before.getValue(it), count(reopened, it)) }
            val raw = withContext(Dispatchers.IO) { reopened.query("SELECT importedRaw FROM sessions WHERE importedRaw IS NOT NULL", null).use { it.moveToFirst(); it.getString(0) } }
            assertEquals("TEXTO ORIGINAL", raw) // la copia sí lleva los datos de rivales
        } finally {
            reopened.close()
        }
        assertEquals(listOf("1.csv"), tracksDir.listFiles()!!.map { it.name }) // trazado vuelto; borrador eliminado
        assertTrue(File(tracksDir, "1.csv").readText().contains("41.0"))
    }

    @Test
    fun aNewerSchema_isRejectedWithAClearError_andNothingChanges() = run {
        populate()
        val zip = service.export()
        val newer = rewriteManifest(zip) { it.put("schemaVersion", 99) }
        val result = service.restore { ByteArrayInputStream(newer) }
        val error = result.exceptionOrNull() as BackupException
        assertEquals(BackupError.NEWER_SCHEMA, error.error)
        assertTrue(error.message!!.contains("más nueva"))
        assertEquals("la BD actual no se toca", 5, count(db, "laps"))
    }

    @Test
    fun invalidFiles_areRejected_andNothingChanges() = run {
        populate()
        val zip = service.export()
        fun assertInvalid(bytes: ByteArray, why: String) {
            val result = runBlocking { service.restore { ByteArrayInputStream(bytes) } }
            assertEquals(why, BackupError.INVALID, (result.exceptionOrNull() as BackupException).error)
        }
        assertInvalid("esto no es un zip".toByteArray(), "basura")
        assertInvalid(zipOf("manifest.json" to """{"schemaVersion":5}""".toByteArray()), "sin base de datos")
        assertInvalid(zipOf("kartlog.db" to zip.readBytes().copyOf(64)), "sin manifiesto")
        assertInvalid(zipOf("manifest.json" to """{"schemaVersion":5}""".toByteArray(), "kartlog.db" to "no es sqlite".toByteArray()), "BD dañada")
        assertInvalid(zipOf("manifest.json" to """{"schemaVersion":5}""".toByteArray(), "../evil.txt" to "x".toByteArray()), "ruta fuera de la copia")
        assertInvalid(rewriteManifest(zip) { it.put("schemaVersion", 3) }, "manifiesto que no coincide con la BD")
        assertEquals(5, count(db, "laps"))
        assertTrue(File(tracksDir, "1.csv").exists())
        assertTrue("no se escribió nada fuera", !File(context.cacheDir, "evil.txt").exists() && !File(context.cacheDir.parentFile, "evil.txt").exists())
    }

    // ---- utilidades

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun rewriteManifest(zip: File, edit: (JSONObject) -> Unit): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(zip.inputStream()).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val bytes = z.readBytes()
                entries += e.name to if (e.name == "manifest.json") JSONObject(bytes.toString(Charsets.UTF_8)).also(edit).toString().toByteArray() else bytes
                e = z.nextEntry
            }
        }
        return zipOf(*entries.toTypedArray())
    }
}
