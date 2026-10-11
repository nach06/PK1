package com.nacho.kartlog.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.data.repository.CircuitRepositoryImpl
import com.nacho.kartlog.data.repository.ImportRepositoryImpl
import com.nacho.kartlog.data.repository.LapRepositoryImpl
import com.nacho.kartlog.data.repository.ParticipantRepositoryImpl
import com.nacho.kartlog.data.repository.SessionRepositoryImpl
import com.nacho.kartlog.domain.model.ImportRequest
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.NewSession
import com.nacho.kartlog.domain.model.ParticipantImport
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: KartLogDatabase
    private lateinit var importer: ImportRepositoryImpl
    private lateinit var sessions: SessionRepositoryImpl
    private lateinit var laps: LapRepositoryImpl
    private lateinit var participants: ParticipantRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(KartLogDatabase.NAME)
        db = KartLogDatabase.create(context)
        importer = ImportRepositoryImpl(db.importDao())
        sessions = SessionRepositoryImpl(db.sessionDao())
        laps = LapRepositoryImpl(db.lapDao())
        participants = ParticipantRepositoryImpl(db.participantDao())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(KartLogDatabase.NAME)
    }

    /** Room no permite consultas síncronas en el hilo principal: se hacen en IO. */
    private suspend fun count(sql: String): Int = withContext(Dispatchers.IO) {
        db.query(sql, null).use { it.moveToFirst(); it.getInt(0) }
    }

    private fun run(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private fun newSession(circuitId: Long) = NewSession(
        circuitId, 1_759_522_560_000, SessionType.FREE, 28, null, null, null, TrackCondition.DRY, "",
        label = "Sesión 46 GP3 DIEGO", durationMin = 10, importedRaw = "TEXTO ORIGINAL",
    )

    private val me = ParticipantImport(5, "Nacho", 28, 79_074, 81_157, 6)
    private val deyvis = ParticipantImport(1, "Deyvis", 26, 76_515, 78_190, 6, listOf(80_938, 77_947, 78_361, 77_091, 78_289, 76_515))

    @Test
    fun newSession_storesMyLapsAsImported_participantsAndOthersLaps() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Asupark", null)
        val id = importer.importSession(
            ImportRequest(newSession(circuit), null, listOf(83_488, 80_902, 82_209, 81_767, 79_074, 79_505), me, listOf(deyvis)),
        )

        val detail = sessions.observeDetail(id).first()!!
        assertEquals("Sesión 46 GP3 DIEGO", detail.session.label)
        assertEquals(10, detail.session.durationMin)
        val myLaps = laps.observeBySession(id).first()
        assertEquals((1..6).toList(), myLaps.map { it.number })
        assertTrue(myLaps.all { it.source == LapSource.IMPORTED })
        assertEquals(79_074L, myLaps.minOf { it.timeMs })

        val classification = participants.observeBySession(id).first()
        assertEquals(listOf("Deyvis", "Nacho"), classification.map { it.name }) // por posición
        assertEquals(listOf(false, true), classification.map { it.isMe })
        // Solo los demás tienen vueltas en participant_laps.
        val deyvisId = classification.first { !it.isMe }.id
        assertEquals(listOf(80_938L, 77_947L, 78_361L, 77_091L, 78_289L, 76_515L), db.participantDao().lapsOf(deyvisId).map { it.timeMs })
        assertEquals(0, db.participantDao().lapsOf(classification.first { it.isMe }.id).size)
    }

    @Test
    fun existingSession_appendsLapsAfterTheExistingOnes_andReplacesClassification() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Asupark", null)
        val id = sessions.create(newSession(circuit))
        laps.addManual(id, 90_000)
        laps.addManual(id, 91_000)

        importer.importSession(ImportRequest(null, id, listOf(80_000, 81_000), me, listOf(deyvis)))
        assertEquals(listOf(1, 2, 3, 4), laps.observeBySession(id).first().map { it.number })
        assertEquals(listOf(LapSource.MANUAL, LapSource.MANUAL, LapSource.IMPORTED, LapSource.IMPORTED), laps.observeBySession(id).first().map { it.source })
        assertEquals(2, participants.observeBySession(id).first().size)

        // Importar otra vez en la misma tanda sustituye la clasificación (no la duplica).
        importer.importSession(ImportRequest(null, id, listOf(79_000), me, listOf(deyvis)))
        assertEquals(2, participants.observeBySession(id).first().size)
        assertEquals(5, laps.observeBySession(id).first().size)
    }

    @Test
    fun failedImport_leavesNothingBehind() = run {
        // Sesión inexistente: la FK de la primera vuelta falla y toda la transacción se revierte.
        val result = runCatching {
            importer.importSession(ImportRequest(null, 999, listOf(80_000), me, listOf(deyvis)))
        }
        assertTrue(result.isFailure)
        assertEquals(0, count("SELECT COUNT(*) FROM participants"))
        assertEquals(0, count("SELECT COUNT(*) FROM laps"))
    }

    @Test
    fun deletingTheSession_cascadesToParticipantsAndTheirLaps() = run {
        val circuit = CircuitRepositoryImpl(db.circuitDao()).add("Asupark", null)
        val id = importer.importSession(ImportRequest(newSession(circuit), null, listOf(80_000), me, listOf(deyvis)))
        sessions.delete(id)
        assertEquals(
            0,
            count("SELECT (SELECT COUNT(*) FROM participants) + (SELECT COUNT(*) FROM participant_laps) + (SELECT COUNT(*) FROM laps)"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun request_needsExactlyOneTarget() {
        ImportRequest(null, null, emptyList(), null, emptyList())
    }
}
