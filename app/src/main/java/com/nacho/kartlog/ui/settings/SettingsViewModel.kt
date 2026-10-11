package com.nacho.kartlog.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.debug.DebugTools
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SettingsMessage { SEEDED, WIPED, FAILED }

data class SettingsUiState(
    val isBusy: Boolean = false,
    val message: SettingsMessage? = null,
    /** `true` solo en builds debug. */
    val debugToolsAvailable: Boolean = false,
)

class SettingsViewModel(private val debugTools: DebugTools?) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(debugToolsAvailable = debugTools != null))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

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
