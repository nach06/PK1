package com.nacho.kartlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeKeypad(
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    /** Alto preferido de cada tecla; se reduce si el alto disponible no alcanza. */
    keyHeight: Dp = 64.dp,
) {
    val backspaceDescription = stringResource(R.string.keypad_backspace)
    // Cuadrícula propia: cada fila mide min(keyHeight, alto disponible / 4), de modo que el teclado
    // encoge en lugar de recortarse cuando el panel no tiene sitio para todo.
    Layout(
        modifier = modifier,
        content = {
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9").forEach { key ->
                Key(text = key, onClick = { onDigits(key) })
            }
            Key(text = "00", onClick = { onDigits("00") })
            Key(text = "0", onClick = { onDigits("0") })
            Key(text = "⌫", description = backspaceDescription, onClick = onBackspace, onLongClick = onClear)
        },
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 3 * keyHeight.roundToPx()
        val cellWidth = ((width - 2 * gap) / 3).coerceAtLeast(0)
        val preferred = keyHeight.roundToPx()
        val fitting = if (constraints.hasBoundedHeight) (constraints.maxHeight - 3 * gap) / 4 else preferred
        val cellHeight = minOf(preferred, fitting).coerceAtLeast(0)
        val placeables = measurables.map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
        layout(width, 4 * cellHeight + 3 * gap) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative((index % 3) * (cellWidth + gap), (index / 3) * (cellHeight + gap))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val kart = MaterialTheme.kartColors
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .clip(shape)
            .background(kart.card)
            .border(BorderStroke(1.dp, kart.cardBorder), shape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = KartTextStyles.keypadKey, color = MaterialTheme.colorScheme.onSurface)
    }
}
