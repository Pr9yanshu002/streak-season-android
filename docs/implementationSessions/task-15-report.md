# Task 15 — the recap — report

Both parts done and green. Part 1 (R19 + `recap()` + fixtures) is in `:domain`;
Part 2 (the recap screen) is in `:app`. `./gradlew :domain:test` (70 fixtures)
and `:app:testDebugUnitTest` both pass.

---

## Part 1 — recap statistics (R19)

### What was built

- **R19 in `rules.md`** (verbatim from the brief), between R18 and the state-machine
  reference.
- **`recap(arc, commitments, days, resolution): Recap?`** — a separate pure function
  in `domain/rules/recap/Recap.kt` (its own package; it was briefly in
  `RulesEngine.kt`, then moved out when that file got large). Returns null unless
  the arc is `completed`/`failed`.
- **`countedDates` extracted from `completionPct` (R8)** so the headline and the
  completion percentage are computed from one definition, never two — behaviour-
  preserving, so `resolve()` and all 62 original fixtures are unchanged.
- The "restricted to the recap period" requirement is met by passing the period's
  last day (`end_date`, or `failed_on_date`) as the cutoff to the existing R8/R11
  helpers; days logged after a failure are never produced, so they are never
  counted.

### Ambiguities in R19 — raised before implementing, confirmed by Priyanshu

Three readings were confirmed up front rather than chosen silently:

1. **Hardest week's "counted days" = R8's counted set** (same rest/pending/future +
   required-scheduled exclusions), not all in-range calendar days.
2. **Counter/duration sum is over scheduled+active days only** — a value logged on a
   day the commitment wasn't scheduled does not count.
3. **"scheduled and active" = `isScheduled`**, which already encodes
   `activeFrom`/`activeUntil`, so a commitment removed mid-arc is counted only over
   its active days.

One thing that turned out to be a non-issue: **"soft-deleted entries never count"**
is unexpressible as a `:domain` fixture — deleted entries are filtered by the `:app`
mapper before the engine ever sees them, so there is nothing for the engine (or a
fixture) to exclude.

### Fixtures added (8), all hand-derived from R19

| Fixture | Asserts |
|---|---|
| `recap_completed_headline_matches_pct` | rest + weekend-unscheduled days excluded; 5/6 = 0.8333; headline == pct |
| `recap_failed_ignores_post_failure` | fails Jan 3; Jan 4–7 logging ignored; 2/3; "made it to day 3" |
| `recap_hardest_week_excludes_short_week` | 0/2 edge week excluded; hardest = W03 (1/3), not W02 (6/7) |
| `recap_hardest_week_tie_earliest_wins` | W02 2/4 vs W03 3/6 — both 0.5, earliest wins |
| `recap_hardest_week_none_qualifies` | two 2-day weeks, no week ≥3 counted → null |
| `recap_commitment_totals_all_types` | binary / counter / duration / log totals |
| `recap_commitment_removed_midarc` | journal active Jan 5–9, logged through Jan 11 → `days_done` 5 |
| `recap_active_returns_null` | active arc → `recap()` null |

**Hardest-week tie, hand-derived** (`recap_hardest_week_tie_earliest_wins`): arc
Jan 8–20. W02 = Jan 8–11, 2 complete of **4** counted = 0.5. W03 = Jan 12–18 with
Jan 18 a rest day (excluded), so 3 complete of **6** counted = 0.5. The comparison
is integer cross-multiplication, not float division (Swift parity): `3·4 < 2·6`
→ `12 < 12` is false, so W03 does not displace W02 — the earliest qualifying week
stays. Different denominators on purpose, so it exercises the comparison rather than
an identical-fraction shortcut.

### Did the key check cover nested `recap` keys? No — it was extended.

`FixtureExpectKeysTest` only diffed the **top-level** `expect` keys, so a typo inside
`expect.recap` (e.g. `best_streek`) would have passed silently. It now recurses into
`recap`, `recap.hardest_week`, and each `recap.commitment_totals[*]`. Proven: injecting
`best_streek` inside a fixture's `recap` block made the check fail; removing it, green.

### A value a card needed that `Recap` did not provide

The **Hardest week** card wants a date range ("14 Sep – 20 Sep"), but `HardestWeek`
only carried `weekKey` + counts. Per the brief ("if a card needs a value `Recap`
does not have, the fix is in Part 1, not the UI"), `weekStart`/`weekEnd` (Monday/
Sunday of the ISO week, via `ChronoField.DAY_OF_WEEK`) were added to `HardestWeek`
in the engine and to the fixtures — not derived by parsing `weekKey` in the
composable.

---

## Part 2 — the recap screen

### What was built

- **`ArcRepository.getRecapScreenData(arcId): RecapScreenData?`** — one-shot; runs
  `recap()` and bundles what the stats don't carry: arc name/date range, `intention`
  (UI-only, read from the entity), the commitments (to show a `CommitmentTotal`'s id
  with its name/unit), and the milestone rows + reflections (card 6 reads the
  `milestones` table, not the engine).
- **`RecapViewModel` + `RecapUiState`/`RecapCard`** — maps the bundle to an ordered
  card list; skip rules + formatting only, no stat computation. The mapping is
  `internal` so the skip rules are unit-tested without Room.
- **`RecapScreen`** — a `VerticalPager` (foundation, no new dependency), one card per
  page, each sized from its slot only (no `LocalConfiguration`) for the PNG-export
  task.
- **Entry + nav** — a **View recap** button on Today's `Ended` state, threaded
  through `TodayRoute`; `AppRoute.Recap(arcId)` + a MainActivity branch. **Run it
  again** → `CreateArc(remixFromArcId)`, **Start something new** → `CreateArc()`.
  Neither writes `archived` — the ended arc's status is untouched (verified by
  construction: the only writes are the new arc's inserts).
- **DI** — `RecapViewModel` registered.

### No computation in the UI

Every number on a card comes from `Recap`. The only thing added to Part 1 for the
UI was the hardest-week date range (above). Formatting (percent, date ranges,
trimming `.0`) and the skip decisions are presentation, done in the view model.

### A real bug this surfaced: `intention` was collected but dropped

The create flow captured `intention` and showed it in the review step, but `begin()`
never passed it and `createArc()` never persisted it — so `arcs.intention` was always
null and the Intention card could never appear for a real arc. Fixed:
`createArc(intention = …)` persists it and `begin()` passes `s.intention`.

### The recap, card by card — on a real `DebugSeeder` arc

A new **`ENDED_RECAP` ("Ended — recap showcase")** debug scenario was added to
exercise every card: a completed 21-day "Winter Arc", all four commitment types,
gym missed on three days (one clearly-worst ISO week, a long closing streak), a
day-1 intention, and reflections on two milestones. `ENDED_COMPLETED` was also fixed
— it had no commitments, so its recap headline read "0 of 0 days"; it now has one
required commitment done every day.

**Confirmed on an emulator** (completed, seeded 2026-10-04), card by card:

1. **Title** — "Winter Arc", "10 Sep 2026 – 30 Sep 2026", "You showed up 18 of 21
   days." *(verified by screenshot)*
2. **Intention** — "Stop starting over every Monday. Build something that sticks."
3. **Best streak** — 11 days.
4. **Hardest week** — "14 Sep – 20 Sep", 4 of 7 days.
5. **What you did** — Gym: 18 days / Push-ups: 630 reps / Read: 465 minutes /
   Weight: 80 → 78 kg.
6. **Milestones** — A quarter (15 Sep, reflection) / Halfway (20 Sep, reflection) /
   Three quarters (25 Sep) / Arc complete (30 Sep).
7. **Completion** — 86%.
8. **What next** — Run it again / Start something new.

**Failed arc** (`ENDED_FAILED`, derived — not captured on device): the recap is
genuinely sparse because that scenario fails on day 1 — "You made it to day 1", best
streak 0, no hardest week (1 counted day < 3), gym `days_done` 0, and the Milestones
card shows `final` ("Arc ended") plus whatever R12 reported as crossed. It is a
correct but thin recap; `ENDED_RECAP` is the scenario worth looking at.

### How to see it (no 10–15 real days needed)

The seeder fabricates a pre-aged, already-ended arc, so the recap is viewable
immediately: install the debug build → 🐞 FAB → **Ended — recap showcase** → Today's
`Ended` → **View recap** → swipe up through the pager. Reseed to view it again (the
ended arc only loads until a new one exists).

### Honest read: story or stats page?

Right now it reads as a **stats page**, not a story. The content is all there and
correct, but it's stock Material 3 — big numbers, small labels, one card per fact —
with no narrative connective tissue, no visual identity, and no pacing. That is
expected (the brief defers the design pass until the content is seen working), and
it's exactly what that pass needs to address: turning eight correct facts into a
sequence that feels like "here's the run you just finished."

### Known Part-2 gap (deferred to the design pass)

The vertical pager has **no discoverability affordance** — nothing signals that you
swipe up to advance (no page indicator, no peek of the next card, no hint). It works,
but a first-time user can be stranded on the Title card. Candidates for the design
pass: page dots/progress, a peeking next card (`contentPadding`/`pageSize`), or a
one-time hint. (A per-page `verticalScroll` was also removed from the cards — inside a
`VerticalPager` it can swallow the swipe; cards no longer scroll, so a very long card
could clip, which the design/sizing pass will revisit.)

### Note on `Ended`/recap reachability

The `Ended` state (and therefore the recap) is reachable only while the arc is stored
`status = 'active'` and the engine resolves it as completed/failed — `getActiveArcId`
only matches `active`. Once `reconcile` persists the terminal status, the next Today
load shows the empty state. This is pre-existing Today behaviour (tasks 12/14), not
introduced here, but it is why the `ENDED_RECAP` seeder resets the stored status back
to `active` after materialising the milestones.

---

## Test status

- `./gradlew :domain:test` — green (70 fixtures, incl. 8 recap).
- `./gradlew :app:testDebugUnitTest` — green (incl. `RecapViewModelTest`).
- `getRecapScreenData` (the repo accessor) has no automated test yet — it was
  verified by the emulator run. An in-memory-Room test would be a durable guard
  (offered, not yet added).
