package com.nacho.kartlog.data.local.entity

import androidx.room.Embedded
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
