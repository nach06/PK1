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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.nacho.kartlog.ui.theme.KartLogTheme
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
import com.nacho.kartlog.ui.components.TimeLineChart
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
    // Gráfica y teclado son mutuamente excluyentes: abrir uno pliega el otro.
    var chartExpanded by rememberSaveable { mutableStateOf(false) }
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
            DetailBodyLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                header = {
                HeaderCard(
                    detail = detail,
                    state = state,
                    chartExpanded = chartExpanded,
                    compact = keypadExpanded,
                    onToggleChart = {
                        chartExpanded = !chartExpanded
                        if (chartExpanded) keypadExpanded = false
                    },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                },
                list = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("lap_list"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (state.laps.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.laps_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.kartColors.textMuted,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    } else {
                        // Más reciente primero: la última vuelta guardada queda junto al teclado.
                        items(state.laps.asReversed(), key = { it.id }) { lap ->
                            LapRow(
                                lap = lap,
                                bestLapMs = state.bestLapMs,
                                isEditing = lap.id == state.editingLap?.id,
                                onEdit = {
                                    viewModel.startEditing(lap)
                                    keypadExpanded = true
                                    chartExpanded = false
                                },
                                onDelete = { viewModel.requestDeleteLap(lap) },
                            )
                        }
                    }
                    if (state.participants.isNotEmpty()) {
                        item(key = "classification") {
                            ClassificationCard(
                                participants = state.participants,
                                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
                },
                panel = {
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
                    recentLaps = state.laps.takeLast(2).asReversed(),
                    onToggle = {
                        keypadExpanded = !keypadExpanded
                        if (keypadExpanded) chartExpanded = false
                    },
                    onDigits = viewModel::onDigits,
                    onBackspace = viewModel::onBackspace,
                    onClear = viewModel::onClear,
                    onSave = {
                        if (viewModel.saveLap()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onCancelEdit = viewModel::cancelEditing,
                )
                },
            )
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
private fun HeaderCard(
    detail: SessionDetail,
    state: SessionDetailUiState,
    chartExpanded: Boolean,
    onToggleChart: () -> Unit,
    modifier: Modifier = Modifier,
    /** Con el teclado abierto se oculta la información de la tanda para dejar sitio a la lista. */
    compact: Boolean = false,
) {
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
        if (!compact) {
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
        }
        StatsRow(
            bestLapMs = state.bestLapMs,
            cleanAverageMs = state.cleanAverageMs,
            consistencyMs = state.consistencyMs,
            modifier = Modifier.padding(top = if (compact) 0.dp else 8.dp),
        )
        // Gráfica de vueltas: plegable para no quitar sitio a la lista (necesita al menos 2 vueltas).
        if (state.laps.size >= 2) {
            TextButton(
                onClick = onToggleChart,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    text = stringResource(R.string.chart_title).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (chartExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (chartExpanded) {
                Box(Modifier.testTag("lap_chart")) {
                val bestIndex = state.laps.indexOfFirst { it.timeMs == state.bestLapMs }
                TimeLineChart(
                    x = state.laps.map { it.number.toDouble() },
                    timesMs = state.laps.map { it.timeMs },
                    xLabel = { it.toInt().toString() },
                    referenceMs = state.cleanAverageMs,
                    referenceLabel = state.cleanAverageMs?.let {
                        stringResource(R.string.format_clean_average, LapTime.format(it))
                    },
                    highlightIndex = bestIndex.takeIf { it >= 0 },
                    // Etiqueta cada vuelta (hasta 12) o cada 2 (hasta 24) según el ancho.
                    xSpacing = (state.laps.size + 11) / 12,
                    clipOutliers = true,
                )
                }
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

/** MEJOR y MEDIA LIMPIA en una fila; ambos valores encogen para caber en una sola línea. */
@Composable
internal fun StatsRow(
    bestLapMs: Long?,
    cleanAverageMs: Long?,
    modifier: Modifier = Modifier,
    consistencyMs: Double? = null,
) {
    val kart = MaterialTheme.kartColors
    val unset = stringResource(R.string.value_unset)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatBadge(
            label = stringResource(R.string.stat_best),
            value = bestLapMs?.let(LapTime::format) ?: unset,
            valueColor = if (bestLapMs != null) kart.telemetry else kart.textMuted,
            valueStyle = KartTextStyles.bestLap,
            valueAreaHeight = 52.dp,
            modifier = Modifier.weight(1.1f),
        )
        StatBadge(
            label = stringResource(R.string.stat_clean_average),
            value = cleanAverageMs?.let(LapTime::format) ?: unset,
            valueStyle = KartTextStyles.lapTime,
            // Puede bajar hasta 18 sp: nunca se recorta por el borde derecho.
            minValueSize = 18.sp,
            valueAreaHeight = 52.dp,
            caption = consistencyMs?.let(LapTime::formatConsistency),
            modifier = Modifier.weight(0.9f),
        )
    }
}

@Preview(widthDp = 393, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun StatsRowPreview() {
    KartLogTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatsRow(bestLapMs = 72_263, cleanAverageMs = 72_981, consistencyMs = 214.0) // 1:12.263 · 1:12.981 ±0,214
            StatsRow(bestLapMs = 47_388, cleanAverageMs = 47_901) // 0:47.388 · 0:47.901
        }
    }
}
