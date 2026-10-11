package com.nacho.kartlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.importing.ImportInbox
import com.nacho.kartlog.domain.importing.SharedContent
import com.nacho.kartlog.domain.repository.ImportRepository
import com.nacho.kartlog.domain.settings.UserSettings
import com.nacho.kartlog.domain.util.SessionsCsvExporter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nacho.kartlog.debug.DebugTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsMessage { SEEDED, WIPED, FAILED, NOTHING_TO_EXPORT, SAMPLES_MISSING, RIVALS_CLEARED }

/** Fichero CSV listo para compartir. */
data class ShareFile(val fileName: String, val content: String)

data class SettingsUiState(
    val isBusy: Boolean = false,
    val message: SettingsMessage? = null,
    /** `true` solo en builds debug. */
    val debugToolsAvailable: Boolean = false,
    /** Muestras entre las que elegir para «Probar OCR» (solo si hay más de una). */
    val sampleChoices: List<String> = emptyList(),
)

class SettingsViewModel(
    private val debugTools: DebugTools?,
    private val exporter: SessionsCsvExporter,
    private val userSettings: UserSettings,
    private val importInbox: ImportInbox? = null,
    private val importRepository: ImportRepository? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(debugToolsAvailable = debugTools != null))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val shareChannel = Channel<ShareFile>(Channel.BUFFERED)

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

    /** Borra clasificaciones, vueltas de rivales y textos originales importados; tus vueltas se conservan. */
    fun clearRivalData() {
        val repository = importRepository ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            val result = runCatching { repository.clearRivalData() }
            _uiState.update {
                it.copy(isBusy = false, message = if (result.isSuccess) SettingsMessage.RIVALS_CLEARED else SettingsMessage.FAILED)
            }
        }
    }

    fun seedDebugData() = runDebug(SettingsMessage.SEEDED) { it.seed() }

    fun wipeAllData() = runDebug(SettingsMessage.WIPED) { it.wipeAll() }

    /**
     * Pasa una imagen de `app/src/debug/assets/muestras/` por el flujo real de «Escanear» (ML Kit → OcrLayout →
     * parser → vista previa): se deja en el buzón de importación y el `NavHost` abre la pantalla.
     */
    fun testOcrWithSample() {
        val tools = debugTools ?: return
        val names = tools.sampleNames()
        when {
            names.isEmpty() -> _uiState.update { it.copy(message = SettingsMessage.SAMPLES_MISSING) }
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
