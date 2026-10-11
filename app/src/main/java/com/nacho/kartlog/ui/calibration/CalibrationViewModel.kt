package com.nacho.kartlog.ui.calibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.gps.FinishAdjust
import com.nacho.kartlog.domain.gps.FinishCalibration
import com.nacho.kartlog.domain.gps.FinishVerification
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
    /** Meta guardada en el circuito (antes de esta sesión de marcado). */
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
    /** Línea que se está editando sin pasada nueva (ajuste de la meta ya guardada). */
    val editingSaved: Boolean = false,
    /** Desplazamiento manual a lo largo del rumbo (−10…+10 m) y ancho de la puerta (10–40 m). */
    val shiftM: Int = 0,
    val gateWidthM: Int = FinishLine.DEFAULT_GATE_WIDTH_M,
    /** La meta nueva va en sentido contrario a la guardada: el circuito se recorre al revés. */
    val reversed: Boolean = false,
    /** La meta se acaba de guardar en esta pantalla. */
    val saved: Boolean = false,
    /** Modo «Verificar meta»: una pasada de comprobación con la meta ya guardada. */
    val verifying: Boolean = false,
    val verification: FinishVerification.Outcome? = null,
) {
    /** Línea base que se está editando: la del resultado del marcado o la ya guardada. */
    val baseLine: FinishLine? get() = result?.finishLine ?: if (editingSaved) currentFinish else null

    /** La puerta tal y como quedaría con el ajuste manual aplicado (es la que se dibuja y se guarda). */
    val adjustedLine: FinishLine? get() = baseLine?.let { FinishAdjust.apply(it, shiftM, gateWidthM) }

    /** Línea que dibuja el plano: la editada o, si no, la guardada. */
    val displayLine: FinishLine? get() = adjustedLine ?: currentFinish

    val hasPendingLine: Boolean get() = baseLine != null
}

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
                _uiState.update { it.copy(circuitName = c.name, currentFinish = c.finishLine, gateWidthM = c.finishLine?.gateWidthM ?: it.gateWidthM) }
            }
        }
    }

    /** Empieza a escuchar el GPS (se llama cuando ya hay permiso). Es idempotente. */
    fun startListening() {
        if (job?.isActive == true) return
        launchListening()
    }

    private fun launchListening() {
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
                trace = if (it.result == null && it.verification == null) buffer.toList() else it.trace,
            )
        }
    }

    // ---------------- Marcado ----------------

    /** MARCAR: calcula punto y rumbo con lo último recibido. */
    fun mark() {
        val state = _uiState.value
        if (state.result != null || state.editingSaved) return
        when (val outcome = FinishCalibration.compute(buffer.toList())) {
            is FinishCalibration.Outcome.Ok -> _uiState.update {
                it.copy(
                    result = outcome, failure = null, trace = buffer.toList(), shiftM = 0,
                    gateWidthM = outcome.finishLine.gateWidthM,
                    reversed = it.currentFinish?.let { old -> FinishAdjust.isReversed(old, outcome.finishLine) } == true,
                )
            }
            is FinishCalibration.Outcome.Failed -> _uiState.update { it.copy(failure = outcome.reason) }
        }
    }

    /** REPETIR: descarta el resultado y vuelve a la vista en vivo. */
    fun retry() = _uiState.update {
        it.copy(result = null, failure = null, trace = buffer.toList(), editingSaved = false, shiftM = 0, reversed = false, saved = false)
    }

    // ---------------- Ajuste manual ----------------

    /** Edita la meta ya guardada (sin pasada nueva): solo mueve y ensancha la puerta. */
    fun editSaved() {
        val saved = _uiState.value.currentFinish ?: return
        _uiState.update { it.copy(editingSaved = true, result = null, failure = null, shiftM = 0, gateWidthM = saved.gateWidthM, saved = false) }
    }

    fun shiftForward() = _uiState.update { it.copy(shiftM = FinishAdjust.clampShift(it.shiftM + 1)) }
    fun shiftBackward() = _uiState.update { it.copy(shiftM = FinishAdjust.clampShift(it.shiftM - 1)) }
    fun widen() = _uiState.update { it.copy(gateWidthM = FinishAdjust.clampGate(it.gateWidthM + FinishAdjust.GATE_STEP_M)) }
    fun narrow() = _uiState.update { it.copy(gateWidthM = FinishAdjust.clampGate(it.gateWidthM - FinishAdjust.GATE_STEP_M)) }

    // ---------------- Guardar ----------------

    /** Guarda la puerta con el ajuste manual aplicado. La pantalla ofrece después verificarla. */
    fun save() {
        val line = _uiState.value.adjustedLine ?: return
        viewModelScope.launch {
            circuits.setFinishLine(circuitId, line)
            _uiState.update {
                it.copy(saved = true, currentFinish = line, result = null, editingSaved = false, shiftM = 0, gateWidthM = line.gateWidthM, reversed = false)
            }
        }
    }

    // ---------------- Verificación ----------------

    /** «Verificar meta»: nueva pasada de comprobación con la meta guardada. */
    fun startVerification() {
        if (_uiState.value.currentFinish == null) return
        buffer.clear()
        job?.cancel()
        _uiState.update { it.copy(verifying = true, verification = null, saved = false, trace = emptyList(), fixCount = 0) }
        launchListening()
    }

    /** El usuario confirma que está pasando por la línea: se compara con el cruce detectado. */
    fun confirmVerification() {
        val line = _uiState.value.currentFinish ?: return
        if (!_uiState.value.verifying || _uiState.value.verification != null) return
        val outcome = FinishVerification.check(line, buffer.toList())
        _uiState.update { it.copy(verification = outcome, trace = buffer.toList()) }
    }

    fun retryVerification() = startVerification()

    /** Sale del modo verificación (vuelve al marcado normal). */
    fun endVerification() = _uiState.update { it.copy(verifying = false, verification = null) }

    private companion object {
        const val KEEP_NS = 15_000_000_000L
        const val RATE_WINDOW_NS = 5_000_000_000L
    }
}
