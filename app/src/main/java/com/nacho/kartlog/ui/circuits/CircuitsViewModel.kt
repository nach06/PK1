package com.nacho.kartlog.ui.circuits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.analytics.AnalyticsSource
import com.nacho.kartlog.domain.analytics.CircuitStats
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.repository.CircuitRepository
import com.nacho.kartlog.ui.analysis.AnalysisSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CircuitNameError { BLANK, DUPLICATE }

data class EditCircuitState(
    val id: Long,
    val name: String,
    val lengthMeters: Int?,
    val error: CircuitNameError? = null,
)

/** Aviso de borrado bloqueado: el circuito aún tiene tandas. */
data class DeleteBlocked(val circuitName: String, val sessionCount: Int)

data class CircuitsUiState(
    val isLoading: Boolean = true,
    val rows: List<CircuitStats> = emptyList(),
    val editing: EditCircuitState? = null,
    val pendingDelete: Circuit? = null,
    val deleteBlocked: DeleteBlocked? = null,
)

private const val LENGTH_STEP_M = 50
private const val MAX_LENGTH_M = 10_000

class CircuitsViewModel(
    private val circuits: CircuitRepository,
    analytics: AnalyticsSource,
    private val selection: AnalysisSelection,
) : ViewModel() {

    private data class Local(
        val editing: EditCircuitState? = null,
        val pendingDelete: Circuit? = null,
        val deleteBlocked: DeleteBlocked? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<CircuitsUiState> =
        combine(circuits.observeAll(), analytics.observe(), local) { list, snapshot, l ->
            CircuitsUiState(
                isLoading = false,
                rows = list.map { snapshot.circuits[it.id] ?: Analytics.emptyCircuitStats(it) }
                    .sortedBy { it.circuit.name.lowercase() },
                editing = l.editing,
                pendingDelete = l.pendingDelete,
                deleteBlocked = l.deleteBlocked,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CircuitsUiState())

    /** Abre Análisis con este circuito seleccionado (la navegación la hace la pantalla). */
    fun selectForAnalysis(circuitId: Long) {
        selection.circuitId.value = circuitId
    }

    // ---- Edición ----
    fun startEdit(circuit: Circuit) =
        local.update { it.copy(editing = EditCircuitState(circuit.id, circuit.name, circuit.lengthMeters)) }

    fun dismissEdit() = local.update { it.copy(editing = null) }

    fun setName(name: String) = local.update { it.copy(editing = it.editing?.copy(name = name, error = null)) }

    fun incrementLength() = local.update { l ->
        l.copy(editing = l.editing?.let { e ->
            e.copy(lengthMeters = ((e.lengthMeters ?: 0) + LENGTH_STEP_M).coerceAtMost(MAX_LENGTH_M))
        })
    }

    fun decrementLength() = local.update { l ->
        l.copy(editing = l.editing?.let { e ->
            e.copy(lengthMeters = e.lengthMeters?.let { m -> if (m <= LENGTH_STEP_M) null else m - LENGTH_STEP_M })
        })
    }

    fun confirmEdit() {
        val edit = local.value.editing ?: return
        if (edit.name.isBlank()) {
            local.update { it.copy(editing = edit.copy(error = CircuitNameError.BLANK)) }
            return
        }
        viewModelScope.launch {
            val ok = circuits.update(edit.id, edit.name, edit.lengthMeters)
            local.update {
                if (ok) it.copy(editing = null) else it.copy(editing = edit.copy(error = CircuitNameError.DUPLICATE))
            }
        }
    }

    // ---- Borrado ----
    /** Con tandas: bloqueado con aviso. Sin tandas: pide confirmación. */
    fun requestDelete(stats: CircuitStats) = local.update {
        if (stats.totalSessions > 0) {
            it.copy(deleteBlocked = DeleteBlocked(stats.circuit.name, stats.totalSessions))
        } else {
            it.copy(pendingDelete = stats.circuit)
        }
    }

    fun dismissDelete() = local.update { it.copy(pendingDelete = null) }

    fun dismissDeleteBlocked() = local.update { it.copy(deleteBlocked = null) }

    fun confirmDelete() {
        val circuit = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        viewModelScope.launch { circuits.delete(circuit.id) }
    }
}
