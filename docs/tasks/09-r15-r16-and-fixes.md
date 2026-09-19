# Task: R15 and R16, fixture contract catch-up, and three fixes

## Context

The Today screen works end to end. A rule-duplication problem was found and
fixed: `Resolution` now exposes `dayNumber`, `totalDays` and
`commitmentStatuses`, and `:app` carries no rule logic.

Three things are outstanding before arc creation can be built. This task clears
all of them, plus two new rules.

Work through the parts in order. Part 2 is the one that matters most.

---

## Part 1 — Add R15 and R16 to `rules.md`

Append these two rules. Keep the existing numbering and formatting style.

```markdown
## R15 — One active arc

At most one arc may have `status = 'active'` at a time.

Creating a new arc while an active arc exists is blocked. The user must
complete, fail, or archive the current arc first.

`draft` is not used in v0 — the creation flow holds state in memory and writes
the arc only when the user confirms it.

## R16 — Valid start date

`start_date` may be in the past, but not before `effectiveToday - backfill_days`.

A start date earlier than that produces sealed missed days (R7) at the moment of
creation, which under strict mode fails the arc immediately (R9). The rule is
R7 stated from the other direction: an arc may only start on a day the user
could still check in for.

`start_date` may be in the future. Before the arc starts, `dayNumber` is 0 (R2).
```

Also record the R2 addition (`dayNumber` / `totalDays`) if it is not already
written into `rules.md` from the earlier session.

### Where these two rules live in code

They are validation rules, not things `resolve()` computes. Do not add them to
`RulesEngine.resolve()`.

- **R16 is portable and belongs in `:domain`.** Add a pure function, e.g.
  `earliestAllowedStartDate(today: LocalDate, backfillDays: Int): LocalDate`,
  plus whatever small validator wraps it. Plain Kotlin unit tests are fine.
- **R15 is a persistence question** ("does an active arc exist?") and belongs in
  `:app`, in the repository. It cannot be a pure function over in-memory lists.
     
---

## Part 2 — Bring the fixture contract up to date

This is the important part.

`Resolution` gained `dayNumber`, `totalDays` and `commitmentStatuses`, and seven
tests were written for them as Kotlin unit tests rather than fixtures. That
leaves rules the cross-client contract does not cover — which means the Swift
port could get them wrong and nothing would catch it.

The whole discipline depends on the fixture set being complete. Close the gap:

1. Extend the `expect` block in `contract/fixtures/README.md` to cover
   `day_number`, `total_days` and `commitment_statuses`. Keep the existing
   conventions: snake_case keys, every field optional, assert only what the
   fixture is about.

   `commitment_statuses` is nested — date, then commitment id, then status
   string. Pick the shape that reads most clearly in JSON and document it with
   an example.

2. Extend `Expect.kt` and the assertion logic in `RulesEngineFixtureTest.kt` to
   match.

3. Port the seven Kotlin tests into fixtures in `contract/fixtures/`, one file
   each, following the existing naming and style. Cover at minimum:
    - `day_number` before the arc starts (0)
    - `day_number` pinned at `total_days` after the arc ends
    - `commitment_statuses` returning `NOT_SCHEDULED` for a weekday-scheduled
      commitment on an off day
    - `PENDING` vs `DONE` for each commitment type

4. **Delete the seven Kotlin rule tests** once the fixtures cover them. Two
   copies of the same assertion will drift, and the fixture is the one that
   ports.

Derive every expected value from `rules.md` by hand. Do not read them off the
existing Kotlin tests — if a test has a wrong expectation, copying it forward
makes it permanent.

---

## Part 3 — `TodayViewModel` lifecycle

It is currently a plain class registered as a Koin `single` with a
hand-made `CoroutineScope`. A `single` lives for the whole process, so it
collects the first `arcId` forever. That breaks the moment a second arc exists.

Make it a real `androidx.lifecycle.ViewModel`:

- Use `viewModelScope`, not a hand-made scope.
- Register with Koin's `viewModel { }` and obtain it with `koinViewModel()`.
- Check whether `lifecycle-viewmodel-compose` and `koin-androidx-compose` are
  already on the classpath before adding anything. If they are not, add them via
  the version catalog — this is a dependency worth taking.

---

## Part 4 — `startKoin` guard

`StreakSeasonApp.onCreate()` calls `startKoin()` unconditionally, so any test
touching an Application context collides. This is currently worked around with
`stopKoin()` in `tearDown()`.

Add a proper guard in the application class and remove the workaround from the
tests.

---

## Constraints

- Do not change `RulesEngine.resolve()`'s behaviour. R15 and R16 are validation,
  not resolution.
- Do not change existing fixtures' expected values. Adding new `expect` keys to
  an existing fixture is fine; changing an answer is not.
- No new rule logic in `:app`. R15's repository query is a persistence question,
  not a rule computation.
- No UI work in this task. Arc creation comes next.

## Definition of done

- `rules.md` has R15 and R16, and R2 reflects `dayNumber` / `totalDays`.
- `./gradlew :domain:test` green, with the fixture count risen by the number of
  new fixtures and the seven Kotlin rule tests gone.
- `TodayViewModel` is an `androidx.lifecycle.ViewModel` using `viewModelScope`.
- Tests pass without `stopKoin()` in `tearDown()`.
- App still runs: seeded arc loads, check-in works, state persists.

## Report back

- New fixture count, and the list of fixtures added.
- The JSON shape chosen for `commitment_statuses`, with one example.
- Confirmation that the seven Kotlin rule tests are deleted, not just
  duplicated.
- Any expected value in the seven ported tests that turned out to disagree with
  `rules.md` — that would mean a rule was implemented from intuition rather than
  from the contract, which is worth knowing.
- Whether `lifecycle-viewmodel-compose` and `koin-androidx-compose` were already
  present or had to be added.