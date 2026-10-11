package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.nacho.kartlog.data.local.entity.LapEntity
import com.nacho.kartlog.domain.model.LapSource
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LapDao {
    @Insert
    abstract suspend fun insert(lap: LapEntity): Long

    @Update
    abstract suspend fun update(lap: LapEntity)

    @Delete
    abstract suspend fun delete(lap: LapEntity)

    @Query("DELETE FROM laps WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("UPDATE laps SET timeMs = :timeMs WHERE id = :id")
    abstract suspend fun updateTime(id: Long, timeMs: Long)

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY number ASC")
    abstract fun observeBySession(sessionId: Long): Flow<List<LapEntity>>

    @Query("SELECT * FROM laps ORDER BY sessionId, number")
    abstract fun observeAll(): Flow<List<LapEntity>>

    /** Siguiente número de vuelta libre: máximo actual + 1 (1 si no hay vueltas). */
    @Query("SELECT COALESCE(MAX(number), 0) + 1 FROM laps WHERE sessionId = :sessionId")
    abstract suspend fun nextNumber(sessionId: Long): Int

    /** Calcula el número y inserta en una sola transacción. */
    @Transaction
    open suspend fun insertNext(sessionId: Long, timeMs: Long, source: LapSource): Long =
        insert(
            LapEntity(
                sessionId = sessionId,
                number = nextNumber(sessionId),
                timeMs = timeMs,
                source = source,
            ),
        )

    @Transaction
    open suspend fun insertAll(sessionId: Long, timesMs: List<Long>, source: LapSource) {
        timesMs.forEach { insertNext(sessionId, it, source) }
    }

    @Query("SELECT sessionId FROM laps WHERE id = :id")
    abstract suspend fun sessionIdOf(id: Long): Long?

    @Query("SELECT id FROM laps WHERE sessionId = :sessionId ORDER BY number ASC")
    protected abstract suspend fun idsInOrder(sessionId: Long): List<Long>

    @Query("UPDATE laps SET number = -number WHERE sessionId = :sessionId")
    protected abstract suspend fun negateNumbers(sessionId: Long)

    @Query("UPDATE laps SET number = :number WHERE id = :id")
    protected abstract suspend fun setNumber(id: Long, number: Int)

    /**
     * Borra la vuelta y deja la numeración de la tanda consecutiva (V1…Vn, por orden). El índice único
     * `(sessionId, number)` impide renumerar «al vuelo», así que primero se pasan a negativo y luego se numeran.
     */
    @Transaction
    open suspend fun deleteAndRenumber(lapId: Long) {
        val sessionId = sessionIdOf(lapId)
        deleteById(lapId)
        if (sessionId != null) renumber(sessionId)
    }

    @Transaction
    open suspend fun renumber(sessionId: Long) {
        val ids = idsInOrder(sessionId)
        negateNumbers(sessionId)
        ids.forEachIndexed { index, id -> setNumber(id, index + 1) }
    }
}
