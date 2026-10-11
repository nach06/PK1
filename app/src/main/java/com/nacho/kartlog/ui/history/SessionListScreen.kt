package com.nacho.kartlog.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.components.Chip
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.KartOverflowMenu
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.MenuAction
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.labelRes
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onNewSession: () -> Unit,
    onImport: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCircuitPicker by rememberSaveable { mutableStateOf(false) }
    // Estado de scroll explícito y fuera de las ramas de carga/vacío: se guarda con la entrada de la pila y
    // la lista vuelve a la misma posición al regresar del Detalle.
    val listState = rememberLazyListState()
    val checked = stringResource(R.string.menu_checked)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KartTopBar(
                title = stringResource(R.string.history_title),
                actions = {
                    KartOverflowMenu(
                        actions = listOf(
                            MenuAction(label = stringResource(R.string.import_menu), onClick = onImport),
                            MenuAction(
                                label = (if (state.filters.sort == SessionSort.DATE) checked else "") +
                                    stringResource(R.string.sort_by_date),
                                onClick = { viewModel.setSort(SessionSort.DATE) },
                            ),
                            MenuAction(
                                label = (if (state.filters.sort == SessionSort.BEST_LAP) checked else "") +
                                    stringResource(R.string.sort_by_best_lap),
                                onClick = { viewModel.setSort(SessionSort.BEST_LAP) },
                            ),
                        ),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSession,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(
                    text = "+  " + stringResource(R.string.action_new_session).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.totalSessions == 0 -> EmptyState(
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_message),
                actionLabel = stringResource(R.string.action_new_session),
                onAction = onNewSession,
                secondaryLabel = stringResource(R.string.import_menu),
                onSecondary = onImport,
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                FilterRow(
                    state = state,
                    onCircuitClick = { showCircuitPicker = true },
                    onTypeClick = viewModel::toggleType,
                )
                if (state.items.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.filter_empty_title),
                        message = stringResource(R.string.filter_empty_message),
                        actionLabel = stringResource(R.string.filter_clear),
                        onAction = viewModel::clearFilters,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.items.forEach { item ->
                            when (item) {
                                is SessionListItem.MonthHeader -> stickyHeader(key = item.key, contentType = "header") {
                                    MonthHeader(item.month)
                                }
                                is SessionListItem.Entry -> item(key = item.key, contentType = "session") {
                                    SessionCard(
                                        stats = item.stats,
                                        onClick = { onOpenSession(item.stats.summary.session.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCircuitPicker) {
        CircuitPickerSheet(
            circuits = state.circuits,
            selectedId = state.filters.circuitId,
            onSelect = {
                viewModel.selectCircuit(it.id)
                showCircuitPicker = false
            },
            onDismiss = { showCircuitPicker = false },
            allLabel = stringResource(R.string.filter_all_circuits),
            onSelectAll = {
                viewModel.selectCircuit(null)
                showCircuitPicker = false
            },
        )
    }
}

@Composable
private fun FilterRow(
    state: SessionListUiState,
    onCircuitClick: () -> Unit,
    onTypeClick: (SessionType) -> Unit,
) {
    // LazyRow: arranca siempre sin desplazamiento (primer chip completo) y conserva el scroll horizontal.
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "circuit") {
            Chip(
                text = (state.selectedCircuit?.name ?: stringResource(R.string.filter_circuit)) + " ▾",
                selected = state.filters.circuitId != null,
                onClick = onCircuitClick,
            )
        }
        items(SessionType.entries, key = { it.name }) { type ->
            Chip(
                text = stringResource(type.labelRes()),
                selected = state.filters.type == type,
                onClick = { onTypeClick(type) },
            )
        }
    }
}

/** Cabecera de mes pegajosa («Octubre 2026»). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthHeader(month: YearMonth) {
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() }
    Text(
        text = "$name ${month.year}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.kartColors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.kartColors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = actionLabel, onClick = onAction)
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = secondaryLabel, onClick = onSecondary)
        }
    }
}
