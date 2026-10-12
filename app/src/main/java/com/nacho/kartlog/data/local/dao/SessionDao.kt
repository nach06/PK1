package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.data.local.entity.SessionEntity
import com.nacho.kartlog.data.local.entity.SessionSummaryRow
import com.nacho.kartlog.data.local.entity.SessionWithCircuit
import kotlinx.coroutines.flow.Flow

private const val SUMMARY_QUERY = """
    SELECT s.*,
           c.id AS circuit_id,
           c.name AS circuit_name,
           c.lengthMeters AS circuit_lengthMeters,
           c.finishLat AS circuit_finishLat,
           c.finishLon AS circuit_finishLon,
           c.finishBearingDeg AS circuit_finishBearingDeg,
           c.gateWidthM AS circuit_gateWidthM,
           (SELECT COUNT(*) FROM laps l WHERE l.sessionId = s.id) AS lapCount,
           (SELECT MIN(l.timeMs) FROM laps l WHERE l.sessionId = s.id) AS bestLapMs
    FROM sessions s
    INNER JOIN circuits c ON c.id = s.circuitId
    ORDER BY s.dateTime DESC
"""

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    /** Edición desde el Detalle: solo los datos del formulario; no toca título importado, duración, texto original ni calidad GPS. */
    @Query(
        "UPDATE sessions SET circuitId = :circuitId, dateTime = :dateTime, type = :type, kartNumber = :kartNumber, " +
            "ballastKg = :ballastKg, ambientTempC = :ambientTempC, trackTempC = :trackTempC, trackCondition = :condition, notes = :notes WHERE id = :id",
    )
    suspend fun updateEditable(
        id: Long, circuitId: Long, dateTime: Long, type: SessionType, kartNumber: Int?, ballastKg: Float?,
        ambientTempC: Float?, trackTempC: Float?, condition: TrackCondition, notes: String,
    )

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(SUMMARY_QUERY)
    fun observeSummaries(): Flow<List<SessionSummaryRow>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionWithCircuit?>
}
