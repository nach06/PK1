package com.nacho.kartlog.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings
import com.nacho.kartlog.ui.components.GpsPermissions
import com.nacho.kartlog.domain.DeviceSnapshot

/** Lee del sistema el estado del móvil para la lista «Antes de salir». */
class DeviceSnapshotReader(private val context: Context) {
    fun read(): DeviceSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val location = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val auto = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC }.getOrDefault(false)
        val brightness = if (auto) null else runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
        return DeviceSnapshot(
            batteryPercent = if (level < 0) 100 else level * 100 / scale.coerceAtLeast(1),
            charging = charging,
            locationEnabled = location?.isLocationEnabled ?: true,
            locationPermission = GpsPermissions.hasRequired(context),
            brightness = brightness,
            powerSaveOn = power?.isPowerSaveMode ?: false,
        )
    }
}
