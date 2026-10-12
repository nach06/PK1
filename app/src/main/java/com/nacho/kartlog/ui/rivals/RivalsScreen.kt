package com.nacho.kartlog.ui.rivals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.analytics.RivalAnalytics
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatShortDate
import java.util.Locale
import kotlin.math.abs

/** Lista de rivales, «Unir pilotos» y efecto de las condiciones (Sprint 8, C1/C4/C6). */
@Composable
fun RivalsScreen(viewModel: RivalsViewModel, onBack: () -> Unit, onOpenRival: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var merging by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    val kart = MaterialTheme.kartColors

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.rivals_title), onBack = onBack) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.rivals.isEmpty()) {
                Text(
                    text = stringResource(R.string.rivals_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = kart.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("rivals_empty"),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { merging = !merging; picked = emptyList() },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("rivals_merge_toggle"),
                    ) { Text(stringResource(if (merging) R.string.rivals_merge_cancel else R.string.rivals_merge), maxLines = 1) }
                    if (state.hasMerges) {
                        OutlinedButton(
                            onClick = viewModel::clearMerges,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("rivals_merge_clear"),
                        ) { Text(stringResource(R.string.rivals_merge_clear), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (merging) {
                    Text(stringResource(R.string.rivals_merge_hint), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.testTag("rivals_merge_hint"))
                }
                state.rivals.forEach { rival ->
                    val selected = rival.key in picked
                    KartCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rival_row_${rival.key}")
                            .then(if (selected) Modifier.border(2.dp, kart.telemetry, MaterialTheme.shapes.medium) else Modifier)
                            .clickable {
                                if (merging) picked = if (selected) picked - rival.key else (picked + rival.key).takeLast(2)
                                else onOpenRival(rival.key)
                            },
                    ) { RivalSummary(rival) }
                }
            }
            ConditionsCard(state)
        }
    }

    if (merging && picked.size == 2) {
        val a = state.rivals.firstOrNull { it.key == picked[0] }
        val b = state.rivals.firstOrNull { it.key == picked[1] }
        if (a != null && b != null) {
            AlertDialog(
                modifier = Modifier.testTag("merge_dialog"),
                onDismissRequest = { picked = emptyList() },
                title = { Text(stringResource(R.string.rivals_merge_title)) },
                text = { Text(stringResource(R.string.rivals_merge_body, a.displayName, b.displayName)) },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.merge(a.key, b.key); picked = emptyList(); merging = false },
                        modifier = Modifier.testTag("merge_confirm"),
                    ) { Text(stringResource(R.string.rivals_merge_confirm)) }
                },
                dismissButton = { TextButton(onClick = { picked = emptyList() }) { Text(stringResource(R.string.rivals_merge_cancel)) } },
            )
        }
    }
}

@Composable
private fun RivalSummary(rival: RivalAnalytics.Rival) {
    val kart = MaterialTheme.kartColors
    Column {
        Text(rival.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            text = stringResource(
                R.string.rivals_row_summary,
                androidx.compose.ui.res.pluralStringResource(R.plurals.format_session_count, rival.sessions, rival.sessions),
                LapTime.format(rival.bestMs),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = kart.textMuted,
        )
        val diff = rival.meanAvgDiffMs
        Text(
            text = when {
                diff == null -> stringResource(R.string.rivals_pace_none)
                diff <= 0 -> stringResource(R.string.rivals_pace_faster, LapTime.formatSeconds(abs(diff).toDouble()))
                else -> stringResource(R.string.rivals_pace_slower, LapTime.formatSeconds(diff.toDouble()))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = when { diff == null -> kart.textMuted; diff <= 0 -> kart.telemetry; else -> kart.warning },
            modifier = Modifier.testTag("rival_pace_${rival.key}"),
        )
    }
}

@Composable
private fun ConditionsCard(state: RivalsUiState) {
    val kart = MaterialTheme.kartColors
    Text(stringResource(R.string.rivals_conditions_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = kart.textMuted)
    KartCard(Modifier.fillMaxWidth().testTag("conditions_card")) {
        val correlations = state.correlations
        if (correlations == null) {
            Text(
                text = stringResource(R.string.rivals_conditions_need, RivalAnalytics.MIN_SESSIONS_FOR_CONDITIONS, state.sessionsWithAverage),
                style = MaterialTheme.typography.bodyMedium,
                color = kart.textMuted,
                modifier = Modifier.testTag("conditions_need_more"),
            )
        } else {
            correlations.forEach { c ->
                val name = stringResource(
                    when (c.variable) {
                        RivalAnalytics.Variable.TRACK_TEMP -> R.string.rivals_cond_track
                        RivalAnalytics.Variable.AMBIENT_TEMP -> R.string.rivals_cond_ambient
                        RivalAnalytics.Variable.BALLAST -> R.string.rivals_cond_ballast
                    },
                )
                val reading = stringResource(
                    when {
                        abs(c.r) < 0.3 -> R.string.rivals_cond_none
                        c.r > 0 -> R.string.rivals_cond_slower
                        else -> R.string.rivals_cond_faster
                    },
                )
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("cond_${c.variable.name}")) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.rivals_cond_line, String.format(Locale.ROOT, "%+.2f", c.r), c.n, reading),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
                        color = kart.textMuted,
                    )
                    Scatter(c.points)
                }
            }
            Text(stringResource(R.string.rivals_cond_caveat), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Scatter(points: List<Pair<Double, Double>>) {
    val color = MaterialTheme.kartColors.telemetry
    val grid = MaterialTheme.kartColors.cardBorder
    val minX = points.minOf { it.first }
    val maxX = points.maxOf { it.first }.let { if (it - minX < 1e-9) minX + 1 else it }
    val limY = points.maxOf { abs(it.second) }.coerceAtLeast(0.5)
    Canvas(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 4.dp)) {
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
        points.forEach { (x, y) ->
            val px = ((x - minX) / (maxX - minX)).toFloat() * (size.width - 12f) + 6f
            val py = size.height / 2 - (y / limY).toFloat() * (size.height / 2 - 6f)
            drawCircle(color, radius = 5f, center = Offset(px, py))
        }
    }
}

/** Cara a cara con un rival (C2): ganadas/perdidas, mejor racha y la diferencia de media en cada tanda. */
@Composable
fun RivalDetailScreen(viewModel: RivalsViewModel, rivalKey: String, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rival = state.rivals.firstOrNull { it.key == rivalKey }
    val kart = MaterialTheme.kartColors
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = rival?.displayName ?: stringResource(R.string.rivals_title), onBack = onBack) },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        if (rival == null) {
            Text(stringResource(R.string.rival_not_found), modifier = Modifier.padding(padding).padding(24.dp).testTag("rival_not_found"), color = kart.textMuted)
            return@Scaffold
        }
        val h2h = RivalAnalytics.headToHead(rival)
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KartCard(Modifier.fillMaxWidth().testTag("h2h_card")) {
                RivalSummary(rival)
                Text(stringResource(R.string.rival_h2h_best, h2h.winsBest, h2h.lossesBest), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).testTag("h2h_best"))
                Text(stringResource(R.string.rival_h2h_avg, h2h.winsAvg, h2h.lossesAvg), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("h2h_avg"))
                Text(stringResource(R.string.rival_h2h_streak, h2h.bestStreak), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("h2h_streak"))
            }
            Text(stringResource(R.string.rival_diff_title).uppercase(), style = MaterialTheme.typography.labelMedium, color = kart.textMuted)
            KartCard(Modifier.fillMaxWidth().testTag("h2h_chart")) {
                val diffs = rival.encounters.filter { it.avgDiffMs != null }
                val max = diffs.maxOfOrNull { abs(it.avgDiffMs!!) }?.coerceAtLeast(1L) ?: 1L
                diffs.forEach { e ->
                    val d = e.avgDiffMs!!
                    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatShortDate(e.dateTime), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.width(64.dp), maxLines = 1)
                        Row(Modifier.weight(1f).height(10.dp)) {
                            Box(Modifier.weight(1f).height(10.dp), contentAlignment = Alignment.CenterEnd) {
                                if (d < 0) Box(Modifier.fillMaxWidth((abs(d).toFloat() / max).coerceIn(0.03f, 1f)).height(10.dp).background(kart.telemetry, RoundedCornerShape(5.dp)))
                            }
                            Box(Modifier.width(2.dp).height(10.dp).background(kart.cardBorder))
                            Box(Modifier.weight(1f).height(10.dp), contentAlignment = Alignment.CenterStart) {
                                if (d >= 0) Box(Modifier.fillMaxWidth((d.toFloat() / max).coerceIn(0.03f, 1f)).height(10.dp).background(kart.warning, RoundedCornerShape(5.dp)))
                            }
                        }
                        Text(LapTime.formatDelta(d), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily), modifier = Modifier.width(76.dp).padding(start = 8.dp), maxLines = 1, softWrap = false)
                    }
                }
                Text(stringResource(R.string.rival_diff_caption), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
