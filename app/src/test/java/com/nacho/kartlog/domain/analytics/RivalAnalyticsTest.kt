package com.nacho.kartlog.domain.analytics

import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.Participant
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sprint 8, C1/C2/C4/C6: análisis de rivales en dominio puro. */
class RivalAnalyticsTest {
    private val zuera = Circuit(1, "Zuera", 1100)

    private fun session(id: Long, track: Float? = null, ambient: Float? = null, ballast: Float? = null) =
        SessionSummary(Session(id, zuera.id, id * 1000, SessionType.FREE, 7, ballast, ambient, track, TrackCondition.DRY, ""), zuera, 0, null)

    private var lapId = 0L
    private fun snapshot(vararg sessions: Pair<SessionSummary, List<Long>>): List<SessionStats> {
        val laps = sessions.flatMap { (s, times) -> times.mapIndexed { i, t -> Lap(++lapId, s.session.id, i + 1, t, LapSource.MANUAL) } }
        return Analytics.compute(sessions.map { it.first }, laps).sessions
    }

    private var pid = 0L
    private fun rival(sessionId: Long, name: String?, vararg times: Long, isMe: Boolean = false) =
        RivalEntry(Participant(++pid, sessionId, null, name, null, isMe, times.minOrNull(), null, times.size), times.toList())

    @Test
    fun groupsTheSameDriverAcrossSessions_ignoringAccentsCaseAndPunctuation_andNeverListsMe() {
        val stats = snapshot(session(1) to listOf(60_000, 61_000), session(2) to listOf(60_000, 60_500))
        val entries = listOf(
            rival(1, "Carlos Ruiz", 59_000, 60_000),
            rival(2, "CARLOS  RUIZ.", 59_500, 60_500),
            rival(1, "Yo", 60_000, 61_000, isMe = true),
            rival(1, null, 58_000),
        )
        val rivals = RivalAnalytics.rivals(stats, entries)
        assertEquals(1, rivals.size)
        assertEquals(2, rivals[0].sessions)
        assertEquals(59_000L, rivals[0].bestMs)
    }

    @Test
    fun pace_isMyCleanAverageMinusTheirs_negativeWhenIAmFaster() {
        val stats = snapshot(session(1) to listOf(60_000, 60_000), session(2) to listOf(61_000, 61_000))
        val entries = listOf(rival(1, "Ana", 61_000, 61_000), rival(2, "Ana", 60_000, 60_000))
        val r = RivalAnalytics.rivals(stats, entries).single()
        assertEquals(listOf(-1_000L, 1_000L), r.encounters.map { it.avgDiffMs })
        assertEquals(0L, r.meanAvgDiffMs)
        val h = RivalAnalytics.headToHead(r)
        assertEquals(1, h.winsBest); assertEquals(1, h.lossesBest)
        assertEquals(1, h.winsAvg); assertEquals(1, h.lossesAvg)
    }

    @Test
    fun bestStreak_countsConsecutiveBestLapWinsInChronologicalOrder() {
        val stats = snapshot(*(1L..6L).map { session(it) to listOf(60_000L, 60_000L) }.toTypedArray())
        // Gano 1,2,3, pierdo 4, gano 5, pierdo 6 → racha 3.
        val theirs = listOf(61_000L, 61_000L, 61_000L, 59_000L, 61_000L, 59_000L)
        val entries = theirs.mapIndexed { i, t -> rival(i + 1L, "Bea", t, t) }
        val h = RivalAnalytics.headToHead(RivalAnalytics.rivals(stats, entries).single())
        assertEquals(3, h.bestStreak)
        assertEquals(4, h.winsBest); assertEquals(2, h.lossesBest)
    }

    @Test
    fun aTieIsNeitherAWinNorALoss() {
        val stats = snapshot(session(1) to listOf(60_000, 60_000))
        val h = RivalAnalytics.headToHead(RivalAnalytics.rivals(stats, listOf(rival(1, "Eva", 60_000, 60_000))).single())
        assertEquals(0, h.winsBest); assertEquals(0, h.lossesBest); assertEquals(0, h.bestStreak)
    }

    @Test
    fun aliases_unifyTwoSpellingsIntoOneRival_andMergeIsTransitiveAndIdempotent() {
        val stats = snapshot(session(1) to listOf(60_000), session(2) to listOf(60_000))
        val entries = listOf(rival(1, "Carlos Ruiz", 59_000), rival(2, "C. Ruiz", 59_500))
        assertEquals(2, RivalAnalytics.rivals(stats, entries).size)
        val a = RivalAnalytics.merge(emptyMap(), from = "cruiz", into = "carlosruiz")
        val merged = RivalAnalytics.rivals(stats, entries, a).single()
        assertEquals(2, merged.sessions)
        assertEquals("carlosruiz", merged.key)
        assertEquals("Carlos Ruiz", merged.displayName) // se muestra el nombre más largo
        // Unir otro alias al ya unido lo lleva al principal; unirse a sí mismo no cambia nada.
        val b = RivalAnalytics.merge(a, from = "cr", into = "cruiz")
        assertEquals("carlosruiz", b["cr"])
        assertEquals(a, RivalAnalytics.merge(a, "cruiz", "carlosruiz"))
    }

    @Test
    fun rivalsInSessionsWithoutMyLaps_areNotListed() {
        val stats = snapshot(session(1) to emptyList())
        assertTrue(RivalAnalytics.rivals(stats, listOf(rival(1, "Ana", 60_000))).isEmpty())
    }

    // ---- Condiciones (C4) ----

    @Test
    fun conditions_needAtLeastEightSessions() {
        val seven = snapshot(*(1L..7L).map { session(it, track = 20f + it) to listOf(60_000L + it * 100, 60_000L + it * 100) }.toTypedArray())
        assertNull(RivalAnalytics.conditionCorrelations(seven))
        val eight = snapshot(*(1L..8L).map { session(it, track = 20f + it) to listOf(60_000L + it * 100, 60_000L + it * 100) }.toTypedArray())
        assertNotNull(RivalAnalytics.conditionCorrelations(eight))
    }

    @Test
    fun conditions_hotterTrackSlowerLaps_givesAPositiveCorrelationAndSkipsVariablesWithoutData() {
        val stats = snapshot(*(1L..9L).map { session(it, track = 15f + it * 2) to listOf(60_000L + it * 200, 60_000L + it * 200) }.toTypedArray())
        val c = RivalAnalytics.conditionCorrelations(stats)!!
        assertEquals(listOf(RivalAnalytics.Variable.TRACK_TEMP), c.map { it.variable }) // sin ambiente ni lastre
        assertTrue("r = ${c[0].r}", c[0].r > 0.99)
        assertEquals(9, c[0].n)
    }

    @Test
    fun conditions_aConstantVariableHasNoCorrelation() {
        val stats = snapshot(*(1L..9L).map { session(it, track = 25f) to listOf(60_000L + it * 200, 60_000L + it * 200) }.toTypedArray())
        assertTrue(RivalAnalytics.conditionCorrelations(stats)!!.isEmpty())
    }
}
