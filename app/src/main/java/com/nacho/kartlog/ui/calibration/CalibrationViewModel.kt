package com.nacho.kartlog.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.gps.FinishCalibration
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.repository.CircuitRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalibrationUiState(
    val circuitName: String = "",
    val currentFinish: FinishLine? = null,
    val listening: Boolean = false,
    val fixCount: Int = 0,
    /** Precisión del último fix (m). */
    val accuracyM: Float? = null,
    /** Frecuencia de fixes de los últimos segundos (Hz). */
    val rateHz: Double? = null,
    /** Trazado reciente (últimos ~15 s) para dibujarlo. */
    val trace: List<GpsFix> = emptyList(),
    val result: FinishCalibration.Outcome.Ok? = null,
    val failure: FinishCalibration.Failure? = null,
    val locationError: Boolean = false,
    val saved: Boolean = false,
)

class CalibrationViewModel(
    private val circuitId: Long,
    private val circuits: CircuitRepository,
    private val locationSource: LocationSource,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalibrationUiState())
    val uiState: StateFlow<CalibrationUiState> = _uiState.asStateFlow()

    private val buffer = ArrayDeque<GpsFix>()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            circuits.getById(circuitId)?.let { c ->
                _uiState.update { it.copy(circuitName = c.name, currentFinish = c.finishLine) }
            }
        }
    }

    /** Empieza a escuchar el GPS (se llama cuando ya hay permiso). Es idempotente. */
    fun startListening() {
        if (job?.isActive == true) return
        _uiState.update { it.copy(listening = true, locationError = false) }
        job = viewModelScope.launch {
            try {
                locationSource.fixes().collect { onFix(it) }
            } catch (e: SecurityException) {
                _uiState.update { it.copy(listening = false, locationError = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(listening = false, locationError = true) }
            }
        }
    }

    private fun onFix(fix: GpsFix) {
        buffer.addLast(fix)
        while (buffer.isNotEmpty() && fix.elapsedNs - buffer.first().elapsedNs > KEEP_NS) buffer.removeFirst()
        val recent = buffer.filter { fix.elapsedNs - it.elapsedNs <= RATE_WINDOW_NS }
        val span = (recent.last().elapsedNs - recent.first().elapsedNs) / 1e9
        _uiState.update {
            it.copy(
                fixCount = it.fixCount + 1,
                accuracyM = fix.accuracyM,
                rateHz = if (recent.size >= 2 && span > 0) (recent.size - 1) / span else null,
                // Con un resultado en pantalla el trazado queda congelado en el que se usó para calcularlo.
                trace = if (it.result == null) buffer.toList() else it.trace,
            )
        }
    }

    /** MARCAR: calcula punto y rumbo con lo último recibido. */
    fun mark() {
        val state = _uiState.value
        if (state.result != null) return
        when (val outcome = FinishCalibration.compute(buffer.toList())) {
            is FinishCalibration.Outcome.Ok -> _uiState.update { it.copy(result = outcome, failure = null, trace = buffer.toList()) }
            is FinishCalibration.Outcome.Failed -> _uiState.update { it.copy(failure = outcome.reason) }
        }
    }

    /** REPETIR: descarta el resultado y vuelve a la vista en vivo. */
    fun retry() = _uiState.update { it.copy(result = null, failure = null, trace = buffer.toList()) }

    fun save() {
        val result = _uiState.value.result ?: return
        viewModelScope.launch {
            circuits.setFinishLine(circuitId, result.finishLine)
            _uiState.update { it.copy(saved = true, currentFinish = result.finishLine) }
        }
    }

    private companion object {
        const val KEEP_NS = 15_000_000_000L
        const val RATE_WINDOW_NS = 5_000_000_000L
    }
}
