package com.nacho.kartlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors
import kotlin.math.roundToInt

/**
 * Slider con el valor en grande y marcas cada 5 °C. Mientras [value] es `null` muestra «—» y el
 * pulgar queda atenuado en [idleValue]; al tocarlo toma un valor y aparece «Quitar» para volver a nulo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempSlider(
    label: String,
    value: Int?,
    onValueChange: (Int) -> Unit,
    onClear: () -> Unit,
    valueText: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    idleValue: Int = 20,
    range: IntRange = 0..60,
) {
    val kart = MaterialTheme.kartColors
    val primary = MaterialTheme.colorScheme.primary
    val shown = value ?: idleValue
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = kart.textMuted,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.action_clear), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                text = if (value != null) valueText(value) else stringResource(R.string.value_unset),
                style = KartTextStyles.value,
                color = if (value != null) MaterialTheme.colorScheme.onSurface else kart.textMuted,
            )
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            thumb = {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(if (value != null) primary else kart.textMuted, CircleShape),
                )
            },
            track = { state ->
                val span = (range.last - range.first).toFloat()
                val fraction = if (value != null) (state.value - range.first) / span else 0f
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                ) {
                    val trackHeight = 8.dp.toPx()
                    val top = (size.height - trackHeight) / 2
                    val corner = CornerRadius(trackHeight / 2)
                    drawRoundRect(kart.cardBorder, Offset(0f, top), Size(size.width, trackHeight), corner)
                    drawRoundRect(primary, Offset(0f, top), Size(size.width * fraction, trackHeight), corner)
                    // Marcas cada 5 °C.
                    var mark = range.first
                    while (mark <= range.last) {
                        if (mark % 5 == 0) {
                            val x = size.width * (mark - range.first) / span
                            val tall = mark % 10 == 0
                            val h = if (tall) 14.dp.toPx() else 8.dp.toPx()
                            drawLine(
                                color = kart.textMuted,
                                start = Offset(x, size.height - h),
                                end = Offset(x, size.height),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                        mark++
                    }
                }
            },
        )
    }
}
