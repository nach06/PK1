package com.nacho.kartlog.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.MatchedLap
import com.nacho.kartlog.domain.gps.TrackAnalysis
import com.nacho.kartlog.domain.gps.TrackGeometry
import com.nacho.kartlog.domain.gps.TrackStore
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import com.nacho.kartlog.domain.model.Participant
import com.nacho.kartlog.domain.model.Stint
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.repository.ParticipantRepository
import com.nacho.kartlog.domain.repository.StintRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import com.nacho.kartlog.domain.util.LapTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Trazado GPS de la tanda en el Detalle. */
sealed interface TrackUiState {
    /** La tanda no tiene trazado guardado: no se muestra la tarjeta. */
    data object None : TrackUiState

    data object Loading : TrackUiState

    /**
     * [matched] = vueltas guardadas emparejadas con el trazado (selector de vuelta); `null` si no se pueden emparejar,
     * con el motivo en [note]: entonces solo se muestra el trazado completo.
     */
    data class Ready(val geometry: TrackGeometry, val matched: List<MatchedLap>?, val note: TrackNote?) : TrackUiState
}

enum class TrackNote { NO_FINISH, MISMATCH }

data class SessionDetailUiState(
    val isLoading: Boolean = true,
    /** `null` con [isLoading] falso: la sesión no existe (o se acaba de borrar). */
    val detail: SessionDetail? = null,
    /** Vueltas ordenadas por número ascendente. */
    val laps: List<Lap> = emptyList(),
    /** Clasificación importada de la sesión (vacía si la tanda no viene de una importación). */
    val participants: List<Participant> = emptyList(),
    /** Stints de una resistencia (vacío en el resto de tandas). */
    val stints: List<Stint> = emptyList(),
    val bestLapMs: Long? = null,
    /** Media limpia (vueltas <= 107 % de la mejor). */
    val cleanAverageMs: Long? = null,
    /** Desviación típica de las vueltas limpias; `null` con menos de 2 vueltas limpias. */
    val consistencyMs: Double? = null,
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
    participantRepository: ParticipantRepository? = null,
    stintRepository: StintRepository? = null,
    private val trackStore: TrackStore? = null,
) : ViewModel() {

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SessionDetailUiState> = combine(
        sessionRepository.observeDetail(sessionId),
        lapRepository.observeBySession(sessionId),
        participantRepository?.observeBySession(sessionId) ?: flowOf(emptyList()),
        stintRepository?.observeBySession(sessionId) ?: flowOf(emptyList()),
        local,
    ) { detail, laps, participants, stints, l ->
        SessionDetailUiState(
            isLoading = false,
            detail = detail,
            laps = laps,
            participants = participants,
            stints = stints,
            bestLapMs = LapTime.bestLapMs(laps),
            cleanAverageMs = Analytics.cleanAverageMs(laps.map { it.timeMs }),
            consistencyMs = laps.map { it.timeMs }.takeIf(Analytics::hasMeaningfulConsistency)?.let(Analytics::consistencyMs),
            digits = l.digits,
            editingLap = l.editingLap,
            pendingDeleteLap = l.pendingDeleteLap,
            confirmDeleteSession = l.confirmDeleteSession,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState())

    private var cachedFixes: List<GpsFix>? = null

    /** Trazado de la tanda: se carga una vez y se recalcula (en segundo plano) cuando cambian las vueltas o la meta. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val track: StateFlow<TrackUiState> = combine(
        sessionRepository.observeDetail(sessionId),
        lapRepository.observeBySession(sessionId),
    ) { detail, laps -> detail to laps }
        .mapLatest { (detail, laps) ->
            val store = trackStore
            if (store == null || detail == null) return@mapLatest TrackUiState.None
            val fixes = cachedFixes ?: store.load(sessionId).also { cachedFixes = it }
            if (fixes.size < 2) return@mapLatest TrackUiState.None
            withContext(Dispatchers.Default) {
                val finish = detail.circuit.finishLine
                val geometry = TrackAnalysis.build(fixes, finish)
                val stored = laps.filter { it.source == LapSource.GPS }.map { it.number to it.timeMs }
                val matched = if (finish == null) null else TrackAnalysis.matchStoredLaps(geometry, stored)
                val note = when {
                    finish == null -> TrackNote.NO_FINISH
                    matched == null -> TrackNote.MISMATCH
                    else -> null
                }
                TrackUiState.Ready(geometry, matched, note)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackUiState.Loading)

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
