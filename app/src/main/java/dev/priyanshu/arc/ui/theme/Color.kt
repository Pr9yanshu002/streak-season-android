package dev.priyanshu.arc.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Streak Season colour tokens, copied exactly from contract/design/tokens.json.
// One constant per theme. Light is listed first and is the fallback.

// frost — page background
val FrostLight = Color(0xFFF3F5F0)
val FrostDark = Color(0xFF0F1612)

// surface — cards and sheets on frost
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceDark = Color(0xFF17201B)

// sunken — wells below the surface: inputs, stepper tracks, future days
val SunkenLight = Color(0xFFE7ECE4)
val SunkenDark = Color(0xFF1F2A24)

// line — decorative hairlines
val LineLight = Color(0xFFCBD3C6)
val LineDark = Color(0xFF34433A)

// line-strong — meaningful borders (unchecked box, input edge, missed ring)
val LineStrongLight = Color(0xFF7A877E)
val LineStrongDark = Color(0xFF6E7E74)

// ink — primary text
val InkLight = Color(0xFF13201A)
val InkDark = Color(0xFFE6EDE7)

// ink-muted — secondary text
val InkMutedLight = Color(0xFF526158)
val InkMutedDark = Color(0xFF9DAAA1)

// evergreen — the brand: primary actions, done things
val EvergreenLight = Color(0xFF1E5B3F)
val EvergreenDark = Color(0xFF74C597)

// evergreen-soft — tint behind evergreen text
val EvergreenSoftLight = Color(0xFFD8E9DE)
val EvergreenSoftDark = Color(0xFF1C3628)

// on-evergreen — text/icons on an evergreen fill (never plain white in dark)
val OnEvergreenLight = Color(0xFFFFFFFF)
val OnEvergreenDark = Color(0xFF0F1612)

// larch — accent: milestones, today marker, partial days
val LarchLight = Color(0xFF8F5C08)
val LarchDark = Color(0xFFE6AA4E)

// larch-soft — tint behind larch text
val LarchSoftLight = Color(0xFFF6E7C8)
val LarchSoftDark = Color(0xFF3B2C13)

// missed — a missed day or failed arc (not an error; always with a hollow ring)
val MissedLight = Color(0xFFA2482D)
val MissedDark = Color(0xFFE39176)

// rest — a sanctioned pause (not a failure; always with the small-dot mark)
val RestLight = Color(0xFF3F6683)
val RestDark = Color(0xFF93B7D0)

/**
 * Every Streak Season colour token, resolved for one theme.
 *
 * Material 3 has slots for most of these (see [StreakColorScheme]); the ones it
 * has no slot for — [missed], [rest], [sunken], [lineStrong], [larch],
 * [larchSoft] and the grounds we also want by name — live here and are reached
 * through [StreakTheme.colors]. [missed] and [rest] are deliberately *not*
 * mapped to Material's `error`.
 *
 * `focus-ring` in tokens.json is an alias of `evergreen`, so [focusRing] simply
 * holds the same value and follows the theme.
 */
@Immutable
data class StreakColors(
    val frost: Color,
    val surface: Color,
    val sunken: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val inkMuted: Color,
    val evergreen: Color,
    val evergreenSoft: Color,
    val onEvergreen: Color,
    val larch: Color,
    val larchSoft: Color,
    val missed: Color,
    val rest: Color,
    val focusRing: Color,
)

val LightStreakColors = StreakColors(
    frost = FrostLight,
    surface = SurfaceLight,
    sunken = SunkenLight,
    line = LineLight,
    lineStrong = LineStrongLight,
    ink = InkLight,
    inkMuted = InkMutedLight,
    evergreen = EvergreenLight,
    evergreenSoft = EvergreenSoftLight,
    onEvergreen = OnEvergreenLight,
    larch = LarchLight,
    larchSoft = LarchSoftLight,
    missed = MissedLight,
    rest = RestLight,
    focusRing = EvergreenLight,
)

val DarkStreakColors = StreakColors(
    frost = FrostDark,
    surface = SurfaceDark,
    sunken = SunkenDark,
    line = LineDark,
    lineStrong = LineStrongDark,
    ink = InkDark,
    inkMuted = InkMutedDark,
    evergreen = EvergreenDark,
    evergreenSoft = EvergreenSoftDark,
    onEvergreen = OnEvergreenDark,
    larch = LarchDark,
    larchSoft = LarchSoftDark,
    missed = MissedDark,
    rest = RestDark,
    focusRing = EvergreenDark,
)
