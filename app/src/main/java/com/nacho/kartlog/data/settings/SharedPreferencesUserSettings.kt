package com.nacho.kartlog.data.settings

import android.content.Context
import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes en `SharedPreferences` (clave `driver_name`). */
class SharedPreferencesUserSettings(context: Context) : UserSettings {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _driverName = MutableStateFlow(prefs.getString(KEY_DRIVER_NAME, "").orEmpty())
    override val driverName: StateFlow<String> = _driverName.asStateFlow()

    override fun setDriverName(name: String) {
        _driverName.value = name
        prefs.edit().putString(KEY_DRIVER_NAME, name).apply()
    }

    private companion object {
        const val FILE = "kartlog_settings"
        const val KEY_DRIVER_NAME = "driver_name"
    }
}
