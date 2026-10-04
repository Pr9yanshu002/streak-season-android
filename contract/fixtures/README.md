# Fixtures

Each fixture is a JSON file describing an arc, some check-ins, a frozen
"now", and the expected output of the rules engine.

Both clients load the same files and assert against them. When Kotlin and Swift
disagree, a fixture goes red and tells you which rule (`R1`–`R18`) broke.

These files are the closest thing this project has to shared code. Treat a
change to a fixture the way you'd treat a change to a public API.

---

## Format

```jsonc
{
  "id": "streak_rest_token_continues",
  "rules": ["R6", "R11"],          // which rules this exercises
  "description": "A spent rest token keeps the streak alive.",

  "now": "2026-01-15T09:30:00",    // local, no timezone — R1 turns this into today

  "arc": {
    "id": "arc-1",
    "name": "Test Arc",
    "start_date": "2026-01-01",
    "end_date": "2026-01-30",
    "status": "active",
    "strictness": "standard",
    "rest_tokens_per_week": 1,
    "backfill_days": 2,
    "day_boundary_hour": 4
  },

  "commitments": [
    {
      "id": "c-gym",
      "name": "Gym",
      "type": "binary",
      "is_required": true,
      "schedule_type": "daily",
      "active_from": "2026-01-01"
    }
  ],

  "days": [
    { "date": "2026-01-01", "entries": { "c-gym": 1 } },
    { "date": "2026-01-02", "entries": { "c-gym": 1 } },
    { "date": "2026-01-03", "entries": {}, "rest_token_used": true },
    { "date": "2026-01-04", "entries": { "c-gym": 1 } }
  ],

  "expect": {
    "day_states": {
      "2026-01-01": "complete",
      "2026-01-02": "complete",
      "2026-01-03": "rest",
      "2026-01-04": "complete"
    },
    "current_streak": 0,
    "best_streak": 4,
    "rest_tokens_remaining": { "2026-W01": 0 },
    "completion_pct": 0.2308,
    "arc_status": "active",
    "day_number": 4,
    "total_days": 30,
    "commitment_statuses": {
      "2026-01-04": { "c-gym": "done" }
    }
  }
}
```

### Conventions

- **`now`** is a local datetime with no offset. The engine applies R1 to it.
- **Omitted day** means no row at all — nothing logged. Different from a day
  with `"entries": {}`, which means the user opened it and logged nothing.
- **`entries`** maps commitment id to raw `value`. Booleans are `1`/`0`.
- **`day_states`** only needs to list dates you're asserting on. A fixture may
  assert one date or all of them.
- **`rest_tokens_remaining`** is keyed by ISO week (`2026-W01`), because the
  allowance is per-week.
- **`completion_pct`** is compared to **4 decimal places**. Both engines must
  round the same way — round half up.
- Any key in `expect` may be omitted. Only assert what the fixture is about.
- **`note`** is an optional top-level prose field — free-text commentary, read
  by neither engine. It must sit at the top level (next to `description`), never
  inside `expect`: a key inside `expect` that isn't a real assertion field is
  how a typo like `current_streek` slips through silently, so `FixtureExpectKeysTest`
  fails on any `expect` key it doesn't recognise (the allowed set is derived
  from `ExpectDto`'s `@SerialName`s).
- **`failed_on_date`** is only meaningful alongside `arc_status`. If a fixture
  asserts `arc_status`, it must also include `failed_on_date` — `null` when
  the arc did not fail. Fixtures that don't touch failure state omit both.
- **`day_number`** and **`total_days`** are R2's day count (`elapsedDates`'s
  size, and the arc's inclusive length). `day_number` is `0` before the arc
  starts and pinned at `total_days` once it's over — see the `daynum_*`
  fixtures.
- **`commitment_statuses`** is nested two levels deep: date, then commitment
  id, then one of `not_scheduled` / `pending` / `done` (R3/R4). Every
  commitment gets a status for every date in the arc's range — there's no
  "missing key means not scheduled" shortcut, so a fixture can assert
  `not_scheduled` explicitly the same way it asserts `done` or `pending`.
  Only the dates/commitments a fixture actually cares about need to be
  listed, same as `day_states`. Example, one date with two commitments:

  ```jsonc
  "commitment_statuses": {
    "2026-01-06": {
      "c-gym": "done",
      "c-weekday-run": "not_scheduled"
    }
  }
  ```
- **`required_milestones`** (R12) maps milestone kind (`start`/`p25`/`p50`/
  `p75`/`final`) to the exact date its threshold was crossed — never the date
  the app happened to check (`effective_today`). Unlike `day_states` and
  `commitment_statuses`, this is asserted as a **whole map**, not key by key:
  a kind the engine returns that the fixture omits fails the test, same
  reasoning as `commit_status_no_missing_keys` — a partial assertion would let
  a threshold firing too early pass silently.

  ```jsonc
  "required_milestones": {
    "start": "2026-01-12",
    "p25":   "2026-01-21",
    "p50":   "2026-01-31"
  }
  ```

- **`recap`** (R19) is the output of the separate `recap()` function, not
  `resolve()`. When a fixture includes an `expect.recap` block the harness calls
  `recap()` and asserts it. Unlike most `expect` keys, the recap block is
  asserted **whole** — every scalar, `hardest_week`, and the complete
  `commitment_totals` set — so a value the author didn't think to list cannot
  pass silently (same reasoning as `required_milestones`). Within it:
  - `is_null: true` asserts `recap()` returned `null` (an arc that hasn't
    ended); all other recap fields are ignored.
  - `status`, `complete_days`, `counted_days`, `completion_pct`, `days_reached`,
    `best_streak` are scalars. `completion_pct` compares to 4 dp like the
    top-level one. `days_reached` is the arc length for a completed arc, or the
    day reached before failing for a failed one.
  - `hardest_week` is `{ week_key, week_start, week_end, complete_days,
    counted_days }` (`week_start`/`week_end` are the Monday/Sunday of that ISO
    week, which the recap card renders as a date range), or **omitted to assert
    there is no hardest week**.
  - `commitment_totals` maps commitment id to a total tagged by `kind`:
    `binary` → `days_done`; `measured` (counter/duration) → `total` +
    `days_target_met`; `log` → `first` + `last` (both null if nothing recorded)
    + `days_recorded`.

  ```jsonc
  "recap": {
    "status": "failed",
    "complete_days": 2, "counted_days": 3, "completion_pct": 0.6667,
    "days_reached": 3, "best_streak": 2,
    "hardest_week": { "week_key": "2026-W01", "complete_days": 2, "counted_days": 3 },
    "commitment_totals": { "c-gym": { "kind": "binary", "days_done": 2 } }
  }
  ```

### Naming

`<area>_<case>.json`, snake_case. Areas: `boundary`, `schedule`, `satisfy`,
`state`, `rest`, `seal`, `strict`, `streak`, `pct`, `milestone`, `edit`,
`commit_status` (R3/R4 per-commitment status), `daynum` (R2 day count),
`recap` (R19 recap statistics).

---

## The 40 cases to write

Grouped by rule. Each is one file.

### R1 — Day boundary (4)
1. `boundary_before_rollover` — 03:59 resolves to yesterday
2. `boundary_at_rollover` — 04:00 resolves to today
3. `boundary_custom_hour` — boundary set to 0, midnight behaves conventionally
4. `boundary_checkin_at_1am` — a 01:30 check-in lands on the previous date

### R2/R3 — Range and scheduling (5)
5. `schedule_daily_all_days`
6. `schedule_weekdays_mon_wed_fri`
7. `schedule_weekdays_sunday_is_free` — no required scheduled → complete
8. `schedule_active_from_midarc` — commitment added day 20, days 1–19 unaffected
9. `range_inclusive_both_ends` — Jan 1 → Jan 5 is 5 days

### R4 — Satisfaction (5)
10. `satisfy_binary`
11. `satisfy_counter_exact_target`
12. `satisfy_counter_under_target`
13. `satisfy_duration_over_target`
14. `satisfy_log_always_counts`

### R5 — Day state (5)
15. `state_complete_all_required`
16. `state_partial_some_required`
17. `state_missed_none_logged`
18. `state_pending_is_today`
19. `state_bonus_does_not_affect`

### R6 — Rest tokens (6)
20. `rest_token_converts_missed`
21. `rest_token_second_in_week_invalid`
22. `rest_token_no_carryover` — unused week-1 token unavailable in week 2
23. `rest_token_on_complete_day_ignored`
24. `rest_token_partial_first_week_full_allowance`
25. `rest_token_ignored_in_strict_mode`

### R7 — Sealing (3)
26. `seal_within_backfill_editable`
27. `seal_outside_backfill_locked`
28. `seal_zero_backfill` — only today is editable

### R9 — Strict failure (4)
29. `strict_fails_on_sealed_miss`
30. `strict_does_not_fail_unsealed_miss`
31. `strict_first_failing_date_wins`
32. `strict_complete_run_survives`

### R11 — Streaks (5)
33. `streak_basic_run`
34. `streak_broken_by_miss`
35. `streak_rest_token_continues`
36. `streak_pending_today_skipped`
37. `streak_best_differs_from_current`

### R8 — Percentage (3)
38. `pct_excludes_rest_days`
39. `pct_excludes_pending_today`
40. `pct_excludes_unscheduled_days`

### Edge cases worth adding once the above pass (5)
41. `edge_timezone_flight` — `now` shifts by 10 hours mid-arc, day count unchanged
42. `edge_arc_completes_on_end_date`
43. `edge_single_day_arc`
44. `edge_all_commitments_bonus` — no required at all
45. `edge_clock_jumps_backwards`

### `day_number` / `total_days` / `commitment_statuses` (11)

Added when `Resolution` grew these fields; ported from what were originally
seven plain Kotlin unit tests, so the cross-client contract wouldn't miss
rules the Swift port could silently get wrong.

46. `daynum_first_day_of_arc` — `day_number` is 1 on `start_date`
47. `daynum_last_day_of_arc` — `day_number` equals `total_days` on `end_date`
48. `daynum_before_arc_starts` — `day_number` is 0 before `start_date`
49. `daynum_after_arc_ends` — `day_number` pinned at `total_days` after `end_date`
50. `boundary_day_number_and_status` — R1's day-boundary hour, applied to both
    `day_number` and `commitment_statuses`, not just `effective_today`
51. `commit_status_mixed_on_one_day` — `not_scheduled` / `pending` / `done` on
    the same date
52. `commit_status_binary_done_and_pending`
53. `commit_status_counter_done_and_pending`
54. `commit_status_duration_done_and_pending`
55. `commit_status_log_done_and_pending`
56. `commit_status_no_missing_keys` — every commitment has a status on every
    date in range, including `not_scheduled`
