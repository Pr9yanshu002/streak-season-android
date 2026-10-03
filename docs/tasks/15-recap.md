# Task 15: the recap

## Context

When an arc ends — completed or failed — the user should get a recap: a short
story of what happened, ending in what to do next. It is the feature that makes
this app different from a habit tracker, and the reason milestones and
intentions are collected at all.

This task builds it **without photos**. Milestone photo capture was deferred and
remains out of scope. It also builds it **without image export** — sharing the
recap as a PNG is the next task.

The task has two parts, and Part 1 must be finished and green before Part 2
starts:

- **Part 1 — recap stats in `:domain`.** New rules, new fixtures, a pure function.
- **Part 2 — the recap screen in `:app`.** Reads those stats; computes nothing.

---

## Part 1 — recap stats as rules

### Why these go in the engine

Every number on the recap — days shown up, hardest week, totals per commitment —
is something the Swift app must compute identically. A number computed only in a
Kotlin view model is one iOS will reinvent differently. So they are rules in
`rules.md`, computed in `:domain`, and pinned by fixtures.

### Add R19 to `rules.md`

Append after R18, in the existing style. Use this content:

```markdown
## R19 — Recap statistics

Computed once an arc has ended (`completed` or `failed`). All statistics are
over the **recap period**: `start_date` through `end_date` for a completed arc,
or `start_date` through `failed_on_date` for a failed one. Days after a failure
do not count, even if the user kept logging.

**Headline.** `completeDays` over `countedDays`, using exactly R8's counted set
restricted to the recap period: the same exclusions (rest, pending, future, and
days with no required commitment scheduled). The headline and the completion
percentage must always agree — they are two views of one ratio, never two
definitions.

**Best streak.** R11's `bestStreak`, restricted to the recap period.

**Hardest week.** The ISO week (same week keys as R6) with the lowest ratio of
complete days to counted days, among weeks with **at least 3 counted days** in
the recap period. Weeks with fewer are excluded — a two-day edge week where one
day was missed would otherwise dominate. Ties go to the earliest week. If no
week qualifies, there is no hardest week.

**Per-commitment totals**, over days in the recap period where the commitment
was scheduled and active:

- `binary` — the number of days it was done.
- `counter` and `duration` — the sum of recorded values, plus the number of days
  the target was met.
- `log` — the first and last recorded values, by date, and the number of days a
  value was recorded. No pass or fail.

Soft-deleted entries never count. Commitments removed mid-arc are included,
over the days they were active.
```

If anything in this rule turns out ambiguous while implementing — and it may —
**stop and report** rather than choosing. A choice made silently in Kotlin is
exactly what the fixtures exist to prevent.

### The function

A separate pure function in `:domain`, not part of `resolve()`:

```kotlin
fun recap(
    arc: Arc,
    commitments: List<Commitment>,
    days: List<Day>,
    resolution: Resolution,
): Recap?
```

Returns `null` unless `resolution.arcStatus` is `COMPLETED` or `FAILED`.

It takes the `Resolution` rather than recomputing day states — R19 is defined in
terms of R8 and R11, so it consumes their output instead of re-deriving it. Two
implementations of "which days count" is the drift this project is built to
avoid.

Kept out of `Resolution` because `resolve()` runs on every database change, and
the recap is shown once.

Shape is your call, but it must carry: headline numerator and denominator, the
completion percentage, best streak, hardest week (ISO week key plus its complete
and counted days, or null), and one total per commitment. Use a sealed type for
the per-commitment total so `binary`, `counter`/`duration` and `log` each carry
only the fields that apply.

### Fixtures

Extend `contract/fixtures/README.md` and `ExpectDto` with an `expect.recap`
block. The harness calls `recap()` when that block is present.

Cover at minimum:

- the headline and `completion_pct` agree on a completed arc with rest days and
  unscheduled days in it
- a failed arc, with entries logged **after** `failed_on_date` that must not
  count
- hardest week: a short edge week excluded for having fewer than 3 counted days
- hardest week: a tie, resolved to the earliest week
- hardest week: an arc too short for any week to qualify
- per-commitment totals for all four commitment types
- a commitment removed mid-arc, counted only over its active days
- an active arc, where `recap()` returns null

Derive every expected value from R19 by hand. The fixture key check from task 13
will catch a misspelled `expect.recap` key — confirm it covers nested keys
inside `recap`, and extend it if not.

---

## Part 2 — the recap screen

### What it shows

A vertical pager, one card per screen, in this order. Cards marked *skip if* are
left out entirely, not shown empty.

1. **Title** — arc name, date range, and the headline: "You showed up 68 of 75
   days." For a failed arc: "You made it to day 19."
2. **Intention** — the day-1 intention, verbatim. *Skip if blank.*
3. **Best streak.**
4. **Hardest week** — which week, and its numbers. Neutral tone: "Your toughest
   week was 13–19 Jan — 3 of 6 days." Not a scolding, not forced cheer.
   *Skip if there is no hardest week.*
5. **What you did** — one row per commitment, the biggest number first. "41
   hours of reading." "Weight: 74.2 → 71.8." Units come from the commitment.
6. **Milestones** — each milestone with its crossing date, and its reflection if
   there is one. Read from the `milestones` table, not the engine. *Skip if no
   milestone beyond `start` exists.*
7. **Completion** — the percentage, large.
8. **What next** — two actions:
    - **Run it again** — opens the create flow pre-filled, using the existing
      remix path. Works for completed and failed arcs alike.
    - **Start something new** — opens the blank create flow. No pre-fill.

### Getting there

The `Ended` state on Today gets a **View recap** button. No auto-open, and no
"recap seen" tracking — that would need a schema change, which is out of scope.

### Rules for the screen

- **No computation in the view model or composables.** Every number comes from
  `Recap`. If a card needs a value `Recap` does not have, the fix is in Part 1,
  not in the UI. Say so in the report if it happens.
- **Size-independent cards.** Each card takes its size from its parameters, not
  from screen measurements. The next task renders these same composables into a
  PNG at a fixed export size; a card that reads `LocalConfiguration` or assumes
  phone dimensions will have to be rewritten then.
- Stock Material 3 with a clear typographic hierarchy — big numbers large,
  context small. **No custom animation, no palette work.** A design pass comes
  after the content has been seen working on a real arc.

### Ended arcs keep their status

Neither action changes the ended arc. Its status stays `completed` or `failed`
permanently. **Nothing in this task writes `archived`.**

The status column records how the arc ended. A history screen and a
longer-period recap are planned later, and both read that fact — `recap()` only
runs for `completed` or `failed`. Overwriting it with `archived` would leave the
data intact but make the arc's recap unreachable and its outcome unknowable.
`archived` stays unused for now, like `draft`.

No "retire" or "hide" action is needed: R15 only blocks a new arc while one is
**active**, and an ended arc is not active, so the create flow is already
reachable. Today keeps showing the most recent ended arc until a new arc exists.

Confirm:

- after either action creates a new arc, the previous arc's status is unchanged
  in the database
- Today shows the new arc once it exists, and the ended arc before that
- R15 does not block creating a new arc while the latest arc is `completed` or
  `failed`

---

## Constraints

- Part 1 green before Part 2 starts.
- `resolve()` behaviour does not change. No existing fixture's expected values
  change.
- No schema change. No new dependencies.
- No photos, no image export.
- Nothing writes `status = 'archived'`. History and longer-period recaps come
  later and depend on ended arcs keeping `completed` / `failed`.

## Definition of done

- R19 in `rules.md`.
- `recap()` in `:domain`, with fixtures for every case listed, all green.
- The recap screen, reachable from `Ended`, showing the cards above with the
  skip rules applied.
- Run it again and Start something new both work end to end, and the ended
  arc's status is unchanged afterwards.
- `./gradlew :domain:test` and `:app:testDebugUnitTest` green.

## Report back

Write to `docs/implementationSessions/task-15-report.md` and commit it with the
task, per `CLAUDE.md`.

- Anything in R19 that was ambiguous, and where you stopped to ask.
- The fixtures added, with the hand-derived values for the hardest-week tie case.
- Whether the key check covered nested `recap` keys or needed extending.
- Any value a card needed that `Recap` did not provide.
- What the recap looks like on a real arc from `DebugSeeder` — completed and
  failed — card by card.
- Honestly: does it read as a story, or as a stats page? That answer decides
  what the design pass needs to do.