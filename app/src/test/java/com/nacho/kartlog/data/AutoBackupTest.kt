package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.AppContainer
import com.nacho.kartlog.data.backup.AutoBackup
import com.nacho.kartlog.data.backup.BackupInfo
import com.nacho.kartlog.data.backup.BackupService
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.settings.InMemoryUserSettings
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
import java.io.InputStream

/** Sprint 6, E4: copia de seguridad automática semanal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupTest {
    private lateinit var context: Context
    private lateinit var dir: File
    private var clock = 1_760_000_000_000L
    private var fail = false

    private val day = 24L * 3600 * 1000

    private val fakeBackup = object : BackupService {
        override suspend fun export(): File {
            check(!fail) { "sin espacio" }
            return File(context.cacheDir, "exports").apply { mkdirs() }.resolve("kartlog_backup_x.zip").also { it.writeText("zip") }
        }
        override suspend fun inspect(open: () -> InputStream?): Result<BackupInfo> = Result.failure(UnsupportedOperationException())
        override suspend fun restore(open: () -> InputStream?): Result<BackupInfo> = Result.failure(UnsupportedOperationException())
    }

    private val settings = InMemoryUserSettings()
    private var hasData = true

    private fun auto() = AutoBackup(fakeBackup, dir, settings, hasData = { hasData }, now = { clock })

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dir = File(context.cacheDir, "backups-test").apply { deleteRecursively() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun theFirstRunMakesACopyAndRemembersWhen() = runBlocking<Unit> {
        val file = auto().runIfDue()
        assertNotNull(file)
        assertTrue(file!!.name.startsWith("kartlog_auto_") && file.exists())
        assertEquals(clock, settings.lastAutoBackupMs.value)
    }

    @Test
    fun itDoesNothingBeforeAWeekHasPassed_andActsAfterwards() = runBlocking<Unit> {
        auto().runIfDue()
        clock += 6 * day
        assertNull(auto().runIfDue())
        clock += 2 * day // 8 días desde la anterior
        assertNotNull(auto().runIfDue())
    }

    @Test
    fun disabledOrWithoutData_doesNothing() = runBlocking<Unit> {
        settings.setAutoBackup(false)
        assertNull(auto().runIfDue())
        settings.setAutoBackup(true)
        hasData = false
        assertNull(auto().runIfDue())
        assertEquals(0L, settings.lastAutoBackupMs.value) // y no cuenta como hecha
    }

    @Test
    fun onlyTheLastFourCopiesAreKept() = runBlocking<Unit> {
        repeat(6) {
            clock += 8 * day
            assertNotNull(auto().runIfDue())
        }
        assertEquals(4, dir.listFiles()!!.size)
    }

    @Test
    fun aFailedCopyIsNotRememberedAsDone() = runBlocking<Unit> {
        fail = true
        assertNull(auto().runIfDue())
        assertEquals(0L, settings.lastAutoBackupMs.value)
        fail = false
        assertNotNull(auto().runIfDue())
    }

    @Test
    fun withTheRealServiceTheAutomaticCopyIsAValidBackup() = runBlocking<Unit> {
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "backups").deleteRecursively()
        val container = AppContainer(context)
        val circuit = container.circuitRepository.add("Zuera", 1_100)
        container.sessionRepository.create(NewSession(circuit, 1_760_000_000_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""))
        val file = container.autoBackup.runIfDue()
        assertNotNull(file)
        assertEquals(File(context.filesDir, "backups").absolutePath, file!!.parentFile!!.absolutePath)
        val info = container.backupService.inspect { file.inputStream() }.getOrThrow()
        assertEquals(KartLogDatabase.VERSION, info.schemaVersion)
        Thread.sleep(200)
        context.deleteDatabase(KartLogDatabase.NAME)
        File(context.filesDir, "backups").deleteRecursively()
    }
}
