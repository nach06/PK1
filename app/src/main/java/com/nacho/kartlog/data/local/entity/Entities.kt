package com.nacho.kartlog.data.local.entity

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition

@Entity(
    tableName = "circuits",
    indices = [Index(value = ["name"], unique = true)],
)
data class CircuitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lengthMeters: Int? = null,
    val finishLat: Double? = null,
    val finishLon: Double? = null,
    val finishBearingDeg: Double? = null,
    @ColumnInfo(defaultValue = "20") val gateWidthM: Int = 20,
)

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = CircuitEntity::class,
            parentColumns = ["id"],
            childColumns = ["circuitId"],
        ),
    ],
    indices = [Index("circuitId")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val circuitId: Long,
    val dateTime: Long,
    val type: SessionType,
    val kartNumber: Int? = null,
    val ballastKg: Float? = null,
    val ambientTempC: Float? = null,
    val trackTempC: Float? = null,
    val trackCondition: TrackCondition,
    val notes: String = "",
    /** Título de la sesión importada (p. ej. «Sesión 46 GP3 DIEGO»). */
    val label: String? = null,
    val durationMin: Int? = null,
    /** Texto original (o salida del OCR) de la importación; nunca la foto. */
    val importedRaw: String? = null,
)

@Entity(
    tableName = "laps",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "number"], unique = true),
    ],
)
data class LapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val number: Int,
    val timeMs: Long,
    val source: LapSource,
    /** Stint (resistencia) al que pertenece la vuelta; `null` en tandas sin stints. */
    val stintId: Long? = null,
)

/** Piloto de una sesión importada; `isMe` marca al propio usuario (sus vueltas están en `laps`). */
@Entity(
    tableName = "participants",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ParticipantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val position: Int? = null,
    val name: String? = null,
    val kartNumber: Int? = null,
    val isMe: Boolean = false,
    val bestMs: Long? = null,
    val avgMs: Long? = null,
    val lapCount: Int? = null,
)

/** Vuelta de otro piloto (las del propio usuario se guardan como [LapEntity] con origen IMPORTED). */
@Entity(
    tableName = "participant_laps",
    foreignKeys = [
        ForeignKey(
            entity = ParticipantEntity::class,
            parentColumns = ["id"],
            childColumns = ["participantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["participantId", "number"], unique = true)],
)
data class ParticipantLapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val participantId: Long,
    val number: Int,
    val timeMs: Long,
)

/** Sesión con su circuito. */
data class SessionWithCircuit(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "circuitId", entityColumn = "id")
    val circuit: CircuitEntity,
)

/** Fila del listado: sesión + circuito + estadísticas calculadas en SQL. */
data class SessionSummaryRow(
    @Embedded val session: SessionEntity,
    @Embedded(prefix = "circuit_") val circuit: CircuitEntity,
    val lapCount: Int,
    val bestLapMs: Long?,
)

/** Tramo de una carrera de resistencia entre dos paradas en boxes (o cambio de piloto). */
@Entity(
    tableName = "stints",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class StintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    /** Primera y última vuelta (nº de vuelta de la tanda) del stint; se recalculan al borrar vueltas. */
    val startLap: Int,
    val endLap: Int,
    val driverName: String? = null,
    /** Tiempo en boxes de la parada que **cierra** este stint (entre el último cruce antes y el primero después); `null` en el último. */
    val pitMs: Long? = null,
)
