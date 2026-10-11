package com.nacho.kartlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Minigráfico de vueltas dibujado con `Canvas`, sin ejes. Un tiempo mayor (más lento) queda más
 * arriba. La mejor vuelta se resalta con un punto de [highlightColor].
 */
@Composable
fun Sparkline(
    values: List<Long>,
    lineColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val pad = 5.dp.toPx() // margen para que el punto resaltado no se recorte
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        val min = values.min()
        val max = values.max()
        val span = (max - min).coerceAtLeast(1L).toFloat()
        fun x(i: Int) = pad + if (values.size == 1) w / 2 else w * i / (values.size - 1)
        // Con todas las vueltas iguales la línea queda a media altura.
        fun y(v: Long) = pad + if (max == min) h / 2 else h * (1f - (v - min) / span)

        if (values.size > 1) {
            val path = Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        val bestIndex = values.indexOf(min)
        drawCircle(color = highlightColor, radius = 4.dp.toPx(), center = Offset(x(bestIndex), y(min)))
    }
}
