package com.nacho.kartlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors

private val KeyRows = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
)

/**
 * Teclado numérico propio de 3×4. Emite dígitos sueltos ("0"–"9") o "00".
 * El retroceso borra un dígito; mantenerlo pulsado limpia todo ([onClear]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeKeypad(
    onDigits: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    keyHeight: Dp = 64.dp,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KeyRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Key(text = key, keyHeight = keyHeight, modifier = Modifier.weight(1f), onClick = { onDigits(key) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(text = "00", keyHeight = keyHeight, modifier = Modifier.weight(1f), onClick = { onDigits("00") })
            Key(text = "0", keyHeight = keyHeight, modifier = Modifier.weight(1f), onClick = { onDigits("0") })
            Key(
                text = "⌫",
                keyHeight = keyHeight,
                modifier = Modifier.weight(1f),
                description = stringResource(R.string.keypad_backspace),
                onClick = onBackspace,
                onLongClick = onClear,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(
    text: String,
    keyHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val kart = MaterialTheme.kartColors
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = keyHeight)
            .clip(shape)
            .background(kart.card)
            .border(BorderStroke(1.dp, kart.cardBorder), shape)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = KartTextStyles.keypadKey, color = MaterialTheme.colorScheme.onSurface)
    }
}
