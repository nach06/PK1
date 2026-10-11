package com.nacho.kartlog.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.analytics.CircuitStats
import com.nacho.kartlog.domain.model.Circuit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalysisUiState(
    val isLoading: Boolean = true,
    val circuits: List<Circuit> = emptyList(),
    val stats: CircuitStats? = null,
) {
    /** Hay gráficas útiles a partir de 2 tandas con vueltas. */
    val hasEnoughData: Boolean get() = (stats?.sessionsWithLaps ?: 0) >= 2
}

class AnalysisViewModel(
    analytics: AnalyticsSource,
    private val selection: AnalysisSelection,
) : ViewModel() {

    val uiState: StateFlow<AnalysisUiState> = combine(analytics.observe(), selection.circuitId) { snapshot, selected ->
        val id = resolveCircuitId(snapshot, selected)
        AnalysisUiState(
            isLoading = false,
            circuits = circuitsWithSessions(snapshot),
            stats = id?.let { snapshot.circuits[it] },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    fun selectCircuit(id: Long) {
        selection.circuitId.value = id
    }
}
