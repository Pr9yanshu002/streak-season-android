# Task 17 — Rest day: report

Branch `task-17-rest-day`. Covers Parts 1–3. The PR is not open yet, because Priyanshu is
testing on the emulator first.

Who wrote what:
- **Priyanshu** hand-wrote two fixtures (`rest_today_reserves_token`,
  `rest_today_second_flag_in_week_invalid`) and wrote the Part 2 engine change in
  `applyRestTokens`.
- **Claude** wrote everything else listed here.

---

## Part 1 — contract and fixtures

### R5 / R6 / R11 diff

- **R5:** the rest check moves above pending. The ladder is now: future, out of range,
  no required commitment → complete, all required satisfied → complete, **rest**, **pending**,
  missed, partial. The two complete lines stay separate. The "why" sentence sits directly
  under the ladder.
- **R6:** the consumption rule is replaced. A flagged day that would otherwise be `partial`,
  `missed` or **`pending`** consumes a token. A flag on a `complete` or `future` day is ignored
  and consumes nothing. Added after it: marking today as rest takes the token immediately, and
  finishing the day anyway returns it. Tokens are never applied automatically. Allowance,
  validity (the earliest flagged date in the ISO week wins), no carryover and partial weeks are
  unchanged.
- **R11:** a note that a flagged, unfinished today with a valid token is `rest`, so it counts
  toward the current streak. This follows from R5 and is not a rule change.

### Existing fixtures that conflict

None. Ten existing fixtures set `rest_token_used`, and every flag is on a date before that
fixture's effective today. On past dates the R5 swap changes nothing, and the new
pending/future consumption clauses never apply.

### New fixtures

All are in ISO week 2026-W03, with a daily Gym commitment and 1 token per week.

| Fixture | Setup | Expected |
|---|---|---|
| `rest_today_finished_releases_token` | 12, 13 done; 14 (today) done + flagged | 14 `complete`, W03 left 1, streak 3 |
| `rest_future_flag_ignored` | 12, 13 done; 16 flagged; today 14 | 14 `pending`, 16 `future`, W03 left 1 |
| `rest_today_counts_toward_streak` | 12, 13 done; 14 (today) flagged, nothing done | 14 `rest`, current 3, best 3, W03 left 0 |
| `rest_today_ignored_in_strict` | same, strict | 14 `pending`, current 2, W03 left 0 |
| `rest_today_reserves_token` *(Priyanshu)* | 12 (today, day 1) flagged, nothing done | 12 `rest`, current 1, W03 left 0 |
| `rest_today_second_flag_in_week_invalid` *(Priyanshu)* | 14 flagged + missed; 15 (today) flagged | 14 `rest`, 15 `pending`, W03 left 0 |

In review, `rest_today_reserves_token` originally expected `current_streak` 0. Under the
amended R5, today is `rest`, not `pending`, so R11 counts it and the value is 1. That has been
corrected.

### Red/green before the engine change

On the old engine, two fixtures were red. Both expected `REST` for today and got `PENDING`:
`rest_today_counts_toward_streak` and `rest_today_reserves_token`. The other four passed
already and act as regression guards. They pin down that a finished day stays complete, a future
flag is ignored, strict ignores flags, and the earliest flag in a week wins.

The temporary "skip fixtures with a TODO key" logic in `FixtureLoader` is gone, along with the
`showStandardStreams` line that existed only to print the skipped file names. Both placeholder
fixtures are real now.

---

## Part 2 — engine (Priyanshu)

- **`applyRestTokens`:** a provisional `PENDING` day is now token-eligible, alongside
  `PARTIAL`/`MISSED`. `wouldOtherwiseFail` is renamed `canUseToken`.
- **Visibility:** `isoWeekKey` is now public so `:app` can look up this week's entry in
  `restTokensRemaining`. `applyRestTokens` stays `internal`. The uncommitted edit had
  accidentally made `applyRestTokens` public and left `isoWeekKey` internal, and Claude swapped
  them at Priyanshu's request.
- **Still open:** the KDoc above `applyRestTokens` still says a token is spent only on a day that
  "would otherwise resolve to `partial` or `missed`". This has not been changed, since it's
  Priyanshu's code.

`:domain:test` is 161 / 161 green (76 fixtures + 76 expect-key checks + 9 validation tests).

---

## Part 3 — repository and Today screen

### `setRestTokenUsed`: what changed

The function itself needed no change. It already:
- goes through `ensureDay()`, the same path `recordEntry` uses, so marking a date with no day
  row creates one;
- bumps `updated_at` (`day.copy(restTokenUsed = …, updatedAt = now)`);
- unmarks by writing `rest_token_used = 0` on the same row, deleting nothing.

The days upsert under it did have a real gap, which is now fixed. `DayDao.upsert` was
`ON CONFLICT(arc_id, date) DO NOTHING`. That breaks the CLAUDE.md hard rule that a soft-deleted
row in a unique index must be revived with `… DO UPDATE SET deleted_at = NULL`. If a day row
were ever soft-deleted, the insert would do nothing, `ensureDay`'s follow-up SELECT (which
filters on `deleted_at IS NULL`) would find no row, and `requireNotNull` would crash. That would
affect `setRestTokenUsed`, `recordEntry` and `setNote` alike. Nothing in the app soft-deletes a
day today, so this was latent.

It is now
`ON CONFLICT(arc_id, date) DO UPDATE SET deleted_at = NULL, updated_at = excluded.updated_at
WHERE days.deleted_at IS NOT NULL`. A dead row is revived with a bumped `updated_at`, and a
live row is still left untouched, exactly as before.

### New repository tests (`ArcRepositoryTest`)

- **`rest day mark, unmark, mark again reuses one row and bumps updated_at`:**
  - Marking with no day row creates one, with `created_at = updated_at = now`, and the engine
    resolves today as `REST`.
  - Unmarking keeps the same id, sets `rest_token_used = 0`, leaves `deleted_at` null, bumps
    `updated_at`, keeps one row in total, and today resolves as `PENDING`.
  - Marking again keeps the same id and `created_at`, bumps `updated_at`, keeps one row, and
    today resolves as `REST`.
  - Row counts use raw SQL that includes soft-deleted rows, so "nothing deleted" is actually
    proven.
- **`marking a rest day on a soft-deleted day row revives that row`:** soft-deletes the row with
  raw SQL, then marks again. The same id comes back live. To confirm the test catches the bug, it
  was run against the old `DO NOTHING` DAO: it fails there with `IllegalArgumentException` from
  `ensureDay`, and passes with the fix.

### Today screen

- **`TodayUiState.Loaded` new fields:** `strictness` (from the arc) and `restTokensLeftThisWeek`
  (`resolution.restTokensRemaining[isoWeekKey(today)] ?: 0`). The ViewModel only looks the value
  up and never counts tokens or works out week boundaries; the week key is the engine's own
  function.
- **`Loaded.restDayRow`:** returns `OFFER`, `RESTING` or `null`, encoding the three show
  conditions: standard arc, today not `COMPLETE`, and today is `REST` or the week has more than 0
  tokens left.
- **`TodayViewModel.setRestDay(resting)`:** calls `repository.setRestTokenUsed(arcId, today,
  resting)`. The new state comes back through `observeToday`.
- **`RestDayCard`:** a stock Material 3 `Card` placed between the header and the commitments,
  using the exact copy from the brief for both forms. The commitments below stay usable. There
  is no animation and no new component. Added a `RestDayRestingPreview`; the existing Today
  preview shows the offer form.

New `TodayViewModelTest` cases:
- **Offer, then rest, then not resting:** a standard arc with nothing done shows `OFFER` with 1
  left. `setRestDay(true)` gives today `REST`, `RESTING` and 0 left. `setRestDay(false)` gives
  `PENDING`, `OFFER` and 1 left.
- **Today complete:** no row.
- **Strict arc:** no row. The arc starts today, because one that started Monday would have
  failed under R9 by Thursday from its sealed missed Monday, and would show Ended.
- **An earlier flagged, missed day this week already used the token:** today is `PENDING` with 0
  left, and there is no row.

### R7: can `recordEntry` or `setRestTokenUsed` write to a sealed date?

Report only, no fix.

- **At the repository level: yes.** Neither `recordEntry`, `setRestTokenUsed`, `undoEntry` nor
  `setNote` checks sealing. They accept any date. Today the only guard is that their UI callers
  pass "today". `DebugSeeder` relies on this to write past and sealed dates on purpose.
- **Through the UI, in normal use: no.** `TodayViewModel` only ever passes `Loaded.today`, the
  effective today. Today is never sealed, since `today > today + backfill_days` is false for any
  `backfill_days >= 0`.
- **Edge case where the UI can do it:** `Loaded.today` comes from the last state the engine
  emitted, and nothing re-emits when the day boundary passes while the screen is open. A tap
  after the boundary writes to *yesterday*. Yesterday is sealed only when `backfill_days = 0`.
  The create flow allows 0 (`coerceAtLeast(0)`). So with a backfill of 0, leaving Today open
  across the boundary and then tapping a commitment or "Rest today" writes to a sealed date. With
  any backfill of 1 or more, the write lands on an editable yesterday, which is still arguably
  the wrong day. This existed before this task; the rest-day button simply joins it.

### Behaviour worth knowing for the emulator pass

- **Flag left on a finished day:** if today is flagged and then everything gets finished, today
  is `complete` and the row disappears. The flag is still stored (R6 ignores it on a complete
  day). Undoing an entry brings today back to `rest`, with the "Today is a rest day" row. That
  matches the amended rules but may feel surprising.

## Test results

- `./gradlew :domain:test`: 161 / 161 pass.
- `./gradlew :app:testDebugUnitTest`: 42 / 42 pass.
