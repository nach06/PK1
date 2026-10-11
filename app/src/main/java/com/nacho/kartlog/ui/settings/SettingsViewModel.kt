package com.nacho.kartlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

enum class SettingsMessage { SEEDED, WIPED, FAILED, NOTHING_TO_EXPORT }

/** Fichero CSV listo para compartir. */
data class ShareFile(val fileName: String, val content: String)

data class SettingsUiState(
    val isBusy: Boolean = false,
    val message: SettingsMessage? = null,
    /** `true` solo en builds debug. */
    val debugToolsAvailable: Boolean = false,
)

class SettingsViewModel(
    private val debugTools: DebugTools?,
    private val exporter: SessionsCsvExporter,
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

    fun seedDebugData() = runDebug(SettingsMessage.SEEDED) { it.seed() }

    fun wipeAllData() = runDebug(SettingsMessage.WIPED) { it.wipeAll() }

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
