# Task: arc creation and empty state

## Context

The Today screen works against a seeded arc. There is no way to create one in
the app. This task adds the creation flow and the empty state, so `DebugSeeder`
stops being the only path to an arc.

Two rules constrain this screen — read both in `rules.md` before starting:

- **R15** — at most one active arc. `ArcRepository.createArc()` already returns
  `CreateArcResult.ActiveArcExists` for this.
- **R16** — `start_date` may not be earlier than `effectiveToday - backfill_days`.
  `:domain` already exposes `earliestAllowedStartDate` and `isValidStartDate`.

Both are already implemented. This task surfaces them in the UI — it does not
reimplement them.

## Scope and deliberate ugliness

Stock Material 3, same as the Today screen. No custom palette, no animation, no
cover images, no template library, no progress metaphor.

The four-step flow from the product spec included an identity step with a cover
and accent colour, and a library of ~60 preset commitments. **Neither is in
scope.** Those are Phase 2. Build the smallest flow that produces a valid arc.

## What to build

### 1. Empty state

When no arc exists, Today shows an empty state instead of a blank screen: one
line explaining what an arc is, and a button to create one.

When an arc exists but is not active (completed, failed, archived), the same
screen should let the user start a new one. Do not build the archive or recap —
just do not leave them stranded.

### 2. Creation flow

Three steps, not four — the identity step collapses into step 1 since there is
no cover or colour yet.

**Step 1 — Name and dates.**
- Name, free text, required, non-blank.
- Start date, defaulting to today. The picker must not offer a date before
  `earliestAllowedStartDate(...)`. Future dates are allowed.
- End date, on or after start date. Offer quick picks (30 / 60 / 75 / 90 days)
  alongside a manual picker.

**Step 2 — Commitments.**
- Add, edit and remove commitments. At least one required. Cap at 10.
- Each needs: name, type, and for counter and duration a target and unit. Plus
  required-or-bonus, and a schedule (daily, or a weekday mask).
- **At least one commitment must be `required`** — an all-bonus arc computes
  0% completion forever (R8), which is nonsense on screen. Block it at creation
  rather than rendering it.
- Duplicate names are allowed by the schema. Warn, do not block.

**Step 3 — Rules and intention.**
- Strictness: the three presets as plain radio options with one-line
  descriptions drawn from `rules.md`.
- `rest_tokens_per_week` shown only when strictness is `standard`.
- `day_boundary_hour` and `backfill_days` under an "advanced" expander,
  defaulting to 4 and 2. Changing `backfill_days` changes the earliest valid
  start date — re-validate step 1's date if it does, and say so rather than
  silently correcting it.
- Intention: one optional free-text field.

Then a review screen listing what will be created, and a Begin button.

### 3. State handling

- Flow state lives in memory in a ViewModel, not in the database. R15 says
  `draft` is not used in v0 — nothing is written until Begin.
- Back navigation between steps preserves entered values.
- Validation is per-step: a step cannot be left until it is valid, with the
  reason shown inline rather than as a disabled button with no explanation.
- On Begin, handle `CreateArcResult.ActiveArcExists` by showing a message. It
  should be unreachable if the entry point is gated correctly, but it is a real
  branch and must not crash.
- After a successful create, navigate to Today showing the new arc.

### 4. Navigation

There is now more than one destination, so add a navigation graph. Two routes:
Today and Create. Nothing speculative beyond that.

## Constraints

- `:domain` is not modified. R15 and R16 already exist there and in the
  repository — call them, do not reimplement. If a validation needs a rule that
  is not written down, **stop and report it** rather than inventing one in the
  UI.
- No rule logic in the ViewModel or composables. Date validity comes from
  `isValidStartDate`, not from a comparison written here.
- No new dependencies.
- Keep `DebugSeeder` working, but gate it so it only seeds when no arc exists.
- No design system work, no animation.

## Definition of done

- `./gradlew :domain:test` still green, unchanged count.
- Fresh install shows the empty state.
- An arc can be created end to end and Today renders it correctly.
- The start date picker refuses a date before the R16 boundary.
- Step 2 refuses to continue with zero commitments, or with all commitments
  marked bonus.
- Creating a second arc while one is active is not reachable from the UI, and
  the `ActiveArcExists` branch shows a message rather than crashing if it is.
- Killing and relaunching shows the created arc.
- ViewModel tests for the validation rules — valid and invalid start dates, the
  all-bonus block, the empty-name block.

## Report back

- Files created, and the navigation structure.
- Anything the UI needed that `:domain` or the repository did not expose,
  and what was done about it. As with the Today screen, this is the most
  valuable output — model gaps are far cheaper to fix before the Swift port.
- Any validation you had to invent because no rule covered it. Each one is a
  candidate rule for `rules.md`, and should be listed explicitly rather than
  left in the UI.
- How changing `backfill_days` in step 3 interacts with an already-chosen start
  date, and what the user sees.
- Whether the three-step split felt right, or whether the steps want
  reorganising once built.