package com.nacho.kartlog.data.repository

import com.nacho.kartlog.data.local.dao.CircuitDao
import com.nacho.kartlog.data.local.dao.ImportDao
import com.nacho.kartlog.data.local.dao.LapDao
import com.nacho.kartlog.data.local.dao.ParticipantDao
import com.nacho.kartlog.data.local.dao.SessionDao
import com.nacho.kartlog.data.local.dao.StintDao
import com.nacho.kartlog.data.local.entity.CircuitEntity
import com.nacho.kartlog.data.local.entity.StintEntity
import com.nacho.kartlog.data.mapper.toDomain
import com.nacho.kartlog.data.mapper.toEntity
import com.nacho.kartlog.domain.gps.TrackStore
import com.nacho.kartlog.domain.importing.ImportPhotoStore
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.FinishLine
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
import com.nacho.kartlog.domain.repository.StintRepository
import com.nacho.kartlog.domain.model.Stint
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
        // Se parte de la fila actual para no perder la meta marcada.
        val current = dao.getById(id) ?: return false
        dao.update(current.copy(name = trimmed, lengthMeters = lengthMeters))
        return true
    }

    override suspend fun setFinishLine(id: Long, line: FinishLine?) {
        val current = dao.getById(id) ?: return
        dao.update(
            current.copy(
                finishLat = line?.lat,
                finishLon = line?.lon,
                finishBearingDeg = line?.bearingDeg,
                gateWidthM = line?.gateWidthM ?: FinishLine.DEFAULT_GATE_WIDTH_M,
            ),
        )
    }

    override suspend fun delete(id: Long): Boolean {
        if (dao.countSessions(id) > 0) return false
        dao.deleteById(id)
        return true
    }
}

class SessionRepositoryImpl(
    private val dao: SessionDao,
    private val tracks: TrackStore? = null,
    private val photos: ImportPhotoStore? = null,
    private val pending: com.nacho.kartlog.domain.PendingDeletions? = null,
) : SessionRepository {
    override fun observeSummaries(): Flow<List<SessionSummary>> =
        withHiddenSessions(dao.observeSummaries().map { rows -> rows.map { it.toDomain() } }) { list, hidden -> list.filter { it.session.id !in hidden } }

    override fun observeDetail(sessionId: Long): Flow<SessionDetail?> =
        withHiddenSessions(dao.observeById(sessionId).map { it?.toDomain() }) { detail, hidden -> detail?.takeIf { sessionId !in hidden } }

    /** Las tandas con borrado pendiente («Deshacer») no se ven aunque sigan en la base de datos. */
    private fun <T> withHiddenSessions(source: Flow<T>, filter: (T, Set<Long>) -> T): Flow<T> =
        if (pending == null) source
        else kotlinx.coroutines.flow.combine(source, pending.hiddenItems) { value, items ->
            filter(value, items.filter { it.kind == com.nacho.kartlog.domain.PendingDeletions.Kind.SESSION }.map { it.id }.toSet())
        }

    override suspend fun create(session: NewSession): Long = dao.insert(session.toEntity())

    override suspend fun update(sessionId: Long, session: NewSession) = dao.updateEditable(
        sessionId, session.circuitId, session.dateTime, session.type, session.kartNumber, session.ballastKg,
        session.ambientTempC, session.trackTempC, session.trackCondition, session.notes,
    )

    override suspend fun delete(sessionId: Long) {
        dao.deleteById(sessionId)
        tracks?.delete(sessionId) // el trazado GPS vive en un fichero y se borra con la tanda
        photos?.delete(sessionId) // y la foto de la hoja importada, también
    }
}

class LapRepositoryImpl(
    private val dao: LapDao,
    private val pending: com.nacho.kartlog.domain.PendingDeletions? = null,
) : LapRepository {
    override fun observeBySession(sessionId: Long): Flow<List<Lap>> = visible(dao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } })

    override fun observeAll(): Flow<List<Lap>> = visible(dao.observeAll().map { list -> list.map { it.toDomain() } })

    /** Sin las vueltas con borrado pendiente («Deshacer») ni las de una tanda con borrado pendiente. */
    private fun visible(source: Flow<List<Lap>>): Flow<List<Lap>> =
        if (pending == null) source
        else kotlinx.coroutines.flow.combine(source, pending.hiddenItems) { laps, items ->
            if (items.isEmpty()) laps else {
                val lapIds = items.filter { it.kind == com.nacho.kartlog.domain.PendingDeletions.Kind.LAP }.map { it.id }.toSet()
                val sessionIds = items.filter { it.kind == com.nacho.kartlog.domain.PendingDeletions.Kind.SESSION }.map { it.id }.toSet()
                val touched = laps.filter { it.id in lapIds }.map { it.sessionId }.toSet()
                val kept = laps.filter { it.id !in lapIds && it.sessionId !in sessionIds }
                // El borrado real renumera las vueltas de la tanda; se muestra ya así para que la lista no cambie al confirmarse.
                if (touched.isEmpty()) kept else kept.groupBy { it.sessionId }.flatMap { (sid, list) ->
                    if (sid in touched) list.sortedBy { it.number }.mapIndexed { i, lap -> lap.copy(number = i + 1) } else list
                }
            }
        }

    override suspend fun addManual(sessionId: Long, timeMs: Long): Long =
        dao.insertNext(sessionId, timeMs, LapSource.MANUAL)

    override suspend fun addAll(sessionId: Long, timesMs: List<Long>, source: LapSource, stintIds: List<Long?>?) =
        dao.insertAll(sessionId, timesMs, source, stintIds)

    override suspend fun updateTime(lapId: Long, timeMs: Long) = dao.updateTime(lapId, timeMs)

    override suspend fun delete(lapId: Long) = dao.deleteAndRenumber(lapId)
}

class StintRepositoryImpl(private val dao: StintDao) : StintRepository {
    override fun observeBySession(sessionId: Long): Flow<List<Stint>> =
        dao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun add(sessionId: Long, startLap: Int, endLap: Int, driverName: String?, pitMs: Long?): Long =
        dao.insert(StintEntity(sessionId = sessionId, startLap = startLap, endLap = endLap, driverName = driverName, pitMs = pitMs))
}

class ParticipantRepositoryImpl(private val dao: ParticipantDao) : ParticipantRepository {
    override fun observeBySession(sessionId: Long): Flow<List<Participant>> =
        dao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    override fun observeAllWithLaps(): Flow<List<com.nacho.kartlog.domain.analytics.RivalEntry>> =
        kotlinx.coroutines.flow.combine(dao.observeAll(), dao.observeAllLaps()) { participants, laps ->
            val byParticipant = laps.groupBy({ it.participantId }, { it.timeMs })
            participants.map { com.nacho.kartlog.domain.analytics.RivalEntry(it.toDomain(), byParticipant[it.id].orEmpty()) }
        }
}

class ImportRepositoryImpl(private val dao: ImportDao, private val photos: ImportPhotoStore? = null) : ImportRepository {
    override suspend fun importSession(request: ImportRequest): Long = dao.importSession(request)

    override suspend fun clearRivalData() {
        dao.clearRivalData()
        photos?.deleteAll() // la foto de la hoja contiene los datos de los demás pilotos
    }
}
