# Task 13 — Report (Part 1 re-run)

Scope of this session: **Part 1 verification only**, re-run because the previous
session's report was lost. No production code was changed. Part 2 is already
done and was not touched. The one extra item requested — `SELECT
sqlite_version()` on the emulator — is recorded under "Environment" below.

Verification method: code reading, the `ArcRepositoryTest` suite (Robolectric,
`:app:testDebugUnitTest`), and one headless emulator (`Pixel_8`, API 36 /
Android 16) for the SQLite query. The guard-removal experiment for 1d edited
two production files temporarily and restored them exactly — `git diff` on both
files is empty afterwards.

---

## Part 1, item by item

### 1a. Remix — **partly works**

Verified against the database via
`ArcRepositoryTest."remix creates a new arc with fresh commitment ids and
remixed_from set, copying nothing else"`, plus the UI/VM path.

| Requirement | Result |
|---|---|
| Failed arc's ended screen offers remix | **works** — `TodayScreen.Ended` shows the "Remix this arc" button only when `!completed` (`TodayScreen.kt:149`). |
| Create flow pre-filled with name | **works** — `CreateArcViewModel` reads `remixFromArcId` → `getRemixSource()` → prefills `name`. |
| Pre-filled with commitments (incl. per-commitment schedule / required flags) | **works** — commitments copied through `RemixSource.commitments` → `toDraft()`. |
| Pre-filled with **rules** (arc-level rule set) | **does NOT work** — see gap below. |
| Fresh dates subject to R16 | **works** — dates reset to `today..today+59`; intention left blank. |
| `remixed_from_arc_id` set to source | **works** — asserted in DB: `remixArc.remixedFromArcId == sourceId`. |
| Every copied commitment gets a **new UUID** | **works** — `createArc()` mints `UUID.randomUUID()` per commitment; asserted in DB: `assertNotEquals(sourceCommitmentId, remixCommitment.id)`. |
| Entries, days, milestones, intention **not** copied | **works** — asserted in DB: remix has empty days and empty milestones; intention isn't carried in `RemixSource` at all. |
| Source arc untouched | **works** — asserted in DB: source still `failed`, still has its own day + milestone rows. |

**Gap (why "partly works"):** the arc-level **rule set is not pre-filled on
remix.** `RemixSource` carries only `name` and `commitments`
(`ArcRepository.kt:88`), so `strictness`, `rest_tokens_per_week`,
`backfill_days`, and `day_boundary_hour` reset to the create flow's defaults
(`STANDARD`, `1`, `2`, `4`) rather than inheriting the source arc's values. The
code comment on `RemixSource` and the 1a brief both say "name, commitments **and
rules**", so this is a real divergence from the brief, not a deliberate
exclusion (intention and dates *are* deliberately excluded and documented as
such). The remix test doesn't catch it because it passes `strictness = STRICT`
to `createArc()` explicitly instead of relying on the pre-fill. Per the task
rules I'm reporting this, not fixing it — Priyanshu should decide whether remix
is meant to restore the source's rule set.

### 1b. Milestone card — **works**

Verified by code + the ordering/exclusion assertions in the 1c test.
Persistence of acknowledgment and reflection is DB-backed, so the
kill-and-relaunch behaviour follows from the schema rather than from a manual
device cycle.

- A card appears when an unacknowledged milestone exists — `TodayScreen` renders
  `MilestoneCard` whenever `pendingMilestone != null`, fed by
  `observePendingMilestones` (`TodayScreen.kt:103`).
- Dismissing sets `acknowledged_at` — `acknowledgeMilestone()` →
  `MilestoneDao.acknowledge` writes `acknowledged_at = :now`
  (`MilestoneDao.kt:48`).
- A dismissed milestone does not reappear after relaunch —
  `observePendingByArc` filters `acknowledged_at IS NULL`, and `acknowledged_at`
  is a persisted column, so a relaunch simply re-queries and the row stays
  filtered out.
- Several pending present oldest-first — query `ORDER BY triggered_on_date ASC,
  created_at ASC` and the VM surfaces them one at a time via `firstOrNull()`.
  Asserted: `[P25, P50, P75]`.
- `start` never appears as a card — query clause `kind != 'start'`.
- Reflection saved and survives relaunch — the card's optional reflection is
  passed on "Continue" and written to the `reflection` column alongside
  `acknowledged_at`; both are persisted.

### 1c. The two-weeks-closed case — **works**

Verified by `ArcRepositoryTest."milestones crossed while the app was closed are
dated at their crossing day, not the open date"`. Setup: a 20-day CHILL arc
(`2026-09-01 … 2026-09-20`) opened for the first time on day 16 (`2026-09-16`),
so `p25`, `p50`, and `p75` were all crossed while the app was closed. `final` is
not required yet — the arc is still active on day 16.

**What the user sees:** three full-screen cards, one at a time, oldest first:

1. "A quarter of the way there" — Day 5 of 20
2. "Halfway there" — Day 10 of 20
3. "Three quarters there" — Day 15 of 20

`start` is excluded, so it is not one of the cards. Each "Day N of 20" is
computed from the engine's crossing date, not the open date.

**Crossing dates (the point of amended R12):** asserted exactly in the DB —
`start → 2026-09-01`, `p25 → 2026-09-05`, `p50 → 2026-09-10`, `p75 →
2026-09-15`. None is stamped `2026-09-16` (the open date). Confirmed.

**Honest opinion on the multi-card sequence:** it's acceptable but leans
tedious, and I wouldn't call it good. Three (and in the worst case four —
`p25/p50/p75/final`) full-screen takeovers, each demanding a "Continue" tap and
an implicit reflect-or-skip decision, stand between the user and today's
check-in after any lapse. For a one-off catch-up it's defensible; the dates are
honest and the order is sane. But a modal wall is a poor fit for "I was away for
a while" — the natural reaction is to tap through all of them without reflecting,
which defeats the feature. A single "you crossed 3 milestones while you were
away" summary, or a dismissible stack that doesn't block the check-in, would
serve the user better. Not broken, but worth revisiting.

### 1d. Reconcile idempotency — **works (both guards verified)**

The test `ArcRepositoryTest."reconcile is idempotent — a second call does not
rewrite status or duplicate milestones"` exists and passes as shipped. I removed
each guard in turn, re-ran, confirmed the failure, and restored it:

| Guard removed | Result |
|---|---|
| `AND status != :status` in `ArcDao.updateStatusIfChanged` | test **FAILED** — the second `reconcile()` re-stamped `completed_at` with the advanced clock, so `arcAfterFirst.completedAt != arcAfterSecond.completedAt`. |
| the `if (wireKind !in existingKinds)` diff in `ArcRepository.reconcile` | test **FAILED** — the second `reconcile()` re-upserted every milestone with a new `updated_at`, so the `{id → updatedAt}` sets diverged. |

With both guards present the test passes. `git diff` on `ArcDao.kt` and
`ArcRepository.kt` is empty — the temporary edits were reverted exactly.

---

## Environment / SQLite

- **Emulator:** `Pixel_8` AVD, API 36 (Android 16), booted headless.
- **`SELECT sqlite_version()` on the emulator:** **`3.44.3`**.

The device has no `sqlite3` CLI binary (`/system/bin/sh: sqlite3: inaccessible
or not found`), so the version was read through the framework
`android.database.sqlite` API — the same SQLite build Room uses at runtime — via
a throwaway instrumented test that was deleted after the run (no code left
behind).

`3.44.3` is far above the `3.24` floor that `INSERT … ON CONFLICT … DO UPDATE`
requires, so every write path (entries, days, milestones) is safe on this image.

**`minSdk`:** currently **29** (`app/build.gradle.kts:21`). It was **26**
originally and bumped to 29 as part of the already-completed Part 2a (commit
`cb14b8e`); API 29 ships SQLite ≥ 3.28, which clears the 3.24 requirement on the
lowest supported device too. (Part 2 was out of scope for this session and not
otherwise touched.)

---

## Test status

- `:domain:test` — green (up to date; 62-fixture harness + validation).
- `:app:testDebugUnitTest` — green, including all four `ArcRepositoryTest` cases
  (round-trip, idempotency, two-weeks-closed, remix).

## Summary

| Item | Verdict |
|---|---|
| 1a Remix | partly works — arc-level rule set not pre-filled |
| 1b Milestone card | works |
| 1c Two-weeks-closed | works; multi-card sequence is tolerable but not good |
| 1d Reconcile idempotency | works — both guards independently verified |

One issue to decide on: **remix does not carry the source arc's rule set**
(strictness / rest tokens / backfill / day-boundary). Everything else in Part 1
behaves as specified.

---

## Follow-up — 1a gap fixed

After the verification above, the 1a gap was closed (this is a code change, no
longer verification-only):

- `RemixSource` now carries `strictness`, `restTokensPerWeek`, `backfillDays`
  and `dayBoundaryHour`, and `getRemixSource()` reads them off the source arc.
- `CreateArcViewModel`'s remix pre-fill seeds all four into the create state.
  They remain editable on step 3 — the existing `RulesAndIntentionStep`
  controls are wired to the same setters, so pre-fill only seeds them.
- `CreateArcViewModelTest` gains two tests on the real user path (open remix →
  pre-fill → `begin()` → read the persisted arc): one asserts the whole rule
  set survives into the created arc using all-non-default source values (so it
  fails if any field stops being copied), the other asserts the fields stay
  editable after pre-fill. Both were confirmed to fail when the pre-fill copy is
  removed, then pass once restored.

`:domain:test` and `:app:testDebugUnitTest` remain green.
