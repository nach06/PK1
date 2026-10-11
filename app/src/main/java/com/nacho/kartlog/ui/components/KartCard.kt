package com.nacho.kartlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.ui.theme.CardShape
import com.nacho.kartlog.ui.theme.kartColors

/** Tarjeta base: fondo card, borde sutil y esquinas de 16 dp. Clicable si se pasa [onClick]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KartCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.kartColors.card)
    val border = BorderStroke(1.dp, MaterialTheme.kartColors.cardBorder)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = CardShape, colors = colors, border = border, content = inner)
    } else {
        Card(modifier = modifier, shape = CardShape, colors = colors, border = border, content = inner)
    }
}
