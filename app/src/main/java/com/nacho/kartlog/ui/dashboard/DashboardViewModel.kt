package com.nacho.kartlog.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.gps.GpsRecording
import com.nacho.kartlog.domain.gps.GpsSessionSaver
import com.nacho.kartlog.domain.gps.GateConfig
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.LocationSource
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.ReferenceProvider
import com.nacho.kartlog.domain.gps.TimingSession
import com.nacho.kartlog.domain.gps.TimingState
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.repository.CircuitRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.settings.DashboardOrientation
import com.nacho.kartlog.domain.settings.InMemoryUserSettings
import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una vuelta de la tanda parada, con su fiabilidad y si se conserva al guardar. */
data class SummaryLap(val number: Int, val timeMs: Long, val reliable: Boolean, val kept: Boolean)

/** Resumen de una tanda parada. Las cifras se calculan solo con las vueltas que se conservan. */
/** Un stint de resistencia en el resumen: quién condujo, cuántas vueltas válidas, la mejor y el tiempo en boxes que lo cierra. */
data class SummaryStint(val number: Int, val driverName: String?, val lapCount: Int, val bestMs: Long?, val pitAfterMs: Long?)

data class DashboardSummary(
    val laps: List<SummaryLap>,
    val stints: List<SummaryStint> = emptyList(),
) {
    /** Solo hay desglose por stints si hubo alguna parada en boxes. */
    val hasStints: Boolean get() = stints.size > 1
    val totalPitMs: Long get() = stints.sumOf { it.pitAfterMs ?: 0L }
    private val keptTimes get() = laps.filter { it.kept }.map { it.timeMs }
    val lapCount: Int get() = keptTimes.size
    val bestMs: Long? get() = keptTimes.minOrNull()
    val cleanAverageMs: Long? get() = Analytics.cleanAverageMs(keptTimes)
    val unreliableLaps: List<SummaryLap> get() = laps.filter { !it.reliable }
    val hasUnreliableLap: Boolean get() = laps.any { !it.reliable }
}

/** Ajustes que afectan al Dashboard en pista. */
data class DashboardPrefs(
    val orientation: DashboardOrientation = DashboardOrientation.LANDSCAPE,
    val dayMode: Boolean = false,
    val vibrationOnly: Boolean = false,
    val maxBrightness: Boolean = false,
    val feedback: Boolean = true,
)

data class DashboardUiState(
    val circuits: List<Circuit> = emptyList(),
    val circuitId: Long? = null,
    val kartNumber: Int? = null,
    val ballastKg: Int? = null,
    val ambientTempC: Int? = null,
    val trackTempC: Int? = null,
    val condition: TrackCondition = TrackCondition.DRY,
    val type: SessionType = SessionType.FREE,
    val showCircuitPicker: Boolean = false,
    val timing: TimingState = TimingState(),
    val summary: DashboardSummary? = null,
    val saving: Boolean = false,
    /** Se intentó guardar sin vueltas cerradas: se descartó la tanda. */
    val nothingToSave: Boolean = false,
    val prefs: DashboardPrefs = DashboardPrefs(),
    /** Récord histórico del circuito (sin contar la tanda en curso), para avisar de un nuevo récord. */
    val circuitRecordMs: Long? = null,
) {
    val selectedCircuit: Circuit? get() = circuits.firstOrNull { it.id == circuitId }

    /** Solo se puede empezar con un circuito que tenga la meta marcada. */
    val canStart: Boolean get() = selectedCircuit?.finishLine != null

    /** El circuito elegido no tiene meta: la pantalla avisa y ofrece marcarla. */
    val needsFinish: Boolean get() = selectedCircuit != null && selectedCircuit?.finishLine == null
}

class DashboardViewModel(
    private val circuitRepository: CircuitRepository,
    private val timingSession: TimingSession,
    private val saver: GpsSessionSaver,
    private val startTracking: () -> Unit,
    private val stopTracking: () -> Unit,
    private val locationSource: () -> LocationSource,
    private val userSettings: UserSettings = InMemoryUserSettings(),
    private val analytics: AnalyticsSource? = null,
    private val referenceProvider: ReferenceProvider? = null,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private data class Form(
        val circuitId: Long? = null,
        val kartNumber: Int? = null,
        val ballastKg: Int? = null,
        val ambientTempC: Int? = null,
        val trackTempC: Int? = null,
        val condition: TrackCondition = TrackCondition.DRY,
        val type: SessionType = SessionType.FREE,
        val showCircuitPicker: Boolean = false,
        val saving: Boolean = false,
        val nothingToSave: Boolean = false,
        val summary: DashboardSummary? = null,
        /** Vueltas poco fiables que el usuario ha decidido descartar al guardar (nº de vuelta de la grabación). */
        val discardedLaps: Set<Int> = emptySet(),
    )

    private val form = MutableStateFlow(Form())

    private val prefsFlow = combine(
        userSettings.dashboardOrientation, userSettings.dashboardDayMode, userSettings.vibrationOnly,
        userSettings.maxBrightness, userSettings.lapFeedback,
    ) { orientation, day, vibration, brightness, feedback ->
        DashboardPrefs(orientation, day, vibration, brightness, feedback)
    }

    private val recordsFlow = analytics?.observe()?.map { snapshot -> snapshot.circuits.mapValues { it.value.recordMs } }
        ?: flowOf(emptyMap())

    val uiState: StateFlow<DashboardUiState> = combine(
        circuitRepository.observeAll(), form, timingSession.state, prefsFlow, recordsFlow,
    ) { circuits, f, timing, prefs, records ->
        // Si hay una tanda en marcha o parada, el circuito mostrado es el suyo.
        val activeCircuit = timing.setup?.circuitId
        DashboardUiState(
            circuits = circuits,
            circuitId = activeCircuit ?: f.circuitId ?: circuits.firstOrNull { it.finishLine != null }?.id,
            kartNumber = f.kartNumber, ballastKg = f.ballastKg, ambientTempC = f.ambientTempC, trackTempC = f.trackTempC,
            condition = f.condition, type = f.type, showCircuitPicker = f.showCircuitPicker, timing = timing,
            summary = if (timing.phase == TimingPhase.STOPPED) summaryOf(timing, f.discardedLaps) else null,
            saving = f.saving, nothingToSave = f.nothingToSave,
            prefs = prefs,
        ).let { it.copy(circuitRecordMs = it.circuitId?.let(records::get)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private val savedChannel = Channel<Long>(Channel.BUFFERED)

    /** Emite el id de la tanda guardada para abrir su Detalle. */
    val saved = savedChannel.receiveAsFlow()

    fun openCircuitPicker() = form.update { it.copy(showCircuitPicker = true) }
    fun dismissCircuitPicker() = form.update { it.copy(showCircuitPicker = false) }
    fun selectCircuit(id: Long) = form.update { it.copy(circuitId = id, showCircuitPicker = false) }
    fun incrementKart() = form.update { it.copy(kartNumber = ((it.kartNumber ?: 0) + 1).coerceAtMost(MAX_KART)) }
    fun decrementKart() = form.update { f -> f.copy(kartNumber = f.kartNumber?.let { if (it <= 1) null else it - 1 }) }
    fun incrementBallast() = form.update { f -> f.copy(ballastKg = f.ballastKg?.plus(1)?.coerceAtMost(MAX_BALLAST_KG) ?: 0) }
    fun decrementBallast() = form.update { f -> f.copy(ballastKg = f.ballastKg?.let { if (it <= 0) null else it - 1 }) }
    fun setAmbientTemp(value: Int) = form.update { it.copy(ambientTempC = value) }
    fun clearAmbientTemp() = form.update { it.copy(ambientTempC = null) }
    fun setTrackTemp(value: Int) = form.update { it.copy(trackTempC = value) }
    fun clearTrackTemp() = form.update { it.copy(trackTempC = null) }
    fun selectCondition(value: TrackCondition) = form.update { it.copy(condition = value) }
    fun selectType(value: SessionType) = form.update { it.copy(type = value) }
    fun setDayMode(enabled: Boolean) = userSettings.setDashboardDayMode(enabled)

    /** EMPEZAR: arranca el cronómetro y el servicio en primer plano. */
    fun start() {
        val state = uiState.value
        val circuit = state.selectedCircuit ?: return
        val finish = circuit.finishLine ?: return
        if (state.timing.phase != TimingPhase.IDLE) return
        form.update { it.copy(summary = null, nothingToSave = false) }
        val setup = GpsSessionSetup(
            circuitId = circuit.id,
            finish = finish,
            kartNumber = state.kartNumber,
            ballastKg = state.ballastKg?.toFloat(),
            ambientTempC = state.ambientTempC?.toFloat(),
            trackTempC = state.trackTempC?.toFloat(),
            trackCondition = state.condition,
            type = state.type,
            startedAtMs = now(),
        )
        if (timingSession.start(setup, locationSource(), GateConfig(delayCompensationMs = userSettings.gpsDelayMs.value.toLong()))) {
            startTracking()
            // Referencia del delta desde la primera vuelta: la mejor vuelta del circuito con trazado (la de esta tanda la sustituye en cuanto exista).
            referenceProvider?.let { provider ->
                viewModelScope.launch { runCatching { provider.bestFor(circuit.id, finish) }.getOrNull()?.let(timingSession::offerReference) }
            }
        }
    }

    /** Fin de la pulsación larga: para el cronómetro (las vueltas quedan en memoria hasta guardar o descartar). */
    fun finish() {
        timingSession.stop()
        stopTracking()
        form.update { it.copy(discardedLaps = emptySet()) }
    }

    /** Alterna entre mantener y descartar una vuelta poco fiable del resumen. */
    fun toggleLap(number: Int) = form.update {
        it.copy(discardedLaps = if (number in it.discardedLaps) it.discardedLaps - number else it.discardedLaps + number)
    }

    /** Guarda la tanda como sesión normal (vueltas `GPS`) y avisa para abrir su Detalle. Las descartadas no se guardan. */
    fun save() {
        val full = timingSession.recording() ?: return
        val discarded = form.value.discardedLaps
        val recording = GpsRecording(
            setup = full.setup,
            laps = full.laps.filterIndexed { i, _ -> (i + 1) !in discarded },
            fixes = full.fixes,
            stoppedAtMs = full.stoppedAtMs,
            stints = full.stints,
            lapStint = full.lapStint.filterIndexed { i, _ -> (i + 1) !in discarded },
        )
        if (form.value.saving) return
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = runCatching { saver.save(recording) }.getOrNull()
            if (id == null) {
                form.update { it.copy(saving = false, nothingToSave = recording.laps.isEmpty()) }
                if (recording.laps.isEmpty()) timingSession.discard()
            } else {
                timingSession.discard()
                form.update { it.copy(saving = false, summary = null) }
                savedChannel.send(id)
            }
        }
    }

    /** Resistencia: «Pit / cambio de piloto». Descarta la vuelta en curso y el próximo cruce cierra el tiempo en boxes. */
    fun requestPit(nextDriver: String?) {
        timingSession.requestPit(nextDriver)
    }

    fun discard() {
        timingSession.discard()
        stopTracking()
        form.update { it.copy(summary = null, nothingToSave = false) }
    }

    private fun summaryOf(timing: TimingState, discarded: Set<Int>) = DashboardSummary(
        laps = timing.laps.mapIndexed { i, ms ->
            SummaryLap(number = i + 1, timeMs = ms, reliable = timing.lapReliable.getOrElse(i) { true }, kept = (i + 1) !in discarded)
        },
        stints = timing.stints.mapIndexed { index, stint ->
            val times = timing.laps.filterIndexed { i, _ -> timing.lapStint.getOrElse(i) { 0 } == index && (i + 1) !in discarded }
            SummaryStint(index + 1, stint.driverName, times.size, times.minOrNull(), stint.pitAfterMs)
        },
    )

    private companion object {
        const val MAX_KART = 99
        const val MAX_BALLAST_KG = 60
    }
}
