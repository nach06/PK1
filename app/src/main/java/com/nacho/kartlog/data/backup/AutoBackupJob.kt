package com.nacho.kartlog.data.backup

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.nacho.kartlog.KartLogApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Copia automática semanal **en segundo plano** (E2): un trabajo periódico del sistema (`JobScheduler`, sin dependencias nuevas) que
 * solo corre con la batería no baja ni el almacenamiento justo. Hace lo mismo que la copia al abrir la app ([AutoBackup.runIfDue]):
 * si no toca todavía, está desactivada o no hay datos, no hace nada. La copia al abrir la app se mantiene como red de seguridad
 * (el trabajo no sobrevive a un reinicio del móvil hasta que se abre la app otra vez).
 *
 * Se eligió `JobScheduler` y no WorkManager porque WorkManager añade una dependencia (≈ 1 MB y varios módulos) para algo que el
 * framework ya ofrece con `setRequiresBatteryNotLow` desde Android 8 (nuestro `minSdk` es 26).
 */
class AutoBackupJobService : JobService() {
    private var scope: CoroutineScope? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as KartLogApp).container
        val work = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        work.launch {
            runCatching { container.autoBackup.runIfDue() }
            jobFinished(params, false)
        }
        return true // el trabajo sigue en segundo plano
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope?.cancel()
        return true // si el sistema lo corta, se reintenta en el siguiente periodo
    }
}

object AutoBackupScheduler {
    const val JOB_ID = 7_001

    /** Programa (o reprograma) la copia semanal. Idempotente: no duplica el trabajo si ya está programado igual. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, AutoBackupJobService::class.java))
            .setPeriodic(AutoBackup.INTERVAL_MS)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler)?.cancel(JOB_ID)
    }
}
