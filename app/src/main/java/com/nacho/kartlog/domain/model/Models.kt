package com.nacho.kartlog.domain.model

enum class SessionType { FREE, QUALIFYING, RACE }

enum class TrackCondition { DRY, DAMP, WET }

/** Origen de una vuelta. En el Sprint 1 siempre es [MANUAL]. */
enum class LapSource { MANUAL, IMPORTED, GPS, LIVE_TIMING }

data class Circuit(
    val id: Long,
    val name: String,
    val lengthMeters: Int?,
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
) {
    init {
        require((newSession == null) != (existingSessionId == null)) { "Indica una sesión nueva o una existente" }
    }
}
