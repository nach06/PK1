package com.nacho.kartlog.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.ui.components.Chip
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.FabAction
import com.nacho.kartlog.ui.components.KartOverflowMenu
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.MenuAction
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.components.SpeedDialFab
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.labelRes
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val ACTION_STRIP_HEIGHT = 84.dp
private val ACTION_STRIP_HEIGHT_COMPACT = 60.dp

/** Alto útil escaso (móvil apaisado): franja inferior y chips más bajos para que quepa al menos una card entera. */
private const val COMPACT_HEIGHT_DP = 480

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onNewSession: () -> Unit,
    onImport: () -> Unit,
    onTrack: () -> Unit,
    onOpenSession: (Long) -> Unit,
    debugShortcuts: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCircuitPicker by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    // Estado de scroll explícito y fuera de las ramas de carga/vacío: se guarda con la entrada de la pila y
    // la lista vuelve a la misma posición al regresar del Detalle.
    val listState = rememberLazyListState()
    val checked = stringResource(R.string.menu_checked)
    val compact = LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
    val stripHeight = if (compact) ACTION_STRIP_HEIGHT_COMPACT else ACTION_STRIP_HEIGHT

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KartTopBar(
                title = stringResource(R.string.history_title),
                actions = {
                    KartOverflowMenu(
                        actions = listOf(
                            MenuAction(label = stringResource(R.string.search_action), onClick = { searchOpen = true }),
                            MenuAction(label = stringResource(R.string.import_menu), onClick = onImport),
                            MenuAction(label = stringResource(R.string.action_on_track), onClick = onTrack),
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
    ) { padding ->
        // La acción principal vive en una franja reservada al pie: la lista termina encima de ella, así que la
        // FAB (cerrada) nunca tapa el texto de ninguna card. Al desplegarla se oscurece el fondo a propósito.
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().padding(bottom = stripHeight)) {
                SessionListBody(
                    compact = compact,
                    state = state,
                    listState = listState,
                    onNewSession = onNewSession,
                    onImport = onImport,
                    onOpenSession = onOpenSession,
                    onCircuitClick = { showCircuitPicker = true },
                    searchOpen = searchOpen,
                    onCloseSearch = { searchOpen = false; viewModel.setQuery("") },
                    debugShortcuts = debugShortcuts,
                    viewModel = viewModel,
                )
            }
            if (fabExpanded) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pointerInput(Unit) { detectTapGestures { fabExpanded = false } }
                        .testTag("fab_scrim"),
                )
            }
            // Franja de ancho completo: a la izquierda el recuento (con el filtro aplicado), a la derecha la acción principal.
            if (state.totalSessions > 0) {
                val shown = state.items.count { it is SessionListItem.Entry }
                Text(
                    text = if (shown == state.totalSessions) {
                        pluralStringResource(R.plurals.format_session_count, shown, shown)
                    } else {
                        stringResource(R.string.history_count_filtered, shown, state.totalSessions)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.kartColors.textMuted,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = if (compact) 16.dp else 24.dp).testTag("list_count"),
                )
            }
            SpeedDialFab(
                expanded = fabExpanded,
                onExpandedChange = { fabExpanded = it },
                closedLabel = stringResource(R.string.action_new_session),
                actions = listOf(
                    FabAction(stringResource(R.string.action_new_session), onNewSession, tag = "fab_new"),
                    FabAction(stringResource(R.string.import_title), onImport, tag = "fab_import"),
                    FabAction(stringResource(R.string.action_on_track), onTrack, tag = "fab_track"),
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = if (compact) 2.dp else 12.dp),
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionListBody(
    compact: Boolean,
    state: SessionListUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onNewSession: () -> Unit,
    onImport: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onCircuitClick: () -> Unit,
    searchOpen: Boolean,
    onCloseSearch: () -> Unit,
    debugShortcuts: Boolean,
    viewModel: SessionListViewModel,
) {
    when {
        state.isLoading -> Unit
        state.totalSessions == 0 -> EmptyState(
            title = stringResource(R.string.history_empty_title),
            message = stringResource(R.string.history_empty_message),
            actionLabel = stringResource(R.string.action_new_session),
            onAction = onNewSession,
            secondaryLabel = stringResource(R.string.import_menu),
            onSecondary = onImport,
            
        )
        else -> Column(Modifier.fillMaxSize()) {
            if (searchOpen || state.filters.query.isNotEmpty()) {
                SearchRow(
                    query = state.filters.query,
                    onQuery = viewModel::setQuery,
                    onClose = onCloseSearch,
                    debugSample = if (debugShortcuts) state.items.filterIsInstance<SessionListItem.Entry>().firstOrNull()?.stats?.summary?.circuit?.name else null,
                )
            }
            FilterRow(
                compact = compact,
                state = state,
                onCircuitClick = onCircuitClick,
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = if (compact) 2.dp else 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp),
                ) {
                    state.items.forEach { item ->
                        when (item) {
                            is SessionListItem.MonthHeader -> stickyHeader(key = item.key, contentType = "header") {
                                MonthHeader(item.month, compact)
                            }
                            is SessionListItem.Entry -> item(key = item.key, contentType = "session") {
                                SessionCard(
                                    stats = item.stats,
                                    compact = compact,
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

/** Campo de búsqueda de texto (D3) con ✕ para cerrarla; en debug, un atajo que rellena la consulta sin teclear. */
@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, debugSample: String?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("search_field"),
            )
            androidx.compose.material3.TextButton(onClick = onClose, modifier = Modifier.testTag("search_close")) {
                Text("✕", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (debugSample != null && query.isEmpty()) {
            androidx.compose.material3.TextButton(onClick = { onQuery(debugSample) }, modifier = Modifier.testTag("debug_search")) {
                Text(stringResource(R.string.search_debug_sample, debugSample), maxLines = 1)
            }
        }
    }
}

@Composable
private fun FilterRow(
    compact: Boolean,
    state: SessionListUiState,
    onCircuitClick: () -> Unit,
    onTypeClick: (SessionType) -> Unit,
) {
    val chipHeight = if (compact) 40.dp else 56.dp
    val chipPadding = if (compact) 6.dp else 12.dp
    // LazyRow: arranca siempre sin desplazamiento (primer chip completo) y conserva el scroll horizontal.
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "circuit") {
            Chip(
                text = (state.selectedCircuit?.name ?: stringResource(R.string.filter_circuit)) + " ▾",
                selected = state.filters.circuitId != null,
                onClick = onCircuitClick,
                minHeight = chipHeight,
                verticalPadding = chipPadding,
            )
        }
        items(SessionType.entries, key = { it.name }) { type ->
            Chip(
                text = stringResource(type.labelRes()),
                selected = state.filters.type == type,
                onClick = { onTypeClick(type) },
                minHeight = chipHeight,
                verticalPadding = chipPadding,
            )
        }
    }
}

/** Cabecera de mes pegajosa («Octubre 2026»). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthHeader(month: YearMonth, compact: Boolean) {
    val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("es", "ES"))
        .replaceFirstChar { it.uppercase() }
    Text(
        text = "$name ${month.year}",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.kartColors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = if (compact) 2.dp else 8.dp, bottom = if (compact) 0.dp else 4.dp),
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
