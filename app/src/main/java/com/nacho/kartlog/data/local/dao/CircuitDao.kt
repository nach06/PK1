package com.nacho.kartlog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nacho.kartlog.data.local.entity.CircuitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CircuitDao {
    /** Devuelve -1 si el nombre ya existe (índice único). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(circuit: CircuitEntity): Long

    @Query("SELECT * FROM circuits ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CircuitEntity>>

    @Query("SELECT * FROM circuits WHERE id = :id")
    suspend fun getById(id: Long): CircuitEntity?

    @Update
    suspend fun update(circuit: CircuitEntity)

    @Query("DELETE FROM circuits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM sessions WHERE circuitId = :id")
    suspend fun countSessions(id: Long): Int

    @Query("SELECT * FROM circuits WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CircuitEntity?
}
