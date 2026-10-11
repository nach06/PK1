package com.nacho.kartlog.debug

import android.content.Context
import com.nacho.kartlog.data.local.KartLogDatabase
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository

/** Variante release: sin herramientas de depuración. */
@Suppress("UNUSED_PARAMETER")
object DebugToolsFactory {
    fun create(
        context: Context,
        database: KartLogDatabase,
        circuits: CircuitRepository,
        sessions: SessionRepository,
        laps: LapRepository,
    ): DebugTools? = null
}
