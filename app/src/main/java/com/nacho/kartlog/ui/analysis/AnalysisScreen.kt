package com.nacho.kartlog.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.nacho.kartlog.domain.analytics.CircuitStats
import com.nacho.kartlog.domain.analytics.ConditionStats
import com.nacho.kartlog.domain.analytics.KartStats
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.StatBadge
import com.nacho.kartlog.ui.components.TimeLineChart
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatShortDate
import com.nacho.kartlog.ui.util.icon
import com.nacho.kartlog.ui.util.labelRes

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.nav_analysis)) },
    ) { padding ->
        val stats = state.stats
        when {
            state.isLoading -> Unit
            stats == null -> Text(
                text = stringResource(R.string.analysis_no_sessions),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.kartColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // 1. Selector de circuito.
                OutlinedButton(
                    onClick = { showPicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stats.circuit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", style = MaterialTheme.typography.titleMedium)
                }

                // 2. Récord, media limpia histórica y nº de tandas.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val unset = stringResource(R.string.value_unset)
                    StatBadge(
                        label = stringResource(R.string.stat_record),
                        value = stats.recordMs?.let(LapTime::format) ?: unset,
                        valueColor = MaterialTheme.kartColors.telemetry,
                        valueStyle = KartTextStyles.lapTime,
                        modifier = Modifier.weight(1.1f),
                    )
                    StatBadge(
                        label = stringResource(R.string.stat_historic_clean_average),
                        value = stats.historicCleanAverageMs?.let(LapTime::format) ?: unset,
                        valueStyle = KartTextStyles.lapTime,
                        modifier = Modifier.weight(1.1f),
                    )
                    StatBadge(
                        label = stringResource(R.string.stat_session_count),
                        value = stats.sessionsWithLaps.toString(),
                        valueStyle = KartTextStyles.lapTime,
                        modifier = Modifier.weight(0.6f),
                    )
                }

                if (!state.hasEnoughData) {
                    KartCard(Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.analysis_not_enough),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.kartColors.textMuted,
                        )
                    }
                } else {
                    EvolutionSection(stats)
                    KartRankingSection(stats.karts)
                    ConditionsSection(stats.conditions)
                }
            }
        }
    }

    if (showPicker) {
        CircuitPickerSheet(
            circuits = state.circuits,
            selectedId = state.stats?.circuit?.id,
            onSelect = {
                viewModel.selectCircuit(it.id)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.kartColors.textMuted,
    )
}

@Composable
private fun EvolutionSection(stats: CircuitStats) {
    val evolution = stats.evolution
    val best = evolution.minOf { it.bestMs }
    SectionTitle(stringResource(R.string.analysis_evolution))
    KartCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
        TimeLineChart(
            x = evolution.indices.map { it.toDouble() },
            timesMs = evolution.map { it.bestMs },
            xLabel = { value -> evolution.getOrNull(value.toInt())?.let { formatShortDate(it.dateTime) } ?: "" },
            highlightIndex = evolution.indexOfFirst { it.bestMs == best },
            highlightLabel = stringResource(R.string.stat_record),
            xSpacing = ((evolution.size + 4) / 5).coerceAtLeast(1),
        )
    }
}

@Composable
private fun KartRankingSection(karts: List<KartStats>) {
    SectionTitle(stringResource(R.string.analysis_kart_ranking))
    if (karts.isEmpty()) {
        Text(
            text = stringResource(R.string.analysis_no_karts),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.kartColors.textMuted,
        )
        return
    }
    val kart = MaterialTheme.kartColors
    KartCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(stringResource(R.string.col_kart), Modifier.weight(0.9f))
            HeaderCell(stringResource(R.string.col_best), Modifier.weight(1.3f))
            HeaderCell(stringResource(R.string.col_clean_average), Modifier.weight(1.3f))
            HeaderCell(stringResource(R.string.col_sessions), Modifier.weight(0.7f))
        }
        karts.forEachIndexed { index, k ->
            HorizontalDivider(color = kart.cardBorder, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}. #${k.kartNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.9f),
                    maxLines = 1,
                )
                MonoCell(
                    text = LapTime.format(k.bestMs),
                    color = if (index == 0) kart.telemetry else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.3f),
                )
                MonoCell(
                    text = k.cleanAverageMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.3f),
                )
                MonoCell(text = k.sessionCount.toString(), color = kart.textMuted, modifier = Modifier.weight(0.7f))
            }
        }
    }
}

@Composable
private fun ConditionsSection(conditions: List<ConditionStats>) {
    SectionTitle(stringResource(R.string.analysis_conditions))
    val kart = MaterialTheme.kartColors
    val dry = conditions.firstOrNull { it.condition == TrackCondition.DRY }
    KartCard(Modifier.fillMaxWidth()) {
        conditions.forEachIndexed { index, c ->
            if (index > 0) HorizontalDivider(color = kart.cardBorder, modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = c.condition.icon(),
                    contentDescription = null,
                    tint = if (c.condition == TrackCondition.DRY) kart.textMuted else kart.warning,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(c.condition.labelRes()),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.format_session_count, c.sessionCount, c.sessionCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kart.textMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = LapTime.format(c.averageBestMs),
                        style = KartTextStyles.lapTime.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (dry != null && c.condition != TrackCondition.DRY) {
                        Text(
                            text = LapTime.formatDelta(c.averageBestMs - dry.averageBestMs),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                            color = kart.textMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.kartColors.textMuted,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
private fun MonoCell(text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
        color = color,
        modifier = modifier,
        maxLines = 1,
        softWrap = false,
    )
}
