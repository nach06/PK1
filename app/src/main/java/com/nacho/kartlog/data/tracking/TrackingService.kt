package com.nacho.kartlog.data.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.nacho.kartlog.KartLogApp
import com.nacho.kartlog.MainActivity
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.TimingState
import com.nacho.kartlog.domain.util.LapTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Arranca y para el servicio del cronometraje; los tests lo sustituyen por uno que no hace nada. */
interface TrackingController {
    fun start()

    fun stop()
}

class ServiceTrackingController(private val context: Context) : TrackingController {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
    }

    override fun stop() {
        context.stopService(Intent(context, TrackingService::class.java))
    }
}

/**
 * Servicio en primer plano de tipo `location`: mantiene vivo el proceso (y permite seguir leyendo el GPS) con la
 * pantalla apagada o la app en segundo plano. La lógica de cronometraje no vive aquí sino en `TimingSession`;
 * el servicio solo muestra la notificación (vuelta y mejor) y se cierra cuando la tanda deja de estar en marcha.
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observer: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.tracking_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = (application as KartLogApp).container.timingSession
        if (intent?.action == ACTION_STOP) {
            // «Parar» desde la notificación: la tanda queda parada (con sus vueltas) esperando guardarse en la app.
            session.stop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(notification(session.state.value))
        observer?.cancel()
        observer = scope.launch {
            session.state.collect { state ->
                if (state.phase != TimingPhase.RUNNING) {
                    stopForegroundCompat()
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
    }

    private fun notification(state: TimingState): Notification {
        val text = when {
            !state.started -> getString(R.string.tracking_waiting)
            else -> getString(
                R.string.tracking_status,
                state.currentLapNumber,
                state.bestLapMs?.let(LapTime::format) ?: getString(R.string.value_unset),
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(getString(R.string.tracking_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.tracking_stop), stop)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nacho.kartlog.action.STOP_TRACKING"
        private const val CHANNEL_ID = "tracking"
        private const val NOTIFICATION_ID = 42
    }
}
