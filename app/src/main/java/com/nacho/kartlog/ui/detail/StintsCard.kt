package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.analytics.Analytics
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.model.Stint
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors

/** Estadísticas de un stint calculadas con las vueltas que le quedan. */
internal data class StintStats(val stint: Stint, val lapCount: Int, val bestMs: Long?, val cleanAverageMs: Long?)

internal fun stintStats(stints: List<Stint>, laps: List<Lap>): List<StintStats> = stints.map { stint ->
    val times = laps.filter { it.stintId == stint.id }.map { it.timeMs }
    StintStats(stint, times.size, times.minOrNull(), Analytics.cleanAverageMs(times))
}

/** «Stints · N» plegable: nº de vueltas, mejor y media limpia por stint, y el tiempo total en boxes. */
@Composable
fun StintsCard(stints: List<Stint>, laps: List<Lap>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val kart = MaterialTheme.kartColors
    val stats = stintStats(stints, laps)
    val pitTotal = stints.mapNotNull { it.pitMs }.sum()
    KartCard(modifier.fillMaxWidth().testTag("stints_card")) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.stints_title, stints.size).uppercase() + if (expanded) " ▾" else " ▸",
                style = MaterialTheme.typography.labelMedium,
                color = kart.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (pitTotal > 0) {
                Text(
                    text = stringResource(R.string.stints_pit_total, LapTime.format(pitTotal)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = kart.warning,
                    modifier = Modifier.testTag("stints_pit_total"),
                )
            }
        }
        if (!expanded) return@KartCard
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
            stats.forEachIndexed { index, st ->
                Column(Modifier.fillMaxWidth().testTag("stint_row_$index")) {
                    Text(
                        text = stringResource(R.string.stint_row_title, index + 1) + (st.stint.driverName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = stringResource(R.string.stint_row_detail, st.stint.startLap, st.stint.endLap, st.lapCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = kart.textMuted,
                    )
                    Text(
                        text = stringResource(
                            R.string.stint_row_stats,
                            st.bestMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                            st.cleanAverageMs?.let(LapTime::format) ?: stringResource(R.string.value_unset),
                        ),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                    )
                    st.stint.pitMs?.let {
                        Text(
                            text = stringResource(R.string.stint_row_pit, LapTime.format(it)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = kart.warning,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StintsCardPreview() {
    KartLogTheme {
        StintsCard(
            stints = listOf(Stint(1, 1, 1, 3, "Nacho", 92_300), Stint(2, 1, 4, 6, "Alvaro", null)),
            laps = listOf(
                Lap(1, 1, 1, 73_400, LapSource.GPS, 1), Lap(2, 1, 2, 72_900, LapSource.GPS, 1), Lap(3, 1, 3, 73_100, LapSource.GPS, 1),
                Lap(4, 1, 4, 74_200, LapSource.GPS, 2), Lap(5, 1, 5, 73_800, LapSource.GPS, 2), Lap(6, 1, 6, 73_500, LapSource.GPS, 2),
            ),
        )
    }
}
