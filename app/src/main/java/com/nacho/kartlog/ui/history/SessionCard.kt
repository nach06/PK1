package com.nacho.kartlog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Thermostat
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.analytics.SessionStats
import com.nacho.kartlog.domain.model.Circuit
import com.nacho.kartlog.domain.model.Session
import com.nacho.kartlog.domain.model.SessionSummary
import com.nacho.kartlog.domain.model.SessionType
import com.nacho.kartlog.domain.model.TrackCondition
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.AutoSizeText
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.components.Sparkline
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors
import com.nacho.kartlog.ui.util.formatDateTime
import com.nacho.kartlog.ui.util.icon
import com.nacho.kartlog.ui.util.labelRes

/** Card de tanda del histórico: circuito + delta, mejor vuelta, datos, condiciones y sparkline. */
@Composable
fun SessionCard(stats: SessionStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val summary = stats.summary
    val session = summary.session
    val kart = MaterialTheme.kartColors
    val hasLaps = stats.lapTimes.isNotEmpty()

    KartCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        // 1. Circuito y, a la derecha, RÉCORD o diferencia con el récord.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary.circuit.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            when {
                stats.isRecord -> Text(
                    text = stringResource(R.string.badge_record),
                    style = MaterialTheme.typography.labelLarge,
                    color = kart.telemetry,
                    maxLines = 1,
                )
                stats.deltaToRecordMs != null -> Text(
                    text = LapTime.formatDelta(stats.deltaToRecordMs),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                    color = kart.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // 2. Mejor vuelta grande en verde (o «Sin vueltas»).
        if (hasLaps && stats.bestMs != null) {
            AutoSizeText(
                text = LapTime.format(stats.bestMs),
                style = KartTextStyles.bestLap,
                color = kart.telemetry,
            )
        } else {
            Text(
                text = stringResource(R.string.history_no_laps),
                style = MaterialTheme.typography.titleMedium,
                color = kart.textMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // 3. Fecha, tipo, kart.
        val line = buildList {
            add(formatDateTime(session.dateTime))
            add(stringResource(session.type.labelRes()))
            session.kartNumber?.let { add(stringResource(R.string.format_kart, it)) }
        }.joinToString(" · ")
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = kart.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // 4. Condiciones con iconos.
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val conditionColor = if (session.trackCondition == TrackCondition.DRY) kart.textMuted else kart.warning
            IconText(
                icon = { Icon(session.trackCondition.icon(), null, Modifier.size(20.dp), tint = conditionColor) },
                text = stringResource(session.trackCondition.labelRes()),
                color = conditionColor,
            )
            val temps = buildList {
                session.ambientTempC?.let { add(stringResource(R.string.format_temp_ambient_short, it.toInt())) }
                session.trackTempC?.let { add(stringResource(R.string.format_temp_track_short, it.toInt())) }
            }
            if (temps.isNotEmpty()) {
                IconText(
                    icon = { Icon(Icons.Default.Thermostat, null, Modifier.size(20.dp), tint = kart.textMuted) },
                    text = temps.joinToString(" · "),
                    color = kart.textMuted,
                )
            }
        }

        // 5. Nº de vueltas, media limpia y sparkline.
        if (hasLaps) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pluralStringResource(R.plurals.format_lap_count, stats.lapTimes.size, stats.lapTimes.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kart.textMuted,
                    )
                    Text(
                        text = stringResource(
                            R.string.format_clean_average,
                            stats.cleanAverageMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
                Sparkline(
                    values = stats.lapTimes,
                    lineColor = kart.telemetry.copy(alpha = 0.7f),
                    highlightColor = kart.telemetry,
                    modifier = Modifier
                        .width(120.dp)
                        .height(40.dp),
                )
            }
        }
    }
}

@Composable
private fun IconText(icon: @Composable () -> Unit, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1)
    }
}

private fun previewStats(
    id: Long,
    times: List<Long>,
    delta: Long?,
    record: Boolean,
    condition: TrackCondition = TrackCondition.DRY,
) = SessionStats(
    summary = SessionSummary(
        session = Session(id, 1, 1_760_000_000_000, SessionType.RACE, 7, 4f, 22f, 31f, condition, ""),
        circuit = Circuit(1, "Circuito Fernando Alonso", 1400),
        lapCount = times.size,
        bestLapMs = times.minOrNull(),
    ),
    lapTimes = times,
    bestMs = times.minOrNull(),
    cleanAverageMs = times.takeIf { it.isNotEmpty() }?.let { com.nacho.kartlog.domain.analytics.Analytics.cleanAverageMs(it) },
    consistencyMs = null,
    deltaToRecordMs = delta,
    isRecord = record,
)

@Preview(widthDp = 393, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun SessionCardPreview() {
    KartLogTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val times = listOf(84_100L, 72_900, 73_200, 72_600, 74_900, 72_800, 73_050, 72_450)
            SessionCard(previewStats(1, times, delta = 0, record = true), onClick = {})
            SessionCard(previewStats(2, times.map { it + 2_927 }, delta = 2_927, record = false, condition = TrackCondition.DAMP), onClick = {})
            SessionCard(previewStats(3, emptyList(), delta = null, record = false), onClick = {})
        }
    }
}
