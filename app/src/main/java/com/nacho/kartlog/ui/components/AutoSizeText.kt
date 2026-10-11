package com.nacho.kartlog.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Texto de una sola línea que reduce su tamaño (de [style].fontSize hasta [minSize]) hasta caber en
 * el ancho disponible. Nunca se parte en dos líneas.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minSize: TextUnit = 28.sp,
) {
    val maxSize = style.fontSize
    val floor = if (minSize.value < maxSize.value) minSize else maxSize
    var size by remember(text, maxSize) { mutableStateOf(maxSize) }
    var ready by remember(text, maxSize) { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text = text,
            style = style.copy(fontSize = size),
            color = color,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { result ->
                if (result.didOverflowWidth && size.value > floor.value) {
                    size = (size.value - 1f).coerceAtLeast(floor.value).sp
                } else {
                    ready = true
                }
            },
            // No dibuja hasta encontrar el tamaño final, para evitar parpadeos.
            modifier = Modifier.drawWithContent { if (ready) drawContent() },
        )
    }
}
