package com.nacho.kartlog.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import com.nacho.kartlog.domain.model.TrackCondition

fun TrackCondition.icon(): ImageVector = when (this) {
    TrackCondition.DRY -> Icons.Default.WbSunny
    TrackCondition.DAMP -> Icons.Default.WaterDrop
    TrackCondition.WET -> Icons.Default.Umbrella
}
