package com.nacho.kartlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nacho.kartlog.R
import com.nacho.kartlog.ui.theme.kartColors

/**
 * Aviso «hay más abajo»: degradado y flecha al pie de una pantalla larga mientras se pueda seguir bajando. No recoge toques
 * (lo de debajo sigue siendo pulsable). En builds de depuración [onDebugJump] añade un botón «Ir al final» de un solo toque.
 */
@Composable
fun MoreBelowHint(visible: Boolean, modifier: Modifier = Modifier, onDebugJump: (() -> Unit)? = null) {
    if (!visible) return
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, background.copy(alpha = 0.92f))))
            .testTag("more_below"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.more_below),
            tint = MaterialTheme.kartColors.textMuted,
            modifier = Modifier.padding(bottom = 4.dp).size(32.dp),
        )
        if (onDebugJump != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.kartColors.warning)
                    .clickable(onClick = onDebugJump)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("debug_jump_end"),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.debug_jump_end).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
            }
        }
    }
}
