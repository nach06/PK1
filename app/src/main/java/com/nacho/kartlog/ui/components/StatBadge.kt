package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors

/** Etiqueta pequeña + valor grande, p. ej. "MEJOR 0:48.532". */
@Composable
fun StatBadge(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle = KartTextStyles.lapTime,
    /** Tamaño mínimo al que puede encoger el valor para caber en una línea. */
    minValueSize: TextUnit = 28.sp,
    /** Si no es nulo, reserva este alto para el valor (alinea varias cifras de tamaños distintos). */
    valueAreaHeight: Dp? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.kartColors.textMuted,
        )
        val area = if (valueAreaHeight != null) Modifier.height(valueAreaHeight) else Modifier
        // 4 dp de margen interior a la derecha: el último dígito nunca roza el borde.
        Box(modifier = area.padding(end = 4.dp), contentAlignment = Alignment.CenterStart) {
            AutoSizeText(text = value, style = valueStyle, color = valueColor, minSize = minValueSize)
        }
    }
}
