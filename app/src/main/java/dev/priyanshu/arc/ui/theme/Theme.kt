package dev.priyanshu.arc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Streak Season's own colours only — no dynamic (wallpaper-derived) colour.
// Tokens map onto Material 3 slots exactly as contract/design/README.md
// specifies. Slots left unset keep Material's default (see the task report).
private val LightColorScheme = lightColorScheme(
    primary = EvergreenLight,
    onPrimary = OnEvergreenLight,
    primaryContainer = EvergreenSoftLight,
    onPrimaryContainer = EvergreenLight,
    background = FrostLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SunkenLight,
    onSurfaceVariant = InkMutedLight,
    outline = LineStrongLight,
    outlineVariant = LineLight,
    tertiary = LarchLight,
    tertiaryContainer = LarchSoftLight,
    onTertiaryContainer = LarchLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = EvergreenDark,
    onPrimary = OnEvergreenDark,
    primaryContainer = EvergreenSoftDark,
    onPrimaryContainer = EvergreenDark,
    background = FrostDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SunkenDark,
    onSurfaceVariant = InkMutedDark,
    outline = LineStrongDark,
    outlineVariant = LineDark,
    tertiary = LarchDark,
    tertiaryContainer = LarchSoftDark,
    onTertiaryContainer = LarchDark,
)

// Static, not dynamic: these change only when the theme flips, so there is no
// reason to track reads of them in every composable that uses a colour. Default
// to the light values — light is the fallback, per tokens.json.
val LocalStreakColors = staticCompositionLocalOf { LightStreakColors }
val LocalStreakType = staticCompositionLocalOf { StreakTypography }

/**
 * The Streak Season tokens that live outside Material's own objects, reached the
 * same way Material exposes `MaterialTheme.colorScheme`:
 *
 *   StreakTheme.colors.missed
 *   StreakTheme.type.voice
 */
object StreakTheme {
    val colors: StreakColors
        @Composable @ReadOnlyComposable
        get() = LocalStreakColors.current

    val type: StreakType
        @Composable @ReadOnlyComposable
        get() = LocalStreakType.current
}

@Composable
fun StreakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val streakColors = if (darkTheme) DarkStreakColors else LightStreakColors

    CompositionLocalProvider(
        LocalStreakColors provides streakColors,
        LocalStreakType provides StreakTypography,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StreakShapes,
            content = content,
        )
    }
}
