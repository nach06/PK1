package com.nacho.kartlog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.repository.CircuitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SessionListUiState(
    val isLoading: Boolean = true,
    val items: List<SessionListItem> = emptyList(),
    /** Nº de tandas sin filtrar: distingue «no hay tandas» de «el filtro no devuelve nada». */
    val totalSessions: Int = 0,
    val circuits: List<Circuit> = emptyList(),
    val filters: SessionFilters = SessionFilters(),
) {
    val selectedCircuit: Circuit? get() = circuits.firstOrNull { it.id == filters.circuitId }
}

class SessionListViewModel(
    analytics: AnalyticsSource,
    circuits: CircuitRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(SessionFilters())

    val uiState: StateFlow<SessionListUiState> =
        combine(analytics.observe(), circuits.observeAll(), filters) { snapshot, circuitList, f ->
            SessionListUiState(
                isLoading = false,
                items = buildSessionItems(snapshot.sessions, f),
                totalSessions = snapshot.sessions.size,
                circuits = circuitList,
                filters = f,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUiState())

    fun selectCircuit(id: Long?) = filters.update { it.copy(circuitId = id) }

    /** Pulsar el tipo activo lo desmarca. */
    fun toggleType(type: SessionType) = filters.update { it.copy(type = if (it.type == type) null else type) }

    fun setSort(sort: SessionSort) = filters.update { it.copy(sort = sort) }

    fun clearFilters() = filters.update { it.copy(circuitId = null, type = null) }
}
