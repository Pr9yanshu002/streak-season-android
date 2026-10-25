# Arc — Rules Specification (v0)

This document defines every rule the two clients must agree on. It is the
authority. If code and this document disagree, the code is wrong.

Each rule has an ID (`R1`, `R2`, …). Fixtures reference these IDs so a failing
test points at a specific rule rather than a vague area.

**Locked decisions for v0:**
- Week starts **Monday** (ISO-8601).
- Rest tokens **do not carry over**. 1 per week by default, use it or lose it.
- Strict-mode failure **archives the arc**. Remix creates a new one.
- Schedules are **daily** or **specific weekdays** only. "N times per week" is v1.

---

## R1 — The current date ("today")

The app's notion of today is **not** the system date.

```
effectiveToday(now, boundaryHour):
    if now.hour < boundaryHour:
        return now.date - 1 day
    else:
        return now.date
```

`boundaryHour` comes from `arcs.day_boundary_hour`, default 4.

All times are the device's **local** time. There is no UTC anywhere in the
domain layer.

Worked example, boundary = 4:
| Local now              | effectiveToday |
|------------------------|----------------|
| 2026-01-15 03:59       | 2026-01-14     |
| 2026-01-15 04:00       | 2026-01-15     |
| 2026-01-15 23:30       | 2026-01-15     |

---

## R2 — Dates in range

A date `D` is **in range** for an arc when `start_date <= D <= end_date`,
inclusive at both ends.

`elapsedDates(arc, today)` = all dates from `start_date` to
`min(today, end_date)` inclusive. Empty if `today < start_date`.

`totalDays(arc)` = inclusive day count from `start_date` to `end_date`.
A 2026-01-01 → 2026-01-05 arc is **5** days, not 4.

`dayNumber(arc, today)` = `elapsedDates(arc, today).count`. `0` before the arc
starts, `1` on `start_date`, and pinned at `totalDays(arc)` from `end_date`
onward — it never counts past the arc's own length.

---

## R3 — Is a commitment scheduled on a date?

A commitment `C` is scheduled on date `D` when **all** of:

1. `C.active_from <= D`
2. `C.active_until` is null **or** `D <= C.active_until`
3. `C.deleted_at` is null
4. and:
   - `schedule_type = 'daily'` → always true
   - `schedule_type = 'weekdays'` → `isoWeekday(D)` is in `schedule_days`
     (Mon = 1 … Sun = 7)

Rule 1 is what makes "add a commitment on day 20" count from day 20 and never
retroactively fail the first 19 days.

---

## R4 — Is an entry satisfying?

Given commitment `C` and its entry `E` for a date:

| `C.type`   | Satisfied when                          |
|------------|-----------------------------------------|
| `binary`   | `E.value >= 1`                          |
| `counter`  | `E.value >= C.target_value`             |
| `duration` | `E.value >= C.target_value`             |
| `log`      | **always satisfied** if an entry exists |

No entry (or a soft-deleted one) is never satisfying.

`log` is a record, not a goal. Weighing yourself is the commitment; the number
is not pass/fail.

---

## R5 — Day state

Day state is **always derived**, never stored.

For arc `A` and date `D`:

```
if D > today:                       -> future
if D < A.start_date or D > A.end_date: -> out_of_range   (not rendered)

required := commitments scheduled on D (R3) where is_required = 1
satisfied := those with a satisfying entry (R4)

if required is empty:               -> complete        (see note)
if satisfied == required:           -> complete
if day.rest_token_used == 1 and rest token is valid (R6): -> rest
if D == today:                      -> pending
if satisfied is empty:              -> missed
otherwise:                          -> partial
```

Rest is checked before pending so a user who marks today as a rest day
sees it immediately, not the next morning. Complete is still checked
first, so finishing everything on a flagged day makes the day complete
and the flag is ignored.

**Note on the empty case:** a date with no required commitments scheduled — for
example a weekdays-only arc on a Sunday — resolves to `complete`. It cannot
break a streak, and it is excluded from completion percentage by R8.

Bonus commitments (`is_required = 0`) never affect day state. They only feed
the per-commitment totals in the recap.

Order matters: `complete` is checked before `pending`, so finishing everything
early in the day shows as complete immediately rather than staying pending
until midnight.

---

## R6 — Rest tokens

Rest tokens exist only when `strictness = 'standard'`.
In `strict` and `chill`, `rest_tokens_per_week` is treated as 0 and any stored
`rest_token_used` flag is ignored.

**Allowance:** `arcs.rest_tokens_per_week` per ISO week (Monday–Sunday).

**Consumption:** a token is consumed on a day that has `rest_token_used = 1`
and would otherwise resolve to `partial`, `missed` or `pending`. A flag on a
`complete` day is ignored and does not consume a token. A flag on a `future`
day is ignored and does not consume a token.

Marking today as a rest day takes the token immediately; finishing
everything that day anyway returns it. Tokens are never applied
automatically: an unflagged `missed` or `partial` day stays `missed` or
`partial` even if the week has tokens left.

**Remaining:** `allowance - consumed`, floored at 0.

**No carryover.** Each ISO week starts fresh. Unused tokens are lost.

**Validity:** a token is only valid if, at the moment of resolution, the count
of tokens already consumed *earlier in the same ISO week* is less than the
allowance. Earlier means by date ascending. So if the allowance is 1 and the
user flags both Tuesday and Thursday, Tuesday gets the token and Thursday
resolves to `partial`/`missed`.

**Partial weeks:** the first and last ISO weeks of an arc get the full
allowance, not a prorated one. A 3-day arc that starts on a Saturday still gets
1 token.

---

## R7 — Sealing

A date `D` is **sealed** when `today > D + backfill_days`.

Sealed dates cannot be edited: no new entries, no edits to existing entries, no
rest tokens. Notes and photos follow the same rule.

Today and any unsealed past date remain editable.

With the default `backfill_days = 2` and today = 2026-01-15:
- 2026-01-15, 14, 13 → editable
- 2026-01-12 and earlier → sealed

Sealing is what makes strict mode survivable — see R9.

---

## R8 — Completion percentage

```
elapsed  := elapsedDates(arc, today)              (R2)
counted  := elapsed where state not in {rest, pending, future}
           and which had at least one required commitment scheduled
complete := counted where state == complete

completionPct := complete / counted        (0.0 if counted is empty)
```

Rest days are excluded from **both** numerator and denominator — a rest day
neither helps nor hurts. Today is excluded while still `pending`. Days with no
required commitments scheduled are excluded so a weekdays-only arc isn't
inflated by free Sundays.

---

## R9 — Strict-mode failure

Only applies when `strictness = 'strict'`.

Evaluated on **sealed dates only** (R7). This is deliberate: if failure were
evaluated the moment the clock rolled over, a user with a 2-day backfill window
could never actually use it.

```
for D in elapsedDates(arc, today), ascending:
    if not sealed(D): break
    if state(D) in {partial, missed}:
        arc.status = 'failed'
        arc.failed_on_date = D
        stop
```

The first sealed failing date wins. A failed arc is terminal: no further
check-ins, and it moves to the archive. The recap still generates.

**Remix** creates a brand-new arc, pre-filled from the failed one, with
`remixed_from_arc_id` set. It does not resurrect or reset the old arc.

`standard` and `chill` arcs never fail. They finish with whatever percentage
they finish with.

---

## R10 — Arc completion

When `today > end_date` and `status = 'active'` and the arc has not failed by
R9, set `status = 'completed'` and `completed_at` to now.

The `final` milestone is created at the same moment, for both completion and
failure.

---

## R11 — Streaks

**Current streak:** walk backwards from `today`.

```
cursor := today
if state(cursor) == pending: cursor := cursor - 1 day

streak := 0
while cursor >= arc.start_date and state(cursor) in {complete, rest}:
    streak += 1
    cursor := cursor - 1 day
return streak
```

A `pending` today is skipped rather than counted or breaking the run — you
haven't failed today yet.

A flagged, unfinished today with a valid token resolves to `rest`, not
`pending` (R5), so it is counted in the current streak like any other rest
day. This is a consequence of the R5 ladder, not a separate rule.

**Best streak:** the longest run of consecutive dates in
`elapsedDates(arc, today)` whose state is in `{complete, rest}`, ignoring a
`pending` today entirely.

Rest days **continue** a streak but the day itself still counts toward the
length. A token is a pass, not an erasure.

---

## R12 — Milestones

Thresholds are on **elapsed time**, not completion:
`progress := elapsedDates.count / totalDays`

| kind    | Created when                                      |
|---------|---------------------------------------------------|
| `start` | at arc creation                                   |
| `p25`   | first resolution where `progress >= 0.25`         |
| `p50`   | first resolution where `progress >= 0.50`         |
| `p75`   | first resolution where `progress >= 0.75`         |
| `final` | when the arc completes (R10) or fails (R9)        |

`triggered_on_date` is the date the threshold was crossed, not the date the app
observed it. For `start` it is `start_date`; for `p25`/`p50`/`p75` the first
date whose `dayNumber` meets the threshold; for `final` the arc's `end_date`
when completed, or `failed_on_date` when failed.

The engine computes these dates exactly from `start_date` and `totalDays`. It
does not stamp `effectiveToday`, which records when the app happened to notice
rather than when the user reached the point.

Several thresholds crossed in one resolution therefore carry different dates —
their own crossing dates, not a shared observation date.

`acknowledged_at` stays null until the user dismisses the full-screen card.

If several thresholds are crossed at once — a short arc, or an app not opened
for a week — create all of them and present them in order.

---

## R13 — Editing a live arc

- **Adding a commitment:** `active_from = today`. Past days are untouched.
- **Removing a commitment:** `active_until = today - 1 day`. Never soft-delete
  a commitment that has entries; that would silently rewrite history.
- **Editing a target** (3L → 4L): edits in place and **does** change how past
  days resolve. Documented and accepted — v0 does not version targets.
- **Editing dates:** allowed while `status = 'draft'` only. Once active, the
  end date is fixed.
- **Deleting an arc:** soft delete. Recoverable for 30 days, then a background
  job hard-deletes it. It is the only place a hard delete is permitted, and it
  runs after the row has synced its tombstone.

---

## R14 — Clock manipulation

Do not fight it. If the system date jumps backwards by more than 24 hours,
resolve normally and award nothing for the skipped span. This is a personal
accountability app, not a competition, and anti-cheat measures cost more than
they are worth.

---

## R15 — One active arc

At most one arc may have `status = 'active'` at a time.

Creating a new arc while an active arc exists is blocked. The user must
complete, fail, or archive the current arc first.

`draft` is not used in v0 — the creation flow holds state in memory and writes
the arc only when the user confirms it.

---

## R16 — Valid start date

`start_date` may be in the past, but not before `effectiveToday - backfill_days`.

A start date earlier than that produces sealed missed days (R7) at the moment of
creation, which under strict mode fails the arc immediately (R9). The rule is
R7 stated from the other direction: an arc may only start on a day the user
could still check in for.

`start_date` may be in the future. Before the arc starts, `dayNumber` is 0 (R2).

"Today" here is `effectiveToday(now, day_boundary_hour)` (R1), using the
boundary hour **the new arc will have**, not a fixed 4. Changing the boundary
while creating the arc can move today, and so the earliest start, by one day.

Worked example, boundary = 4, `backfill_days` = 2:
| Local now          | effectiveToday | Earliest start |
|--------------------|----------------|----------------|
| 2026-01-15 02:30   | 2026-01-14     | 2026-01-12     |

---

## R17 — Arc name

An arc's name must contain at least one non-whitespace character.

---

## R18 — At least one required commitment

An arc must have at least one commitment with `is_required = 1`.

An arc where every commitment is a bonus computes 0% completion for its entire
life under R8, because no day has a required commitment scheduled and the
counted set is always empty. That is correct by the rules and meaningless on
screen, so it is blocked at creation rather than rendered.

---

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

---

## R20 — A valid commitment

A commitment can be created when **all** of:

1. `name` contains at least one non-whitespace character
2. for `counter` and `duration`: `target_value` > 0 **and** `unit` is
   non-blank
3. for `binary` and `log`: `target_value` is null
4. for `schedule_type = 'weekdays'`: `schedule_days` holds at least one day,
   and each day is in 1–7 (Mon = 1 … Sun = 7)

A counter or duration with no target can never be satisfied (R4), and a
weekdays commitment with no days is never scheduled (R3).

---

## R21 — Creation limits

- `rest_tokens_per_week` is 0–7. A week has seven days, so more could never be
  used (R6). Strict and chill arcs store 0: R6 already treats their allowance
  as 0, and the stored value says the same.
- An arc has at most **10** commitments when it is created.


## Reference: state machine

```
draft ──begin──> active ──┬── today > end_date ────> completed ──> archived
                          │
                          └── sealed miss (strict) ─> failed ────> archived
```

`archived` is a user action from either terminal state, or automatic 30 days
after completion.
