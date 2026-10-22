# Task 18: Today screen — build the design

## Context

The Today screen works, but it's stock Material 3: mostly text, no day
discs, no cards. The designs for every Today state are in
`docs/design/today/` (see its README for how to read them). Task 16 already
added the theme: colour tokens, `StreakColors`, Bricolage Grotesque and
Literata.

This task makes the Today screen look like those designs. **Behaviour does
not change.** Every number, state and condition on screen still comes from
`Resolution`, exactly as today.

Precondition: task 17 (rest days) is merged to `main`.

This task has three parts. **Stop after each part** so Priyanshu can check it
on the emulator before the next one starts.

---

## Part 1 — components only

Build the reusable pieces in `ui/components/` (or the existing equivalent).
Each one is a stateless composable that takes plain data and lambdas, with
`@Preview`s in **light and dark**. No screen changes yet.

| Component | What it draws | Design reference |
|---|---|---|
| `DayDisc` | one day state as a disc: complete, partial, missed, rest, future, today. Shape carries the meaning, not just colour | `disc()` in any design file |
| `WeekStrip` | Monday–Sunday row of `DayDisc`s with day letters; today's letter bold larch | 01 |
| `StreakPill` | "N-day streak" pill | 01 header |
| `BinaryCommitmentCard` | round tick button + name + "Done"/status | 01 Gym |
| `CounterCommitmentCard` | name, "X of Y unit", − and + buttons, progress bar | 01 Water, Reading |
| `NumberCommitmentCard` | name, number field, unit, helper text | 01 Weight |
| `UnscheduledCommitmentRow` | dashed row, "Not today · <schedule>" | 01 Long walk |
| `BonusChip` | "Bonus" chip for non-required commitments | 01 Reading |
| `RestDayCard` | both forms of the task-17 rest row | 01 bottom, 03 top |

Rules:
- If `DayDisc` or similar already exists from task 16, reuse it.
- Touch targets at least 44 dp, matching the design's buttons.
- Every disc gets a content description ("Monday, complete"), so the strip
  works with TalkBack.
- Use tokens from the theme. No hard-coded colours.

`./gradlew :app:testDebugUnitTest` green. Commit. **Stop.**

---

## Part 2 — the active day (designs 01, 02, 03)

Rebuild the `Loaded` state of the Today screen from the Part 1 components:

1. Header: arc name, today's date, `StreakPill` (hidden when the current
   streak is 0).
2. "Day N of M" (large).
3. `WeekStrip` for the current ISO week.
4. "Today" + "X of Y required done".
5. Commitment cards, required first, then bonus, then unscheduled.
6. `RestDayCard`, under the same conditions as task 17.

State variations:
- **Day complete (02):** headline becomes "Day N done" with "Everything
  required, N days in a row."; completed counters show "· done"; bonus shows
  "doesn't affect your day"; the rest card is hidden (as in task 17).
- **Rest day (03):** the rest card moves to the top. Required commitments
  move under "Still here if you want them / Anything you log today still
  counts." Commitments still work.

Data rules:
- Day states, streak, day number, required-done counts and token counts all
  come from `Resolution`, mapped into `TodayUiState`. **No rule logic in the
  ViewModel or composables.**
- The week strip's dates (this Monday to Sunday) may be worked out in the
  mapper; their **states** must come from `Resolution.dayStates`. Dates
  outside the arc show an empty slot, not a disc.
- The number field keeps its task-10 behaviour: it saves on done or when focus
  leaves, never per keystroke.

`./gradlew :domain:test` and `:app:testDebugUnitTest` green. Commit. **Stop.**

---

## Part 3 — the other states (designs 04–08)

Restyle the remaining Today states to match:

- **04 / 05 milestones:** the single milestone card and the "You've passed N
  milestones" summary card (the collapse behaviour from task 15 stays).
- **06 not started:** "Starts <date> · N days", the big countdown, the
  intention in Literata italic, and the list of commitments with schedules.
- **07 no arc:** the four-step explainer and "Start an arc".
- **08 ended:** "Ended <date>", the headline, "You showed up X of Y days",
  "See your recap", "Start something new".

Same data rules as Part 2. Commit. **Stop.**

---

## Theme and colours

Task 16 built the theme. This task **uses** it and doesn't change it.

- Colours with a Material slot: `MaterialTheme.colorScheme` (`primary` =
  evergreen, `background` = frost, `surfaceVariant` = sunken, `outline` =
  line-strong, `tertiary` = larch …).
- Colours without one (`missed`, `rest`, `larch-soft`, `sunken`, `line`):
  `StreakTheme.colors`.
- Text: `MaterialTheme.typography` slots from task 16 (`displayMedium` for
  "Day 52", `titleLarge` for "Today", `labelLarge` for pills and buttons,
  `bodySmall` for captions).
- The user's own words (intention, milestone notes): `StreakTheme.type.voice`
  (Literata italic), never a Material style.
- Corners and spacing: `MaterialTheme.shapes.medium` (16 dp) for cards,
  `CircleShape` for discs and buttons, `Space.*` for padding.
- **Fix every hard-coded colour in the Today screen** that the task-16 report
  listed (`Color(0xFF…)`, `Color.White`, `Color.Black`).
- If the design uses a colour or size the theme doesn't have, don't add one
  locally. List it in the report.

---

## Constraints

- No schema change, no new fields, no new dependencies.
- No rule logic in `:app`.
- No animation in this task. It comes later, once the static screen is right.
- If the design shows something the data model doesn't have, **don't invent
  it.** Leave it out or use what exists, and list it in the report. Known
  candidates to check:
  - a step size for counters (the design adds 250 ml or 5 min per tap)
  - the schedule text for unscheduled commitments ("Saturdays and Sundays")
  - the arc's intention ("You wrote …")
  - an optional note on a milestone ("A line for later")

## Definition of done

- All three parts committed, each checked on the emulator.
- Every Today state matches its design closely in light and dark mode.
- All tests green.

## Report back

Write to `docs/implementationSessions/task-18-report.md` per `CLAUDE.md`:

- Each component built, and any that already existed.
- **Every gap between the design and the data model**, and what you did
  instead.
- Anything in the design you couldn't match, and why.
- Screenshots or a short note per state, light and dark.