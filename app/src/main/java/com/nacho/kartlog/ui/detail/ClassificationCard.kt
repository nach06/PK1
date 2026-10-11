package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Participant
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartCard
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors

/**
 * Clasificación de la sesión importada, plegable (arranca cerrada). Tu fila va resaltada y el GAP es la
 * diferencia entre mejores vueltas respecto al líder (el de mejor vuelta).
 */
@Composable
fun ClassificationCard(participants: List<Participant>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val kart = MaterialTheme.kartColors
    val leaderBest = participants.mapNotNull { it.bestMs }.minOrNull()
    KartCard(modifier.fillMaxWidth().testTag("classification")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.session_ranking_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = kart.textMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = kart.textMuted,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RankingRow(
                    pos = stringResource(R.string.col_pos),
                    driver = stringResource(R.string.col_driver),
                    best = stringResource(R.string.badge_best).uppercase(),
                    gap = stringResource(R.string.col_gap),
                    header = true,
                    highlighted = false,
                )
                participants.forEach { p ->
                    val gap = if (p.bestMs != null && leaderBest != null && p.bestMs != leaderBest) {
                        LapTime.formatDelta(p.bestMs - leaderBest)
                    } else {
                        "—"
                    }
                    RankingRow(
                        pos = p.position?.toString() ?: "—",
                        driver = (p.name ?: "—") + (p.kartNumber?.let { " ($it)" } ?: "") +
                            if (p.isMe) " · " + stringResource(R.string.you) else "",
                        best = p.bestMs?.let(LapTime::format) ?: "—",
                        gap = gap,
                        header = false,
                        highlighted = p.isMe,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingRow(pos: String, driver: String, best: String, gap: String, header: Boolean, highlighted: Boolean) {
    val kart = MaterialTheme.kartColors
    val base = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge
    val color = when {
        header -> kart.textMuted
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .heightIn(min = 36.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(pos, style = base, color = color, modifier = Modifier.width(32.dp), maxLines = 1)
        Text(driver, style = base, color = color, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(best, style = base.copy(fontFamily = MonoFontFamily), color = color, maxLines = 1, softWrap = false)
        Text(
            gap,
            style = base.copy(fontFamily = MonoFontFamily),
            color = if (header) color else kart.textMuted,
            modifier = Modifier.width(76.dp).padding(start = 8.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ClassificationCardPreview() {
    KartLogTheme {
        ClassificationCard(
            participants = listOf(
                Participant(1, 1, 1, "Alonso", 4, false, 71_900, 73_100, 12),
                Participant(2, 1, 2, "Nacho", 26, true, 72_263, 73_193, 12),
                Participant(3, 1, 3, "Lucía", 7, false, 73_410, 74_000, 11),
            ),
        )
    }
}
