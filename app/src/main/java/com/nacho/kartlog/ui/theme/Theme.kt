package com.nacho.kartlog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val KartColorScheme = darkColorScheme(
    primary = PrimaryColor,
    onPrimary = Color.White,
    secondary = TelemetryColor,
    onSecondary = Color.Black,
    tertiary = WarningColor,
    onTertiary = Color.Black,
    background = BackgroundColor,
    onBackground = TextPrimaryColor,
    surface = SurfaceColor,
    onSurface = TextPrimaryColor,
    surfaceVariant = CardColor,
    onSurfaceVariant = TextMutedColor,
    surfaceContainerLowest = BackgroundColor,
    surfaceContainerLow = SurfaceColor,
    surfaceContainer = SurfaceColor,
    surfaceContainerHigh = CardColor,
    surfaceContainerHighest = CardColor,
    outline = CardBorderColor,
    outlineVariant = CardBorderColor,
    error = PrimaryColor,
    onError = Color.White,
)

val LocalKartColors = staticCompositionLocalOf { KartColors() }

val MaterialTheme.kartColors: KartColors
    @Composable
    @ReadOnlyComposable
    get() = LocalKartColors.current

/** Tema oscuro permanente: ignora el tema del sistema. */
@Composable
fun KartLogTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKartColors provides KartColors()) {
        MaterialTheme(
            colorScheme = KartColorScheme,
            typography = KartTypography,
            shapes = KartShapes,
            content = content,
        )
    }
}
