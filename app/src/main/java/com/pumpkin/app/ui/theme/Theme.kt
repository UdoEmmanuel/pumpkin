package com.pumpkin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * User-selectable accent palettes (see ThemeStore) — chosen from the chat
 * list's overflow menu. Only the accent/container colors change; background,
 * surface, and text colors stay fixed so the app doesn't need a whole
 * second light/dark design. Deliberately does NOT touch the decoy
 * calculator, which always renders in [CalculatorColors] regardless of this
 * selection — see PumpkinNavHost's CALCULATOR route.
 */
// Each pair is the original hue, taken two shades darker (~35% reduced
// RGB) per the user's request — same hue, just deeper/less saturated-bright.
enum class AppColorTheme(val displayName: String, val accent: Color, val accentContainer: Color) {
    PUMPKIN_RED("Pumpkin Red", Color(0xFF921F31), Color(0xFF781827)),
    OCEAN_BLUE("Ocean Blue", Color(0xFF1F539A), Color(0xFF143E7C)),
    FOREST_GREEN("Forest Green", Color(0xFF1E8549), Color(0xFF15693B)),
    ROYAL_PURPLE("Royal Purple", Color(0xFF653A76), Color(0xFF512763)),
    SUNSET_ORANGE("Sunset Orange", Color(0xFFA65B2B), Color(0xFF85451A));

    companion object {
        fun fromId(id: String?): AppColorTheme = entries.find { it.name == id } ?: PUMPKIN_RED
    }
}

// Kept as top-level constants: ReadReceiptBlue is deliberately theme-invariant
// (it's a universal "read" signal, not a brand accent). PumpkinRed/PumpkinRedContainer
// stay at their ORIGINAL (pre-darkening) values — they're what the calculator
// uses (see CalculatorColors below), which must never change regardless of
// what accent the user picks for the real app.
val PumpkinRed = Color(0xFFE0304B)
val PumpkinRedContainer = Color(0xFFB8253C)
val ReadReceiptBlue = Color(0xFF4FC3F7)

private val DarkBackground = Color(0xFF17171B)
private val DarkSurface = Color(0xFF232328)
private val DarkSurfaceVariant = Color(0xFF2E2E34)
private val DarkOnSurface = Color(0xFFECECEE)
private val DarkOnSurfaceMuted = Color(0xFFA6A6AD)

private fun colorsFor(theme: AppColorTheme) = darkColorScheme(
    primary = theme.accent,
    onPrimary = Color.White,
    primaryContainer = theme.accentContainer,
    onPrimaryContainer = Color.White,
    secondary = theme.accent,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    error = Color(0xFFFF6B6B)
)

// The decoy calculator's permanent look — built from the original
// (pre-darkening) red directly, not from AppColorTheme.PUMPKIN_RED, so it
// never hints at whatever accent color the real app is using underneath and
// never changes when that palette is retouched.
val CalculatorColors = darkColorScheme(
    primary = PumpkinRed,
    onPrimary = Color.White,
    primaryContainer = PumpkinRedContainer,
    onPrimaryContainer = Color.White,
    secondary = PumpkinRed,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceMuted,
    error = Color(0xFFFF6B6B)
)

@Composable
fun PumpkinTheme(
    colorTheme: AppColorTheme = AppColorTheme.PUMPKIN_RED,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = colorsFor(colorTheme), content = content)
}
