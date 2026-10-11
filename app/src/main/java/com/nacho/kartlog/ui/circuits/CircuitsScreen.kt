package com.nacho.kartlog.ui.circuits

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.analytics.CircuitStats
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.CircuitEditSheet
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartOverflowMenu
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.MenuAction
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors

@Composable
fun CircuitsScreen(
    viewModel: CircuitsViewModel,
    onOpenAnalysis: () -> Unit,
    onMarkFinish: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.nav_circuits)) },
    ) { padding ->
        when {
            state.isLoading -> Unit
            state.rows.isEmpty() -> Text(
                text = stringResource(R.string.circuits_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.kartColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.circuit.id }) { stats ->
                    CircuitCard(
                        stats = stats,
                        onClick = {
                            viewModel.selectForAnalysis(stats.circuit.id)
                            onOpenAnalysis()
                        },
                        onMarkFinish = { onMarkFinish(stats.circuit.id) },
                        onEdit = { viewModel.startEdit(stats.circuit) },
                        onDelete = { viewModel.requestDelete(stats) },
                    )
                }
            }
        }
    }

    state.editing?.let { edit ->
        CircuitEditSheet(
            title = stringResource(R.string.circuit_edit_title),
            name = edit.name,
            lengthMeters = edit.lengthMeters,
            errorText = when (edit.error) {
                CircuitNameError.BLANK -> stringResource(R.string.circuit_name_required)
                CircuitNameError.DUPLICATE -> stringResource(R.string.circuit_name_duplicate)
                null -> null
            },
            onNameChange = viewModel::setName,
            onDecrementLength = viewModel::decrementLength,
            onIncrementLength = viewModel::incrementLength,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::dismissEdit,
        )
    }

    state.pendingDelete?.let { circuit ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.circuit_delete_title, circuit.name), style = MaterialTheme.typography.titleLarge) },
            text = { Text(stringResource(R.string.circuit_delete_message), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(
                        stringResource(R.string.action_delete).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_cancel).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }

    state.deleteBlocked?.let { blocked ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteBlocked,
            containerColor = MaterialTheme.kartColors.card,
            title = { Text(stringResource(R.string.circuit_delete_blocked_title), style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = pluralStringResource(
                        R.plurals.circuit_has_sessions,
                        blocked.sessionCount,
                        blocked.circuitName,
                        blocked.sessionCount,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDeleteBlocked, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.action_accept).uppercase(), style = MaterialTheme.typography.titleSmall)
                }
            },
        )
    }
}

@Composable
private fun CircuitCard(
    stats: CircuitStats,
    onClick: () -> Unit,
    onMarkFinish: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val kart = MaterialTheme.kartColors
    KartCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stats.circuit.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stats.circuit.lengthMeters?.let { stringResource(R.string.format_length_m, it) }
                        ?: stringResource(R.string.circuit_no_length),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kart.textMuted,
                )
                stats.gpsAccuracyMs?.let { ms ->
                    Text(
                        text = stringResource(
                            R.string.circuit_gps_accuracy,
                            LapTime.formatSeconds(ms),
                            pluralStringResource(R.plurals.format_session_count, stats.gpsAccuracySessions, stats.gpsAccuracySessions),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kart.telemetry,
                        modifier = Modifier.testTag("circuit_gps_accuracy"),
                    )
                }
                Text(
                    text = stringResource(if (stats.circuit.finishLine != null) R.string.circuit_finish_set else R.string.circuit_finish_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stats.circuit.finishLine != null) kart.telemetry else kart.textMuted,
                )
            }
            KartOverflowMenu(
                actions = listOf(
                    MenuAction(label = stringResource(R.string.circuit_set_finish), onClick = onMarkFinish),
                    MenuAction(label = stringResource(R.string.action_edit), onClick = onEdit),
                    MenuAction(label = stringResource(R.string.action_delete), onClick = onDelete, destructive = true),
                ),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.stat_record).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = kart.textMuted,
                )
                Text(
                    text = stats.recordMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                    color = if (stats.recordMs != null) kart.telemetry else kart.textMuted,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.format_session_count, stats.totalSessions, stats.totalSessions),
                style = MaterialTheme.typography.bodyLarge,
                color = kart.textMuted,
            )
        }
    }
}
