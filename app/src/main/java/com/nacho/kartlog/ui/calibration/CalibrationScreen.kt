package com.nacho.kartlog.ui.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.FinishCalibration
import com.nacho.kartlog.domain.gps.FinishVerification
import com.nacho.kartlog.domain.gps.GeoMath
import com.nacho.kartlog.domain.gps.GpsFix
import com.nacho.kartlog.domain.gps.LocalFrame
import com.nacho.kartlog.domain.gps.OvalTrack
import com.nacho.kartlog.domain.gps.TrackSimulator
import com.nacho.kartlog.domain.model.FinishLine
import com.nacho.kartlog.ui.components.BigStepper
import com.nacho.kartlog.ui.components.GpsPermissionGate
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import java.util.Locale
import kotlin.math.max

/** Acciones de la pantalla de meta (agrupadas para no arrastrar una docena de parámetros). */
internal class CalibrationActions(
    val onMark: () -> Unit,
    val onRetry: () -> Unit,
    val onSave: () -> Unit,
    val onEditSaved: () -> Unit,
    val onShiftForward: () -> Unit,
    val onShiftBackward: () -> Unit,
    val onWiden: () -> Unit,
    val onNarrow: () -> Unit,
    val onStartVerification: () -> Unit,
    val onConfirmVerification: () -> Unit,
    val onEndVerification: () -> Unit,
    val onDone: () -> Unit,
    /** Solo builds debug: marcar / verificar con un pase simulado. */
    val onDebugMark: (() -> Unit)? = null,
    val onDebugVerify: (() -> Unit)? = null,
)

@Composable
fun CalibrationScreen(viewModel: CalibrationViewModel, onBack: () -> Unit, onSaved: () -> Unit, debugShortcuts: Boolean = false) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        CalibrationActions(
            onMark = viewModel::mark, onRetry = viewModel::retry, onSave = viewModel::save, onEditSaved = viewModel::editSaved,
            onShiftForward = viewModel::shiftForward, onShiftBackward = viewModel::shiftBackward,
            onWiden = viewModel::widen, onNarrow = viewModel::narrow,
            onStartVerification = viewModel::startVerification, onConfirmVerification = viewModel::confirmVerification,
            onEndVerification = viewModel::endVerification, onDone = onSaved,
            onDebugMark = if (debugShortcuts) viewModel::debugSimulateMark else null,
            onDebugVerify = if (debugShortcuts) viewModel::debugSimulateVerify else null,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.calibration_title), onBack = onBack) },
    ) { padding ->
        GpsPermissionGate(
            title = stringResource(R.string.permission_gps_title),
            message = stringResource(R.string.permission_gps_message),
            modifier = Modifier.padding(padding),
        ) {
            LaunchedEffect(viewModel) { viewModel.startListening() }
            CalibrationContent(state = state, actions = actions, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
internal fun CalibrationContent(
    state: CalibrationUiState,
    actions: CalibrationActions,
    modifier: Modifier = Modifier,
) {
    val kart = MaterialTheme.kartColors
    val compactPlot = state.hasPendingLine || state.saved || state.verifying
    // Cuerpo desplazable arriba y botones de acción fijos abajo: GUARDAR / MARCAR / VERIFICAR siempre a la vista.
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp).testTag("calibration_body"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.circuitName, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(
                stringResource(if (state.verifying) R.string.calibration_verify_instructions else R.string.calibration_instructions),
                style = MaterialTheme.typography.bodyLarge,
                color = kart.textMuted,
            )
            GpsStatusLine(state)
            if (actions.onDebugMark != null || actions.onDebugVerify != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.onDebugMark?.let { Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.debug_mark_sim), it, modifier = Modifier.testTag("debug_mark_sim")) } }
                    actions.onDebugVerify?.let { Box(Modifier.weight(1f)) { SecondaryButton(stringResource(R.string.debug_verify_sim), it, modifier = Modifier.testTag("debug_verify_sim")) } }
                }
            }
            if (state.locationError) {
                Text(stringResource(R.string.calibration_location_error), style = MaterialTheme.typography.bodyLarge, color = kart.warning)
            }

            KartCard(Modifier.fillMaxWidth()) {
                TracePlot(
                    trace = state.trace,
                    finish = state.displayLine,
                    confirmed = state.result != null || state.editingSaved || state.saved,
                    modifier = Modifier.fillMaxWidth().aspectRatio(if (compactPlot) 2.4f else 1.4f).testTag("calibration_plot"),
                )
            }

            state.failure?.let { failure ->
                Text(
                    text = stringResource(
                        when (failure) {
                            FinishCalibration.Failure.NO_FIXES -> R.string.calibration_fail_no_fixes
                            FinishCalibration.Failure.POOR_ACCURACY -> R.string.calibration_fail_accuracy
                            FinishCalibration.Failure.TOO_SLOW -> R.string.calibration_fail_slow
                            FinishCalibration.Failure.NOT_STRAIGHT -> R.string.calibration_fail_curve
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = kart.warning,
                    modifier = Modifier.testTag("calibration_failure"),
                )
            }

            when {
                state.verifying -> VerificationBody(state, actions)
                state.hasPendingLine -> PendingLineBody(state, actions)
                state.saved -> SavedBody()
                else -> LiveBody(state)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp).testTag("calibration_bar"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                state.verifying -> VerificationBar(state, actions)
                state.hasPendingLine -> PendingLineBar(state, actions)
                state.saved -> SavedBar(actions)
                else -> LiveBar(state, actions)
            }
        }
    }
}

@Composable
private fun LiveBody(state: CalibrationUiState) {
    if (state.currentFinish != null) {
        Text(stringResource(R.string.calibration_current_saved), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
    }
}

@Composable
private fun LiveBar(state: CalibrationUiState, actions: CalibrationActions) {
    PrimaryButton(
        text = stringResource(R.string.calibration_mark).uppercase(),
        onClick = actions.onMark,
        enabled = state.listening && state.fixCount > 0,
    )
    if (state.currentFinish != null) {
        SecondaryButton(stringResource(R.string.calibration_adjust).uppercase(), actions.onEditSaved)
        SecondaryButton(stringResource(R.string.calibration_verify).uppercase(), actions.onStartVerification, enabled = state.listening)
    }
}

/** Resultado del marcado (o meta guardada en edición): valores, ajuste manual y guardar. */
@Composable
private fun PendingLineBody(state: CalibrationUiState, actions: CalibrationActions) {
    val kart = MaterialTheme.kartColors
    val line = state.adjustedLine ?: return
    if (state.reversed) {
        Text(
            stringResource(R.string.calibration_reversed_warning),
            style = MaterialTheme.typography.bodyLarge,
            color = kart.warning,
            modifier = Modifier.testTag("calibration_reversed"),
        )
    }
    KartCard(Modifier.fillMaxWidth().testTag("calibration_values")) {
        ValueRow(stringResource(R.string.calibration_lat), String.format(Locale.ROOT, "%.6f°", line.lat))
        ValueRow(stringResource(R.string.calibration_lon), String.format(Locale.ROOT, "%.6f°", line.lon))
        ValueRow(stringResource(R.string.calibration_bearing), String.format(Locale.ROOT, "%.1f°", line.bearingDeg))
        ValueRow(stringResource(R.string.calibration_gate), "${line.gateWidthM} m")
        state.result?.let { ValueRow(stringResource(R.string.calibration_accuracy), String.format(Locale.ROOT, "±%.0f m", it.accuracyM)) }
    }
    KartCard(Modifier.fillMaxWidth()) {
        BigStepper(
            label = stringResource(R.string.calibration_shift),
            valueText = String.format(Locale.ROOT, "%+d m", state.shiftM),
            onDecrement = actions.onShiftBackward,
            onIncrement = actions.onShiftForward,
            modifier = Modifier.testTag("shift_stepper"),
        )
        BigStepper(
            label = stringResource(R.string.calibration_gate),
            valueText = "${state.gateWidthM} m",
            onDecrement = actions.onNarrow,
            onIncrement = actions.onWiden,
            modifier = Modifier.padding(top = 16.dp).testTag("width_stepper"),
        )
    }
}

@Composable
private fun PendingLineBar(state: CalibrationUiState, actions: CalibrationActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) { SecondaryButton(stringResource(if (state.editingSaved) R.string.action_cancel else R.string.calibration_retry).uppercase(), actions.onRetry) }
        Box(Modifier.weight(1f)) { PrimaryButton(stringResource(R.string.calibration_save).uppercase(), actions.onSave, modifier = Modifier.testTag("calibration_save")) }
    }
}

@Composable
private fun SavedBody() {
    Text(
        stringResource(R.string.calibration_saved),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.kartColors.telemetry,
        modifier = Modifier.testTag("calibration_saved"),
    )
    Text(stringResource(R.string.calibration_verify_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.kartColors.textMuted)
}

@Composable
private fun SavedBar(actions: CalibrationActions) {
    SecondaryButton(stringResource(R.string.calibration_verify).uppercase(), actions.onStartVerification)
    PrimaryButton(stringResource(R.string.calibration_done).uppercase(), actions.onDone)
}

@Composable
private fun VerificationBody(state: CalibrationUiState, actions: CalibrationActions) {
    val kart = MaterialTheme.kartColors
    when (val outcome = state.verification) {
        null -> Unit
        FinishVerification.Outcome.NoCrossing -> Text(
            stringResource(R.string.calibration_verify_none),
            style = MaterialTheme.typography.bodyLarge,
            color = kart.warning,
            modifier = Modifier.testTag("verification_none"),
        )
        is FinishVerification.Outcome.Checked -> {
            KartCard(Modifier.fillMaxWidth().testTag("verification_values")) {
                ValueRow(stringResource(R.string.calibration_verify_time), String.format(Locale.ROOT, "%+.2f s", outcome.timeDiffMs / 1000.0))
                ValueRow(stringResource(R.string.calibration_bearing), String.format(Locale.ROOT, "%.1f° (%+.1f°)", outcome.travelBearingDeg, outcome.bearingDiffDeg))
                ValueRow(stringResource(R.string.calibration_verify_lateral), String.format(Locale.ROOT, "%+.1f m", outcome.lateralOffsetM))
            }
            Text(
                stringResource(if (outcome.ok) R.string.calibration_verify_ok else R.string.calibration_verify_warn),
                style = MaterialTheme.typography.titleMedium,
                color = if (outcome.ok) kart.telemetry else kart.warning,
                modifier = Modifier.testTag(if (outcome.ok) "verification_ok" else "verification_warn"),
            )
        }
    }
}

@Composable
private fun VerificationBar(state: CalibrationUiState, actions: CalibrationActions) {
    when (val outcome = state.verification) {
        null -> PrimaryButton(
            text = stringResource(R.string.calibration_verify_now).uppercase(),
            onClick = actions.onConfirmVerification,
            enabled = state.listening && state.fixCount > 0,
        )
        FinishVerification.Outcome.NoCrossing ->
            SecondaryButton(stringResource(R.string.calibration_verify_again).uppercase(), actions.onStartVerification)
        is FinishVerification.Outcome.Checked -> {
            SecondaryButton(stringResource(R.string.calibration_verify_again).uppercase(), actions.onStartVerification)
            if (!outcome.ok) SecondaryButton(stringResource(R.string.calibration_adjust).uppercase(), { actions.onEndVerification(); actions.onEditSaved() })
        }
    }
    SecondaryButton(stringResource(R.string.calibration_verify_exit).uppercase(), actions.onEndVerification)
}

@Composable
private fun GpsStatusLine(state: CalibrationUiState) {
    val kart = MaterialTheme.kartColors
    val acc = state.accuracyM
    val color = when {
        acc == null -> kart.textMuted
        acc > 10f -> kart.warning
        else -> kart.telemetry
    }
    Text(
        text = if (acc == null) {
            stringResource(R.string.gps_waiting)
        } else {
            stringResource(R.string.gps_status, String.format(Locale.ROOT, "%.0f", acc), state.rateHz?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—")
        },
        style = MaterialTheme.typography.titleSmall,
        color = color,
        modifier = Modifier.testTag("gps_status"),
    )
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.kartColors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily))
    }
}

/**
 * Trazado reciente (norte arriba) con la puerta de meta: una línea perpendicular al rumbo y una flecha que indica
 * el sentido de paso. Se escala para que todo quepa; si no hay puerta solo se dibuja la traza.
 */
@Composable
internal fun TracePlot(trace: List<GpsFix>, finish: FinishLine?, confirmed: Boolean, modifier: Modifier = Modifier) {
    val kart = MaterialTheme.kartColors
    val traceColor = kart.telemetry
    val gateColor = if (confirmed) MaterialTheme.colorScheme.primary else kart.warning
    val dotColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val origin = finish?.let { LocalFrame(it.lat, it.lon) } ?: trace.lastOrNull()?.let { LocalFrame(it.lat, it.lon) } ?: return@Canvas
        val points = trace.map { origin.toXY(it.lat, it.lon) }
        val half = (finish?.gateWidthM ?: 20) / 2.0
        val dir = finish?.let { GeoMath.unit(it.bearingDeg) }
        // Extensión necesaria alrededor del origen para que quepan traza y puerta (+ margen).
        val extent = max(
            points.maxOfOrNull { max(kotlin.math.abs(it.x), kotlin.math.abs(it.y)) } ?: 0.0,
            half + 4.0,
        ) * 1.15 + 1.0
        val scale = (size.minDimension / 2f) / extent.toFloat()
        fun px(x: Double, y: Double) = Offset(size.width / 2f + (x * scale).toFloat(), size.height / 2f - (y * scale).toFloat())

        if (points.size >= 2) {
            for (i in 1 until points.size) {
                drawLine(traceColor, px(points[i - 1].x, points[i - 1].y), px(points[i].x, points[i].y), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }
        points.lastOrNull()?.let { drawCircle(dotColor, radius = 7f, center = px(it.x, it.y)) }
        if (finish != null && dir != null) {
            val nx = dir.y
            val ny = -dir.x
            drawLine(gateColor, px(nx * half, ny * half), px(-nx * half, -ny * half), strokeWidth = 8f, cap = StrokeCap.Round)
            // Flecha del sentido de paso desde el centro de la puerta.
            val tip = px(dir.x * half, dir.y * half)
            val tail = px(0.0, 0.0)
            drawLine(gateColor, tail, tip, strokeWidth = 4f, cap = StrokeCap.Round)
            val back = Offset(tip.x - (dir.x * 12).toFloat() , tip.y + (dir.y * 12).toFloat())
            drawLine(gateColor, tip, Offset(back.x + (dir.y * 8).toFloat(), back.y + (dir.x * 8).toFloat()), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(gateColor, tip, Offset(back.x - (dir.y * 8).toFloat(), back.y - (dir.x * 8).toFloat()), strokeWidth = 4f, cap = StrokeCap.Round)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 900)
@Composable
private fun CalibrationPreview() {
    val track = OvalTrack()
    val sim = TrackSimulator.simulate(track, listOf(58_400L), hz = 1.0)
    val cross = sim.crossingNs.first()
    val window = sim.fixes.filter { it.elapsedNs in (cross - 6_000_000_000L)..(cross + 1_000_000_000L) }
    val ok = FinishCalibration.compute(window) as FinishCalibration.Outcome.Ok
    KartLogTheme {
        CalibrationContent(
            state = CalibrationUiState(
                circuitName = "Karting Zuera", listening = true, fixCount = 14, accuracyM = 3f, rateHz = 1.0,
                trace = window, result = ok, shiftM = 3, gateWidthM = 25,
            ),
            actions = CalibrationActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
