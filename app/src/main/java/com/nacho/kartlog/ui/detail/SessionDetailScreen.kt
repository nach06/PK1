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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.SessionDetail
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.StatBadge
import com.nacho.kartlog.ui.components.TimeKeypad
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.labelRes

@Composable
fun SessionDetailScreen(
    viewModel: SessionDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Sesión borrada (o inexistente): volver al histórico.
    LaunchedEffect(state.isLoading, state.detail == null) {
        if (!state.isLoading && state.detail == null) onBack()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            KartTopBar(
                title = state.detail?.circuit?.name ?: stringResource(R.string.detail_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = viewModel::requestDeleteSession,
                        modifier = Modifier.heightIn(min = 56.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_delete).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        if (detail == null) {
            Box(Modifier.fillMaxSize().padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeaderCard(detail) }
                item { StatsRow(state) }
                item {
                    LapEntry(
                        state = state,
                        onDigits = viewModel::onDigits,
                        onBackspace = viewModel::onBackspace,
                        onClear = viewModel::onClear,
                        onSave = {
                            if (viewModel.saveLap()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onCancelEdit = viewModel::cancelEditing,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.laps_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.kartColors.textMuted,
                    )
                }
                if (state.laps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.laps_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.kartColors.textMuted,
                        )
                    }
                } else {
                    // Más reciente primero: la última vuelta guardada queda justo bajo el teclado.
                    items(state.laps.asReversed(), key = { it.id }) { lap ->
                        LapRow(
                            lap = lap,
                            isBest = lap.timeMs == state.bestLapMs,
                            isEditing = lap.id == state.editingLap?.id,
                            onEdit = { viewModel.startEditing(lap) },
                            onDelete = { viewModel.requestDeleteLap(lap) },
                        )
                    }
                }
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

@Composable
private fun HeaderCard(detail: SessionDetail) {
    val session = detail.session
    val kart = MaterialTheme.kartColors
    KartCard(Modifier.fillMaxWidth()) {
        Text(
            text = formatDateTime(session.dateTime),
            style = MaterialTheme.typography.bodyLarge,
            color = kart.textMuted,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(session.type.labelRes()),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            session.kartNumber?.let {
                Text(
                    text = stringResource(R.string.format_kart, it),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            session.ballastKg?.let {
                Text(
                    text = stringResource(R.string.format_ballast, it.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        val ambient = session.ambientTempC
        val track = session.trackTempC
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (ambient != null && track != null) {
                Text(
                    text = stringResource(R.string.format_temps, ambient.toInt(), track.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kart.textMuted,
                )
            }
            Text(
                text = stringResource(session.trackCondition.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = if (session.trackCondition == TrackCondition.DRY) {
                    kart.textMuted
                } else {
                    kart.warning
                },
            )
        }
        if (session.notes.isNotBlank()) {
            Text(
                text = session.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatsRow(state: SessionDetailUiState) {
    val kart = MaterialTheme.kartColors
    val unset = stringResource(R.string.value_unset)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StatBadge(
            label = stringResource(R.string.stat_best),
            value = state.bestLapMs?.let(LapTime::format) ?: unset,
            valueColor = if (state.bestLapMs != null) kart.telemetry else kart.textMuted,
            valueStyle = KartTextStyles.bestLap,
            modifier = Modifier.weight(1f),
        )
        StatBadge(
            label = stringResource(R.string.stat_average),
            value = state.averageMs?.let(LapTime::format) ?: unset,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LapEntry(
    state: SessionDetailUiState,
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    val kart = MaterialTheme.kartColors
    val editing = state.editingLap
    KartCard(Modifier.fillMaxWidth()) {
        Text(
            text = (
                if (editing != null) {
                    stringResource(R.string.lap_entry_editing, editing.number)
                } else {
                    stringResource(R.string.lap_entry_title, state.nextLapNumber)
                }
                ).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (editing != null) kart.warning else kart.textMuted,
        )
        Text(
            text = state.timeText,
            style = KartTextStyles.timeEntry,
            color = when {
                state.timeInvalid -> MaterialTheme.colorScheme.error
                state.digits.isEmpty() -> kart.textMuted
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
        if (state.timeInvalid) {
            Text(
                text = stringResource(R.string.time_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        TimeKeypad(
            onDigits = onDigits,
            onBackspace = onBackspace,
            onClear = onClear,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(
                text = stringResource(if (editing != null) R.string.action_update_lap else R.string.action_save_lap),
                onClick = onSave,
                enabled = state.canSave,
            )
            if (editing != null) {
                TextButton(
                    onClick = onCancelEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(
                        stringResource(R.string.action_cancel_edit).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
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
