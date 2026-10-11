package com.nacho.kartlog.ui.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nacho.kartlog.R
import com.nacho.kartlog.domain.model.Lap
import com.nacho.kartlog.domain.model.LapSource
import com.nacho.kartlog.domain.util.LapTime
import com.nacho.kartlog.ui.components.AutoSizeText
import com.nacho.kartlog.ui.components.PrimaryButton
import com.nacho.kartlog.ui.components.TimeKeypad
import com.nacho.kartlog.ui.theme.KartLogTheme
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.MonoFontFamily
import com.nacho.kartlog.ui.theme.kartColors

/**
 * Panel de entrada de tiempos fijado abajo. Plegado muestra solo el display y «ABRIR TECLADO»;
 * desplegado muestra el teclado completo y el botón de guardar.
 */
@Composable
fun LapEntryPanel(
    expanded: Boolean,
    timeText: String,
    hasDigits: Boolean,
    invalid: Boolean,
    canSave: Boolean,
    /** Etiqueta superior: «VUELTA 6» o «EDITANDO V3». */
    label: String,
    isEditing: Boolean,
    /** Últimas vueltas guardadas, la más reciente primero (se muestran las 2 primeras con el teclado abierto). */
    recentLaps: List<Lap>,
    onToggle: () -> Unit,
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kart = MaterialTheme.kartColors
    val timeColor = when {
        invalid -> MaterialTheme.colorScheme.error
        !hasDigits -> kart.textMuted
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(BorderStroke(1.dp, kart.cardBorder), shape),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (expanded) RecentLapsStrip(recentLaps.take(2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isEditing) kart.warning else kart.textMuted,
                        maxLines = 1,
                    )
                    AutoSizeText(
                        text = timeText,
                        style = if (expanded) KartTextStyles.timeEntry else KartTextStyles.timeEntry.copy(fontSize = 32.sp),
                        color = timeColor,
                        minSize = 24.sp,
                    )
                }
                if (expanded) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.action_close_keyboard),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Button(
                        onClick = onToggle,
                        modifier = Modifier.heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                        Text(
                            stringResource(R.string.action_open_keyboard).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            if (expanded) {
                if (invalid) {
                    Text(
                        text = stringResource(R.string.time_invalid),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TimeKeypad(
                    onDigits = onDigits,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    keyHeight = 52.dp,
                    // Cede altura si el panel no cabe: GUARDAR VUELTA nunca se recorta.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                )
                PrimaryButton(
                    text = stringResource(if (isEditing) R.string.action_update_lap else R.string.action_save_lap),
                    onClick = onSave,
                    enabled = canSave,
                )
                if (isEditing) {
                    OutlinedButton(
                        onClick = onCancelEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            stringResource(R.string.action_cancel_edit).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 393, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun LapEntryPanelCollapsedPreview() {
    KartLogTheme {
        LapEntryPanel(
            expanded = false, timeText = "0:00.000", hasDigits = false, invalid = false, canSave = false,
            label = "Vuelta 6", isEditing = false, recentLaps = emptyList(), onToggle = {}, onDigits = {}, onBackspace = {},
            onClear = {}, onSave = {}, onCancelEdit = {},
        )
    }
}

@Preview(widthDp = 393, heightDp = 520, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun LapEntryPanelExpandedPreview() {
    KartLogTheme {
        LapEntryPanel(
            expanded = true, timeText = "1:17.263", hasDigits = true, invalid = false, canSave = true,
            label = "Vuelta 11", isEditing = false,
            recentLaps = listOf(Lap(10, 1, 10, 72_858, LapSource.MANUAL), Lap(9, 1, 9, 72_951, LapSource.MANUAL)),
            onToggle = {}, onDigits = {}, onBackspace = {},
            onClear = {}, onSave = {}, onCancelEdit = {},
        )
    }
}

/** Franja con las últimas vueltas guardadas: `V10 1:12.858 · V9 1:12.951`. */
@Composable
private fun RecentLapsStrip(laps: List<Lap>) {
    if (laps.isEmpty()) return
    val text = laps.map { stringResource(R.string.recent_lap_item, it.number, LapTime.format(it.timeMs)) }
        .joinToString(" · ")
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFontFamily),
        color = MaterialTheme.kartColors.textMuted,
        maxLines = 1,
        softWrap = false,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}

/** F1: con poca altura (cabecera + panel en 470 dp) las teclas encogen y GUARDAR VUELTA sigue a la vista. */
@Preview(widthDp = 393, heightDp = 470, showBackground = true, backgroundColor = 0xFF0A0A0C)
@Composable
private fun LapEntryPanelTightPreview() {
    KartLogTheme {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(160.dp)) // hueco de la cabecera
            LapEntryPanel(
                expanded = true, timeText = "1:12.858", hasDigits = true, invalid = false, canSave = true,
                label = "Vuelta 11", isEditing = false,
                recentLaps = listOf(Lap(10, 1, 10, 72_858, LapSource.MANUAL), Lap(9, 1, 9, 72_951, LapSource.MANUAL)),
                onToggle = {}, onDigits = {}, onBackspace = {}, onClear = {}, onSave = {}, onCancelEdit = {},
            )
        }
    }
}
