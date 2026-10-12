package com.nacho.kartlog.domain.gps

import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

/** Un stint de resistencia: quién conduce (opcional) y cuánto duró la parada en boxes que lo cierra. */
data class StintInfo(val driverName: String? = null, val pitAfterMs: Long? = null)

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
    /** Fiabilidad de cada vuelta cerrada (misma posición que [laps]): `false` si un hueco de fixes rodeó alguno de sus dos cruces. */
    val lapReliable: List<Boolean> = emptyList(),
    /** Fiabilidad del último cruce de meta (inicio de la vuelta en curso). */
    val lastCrossingReliable: Boolean = true,
    /** Stints de la tanda; siempre hay al menos uno (el actual es el último). */
    val stints: List<StintInfo> = listOf(StintInfo()),
    /** Índice en [stints] del stint de cada vuelta cerrada (misma posición que [laps]). */
    val lapStint: List<Int> = emptyList(),
    /** Se pidió parada en boxes: la vuelta en curso se descarta y el próximo cruce cierra el tiempo en boxes. */
    val pitPending: Boolean = false,
    val pendingDriver: String? = null,
    val error: String? = null,
    /** Hora (epoch ms) a la que se paró la tanda; `null` mientras sigue en marcha. */
    val stoppedAtMs: Long? = null,
    /** Vuelta de referencia del delta en vuelta: la mejor vuelta GPS de la tanda o, si aún no hay, la mejor del circuito con trazado. */
    val reference: ReferenceProfile? = null,
    val referenceSource: ReferenceSource? = null,
    /** Posición y velocidad en el último fix (para el delta en vuelta). */
    val lapPosition: LapPosition? = null,
) {
    /** Alguna vuelta se midió con un hueco de fixes grande: su tiempo es menos fiable. */
    val hasUnreliableLap: Boolean get() = lapReliable.any { !it }

    val running: Boolean get() = phase == TimingPhase.RUNNING
    val started: Boolean get() = currentLapStartNs != null
    val lastLapMs: Long? get() = laps.lastOrNull()
    val bestLapMs: Long? get() = laps.minOrNull()

    /** Nº del stint en curso (1 = el primero). */
    val currentStintNumber: Int get() = stints.size

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

    /** Fin de una parada en boxes (resistencia): [pitMs] va del último cruce antes de parar al primero después. */
    data class PitCompleted(val pitMs: Long, val newStintNumber: Int) : TimingEvent
}

/** Petición de parada en boxes, con el instante (reloj de los fixes) en que se pidió: se guarda en el borrador. */
data class PitRequest(val atElapsedNs: Long, val driverName: String?)

/** Lo que queda de una tanda parada: vueltas y trazado completo. */
class GpsRecording(
    val setup: GpsSessionSetup,
    val laps: List<Long>,
    val fixes: List<GpsFix>,
    /** Hora (epoch ms) a la que se paró; con [GpsSessionSetup.startedAtMs] da la duración de la tanda. */
    val stoppedAtMs: Long = setup.startedAtMs,
    val stints: List<StintInfo> = listOf(StintInfo()),
    /** Stint de cada vuelta de [laps] (índice en [stints]); vacío = todas en el primero. */
    val lapStint: List<Int> = emptyList(),
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
    private val draftStore: DraftStore? = null,
    private val draftIntervalMs: Long = DRAFT_INTERVAL_MS,
) {
    private val _state = MutableStateFlow(TimingState())
    val state: StateFlow<TimingState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TimingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TimingEvent> = _events.asSharedFlow()

    private var job: Job? = null
    private var draftJob: Job? = null
    private var draftOps: Channel<suspend () -> Unit>? = null
    private val pendingForDraft = ArrayList<GpsFix>()
    private var lastDraftFlushNs = 0L
    private var restoring = false
    private var detector: GateDetector? = null
    private val fixes = ArrayList<GpsFix>()
    private val recentArrivals = ArrayDeque<Long>()

    // Geometría de la vuelta en curso (delta en vuelta): puntos desde el último cruce en el sistema local de la meta.
    private var frame: LocalFrame? = null
    private val lapPts = ArrayList<RefPoint>()
    private var lapMaxGapNs = 0L
    private var prevFix: GpsFix? = null
    private var prevXY: XY? = null

    private fun resetGeometry(finish: com.nacho.kartlog.domain.model.FinishLine?) {
        frame = finish?.let { LocalFrame(it.lat, it.lon) }
        lapPts.clear()
        lapMaxGapNs = 0L
        prevFix = null
        prevXY = null
    }

    /**
     * Ofrece la mejor vuelta del circuito con trazado como referencia desde la primera vuelta. Solo se usa si la tanda
     * aún no tiene vuelta propia de referencia.
     */
    fun offerReference(profile: ReferenceProfile) {
        _state.update { if (it.reference == null) it.copy(reference = profile, referenceSource = ReferenceSource.CIRCUIT) else it }
    }

    /** Empieza a cronometrar. Si ya hay una tanda (en marcha o parada sin guardar) no hace nada y devuelve `false`. */
    fun start(setup: GpsSessionSetup, source: LocationSource, config: GateConfig = GateConfig()): Boolean {
        if (_state.value.phase != TimingPhase.IDLE) return false
        fixes.clear()
        recentArrivals.clear()
        detector = GateDetector(setup.finish, config)
        resetGeometry(setup.finish)
        _state.value = TimingState(phase = TimingPhase.RUNNING, setup = setup, playbackSpeed = source.playbackSpeed)
        startDraft(setup)
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

    /** Las operaciones sobre el borrador salen por un canal y las ejecuta una sola corrutina: siempre en orden. */
    private fun startDraft(setup: GpsSessionSetup) {
        val store = draftStore ?: return
        draftJob?.cancel()
        pendingForDraft.clear()
        lastDraftFlushNs = clockNs()
        val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)
        draftOps = ops
        ops.trySend { store.begin(setup) }
        draftJob = scope.launch(Dispatchers.IO) {
            for (op in ops) runCatching { op() } // un fallo de disco no debe parar el cronometraje
        }
    }

    private fun flushDraft() {
        val ops = draftOps ?: return
        val store = draftStore ?: return
        if (pendingForDraft.isEmpty()) return
        val batch = pendingForDraft.toList()
        pendingForDraft.clear()
        ops.trySend { store.append(batch) }
    }

    /** Recupera una tanda interrumpida: recalcula las vueltas con las posiciones y la deja «parada» para guardarla o descartarla. */
    fun restore(draft: SessionDraft): Boolean {
        if (_state.value.phase != TimingPhase.IDLE) return false
        fixes.clear()
        recentArrivals.clear()
        detector = GateDetector(draft.setup.finish)
        resetGeometry(draft.setup.finish)
        _state.value = TimingState(phase = TimingPhase.RUNNING, setup = draft.setup)
        restoring = true
        val pits = draft.pits.sortedBy { it.atElapsedNs }.toMutableList()
        draft.fixes.forEach { fix ->
            // Cada parada pedida se aplica en el momento (reloj de fixes) en que se pidió.
            while (pits.isNotEmpty() && pits.first().atElapsedNs <= fix.elapsedNs && _state.value.started) {
                applyPitRequest(pits.removeAt(0).driverName)
            }
            onFix(fix)
        }
        restoring = false
        job = null
        _state.update { it.copy(phase = TimingPhase.STOPPED, stoppedAtMs = draft.savedAtMs) }
        return true
    }

    private fun onFix(fix: GpsFix) {
        val arrival = clockNs()
        fixes += fix
        if (!restoring && draftOps != null) {
            pendingForDraft += fix
            if (arrival - lastDraftFlushNs >= draftIntervalMs * 1_000_000L) {
                lastDraftFlushNs = arrival
                flushDraft()
            }
        }
        recentArrivals.addLast(arrival)
        while (recentArrivals.isNotEmpty() && arrival - recentArrivals.first() > RATE_WINDOW_NS) recentArrivals.removeFirst()
        val span = (arrival - recentArrivals.first()) / 1e9 * _state.value.playbackSpeed
        val rate = if (recentArrivals.size >= 2 && span > 0) (recentArrivals.size - 1) / span else null

        val event = detector?.feed(fix)
        var pitCompleted: TimingEvent.PitCompleted? = null

        // Geometría de la vuelta: posición/velocidad del fix y, al cerrar una vuelta válida, su perfil como candidata a referencia.
        var position: LapPosition? = null
        var candidate: ReferenceProfile? = null
        frame?.let { f ->
            val p = f.toXY(fix.lat, fix.lon)
            val before = prevFix
            val beforeXY = prevXY
            if (before != null && beforeXY != null) {
                val dt = (fix.elapsedNs - before.elapsedNs) / 1e9
                if (dt > 0) position = LapPosition(p.x, p.y, (p.x - beforeXY.x) / dt, (p.y - beforeXY.y) / dt)
                lapMaxGapNs = maxOf(lapMaxGapNs, fix.elapsedNs - before.elapsedNs)
            }
            if (event != null && before != null && beforeXY != null) {
                val span = (fix.elapsedNs - before.elapsedNs).toDouble()
                val t = if (span <= 0.0) 0.0 else ((event.crossing.elapsedNs - before.elapsedNs) / span).coerceIn(0.0, 1.0)
                val cx = beforeXY.x + t * (p.x - beforeXY.x)
                val cy = beforeXY.y + t * (p.y - beforeXY.y)
                val prior = _state.value
                if (event.lapTimeMs != null && lapPts.isNotEmpty() && !prior.pitPending &&
                    prior.lastCrossingReliable && event.crossing.reliable && lapMaxGapNs <= REFERENCE_MAX_GAP_NS
                ) {
                    lapPts += RefPoint(cx, cy, event.lapTimeMs.toDouble())
                    candidate = ReferenceProfile.from(lapPts)
                }
                lapPts.clear()
                lapMaxGapNs = 0L
                lapPts += RefPoint(cx, cy, 0.0)
                lapPts += RefPoint(p.x, p.y, (fix.elapsedNs - event.crossing.elapsedNs) / 1e6)
            } else if (event == null) {
                _state.value.currentLapStartNs?.let { start -> lapPts += RefPoint(p.x, p.y, (fix.elapsedNs - start) / 1e6) }
            }
            prevFix = fix
            prevXY = p
        }

        _state.update { s ->
            var next = s.copy(
                lastFixElapsedNs = fix.elapsedNs,
                lastFixArrivalNs = arrival,
                accuracyM = fix.accuracyM,
                speedMps = fix.speedMps,
                rateHz = rate,
                lapPosition = position ?: s.lapPosition,
            )
            if (event != null) {
                next = next.copy(currentLapStartNs = event.crossing.elapsedNs, lastCrossingReliable = event.crossing.reliable)
                // La mejor vuelta GPS de la tanda pasa a ser la referencia (la del circuito solo vale hasta que haya una propia).
                candidate?.let { c ->
                    val current = s.reference
                    if (current == null || s.referenceSource != ReferenceSource.SESSION || c.lapTimeMs < current.lapTimeMs) {
                        next = next.copy(reference = c, referenceSource = ReferenceSource.SESSION)
                    }
                }
                if (event.lapTimeMs != null && s.pitPending) {
                    // Parada en boxes: este «giro» no es una vuelta sino el tiempo en boxes; el stint siguiente empieza aquí.
                    val pitMs = event.lapTimeMs
                    val closed = s.stints.dropLast(1) + s.stints.last().copy(pitAfterMs = pitMs)
                    next = next.copy(
                        stints = closed + StintInfo(driverName = s.pendingDriver),
                        pitPending = false,
                        pendingDriver = null,
                    )
                    pitCompleted = TimingEvent.PitCompleted(pitMs, next.stints.size)
                } else if (event.lapTimeMs != null) {
                    next = next.copy(
                        laps = s.laps + event.lapTimeMs,
                        lapStint = s.lapStint + s.stints.lastIndex,
                        // Una vuelta es fiable si lo son sus dos cruces (el que la abre y el que la cierra).
                        lapReliable = s.lapReliable + (s.lastCrossingReliable && event.crossing.reliable),
                    )
                }
            }
            next
        }
        if (event != null) {
            if (event.lapTimeMs == null) {
                _events.tryEmit(TimingEvent.Started)
            } else if (pitCompleted != null) {
                _events.tryEmit(pitCompleted)
            } else {
                val laps = _state.value.laps
                _events.tryEmit(
                    TimingEvent.LapClosed(
                        number = laps.size, // nº entre las vueltas guardadas (la parada en boxes no cuenta)
                        lapTimeMs = event.lapTimeMs,
                        isBest = event.lapTimeMs == laps.min(),
                        reliable = event.crossing.reliable,
                    ),
                )
            }
        }
    }

    /**
     * Resistencia: «Pit / cambio de piloto». La vuelta en curso se descarta (entrada + parada + salida) y el próximo cruce de
     * meta cierra el tiempo en boxes y abre un stint nuevo. Solo tiene sentido con la tanda arrancada.
     */
    fun requestPit(nextDriver: String? = null): Boolean {
        val s = _state.value
        if (s.phase != TimingPhase.RUNNING || !s.started || s.pitPending) return false
        applyPitRequest(nextDriver)
        draftOps?.let { ops -> draftStore?.let { store -> ops.trySend { store.appendPit(PitRequest(s.lastFixElapsedNs ?: 0L, nextDriver)) } } }
        return true
    }

    private fun applyPitRequest(nextDriver: String?) {
        _state.update { it.copy(pitPending = true, pendingDriver = nextDriver?.trim()?.ifEmpty { null }) }
    }

    /** Para el cronómetro conservando las vueltas hasta que se guarden ([takeRecording]) o se descarten ([discard]). */
    fun stop() {
        if (_state.value.phase != TimingPhase.RUNNING) return
        job?.cancel()
        job = null
        flushDraft() // lo que quede pendiente, para que el borrador esté completo
        _state.update { it.copy(phase = TimingPhase.STOPPED, stoppedAtMs = wallClockMs()) }
    }

    /** Las vueltas y el trazado de la tanda parada, o `null` si no hay ninguna. */
    fun recording(): GpsRecording? {
        val s = _state.value
        val setup = s.setup ?: return null
        if (s.phase != TimingPhase.STOPPED) return null
        return GpsRecording(setup, s.laps, fixes.toList(), s.stoppedAtMs ?: setup.startedAtMs, s.stints, s.lapStint)
    }

    /** Descarta todo (tras guardar o al cancelar) y vuelve a IDLE. */
    fun discard() {
        job?.cancel()
        job = null
        pendingForDraft.clear()
        draftStore?.let { store ->
            val ops = draftOps
            if (ops != null) {
                // El borrador se borra en orden, tras lo ya encolado; luego se cierra el canal.
                ops.trySend { store.clear() }
                ops.close()
            } else {
                // Tanda recuperada (sin canal abierto): se borra directamente.
                scope.launch(Dispatchers.IO) { runCatching { store.clear() } }
            }
            draftOps = null
        }
        detector = null
        resetGeometry(null)
        fixes.clear()
        recentArrivals.clear()
        _state.value = TimingState()
    }

    companion object {
        private const val RATE_WINDOW_NS = 5_000_000_000L

        /** Una vuelta con un hueco de fixes mayor que esto no sirve como referencia. */
        private const val REFERENCE_MAX_GAP_NS = 3_000_000_000L

        /** Cada cuánto se vuelcan las posiciones a disco. */
        const val DRAFT_INTERVAL_MS = 30_000L
    }
}
