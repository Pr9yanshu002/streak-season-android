# Task 17 — Rest day, Part 1 report

Part 1 only: contract amendments, fixtures, harness skip. No engine change. Part 2 not started.

## R5 / R6 / R11 diff

- **R5:** the rest check now comes before pending. The full ladder is: future, then out of
  range, then empty-required → complete, then all-satisfied → complete, then **rest**, then
  **pending**, then missed, then partial. The two complete lines stay separate. The "why"
  sentence is added directly under the ladder.
- **R6:** the consumption rule is replaced. A flagged day that would otherwise be `partial`,
  `missed` or **`pending`** consumes a token. Flags on `complete` or `future` days are ignored
  and don't consume one. Added after it: marking today as rest takes the token immediately and
  finishing the day returns it, and tokens are never applied automatically. Allowance,
  validity (earliest date wins), no carryover and partial weeks are unchanged.
- **R11:** added a note that a flagged, unfinished today with a valid token is `rest`, so it
  counts toward the current streak. This follows from R5 and is not a rule change.

## Existing fixtures that conflict

None. Ten existing fixtures set `rest_token_used`, and every flag is on a date before that
fixture's effective today. On past dates the R5 swap changes nothing, because the pending line
only matches today. The new pending/future consumption clauses also change nothing, because no
existing flag is on today or a future date.

## New fixtures

All four are in ISO week 2026-W03 (Mon 01-12 to Sun 01-18), with `now` on Wed 01-14, a daily
Gym commitment and 1 token per week.

| Fixture | Setup | Expected (derived by hand) |
|---|---|---|
| `rest_today_finished_releases_token` | 12, 13 done; 14 done + flagged | 14 `complete`, W03 remaining 1, current streak 3 |
| `rest_future_flag_ignored` | 12, 13 done; 16 flagged; now 10:00 | 14 `pending`, 16 `future`, W03 remaining 1 |
| `rest_today_counts_toward_streak` | 12, 13 done; 14 flagged, nothing done | 14 `rest`, current 3, best 3, W03 remaining 0 |
| `rest_today_ignored_in_strict` | same, strict | 14 `pending`, current 2, W03 remaining 0 |

There are two placeholders, `rest_today_reserves_token.json` and
`rest_today_second_flag_in_week_invalid.json`, each containing only `{"TODO": ...}`.

## Harness

`FixtureLoader` skips any fixture with a top-level `"TODO"` key and prints its file name.
`:domain:test` now sets `testLogging.showStandardStreams = true` so that output shows up in the
Gradle console. Before this change nothing in `:domain` tests printed anything.
`FixtureExpectKeysTest` needed no change, because a placeholder has no `expect` block and passes
trivially.

## `./gradlew :domain:test`

159 tests, 1 failure. Both placeholders were reported as skipped. All existing fixtures pass.

- 🔴 `rest_today_counts_toward_streak`: `day_states[2026-01-14]` expected `REST`, got
  `PENDING`. The engine still checks pending before rest, and `applyRestTokens` only spends a
  token on a provisional `partial`/`missed`.
- 🟢 `rest_today_finished_releases_token`: passes on the current engine, which already checks
  complete before rest.
- 🟢 `rest_future_flag_ignored`: passes on the current engine, which already resolves future
  first and never spends a token on it.
- 🟢 `rest_today_ignored_in_strict`: passes on the current engine, where strict has an
  allowance of 0 and today is pending.

Three of the four new fixtures are regression guards rather than red tests. They pin behaviour
that the Part 2 engine change must not break.

## Follow-up: placeholders written

Priyanshu wrote both placeholder fixtures. Review changed one value: `rest_today_reserves_token`
expected `current_streak` 0, but today resolves to `rest`, not `pending`, so R11 counts it and
the value is 1. That has been corrected.

`./gradlew :domain:test` now runs 161 tests with 2 failures and nothing skipped:
- 🔴 `rest_today_counts_toward_streak`: today expected `REST`, got `PENDING`.
- 🔴 `rest_today_reserves_token`: today expected `REST`, got `PENDING`. Same cause as above.
- 🟢 `rest_today_second_flag_in_week_invalid`: passes on the current engine. It guards that
  today's flag is refused when an earlier day that week already used the token.
