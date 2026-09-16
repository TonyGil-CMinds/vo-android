package com.example.miprimeraapp.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle

private object ThemePalette {
    val Blue = Color(0xFF0085FF)
    val White = Color(0xFFFFFFFF)
    val Ice = Color(0xFFDBFFFF)
    val Black = Color(0xFF282727)
    val Red = Color(0xFFD94B3A)
}

val HeadingFontFamily = FontFamily.SansSerif

val BodyFontFamily = FontFamily.SansSerif

private fun TextStyle.withHeadingFont() = copy(
    fontFamily = HeadingFontFamily,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
)

private val MaterialDefaults = Typography()

// Preserve Material three font sizes, line height, and letter spacing.
val ThemeTypography = Typography(
    displayLarge = MaterialDefaults.displayLarge.withHeadingFont(),
    displayMedium = MaterialDefaults.displayMedium.withHeadingFont(),
    displaySmall = MaterialDefaults.displaySmall.withHeadingFont(),
    headlineLarge = MaterialDefaults.headlineLarge.withHeadingFont(),
    headlineMedium = MaterialDefaults.headlineMedium.withHeadingFont(),
    headlineSmall = MaterialDefaults.headlineSmall.withHeadingFont(),
    titleLarge = MaterialDefaults.titleLarge.copy(fontFamily = BodyFontFamily),
    titleMedium = MaterialDefaults.titleMedium.copy(fontFamily = BodyFontFamily),
    titleSmall = MaterialDefaults.titleSmall.copy(fontFamily = BodyFontFamily),
    bodyLarge = MaterialDefaults.bodyLarge.copy(fontFamily = BodyFontFamily),
    bodyMedium = MaterialDefaults.bodyMedium.copy(fontFamily = BodyFontFamily),
    bodySmall = MaterialDefaults.bodySmall.copy(fontFamily = BodyFontFamily),
    labelLarge = MaterialDefaults.labelLarge.copy(fontFamily = BodyFontFamily),
    labelMedium = MaterialDefaults.labelMedium.copy(fontFamily = BodyFontFamily),
    labelSmall = MaterialDefaults.labelSmall.copy(fontFamily = BodyFontFamily),
)

// La identidad visual usa siempre una superficie clara, incluso en modo oscuro del sistema.
private val AppColors = lightColorScheme(
    primary = ThemePalette.Blue,
    onPrimary = ThemePalette.Black,
    primaryContainer = ThemePalette.Ice,
    onPrimaryContainer = ThemePalette.Black,
    inversePrimary = ThemePalette.Ice,
    secondary = ThemePalette.Black,
    onSecondary = ThemePalette.White,
    secondaryContainer = ThemePalette.Ice,
    onSecondaryContainer = ThemePalette.Black,
    tertiary = ThemePalette.Red,
    onTertiary = ThemePalette.Black,
    tertiaryContainer = ThemePalette.White,
    onTertiaryContainer = ThemePalette.Black,
    background = ThemePalette.White,
    onBackground = ThemePalette.Black,
    surface = ThemePalette.White,
    onSurface = ThemePalette.Black,
    surfaceVariant = ThemePalette.Ice,
    onSurfaceVariant = ThemePalette.Black,
    surfaceTint = ThemePalette.Blue,
    inverseSurface = ThemePalette.Black,
    inverseOnSurface = ThemePalette.White,
    error = ThemePalette.Red,
    onError = ThemePalette.Black,
    errorContainer = ThemePalette.White,
    onErrorContainer = ThemePalette.Black,
    outline = ThemePalette.Black,
    outlineVariant = ThemePalette.Ice,
    scrim = ThemePalette.Black,
    surfaceBright = ThemePalette.White,
    surfaceDim = ThemePalette.Ice,
    surfaceContainerLowest = ThemePalette.White,
    surfaceContainerLow = ThemePalette.White,
    surfaceContainer = ThemePalette.White,
    surfaceContainerHigh = ThemePalette.Ice,
    surfaceContainerHighest = ThemePalette.Ice,
)

@Composable
fun MiPrimeraAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = ThemeTypography,
        content = content,
    )
}

/** Fondo compartido; los Scaffold dentro deben usar containerColor = Color.Transparent. */
@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(ThemePalette.White, ThemePalette.Ice)),
        ),
    ) {
        content()
    }
}
