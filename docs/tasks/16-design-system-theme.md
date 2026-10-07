# Task 16: the Streak Season design system in Compose

## Context

The app is still on the Android Studio template theme. A design system now
exists for it — colours in light and dark, two typefaces, a type scale, spacing
and corner radii. Its source of truth is `contract/design/tokens.json`, with the
rules in `contract/design/README.md`. Read both first.

The design system lives in `contract/` for the same reason `rules.md` does: the
Swift app will build from the same file. Treat `tokens.json` as authoritative.
If this brief and `tokens.json` disagree on a value, `tokens.json` wins.

This task puts the design system into the theme. **It does not redesign any
screen.** After it, screens look different because the theme changed, but
their layouts are untouched. Screen redesigns are separate tasks.

---

## Part 1 — fonts

Priyanshu downloads these from Google Fonts and places them in
`app/src/main/res/font/` before this task runs (both are OFL-licensed). If any
file is missing, stop and say which.

- `bricolage_grotesque_regular.ttf` (400)
- `bricolage_grotesque_semibold.ttf` (600)
- `bricolage_grotesque_bold.ttf` (700)
- `literata_italic.ttf` (400 italic)

Static weights are used rather than the variable font, to keep this simple.
`tokens.json` asks for weight 650 on `ss-title`; use 600 and note it in the
report.

Define two `FontFamily` values in `Type.kt`: `Bricolage` and `Literata`.

## Part 2 — colours

### `Color.kt`

Every colour token from `tokens.json` as a named constant, once per theme —
`FrostLight`, `FrostDark`, `EvergreenLight`, `EvergreenDark`, and so on. Hex
values copied exactly. Delete the template's `Purple80`, `Pink40` etc.

### Material colour schemes, in `Theme.kt`

Map the tokens onto Material 3 slots, exactly as `contract/design/README.md`
specifies:

| Material slot | Token |
|---|---|
| `primary` | `evergreen` |
| `onPrimary` | `on-evergreen` |
| `primaryContainer` | `evergreen-soft` |
| `onPrimaryContainer` | `evergreen` |
| `background` | `frost` |
| `onBackground` | `ink` |
| `surface` | `surface` |
| `onSurface` | `ink` |
| `surfaceVariant` | `sunken` |
| `onSurfaceVariant` | `ink-muted` |
| `outline` | `line-strong` |
| `outlineVariant` | `line` |
| `tertiary` | `larch` |
| `tertiaryContainer` | `larch-soft` |
| `onTertiaryContainer` | `larch` |

Build `lightColorScheme(...)` and `darkColorScheme(...)` from these. Any slot not
listed keeps Material's default for now — list which ones in the report.

### Colours Material has no slot for

`missed`, `rest`, `sunken`, `line-strong`, `larch`, `larch-soft` and the rest are
needed by name in screens, and `missed` and `rest` are **not errors** — do not
map them to `error`.

Create an `@Immutable data class StreakColors` holding every token, one instance
per theme, provided through a `staticCompositionLocalOf`. Expose it as
`StreakTheme.colors`, following the pattern Material uses for
`MaterialTheme.colorScheme`:

```kotlin
object StreakTheme {
    val colors: StreakColors
        @Composable @ReadOnlyComposable
        get() = LocalStreakColors.current
}
```

`staticCompositionLocalOf`, not `compositionLocalOf`: the value only changes
when the theme flips, and a static local avoids tracking reads in every
composable that uses a colour.

### Dynamic colour — off

The template theme has `dynamicColor: Boolean = true`, which on Android 12+
replaces the app's colours with ones derived from the user's wallpaper. Remove
the parameter and the dynamic branch entirely. Streak Season always uses its own
colours.

## Part 3 — type

Map the `ss-*` styles onto Material `Typography` slots, all using `Bricolage`:

| Style | Material slot | Size / line height | Weight | Letter spacing |
|---|---|---|---|---|
| `ss-display` | `displayMedium` | 56 / 56 sp | 700 | -0.02 em |
| `ss-title` | `headlineMedium` | 28 / 34 sp | 600 | -0.01 em |
| `ss-heading` | `titleLarge` | 20 / 26 sp | 600 | 0 |
| `ss-body` | `bodyLarge` | 16 / 24 sp | 400 | 0 |
| `ss-label` | `labelLarge` | 13 / 18 sp | 600 | 0.02 em |
| `ss-caption` | `bodySmall` | 12 / 16 sp | 400 | 0 |

Unlisted slots fall back to the closest of these — say which you chose for each
in the report.

`ss-voice` (Literata italic, 18 / 28 sp, 400) is **not** a Material slot. It is
only for words the user wrote: the intention, reflections, notes. Put it on
`StreakTheme` as `StreakTheme.type.voice`, through the same `CompositionLocal`
approach, so no screen can mistake it for an ordinary text style.

Use `.sp` for sizes and line heights and `.em` for letter spacing, so text
respects the user's font-size setting.

## Part 4 — shape and space

- `Shapes(small = 6.dp, medium = 16.dp, large = 16.dp)` from `radius-sm` and
  `radius-md`. `radius-full` is `CircleShape`, not a `Shapes` slot.
- A plain `object Space` with `s1 = 4.dp`, `s2 = 8.dp`, `s4 = 16.dp`,
  `s6 = 24.dp`, `s10 = 40.dp`. No CompositionLocal — spacing never changes by
  theme.

## Part 5 — wire it up

- One `@Composable fun StreakTheme(darkTheme: Boolean = isSystemInDarkTheme(),
  content: ...)` that provides the colour scheme, typography, shapes, and the
  `StreakColors` / voice locals.
- `MainActivity` wraps its content in it, replacing the template theme.
- Rename or delete the template's `<AppName>Theme` so there is one theme only.
- `themes.xml`: set the window background to `frost` per theme, so there's no
  white flash before Compose draws.

## Part 6 — a preview to see it

Add `ui/theme/ThemePreview.kt` with `@Preview` composables, light and dark,
showing every colour swatch with its token name and every text style with a
sample. This is how the design system gets checked by eye in Android Studio
without running the app.

---

## Constraints

- No screen layouts change. Find any **hard-coded colour** in a screen
  (`Color(0xFF…)`, `Color.White`, `Color.Black`) and **list it in the report**
  rather than fixing it — fixing it is part of each screen's redesign task.
- No new dependencies.
- `:domain` is not touched.
- No animation.

## Definition of done

- The app runs in light and dark, in the new colours and fonts.
- Dynamic colour is gone.
- `ThemePreview` renders every token in both themes.
- `./gradlew :domain:test` and `:app:testDebugUnitTest` green.

## Report back

Write to `docs/implementationSessions/task-16-report.md` and commit it with the
task, per `CLAUDE.md`.

- The fallback chosen for each unlisted Material typography slot and any
  unlisted colour slot.
- Every hard-coded colour found in screens, with file and line.
- The 650 → 600 weight substitution, and anything else that didn't match
  `tokens.json` exactly.
- Screenshots or a description of the app in both themes — what looks right and
  what looks off now that the theme changed under screens that weren't designed
  for it.