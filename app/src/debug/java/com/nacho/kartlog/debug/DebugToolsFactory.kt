package com.nacho.kartlog.debug

import android.content.Context
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.gps.TrackStore
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository

/** Variante debug: devuelve el sembrador de datos de prueba. */
object DebugToolsFactory {
    fun create(
        context: Context,
        database: KartLogDatabase,
        circuits: CircuitRepository,
        sessions: SessionRepository,
        laps: LapRepository,
        tracks: TrackStore,
    ): DebugTools = DebugSeeder(database, circuits, sessions, laps, SampleImages(context), tracks)
}
