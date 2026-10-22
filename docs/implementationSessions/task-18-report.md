# Task 18 — Today redesign: report

Branch `task-18-today-redesign`. Parts are added here as they finish.

---

## Part 1 — components

### Built

All are new, in `app/src/main/java/dev/priyanshu/arc/ui/components/`. None existed from task
16: there was no `DayDisc` or similar, and task 16 only touched `ui/theme/`. Every component is
stateless, takes plain data and lambdas, and has `@PreviewLightDark` previews, so each preview
renders once in light and once in dark.

| Component | File | Notes |
|---|---|---|
| `DayDisc` | `DayDisc.kt` | Takes the engine's `DayState` plus `isToday`. The shape carries the meaning: complete is filled, partial is half filled, missed is a hollow ring, rest is a centre dot, and future/pending is a plain sunken disc. Today adds a larch ring on top of any of these. `OUT_OF_RANGE` leaves an empty slot. |
| `WeekStrip` | `DayDisc.kt` | Takes a list of `WeekStripDay(date, state, isToday)`. Today's letter is bold larch. Each column is a single TalkBack item, read as e.g. "Monday, complete" or "Wednesday, today, in progress". |
| `StreakPill` | `Chips.kt` | "N-day streak". Hiding it at 0 is the caller's job. |
| `BonusChip` | `Chips.kt` | "Bonus", larch on larch-soft. |
| `BinaryCommitmentCard` | `CommitmentCards.kt` | A 44 dp round toggle with checkbox semantics. When done, the card is evergreen-soft with a filled tick and "Done". |
| `CounterCommitmentCard` | `CommitmentCards.kt` | Shows "X of Y unit" with the amount bold, − and + buttons (44 dp) and a progress bar. `isDone` switches to the design-02 look. `captionSuffix` covers "doesn't affect your day". Used for both counter and duration. |
| `NumberCommitmentCard` | `CommitmentCards.kt` | A 48 dp field on sunken, the unit, and the helper line. It keeps task 10's save rule: the value is saved on done or when focus leaves, never per keystroke. A blank field clears the value, and non-numbers snap back. |
| `UnscheduledCommitmentRow` | `CommitmentCards.kt` | A dashed outline drawn by hand, since Compose has no dashed border. Shows "Not today", plus " · <schedule>" when the caller passes schedule text. |
| `RestDayCard` | `RestDayCard.kt` | Both task-17 forms, with the copy unchanged. `resting = false` is the offer row from design 01; `resting = true` is the rest-outlined card from design 03. |

Shared pieces live in `Primitives.kt`:
- **Glyphs:** the tick, plus and minus icons are drawn from the design's SVG paths, because there
  is no icon library and the task forbids new dependencies.
- **Building blocks:** `RoundIconButton` (44 dp), the `StreakCard` shell, and `PreviewFrame`,
  which renders previews on the real page background.
- **Number helpers:** `formatAmount` gives "2,100" and "72.5"; `formatInputAmount` gives "70" and
  "72.5", which parse back without trouble.

New test: `ComponentTextTest`. It covers the TalkBack labels and amount formatting.

### Colours and type

- **No hard-coded colours.** The only `Color` literal used is `Color.Transparent`, for outlined
  buttons, which the design also has.
- **Colours with a Material slot:** taken from `MaterialTheme.colorScheme` (`primary`,
  `onPrimary`, `primaryContainer`, `surface`, `onSurface`, `onSurfaceVariant`, `outline`,
  `tertiary`, `background`).
- **Colours without one:** `sunken`, `line`, `larchSoft`, `missed` and `rest` come from
  `StreakTheme.colors`, as the brief says.
- **Type:** `bodySmall` for captions and day letters, `labelLarge` for pills and buttons,
  `titleLarge` for "Today is a rest day", and `bodyLarge` for body text.

### Gaps between the design and the data model

1. **Counter step size.**
   - Design: +/− changes the value by 250 ml or 5 min.
   - Data: there is no per-commitment step.
   - What I did: `CounterCommitmentCard` takes `step`, defaulting to 1, which is today's
     behaviour. The TalkBack labels follow it ("Add 1 ml to Water").
2. **Schedule text for unscheduled rows.**
   - Design: "Saturdays and Sundays".
   - Data: only the raw `schedule_days` string ('6,7') exists. `Commitment.kt` says parsing it is
     R3, which belongs to the engine.
   - What I did: the row takes optional text and shows just "Not today" without it. Whether Part 2
     may turn `scheduleDays` into words in `:app` is your call.
3. **Binary "not done" status.**
   - Design: only shows the done state ("Done").
   - What I did: used "Not done yet". This is new copy, so change it if you want something else.
4. **Done counter in design 02 has only a − button.**
   - What I did: kept + as well, because "behaviour does not change". Today you can keep counting
     past the target, and the recap totals use the full value.
   - Decision needed: if you want the design exactly, dropping + is a one-line change.
5. **Design 02's "Day N done" hero.** It uses an inverted week strip (on-evergreen discs on an
   evergreen card). That variant isn't built. It's only needed in Part 2, so I'll ask then whether
   to add an `inverted` option to `WeekStrip` or treat it differently.
6. **Intention and milestone notes.** These belong to Part 3 and aren't touched here.

### Sizes and spacing the theme doesn't have

None of these are added to the theme. Component geometry is written inline:

| Kind | Values |
|---|---|
| Disc sizes | 30 dp (strip and offer row), 48 dp (resting card). Design 02's 28 dp isn't used yet. |
| Buttons and touch | 44 dp buttons and touch targets |
| Icons | 18 dp (stepper), 20 dp (tick) |
| Controls | 6 dp progress bar, 48 dp number field, 22 dp unscheduled dot |
| Strokes | 1 / 1.5 / 2.5 dp; dash pattern 6 on, 4 off |
| Rest dot | 42% of the disc diameter. Design 01 uses 26% and 08 uses 30%, so I picked one. |
| Dashed outline corners | 16 dp, the same value as `shapes.medium`, written out because the dashed outline is drawn by hand |

Spacing is built from `Space` where possible: 12 = `s2 + s1` and 20 = `s4 + s1`. Values that
aren't on the scale were rounded:

| Design value | Used |
|---|---|
| 14 (week strip and binary card padding, card gaps) | 16 (`s4`) |
| 10 (number card internal gap) | 8 (`s2`) |
| 6 (pill vertical padding) | 4 (`s1`) |
| 18 (pill button horizontal padding) | 16 (`s4`) |
| 2 dp (bonus chip vertical padding) | 2 dp, literal |

Type the theme doesn't have:
- **Commitment names (16 sp, weight 600):** no token matches, so they use `bodyLarge` with
  SemiBold.
- **Bonus chip (12 sp, weight 600, 0.02 em):** uses `bodySmall` with SemiBold and `labelLarge`'s
  letter spacing.

### Couldn't match / not checked

- **No rendered screenshots.** I can't render Compose previews from here. Each component has
  light and dark previews to check in Android Studio's Design or Split view.
- **Two `RestDayCard`s for now.** Task 17's version is still a private composable in
  `TodayScreen.kt` and is still the one the screen uses. Part 2 swaps in the new component and
  deletes the old one.

### Housekeeping

- **Brief renamed:** the brief was added as `docs/tasks/18-today-redesign` with no extension, so
  I renamed it to `18-today-redesign.md`.
- **CLAUDE.md:** "Current phase" now covers task 17 (carried over to this branch as agreed) and
  notes that the task-18 components live in `ui/components/`.
- **Brief wording:** the brief says the milestone-collapse behaviour is "from task 15". It's from
  task 14. Nothing depends on this.

### Tests

`./gradlew :app:testDebugUnitTest`: 46 / 46 (42 existing + 4 new).

---

## Part 2 — the active day (designs 01, 02, 03)

### Decisions from the Part 1 review, applied

1. **Counter step:** stays 1. A step field will be its own task.
2. **Schedule text:** `formatSchedule(days: Set<DayOfWeek>)` in `ui/today/ScheduleText.kt` gives
   "Every day", "Weekdays", "Saturdays and Sundays", or "Mon, Wed, Fri". It's covered by
   `ScheduleTextTest` (4 cases). The mapper turns DAILY into all seven days and parses
   WEEKDAYS' `'6,7'` into `DayOfWeek`s. Whether a commitment is scheduled today is still only
   Resolution's `NOT_SCHEDULED` (R3).
3. **Binary card:** "Not done yet" kept.
4. **Finished counter:** + stays, but switches from filled to the outline style once the target
   is met.
5. **Reversed strip:** `WeekStrip(reversed = true)` is a parameter on the same component, with
   `DayDisc(reversed = …)` underneath. It has its own light and dark preview
   (`WeekStripReversedPreview`), which also shows partial, missed and rest reversed for reference.

### What changed

- **`TodayUiState.Loaded`** gains these fields:
  - `currentStreak` (`Resolution.currentStreak`)
  - `requiredDone` / `requiredTotal`
  - `week` (seven `WeekStripDay`s)
  - `isDayComplete`, a getter on `todayState == COMPLETE`
- **`CommitmentUiState`** gains `isScheduledToday` and `scheduleText`.
- **Commitment order:** unscheduled commitments are no longer dropped. The mapper orders the
  list as required, then bonus, then not today, keeping arc order within each group.
- **Week strip:** the dates run from this ISO Monday to Sunday (date arithmetic in the mapper).
  Each state is `Resolution.dayStates[date]`, and dates outside the arc are `OUT_OF_RANGE`, drawn
  as an empty slot.
- **`TodayScreen`'s Loaded branch is rebuilt** from the Part 1 components:
  - **In progress (01):** header (arc name, "Wednesday 21 January", `StreakPill` hidden at 0),
    "Day N of M", `WeekStrip`, "Today" with "X of Y required done", the cards, and the rest offer
    at the bottom.
  - **Day complete (02):** an evergreen card with a tick, "Day N done", "Everything required, N
    days in a row." and the reversed strip. The count turns evergreen. An unfinished bonus
    counter reads "· doesn't affect your day". The rest card is hidden (restDayRow is null on a
    complete day, as in task 17).
  - **Rest day (03):** the resting card sits above the strip. The cards come under "Still here if
    you want them / Anything you log today still counts." and stay fully usable.
- **Removed:**
  - the stock controls (`CommitmentCard`, `BinaryControl`, `StepperControl`, `LogControl`)
  - task 17's private `RestDayCard`
  - `DayState.label()`
  - the old previews

  New `@PreviewLightDark` previews cover in-progress, complete and resting.
- **Hard-coded colours:** none to fix. The task-16 report found none on Today, and there are
  none now.

### Data rules: one point to check

**"X of Y required done" is counted in the mapper.**
- Y = required commitments the engine didn't mark `NOT_SCHEDULED` today. X = those it marked
  `DONE`.
- `Resolution` has no ready-made count, and `:domain` wasn't changed.
- It's a tally of the engine's per-commitment answers, with no R3/R4 decision repeated in `:app`.
- If you'd rather the engine expose it, it can move into `Resolution`.
- When Y is 0 the count is hidden rather than showing "0 of 0".

### Design details not matched

- **Design 02's logged weight** ("Logged today · 75.6 kg" with a pencil button) isn't built.
  The number card always shows its field, which keeps task 10's save behaviour as is. The logged
  variant would add an edit mode, so I'm leaving it for your call.
- **Design 03 merges the unscheduled rows into one** ("Gym · Long walk / Not today"). I kept one
  row per commitment, each with its schedule text, as in 01 and 02.
- **Design 03 hides − on a counter at 0.** I did the same. The old − at 0 wrote a 0-value entry,
  which changed nothing the engine reads.
- **No "Undo" button.** The designs don't have one:
  - A binary is unticked by tapping it again (writes 0).
  - A counter steps down to 0.
  - A number field is cleared by blanking it, which still calls the same undo.

  In every case the engine resolves the day exactly as before.
- **Duration unit:** duration cards still say "min" whatever unit is stored, the same as before.

### Edge to know about

A commitment whose `active_from` is after today shows as "Not today · <schedule>". That's
misleading, since the real reason is "not started yet", but it can't happen today: `createArc`
pins every commitment's `active_from` to the arc start, and there's no "add a commitment
mid-arc" flow yet.

### Spacing and type

- Sections are 20 dp apart (`s4 + s1`) and cards 12 dp apart (`s2 + s1`), with 24 dp page
  padding (`s6`).
- The 13 sp regular text ("Everything required, …", "Anything you log today still counts.") uses
  `labelLarge` at normal weight. There is no 13 sp regular token.
- No tokens added.

### Emulator

- The debug build installs and launches.
- The running emulator has no active arc, so it shows the existing no-arc screen (Part 3's job).
  I didn't open the debug menu to reseed, because that wipes the database.
- To reach the three Loaded layouts, reseed `active-mid-arc` from the debug menu. That is a
  standard arc with 1 token, and today is left pending. From there:
  - **In progress:** this is what you see first. Yesterday is seeded as partial, so the current
    streak is 0 and the `StreakPill` is hidden. That is correct, not a bug.
  - **Rest day:** tap "Rest today". The offer only appears if this ISO week still has its token.
    The seed's rest day is 5 days back, so on a Monday–Friday it falls in the previous week and
    the offer shows.
  - **Day complete:** tick Meditate, take Push-ups to 30 (weekdays only) and enter a Weight.

### Tests

- `./gradlew :domain:test`: 161 / 161.
- `./gradlew :app:testDebugUnitTest`: 51 / 51 (46 + 4 `ScheduleTextTest` + 1 new
  `TodayViewModelTest` case covering ordering, required counts, the week's states and empty
  slots, schedule text and streak).

---

## Part 3 — the other states (designs 04–08)

### Repository change (approved)

Design 06's "You wrote …" needs `arcs.intention`, which was stored but never reached Today.
Priyanshu approved a small change to `ArcRepository`:
- The private `ArcSnapshot` and the public `TodaySnapshot` each gain `intention: String?`.
- `observeSnapshot` fills it from the `ArcEntity` it already loads, the same field
  `getRecapScreenData` reads.

No other repository code changed.

### New components (`ui/components/`)

All three have light and dark previews.

| Component | Used by |
|---|---|
| `ProgressPie` | Filled clockwise to a fraction. With a track: larch on larch-soft (milestones, 04/05). Without one: an evergreen outline ring (the no-arc row, 07). |
| `ReflectionField` | "<label> · optional", a 3-line field in `StreakTheme.type.voice` (Literata italic) on sunken, and a helper line. |
| `LargeButton` | The full-width 56 dp pill, filled or outlined. |

### States

- **04, single milestone:**
  - The pie (¼, ½, ¾ or full) above the label in `displayMedium`.
  - "Day 45 of 90 · 14 January" in larch, then "45 days behind you, 45 ahead.".
  - "A line for later", "You'll see it again in your recap.", then Continue.
  - `Single` gains `triggeredOnDate`.
- **05, several milestones:**
  - "Winter Arc · Day 70 of 90" and "You've passed N milestones".
  - One card per milestone with its pie and "Day 23 · 23 December". The latest row has a larch
    border and "· latest".
  - The reflection helper names the latest milestone. The collapse rules from task 14 are
    unchanged.
  - `Summary` gains `arcName` and `todayDayNumber` (`Resolution.dayNumber`).
- **06, not started:**
  - "Starts Monday 6 April · 30 days", the countdown in larch, "days to go" (or "day to go"),
    and "Nothing to check in yet.".
  - The "You wrote" card in the voice style, shown only when an intention exists.
  - "What you'll do": required commitments first, then bonus, with `BonusChip` and
    `formatSchedule` text.
  - `NotStarted` gains `totalDays` (from Resolution), `daysToGo` (calendar distance to
    `start_date`), `intention` and `commitments`.
- **07, no arc:** the five evergreen pies, "Start an arc", the explainer, the four numbered steps,
  and "Start an arc". This uses the design's copy.
- **08, ended:**
  - "Ended Saturday 28 February": the failure date for a failed arc, the end date for a
    completed one.
  - "Ninety days, done." (completed only), then "You showed up / 72 / of 84 days", with a grid of
    every day as 12 dp discs, seven to a row from the start date.
  - The recap line, "See your recap" and "Start something new". "Remix this arc" is kept for a
    failed arc, as before.
  - `Ended` gains `endedOn`, `season` (every `Resolution.dayStates` value in date order),
    `daysShowedUp` and `daysCounted`.

### Data rules

- **"You showed up X of Y days"** is the recap's own `complete_days` / `counted_days` (R19).
  Those need every day's entries, which `TodaySnapshot` doesn't carry, so `TodayViewModel` reads
  them once from the existing `ArcRepository.getRecapScreenData` (the recap screen's own loader)
  and caches them per arc. Nothing about R8/R19 is redone in `:app`. They're fetched before the
  Ended state is published, so the numbers don't pop in later.
- **"Ninety"** comes from ICU's spell-out (`android.icu.text.MessageFormat("{0,spellout}")`,
  in the platform since API 24, so no new dependency), capitalised. It's covered by
  `SpellOutTest` under Robolectric.
- **`daysToGo` and "N behind you, M ahead"** are calendar arithmetic on dates and numbers the
  engine already produced (`start_date`, `dayNumber`, `totalDays`).

### Gaps and things not matched

- **Display sizes the theme doesn't have.** None were added. All of these use `displayMedium`
  (56):
  - 06's 120 sp countdown
  - 08's 96 sp "72"
  - 05's 44 sp title
- **The intention text** is 20/30 in design 06. `StreakTheme.type.voice` is 18/28, and that's
  what is used.
- **Failed arc on 08.** The design only shows a completed arc. For a failed one, the "N days,
  done." line is left out, and the page shows "Ended <failure date>", "You showed up X of Y" and
  the same buttons, plus Remix.
- **FINAL milestone on 04.** There's no "behind you / ahead" line, because "0 ahead" says nothing
  and a failed arc's days weren't all run. The pie is full for both completed and failed.
- **Long arcs on 08.** The season grid grows one row per 7 days, so a 365-day arc is about 53
  rows. The page scrolls (`AnchoredPage`), so nothing is cut off, but on long arcs the grid
  dominates the page.
- **Milestone dates** now read "14 January" instead of "14 Jan", as in the design.
- **Debug button overlap.** The debug-only 🐞 button sits over the bottom button on the
  bottom-anchored pages (04, 05, 07, 08). This only happens in debug builds.
- **Previously listed gaps:** the step size, the arc's intention ("You wrote …", now done) and the
  milestone note ("A line for later", already in the data as `milestones.reflection`) are all
  covered above or in Parts 1–2.

### Emulator

- **07 (no arc):** checked on the emulator in light and dark, using `cmd uimode night`, then set
  back to light. It matches the design. The screenshots are in the session, not committed.
- **04, 05, 06, 08:** each has light and dark `@PreviewLightDark` previews in `TodayScreen.kt`.
  On the emulator they need a reseed from the debug menu, which wipes the database, so I left
  that to Priyanshu:
  - `milestone-pending` → 05
  - `not-started` → 06
  - `ended-completed` / `ended-failed` → 08

### Tests

- `./gradlew :domain:test`: 161 / 161.
- `./gradlew :app:testDebugUnitTest`: 53 / 53. New: one `TodayViewModelTest` case (intention and
  planned commitments) and `SpellOutTest`. Updated:
  - the `NotStarted` and `Ended` equality tests, which now cover `endedOn`, `season` and the
    recap counts
  - the milestone tests, which now check `triggeredOnDate`, `arcName` and `todayDayNumber`
