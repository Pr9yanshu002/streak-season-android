# Task 20: creation rules into the contract

## Context

The task 19 review (see `docs/implementationSessions/task-19b-report.md`,
"Follow-ups") found rules for creating an arc that live only in `:app`:

- **D1** The create flow's "today" is the calendar date, not R1's
  `effectiveToday`. Between midnight and the boundary hour, R16's earliest
  start date is one day too late.
- **D2** `lengthInDays` re-derives R2's total day count.
- **D4** The commitment editor's checks (target above 0, a unit, at least one
  day) aren't in `rules.md`.
- The limits of **10 commitments** and **7 rest days a week** are app-only.

Anything left only in `:app` is something the Swift app would have to copy by
hand, and could get wrong. This task moves all of it into the contract.

### Decisions already made (Priyanshu)

1. **"Today" while creating follows the arc's own boundary.** R1 is applied
   with the `day_boundary_hour` chosen in the flow. Changing the boundary on
   the strictness step can move today, and so R16's earliest start, by one
   day. That's intended.
2. **Both limits become numbered rules:** at most 7 rest days a week, and at
   most 10 commitments per arc.

This task has three parts, in order. **Each part must be green before the next
starts. Stop after each part** so Priyanshu can review it.

---

## Part 1 — `rules.md`

### Amend R16

State that "today" in R16 is `effectiveToday(now, day_boundary_hour)` (R1),
using the boundary hour **the new arc will have**, not a fixed 4.

Add a worked example: boundary 4, now = 2026-01-15 02:30, backfill 2 →
effective today 2026-01-14 → earliest start 2026-01-12.

### Add R20 — a valid commitment

A commitment can be created when **all** of:

1. `name` contains a non-whitespace character
2. for `counter` and `duration`: `target_value` > 0 **and** `unit` is
   non-blank
3. for `binary` and `log`: `target_value` is null
4. for `schedule_type = 'weekdays'`: `schedule_days` holds at least one day,
   each in 1–7

Say in one sentence why: a counter with no target can never be satisfied (R4),
and a weekdays commitment with no days is never scheduled (R3).

### Add R21 — creation limits

- `rest_tokens_per_week` is 0–7. A week has seven days, so more can never be
  used (R6). Strict and chill arcs store 0 (R6 already says they're treated as
  0; record that the app also stores 0).
- An arc has at most **10** commitments when it is created.

Number them R20 and R21 (R19 is the recap). Don't renumber anything.

`./gradlew :domain:test` still green, unchanged. Commit. **Stop.**

---

## Part 2 — `:domain`

1. Make `effectiveToday(now, boundaryHour)` **public**, with a KDoc naming R1.
2. Make the R2 total-day-count function **public**, with a KDoc naming R2. If
   its current name or shape is awkward to call from outside, report that
   instead of renaming it.
3. In `Validation.kt`, add:
    - `const val MAX_REST_TOKENS_PER_WEEK = 7` and
      `const val MAX_COMMITMENTS_PER_ARC = 10` (R21)
    - `fun isValidRestTokensPerWeek(n: Int): Boolean` (R21)
    - `fun canAddCommitment(currentCount: Int): Boolean` (R21)
    - `enum class CommitmentProblem { BLANK_NAME, TARGET_NOT_POSITIVE, MISSING_UNIT, NO_DAYS }`
    - `fun commitmentProblems(name, type, targetValue, unit, scheduleType, scheduleDays: Set<Int>): List<CommitmentProblem>`
      returning every problem, in the order above, or an empty list (R20).
      It takes plain values, like `hasAtLeastOneRequiredCommitment` does,
      because `:domain` has no notion of the app's draft.
    - KDoc on each naming its rule.
4. `R20 condition 3` (binary/log have no target) is something the app ensures
   when saving, not something a person can get wrong. Don't add a problem for
   it; mention in the KDoc that callers clear the target for those types.
5. Tests in `ValidationTest.kt`, the same style as R16–R18: every problem on
   its own, several at once, a valid commitment of each type, the edges of both
   limits (0, 7, 8; 9, 10 commitments), and the R16 worked example from Part 1
   through `effectiveToday` + `earliestAllowedStartDate`.

No fixture changes: fixtures test `resolve()`, and none of this changes it.

`./gradlew :domain:test` green. Commit. **Stop.**

---

## Part 3 — `:app` create flow

1. **Today follows R1.** In `CreateArcUiState`, replace `today: LocalDate`
   with `now: LocalDateTime`, and add
   `val today: LocalDate get() = effectiveToday(now, dayBoundaryHour)`.
   The ViewModel's `init` passes `clock.now()` and sets the default start and
   end dates from `effectiveToday(now, DEFAULT_DAY_BOUNDARY_HOUR)`.
   Remove the comment that explains why the calendar date was used.
2. **Length from R2.** `lengthInDays` calls the now-public R2 function.
3. **Editor checks from R20.** `CommitmentDraft.error` calls
   `commitmentProblems(...)` and turns the **first** problem into the existing
   words. No copy changes.
4. **Limits from R21.** `setRestTokensPerWeek` and the remix pre-fill use
   `MAX_REST_TOKENS_PER_WEEK`. The screen's `MAX_COMMITMENTS` is replaced by
   `MAX_COMMITMENTS_PER_ARC` and `canAddCommitment(...)`.
5. **Tests** in `CreateArcViewModelTest`:
    - update setup for `now` instead of `today`
    - at 02:00 with boundary 4, `today` is the previous calendar day, and a
      start date two days before *that* is valid
    - changing `dayBoundaryHour` from 4 to 0 at 02:00 moves `today` forward by
      a day, and a start date that was valid becomes a `strictnessError`
    - all existing tests still pass.

`./gradlew :domain:test` and `:app:testDebugUnitTest` green. Commit. **Stop.**

---

## Constraints

- Rules first, `:domain` second, `:app` last.
- No change to `resolve()`, fixtures, schema, or any screen's look or copy.
- No rule logic left in `:app` for anything R16, R20 or R21 covers.
- No new dependencies.

## Definition of done

- R16 amended; R20 and R21 added.
- `effectiveToday`, the R2 count and the new validation functions are public
  in `:domain`, with KDoc and tests.
- The create flow calls them and computes nothing these rules cover.
- On the emulator, at a time before the boundary hour (set the emulator clock
  to 02:00), "Today" on step 1 is the previous calendar day.
- All tests green.

## Report back

Write `docs/implementationSessions/task-20-report.md` per `CLAUDE.md`:

- The exact R16/R20/R21 text added.
- Anything in `:domain` that was awkward to make public, and what you did.
- Every place in `:app` that changed, and confirmation that no copy changed.
- Anything else in `:app` that still looks like a rule and isn't covered.