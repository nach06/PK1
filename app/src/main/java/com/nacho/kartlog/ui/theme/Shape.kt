package com.nacho.kartlog.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(16.dp)

val KartShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = CardShape,
    large = CardShape,
    extraLarge = CardShape,
)
