package com.nacho.kartlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.ui.theme.KartTextStyles
import com.nacho.kartlog.ui.theme.kartColors
import kotlin.math.roundToInt

/** Slider con el valor en grande y marcas cada 5 °C. [valueText] formatea el valor mostrado. */
@Composable
fun TempSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueText: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
    range: IntRange = 0..60,
) {
    val kart = MaterialTheme.kartColors
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = kart.textMuted,
            )
            Text(text = valueText(value), style = KartTextStyles.value, color = MaterialTheme.colorScheme.onSurface)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            thumb = {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(primary, CircleShape),
                )
            },
            track = { state ->
                val span = (range.last - range.first).toFloat()
                val fraction = (state.value - range.first) / span
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
