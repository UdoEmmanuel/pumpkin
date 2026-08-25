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
enum class AppColorTheme(val displayName: String, val accent: Color, val accentContainer: Color) {
    PUMPKIN_RED("Pumpkin Red", Color(0xFFE0304B), Color(0xFFB8253C)),
    OCEAN_BLUE("Ocean Blue", Color(0xFF2F80ED), Color(0xFF1F5FBF)),
    FOREST_GREEN("Forest Green", Color(0xFF2ECC71), Color(0xFF20A15A)),
    ROYAL_PURPLE("Royal Purple", Color(0xFF9B59B6), Color(0xFF7D3C98)),
    SUNSET_ORANGE("Sunset Orange", Color(0xFFFF8C42), Color(0xFFCC6A28));

    companion object {
        fun fromId(id: String?): AppColorTheme = entries.find { it.name == id } ?: PUMPKIN_RED
    }
}

// Kept as top-level constants: ReadReceiptBlue is deliberately theme-invariant
// (it's a universal "read" signal, not a brand accent), and the calculator
// needs one fixed palette independent of the user's selection.
val PumpkinRed = AppColorTheme.PUMPKIN_RED.accent
val PumpkinRedContainer = AppColorTheme.PUMPKIN_RED.accentContainer
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

// The decoy calculator's permanent look — a plain, unbranded scheme so it
// never hints at whatever accent color the real app is using underneath.
val CalculatorColors = colorsFor(AppColorTheme.PUMPKIN_RED)

@Composable
fun PumpkinTheme(
    colorTheme: AppColorTheme = AppColorTheme.PUMPKIN_RED,
    content: @Composable () -> Unit
) {
    MaterialTheme(colorScheme = colorsFor(colorTheme), content = content)
}
