package com.nacho.kartlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import com.nacho.kartlog.domain.util.LapTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    /** `null` con [isLoading] falso: la sesión no existe (o se acaba de borrar). */
    val detail: SessionDetail? = null,
    /** Vueltas ordenadas por número ascendente. */
    val laps: List<Lap> = emptyList(),
    val bestLapMs: Long? = null,
    /** Media limpia (vueltas <= 107 % de la mejor). */
    val cleanAverageMs: Long? = null,
    val digits: String = "",
    val editingLap: Lap? = null,
    val pendingDeleteLap: Lap? = null,
    val confirmDeleteSession: Boolean = false,
) {
    val nextLapNumber: Int get() = (laps.maxOfOrNull { it.number } ?: 0) + 1
    val timeText: String get() = LapTime.formatDigits(digits)
    val timeInvalid: Boolean get() = LapTime.isInvalid(digits)
    val canSave: Boolean get() = LapTime.digitsToMs(digits) != null
}

private data class Local(
    val digits: String = "",
    val editingLap: Lap? = null,
    val pendingDeleteLap: Lap? = null,
    val confirmDeleteSession: Boolean = false,
)

class SessionDetailViewModel(
    private val sessionId: Long,
    private val sessionRepository: SessionRepository,
    private val lapRepository: LapRepository,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SessionDetailUiState> = combine(
        sessionRepository.observeDetail(sessionId),
        lapRepository.observeBySession(sessionId),
        local,
    ) { detail, laps, l ->
        SessionDetailUiState(
            isLoading = false,
            detail = detail,
            laps = laps,
            bestLapMs = LapTime.bestLapMs(laps),
            cleanAverageMs = Analytics.cleanAverageMs(laps.map { it.timeMs }),
            digits = l.digits,
            editingLap = l.editingLap,
            pendingDeleteLap = l.pendingDeleteLap,
            confirmDeleteSession = l.confirmDeleteSession,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    fun onDigits(input: String) = local.update { it.copy(digits = LapTime.appendDigits(it.digits, input)) }

    fun onBackspace() = local.update { it.copy(digits = it.digits.dropLast(1)) }

    fun onClear() = local.update { it.copy(digits = "") }

    /** Guarda una vuelta nueva o actualiza la que se está editando. Devuelve `true` si guardó. */
    fun saveLap(): Boolean {
        val current = local.value
        val timeMs = LapTime.digitsToMs(current.digits) ?: return false
        val editing = current.editingLap
        // Limpiar ya: el campo queda listo para la siguiente vuelta aunque la escritura tarde.
        local.update { it.copy(digits = "", editingLap = null) }
        viewModelScope.launch {
            if (editing != null) {
                lapRepository.updateTime(editing.id, timeMs)
            } else {
                lapRepository.addManual(sessionId, timeMs)
            }
        }
        return true
    }

    fun startEditing(lap: Lap) =
        local.update { it.copy(editingLap = lap, digits = LapTime.msToDigits(lap.timeMs)) }

    fun cancelEditing() = local.update { it.copy(editingLap = null, digits = "") }

    fun requestDeleteLap(lap: Lap) = local.update { it.copy(pendingDeleteLap = lap) }

    fun dismissDeleteLap() = local.update { it.copy(pendingDeleteLap = null) }

    fun confirmDeleteLap() {
        val lap = local.value.pendingDeleteLap ?: return
        local.update {
            val stillEditing = it.editingLap?.id == lap.id
            it.copy(
                pendingDeleteLap = null,
                editingLap = if (stillEditing) null else it.editingLap,
                digits = if (stillEditing) "" else it.digits,
            )
        }
        viewModelScope.launch { lapRepository.delete(lap.id) }
    }

    fun requestDeleteSession() = local.update { it.copy(confirmDeleteSession = true) }

    fun dismissDeleteSession() = local.update { it.copy(confirmDeleteSession = false) }

    /** Borra la sesión; la UI detecta `detail == null` y vuelve atrás. */
    fun confirmDeleteSession() {
        local.update { it.copy(confirmDeleteSession = false) }
        viewModelScope.launch { sessionRepository.delete(sessionId) }
    }
}
