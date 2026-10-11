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
