package com.nacho.kartlog.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.repository.SessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SessionListUiState(
    val isLoading: Boolean = true,
    val sessions: List<SessionSummary> = emptyList(),
)

class SessionListViewModel(sessionRepository: SessionRepository) : ViewModel() {
    val uiState: StateFlow<SessionListUiState> = sessionRepository.observeSummaries()
        .map { SessionListUiState(isLoading = false, sessions = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionListUiState())
}
