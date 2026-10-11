package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Datos de la tanda que se cronometra (los mismos que «Nueva tanda»; todo opcional salvo el circuito). */
data class GpsSessionSetup(
    val circuitId: Long,
    val finish: FinishLine,
    val kartNumber: Int? = null,
    val ballastKg: Float? = null,
    val ambientTempC: Float? = null,
    val trackTempC: Float? = null,
    val trackCondition: TrackCondition = TrackCondition.DRY,
    val type: SessionType = SessionType.FREE,
    /** Hora (epoch ms) a la que se pulsó EMPEZAR: es la fecha de la tanda, no la de guardarla. */
    val startedAtMs: Long = 0L,
)

enum class TimingPhase {
    /** No hay tanda en marcha. */
    IDLE,

    /** Cronometrando (espera el primer cruce de meta o ya está dando vueltas). */
    RUNNING,

    /** Parada por el usuario: las vueltas siguen en memoria hasta guardarlas o descartarlas. */
    STOPPED,
}

data class TimingState(
    val phase: TimingPhase = TimingPhase.IDLE,
    val setup: GpsSessionSetup? = null,
    /** Tiempos de las vueltas cerradas, en ms. */
    val laps: List<Long> = emptyList(),
    /** `elapsedNs` del último cruce (inicio de la vuelta en curso); `null` hasta el primer cruce. */
    val currentLapStartNs: Long? = null,
    val lastFixElapsedNs: Long? = null,
    /** Reloj local (`nanoTime`) en el que llegó el último fix: sirve para animar el cronómetro entre fixes. */
    val lastFixArrivalNs: Long? = null,
    val accuracyM: Float? = null,
    val rateHz: Double? = null,
    val speedMps: Float? = null,
    val playbackSpeed: Double = 1.0,
    /** Alguna vuelta se midió con un hueco de fixes grande: su tiempo es menos fiable. */
    val hasUnreliableLap: Boolean = false,
    val error: String? = null,
    /** Hora (epoch ms) a la que se paró la tanda; `null` mientras sigue en marcha. */
    val stoppedAtMs: Long? = null,
) {
    val running: Boolean get() = phase == TimingPhase.RUNNING
    val started: Boolean get() = currentLapStartNs != null
    val lastLapMs: Long? get() = laps.lastOrNull()
    val bestLapMs: Long? get() = laps.minOrNull()

    /** Vuelta en curso (1 = la primera tras salir). */
    val currentLapNumber: Int get() = laps.size + 1

    /** Última vuelta contra la mejor **anterior** (negativo = mejoró). `null` si aún no hay con qué comparar. */
    val deltaMs: Long?
        get() = if (laps.size < 2) null else laps.last() - laps.dropLast(1).min()

    /** Tiempo de la vuelta en curso en [nowNs] (reloj local), interpolado entre fixes. `null` antes del primer cruce. */
    fun currentLapMs(nowNs: Long): Long? {
        val start = currentLapStartNs ?: return null
        val fix = lastFixElapsedNs ?: return null
        val arrival = lastFixArrivalNs ?: return null
        val sinceFix = ((nowNs - arrival).coerceAtLeast(0) * playbackSpeed).toLong()
        return ((fix + sinceFix - start) / 1_000_000L).coerceAtLeast(0)
    }
}

sealed interface TimingEvent {
    /** Primer cruce: empieza a contar. */
    data object Started : TimingEvent

    data class LapClosed(val number: Int, val lapTimeMs: Long, val isBest: Boolean, val reliable: Boolean) : TimingEvent
}

/** Lo que queda de una tanda parada: vueltas y trazado completo. */
class GpsRecording(
    val setup: GpsSessionSetup,
    val laps: List<Long>,
    val fixes: List<GpsFix>,
    /** Hora (epoch ms) a la que se paró; con [GpsSessionSetup.startedAtMs] da la duración de la tanda. */
    val stoppedAtMs: Long = setup.startedAtMs,
)

/**
 * Cronometraje en marcha: lee posiciones, detecta la meta y mantiene el estado que pinta el Dashboard y la
 * notificación. Vive fuera de cualquier pantalla (en el contenedor de la app), así que sobrevive a girar el móvil,
 * a apagar la pantalla o a salir de la app; el servicio en primer plano evita que Android mate el proceso.
 */
class TimingSession(
    private val scope: CoroutineScope,
    private val clockNs: () -> Long = System::nanoTime,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(TimingState())
    val state: StateFlow<TimingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimingEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var detector: GateDetector? = null
    private val fixes = ArrayList<GpsFix>()
    private val recentArrivals = ArrayDeque<Long>()

    /** Empieza a cronometrar. Si ya hay una tanda (en marcha o parada sin guardar) no hace nada y devuelve `false`. */
    fun start(setup: GpsSessionSetup, source: LocationSource, config: GateConfig = GateConfig()): Boolean {
        if (_state.value.phase != TimingPhase.IDLE) return false
        fixes.clear()
        recentArrivals.clear()
        detector = GateDetector(setup.finish, config)
        _state.value = TimingState(phase = TimingPhase.RUNNING, setup = setup, playbackSpeed = source.playbackSpeed)
        job = scope.launch {
            try {
                source.fixes().collect(::onFix)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _state.update { it.copy(error = "permiso") }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "gps") }
            }
        }
        return true
    }

    private fun onFix(fix: GpsFix) {
        val arrival = clockNs()
        fixes += fix
        recentArrivals.addLast(arrival)
        while (recentArrivals.isNotEmpty() && arrival - recentArrivals.first() > RATE_WINDOW_NS) recentArrivals.removeFirst()
        val span = (arrival - recentArrivals.first()) / 1e9 * _state.value.playbackSpeed
        val rate = if (recentArrivals.size >= 2 && span > 0) (recentArrivals.size - 1) / span else null

        val event = detector?.feed(fix)
        _state.update { s ->
            var next = s.copy(
                lastFixElapsedNs = fix.elapsedNs,
                lastFixArrivalNs = arrival,
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                rateHz = rate,
            )
            if (event != null) {
                next = next.copy(currentLapStartNs = event.crossing.elapsedNs)
                if (event.lapTimeMs != null) {
                    next = next.copy(
                        laps = s.laps + event.lapTimeMs,
                        hasUnreliableLap = s.hasUnreliableLap || !event.crossing.reliable,
                    )
                }
            }
            next
        }
        if (event != null) {
            if (event.lapTimeMs == null) {
                _events.tryEmit(TimingEvent.Started)
            } else {
                val laps = _state.value.laps
                _events.tryEmit(
                    TimingEvent.LapClosed(
                        number = event.lapNumber!!,
                        lapTimeMs = event.lapTimeMs,
                        isBest = event.lapTimeMs == laps.min(),
                        reliable = event.crossing.reliable,
                    ),
                )
            }
        }
    }

    /** Para el cronómetro conservando las vueltas hasta que se guarden ([takeRecording]) o se descarten ([discard]). */
    fun stop() {
        if (_state.value.phase != TimingPhase.RUNNING) return
        job?.cancel()
        job = null
        _state.update { it.copy(phase = TimingPhase.STOPPED, stoppedAtMs = wallClockMs()) }
    }

    /** Las vueltas y el trazado de la tanda parada, o `null` si no hay ninguna. */
    fun recording(): GpsRecording? {
        val s = _state.value
        val setup = s.setup ?: return null
        if (s.phase != TimingPhase.STOPPED) return null
        return GpsRecording(setup, s.laps, fixes.toList(), s.stoppedAtMs ?: setup.startedAtMs)
    }

    /** Descarta todo (tras guardar o al cancelar) y vuelve a IDLE. */
    fun discard() {
        job?.cancel()
        job = null
        detector = null
        fixes.clear()
        recentArrivals.clear()
        _state.value = TimingState()
    }

    private companion object {
        const val RATE_WINDOW_NS = 5_000_000_000L
    }
}
