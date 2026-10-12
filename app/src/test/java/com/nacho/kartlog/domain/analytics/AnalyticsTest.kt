package com.nacho.kartlog.domain.analytics

import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.analytics.ConsistencyTrend
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsTest {
    private val zuera = Circuit(1, "Zuera", 1100)
    private val alonso = Circuit(2, "Alonso", 1400)

    private fun summary(
        id: Long,
        circuit: Circuit = zuera,
        date: Long = id * 1000,
        kart: Int? = null,
        condition: TrackCondition = TrackCondition.DRY,
    ) = SessionSummary(
        session = Session(id, circuit.id, date, SessionType.FREE, kart, null, null, null, condition, ""),
        circuit = circuit,
        lapCount = 0,
        bestLapMs = null,
    )

    private var lapId = 0L
    private fun laps(sessionId: Long, vararg times: Long) =
        times.mapIndexed { i, t -> Lap(++lapId, sessionId, i + 1, t, LapSource.MANUAL) }

    // ---- Media limpia ----

    @Test
    fun cleanAverage_discardsLapsAbove107PercentOfBest() {
        // mejor 50.000 -> límite 53.500; 60.000 (salida de boxes) queda fuera.
        val times = listOf(60_000L, 50_000L, 51_000L, 52_000L)
        assertEquals(listOf(50_000L, 51_000L, 52_000L), Analytics.cleanLaps(times))
        assertEquals(51_000L, Analytics.cleanAverageMs(times))
    }

    @Test
    fun cleanLap_exactly107PercentIsIncluded_oneMsMoreIsNot() {
        val best = 100_000L
        assertEquals(listOf(100_000L, 107_000L), Analytics.cleanLaps(listOf(best, 107_000L)))
        assertEquals(listOf(100_000L), Analytics.cleanLaps(listOf(best, 107_001L)))
    }

    @Test
    fun cleanAverage_emptySession_isNull() {
        assertNull(Analytics.cleanAverageMs(emptyList()))
        assertTrue(Analytics.cleanLaps(emptyList()).isEmpty())
    }

    @Test
    fun cleanAverage_singleLap_isThatLap() {
        assertEquals(48_532L, Analytics.cleanAverageMs(listOf(48_532L)))
    }

    // ---- Consistencia ----

    @Test
    fun consistency_isPopulationStdDevOfCleanLaps() {
        // limpias: 50.000 y 52.000 -> media 51.000, desv. típica 1.000 (el 70.000 no cuenta).
        assertEquals(1_000.0, Analytics.consistencyMs(listOf(50_000, 52_000, 70_000))!!, 1e-9)
    }

    @Test
    fun consistency_singleLapIsZero_emptyIsNull() {
        assertEquals(0.0, Analytics.consistencyMs(listOf(50_000))!!, 1e-9)
        assertNull(Analytics.consistencyMs(emptyList()))
    }

    // ---- Récord y delta ----

    @Test
    fun record_andDelta_acrossSessionsOfACircuit() {
        val summaries = listOf(summary(1), summary(2), summary(3, circuit = alonso))
        val all = laps(1, 50_000, 51_000) + laps(2, 49_500, 55_000) + laps(3, 70_000)
        val snapshot = Analytics.compute(summaries, all)

        assertEquals(49_500L, snapshot.circuits.getValue(zuera.id).recordMs)
        assertEquals(70_000L, snapshot.circuits.getValue(alonso.id).recordMs) // otro circuito, otro récord

        val s1 = snapshot.session(1)!!
        assertEquals(500L, s1.deltaToRecordMs)
        assertFalse(s1.isRecord)

        val s2 = snapshot.session(2)!!
        assertEquals(0L, s2.deltaToRecordMs)
        assertTrue(s2.isRecord)
    }

    @Test
    fun emptySession_hasNoDelta_andDoesNotAffectRecordOrAverages() {
        val summaries = listOf(summary(1), summary(2))
        val snapshot = Analytics.compute(summaries, laps(1, 50_000, 51_000))
        val empty = snapshot.session(2)!!
        assertNull(empty.bestMs)
        assertNull(empty.deltaToRecordMs)
        assertNull(empty.cleanAverageMs)
        assertFalse(empty.isRecord)
        val circuit = snapshot.circuits.getValue(zuera.id)
        assertEquals(50_000L, circuit.recordMs)
        assertEquals(2, circuit.totalSessions) // cuenta para bloquear borrados...
        assertEquals(1, circuit.sessionsWithLaps) // ...pero no para análisis
        assertEquals(1, circuit.evolution.size)
    }

    @Test
    fun singleLapSession_isItsOwnRecord() {
        val snapshot = Analytics.compute(listOf(summary(1)), laps(1, 48_000))
        val s = snapshot.session(1)!!
        assertTrue(s.isRecord)
        assertEquals(48_000L, s.cleanAverageMs)
        assertEquals(0.0, s.consistencyMs!!, 1e-9)
    }

    @Test
    fun circuitWithoutAnyLaps_hasNoRecordOrAverage() {
        val snapshot = Analytics.compute(listOf(summary(1)), emptyList())
        val c = snapshot.circuits.getValue(zuera.id)
        assertNull(c.recordMs)
        assertNull(c.historicCleanAverageMs)
        assertTrue(c.karts.isEmpty())
        assertTrue(c.conditions.isEmpty())
    }

    // ---- Histórico, karts y condiciones ----

    @Test
    fun historicCleanAverage_poolsCleanLapsOfAllSessions() {
        // s1 limpias: 50.000, 51.000 (60.000 fuera). s2 limpias: 54.000, 55.000.
        val summaries = listOf(summary(1), summary(2))
        val all = laps(1, 60_000, 50_000, 51_000) + laps(2, 54_000, 55_000)
        val c = Analytics.compute(summaries, all).circuits.getValue(zuera.id)
        assertEquals(52_500L, c.historicCleanAverageMs) // (50+51+54+55)/4
    }

    @Test
    fun kartRanking_sortsByBest_ignoresSessionsWithoutKart() {
        val summaries = listOf(
            summary(1, kart = 7), summary(2, kart = 12), summary(3, kart = 7), summary(4, kart = null),
        )
        val all = laps(1, 51_000) + laps(2, 50_000) + laps(3, 49_000, 52_000) + laps(4, 40_000)
        val karts = Analytics.compute(summaries, all).circuits.getValue(zuera.id).karts
        assertEquals(listOf(7, 12), karts.map { it.kartNumber })
        assertEquals(49_000L, karts[0].bestMs)
        assertEquals(2, karts[0].sessionCount)
        assertEquals(50_000L, karts[1].bestMs)
    }

    @Test
    fun conditions_onlyGroupsWithData_averageOfSessionBests() {
        val summaries = listOf(
            summary(1, condition = TrackCondition.DRY),
            summary(2, condition = TrackCondition.DRY),
            summary(3, condition = TrackCondition.WET),
            summary(4, condition = TrackCondition.DAMP), // sin vueltas: no entra
        )
        val all = laps(1, 50_000) + laps(2, 52_000) + laps(3, 58_000)
        val conditions = Analytics.compute(summaries, all).circuits.getValue(zuera.id).conditions
        assertEquals(listOf(TrackCondition.DRY, TrackCondition.WET), conditions.map { it.condition })
        assertEquals(51_000L, conditions[0].averageBestMs)
        assertEquals(2, conditions[0].sessionCount)
        assertEquals(58_000L, conditions[1].averageBestMs)
    }

    @Test
    fun evolution_isOrderedByDate() {
        val summaries = listOf(summary(1, date = 3_000), summary(2, date = 1_000), summary(3, date = 2_000))
        val all = laps(1, 50_000) + laps(2, 52_000) + laps(3, 51_000)
        val evolution = Analytics.compute(summaries, all).circuits.getValue(zuera.id).evolution
        assertEquals(listOf(2L, 3L, 1L), evolution.map { it.sessionId })
    }

    @Test
    fun historicConsistency_isMeanOfSessionConsistencies_ignoringSessionsWithLessThanTwoCleanLaps() {
        // s1 limpias 50.000/52.000 -> ±1.000; s2 limpias 60.000/64.000 -> ±2.000; s3 una sola vuelta -> no cuenta.
        val summaries = listOf(summary(1), summary(2), summary(3))
        val all = laps(1, 50_000, 52_000) + laps(2, 60_000, 64_000) + laps(3, 48_000)
        val c = Analytics.compute(summaries, all).circuits.getValue(zuera.id)
        assertEquals(1_500.0, c.historicConsistencyMs!!, 1e-9)
        assertFalse(Analytics.hasMeaningfulConsistency(listOf(48_000)))
        assertTrue(Analytics.hasMeaningfulConsistency(listOf(50_000, 52_000)))
        assertNull(Analytics.compute(listOf(summary(1)), laps(1, 48_000)).circuits.getValue(zuera.id).historicConsistencyMs)
    }

    @Test
    fun circuitGpsAccuracy_averagesTheSessionsThatHaveBothGpsAndOfficialLaps() {
        val s1 = SessionSummary(Session(1, 1, 1_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 4, 58_000)
        val s2 = SessionSummary(Session(2, 1, 2_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 4, 58_000)
        val s3 = SessionSummary(Session(3, 1, 3_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 2, 58_000)
        fun lap(id: Long, session: Long, n: Int, ms: Long, src: com.nacho.kartlog.domain.model.LapSource) =
            Lap(id, session, n, ms, src)
        val gps = com.nacho.kartlog.domain.model.LapSource.GPS
        val off = com.nacho.kartlog.domain.model.LapSource.IMPORTED
        val laps = listOf(
            // Sesión 1: error medio 50 ms (GPS 1-2, oficial 3-4).
            lap(1, 1, 1, 58_050, gps), lap(2, 1, 2, 58_150, gps), lap(3, 1, 3, 58_000, off), lap(4, 1, 4, 58_100, off),
            // Sesión 2: error medio 150 ms.
            lap(5, 2, 1, 58_150, gps), lap(6, 2, 2, 58_050, gps), lap(7, 2, 3, 58_000, off), lap(8, 2, 4, 58_200, off),
            // Sesión 3: solo vueltas manuales, no cuenta.
            lap(9, 3, 1, 58_000, com.nacho.kartlog.domain.model.LapSource.MANUAL), lap(10, 3, 2, 58_100, com.nacho.kartlog.domain.model.LapSource.MANUAL),
        )
        val snapshot = Analytics.compute(listOf(s1, s2, s3), laps)
        val stats = snapshot.circuits.getValue(zuera.id)
        assertEquals(2, stats.gpsAccuracySessions)
        assertEquals(100.0, stats.gpsAccuracyMs!!, 1e-9)
        assertEquals(50.0, snapshot.session(1)!!.gpsAccuracy!!.meanAbsErrorMs!!, 1e-9)
        assertNull(snapshot.session(3)!!.gpsAccuracy)
    }

    @Test
    fun consistencySeriesAndTrend_followTheSessionsInDateOrder() {
        // Cuatro tandas cada vez más regulares: desviaciones ~0,5 s, 0,35 s, 0,2 s y 0,1 s.
        fun summary(id: Long, date: Long) = SessionSummary(Session(id, 1, date, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 4, 58_000)
        fun laps(session: Long, base: Long, spread: Long) = listOf(base - spread, base + spread, base - spread, base + spread)
            .mapIndexed { i, ms -> Lap(session * 10 + i, session, i + 1, ms, LapSource.MANUAL) }
        val summaries = listOf(summary(1, 1_000), summary(2, 2_000), summary(3, 3_000), summary(4, 4_000))
        val all = laps(1, 58_000, 500) + laps(2, 58_000, 350) + laps(3, 58_000, 200) + laps(4, 58_000, 100)
        // Se mezclan en desorden: la serie sale por fecha.
        val stats = Analytics.compute(summaries.shuffled(), all.shuffled()).circuits.getValue(zuera.id)
        assertEquals(listOf(1L, 2L, 3L, 4L), stats.consistencySeries.map { it.sessionId })
        assertEquals(listOf(500.0, 350.0, 200.0, 100.0), stats.consistencySeries.map { it.consistencyMs })
        assertEquals(-135.0, stats.consistencyTrendMsPerSession!!, 1e-9) // mínimos cuadrados de 500, 350, 200, 100
        assertEquals(ConsistencyTrend.IMPROVING, stats.consistencyTrend)
    }

    @Test
    fun trendThresholds_andNotEnoughSessions() {
        assertEquals(ConsistencyTrend.IMPROVING, ConsistencyTrend.of(-10.0))
        assertEquals(ConsistencyTrend.STABLE, ConsistencyTrend.of(-9.9))
        assertEquals(ConsistencyTrend.STABLE, ConsistencyTrend.of(9.9))
        assertEquals(ConsistencyTrend.WORSENING, ConsistencyTrend.of(10.0))
        assertEquals(0.0, Analytics.slope(listOf(5.0)), 0.0)
        assertEquals(2.0, Analytics.slope(listOf(1.0, 3.0, 5.0, 7.0)), 1e-9)
        // Con solo dos tandas hay serie pero no tendencia.
        val s1 = SessionSummary(Session(1, 1, 1_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 3, 58_000)
        val s2 = SessionSummary(Session(2, 1, 2_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 3, 58_000)
        val laps = listOf(58_000L, 58_300L, 58_100L).mapIndexed { i, ms -> listOf(1L, 2L).map { Lap(it * 10 + i, it, i + 1, ms, LapSource.MANUAL) } }.flatten()
        val stats = Analytics.compute(listOf(s1, s2), laps).circuits.getValue(zuera.id)
        assertEquals(2, stats.consistencySeries.size)
        assertNull(stats.consistencyTrendMsPerSession)
        assertNull(stats.consistencyTrend)
    }

    @Test
    fun improvementGap_isTheCleanAverageMinusTheRecord() {
        val s1 = SessionSummary(Session(1, 1, 1_000, SessionType.FREE, 7, null, null, null, TrackCondition.DRY, ""), zuera, 3, 57_500)
        val laps = listOf(57_500L, 58_000L, 58_500L).mapIndexed { i, ms -> Lap(i + 1L, 1, i + 1, ms, LapSource.MANUAL) }
        val stats = Analytics.compute(listOf(s1), laps).circuits.getValue(zuera.id)
        assertEquals(57_500L, stats.recordMs)
        assertEquals(58_000L, stats.historicCleanAverageMs)
        assertEquals(500L, stats.improvementGapMs)
        // Sin vueltas no hay hueco.
        assertNull(Analytics.emptyCircuitStats(zuera).improvementGapMs)
    }

    @Test
    fun sessionStats_flagGpsLaps_andCountDistinctStints() {
        val gps = listOf(
            Lap(1, 1, 1, 58_000, LapSource.GPS, stintId = 10),
            Lap(2, 1, 2, 58_100, LapSource.GPS, stintId = 10),
            Lap(3, 1, 3, 58_200, LapSource.GPS, stintId = 11),
        )
        val manual = laps(2, 59_000, 59_100)
        val snapshot = Analytics.compute(listOf(summary(1), summary(2)), gps + manual)
        val a = snapshot.sessions.first { it.summary.session.id == 1L }
        val b = snapshot.sessions.first { it.summary.session.id == 2L }
        assertTrue(a.hasGpsLaps)
        assertEquals(2, a.stintCount)
        assertFalse(b.hasGpsLaps)
        assertEquals(0, b.stintCount)
    }

    @Test
    fun oneRoundingRule_roundsHalfUp_andOfficialNoteOnlyForOneMillisecond() {
        assertEquals(81_158L, Analytics.averageMs(listOf(80_000L, 82_315L)))
        assertEquals(81_157L, Analytics.officialAverageNote(81_157, listOf(80_000L, 82_315L)))
        assertEquals(null, Analytics.officialAverageNote(81_158, listOf(80_000L, 82_315L))) // igual: nada que enseñar
        assertEquals(null, Analytics.officialAverageNote(81_100, listOf(80_000L, 82_315L))) // otra definición de media: sin nota
        assertEquals(null, Analytics.officialAverageNote(81_157, emptyList()))
    }
}
