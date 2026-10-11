package com.nacho.kartlog.ui.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewSessionUiState(
    val circuits: List<Circuit> = emptyList(),
    val circuitId: Long? = null,
    val type: SessionType = SessionType.FREE,
    val kartNumber: Int? = null,
    val ballastKg: Int? = null,
    val ambientTempC: Int = DEFAULT_AMBIENT_C,
    val trackTempC: Int = DEFAULT_TRACK_C,
    val condition: TrackCondition = TrackCondition.DRY,
    val notes: String = "",
    val isSaving: Boolean = false,
    val circuitDialog: CircuitDialogState? = null,
) {
    val canSave: Boolean get() = circuitId != null && !isSaving
}

data class CircuitDialogState(
    val name: String = "",
    val lengthMeters: Int? = null,
    val nameError: Boolean = false,
)

private data class Form(
    val circuitId: Long? = null,
    val type: SessionType = SessionType.FREE,
    val kartNumber: Int? = null,
    val ballastKg: Int? = null,
    val ambientTempC: Int = DEFAULT_AMBIENT_C,
    val trackTempC: Int = DEFAULT_TRACK_C,
    val condition: TrackCondition = TrackCondition.DRY,
    val notes: String = "",
    val isSaving: Boolean = false,
    val circuitDialog: CircuitDialogState? = null,
)

private const val DEFAULT_AMBIENT_C = 20
private const val DEFAULT_TRACK_C = 30
private const val MAX_KART = 99
private const val MAX_BALLAST_KG = 60
private const val LENGTH_STEP_M = 50
private const val MAX_LENGTH_M = 10_000

class NewSessionViewModel(
    private val circuits: CircuitRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<NewSessionUiState> = combine(form, circuits.observeAll()) { f, list ->
        NewSessionUiState(
            circuits = list,
            circuitId = f.circuitId,
            type = f.type,
            kartNumber = f.kartNumber,
            ballastKg = f.ballastKg,
            ambientTempC = f.ambientTempC,
            trackTempC = f.trackTempC,
            condition = f.condition,
            notes = f.notes,
            isSaving = f.isSaving,
            circuitDialog = f.circuitDialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewSessionUiState())

    private val savedChannel = Channel<Long>(Channel.BUFFERED)

    /** Emite el id de la sesión recién creada para navegar a su detalle. */
    val saved = savedChannel.receiveAsFlow()

    fun selectCircuit(id: Long) = form.update { it.copy(circuitId = id) }
    fun selectType(type: SessionType) = form.update { it.copy(type = type) }
    fun selectCondition(condition: TrackCondition) = form.update { it.copy(condition = condition) }
    fun setAmbientTemp(value: Int) = form.update { it.copy(ambientTempC = value) }
    fun setTrackTemp(value: Int) = form.update { it.copy(trackTempC = value) }
    fun setNotes(value: String) = form.update { it.copy(notes = value) }

    // Steppers: null = "sin dato". "+" desde null arranca en el mínimo; "−" en el mínimo vuelve a null.
    fun incrementKart() = form.update { it.copy(kartNumber = ((it.kartNumber ?: 0) + 1).coerceAtMost(MAX_KART)) }
    fun decrementKart() = form.update { f ->
        f.copy(kartNumber = f.kartNumber?.let { if (it <= 1) null else it - 1 })
    }

    fun incrementBallast() = form.update { f ->
        f.copy(ballastKg = f.ballastKg?.plus(1)?.coerceAtMost(MAX_BALLAST_KG) ?: 0)
    }

    fun decrementBallast() = form.update { f ->
        f.copy(ballastKg = f.ballastKg?.let { if (it <= 0) null else it - 1 })
    }

    // Alta rápida de circuito.
    fun openCircuitDialog() = form.update { it.copy(circuitDialog = CircuitDialogState()) }
    fun dismissCircuitDialog() = form.update { it.copy(circuitDialog = null) }

    fun setCircuitName(name: String) = form.update { f ->
        f.copy(circuitDialog = f.circuitDialog?.copy(name = name, nameError = false))
    }

    fun incrementCircuitLength() = form.update { f ->
        f.copy(
            circuitDialog = f.circuitDialog?.let {
                it.copy(lengthMeters = ((it.lengthMeters ?: 0) + LENGTH_STEP_M).coerceAtMost(MAX_LENGTH_M))
            },
        )
    }

    fun decrementCircuitLength() = form.update { f ->
        f.copy(
            circuitDialog = f.circuitDialog?.let {
                it.copy(lengthMeters = it.lengthMeters?.let { m -> if (m <= LENGTH_STEP_M) null else m - LENGTH_STEP_M })
            },
        )
    }

    fun confirmCircuitDialog() {
        val dialog = form.value.circuitDialog ?: return
        if (dialog.name.isBlank()) {
            form.update { it.copy(circuitDialog = dialog.copy(nameError = true)) }
            return
        }
        viewModelScope.launch {
            val id = circuits.add(dialog.name, dialog.lengthMeters)
            form.update { it.copy(circuitId = id, circuitDialog = null) }
        }
    }

    fun save() {
        val f = form.value
        val circuitId = f.circuitId ?: return
        if (f.isSaving) return
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val id = sessionRepository.create(
                    NewSession(
                        circuitId = circuitId,
                        dateTime = System.currentTimeMillis(),
                        type = f.type,
                        kartNumber = f.kartNumber,
                        ballastKg = f.ballastKg?.toFloat(),
                        ambientTempC = f.ambientTempC.toFloat(),
                        trackTempC = f.trackTempC.toFloat(),
                        trackCondition = f.condition,
                        notes = f.notes.trim(),
                    ),
                )
                savedChannel.send(id)
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }
}
