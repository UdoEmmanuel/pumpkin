package com.pumpkin.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Red-accent-on-near-black palette, matching the reference messaging UI.
val PumpkinRed = Color(0xFFE0304B)
val PumpkinRedContainer = Color(0xFFB8253C)
// Read-receipt "seen" tick color — matches the familiar blue-checkmark
// convention from other chat apps; distinct from the red brand accent so it
// reads as its own signal.
val ReadReceiptBlue = Color(0xFF4FC3F7)
private val DarkBackground = Color(0xFF17171B)
private val DarkSurface = Color(0xFF232328)
private val DarkSurfaceVariant = Color(0xFF2E2E34)
private val DarkOnSurface = Color(0xFFECECEE)
private val DarkOnSurfaceMuted = Color(0xFFA6A6AD)

private val DarkColors = darkColorScheme(
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

private val LightColors = lightColorScheme(
    primary = PumpkinRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DD),
    secondary = PumpkinRed
)

@Composable
fun PumpkinTheme(
    // Defaults to the dark, red-accent look from the reference design
    // regardless of system theme — this is a fixed brand look, not a
    // light/dark-adaptive one (see chatlist/chat screen restyle).
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
