package com.nacho.kartlog.ui.rivals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.analytics.RivalAnalytics
import com.nacho.kartlog.domain.repository.ParticipantRepository
import com.nacho.kartlog.domain.settings.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RivalsUiState(
    val isLoading: Boolean = true,
    val rivals: List<RivalAnalytics.Rival> = emptyList(),
    /** `null` con menos de [RivalAnalytics.MIN_SESSIONS_FOR_CONDITIONS] tandas con media limpia. */
    val correlations: List<RivalAnalytics.Correlation>? = null,
    /** Tandas con media limpia (las que cuentan para las condiciones). */
    val sessionsWithAverage: Int = 0,
    val hasMerges: Boolean = false,
)

/** Rivales (Sprint 8, C): todo se calcula en el móvil a partir de lo importado; nada de esto sale en los exports. */
class RivalsViewModel(
    analytics: AnalyticsSource,
    participants: ParticipantRepository,
    private val settings: UserSettings,
) : ViewModel() {

    val uiState: StateFlow<RivalsUiState> =
        combine(analytics.observe(), participants.observeAllWithLaps(), settings.driverAliases) { snapshot, entries, aliases ->
            RivalsUiState(
                isLoading = false,
                rivals = RivalAnalytics.rivals(snapshot.sessions, entries, aliases),
                correlations = RivalAnalytics.conditionCorrelations(snapshot.sessions),
                sessionsWithAverage = snapshot.sessions.count { it.cleanAverageMs != null },
                hasMerges = aliases.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RivalsUiState())

    /** Une dos pilotos (claves de [RivalAnalytics.Rival.key]): a partir de ahora cuentan como uno solo. */
    fun merge(keyA: String, keyB: String) {
        settings.setDriverAliases(RivalAnalytics.merge(settings.driverAliases.value, from = keyB, into = keyA))
    }

    fun clearMerges() = settings.setDriverAliases(emptyMap())
}
