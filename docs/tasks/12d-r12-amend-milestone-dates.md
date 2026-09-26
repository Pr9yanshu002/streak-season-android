# Task: amend R12 and restore `requiredMilestones` as a dated map

## Context

`Resolution.requiredMilestones` was changed from
`Map<MilestoneKind, LocalDate>` to `Set<MilestoneKind>`, on the grounds that
R12 says `triggered_on_date` is "the `today` at which it was created."

The reading of R12 was correct. **R12 itself is wrong**, and this task fixes the
rule first, then restores the map.

`rules.md` is authoritative, so leaving the rule unamended means any future
review will keep pulling the code back to `Set`. The rule changes first.

## Why R12 is wrong

R12 stamps the date the **app observed** a threshold, not the date the user
**crossed** it. Those differ whenever the app is not opened daily, which is most
of the time over a 40- to 90-day arc.

Worked example. 40-day arc starting 2026-01-12. Day 20 is 50%, which falls on
2026-01-31. The user does not open the app until 2026-02-05.

- Current behaviour: `p50` is recorded as 2026-02-05.
- Reality: the halfway point was reached on 2026-01-31.

The recap is built on these dates — the milestone sequence and the photo
timeline both order by them. Recording observation dates makes both wrong for
any user who skips days.

The crossing date is not a reconstruction or an estimate. It is exact
arithmetic:

```
crossingDay  = ceil(totalDays × numerator / denominator)
crossingDate = start_date + (crossingDay − 1) days
```

Day 20 of a 40-day arc starting 2026-01-12 is 2026-01-31. Always, regardless of
when anyone looks. `effectiveToday` is the approximate value here, not the
computed one.

The multiple-threshold case argues the same way. Opening the app after a week
and crossing p25, p50 and p75 at once should produce three distinct dates —
2026-01-21, 2026-01-31, 2026-02-10 — because that is when each was reached.
Stamping all three with today records them as though a quarter, a half and three
quarters of the arc were reached on the same day.

## Part 1 — amend R12

Replace the `triggered_on_date` sentence in R12 with:

```markdown
`triggered_on_date` is the date the threshold was crossed, not the date the app
observed it. For `start` it is `start_date`; for `p25`/`p50`/`p75` the first
date whose `dayNumber` meets the threshold; for `final` the arc's `end_date`
when completed, or `failed_on_date` when failed.

The engine computes these dates exactly from `start_date` and `totalDays`. It
does not stamp `effectiveToday`, which records when the app happened to notice
rather than when the user reached the point.

Several thresholds crossed in one resolution therefore carry different dates —
their own crossing dates, not a shared observation date.
```

Leave the threshold table unchanged.

## Part 2 — restore the map

```kotlin
val requiredMilestones: Map<MilestoneKind, LocalDate>
```

- Key: the kind.
- Value: the crossing date per the amended R12.
- Contents: every kind required as of now, not only newly-required ones. The
  engine cannot know what is stored; `:app` diffs against the database.
- Order: `LinkedHashMap` built in canonical order
  (`START, P25, P50, P75, FINAL`), so iteration order is presentation order.

Use **integer arithmetic** for the thresholds:

```kotlin
private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
```

`crossingDay = ceilDiv(totalDays * numerator, denominator)`, with p25 = 1/4,
p50 = 1/2, p75 = 3/4. Do not use `Double`. On a 75-day arc, p50 crosses at day
38 (38/75 = 0.5067) and not day 39, and a float comparison at a `>=` boundary is
precisely where that lands on the wrong side.

Verify against these, derived by hand:

| totalDays | p25 | p50 | p75 |
|---|---|---|---|
| 40 | day 10 | day 20 | day 30 |
| 75 | day 19 | day 38 | day 57 |
| 1 | day 1 | day 1 | day 1 |

`FINAL` is `arc.endDate` when the derived status is `completed`, and
`failedOnDate` when `failed`. Absent otherwise.

`START` is `arc.startDate`, present as soon as the arc exists — including before
it has started.

## Part 3 — `:app`

`triggered_on_date` comes from the map's value. **Do not substitute
`effectiveToday`.**

The milestone upsert diffs `resolution.requiredMilestones` against the kinds
already stored and inserts the missing ones, using the engine's date.

## Part 4 — fixtures

Restore or update the `required_milestones` block in
`contract/fixtures/README.md` and `Expect.kt`:

```json
"required_milestones": {
  "start": "2026-01-12",
  "p25":   "2026-01-21",
  "p50":   "2026-01-31"
}
```

Assert the **whole map**, not key by key. A kind the engine returns that the
fixture does not list must fail — a partial assertion would let a threshold
firing too early pass silently. Same reasoning as
`commit_status_no_missing_keys`.

Add a fixture specifically for the divergence this task exists to prevent: an
arc where `effectiveToday` is several days after a threshold's crossing date,
asserting the crossing date and not today. Name it so its purpose is obvious,
e.g. `milestone_date_is_crossing_not_observation`.

## Constraints

- Amend R12 before changing code. The rule is authoritative; code that
  disagrees with it will be reverted by the next review.
- Do not stamp `effectiveToday` as `triggered_on_date` anywhere.
- No `Double` in threshold arithmetic.
- Do not change the R12 threshold table or any other rule.

## Definition of done

- R12 amended in `rules.md`.
- `requiredMilestones` is `Map<MilestoneKind, LocalDate>` again.
- Fixtures updated, including the crossing-vs-observation case.
- `./gradlew :domain:test` green.
- `:app` writes the engine's date, verifiable by inspecting a `milestones` row
  after reconciling an arc whose threshold was crossed before today.

## Report back

- Confirmation that R12 is amended and the code now matches it.
- The crossing days computed for `totalDays` of 40, 75 and 1, checked against
  the table above.
- The fixture added for the crossing-vs-observation case, and its expected
  values.
- Anywhere else in the codebase that stamps `effectiveToday` onto something the
  engine could compute exactly. This is the second time this substitution has
  been made, so it is worth checking whether it has happened elsewhere.