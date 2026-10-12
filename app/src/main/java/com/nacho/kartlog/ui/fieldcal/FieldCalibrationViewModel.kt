package com.nacho.kartlog.ui.fieldcal

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.gps.FieldCalibration
import com.nacho.kartlog.domain.gps.GateDetector
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FieldCalUiState(
    val circuits: List<Circuit> = emptyList(),
    val circuitId: Long? = null,
    val listening: Boolean = false,
    val accuracyM: Float? = null,
    val crossings: Int = 0,
    val taps: Int = 0,
    val report: FieldCalibration.Report? = null,
    val appliedMs: Int? = null,
    val currentDelayMs: Int = 0,
)

/** Calibración en pista (Sprint 8, B3): detecta los cruces por GPS mientras se pulsa «AQUÍ» en cada paso por la línea. */
class FieldCalibrationViewModel(
    private val circuitRepository: CircuitRepository,
    private val locationSource: LocationSource,
    private val settings: UserSettings,
    /** Mismo reloj que `elapsedRealtimeNanos` de los fixes. */
    private val elapsedRealtimeNs: () -> Long = SystemClock::elapsedRealtimeNanos,
) : ViewModel() {
    private val _state = MutableStateFlow(FieldCalUiState(currentDelayMs = settings.gpsDelayMs.value))
    val uiState: StateFlow<FieldCalUiState> = _state.asStateFlow()

    private val taps = ArrayList<Long>()
    private val crossings = ArrayList<Long>()
    private var detector: GateDetector? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val withFinish = circuitRepository.observeAll().first().filter { it.finishLine != null }
            _state.update { it.copy(circuits = withFinish, circuitId = withFinish.firstOrNull()?.id) }
        }
    }

    fun selectCircuit(id: Long) { if (!_state.value.listening) _state.update { it.copy(circuitId = id) } }

    /** Empieza a escuchar el GPS (con permiso ya concedido) y a detectar cruces con la meta del circuito elegido. */
    fun start() {
        val s = _state.value
        val finish = s.circuits.firstOrNull { it.id == s.circuitId }?.finishLine ?: return
        if (job?.isActive == true) return
        reset(GateDetector(finish))
        _state.update { it.copy(listening = true) }
        job = viewModelScope.launch {
            try {
                locationSource.fixes().collect(::onFix)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(listening = false) }
            }
        }
    }

    private fun reset(d: GateDetector?) {
        taps.clear(); crossings.clear(); detector = d
        _state.update { it.copy(crossings = 0, taps = 0, report = null, appliedMs = null) }
    }

    private fun onFix(fix: GpsFix) {
        detector?.feed(fix)?.let { crossings += it.crossing.elapsedNs }
        _state.update { it.copy(accuracyM = fix.accuracyM, crossings = crossings.size) }
    }

    /** «AQUÍ»: guarda el instante (reloj de los fixes) en que se pulsó. */
    fun tap() {
        if (!_state.value.listening) return
        taps += elapsedRealtimeNs()
        _state.update { it.copy(taps = taps.size) }
    }

    /** Termina y calcula el informe. */
    fun finish() {
        job?.cancel()
        _state.update { it.copy(listening = false, report = FieldCalibration.compute(taps, crossings)) }
    }

    /** Aplica la compensación propuesta al ajuste «Compensación de retardo (ms)». */
    fun apply() {
        val proposed = _state.value.report?.proposedDelayMs ?: return
        settings.setGpsDelayMs(proposed)
        _state.update { it.copy(appliedMs = proposed, currentDelayMs = settings.gpsDelayMs.value) }
    }

    fun again() { job?.cancel(); reset(null); _state.update { it.copy(listening = false) } }

    /** Solo depuración: tanda simulada de 4 vueltas con el GPS 300 ms retrasado y una pulsación exacta por paso. */
    fun debugSimulate() {
        job?.cancel()
        val oval = OvalTrack()
        val sim = TrackSimulator.simulate(oval, listOf(58_000L, 58_400L, 57_800L), hz = 2.0)
        reset(GateDetector(oval.finishLine))
        sim.fixes.map { it.copy(elapsedNs = it.elapsedNs + 300_000_000L) }.forEach { f -> detector?.feed(f)?.let { crossings += it.crossing.elapsedNs } }
        taps += sim.crossingNs
        _state.update { it.copy(crossings = crossings.size, taps = taps.size) }
        finish()
    }
}
