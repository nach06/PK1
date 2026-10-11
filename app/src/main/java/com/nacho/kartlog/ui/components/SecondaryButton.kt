package com.nacho.kartlog.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.ui.theme.CardBorderColor
import com.nacho.kartlog.ui.theme.TextMutedColor

/**
 * Botón secundario de ancho completo: contorno y texto en [color] (por defecto `primary`), 56 dp de alto.
 * El rojo relleno ([PrimaryButton]) queda reservado a la acción principal de cada pantalla.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(2.dp, if (enabled) color else CardBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = TextMutedColor,
        ),
    ) {
        Text(text = text.uppercase(), style = MaterialTheme.typography.titleMedium)
    }
}
