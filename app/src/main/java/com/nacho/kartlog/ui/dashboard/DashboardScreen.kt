package com.nacho.kartlog.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.GpsHealth
import com.nacho.kartlog.domain.gps.GpsSessionSetup
import com.nacho.kartlog.domain.gps.TimingEvent
import com.nacho.kartlog.domain.gps.TimingPhase
import com.nacho.kartlog.domain.gps.TimingState
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.AutoSizeText
import com.nacho.kartlog.ui.components.BigStepper
import com.nacho.kartlog.ui.components.ChipSelector
import com.nacho.kartlog.ui.components.CircuitPickerSheet
import com.nacho.kartlog.ui.components.GpsPermissionGate
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.components.TempSlider
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.LapFeedback
import com.nacho.kartlog.ui.util.labelRes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

/** Los tests desactivan el reloj en vivo (un bucle infinito de `delay` impediría que Compose llegue a «reposo»). */
val LocalLiveClock = compositionLocalOf { true }

/** Reloj monótono (ns) que lee el cronómetro en vivo; los tests lo sustituyen por uno manual. */
val LocalNanoClock = compositionLocalOf<() -> Long> { System::nanoTime }


@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    timingEvents: Flow<TimingEvent>,
    feedbackEnabled: Boolean,
    onBack: () -> Unit,
    onMarkFinish: (Long) -> Unit,
    onSaved: (Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = remember { LapFeedback(context) }
    var alert by remember { mutableStateOf<LapAlert?>(null) }
    val currentState by rememberUpdatedState(state)

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved(it) } }
    LaunchedEffect(timingEvents, feedbackEnabled) {
        timingEvents.collect { event ->
            if (event is TimingEvent.LapClosed) {
                if (feedbackEnabled) feedback.lapClosed(vibrationOnly = currentState.prefs.vibrationOnly)
                val laps = viewModel.uiState.value.timing.laps
                alert = LapAlert.of(
                    lapNumber = event.number,
                    lapMs = event.lapTimeMs,
                    previousBestMs = laps.take(event.number - 1).minOrNull(),
                    isBest = event.isBest,
                    circuitRecordMs = currentState.circuitRecordMs,
                )
            }
        }
    }
    // El aviso a pantalla completa dura 2 s; una vuelta nueva lo sustituye y reinicia la cuenta.
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(LapAlert.DURATION_MS)
            alert = null
        }
    }

    when (state.timing.phase) {
        TimingPhase.RUNNING -> {
            DashboardWindowEffects(state.prefs)
            val battery = rememberBatteryStatus()
            var confirmLeave by remember { mutableStateOf(false) }
            androidx.activity.compose.BackHandler { confirmLeave = true }
            if (confirmLeave) LeaveDialog(onStay = { confirmLeave = false }, onExit = { confirmLeave = false; onBack() })
            RunningDashboard(
                timing = state.timing,
                onFinish = viewModel::finish,
                onPit = viewModel::requestPit,
                prefs = state.prefs,
                alert = alert,
                battery = battery,
                modifier = Modifier.fillMaxSize(),
            )
        }
        TimingPhase.STOPPED -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { KartTopBar(title = stringResource(R.string.track_summary_title)) },
        ) { padding ->
            SummaryContent(
                state = state,
                onSave = viewModel::save,
                onDiscard = viewModel::discard,
                onToggleLap = viewModel::toggleLap,
                modifier = Modifier.padding(padding),
            )
        }
        TimingPhase.IDLE -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { KartTopBar(title = stringResource(R.string.track_title), onBack = onBack) },
        ) { padding ->
            GpsPermissionGate(
                title = stringResource(R.string.permission_gps_title),
                message = stringResource(R.string.permission_gps_message),
                modifier = Modifier.padding(padding),
            ) {
                PrepContent(
                    state = state,
                    viewModel = viewModel,
                    onMarkFinish = onMarkFinish,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (state.showCircuitPicker && state.timing.phase == TimingPhase.IDLE) {
        CircuitPickerSheet(
            circuits = state.circuits,
            selectedId = state.circuitId,
            onSelect = { viewModel.selectCircuit(it.id) },
            onDismiss = viewModel::dismissCircuitPicker,
        )
    }
}

/** Atrás con la tanda en marcha: seguir en pista o salir dejándola corriendo (A4). Terminar solo con la pulsación larga. */
@Composable
private fun LeaveDialog(onStay: () -> Unit, onExit: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onStay,
        containerColor = MaterialTheme.kartColors.card,
        title = { Text(stringResource(R.string.track_leave_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("leave_title")) },
        text = { Text(stringResource(R.string.track_leave_message), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onStay, modifier = Modifier.heightIn(min = 56.dp).testTag("leave_stay")) {
                Text(stringResource(R.string.track_leave_stay).uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onExit, modifier = Modifier.heightIn(min = 56.dp).testTag("leave_exit")) {
                Text(stringResource(R.string.track_leave_exit).uppercase(), style = MaterialTheme.typography.titleSmall)
            }
        },
    )
}

// ---------------------------------------------------------------- Preparación

@Composable
private fun PrepContent(
    state: DashboardUiState,
    viewModel: DashboardViewModel,
    onMarkFinish: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kart = MaterialTheme.kartColors
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.label_circuit).uppercase(), style = MaterialTheme.typography.labelMedium, color = kart.textMuted)
            SecondaryButton(
                text = (state.selectedCircuit?.name ?: stringResource(R.string.circuit_pick_hint)) + " ▾",
                onClick = viewModel::openCircuitPicker,
            )
            if (state.needsFinish) {
                KartCard(Modifier.fillMaxWidth().testTag("needs_finish")) {
                    Text(stringResource(R.string.track_needs_finish), style = MaterialTheme.typography.bodyLarge, color = kart.warning)
                    SecondaryButton(
                        text = stringResource(R.string.circuit_set_finish).uppercase(),
                        onClick = { state.selectedCircuit?.let { onMarkFinish(it.id) } },
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (state.circuits.isEmpty()) {
                Text(stringResource(R.string.track_no_circuits), style = MaterialTheme.typography.bodyLarge, color = kart.textMuted)
            }

            KartCard(Modifier.fillMaxWidth()) {
                BigStepper(
                    label = stringResource(R.string.label_kart_number),
                    valueText = state.kartNumber?.let { stringResource(R.string.format_kart_value, it) } ?: stringResource(R.string.value_unset),
                    onDecrement = viewModel::decrementKart,
                    onIncrement = viewModel::incrementKart,
                )
                BigStepper(
                    label = stringResource(R.string.label_ballast),
                    valueText = state.ballastKg?.let { stringResource(R.string.format_ballast_value, it) } ?: stringResource(R.string.value_unset),
                    onDecrement = viewModel::decrementBallast,
                    onIncrement = viewModel::incrementBallast,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            KartCard(Modifier.fillMaxWidth()) {
                TempSlider(
                    label = stringResource(R.string.label_ambient_temp),
                    value = state.ambientTempC,
                    onValueChange = viewModel::setAmbientTemp,
                    onClear = viewModel::clearAmbientTemp,
                    valueText = { stringResource(R.string.format_temp, it) },
                    idleValue = 20,
                )
                TempSlider(
                    label = stringResource(R.string.label_track_temp),
                    value = state.trackTempC,
                    onValueChange = viewModel::setTrackTemp,
                    onClear = viewModel::clearTrackTemp,
                    valueText = { stringResource(R.string.format_temp, it) },
                    idleValue = 30,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(stringResource(R.string.label_session_type).uppercase(), style = MaterialTheme.typography.labelMedium, color = kart.textMuted)
            Box(Modifier.testTag("type_selector")) {
                ChipSelector(
                    options = SessionType.entries,
                    selected = state.type,
                    onSelect = viewModel::selectType,
                    label = { stringResource(it.labelRes()) },
                )
            }
            Text(stringResource(R.string.label_track_condition).uppercase(), style = MaterialTheme.typography.labelMedium, color = kart.textMuted)
            ChipSelector(
                options = TrackCondition.entries,
                selected = state.condition,
                onSelect = viewModel::selectCondition,
                label = { stringResource(it.labelRes()) },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.track_day_mode), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.track_day_mode_hint), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted)
                }
                androidx.compose.material3.Switch(
                    checked = state.prefs.dayMode,
                    onCheckedChange = viewModel::setDayMode,
                    modifier = Modifier.testTag("switch_day_mode"),
                )
            }
        }
        Column(Modifier.padding(16.dp)) {
            PrimaryButton(
                text = stringResource(R.string.track_start).uppercase(),
                onClick = viewModel::start,
                enabled = state.canStart,
            )
        }
    }
}

// ---------------------------------------------------------------- Resumen

@Composable
private fun SummaryContent(state: DashboardUiState, onSave: () -> Unit, onDiscard: () -> Unit, onToggleLap: (Int) -> Unit, modifier: Modifier = Modifier) {
    val kart = MaterialTheme.kartColors
    val summary = state.summary
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        KartCard(Modifier.fillMaxWidth()) {
            SummaryRow(stringResource(R.string.track_summary_laps), (summary?.lapCount ?: 0).toString(), Modifier.testTag("summary_laps"))
            SummaryRow(stringResource(R.string.badge_best), summary?.bestMs?.let(LapTime::format) ?: stringResource(R.string.value_unset), Modifier.testTag("summary_best"))
            SummaryRow(stringResource(R.string.stat_clean_average), summary?.cleanAverageMs?.let(LapTime::format) ?: stringResource(R.string.value_unset), Modifier.testTag("summary_avg"))
        }
        if (summary?.hasUnreliableLap == true) {
            Text(stringResource(R.string.track_unreliable_lap), style = MaterialTheme.typography.bodyLarge, color = kart.warning)
            KartCard(Modifier.fillMaxWidth().testTag("unreliable_laps")) {
                summary.unreliableLaps.forEach { lap ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.lap_short, lap.number) + "  " + LapTime.format(lap.timeMs),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                color = if (lap.kept) MaterialTheme.colorScheme.onSurface else kart.textMuted,
                            )
                            Text(
                                stringResource(if (lap.kept) R.string.track_lap_kept else R.string.track_lap_discarded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (lap.kept) kart.warning else kart.textMuted,
                            )
                        }
                        SecondaryButton(
                            text = stringResource(if (lap.kept) R.string.import_lap_discard else R.string.import_lap_restore).uppercase(),
                            onClick = { onToggleLap(lap.number) },
                            modifier = Modifier.testTag("lap_toggle_${lap.number}"),
                        )
                    }
                }
            }
        }
        if ((summary?.lapCount ?: 0) == 0 || state.nothingToSave) {
            Text(stringResource(R.string.track_no_laps), style = MaterialTheme.typography.bodyLarge, color = kart.warning)
        }
        Box(Modifier.weight(1f))
        PrimaryButton(
            text = stringResource(R.string.track_save).uppercase(),
            onClick = onSave,
            enabled = !state.saving && (summary?.lapCount ?: 0) > 0,
        )
        SecondaryButton(stringResource(R.string.track_discard).uppercase(), onDiscard, color = kart.warning)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.kartColors.textMuted, modifier = Modifier.weight(1f))
        // La etiqueta de prueba va en el valor (no en la fila) para poder comprobar su texto.
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily), modifier = modifier)
    }
}

