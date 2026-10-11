package com.nacho.kartlog.data.repository

import com.nacho.kartlog.data.local.dao.CircuitDao
import com.nacho.kartlog.data.local.dao.ImportDao
import com.nacho.kartlog.data.local.dao.LapDao
import com.nacho.kartlog.data.local.dao.ParticipantDao
import com.nacho.kartlog.data.local.dao.SessionDao
import com.nacho.kartlog.data.local.entity.CircuitEntity
import com.nacho.kartlog.data.mapper.toDomain
import com.nacho.kartlog.data.mapper.toEntity
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.Participant
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.repository.ImportRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.ParticipantRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CircuitRepositoryImpl(private val dao: CircuitDao) : CircuitRepository {
    override fun observeAll(): Flow<List<Circuit>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: Long): Circuit? = dao.getById(id)?.toDomain()

    override suspend fun add(name: String, lengthMeters: Int?): Long {
        val trimmed = name.trim()
        val id = dao.insert(CircuitEntity(name = trimmed, lengthMeters = lengthMeters))
        return if (id != -1L) id else requireNotNull(dao.getByName(trimmed)).id
    }

    override suspend fun update(id: Long, name: String, lengthMeters: Int?): Boolean {
        val trimmed = name.trim()
        val clash = dao.getByName(trimmed)
        if (clash != null && clash.id != id) return false
        dao.update(CircuitEntity(id = id, name = trimmed, lengthMeters = lengthMeters))
        return true
    }

    override suspend fun delete(id: Long): Boolean {
        if (dao.countSessions(id) > 0) return false
        dao.deleteById(id)
        return true
    }
}

class SessionRepositoryImpl(private val dao: SessionDao) : SessionRepository {
    override fun observeSummaries(): Flow<List<SessionSummary>> =
        dao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeDetail(sessionId: Long): Flow<SessionDetail?> =
        dao.observeById(sessionId).map { it?.toDomain() }

    override suspend fun create(session: NewSession): Long = dao.insert(session.toEntity())

    override suspend fun delete(sessionId: Long) = dao.deleteById(sessionId)
}

class LapRepositoryImpl(private val dao: LapDao) : LapRepository {
    override fun observeBySession(sessionId: Long): Flow<List<Lap>> =
        dao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Lap>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addManual(sessionId: Long, timeMs: Long): Long =
        dao.insertNext(sessionId, timeMs, LapSource.MANUAL)

    override suspend fun updateTime(lapId: Long, timeMs: Long) = dao.updateTime(lapId, timeMs)

    override suspend fun delete(lapId: Long) = dao.deleteById(lapId)
}

class ParticipantRepositoryImpl(private val dao: ParticipantDao) : ParticipantRepository {
    override fun observeBySession(sessionId: Long): Flow<List<Participant>> =
        dao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }
}

class ImportRepositoryImpl(private val dao: ImportDao) : ImportRepository {
    override suspend fun importSession(request: ImportRequest): Long = dao.importSession(request)
}
