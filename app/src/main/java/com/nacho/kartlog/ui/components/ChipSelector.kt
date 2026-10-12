package com.nacho.kartlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.ui.theme.kartColors

/** Selección única en chips grandes (tipo de sesión, estado de pista, circuito…). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipSelector(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            Chip(text = label(option), selected = option == selected, onClick = { onSelect(option) })
        }
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, minHeight: androidx.compose.ui.unit.Dp = 56.dp, verticalPadding: androidx.compose.ui.unit.Dp = 12.dp) {
    val kart = MaterialTheme.kartColors
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else kart.card)
            .border(BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else kart.cardBorder), shape)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
