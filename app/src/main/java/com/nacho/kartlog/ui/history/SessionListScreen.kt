package com.nacho.kartlog.ui.history

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.StatBadge
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.labelRes

@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onNewSession: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.history_title)) },
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
            state.sessions.isEmpty() -> EmptyState(onNewSession, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.session.id }) { summary ->
                    SessionCard(summary, onClick = { onOpenSession(summary.session.id) })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onNewSession: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.kartColors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = stringResource(R.string.action_new_session), onClick = onNewSession)
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, onClick: () -> Unit) {
    val session = summary.session
    val kart = MaterialTheme.kartColors
    KartCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Text(
            text = summary.circuit.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = formatDateTime(session.dateTime),
            style = MaterialTheme.typography.bodyMedium,
            color = kart.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        val conditionColor = if (session.trackCondition == TrackCondition.DRY) kart.textMuted else kart.warning
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(session.type.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            session.kartNumber?.let {
                Text(
                    text = stringResource(R.string.format_kart, it),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(session.trackCondition.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = conditionColor,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatBadge(
                label = stringResource(R.string.label_best_short),
                value = summary.bestLapMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                valueColor = if (summary.bestLapMs != null) kart.telemetry else kart.textMuted,
                valueStyle = KartTextStyles.bestLap,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (summary.lapCount == 0) {
                    stringResource(R.string.history_no_laps)
                } else {
                    pluralStringResource(R.plurals.format_lap_count, summary.lapCount, summary.lapCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = kart.textMuted,
            )
        }
    }
}
