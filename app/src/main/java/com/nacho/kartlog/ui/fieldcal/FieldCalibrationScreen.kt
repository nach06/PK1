package com.nacho.kartlog.ui.fieldcal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.GpsPermissionGate
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.KartTopBar
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.SecondaryButton
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import java.util.Locale

@Composable
fun FieldCalibrationScreen(viewModel: FieldCalibrationViewModel, onBack: () -> Unit, debugShortcuts: Boolean = false) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kart = MaterialTheme.kartColors
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { KartTopBar(title = stringResource(R.string.fieldcal_title), onBack = onBack) },
    ) { padding ->
        GpsPermissionGate(
            title = stringResource(R.string.permission_gps_title),
            message = stringResource(R.string.permission_gps_message),
            modifier = Modifier.padding(padding),
        ) {
            val report = state.report
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (report == null) {
                        Text(stringResource(R.string.fieldcal_howto), style = MaterialTheme.typography.bodyLarge, color = kart.textMuted)
                        if (state.circuits.isEmpty()) {
                            Text(stringResource(R.string.fieldcal_no_circuit), style = MaterialTheme.typography.bodyLarge, color = kart.warning, modifier = Modifier.testTag("fieldcal_no_circuit"))
                        } else {
                            Text(state.circuits.firstOrNull { it.id == state.circuitId }?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("fieldcal_circuit"))
                        }
                        Text(
                            stringResource(R.string.fieldcal_status, state.crossings, state.taps) + (state.accuracyM?.let { "  ·  ±" + String.format(Locale.ROOT, "%.0f", it) + " m" } ?: ""),
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("fieldcal_status"),
                        )
                    } else {
                        ReportView(state, kart.telemetry, kart.warning, kart.textMuted)
                    }
                }
                if (report == null) {
                    if (!state.listening) {
                        PrimaryButton(stringResource(R.string.fieldcal_start).uppercase(), viewModel::start, enabled = state.circuitId != null, modifier = Modifier.testTag("fieldcal_start"))
                        if (debugShortcuts) SecondaryButton(stringResource(R.string.fieldcal_debug_sim), viewModel::debugSimulate, modifier = Modifier.testTag("fieldcal_debug"))
                    } else {
                        Button(
                            onClick = viewModel::tap,
                            modifier = Modifier.fillMaxWidth().height(160.dp).testTag("fieldcal_here"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = MaterialTheme.shapes.large,
                        ) { Text(stringResource(R.string.fieldcal_here).uppercase(), style = MaterialTheme.typography.displayLarge) }
                        SecondaryButton(stringResource(R.string.fieldcal_finish).uppercase(), viewModel::finish, modifier = Modifier.testTag("fieldcal_finish"))
                    }
                } else {
                    if (report.proposedDelayMs != null && state.appliedMs == null) {
                        PrimaryButton(stringResource(R.string.fieldcal_apply, report.proposedDelayMs!!).uppercase(), viewModel::apply, modifier = Modifier.testTag("fieldcal_apply"))
                    }
                    SecondaryButton(stringResource(R.string.fieldcal_again).uppercase(), viewModel::again, modifier = Modifier.testTag("fieldcal_again"))
                }
            }
        }
    }
}

@Composable
private fun ReportView(state: FieldCalUiState, good: androidx.compose.ui.graphics.Color, warn: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color) {
    val r = state.report ?: return
    KartCard(Modifier.fillMaxWidth().testTag("fieldcal_report")) {
        Text(stringResource(R.string.fieldcal_bias_title), style = MaterialTheme.typography.labelMedium, color = muted)
        Text(
            r.meanBiasMs?.let { String.format(Locale.ROOT, "%+.0f ms  (±%.0f)", it, r.stdevBiasMs ?: 0.0) } ?: stringResource(R.string.value_unset),
            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MonoFontFamily),
            modifier = Modifier.testTag("fieldcal_bias"),
        )
        Text(
            stringResource(
                when {
                    r.pairs.size < 3 -> R.string.fieldcal_verdict_few
                    r.constantBias -> R.string.fieldcal_verdict_constant
                    else -> R.string.fieldcal_verdict_none
                },
            ),
            style = MaterialTheme.typography.bodyMedium, color = if (r.constantBias) warn else muted, modifier = Modifier.testTag("fieldcal_verdict"),
        )
        if (r.unmatchedTaps > 0 || r.unmatchedCrossings > 0) {
            Text(stringResource(R.string.fieldcal_unmatched, r.unmatchedTaps, r.unmatchedCrossings), style = MaterialTheme.typography.bodyMedium, color = warn)
        }
    }
    KartCard(Modifier.fillMaxWidth().testTag("fieldcal_table")) {
        Row(Modifier.fillMaxWidth()) {
            listOf(R.string.fieldcal_col_lap, R.string.fieldcal_col_gps, R.string.fieldcal_col_manual, R.string.fieldcal_col_diff).forEach {
                Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        r.laps.forEach { lap ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("fieldcal_lap_${lap.lap}")) {
                val mono = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily)
                Text("V${lap.lap}", style = mono, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(LapTime.format(lap.gpsMs), style = mono, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(LapTime.format(lap.manualMs), style = mono, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Text(String.format(Locale.ROOT, "%+d", lap.diffMs), style = mono, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
    }
    state.appliedMs?.let {
        Text(stringResource(R.string.fieldcal_applied, it), style = MaterialTheme.typography.titleSmall, color = good, modifier = Modifier.testTag("fieldcal_applied"))
    }
}
