package com.nacho.kartlog.domain.analytics

import com.nacho.kartlog.domain.repository.LapRepository
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Emite un [AnalyticsSnapshot] actualizado cada vez que cambian tandas o vueltas. */
class AnalyticsSource(
    private val sessions: SessionRepository,
    private val laps: LapRepository,
) {
    fun observe(): Flow<AnalyticsSnapshot> =
        combine(sessions.observeSummaries(), laps.observeAll()) { summaries, allLaps ->
            Analytics.compute(summaries, allLaps)
        }.flowOn(Dispatchers.Default)
}
