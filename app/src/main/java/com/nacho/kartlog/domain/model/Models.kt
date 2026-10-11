package com.nacho.kartlog.domain.model

enum class SessionType { FREE, QUALIFYING, RACE, ENDURANCE }

enum class TrackCondition { DRY, DAMP, WET }

/** Origen de una vuelta. En el Sprint 1 siempre es [MANUAL]. */
enum class LapSource { MANUAL, IMPORTED, GPS, LIVE_TIMING }

/**
 * Línea de meta marcada con el GPS: punto, rumbo de paso (grados desde el norte, sentido horario) y ancho de la
 * «puerta» (segmento perpendicular al rumbo) en metros.
 */
data class FinishLine(
    val lat: Double,
    val lon: Double,
    val bearingDeg: Double,
    val gateWidthM: Int = DEFAULT_GATE_WIDTH_M,
) {
    companion object {
        const val DEFAULT_GATE_WIDTH_M = 20
    }
}

data class Circuit(
    val id: Long,
    val name: String,
    val lengthMeters: Int?,
    /** `null` si todavía no se ha marcado la meta (sin ella no se puede cronometrar por GPS). */
    val finishLine: FinishLine? = null,
)

data class Session(
    val id: Long,
    val circuitId: Long,
    val dateTime: Long,
    val type: SessionType,
    val kartNumber: Int?,
    val ballastKg: Float?,
    val ambientTempC: Float?,
    val trackTempC: Float?,
    val trackCondition: TrackCondition,
    val notes: String,
    /** Título de la sesión importada (p. ej. «Sesión 46 GP3 DIEGO»). */
    val label: String? = null,
    val durationMin: Int? = null,
)

/** Datos para crear una sesión; el id lo asigna la base de datos. */
data class NewSession(
    val circuitId: Long,
    val dateTime: Long,
    val type: SessionType,
    val kartNumber: Int?,
    val ballastKg: Float?,
    val ambientTempC: Float?,
    val trackTempC: Float?,
    val trackCondition: TrackCondition,
    val notes: String,
    val label: String? = null,
    val durationMin: Int? = null,
    /** Texto original (o salida del OCR) de la importación. */
    val importedRaw: String? = null,
)

data class Lap(
    val id: Long,
    val sessionId: Long,
    val number: Int,
    /** Tiempo siempre en milisegundos; se formatea solo en la UI. */
    val timeMs: Long,
    val source: LapSource,
)

data class SessionDetail(
    val session: Session,
    val circuit: Circuit,
)

data class SessionSummary(
    val session: Session,
    val circuit: Circuit,
    val lapCount: Int,
    val bestLapMs: Long?,
)

/** Piloto de una sesión importada. `isMe` marca al usuario (sus vueltas están en [Lap], origen IMPORTED). */
data class Participant(
    val id: Long,
    val sessionId: Long,
    val position: Int?,
    val name: String?,
    val kartNumber: Int?,
    val isMe: Boolean,
    val bestMs: Long?,
    val avgMs: Long?,
    val lapCount: Int?,
)

/** Piloto a guardar en una importación; [lapTimesMs] solo se guarda para los demás pilotos. */
data class ParticipantImport(
    val position: Int?,
    val name: String?,
    val kartNumber: Int?,
    val bestMs: Long?,
    val avgMs: Long?,
    val lapCount: Int?,
    val lapTimesMs: List<Long> = emptyList(),
)

/**
 * Resultado de la vista previa listo para guardar. O bien [newSession], o bien [existingSessionId]
 * (las vueltas se añaden a continuación de las que ya tiene).
 */
data class ImportRequest(
    val newSession: NewSession?,
    val existingSessionId: Long?,
    val myLapTimesMs: List<Long>,
    /** Fila del propio usuario en la clasificación (sin vueltas propias: van a [Lap]). */
    val me: ParticipantImport?,
    val others: List<ParticipantImport>,
    /** Texto original; en una sesión existente se guarda si esta aún no tenía uno. */
    val importedRaw: String? = null,
) {
    init {
        require((newSession == null) != (existingSessionId == null)) { "Indica una sesión nueva o una existente" }
    }
}
