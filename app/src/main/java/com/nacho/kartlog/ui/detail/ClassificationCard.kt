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
fun ClassificationCard(participants: List<Participant>, modifier: Modifier = Modifier, myLapTimesMs: List<Long> = emptyList()) {
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
                text = stringResource(R.string.session_ranking_header, participants.size) + if (expanded) " ▾" else " ▸",
                style = MaterialTheme.typography.labelMedium,
                color = kart.textMuted,
                modifier = Modifier.weight(1f),
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RankingRow(
                    note = null,
                    pos = stringResource(R.string.col_pos),
                    kartLabel = null,
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
                    val official = if (p.isMe) com.nacho.kartlog.domain.analytics.Analytics.officialAverageNote(p.avgMs, myLapTimesMs) else null
                    RankingRow(
                        note = official?.let { stringResource(R.string.official_average, LapTime.format(it)) },
                        pos = p.position?.toString() ?: "—",
                        kartLabel = p.kartNumber?.let { "K$it" },
                        driver = (p.name ?: "—") + if (p.isMe) " · " + stringResource(R.string.you) else "",
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

/** Colores de la fila propia: texto claro sobre un rojo oscuro (contraste ≥ 4,5:1, comprobado en test). */
internal object RankingColors {
    val highlightBackground = Color(0xFFB71C00)
    val highlightText = Color(0xFFF5F5F5)
}

/** Relación de contraste WCAG entre dos colores opacos (1…21). */
internal fun contrastRatio(a: Color, b: Color): Double {
    fun lin(c: Float): Double = if (c <= 0.03928f) c / 12.92 else Math.pow(((c + 0.055) / 1.055), 2.4)
    fun lum(c: Color) = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
    val l1 = lum(a)
    val l2 = lum(b)
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

@Composable
private fun RankingRow(note: String?, pos: String, kartLabel: String?, driver: String, best: String, gap: String, header: Boolean, highlighted: Boolean) {
    val kart = MaterialTheme.kartColors
    val base = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = when {
        header -> kart.textMuted
        highlighted -> RankingColors.highlightText
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (highlighted) RankingColors.highlightBackground else Color.Transparent)
            .testTag(if (highlighted) "ranking_me" else "ranking_row")
            .heightIn(min = 36.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(36.dp)) {
            Text(pos, style = base, color = color, maxLines = 1)
            if (kartLabel != null) Text(kartLabel, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(end = 4.dp)) {
            Text(driver, style = base, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 2, modifier = Modifier.testTag("official_avg_note"))
        }
        Text(best, style = base.copy(fontFamily = MonoFontFamily), color = color, maxLines = 1, softWrap = false)
        Text(
            gap,
            style = base.copy(fontFamily = MonoFontFamily),
            color = if (header || highlighted) color else kart.textMuted,
            modifier = Modifier.width(64.dp).padding(start = 4.dp),
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
