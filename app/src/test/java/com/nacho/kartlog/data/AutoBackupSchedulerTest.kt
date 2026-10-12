package com.nacho.kartlog.data

import android.app.job.JobScheduler
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.backup.AutoBackup
import com.nacho.kartlog.data.backup.AutoBackupJobService
import com.nacho.kartlog.data.backup.AutoBackupScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sprint 7, E2: la copia semanal se programa en segundo plano, solo con batería y almacenamiento no bajos. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scheduler get() = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    @Test
    fun schedulesOneWeeklyJobWithBatteryAndStorageConstraints_andDoesNotDuplicateIt() {
        AutoBackupScheduler.schedule(context)
        AutoBackupScheduler.schedule(context)
        val jobs = scheduler.allPendingJobs.filter { it.id == AutoBackupScheduler.JOB_ID }
        assertEquals("un solo trabajo", 1, jobs.size)
        val job = jobs.single()
        assertTrue("periódico", job.isPeriodic)
        assertEquals(AutoBackup.INTERVAL_MS, job.intervalMillis)
        assertTrue("batería no baja", job.isRequireBatteryNotLow)
        assertTrue("almacenamiento no justo", job.isRequireStorageNotLow)
        assertEquals(AutoBackupJobService::class.java.name, job.service.className)
    }

    @Test
    fun cancelRemovesTheJob() {
        AutoBackupScheduler.schedule(context)
        AutoBackupScheduler.cancel(context)
        assertTrue(scheduler.allPendingJobs.none { it.id == AutoBackupScheduler.JOB_ID })
    }
}
