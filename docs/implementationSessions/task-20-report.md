# Task 20 — Creation rules into the contract: report

Branch `task-20-creation-rules-into-contract`. Priyanshu asked Claude to do
the whole task, including the `:domain` part that Claude would normally only
review. Each part was committed separately once green:

- Part 1, `rules.md`: `404077a`
- Part 2, `:domain`: `d32f312`
- Part 3, `:app`: `9ef7d44`

Four choices were Claude's, made where the brief left a gap. Priyanshu said
to go ahead with Claude's defaults:

1. R20 says each weekday is in 1–7, so `CommitmentProblem` has a fifth value,
   `DAY_OUT_OF_RANGE`, after `NO_DAYS`.
2. R21 records that strict and chill arcs store 0 rest tokens, so `:domain`
   has `restTokensToStore(strictness, n)` and `begin()` calls it.
3. R2's day count took an `Arc`; see "Awkward to make public" below.
4. The brief file itself was committed with Part 1.

## Text added to `rules.md`

### R16, appended

> "Today" here is `effectiveToday(now, day_boundary_hour)` (R1), using the
> boundary hour **the new arc will have**, not a fixed 4. Changing the boundary
> while creating the arc can move today, and so the earliest start, by one day.
>
> Worked example, boundary = 4, `backfill_days` = 2:
>
> | Local now          | effectiveToday | Earliest start |
> |--------------------|----------------|----------------|
> | 2026-01-15 02:30   | 2026-01-14     | 2026-01-12     |

### R20 — A valid commitment

> A commitment can be created when **all** of:
>
> 1. `name` contains at least one non-whitespace character
> 2. for `counter` and `duration`: `target_value` > 0 **and** `unit` is
>    non-blank
> 3. for `binary` and `log`: `target_value` is null
> 4. for `schedule_type = 'weekdays'`: `schedule_days` holds at least one day,
>    and each day is in 1–7 (Mon = 1 … Sun = 7)
>
> A counter or duration with no target can never be satisfied (R4), and a
> weekdays commitment with no days is never scheduled (R3).

### R21 — Creation limits

> - `rest_tokens_per_week` is 0–7. A week has seven days, so more could never be
>   used (R6). Strict and chill arcs store 0: R6 already treats their allowance
>   as 0, and the stored value says the same.
> - An arc has at most **10** commitments when it is created.

No other rule was renumbered or reworded.

## `:domain`

- `effectiveToday(now, boundaryHour)` is public. Its KDoc names R1 and why
  `:app` needs it.
- **Awkward to make public:** the R2 count was `totalDays(arc: Arc)`. The
  create flow has two dates and no `Arc`, and building one just to count days
  would mean inventing an id, a status and a timestamp. It was not renamed.
  A public overload `totalDays(startDate, endDate)` was added, and
  `totalDays(arc)` now delegates to it. Both are public with R2 KDoc.
- `Validation.kt`:
  - `MAX_REST_TOKENS_PER_WEEK = 7` and `MAX_COMMITMENTS_PER_ARC = 10` (R21)
  - `isValidRestTokensPerWeek(n)` (R21)
  - `restTokensToStore(strictness, n)` (R21)
  - `canAddCommitment(currentCount)` (R21)
  - `enum class CommitmentProblem { BLANK_NAME, TARGET_NOT_POSITIVE,
    MISSING_UNIT, NO_DAYS, DAY_OUT_OF_RANGE }`
  - `commitmentProblems(...)`, which returns every problem in that order (R20).
    Its KDoc says R20.3 has no problem because callers clear the target for
    binary and log. A target of NaN or Infinity counts as
    `TARGET_NOT_POSITIVE`.
- `resolve()`, fixtures and the schema are unchanged. No new files in
  `:domain`, and no clock reads.

## `:app` changes

All in `ui/create/` and its tests:

- **`CreateArcViewModel.kt`:**
  - `CreateArcUiState.today: LocalDate` became `now: LocalDateTime`, and
    `today` is now a getter, `effectiveToday(now, dayBoundaryHour)`.
  - `init` passes `clock.now()` and sets the default dates from
    `effectiveToday(now, DEFAULT_DAY_BOUNDARY_HOUR)`. The comment explaining
    the calendar-date workaround is gone.
  - `lengthInDays` calls `totalDays(startDate, endDate)`.
  - `CommitmentDraft.error` maps the first `commitmentProblems()` result to
    the same words as before. `DAY_OUT_OF_RANGE` uses the `NO_DAYS` words;
    the day picker can't produce it.
  - `setRestTokensPerWeek` and the remix pre-fill clamp to
    `MAX_REST_TOKENS_PER_WEEK`.
  - `begin()` stores `restTokensToStore(...)`.
- **`CreateArcScreen.kt`:** the private `MAX_COMMITMENTS` is gone. The
  counter reads `MAX_COMMITMENTS_PER_ARC`, and the add button shows when
  `canAddCommitment(...)`. The preview's sample state uses `now`.
- **Tests:**
  - `CreateArcViewModelTest` gains a `viewModelAt(time)` helper and two tests.
    At 02:00 with boundary 4, today is the previous day and a start two days
    before it is valid. Moving the boundary to 0 at 02:00 moves today forward
    and turns that start into a `strictnessError`.
  - `CreateArcTextTest`'s sample state uses `now`.

**No copy changed.** The diff's only changed strings are the five editor
messages, which moved into the new `when` word for word, and the "N of 10"
counter, which now reads the domain constant.

## Still in `:app` and looks like a rule

- **"Isn't a number."** `CommitmentDraft.error` still checks that the
  target text parses (`parseAmount`, with the phone's decimal comma). This is
  input reading, not R20, so it stayed.
- **End date not before the start date** (`endDateError`). No rule says
  this; R2 only assumes it.
- **Day boundary hour 0–23** (`setDayBoundaryHour`). The range is in a
  `schema.sql` comment, not `rules.md`.
- **Backfill of at least 0, with no upper limit** (`setBackfillDays`). This
  isn't in `rules.md` either.
- **`begin()` doesn't re-check R20 or R21.** The 10-commitment limit and the
  per-commitment checks are applied as you edit: the add button hides at 10,
  and `saveEditing` refuses an invalid draft. `begin()` only re-checks
  R16–R18. A remixed arc can't exceed 10 today, but nothing at Begin says so.
- **Clearing fields that don't apply.** `cleaned()` clears target, unit and
  days that don't apply to the kind. This is what R20.3 expects callers to
  do; it's not a check.
- **Duplicate commitment names** only give a warning, as before.

`CLAUDE.md` still says "Rules are numbered R1–R18". That was already out of
date once R19 existed, and is now R1–R21; it isn't changed in this task.

## Tests

- `./gradlew :domain:test`: 177/177. That is 161 before, plus 16 new
  `ValidationTest` cases: each R20 problem alone and together, a valid
  commitment of each type, the R21 edges (0, 7, 8; 9, 10), `restTokensToStore`,
  `totalDays` from two dates, and the R16 worked example through
  `effectiveToday` + `earliestAllowedStartDate`.
- `./gradlew :app:testDebugUnitTest`: 117/117 (115 before, plus 2).

**Not checked on the emulator.** The definition of done asks for a check that,
with the emulator clock at 02:00, "Today" on step 1 is the previous calendar
day. Priyanshu needs to do that.
