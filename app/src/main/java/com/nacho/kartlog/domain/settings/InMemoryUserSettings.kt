package com.nacho.kartlog.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes en memoria (valores por defecto): para tests y vistas previas. */
class InMemoryUserSettings(driverName: String = "") : UserSettings {
    private val driver = MutableStateFlow(driverName)
    override val driverName: StateFlow<String> = driver
    override fun setDriverName(name: String) { driver.value = name }

    private val gpsDelay = MutableStateFlow(0)
    override val gpsDelayMs: StateFlow<Int> = gpsDelay
    override fun setGpsDelayMs(value: Int) { gpsDelay.value = value.coerceIn(-500, 500) }

    private val lastColumn = MutableStateFlow("")
    override val lastImportColumn: StateFlow<String> = lastColumn
    override fun setLastImportColumn(value: String) { lastColumn.value = value }

    private val feedback = MutableStateFlow(true)
    override val lapFeedback: StateFlow<Boolean> = feedback
    override fun setLapFeedback(enabled: Boolean) { feedback.value = enabled }

    private val orientation = MutableStateFlow(DashboardOrientation.LANDSCAPE)
    override val dashboardOrientation: StateFlow<DashboardOrientation> = orientation
    override fun setDashboardOrientation(value: DashboardOrientation) { orientation.value = value }

    private val day = MutableStateFlow(false)
    override val dashboardDayMode: StateFlow<Boolean> = day
    override fun setDashboardDayMode(enabled: Boolean) { day.value = enabled }

    private val vibration = MutableStateFlow(false)
    override val vibrationOnly: StateFlow<Boolean> = vibration
    override fun setVibrationOnly(enabled: Boolean) { vibration.value = enabled }

    private val brightness = MutableStateFlow(false)
    override val maxBrightness: StateFlow<Boolean> = brightness
    override fun setMaxBrightness(enabled: Boolean) { brightness.value = enabled }

    private val photo = MutableStateFlow(true)
    override val saveImportPhoto: StateFlow<Boolean> = photo
    override fun setSaveImportPhoto(enabled: Boolean) { photo.value = enabled }

    private val filter = MutableStateFlow(SavedListFilter())
    override val listFilter: StateFlow<SavedListFilter> = filter
    override fun setListFilter(filter: SavedListFilter) { this.filter.value = filter }

    private val auto = MutableStateFlow(true)
    override val autoBackup: StateFlow<Boolean> = auto
    override fun setAutoBackup(enabled: Boolean) { auto.value = enabled }

    private val lastAuto = MutableStateFlow(0L)
    override val lastAutoBackupMs: StateFlow<Long> = lastAuto
    override fun setLastAutoBackupMs(epochMs: Long) { lastAuto.value = epochMs }
}
