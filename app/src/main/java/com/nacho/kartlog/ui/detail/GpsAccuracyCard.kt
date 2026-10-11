package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.gps.GpsAccuracy
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import java.util.Locale

/**
 * «Precisión del GPS vs oficial»: aparece en las tandas que tienen vueltas `GPS` y vueltas `IMPORTED` (los tiempos
 * oficiales importados sobre la misma tanda). Plegable, como la clasificación.
 */
@Composable
fun GpsAccuracyCard(gpsMs: List<Long>, officialMs: List<Long>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val kart = MaterialTheme.kartColors
    val report = remember(gpsMs, officialMs, offset) { GpsAccuracy.compare(officialMs, gpsMs, offset) }
    KartCard(modifier.fillMaxWidth().testTag("gps_accuracy")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val mean = report.meanAbsErrorMs
            Text(
                text = stringResource(R.string.gps_accuracy_title).uppercase() + (if (expanded) " ▾" else " ▸"),
                style = MaterialTheme.typography.labelMedium,
                color = kart.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (mean != null) {
                Text(
                    text = "±" + LapTime.formatSeconds(mean) + " s",
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                    color = kart.telemetry,
                    modifier = Modifier.testTag("gps_accuracy_mean"),
                )
            }
        }
        if (!expanded) return@KartCard
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (report.countMismatch) {
                Text(
                    stringResource(R.string.gps_accuracy_mismatch, report.gpsCount, report.officialCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kart.warning,
                    modifier = Modifier.padding(bottom = 8.dp).testTag("gps_accuracy_mismatch"),
                )
            }
            AccuracyRow("", stringResource(R.string.gps_accuracy_official), stringResource(R.string.gps_accuracy_gps), stringResource(R.string.gps_accuracy_diff), header = true)
            report.rows.forEach { r ->
                AccuracyRow(
                    stringResource(R.string.lap_short, r.number),
                    LapTime.format(r.officialMs),
                    LapTime.format(r.gpsMs),
                    LapTime.formatDelta(r.diffMs),
                    header = false,
                )
            }
            report.meanAbsErrorMs?.let { mean ->
                Text(
                    text = stringResource(
                        R.string.gps_accuracy_summary,
                        LapTime.formatSeconds(mean),
                        LapTime.formatSeconds((report.maxAbsErrorMs ?: 0L).toDouble()),
                        (if ((report.meanSignedErrorMs ?: 0.0) >= 0) "+" else "−") + LapTime.formatSeconds(kotlin.math.abs(report.meanSignedErrorMs ?: 0.0)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp).testTag("gps_accuracy_summary"),
                )
            }
            report.systematicBiasMs?.let { bias ->
                Text(
                    text = stringResource(
                        R.string.gps_accuracy_bias,
                        (if (bias >= 0) "+" else "−") + LapTime.formatSeconds(kotlin.math.abs(bias)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kart.warning,
                    modifier = Modifier.padding(top = 8.dp).testTag("gps_accuracy_bias"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.gps_accuracy_offset, offset), style = MaterialTheme.typography.bodyMedium, color = kart.textMuted, modifier = Modifier.weight(1f))
                TextButton(onClick = { offset = (offset - 1).coerceAtLeast(-MAX_OFFSET) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("−") }
                TextButton(onClick = { offset = (offset + 1).coerceAtMost(MAX_OFFSET) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+") }
            }
        }
    }
}

private const val MAX_OFFSET = 3

@Composable
private fun AccuracyRow(lap: String, official: String, gps: String, diff: String, header: Boolean) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily)
    val color = if (header) MaterialTheme.kartColors.textMuted else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(lap, style = style, color = color, modifier = Modifier.width(40.dp), maxLines = 1)
        Text(official, style = style, color = color, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        Text(gps, style = style, color = color, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        Text(diff, style = style, color = if (header) color else MaterialTheme.kartColors.textMuted, modifier = Modifier.width(72.dp), maxLines = 1, softWrap = false)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun GpsAccuracyCardPreview() {
    KartLogTheme {
        GpsAccuracyCard(
            gpsMs = listOf(58_460, 57_820, 58_150, 59_340),
            officialMs = listOf(58_400, 57_900, 58_120, 59_300),
        )
    }
}
