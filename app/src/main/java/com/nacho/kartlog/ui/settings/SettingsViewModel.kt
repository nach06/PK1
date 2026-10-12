package com.nacho.kartlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.importing.ImportInbox
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.domain.repository.ImportRepository
import com.nacho.kartlog.domain.settings.DashboardOrientation
import com.nacho.kartlog.domain.settings.UserSettings
import com.nacho.kartlog.domain.util.SessionsCsvExporter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nacho.kartlog.data.backup.BackupError
import com.nacho.kartlog.data.backup.BackupException
import com.nacho.kartlog.data.backup.BackupInfo
import com.nacho.kartlog.data.backup.BackupService
import com.nacho.kartlog.debug.DebugTools
import com.nacho.kartlog.debug.TextSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsMessage { SEEDED, GPS_SEEDED, WIPED, FAILED, NOTHING_TO_EXPORT, SAMPLES_MISSING, RIVALS_CLEARED, BACKUP_FAILED, RESTORE_INVALID, RESTORE_NEWER, RESTORE_IO, NO_DEBUG_BACKUP }

/** Fichero CSV listo para compartir. */
data class ShareFile(val fileName: String, val content: String)

data class SettingsUiState(
    val isBusy: Boolean = false,
    val message: SettingsMessage? = null,
    /** `true` solo en builds debug. */
    val debugToolsAvailable: Boolean = false,
    /** Muestras entre las que elegir para «Probar OCR» (solo si hay más de una). */
    val sampleChoices: List<String> = emptyList(),
    /** Diagnóstico de la carpeta de muestras cuando falta (se enseña en un diálogo). */
    val sampleDiagnosis: com.nacho.kartlog.debug.SampleDiagnosis? = null,
    /** El simulador está listo: la pantalla debe abrir el Dashboard en pista. */
    val openDashboard: Boolean = false,
    /** Copia elegida para restaurar, ya validada, pendiente de confirmar (sustituye TODO). */
    val pendingRestore: BackupInfo? = null,
    /** Solo debug: dónde quedó la última copia guardada sin selector (ruta legible, se enseña bajo los botones). */
    val debugBackupPath: String? = null,
)

class SettingsViewModel(
    private val debugTools: DebugTools?,
    private val exporter: SessionsCsvExporter,
    private val userSettings: UserSettings,
    private val importInbox: ImportInbox? = null,
    private val importRepository: ImportRepository? = null,
    private val onSimulationReady: ((LocationSource) -> Unit)? = null,
    private val onEnduranceReady: ((com.nacho.kartlog.debug.EnduranceSimulation) -> Unit)? = null,
    private val backup: BackupService? = null,
    private val restartApp: () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(debugToolsAvailable = debugTools != null))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val shareChannel = Channel<ShareFile>(Channel.BUFFERED)

    private val backupChannel = Channel<java.io.File>(Channel.BUFFERED)

    /** Copias de seguridad (.zip) que la pantalla debe compartir. */
    val backupFiles = backupChannel.receiveAsFlow()

    private var pendingOpen: (() -> java.io.InputStream?)? = null

    /** Ficheros que la pantalla debe compartir con el selector de Android. */
    val shareFiles = shareChannel.receiveAsFlow()

    /** Exporta todas las vueltas de todas las tandas a un único CSV. */
    fun exportAll() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runCatching { exporter.export() }
            _uiState.update { it.copy(isBusy = false) }
            val csv = result.getOrNull()
            when {
                result.isFailure -> _uiState.update { it.copy(message = SettingsMessage.FAILED) }
                csv == null -> _uiState.update { it.copy(message = SettingsMessage.NOTHING_TO_EXPORT) }
                else -> {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(now()))
                    shareChannel.send(ShareFile("kartlog_todo_$stamp.csv", csv))
                }
            }
        }
    }

    /** Nombre guardado de «Mi nombre de piloto». */
    val driverName: StateFlow<String> = userSettings.driverName

    fun setDriverName(name: String) = userSettings.setDriverName(name)

    /** Vibración y pitido al cruzar la meta en el Dashboard en pista. */
    val lapFeedback: StateFlow<Boolean> = userSettings.lapFeedback

    fun setLapFeedback(enabled: Boolean) = userSettings.setLapFeedback(enabled)

    /** Ajustes del Dashboard en pista (Sprint 6, B1/B3/B5). */
    val dashboardOrientation: StateFlow<DashboardOrientation> = userSettings.dashboardOrientation
    fun setDashboardOrientation(value: DashboardOrientation) = userSettings.setDashboardOrientation(value)
    val vibrationOnly: StateFlow<Boolean> = userSettings.vibrationOnly
    fun setVibrationOnly(enabled: Boolean) = userSettings.setVibrationOnly(enabled)
    val gpsDelayMs: StateFlow<Int> = userSettings.gpsDelayMs
    fun adjustGpsDelay(deltaMs: Int) = userSettings.setGpsDelayMs(userSettings.gpsDelayMs.value + deltaMs)
    val autoBackup: StateFlow<Boolean> = userSettings.autoBackup
    fun setAutoBackup(enabled: Boolean) = userSettings.setAutoBackup(enabled)
    val lastAutoBackupMs: StateFlow<Long> = userSettings.lastAutoBackupMs
    val saveImportPhoto: StateFlow<Boolean> = userSettings.saveImportPhoto
    fun setSaveImportPhoto(enabled: Boolean) = userSettings.setSaveImportPhoto(enabled)
    val maxBrightness: StateFlow<Boolean> = userSettings.maxBrightness
    fun setMaxBrightness(enabled: Boolean) = userSettings.setMaxBrightness(enabled)

    /** Borra clasificaciones, vueltas de rivales y textos originales importados; tus vueltas se conservan. */
    fun clearRivalData() {
        val repository = importRepository ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runCatching { repository.clearRivalData() }
            if (result.isSuccess) userSettings.setDriverAliases(emptyMap()) // los alias son nombres de rivales
            _uiState.update {
                it.copy(isBusy = false, message = if (result.isSuccess) SettingsMessage.RIVALS_CLEARED else SettingsMessage.FAILED)
            }
        }
    }

    /** Crea el .zip con la BD y los trazados y lo entrega a la pantalla para compartirlo. */
    fun exportBackup() {
        val service = backup ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runCatching { service.export() }
            _uiState.update { it.copy(isBusy = false, message = if (result.isFailure) SettingsMessage.BACKUP_FAILED else it.message) }
            result.getOrNull()?.let { backupChannel.send(it) }
        }
    }

    /** Solo debug: crea la copia y la deja en Descargas / almacenamiento de la app, sin selector ni hoja de compartir. */
    fun exportBackupToDownloads() {
        val service = backup ?: return
        val tools = debugTools ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val path = runCatching { tools.saveBackupCopy(service.export()) }.getOrNull()
            _uiState.update {
                it.copy(isBusy = false, debugBackupPath = path ?: it.debugBackupPath, message = if (path == null) SettingsMessage.BACKUP_FAILED else it.message)
            }
        }
    }

    /** Solo debug: restaura la última copia guardada con [exportBackupToDownloads] (pide la confirmación habitual). */
    fun restoreLatestDebugBackup() {
        val open = debugTools?.latestBackupOpener()
        if (open == null) _uiState.update { it.copy(message = SettingsMessage.NO_DEBUG_BACKUP) } else prepareRestore(open)
    }

    /** Valida la copia elegida y, si es buena, pide confirmación antes de sustituir nada. */
    fun prepareRestore(open: () -> java.io.InputStream?) {
        val service = backup ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = service.inspect(open)
            pendingOpen = if (result.isSuccess) open else null
            _uiState.update {
                it.copy(isBusy = false, pendingRestore = result.getOrNull(), message = result.exceptionOrNull()?.let(::messageFor) ?: it.message)
            }
        }
    }

    fun cancelRestore() {
        pendingOpen = null
        _uiState.update { it.copy(pendingRestore = null) }
    }

    /** Sustituye BD y trazados por los de la copia y reinicia la app. */
    fun confirmRestore() {
        val service = backup ?: return
        val open = pendingOpen ?: return
        _uiState.update { it.copy(isBusy = true, pendingRestore = null) }
        viewModelScope.launch {
            val result = service.restore(open)
            pendingOpen = null
            _uiState.update { it.copy(isBusy = false, message = result.exceptionOrNull()?.let(::messageFor) ?: it.message) }
            if (result.isSuccess) restartApp()
        }
    }

    private fun messageFor(error: Throwable) = when ((error as? BackupException)?.error) {
        BackupError.NEWER_SCHEMA -> SettingsMessage.RESTORE_NEWER
        BackupError.IO -> SettingsMessage.RESTORE_IO
        else -> SettingsMessage.RESTORE_INVALID
    }

    fun seedDebugData() = runDebug(SettingsMessage.SEEDED) { it.seed() }

    /** Solo debug: crea una tanda GPS de ejemplo con su trazado. */
    fun seedGpsSession() = runDebug(SettingsMessage.GPS_SEEDED) { it.seedGpsSession() }

    fun wipeAllData() = runDebug(SettingsMessage.WIPED) { it.wipeAll() }

    /**
     * Pasa una imagen de `app/src/debug/assets/muestras/` por el flujo real de «Escanear» (ML Kit → OcrLayout →
     * parser → vista previa): se deja en el buzón de importación y el `NavHost` abre la pantalla.
     */
    fun testOcrWithSample() {
        val tools = debugTools ?: return
        val names = tools.sampleNames()
        when {
            names.isEmpty() -> _uiState.update { it.copy(message = SettingsMessage.SAMPLES_MISSING, sampleDiagnosis = tools.sampleDiagnosis()) }
            names.size == 1 -> runSample(names.single())
            else -> _uiState.update { it.copy(sampleChoices = names) }
        }
    }

    fun runSample(name: String) {
        val tools = debugTools ?: return
        _uiState.update { it.copy(sampleChoices = emptyList()) }
        viewModelScope.launch {
            val uri = runCatching { tools.prepareSample(name) }.getOrNull()
            if (uri == null) _uiState.update { it.copy(message = SettingsMessage.FAILED) } else importInbox?.put(SharedContent.Image(uri))
        }
    }

    /** Solo debug: deja un óvalo sintético como origen de posiciones y pide abrir el Dashboard en pista. */
    fun simulateTrackSession() {
        val tools = debugTools ?: return
        val callback = onSimulationReady ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val source = runCatching { tools.prepareSimulation() }.getOrNull()
            if (source != null) callback(source)
            _uiState.update {
                it.copy(isBusy = false, openDashboard = source != null, message = if (source == null) SettingsMessage.FAILED else it.message)
            }
        }
    }

    /** Solo debug: arranca una resistencia simulada con 2 paradas automáticas y abre el Dashboard. */
    fun simulateEndurance() {
        val tools = debugTools ?: return
        val callback = onEnduranceReady ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val sim = runCatching { tools.prepareEnduranceSimulation() }.getOrNull()
            if (sim != null) callback(sim)
            _uiState.update { it.copy(isBusy = false, openDashboard = sim != null, message = if (sim == null) SettingsMessage.FAILED else it.message) }
        }
    }

    fun dashboardOpened() = _uiState.update { it.copy(openDashboard = false) }

    /** Solo debug: carga una muestra de texto de los fixtures en el flujo real de «Importar tiempos» (sin teclear ni portapapeles). */
    fun importTextSample(sample: TextSample, asMe: String? = null, circuit: String? = null) {
        val tools = debugTools ?: return
        viewModelScope.launch {
            val text = runCatching { tools.loadTextSample(sample) }.getOrNull()
            if (text == null) {
                _uiState.update { it.copy(message = SettingsMessage.FAILED) }
            } else {
                // Atajo «como Nacho»: deja el circuito del documento creado y la columna propia ya marcada, para llegar a GUARDAR con un toque.
                if (circuit != null) runCatching { tools.ensureCircuit(circuit, 900) }
                importInbox?.put(SharedContent.Text(text, meName = asMe))
            }
        }
    }

    fun dismissSampleDiagnosis() = _uiState.update { it.copy(sampleDiagnosis = null) }

    /** Solo debug: una imagen elegida del almacenamiento (selector del sistema) entra por el flujo real de importar. */
    fun importPickedImage(uri: String) { importInbox?.put(SharedContent.Image(uri)) }

    fun dismissSampleChoices() = _uiState.update { it.copy(sampleChoices = emptyList()) }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun runDebug(success: SettingsMessage, block: suspend (DebugTools) -> Unit) {
        val tools = debugTools ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runCatching { block(tools) }
            _uiState.update { it.copy(isBusy = false, message = if (result.isSuccess) success else SettingsMessage.FAILED) }
        }
    }
}
