package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartOverflowMenu
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.MenuAction
import com.nacho.kartlog.ui.components.StatBadge
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.labelRes
import com.nacho.kartlog.ui.util.shareLapsCsv
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Teclado plegable. Estado inicial: desplegado solo si la tanda no tiene vueltas.
    var keypadExpanded by rememberSaveable { mutableStateOf(false) }
    var keypadInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading && !keypadInitialized) {
            keypadExpanded = state.laps.isEmpty()
            keypadInitialized = true
        }
    }

    // Sesión borrada (o inexistente): volver al histórico.
    LaunchedEffect(state.isLoading, state.detail == null) {
        if (!state.isLoading && state.detail == null) onBack()
    }

    val detail = state.detail
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KartTopBar(
                title = detail?.circuit?.name ?: stringResource(R.string.detail_title),
                onBack = onBack,
                actions = {
                    KartOverflowMenu(
                        actions = listOf(
                            MenuAction(
                                label = stringResource(R.string.action_export_csv),
                                enabled = state.laps.isNotEmpty() && detail != null,
                                onClick = {
                                    if (detail != null) {
                                        scope.launch {
                                            shareLapsCsv(context, detail.circuit.name, detail.session.dateTime, state.laps)
                                        }
                                    }
                                },
                            ),
                            MenuAction(
                                label = stringResource(R.string.action_delete_session),
                                destructive = true,
                                onClick = viewModel::requestDeleteSession,
                            ),
                        ),
                    )
                },
            )
        },
    ) { padding ->
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                HeaderCard(
                    detail = detail,
                    state = state,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.laps.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.laps_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.kartColors.textMuted,
                            )
                        }
                    } else {
                        // Más reciente primero: la última vuelta guardada queda junto al teclado.
                        items(state.laps.asReversed(), key = { it.id }) { lap ->
                            LapRow(
                                lap = lap,
                                isBest = lap.timeMs == state.bestLapMs,
                                isEditing = lap.id == state.editingLap?.id,
                                onEdit = {
                                    viewModel.startEditing(lap)
                                    keypadExpanded = true
                                },
                                onDelete = { viewModel.requestDeleteLap(lap) },
                            )
                        }
                    }
                }
                val editing = state.editingLap
                LapEntryPanel(
                    expanded = keypadExpanded,
                    timeText = state.timeText,
                    hasDigits = state.digits.isNotEmpty(),
                    invalid = state.timeInvalid,
                    canSave = state.canSave,
                    label = if (editing != null) {
                        stringResource(R.string.lap_entry_editing, editing.number)
                    } else {
                        stringResource(R.string.lap_entry_title, state.nextLapNumber)
                    },
                    isEditing = editing != null,
                    onToggle = { keypadExpanded = !keypadExpanded },
                    onDigits = viewModel::onDigits,
                    onBackspace = viewModel::onBackspace,
                    onClear = viewModel::onClear,
                    onSave = {
                        if (viewModel.saveLap()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onCancelEdit = viewModel::cancelEditing,
                )
            }
        }
    }

    state.pendingDeleteLap?.let { lap ->
        ConfirmDialog(
            title = stringResource(R.string.delete_lap_title, lap.number),
            message = stringResource(R.string.delete_lap_message, LapTime.format(lap.timeMs)),
            onConfirm = viewModel::confirmDeleteLap,
            onDismiss = viewModel::dismissDeleteLap,
        )
    }
    if (state.confirmDeleteSession) {
        ConfirmDialog(
            title = stringResource(R.string.delete_session_title),
            message = stringResource(R.string.delete_session_message),
            onConfirm = viewModel::confirmDeleteSession,
            onDismiss = viewModel::dismissDeleteSession,
        )
    }
}

/** Cabecera compacta: datos de la tanda (solo los no nulos) y fila MEJOR / MEDIA. */
@Composable
private fun HeaderCard(detail: SessionDetail, state: SessionDetailUiState, modifier: Modifier = Modifier) {
    val session = detail.session
    val kart = MaterialTheme.kartColors
    val unset = stringResource(R.string.value_unset)

    val line1 = buildList {
        add(formatDateTime(session.dateTime))
        add(stringResource(session.type.labelRes()))
        session.kartNumber?.let { add(stringResource(R.string.format_kart, it)) }
        session.ballastKg?.let { add(stringResource(R.string.format_ballast, it.toInt())) }
    }.joinToString(" · ")
    val line2 = buildList {
        session.ambientTempC?.let { add(stringResource(R.string.format_temp_ambient, it.toInt())) }
        session.trackTempC?.let { add(stringResource(R.string.format_temp_track, it.toInt())) }
    }.joinToString(" · ")

    KartCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        Text(
            text = line1,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (line2.isNotEmpty()) {
                Text(text = line2, style = MaterialTheme.typography.bodyMedium, color = kart.textMuted)
            }
            Text(
                text = stringResource(session.trackCondition.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (session.trackCondition == TrackCondition.DRY) kart.textMuted else kart.warning,
            )
        }
        if (session.notes.isNotBlank()) {
            Text(
                text = session.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = kart.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            StatBadge(
                label = stringResource(R.string.stat_best),
                value = state.bestLapMs?.let(LapTime::format) ?: unset,
                valueColor = if (state.bestLapMs != null) kart.telemetry else kart.textMuted,
                valueStyle = KartTextStyles.bestLap,
                modifier = Modifier.weight(1.2f),
            )
            StatBadge(
                label = stringResource(R.string.stat_average),
                value = state.averageMs?.let(LapTime::format) ?: unset,
                modifier = Modifier.weight(0.8f),
            )
        }
    }
}

@Composable
private fun LapRow(
    lap: Lap,
    isBest: Boolean,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val kart = MaterialTheme.kartColors
    KartCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lap_number, lap.number),
                style = MaterialTheme.typography.titleSmall,
                color = if (isEditing) kart.warning else kart.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = LapTime.format(lap.timeMs),
                style = KartTextStyles.lapTime,
                color = if (isBest) kart.telemetry else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_edit).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
            TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    stringResource(R.string.action_delete).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.kartColors.card,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(
                    stringResource(R.string.action_delete).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}
