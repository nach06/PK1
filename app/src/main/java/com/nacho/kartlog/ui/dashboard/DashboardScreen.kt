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
import androidx.compose.foundation.layout.padding
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

private const val FINISH_HOLD_MS = 1_500
private const val SIGNAL_LOST_MS = 3_000L
private const val POOR_ACCURACY_M = 10f

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

    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved(it) } }
    LaunchedEffect(timingEvents, feedbackEnabled) {
        timingEvents.collect { if (feedbackEnabled && it is TimingEvent.LapClosed) feedback.lapClosed() }
    }

    when (state.timing.phase) {
        TimingPhase.RUNNING -> {
            KeepScreenOn()
            RunningDashboard(timing = state.timing, onFinish = viewModel::finish, modifier = Modifier.fillMaxSize())
        }
        TimingPhase.STOPPED -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { KartTopBar(title = stringResource(R.string.track_summary_title)) },
        ) { padding ->
            SummaryContent(
                state = state,
                onSave = viewModel::save,
                onDiscard = viewModel::discard,
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

/** Mantiene la pantalla encendida mientras se cronometra. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
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

// ---------------------------------------------------------------- En marcha

/** Pantalla completa de cronometraje; el reloj en vivo se anima entre posiciones del GPS. */
@Composable
fun RunningDashboard(timing: TimingState, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val kart = MaterialTheme.kartColors
    val live = LocalLiveClock.current
    var nowNs by remember { mutableLongStateOf(timing.lastFixArrivalNs ?: System.nanoTime()) }
    if (live) {
        LaunchedEffect(Unit) {
            while (true) {
                nowNs = System.nanoTime()
                delay(50)
            }
        }
    } else {
        nowNs = timing.lastFixArrivalNs ?: nowNs
    }
    val currentMs = timing.currentLapMs(nowNs)
    val signalLost = timing.lastFixArrivalNs?.let { (nowNs - it) / 1_000_000L > SIGNAL_LOST_MS } ?: false
    val delta = timing.deltaMs
    val deltaColor = when {
        delta == null -> kart.textMuted
        delta <= 0 -> kart.telemetry
        else -> MaterialTheme.colorScheme.primary
    }

    BoxWithConstraints(modifier.background(Color.Black).padding(16.dp)) {
        val landscape = maxWidth > maxHeight
        val bigStyle = MaterialTheme.typography.displayLarge.copy(
            fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = if (landscape) 110.sp else 92.sp,
        )
        val current: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    text = if (timing.started) stringResource(R.string.track_lap_n, timing.currentLapNumber).uppercase() else stringResource(R.string.track_waiting_start).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = kart.textMuted,
                    modifier = Modifier.testTag("dash_lap_number"),
                )
                AutoSizeText(
                    text = currentMs?.let(LapTime::format) ?: "–:––.–––",
                    style = bigStyle,
                    color = Color.White,
                    minSize = 40.sp,
                    modifier = Modifier.fillMaxWidth().testTag("dash_current"),
                )
            }
        }
        val details: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DashValue(stringResource(R.string.track_last), timing.lastLapMs?.let(LapTime::format) ?: stringResource(R.string.value_unset), Color.White, Modifier.weight(1f).testTag("dash_last"))
                    DashValue(stringResource(R.string.badge_best), timing.bestLapMs?.let(LapTime::format) ?: stringResource(R.string.value_unset), kart.telemetry, Modifier.weight(1f).testTag("dash_best"))
                }
                DashValue(
                    stringResource(R.string.track_delta),
                    delta?.let(LapTime::formatDelta) ?: stringResource(R.string.value_unset),
                    deltaColor,
                    Modifier.fillMaxWidth().testTag("dash_delta"),
                )
                GpsIndicator(timing, signalLost)
                if (timing.hasUnreliableLap) {
                    Text(stringResource(R.string.track_unreliable_lap), style = MaterialTheme.typography.bodyMedium, color = kart.warning)
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                current(Modifier.weight(1.3f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    details(Modifier.fillMaxWidth())
                    FinishHoldButton(onFinish)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                current(Modifier.fillMaxWidth().weight(1f))
                details(Modifier.fillMaxWidth())
                Spacer16()
                FinishHoldButton(onFinish)
            }
        }
    }
}

@Composable
private fun Spacer16() = Box(Modifier.height(16.dp))

@Composable
private fun DashValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.kartColors.textMuted)
        AutoSizeText(
            text = value,
            style = MaterialTheme.typography.displayLarge.copy(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 44.sp),
            color = color,
            minSize = 24.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GpsIndicator(timing: TimingState, signalLost: Boolean) {
    val kart = MaterialTheme.kartColors
    val acc = timing.accuracyM
    val poor = acc != null && acc > POOR_ACCURACY_M
    val color = when {
        signalLost || poor -> kart.warning
        acc == null -> kart.textMuted
        else -> kart.telemetry
    }
    val text = when {
        signalLost -> stringResource(R.string.track_signal_lost)
        acc == null -> stringResource(R.string.gps_waiting)
        else -> stringResource(R.string.gps_status, String.format(Locale.ROOT, "%.0f", acc), timing.rateHz?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—") +
            if (poor) "  ·  " + stringResource(R.string.track_poor_accuracy) else ""
    }
    Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.testTag("dash_gps"))
}

/**
 * Para terminar hay que mantener pulsado 1,5 s (con barra de progreso): sin menús ni botones que se puedan tocar
 * sin querer con los guantes puestos. Soltar antes deja la barra a cero y no hace nada.
 */
@Composable
internal fun FinishHoldButton(onConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val kart = MaterialTheme.kartColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(kart.card)
            .testTag("finish_hold")
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val hold = scope.launch {
                            progress.animateTo(1f, tween(FINISH_HOLD_MS, easing = LinearEasing))
                            onConfirmed()
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            hold.cancel()
                            scope.launch { progress.snapTo(0f) }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
        )
        Text(
            stringResource(R.string.track_hold_to_finish).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- Resumen

@Composable
private fun SummaryContent(state: DashboardUiState, onSave: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
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
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.kartColors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily))
    }
}

// ---------------------------------------------------------------- Previews

private fun sampleTiming() = TimingState(
    phase = TimingPhase.RUNNING,
    setup = null,
    laps = listOf(73_412, 72_986, 72_540, 73_105),
    currentLapStartNs = 100_000_000_000L,
    lastFixElapsedNs = 143_700_000_000L,
    lastFixArrivalNs = 5_000_000_000L,
    accuracyM = 3f,
    rateHz = 1.0,
    speedMps = 16f,
)

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 360, heightDp = 780)
@Composable
private fun RunningDashboardPortraitPreview() {
    KartLogTheme {
        CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(timing = sampleTiming(), onFinish = {}, modifier = Modifier.fillMaxSize())
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 780, heightDp = 360)
@Composable
private fun RunningDashboardLandscapePreview() {
    KartLogTheme {
        CompositionLocalProvider(LocalLiveClock provides false) {
            RunningDashboard(timing = sampleTiming().copy(accuracyM = 12f), onFinish = {}, modifier = Modifier.fillMaxSize())
        }
    }
}
