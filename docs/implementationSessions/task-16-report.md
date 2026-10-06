# Task 16 — the Streak Season design system in Compose — report

The template theme is gone. Colours, two typefaces, the type scale, shapes and
spacing now come from `contract/design/tokens.json`, mapped onto Material 3 where
a slot exists and exposed through `StreakTheme` where one doesn't. No screen
layout changed; screens look different only because the theme underneath them
did. `:domain` untouched, no new dependencies.

## Files

- `ui/theme/Color.kt` — every colour token as a per-theme constant
  (`FrostLight`/`FrostDark`, …), plus the `@Immutable StreakColors` holder and
  its `LightStreakColors`/`DarkStreakColors` instances.
- `ui/theme/Type.kt` — `Bricolage` and `Literata` `FontFamily`s, the six `ss-*`
  styles mapped onto `Typography`, and `StreakType`/`StreakTypography` for
  `ss-voice`.
- `ui/theme/Shape.kt` — `StreakShapes` (6/16/16) and the `Space` object.
- `ui/theme/Theme.kt` — the two `ColorScheme`s, the `LocalStreakColors` /
  `LocalStreakType` static locals, the `StreakTheme` object (`.colors`, `.type`)
  and the `StreakTheme(darkTheme, content)` composable.
- `ui/theme/ThemePreview.kt` — light and dark `@Preview`s: every swatch with its
  token name, every text style with its sample.
- `res/font/` — four static TTFs. `res/values/colors.xml` +
  `res/values-night/colors.xml` (frost) and the matching `themes.xml` for the
  window background.

## Fonts

Static weights, per the brief — the variable fonts were not shipped into the
app:

- `bricolage_grotesque_regular.ttf` (400)
- `bricolage_grotesque_semibold.ttf` (600)
- `bricolage_grotesque_bold.ttf` (700)
- `literata_italic.ttf` (400 italic)

The default-optical-size statics were used (`BricolageGrotesque-Regular/SemiBold/
Bold`, `Literata-Italic`), not the `*_NNpt` optical variants — there is no
optical-size axis once you drop the variable font, so one cut per weight.

## The 650 → 600 substitution, and other deviations from tokens.json

- **`ss-title` weight 650 → 600.** No static 650 cut exists; `FontWeight.SemiBold`
  (600) is the nearest shipped weight. This is the only value that does not match
  `tokens.json` exactly.
- Everything else matches: all sizes, line heights, letter spacing, every hex
  value. Letter spacing uses `.em` (so it tracks font size), sizes/line heights
  use `.sp` (so they respect the user's font-size setting), exactly as asked.

## Typography — the unlisted Material slots

The six listed styles are exact. Every *other* Material slot falls back to the
assigned member of its own Material **role family**, so no slot is ever left on
Roboto and small/body text never inherits a heading's weight:

| Material slot | Gets | Why |
|---|---|---|
| `displayLarge` | ss-display | display family |
| `displayMedium` | **ss-display** | listed |
| `displaySmall` | ss-display | display family |
| `headlineLarge` | ss-title | headline family |
| `headlineMedium` | **ss-title** | listed |
| `headlineSmall` | ss-title | headline family |
| `titleLarge` | **ss-heading** | listed |
| `titleMedium` | ss-heading | title family |
| `titleSmall` | ss-heading | title family |
| `bodyLarge` | **ss-body** | listed |
| `bodyMedium` | ss-body | body family |
| `bodySmall` | **ss-caption** | listed |
| `labelLarge` | **ss-label** | listed |
| `labelMedium` | ss-label | label family |
| `labelSmall` | ss-label | label family |

`ss-voice` (Literata italic) is deliberately **not** a `Typography` slot — it is
only `StreakTheme.type.voice`, so no screen can reach it as an ordinary style.

## Colour — the unlisted Material slots

The 15 slots in the brief's table are mapped exactly. These are left on
Material's own default for now (listed per the brief):

`secondary`, `onSecondary`, `secondaryContainer`, `onSecondaryContainer`,
`error`, `onError`, `errorContainer`, `onErrorContainer`, `inversePrimary`,
`inverseSurface`, `inverseOnSurface`, `scrim`, `surfaceTint` (defaults to
`primary` = evergreen), and the M3 tonal `surfaceContainer*` / `surfaceBright` /
`surfaceDim` family.

`missed`, `rest`, `sunken`, `line-strong`, `larch`, `larch-soft` and the grounds
also needed by name live on `StreakColors` (reached via `StreakTheme.colors`).
`missed` and `rest` are **not** mapped to `error`. `focus-ring` is an alias of
`evergreen` and simply holds the same per-theme value.

## Dynamic colour

Removed entirely — the `dynamicColor` parameter and the `dynamicLight/
DarkColorScheme` branch are gone. Streak Season always uses its own colours.

## Hard-coded colours found in screens

**None.** Grepped `app/src/main/java` (outside `ui/theme/`) for `Color(0x…)`,
`Color.White`, `Color.Black`, `Color.Red/Gray/Green/Yellow` and for
`@color/…` / `R.color.…` references to the deleted template colours — no hits.
Every screen already drew from `MaterialTheme.colorScheme`, so nothing needs to
be deferred to a redesign task on the colour front.

## Renames / cleanup

- `StreakSeasonTheme` → `StreakTheme`, updated at all five call sites
  (`MainActivity`, and the previews in `RecapScreen` and `TodayScreen`). One
  theme only now.
- Deleted the template's `Purple80`/`Pink40`/… from `Color.kt` and the
  `purple_*`/`teal_*`/`black`/`white` entries from `res/values/colors.xml`.
- `object StreakTheme` and `fun StreakTheme(...)` share a name on purpose — the
  same pattern Material uses for `MaterialTheme`.

## Verification

- `./gradlew :domain:test` — green.
- `./gradlew :app:testDebugUnitTest` — green.
- `:app:assembleDebug` — builds; fonts and night resources link.
- Ran the debug APK on a Pixel 8 emulator (API) in light and dark.

### What it looks like

- **Light** is the frost-tinted paper (`#F3F5F0`) with near-black ink and
  Bricolage throughout — the empty state's sentence and the debug menu's title
  are visibly the new grotesque, not Roboto. The debug menu's scenario buttons
  are evergreen fills with white (`on-evergreen`) labels; "Cancel" is evergreen
  text on frost.
- **Dark** is the forest-at-night frost (`#0F1612`) with `ink` (`#E6EDE7`) text —
  not an inversion, a re-tuned palette. The 🐞 debug FAB picks up the evergreen
  surface tint. Text stays comfortably legible (the system's 4.5:1 claim holds by
  eye).
- **What's off, as expected:** these screens were built against the template
  theme, so spacing/!type pairings weren't designed for Bricolage's metrics — e.g.
  the empty-state sentence is centered Bricolage with default sizing, and the FAB
  is Material's default placement, not a designed control. Those are for the
  per-screen redesign tasks, not this one. The active-arc Today screen (commitment
  cards on `surface`, evergreen checks, larch today-marker) is best checked via
  `ThemePreview` and the upcoming redesign; the emulator's debug FAB wouldn't
  register taps this session, so I didn't capture that state live.

`ThemePreview` is the by-eye reference: open it in Android Studio's preview pane
to see all 15 swatches and all seven text styles in both themes at once.
