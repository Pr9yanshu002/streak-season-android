# Streak Season — design system

Streak Season is an app for seasons of effort: a chapter with a start, an end, and a record of whether you showed up. The look is seasonal and natural — evergreen for the thing that keeps going through every season, larch gold for the moments worth marking — on a frost-tinted page by day and a forest at night. Built for Android (Compose) first, then iOS (SwiftUI), from the same tokens. This is a first system: tokens and rules, no components yet.

## Voice

Honest, plain, and on your side. The app reports what happened; it does not cheer or scold.

- Lead with the number. "Day 12 of 40." "3 of 4 done." Adjectives come second, if at all.
- Say a hard thing plainly. A failed arc reads "You made it to day 19." — not "You failed!" and not "Amazing effort!"
- Neutral about bad weeks. "Your toughest week was 13–19 Jan — 3 of 6 days." Not a warning, not a pep talk.
- Sentence case everywhere. No exclamation marks, no emoji.
- Rest is not failure. A rest day is written as a rest day, never as a miss with an excuse.

## Two voices, two typefaces

The one rule to remember: **the app speaks in sans; you speak in serif.**

- **Bricolage Grotesque** (`--font-sans`) is the app's voice: every label, number, heading and button. Its optical-size axis makes it expressive at 56px and quiet at 12px, so the display number (`ss-display`) and the caption (`ss-caption`) feel like one family.
- **Literata italic** (`--font-serif`, style `ss-voice`) is reserved for words the user wrote — the day-1 intention, milestone reflections, notes. Wherever those are quoted back, on Today or in the recap, they keep this face. Never use it for anything the app wrote.

On the recap this is the point: your own sentence, in its own typeface, next to the numbers that answer it.

## Colour

Semantic tokens with a light and a dark theme; light is listed first and is the fallback.

- **Grounds:** `frost` (page), `surface` (cards), `sunken` (wells, inputs, future days).
- **Text:** `ink` and `ink-muted`, legible on all three grounds in both themes.
- **Brand:** `evergreen` for primary actions and done things; `evergreen-soft` behind evergreen text; `on-evergreen` for labels on an evergreen fill — never hard-coded white, which fails in dark.
- **Accent:** `larch` for milestones, the today marker and partial days, with `larch-soft` behind it. Use it sparingly — it means *notice this*.
- **States:** `missed` and `rest`, each only ever with its shape (below).
- **Lines:** `line` is decorative. Anything a person must see to use a control — an unchecked box, an input edge, the missed ring — uses `line-strong`, which clears 3:1.

Dark is a forest at night, not an inversion: the same roles, re-tuned. Every text pairing in this system clears 4.5:1 in both themes.

## Day states — shape first, colour second

The four state colours sit at similar lightness, so colour alone cannot separate them for a colour-blind user. **Shape carries the state; colour only reinforces it.** This holds on the calendar, in the recap, and anywhere a day is drawn.

| State | Mark | Colour |
|---|---|---|
| complete | filled disc | `evergreen` |
| partial | half-filled disc | `larch` on `larch-soft` |
| missed | hollow ring | `missed` ring, `line-strong` weight |
| rest | small centred dot | `rest` |
| pending (today) | ring | `larch` |
| future | empty well | `sunken` |

Discs use `radius-full`. A screen reader names the state in words; the mark is never the only carrier.

## Shape and space

- **Spacing** steps are 4, 8, 16, 24 and 40px (`space-1` … `space-10`). Screen margins `space-6`, card padding `space-4`.
- **Radius:** `radius-md` for cards and sheets, `radius-sm` for small controls, `radius-full` for pills and the day discs.
- **Hairlines, not shadows.** Cards sit on `frost` with a `line` border. Depth is not part of this system yet.
- **Focus:** `focus-ring` — a 2px gap in `frost`, then a 2px ring.

## Iconography and marks

There is no logo yet: the name set in Bricolage Grotesque at `ss-title` weight stands in for one. There is no icon set yet either; until one is chosen, use Material Symbols Rounded at 24px in `ink` or `ink-muted`, and never colour an icon to carry a state on its own. The tree — the progress metaphor — is not designed yet and is not part of this system.

## Using it in the app

**Android (Compose).** Download Bricolage Grotesque and Literata from Google Fonts (both OFL) into `res/font/`.

- Colours → two `ColorScheme`s in `Color.kt` / `Theme.kt`: `primary` = `evergreen`, `onPrimary` = `on-evergreen`, `primaryContainer` = `evergreen-soft`, `background` = `frost`, `surface` = `surface`, `surfaceVariant` = `sunken`, `onSurface` = `ink`, `onSurfaceVariant` = `ink-muted`, `outline` = `line-strong`, `outlineVariant` = `line`, `tertiary` = `larch`, `tertiaryContainer` = `larch-soft`.
- `missed` and `rest` have no Material slot and are not errors — put them, with the day-state colours, in a small `StreakColors` class provided through a `CompositionLocal`.
- Type → a `Typography` built from the `ss-*` styles; `ss-voice` is a separate `TextStyle`, not a Material slot.
- Radius → `Shapes(small = radius-sm, medium = radius-md, large = radius-md)`. Spacing → a plain `Dimens` object.

**iOS (SwiftUI), later.** The same tokens become colour sets in the asset catalog with Any and Dark appearances, and `Font.custom` for both families. Same names, same values — this file is the contract between the two apps, the way `rules.md` is for behaviour.

## Not here yet

Components (commitment card, day disc, milestone card, recap card), a logo, an icon set, and the tree.