package com.nacho.kartlog.domain.repository

import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.Participant
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.model.SessionSummary
import kotlinx.coroutines.flow.Flow

interface CircuitRepository {
    fun observeAll(): Flow<List<Circuit>>

    suspend fun getById(id: Long): Circuit?

    /** Crea el circuito y devuelve su id. Si ya existe uno con ese nombre, devuelve el existente. */
    suspend fun add(name: String, lengthMeters: Int?): Long

    /** Edita nombre y longitud. Devuelve `false` si el nombre ya lo usa otro circuito. */
    suspend fun update(id: Long, name: String, lengthMeters: Int?): Boolean

    /** Borra el circuito. Devuelve `false` (sin borrar nada) si tiene tandas. */
    suspend fun delete(id: Long): Boolean
}

interface SessionRepository {
    /** Sesiones con circuito y estadísticas, más recientes primero. */
    fun observeSummaries(): Flow<List<SessionSummary>>

    /** Emite `null` si la sesión no existe (por ejemplo, tras borrarla). */
    fun observeDetail(sessionId: Long): Flow<SessionDetail?>

    suspend fun create(session: NewSession): Long

    suspend fun delete(sessionId: Long)
}

interface LapRepository {
    /** Vueltas de la sesión ordenadas por número. */
    fun observeBySession(sessionId: Long): Flow<List<Lap>>

    /** Todas las vueltas de todas las tandas (para análisis). */
    fun observeAll(): Flow<List<Lap>>

    /** Añade una vuelta manual con el siguiente número libre de la sesión. */
    suspend fun addManual(sessionId: Long, timeMs: Long): Long

    suspend fun updateTime(lapId: Long, timeMs: Long)

    suspend fun delete(lapId: Long)
}

interface ParticipantRepository {
    /** Clasificación de la sesión importada, por posición (los sin posición, al final). */
    fun observeBySession(sessionId: Long): Flow<List<Participant>>
}

interface ImportRepository {
    /**
     * Guarda en una sola transacción la sesión (o las vueltas añadidas a la existente), las vueltas
     * propias con origen IMPORTED y los participantes con las vueltas de los demás. Devuelve el id de la sesión.
     */
    suspend fun importSession(request: ImportRequest): Long
}
