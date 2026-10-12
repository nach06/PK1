package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.KartOverflowMenu
import com.nacho.kartlog.ui.components.MenuAction
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors

/**
 * Fila compacta de vuelta (mín. 56 dp): `V5` · tiempo · diferencia con la mejor (o `MEJOR`).
 * Tocar la fila entra en modo edición; editar y borrar también están en el menú ⋮.
 */
@Composable
fun LapRow(
    lap: Lap,
    bestLapMs: Long?,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kart = MaterialTheme.kartColors
    val isBest = bestLapMs != null && lap.timeMs == bestLapMs
    Column(modifier.testTag("lap_row")) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(if (isEditing) kart.warning.copy(alpha = 0.15f) else Color.Transparent)
                .clickable(onClick = onEdit)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.lap_short, lap.number),
                style = MaterialTheme.typography.titleSmall,
                color = if (isEditing) kart.warning else kart.textMuted,
                modifier = Modifier.width(if (lap.source == LapSource.IMPORTED || lap.source == LapSource.GPS) 36.dp else 52.dp),
                maxLines = 1,
            )
            if (lap.source == LapSource.GPS) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = stringResource(R.string.lap_gps),
                    tint = kart.textMuted,
                    modifier = Modifier.width(16.dp),
                )
            }
            if (lap.source == LapSource.IMPORTED) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = stringResource(R.string.lap_imported),
                    tint = kart.textMuted,
                    modifier = Modifier.width(16.dp),
                )
            }
            Text(
                text = LapTime.format(lap.timeMs),
                style = KartTextStyles.lapTime,
                color = if (isBest) kart.telemetry else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
            if (isBest) {
                Text(
                    text = stringResource(R.string.badge_best).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = kart.telemetry,
                    maxLines = 1,
                )
            } else if (bestLapMs != null) {
                Text(
                    text = LapTime.formatDelta(lap.timeMs - bestLapMs),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily),
                    color = kart.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            KartOverflowMenu(
                actions = listOf(
                    MenuAction(label = stringResource(R.string.action_edit), onClick = onEdit),
                    MenuAction(label = stringResource(R.string.action_delete), onClick = onDelete, destructive = true),
                ),
            )
        }
        HorizontalDivider(color = kart.cardBorder)
    }
}

@Preview(widthDp = 393, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun LapRowPreview() {
    KartLogTheme {
        Column {
            val laps = listOf(
                Lap(1, 1, 5, 77_263, LapSource.MANUAL),
                Lap(2, 1, 4, 80_190, LapSource.MANUAL),
                Lap(3, 1, 3, 78_001, LapSource.MANUAL),
            )
            laps.forEachIndexed { i, lap ->
                LapRow(lap = lap, bestLapMs = 77_263, isEditing = i == 2, onEdit = {}, onDelete = {})
            }
        }
    }
}
