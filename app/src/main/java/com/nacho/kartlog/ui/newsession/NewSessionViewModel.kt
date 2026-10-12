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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class NewSessionUiState(
    /** Circuitos con los usados más recientemente primero; el resto, por nombre. */
    val circuits: List<Circuit> = emptyList(),
    val circuitId: Long? = null,
    val dateTime: Long = 0L,
    val type: SessionType = SessionType.FREE,
    val kartNumber: Int? = null,
    val ballastKg: Int? = null,
    /** `null` hasta que el usuario toca el slider. */
    val ambientTempC: Int? = null,
    val trackTempC: Int? = null,
    val condition: TrackCondition = TrackCondition.DRY,
    val notes: String = "",
    val isSaving: Boolean = false,
    val showCircuitPicker: Boolean = false,
    val circuitDialog: CircuitDialogState? = null,
    /** Edición de una tanda existente (desde el Detalle) en vez de una nueva. */
    val isEditing: Boolean = false,
) {
    val selectedCircuit: Circuit? get() = circuits.firstOrNull { it.id == circuitId }
    val canSave: Boolean get() = circuitId != null && !isSaving
}

data class CircuitDialogState(
    val name: String = "",
    val lengthMeters: Int? = null,
    val nameError: Boolean = false,
)

private data class Form(
    val circuitId: Long? = null,
    val dateTime: Long,
    val type: SessionType = SessionType.FREE,
    val kartNumber: Int? = null,
    val ballastKg: Int? = null,
    val ambientTempC: Int? = null,
    val trackTempC: Int? = null,
    val condition: TrackCondition = TrackCondition.DRY,
    val notes: String = "",
    val isSaving: Boolean = false,
    val showCircuitPicker: Boolean = false,
    val circuitDialog: CircuitDialogState? = null,
)

private const val MAX_KART = 99
private const val MAX_BALLAST_KG = 60
private const val LENGTH_STEP_M = 50
private const val MAX_LENGTH_M = 10_000

class NewSessionViewModel(
    private val circuits: CircuitRepository,
    private val sessionRepository: SessionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    now: () -> Long = System::currentTimeMillis,
    /** Si no es `null`, el formulario edita esa tanda (D1): se rellena con sus datos y GUARDAR la actualiza. */
    private val editSessionId: Long? = null,
) : ViewModel() {

    private val form = MutableStateFlow(Form(dateTime = now().truncatedToMinute()))

    /** Circuitos ordenados por uso reciente (última tanda creada) y, tras ellos, por nombre. */
    private val orderedCircuits: Flow<Pair<List<Circuit>, Set<Long>>> =
        combine(circuits.observeAll(), sessionRepository.observeSummaries()) { list, summaries ->
            val lastUse = summaries.groupBy { it.circuit.id }.mapValues { (_, v) -> v.maxOf { it.session.id } }
            val sorted = list.sortedWith(
                compareByDescending<Circuit> { lastUse[it.id] ?: Long.MIN_VALUE }.thenBy { it.name.lowercase() },
            )
            sorted to lastUse.keys
        }

    val uiState: StateFlow<NewSessionUiState> = combine(form, orderedCircuits) { f, (list, _) ->
        NewSessionUiState(
            circuits = list,
            circuitId = f.circuitId,
            dateTime = f.dateTime,
            type = f.type,
            kartNumber = f.kartNumber,
            ballastKg = f.ballastKg,
            ambientTempC = f.ambientTempC,
            trackTempC = f.trackTempC,
            condition = f.condition,
            notes = f.notes,
            isSaving = f.isSaving,
            showCircuitPicker = f.showCircuitPicker,
            circuitDialog = f.circuitDialog,
            isEditing = editSessionId != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewSessionUiState(dateTime = form.value.dateTime))

    private val savedChannel = Channel<Long>(Channel.BUFFERED)

    /** Emite el id de la sesión recién creada para navegar a su detalle. */
    val saved = savedChannel.receiveAsFlow()

    init {
        if (editSessionId != null) {
            viewModelScope.launch {
                val s = sessionRepository.observeDetail(editSessionId).first()?.session ?: return@launch
                form.update {
                    it.copy(
                        circuitId = s.circuitId, dateTime = s.dateTime, type = s.type, kartNumber = s.kartNumber,
                        ballastKg = s.ballastKg?.let(Math::round), ambientTempC = s.ambientTempC?.let(Math::round),
                        trackTempC = s.trackTempC?.let(Math::round), condition = s.trackCondition, notes = s.notes,
                    )
                }
            }
        } else {
            // Preselecciona el circuito de la última tanda creada; sin tandas, no preselecciona nada.
            viewModelScope.launch {
                val (sorted, used) = orderedCircuits.first()
                val last = sorted.firstOrNull { it.id in used } ?: return@launch
                form.update { if (it.circuitId == null) it.copy(circuitId = last.id) else it }
            }
        }
    }

    fun selectCircuit(id: Long) = form.update { it.copy(circuitId = id, showCircuitPicker = false) }
    fun openCircuitPicker() = form.update { it.copy(showCircuitPicker = true) }
    fun dismissCircuitPicker() = form.update { it.copy(showCircuitPicker = false) }
    fun selectType(type: SessionType) = form.update { it.copy(type = type) }
    fun selectCondition(condition: TrackCondition) = form.update { it.copy(condition = condition) }
    fun setAmbientTemp(value: Int) = form.update { it.copy(ambientTempC = value) }
    fun clearAmbientTemp() = form.update { it.copy(ambientTempC = null) }
    fun setTrackTemp(value: Int) = form.update { it.copy(trackTempC = value) }
    fun clearTrackTemp() = form.update { it.copy(trackTempC = null) }
    fun setNotes(value: String) = form.update { it.copy(notes = value) }

    /** [utcMillis] es el valor del DatePicker de Material 3: medianoche UTC del día elegido. */
    fun setDate(utcMillis: Long) = form.update { f ->
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(f.dateTime).atZone(zone).toLocalTime()
        f.copy(dateTime = date.atTime(time).atZone(zone).toInstant().toEpochMilli())
    }

    fun setTime(hour: Int, minute: Int) = form.update { f ->
        val date = Instant.ofEpochMilli(f.dateTime).atZone(zone).toLocalDate()
        f.copy(dateTime = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli())
    }

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
    fun openCircuitDialog() = form.update { it.copy(circuitDialog = CircuitDialogState(), showCircuitPicker = false) }
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
                val data = NewSession(
                    circuitId = circuitId,
                    dateTime = f.dateTime,
                    type = f.type,
                    kartNumber = f.kartNumber,
                    ballastKg = f.ballastKg?.toFloat(),
                    ambientTempC = f.ambientTempC?.toFloat(),
                    trackTempC = f.trackTempC?.toFloat(),
                    trackCondition = f.condition,
                    notes = f.notes.trim(),
                )
                val id = if (editSessionId != null) editSessionId.also { sessionRepository.update(it, data) } else sessionRepository.create(data)
                savedChannel.send(id)
            } finally {
                form.update { it.copy(isSaving = false) }
            }
        }
    }
}

private fun Long.truncatedToMinute(): Long = this - this % 60_000
