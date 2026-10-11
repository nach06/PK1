package com.nacho.kartlog.domain.util

import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AllSessionsCsvTest {
    private fun epoch(y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun summary(id: Long, name: String, date: Long, kart: Int?, ballast: Float?, amb: Float?, track: Float?, cond: TrackCondition) =
        SessionSummary(
            Session(id, 1, date, SessionType.RACE, kart, ballast, amb, track, cond, ""),
            Circuit(1, name, null), 0, null,
        )

    @Test
    fun oneRowPerLap_sortedByDate_withNumericFormatsAndEmptyFields() {
        val older = summary(1, "Zuera", epoch(2026, 3, 5, 10, 0), kart = 7, ballast = 4f, amb = 22.5f, track = 31f, cond = TrackCondition.DRY)
        val newer = summary(2, "Zuera", epoch(2026, 4, 1, 18, 30), kart = null, ballast = null, amb = null, track = null, cond = TrackCondition.WET)
        val empty = summary(3, "Zuera", epoch(2026, 5, 1, 9, 0), 1, null, null, null, TrackCondition.DRY) // sin vueltas
        val laps = listOf(
            Lap(1, 2, 1, 70_000, LapSource.MANUAL),
            Lap(2, 1, 2, 49_000, LapSource.MANUAL),
            Lap(3, 1, 1, 48_532, LapSource.MANUAL),
        )
        val csv = AllSessionsCsv.build(listOf(newer, empty, older), laps, ZoneOffset.UTC)
        assertEquals(
            listOf(
                "fecha;circuito;tipo;kart;lastre_kg;temp_ambiente;temp_pista;estado_pista;vuelta;tiempo;tiempo_ms;origen",
                "2026-03-05 10:00;Zuera;RACE;7;4;22.5;31;DRY;1;0:48.532;48532;MANUAL",
                "2026-03-05 10:00;Zuera;RACE;7;4;22.5;31;DRY;2;0:49.000;49000;MANUAL",
                "2026-04-01 18:30;Zuera;RACE;;;;;WET;1;1:10.000;70000;MANUAL",
            ).joinToString("\n", postfix = "\n"),
            csv,
        )
    }

    @Test
    fun circuitNamesWithSeparatorsOrQuotesAreQuoted() {
        val s = summary(1, "Kart; \"Norte\"", epoch(2026, 1, 1, 0, 0), null, null, null, null, TrackCondition.DRY)
        val csv = AllSessionsCsv.build(listOf(s), listOf(Lap(1, 1, 1, 50_000, LapSource.MANUAL)), ZoneOffset.UTC)
        assertEquals("2026-01-01 00:00;\"Kart; \"\"Norte\"\"\";RACE;;;;;DRY;1;0:50.000;50000;MANUAL", csv.lines()[1])
    }

    @Test
    fun noSessions_onlyHeader() {
        assertEquals(AllSessionsCsv.HEADER + "\n", AllSessionsCsv.build(emptyList(), emptyList()))
    }
}
