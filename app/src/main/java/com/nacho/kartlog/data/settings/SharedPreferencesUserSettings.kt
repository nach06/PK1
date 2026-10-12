package com.nacho.kartlog.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.nacho.kartlog.domain.settings.DashboardOrientation
import com.nacho.kartlog.domain.settings.SavedListFilter
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

    private val _gpsDelayMs = MutableStateFlow(prefs.getInt(KEY_GPS_DELAY_MS, 0).coerceIn(-500, 500))
    override val gpsDelayMs: StateFlow<Int> = _gpsDelayMs.asStateFlow()

    override fun setGpsDelayMs(value: Int) {
        val clamped = value.coerceIn(-500, 500)
        _gpsDelayMs.value = clamped
        prefs.edit().putInt(KEY_GPS_DELAY_MS, clamped).apply()
    }

    private val _lastImportColumn = MutableStateFlow(prefs.getString(KEY_LAST_IMPORT_COLUMN, "").orEmpty())
    override val lastImportColumn: StateFlow<String> = _lastImportColumn.asStateFlow()

    override fun setLastImportColumn(value: String) {
        _lastImportColumn.value = value
        prefs.edit().putString(KEY_LAST_IMPORT_COLUMN, value).apply()
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

    private val _listFilter = MutableStateFlow(
        SavedListFilter(
            circuitId = prefs.getLong(KEY_FILTER_CIRCUIT, -1L).takeIf { it >= 0 },
            type = prefs.getString(KEY_FILTER_TYPE, null),
            sort = prefs.getString(KEY_FILTER_SORT, null),
        ),
    )
    override val listFilter: StateFlow<SavedListFilter> = _listFilter.asStateFlow()

    override fun setListFilter(filter: SavedListFilter) {
        _listFilter.value = filter
        prefs.edit()
            .putLong(KEY_FILTER_CIRCUIT, filter.circuitId ?: -1L)
            .apply { if (filter.type != null) putString(KEY_FILTER_TYPE, filter.type) else remove(KEY_FILTER_TYPE) }
            .apply { if (filter.sort != null) putString(KEY_FILTER_SORT, filter.sort) else remove(KEY_FILTER_SORT) }
            .apply()
    }

    private val _autoBackup = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP, true))
    override val autoBackup: StateFlow<Boolean> = _autoBackup.asStateFlow()

    override fun setAutoBackup(enabled: Boolean) {
        _autoBackup.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    private val _lastAutoBackup = MutableStateFlow(prefs.getLong(KEY_LAST_AUTO_BACKUP, 0L))
    override val lastAutoBackupMs: StateFlow<Long> = _lastAutoBackup.asStateFlow()

    override fun setLastAutoBackupMs(epochMs: Long) {
        _lastAutoBackup.value = epochMs
        prefs.edit().putLong(KEY_LAST_AUTO_BACKUP, epochMs).apply()
    }

    private companion object {
        const val KEY_AUTO_BACKUP = "auto_backup"
        const val KEY_LAST_AUTO_BACKUP = "last_auto_backup"
        const val KEY_FILTER_CIRCUIT = "list_filter_circuit"
        const val KEY_FILTER_TYPE = "list_filter_type"
        const val KEY_FILTER_SORT = "list_filter_sort"
        const val KEY_SAVE_IMPORT_PHOTO = "save_import_photo"
        const val FILE = "kartlog_settings"
        const val KEY_DRIVER_NAME = "driver_name"
        const val KEY_GPS_DELAY_MS = "gps_delay_ms"
        const val KEY_LAST_IMPORT_COLUMN = "last_import_column"
        const val KEY_LAP_FEEDBACK = "lap_feedback"
        const val KEY_ORIENTATION = "dashboard_orientation"
        const val KEY_DAY_MODE = "dashboard_day_mode"
        const val KEY_VIBRATION_ONLY = "vibration_only"
        const val KEY_MAX_BRIGHTNESS = "max_brightness"
    }
}
