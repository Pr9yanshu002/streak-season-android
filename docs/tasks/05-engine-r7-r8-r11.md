# Task: rules engine, slice 3 — R7, R8, R11

## Context

R1–R6 are implemented. 27 of 45 fixtures are green. This task adds the three
rules that read day states and derive numbers from them: sealing, completion
percentage, and streaks.

Read `contract/rules.md` R7, R8 and R11 first. They are authoritative and each
is short — R8 and R11 are given as pseudocode, so follow them literally rather
than reimplementing from the prose description.

## Why this slice is easier than the last

R6 was hard because token validity depended on other days in the same week, so
day resolution could not happen in one pass.

These three are pure consumers. Day states are already final by the time these
run. Nothing here changes a day state, and nothing here feeds back into R1–R6.
If you find yourself wanting to modify a day state in this slice, something has
gone wrong.

## Scope

- **R7 — sealing.** `sealedDates` in `Resolution` returns real values.
- **R8 — completion percentage.** `completionPct` returns a real value.
- **R11 — streaks.** `currentStreak` and `bestStreak` return real values.

Still out of scope, keep the placeholders: `arcStatus` stays `arc.status`
unchanged, `failedOnDate` stays `null`. R9 and R10 come next.

## The rules, restated

**R7.** A date `D` is sealed when `today > D + backfill_days`. Today and any
unsealed past date are editable. Note this produces sealed dates only — the
engine does not enforce editability, that is the app's job.

**R8.** Three exclusions from the denominator, and all three have fixtures:
`rest`, `pending`/`future`, and any date with no required commitment scheduled.
Rest days come out of the numerator too. `0.0` when the counted set is empty.

Round to 4 decimal places, half up. Use `BigDecimal` with `RoundingMode.HALF_UP`
— not `String.format`, not `Math.round`, not `"%.4f"`. Kotlin's default `Double`
rounding is not half-up and will produce off-by-one-in-the-last-digit failures.

**R11.** Current streak walks backwards from today; a `pending` today is skipped
rather than counted or breaking the run. Best streak is the longest run over
elapsed dates. Both treat `rest` as continuing the run, **and** counting toward
its length — a token is a pass, not an erasure. That distinction has a fixture.

## Constraints

- **Never read the clock.** `now` is a parameter.
- No new dependencies beyond `java.math.BigDecimal`, which is standard library.
- Follow the pseudocode in R8 and R11 structurally. Where `rules.md` gives an
  algorithm, the code should be recognisably the same algorithm — that is what
  makes the Swift port a translation rather than a reimplementation.
- Explicit over clever. A readable loop beats a `windowed`/`fold` chain here,
  especially for best streak.
- Do not restructure R1–R6. If a change there is genuinely needed, stop and
  report it.

## Definition of done

`./gradlew :domain:test` passes everything from slices 1 and 2 plus:

- `seal_within_backfill_editable`
- `seal_outside_backfill_locked`
- `seal_zero_backfill`
- `streak_basic_run`
- `streak_broken_by_miss`
- `streak_rest_token_continues`
- `streak_pending_today_skipped`
- `streak_best_differs_from_current`
- `pct_excludes_rest_days`
- `pct_excludes_pending_today`
- `pct_excludes_unscheduled_days`

Note that `edge_all_commitments_bonus` is currently green **by coincidence** —
it asserts `completion_pct: 0.0` and the placeholder is `0.0`. After this slice
it should still be green, but for the right reason: the counted set is empty
because no day has a required commitment scheduled. Confirm that in your report.

No previously green fixture may go red.

**Do not make a fixture pass by special-casing it.** If a fixture in scope fails
and the rule as written says it should pass, the implementation is wrong. If the
fixture's expected value looks wrong, stop and report it.

## Report back

- Green count and which fixtures moved.
- For each fixture asserting `completion_pct`, the fraction the engine computed
  (e.g. `4/5 = 0.8000`), so it can be checked against the fixture by hand.
- Confirmation that `edge_all_commitments_bonus` now passes because the counted
  set is empty, not because of the placeholder.
- Anything in R7, R8 or R11 that was ambiguous, and the interpretation chosen.
- Any rounding or date-arithmetic behaviour you expect to differ in Swift. Add a
  line to `docs/notes.md` — rounding under a "Numbers" heading, date walking
  under "Dates". Swift's `rounded()` is banker's rounding by default, which is
  not what R8 specifies; that one is worth recording now.
