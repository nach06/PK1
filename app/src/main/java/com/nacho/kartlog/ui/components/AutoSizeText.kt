package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Texto de una sola línea que reduce su tamaño (de [style].fontSize hasta [minSize]) hasta caber en
 * el ancho disponible. Nunca se parte en dos líneas.
 *
 * El tamaño se calcula midiendo en la propia composición, sin estados intermedios: la versión anterior ocultaba el texto
 * hasta recibir `onTextLayout` y lo reiniciaba en cada cambio de texto, de modo que un cronómetro que cambia cada 50 ms
 * no se llegaba a dibujar nunca (Sprint 6, A1).
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minSize: TextUnit = 28.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidthPx = constraints.maxWidth
        val maxHeightPx = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val size = if (constraints.hasBoundedWidth) fittingSize(measurer, text, style, minSize, maxWidthPx, maxHeightPx) else style.fontSize
        Text(
            text = text,
            style = style.copy(fontSize = size),
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun fittingSize(measurer: TextMeasurer, text: String, style: TextStyle, minSize: TextUnit, maxWidthPx: Int, maxHeightPx: Int): TextUnit {
    val max = style.fontSize
    val floor = if (minSize.value < max.value) minSize else max
    fun fits(size: TextUnit): Pair<Boolean, Float> {
        val box = measurer.measure(text, style.copy(fontSize = size), softWrap = false, maxLines = 1, constraints = Constraints()).size
        val scale = minOf(maxWidthPx / box.width.toFloat(), maxHeightPx / box.height.toFloat())
        return (box.width <= maxWidthPx && box.height <= maxHeightPx) to scale
    }
    var size = max.value
    // El ancho y el alto son casi proporcionales al cuerpo: un salto proporcional y unos ajustes finos.
    repeat(5) {
        val (ok, scale) = fits(size.sp)
        if (ok || size <= floor.value) return size.sp.let { if (it.value < floor.value) floor else it }
        size = (size * scale - 0.5f).coerceAtLeast(floor.value)
    }
    return size.sp
}
