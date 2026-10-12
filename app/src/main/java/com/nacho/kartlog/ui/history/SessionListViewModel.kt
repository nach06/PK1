package com.nacho.kartlog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.domain.settings.SavedListFilter
import com.nacho.kartlog.domain.settings.UserSettings
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
    /** Si se da, el filtro (circuito, tipo y orden) se recuerda entre sesiones. */
    private val settings: UserSettings? = null,
) : ViewModel() {
    private val filters = MutableStateFlow(initialFilters())

    private fun initialFilters(): SessionFilters {
        val saved = settings?.listFilter?.value ?: return SessionFilters()
        return SessionFilters(
            circuitId = saved.circuitId,
            type = saved.type?.let { name -> SessionType.entries.firstOrNull { it.name == name } },
            sort = saved.sort?.let { name -> SessionSort.entries.firstOrNull { it.name == name } } ?: SessionSort.DATE,
        )
    }

    val uiState: StateFlow<SessionListUiState> =
        combine(analytics.observe(), circuits.observeAll(), filters) { snapshot, circuitList, stored ->
            // Un circuito borrado desde la última vez ya no filtra (si no, la lista saldría vacía sin explicación).
            val f = if (stored.circuitId != null && circuitList.none { it.id == stored.circuitId }) stored.copy(circuitId = null) else stored
            SessionListUiState(
                isLoading = false,
                items = buildSessionItems(snapshot.sessions, f),
                totalSessions = snapshot.sessions.size,
                circuits = circuitList,
                filters = f,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUiState())

    private fun change(change: (SessionFilters) -> SessionFilters) {
        filters.update(change)
        settings?.setListFilter(filters.value.let { SavedListFilter(it.circuitId, it.type?.name, it.sort.name) })
    }

    fun selectCircuit(id: Long?) = change { it.copy(circuitId = id) }

    /** Pulsar el tipo activo lo desmarca. */
    fun toggleType(type: SessionType) = change { it.copy(type = if (it.type == type) null else type) }

    fun setSort(sort: SessionSort) = change { it.copy(sort = sort) }

    fun clearFilters() = change { it.copy(circuitId = null, type = null, query = "") }

    /** Búsqueda de texto: se aplica al escribir y no se guarda con los demás filtros. */
    fun setQuery(query: String) = filters.update { it.copy(query = query) }
}
