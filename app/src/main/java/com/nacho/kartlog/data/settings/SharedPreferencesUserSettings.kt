package com.nacho.kartlog.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.nacho.kartlog.domain.settings.DashboardOrientation
import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes en `SharedPreferences`. Cada ajuste es un `StateFlow` que se escribe a disco al cambiar. */
class SharedPreferencesUserSettings(context: Context) : UserSettings {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _driverName = MutableStateFlow(prefs.getString(KEY_DRIVER_NAME, "").orEmpty())
    override val driverName: StateFlow<String> = _driverName.asStateFlow()

    override fun setDriverName(name: String) {
        _driverName.value = name
        prefs.edit().putString(KEY_DRIVER_NAME, name).apply()
    }

    private val _lapFeedback = MutableStateFlow(prefs.getBoolean(KEY_LAP_FEEDBACK, true))
    override val lapFeedback: StateFlow<Boolean> = _lapFeedback.asStateFlow()

    override fun setLapFeedback(enabled: Boolean) {
        _lapFeedback.value = enabled
        prefs.edit().putBoolean(KEY_LAP_FEEDBACK, enabled).apply()
    }

    private val _orientation = MutableStateFlow(
        runCatching { DashboardOrientation.valueOf(prefs.getString(KEY_ORIENTATION, null).orEmpty()) }.getOrDefault(DashboardOrientation.LANDSCAPE),
    )
    override val dashboardOrientation: StateFlow<DashboardOrientation> = _orientation.asStateFlow()

    override fun setDashboardOrientation(value: DashboardOrientation) {
        _orientation.value = value
        prefs.edit().putString(KEY_ORIENTATION, value.name).apply()
    }

    private val _dayMode = MutableStateFlow(prefs.getBoolean(KEY_DAY_MODE, false))
    override val dashboardDayMode: StateFlow<Boolean> = _dayMode.asStateFlow()

    override fun setDashboardDayMode(enabled: Boolean) {
        _dayMode.value = enabled
        prefs.edit().putBoolean(KEY_DAY_MODE, enabled).apply()
    }

    private val _vibrationOnly = MutableStateFlow(prefs.getBoolean(KEY_VIBRATION_ONLY, false))
    override val vibrationOnly: StateFlow<Boolean> = _vibrationOnly.asStateFlow()

    override fun setVibrationOnly(enabled: Boolean) {
        _vibrationOnly.value = enabled
        prefs.edit().putBoolean(KEY_VIBRATION_ONLY, enabled).apply()
    }

    private val _maxBrightness = MutableStateFlow(prefs.getBoolean(KEY_MAX_BRIGHTNESS, false))
    override val maxBrightness: StateFlow<Boolean> = _maxBrightness.asStateFlow()

    override fun setMaxBrightness(enabled: Boolean) {
        _maxBrightness.value = enabled
        prefs.edit().putBoolean(KEY_MAX_BRIGHTNESS, enabled).apply()
    }

    private val _saveImportPhoto = MutableStateFlow(prefs.getBoolean(KEY_SAVE_IMPORT_PHOTO, true))
    override val saveImportPhoto: StateFlow<Boolean> = _saveImportPhoto.asStateFlow()

    override fun setSaveImportPhoto(enabled: Boolean) {
        _saveImportPhoto.value = enabled
        prefs.edit().putBoolean(KEY_SAVE_IMPORT_PHOTO, enabled).apply()
    }

    private companion object {
        const val KEY_SAVE_IMPORT_PHOTO = "save_import_photo"
        const val FILE = "kartlog_settings"
        const val KEY_DRIVER_NAME = "driver_name"
        const val KEY_LAP_FEEDBACK = "lap_feedback"
        const val KEY_ORIENTATION = "dashboard_orientation"
        const val KEY_DAY_MODE = "dashboard_day_mode"
        const val KEY_VIBRATION_ONLY = "vibration_only"
        const val KEY_MAX_BRIGHTNESS = "max_brightness"
    }
}
