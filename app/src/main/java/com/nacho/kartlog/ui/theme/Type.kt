package com.nacho.kartlog.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nacho.kartlog.R

/** Títulos y etiquetas. */
val DisplayFontFamily = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Tiempos y cifras: monoespaciada para que los dígitos no bailen. */
@OptIn(ExperimentalTextApi::class)
val MonoFontFamily = FontFamily(
    Font(
        R.font.jetbrains_mono,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.jetbrains_mono,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Estilos de cifras (no existen en la escala de Material). */
object KartTextStyles {
    /** Tiempo de vuelta: >= 28 sp. */
    val lapTime = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)

    /** Mejor vuelta destacada: 40 sp. */
    val bestLap = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp)

    /** Tiempo que se está tecleando. */
    val timeEntry = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp)

    val keypadKey = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp)

    /** Valor numérico de steppers y sliders. */
    val value = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp)
}

private fun style(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = DisplayFontFamily, fontWeight = weight, fontSize = size.sp)

// Cuerpo >= 16 sp, etiquetas >= 14 sp.
val KartTypography = Typography(
    displayLarge = style(40, FontWeight.Bold),
    headlineLarge = style(32, FontWeight.Bold),
    headlineMedium = style(28, FontWeight.Bold),
    headlineSmall = style(24, FontWeight.Bold),
    titleLarge = style(24, FontWeight.Bold),
    titleMedium = style(20, FontWeight.Bold),
    titleSmall = style(18, FontWeight.Bold),
    bodyLarge = style(18),
    bodyMedium = style(16),
    bodySmall = style(16),
    labelLarge = style(16, FontWeight.Bold),
    labelMedium = style(14, FontWeight.Bold),
    labelSmall = style(14, FontWeight.Bold),
)
