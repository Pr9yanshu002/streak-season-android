# Task 14 — collapse catch-up milestone cards — report

> Filename note: the brief's "Report back" section named this file
> `task-15-report.md`, but the task and branch are **14**
> (`task-14-collapse-milestone-cards`), so it has been renamed to
> `task-14-report.md` to match. The brief's reference has been corrected too.

## Part 1 — the summary card

A presentation-only change, as specified: no `:domain`, `rules.md`,
`schema.sql` or fixture edits, no change to `resolve()` or to which milestones
exist or what dates they carry.

### Where the choice lives

`TodayViewModel` maps the whole pending list into a sealed
`PendingMilestoneUiState`:

- empty → `null` (no card)
- exactly one → `PendingMilestoneUiState.Single` — the task-12 card, unchanged
- two or more → `PendingMilestoneUiState.Summary` with one `Row` per milestone

The list arrives oldest-first from `observePendingByArc` (`ORDER BY
triggered_on_date ASC, created_at ASC`) and is **not** re-sorted in the view
model. `dayNumber` on each is still computed from the engine's exact
`triggered_on_date`, not from today.

### What the summary card shows — three pending milestones

Derived from the composable (`MilestoneSummaryCard`) and the two unit-tested
mappings; the card is plain string interpolation over the state. For a 20-day
arc opened after p25/p50/p75 had all been crossed (days 5/10/15):

```
You've passed 3 milestones.

A quarter — day 5 of 20, 5 Sep
Halfway — day 10 of 20, 10 Sep
Three quarters — day 15 of 20, 15 Sep

[ Reflection (optional) ______________ ]

            [ Continue ]
```

- Header copy is neutral ("You've passed N milestones.") — no "while you were
  away", so it is equally true when the second milestone crosses without any
  absence.
- Each row: short label — `day X of Y`, crossing date (`d MMM`, read from
  `triggered_on_date`).
- One optional reflection field. **Continue** acknowledges all three.
- You can see this live: the `milestone-pending` debug scenario
  (`DebugScenario.MILESTONE_PENDING`, "Halfway There") seeds a 20-day arc on
  day 10, so p25 and p50 are both pending — it now renders a two-row summary
  instead of two sequential single cards.

### `final` among them

Not special-cased — `final` maps to a `Row` like any other kind. If the arc
ended while milestones were pending, the summary lists them including the
final row, whose label is "Arc complete" (completion) or "Arc ended"
(failure), e.g.:

```
You've passed 3 milestones.

Halfway — day 10 of 20, 10 Sep
Three quarters — day 15 of 20, 15 Sep
Arc complete — day 20 of 20, 20 Sep

[ Reflection (optional) ______________ ]

            [ Continue ]
```

**How it reads followed by the `Ended` screen:** the card takes over the whole
screen and is checked before `state`, so the summary shows first; **Continue**
acknowledges everything, the pending list empties, and the `Ended`
(completed/failed) screen renders next. It reads as "here's the run you just
finished, milestone by milestone" → "here's the end-of-arc screen". That is a
reasonable order — the final row naming the completion/failure inside the
summary is a slightly redundant with the Ended screen that immediately follows,
but it is honest (the final milestone genuinely was pending) and avoids a
special case. No change recommended.

### Reflection target

Saved on the **most recent** milestone on the card (latest `triggered_on_date`
= `rows.last()`), exposed as `reflectionTargetId`; the others' `reflection`
stays null. A `Single`'s target is simply its own id.

### Acknowledging — the two requirements

**By the ids shown, not "everything pending".** `acknowledgeMilestone` captures
`pendingMilestone.value.ids` — exactly what the card rendered — and passes that
set down. A milestone that goes pending while the card is on screen is not in
the set and stays pending, so it is never buried unseen.

**Atomically.** The mechanism is a Room `@Transaction` suspend **default
method** on `MilestoneDao`:

```kotlin
@Transaction
suspend fun acknowledge(ids, reflection, reflectionMilestoneId, now) {
    acknowledgeIds(ids, now)                               // UPDATE … WHERE id IN (:ids)
    if (reflection != null) setReflection(reflectionMilestoneId, reflection, now)
}
```

Both writes run inside one Room transaction, so a kill between them cannot
leave half a summary acknowledged (which would resurface as a fresh card next
launch). `acknowledged_at` and `updated_at` are set on every row written, and
`updated_at` again on the reflection target — per the hard rules. Room 2.8.4
generates the transaction wrapper for the default method; verified by
`:app:kspDebugKotlin` + the passing repository tests.

### Tests added

- `TodayViewModelTest`
  - `one pending milestone maps to Single` — day 7 of a 20-day arc, only p25
    crossed.
  - `three pending milestones map to Summary oldest-first` — day 16, p25/p50/p75
    pending; asserts kind order, dayNumbers (5/10/15), crossing dates, and that
    `reflectionTargetId` is the latest (p75).
- `ArcRepositoryTest`
  - `acknowledging a set of ids clears exactly those, leaving the rest pending`
    — acknowledges p25+p50, asserts p75 still pending and its `acknowledged_at`
    still null.
  - `reflection lands on the latest milestone and nowhere else` — acknowledges
    all three with a reflection targeted at p75; asserts the trimmed text is on
    p75 only and every row got `acknowledged_at`.

## Part 2 — remix test through the real path

1. **Was it already going through the pre-fill path?** Yes. Task 13 had already
   rewritten it: `remix carries the whole rule set through begin into the
   created arc` constructs `CreateArcViewModel(remixFromArcId = sourceId)` —
   which calls `getRemixSource()` and pre-fills — then `begin()` → `createArc()`
   and reads the persisted arc back. Source values are all non-default
   (`STRICT / 3 / 5 / 6` vs defaults `STANDARD / 1 / 2 / 4`), so a pre-fill that
   silently fell back to defaults would fail an assertion. No rewrite needed.

2. **Did it fail with `strictness` removed from `RemixSource`?** Yes. I set
   `RemixSource.strictness = Strictness.STANDARD` in `getRemixSource()`
   (dropping the copy), ran `CreateArcViewModelTest`, and
   `remix carries the whole rule set through begin into the created arc`
   **FAILED** (persisted arc was `standard`, expected `strict`). Restored the
   copy; green again.

3. **Backfill / R16 re-validation on a pre-filled remix** — behaves correctly.
   Added `remixed backfill_days moves the R16 earliest start date and
   re-validation uses it`: a source with `backfill_days = 5` pre-fills, and
   - `earliestAllowedStartDate` becomes `today − 5` (not the default `today − 2`);
   - a start date of `today − 6` (before the wider window) is flagged by both
     `startDateError` and `step3Error`;
   - `today − 5` (edge) and `today − 4` (valid only under the wider window) are
     accepted — confirming the pre-filled source window, not the default, is in
     force.

## Test status

- `./gradlew :domain:test` — green.
- `./gradlew :app:testDebugUnitTest` — green (all new view-model, repository and
  create-flow tests included).

## Honest notes

- The `final`-in-summary row restates what the subsequent `Ended` screen says.
  Kept it rather than special-casing `final` out of the summary, per the brief
  ("Do not special-case it").
- Screens are described from the implementation and the unit-tested state
  mappings, not a captured emulator screenshot; the string content is
  deterministic from the composables. The `milestone-pending` debug scenario
  now renders a live two-row summary if a visual check is wanted.
