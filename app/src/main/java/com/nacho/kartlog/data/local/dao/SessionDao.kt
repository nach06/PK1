package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nacho.kartlog.data.local.entity.SessionEntity
import com.nacho.kartlog.data.local.entity.SessionSummaryRow
import com.nacho.kartlog.data.local.entity.SessionWithCircuit
import kotlinx.coroutines.flow.Flow

private const val SUMMARY_QUERY = """
    SELECT s.*,
           c.id AS circuit_id,
           c.name AS circuit_name,
           c.lengthMeters AS circuit_lengthMeters,
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

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(SUMMARY_QUERY)
    fun observeSummaries(): Flow<List<SessionSummaryRow>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionWithCircuit?>
}
