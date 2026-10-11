package com.nacho.kartlog.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

val BackgroundColor = Color(0xFF0A0A0C)
val SurfaceColor = Color(0xFF16161A)
val CardColor = Color(0xFF1F1F26)
val CardBorderColor = Color(0xFF2E2E38)
val PrimaryColor = Color(0xFFFF1E00)
val TelemetryColor = Color(0xFF00FF66)
val WarningColor = Color(0xFFFFB800)
val TextPrimaryColor = Color(0xFFF5F5F5)
val TextMutedColor = Color(0xFF9A9AA5)

/** Tokens propios que Material 3 no cubre (se leen con `MaterialTheme.kartColors`). */
@Immutable
data class KartColors(
    val card: Color = CardColor,
    val cardBorder: Color = CardBorderColor,
    val telemetry: Color = TelemetryColor,
    val warning: Color = WarningColor,
    val textMuted: Color = TextMutedColor,
)
