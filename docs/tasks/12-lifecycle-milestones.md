# Task: arc lifecycle and milestones (R10, R12)

## Context

The engine computes `arcStatus` as `completed` or `failed`, but nothing acts on
it. An arc runs past its end date and keeps going as if still active. The
product's premise is that arcs end — right now they do not.

Milestones (R12) have never been implemented. They were explicitly out of scope
in every engine slice. They matter now because they are the recap's raw
material: without them the recap has only numbers, and numbers alone are not
worth building the feature for.

Read R9, R10 and R12 in `rules.md` first.

## The central design question

The engine is a pure function. It **derives** `arcStatus` and it **cannot**
write to the database or create rows. R10 and R12 both say things get created or
set — that work belongs to `:app`.

So the shape is:

- `:domain` decides *what should be true* — the arc's derived status, and which
  milestone kinds should exist by now.
- `:app` makes it true — writes the status, inserts the milestone rows.

Do not move persistence into `:domain`. Do not re-derive status in `:app`.

---

## Part 1 — Arc lifecycle (R10, R9)

### What the engine already gives you

`Resolution.arcStatus` and `Resolution.failedOnDate`. Both correct, both tested.

### What to build

A reconciliation step in `:app`: when a resolution comes back with an
`arcStatus` differing from the stored `arcs.status`, persist the new status.

- `completed` → set `status`, set `completed_at`.
- `failed` → set `status`, set `failed_on_date` from the resolution.
- Bump `updated_at`. Never hard-delete anything.

Decide and state in your report **where** this runs. The repository's
`observeResolution` is the obvious place, but a read path that writes is worth
thinking about rather than doing reflexively. Whatever you choose, it must be
idempotent — reconciling twice must not produce two writes or two milestone
rows.

### UI

Today currently renders an active arc or an empty state. It now needs a third
case: the arc has ended.

Keep it minimal — no recap, no archive, those come later. Show that the arc
ended, whether it completed or failed, and on what date if it failed. Give the
user a way forward: start a new arc.

A failed arc gets a **remix** option that opens the create flow pre-filled from
the failed arc's name, commitments and rules, with fresh dates. `arcs` already
has `remixed_from_arc_id` — set it. Do not copy entries, days, milestones or the
intention.

---

## Part 2 — Milestones (R12)

### Rules, restated

Thresholds are on **elapsed time**, not completion:
`progress = elapsedDates.count / totalDays`.

| kind | created when |
|---|---|
| `start` | at arc creation |
| `p25` | first resolution where `progress >= 0.25` |
| `p50` | first resolution where `progress >= 0.50` |
| `p75` | first resolution where `progress >= 0.75` |
| `final` | when the arc completes (R10) or fails (R9) |

`triggered_on_date` is the `today` at which it was created. `acknowledged_at`
stays null until the user dismisses the card.

Several thresholds can be crossed at once — a short arc, or the app not opened
for a week. Create all of them and present in order.

### Engine side

Add to `Resolution` the set of milestone kinds that **should exist** given the
current progress. The engine does not know which already exist in the database —
it reports what the rules require, and `:app` diffs that against what is stored.

This is engine work in `:domain`, so it needs fixtures. Extend the `expect`
block in `contract/fixtures/README.md` and `Expect.kt`, then write fixtures for
at minimum:

- exactly at the 0.25 boundary (`>=`, not `>`)
- several thresholds crossed in one resolution
- a single-day arc, where every threshold is crossed at once
- a failed arc, where `final` is required from the failure date
- an arc not yet started, where only `start` is required

Derive every expected value from R12 by hand.

### Persistence

`milestones` has `UNIQUE (arc_id, kind)`, and soft-deleted rows still occupy a
unique index — this project has already been bitten by that twice. Insert via
`INSERT ... ON CONFLICT(arc_id, kind) DO UPDATE ... SET deleted_at = NULL`, not
a filtered read followed by a plain insert. Do not use Room's `@Upsert`; the
primary key is a fresh UUID and it would never detect the conflict.

### UI

A full-screen card, shown when an unacknowledged milestone exists. Stock
Material 3 — no animation, no photo picker yet.

- Says which milestone and where the user is ("Day 20 of 75").
- One optional reflection text field.
- Dismiss sets `acknowledged_at`.
- Several pending milestones present in order, oldest first.
- `start` is created at arc creation but should **not** be shown as a card — it
  exists to anchor the recap's timeline, and the user already wrote their
  intention during creation.

Photo capture is deferred. Leave `photo_path` null and do not build a picker.

---

## Constraints

- The engine does not write to the database and does not read the clock.
- No rule logic in `:app`. Which milestones should exist comes from
  `Resolution`, not from a percentage computed in a ViewModel.
- Reconciliation must be idempotent.
- No new dependencies.
- Stock Material 3. No animation, no design system work.
- Do not build the recap, the archive, or photo capture.

## Definition of done

- `./gradlew :domain:test` green, fixture count risen by the milestone fixtures.
- `:app` tests green.
- An arc whose end date has passed shows as completed, and the stored row says
  `completed` with `completed_at` set.
- A strict arc with a sealed miss shows as failed, with `failed_on_date` stored.
- Remix opens the create flow pre-filled, and the new arc has
  `remixed_from_arc_id` set.
- Milestone cards appear at the thresholds and dismiss correctly.
- Relaunching does not re-show an acknowledged milestone or duplicate a row.
- A test that reconciles twice and asserts one status write and one milestone
  row per kind.

## Report back

- Where reconciliation runs, and why that location was chosen over the
  alternatives.
- How idempotency is guaranteed — the specific mechanism, not the intent.
- The fixtures added and the `expect` shape chosen for milestone kinds.
- What happens when an arc ends while the app is closed for two weeks and then
  opened: which milestones appear, in what order, and whether that sequence is
  reasonable to sit through.
- Anything R12 left ambiguous, and the interpretation chosen.
- Anything the recap will need that is not being captured now. Milestones exist
  to feed it, and a field missing today is a hole in the recap later.