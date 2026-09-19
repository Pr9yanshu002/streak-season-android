# Task: rules engine, slice 1 — R1 to R5

## Context

`RulesEngine.resolve()` is a `TODO()`. All 45 fixtures fail with
`NotImplementedError`. This task implements the first five rules, which is the
smallest slice that turns any fixture green.

Read `contract/rules.md` first. It is authoritative. If this brief and
`rules.md` disagree, `rules.md` wins.

## Scope — implement these only

- **R1** — day boundary. Turn `now` (a local datetime) plus
  `arc.day_boundary_hour` into `effectiveToday`.
- **R2** — range. Which dates are inside the arc, and the elapsed set used later
  by R8.
- **R3** — scheduling. Whether a commitment applies on a given date, accounting
  for `schedule_type`, weekday mask, and `active_from` / `active_until`.
- **R4** — satisfaction. Whether an entry satisfies its commitment, per type.
- **R5** — day state, **excluding the rest-token branch**.

## Explicitly out of scope

Leave these unimplemented, returning the placeholder values below:

| Field | Placeholder |
|---|---|
| `restTokensRemaining` | `emptyMap()` |
| `sealedDates` | `emptySet()` |
| `currentStreak`, `bestStreak` | `0` |
| `completionPct` | `0.0` |
| `arcStatus` | `arc.status` unchanged |
| `failedOnDate` | `null` |

In R5's ladder, the `rest` branch is skipped entirely for now — a day that would
have taken a rest token resolves to `partial` or `missed`. R6 comes next and
will slot in above those.

Do not implement R6–R14. Do not touch the fixtures, the contract, or the test
harness.

## Structure

Break the rules into separate small functions rather than one large `resolve()`.
Each rule should be readable on its own and testable in isolation:

```kotlin
internal fun effectiveToday(now: LocalDateTime, boundaryHour: Int): LocalDate
internal fun isScheduled(commitment: Commitment, date: LocalDate): Boolean
internal fun isSatisfied(commitment: Commitment, entry: Entry?): Boolean
internal fun dayState(...): DayState
```

`resolve()` composes them. Name each function after what it decides, and put the
rule id in a KDoc comment above it (`/** R3 — ... */`) so the code and the
contract can be read side by side.

## Constraints

- **Never read the clock.** No `LocalDate.now()`, `System.currentTimeMillis()`,
  or `Clock.systemDefaultZone()`. `now` is a parameter.
- No new dependencies. `java.time` and the standard library only.
- No coroutines, no interfaces, no DI. Plain functions over in-memory lists.
- Explicit over clever. This gets translated to Swift by hand — avoid Kotlin
  idioms that have no direct Swift equivalent. Prefer a readable `for` loop to a
  dense chain of `fold`/`groupingBy`.
- Order matters in R5's ladder. Implement it in the order written, including
  `complete` before `pending`.

## Definition of done

`./gradlew :domain:test` runs, and the fixtures in these groups pass:
`boundary_*`, `schedule_*`, `range_*`, `satisfy_*`, `state_*`.

Everything else stays red. Fixtures in the `rest_*`, `seal_*`, `strict_*`,
`streak_*`, `pct_*` and `edge_*` groups are expected to fail — they assert
fields this slice does not compute.

**Do not make a fixture pass by special-casing it.** If a fixture in scope fails
and the rule as written says it should pass, the implementation is wrong. If the
fixture's expected value seems wrong, stop and report it rather than bending the
engine to match.

## Report back

- Which fixtures went green, and the count.
- Any fixture in the in-scope groups that still fails, and why.
- Anything in R1–R5 that was ambiguous enough that you had to choose an
  interpretation, with the choice you made.
- A one-paragraph plain-language description of how `resolve()` is structured —
  what calls what, in what order.