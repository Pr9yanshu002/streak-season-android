# Task: Today screen — first UI slice

## Context

The rules engine is complete (45/45 fixtures green) and the persistence layer is
done: Room entities, DAOs, mappers, `Clock`, `ArcRepository`, Koin DI.

Nothing is on screen yet. This task builds the first screen and proves the full
chain end to end: database → mapper → engine → UI → write → re-resolve.

## Scope and deliberate ugliness

**This is a functional slice, not a designed one.**

Use stock Material 3 defaults throughout. No custom palette, no custom
typography, no animation, no progress metaphor, no cover images, no icons beyond
Material's built-in set. A plain vertical list of plain cards is correct.

The reason: the domain model has never been used by real UI, and it will
probably need a shape change or two. Design effort spent on a screen whose shape
may change is wasted. The visual language, the tree metaphor and the check-in
animation are Phase 2 work.

If a layout decision needs making, pick the plainest option and move on.

## What to build

### 1. Debug seed

There is no way to create an arc yet, so seed one on first run in debug builds
only.

- An active arc, `strictness = 'standard'`, starting ~10 days before the
  current date, ending ~30 days after.
- Four commitments covering all four types: one binary, one counter with a
  target, one duration with a target, one log. Make one of them `bonus` and one
  scheduled on weekdays only, so the Today screen exercises R3 and R5 properly.
- Entries for some of the past days, with a mix of comple
- te, partial and missed,
  and at least one day flagged `rest_token_used`.

Seed it idempotently — check for existing rows first, do not re-seed on every
launch. Release builds seed nothing.

### 2. `TodayViewModel`

- Collects `ArcRepository.observeResolution(arcId)`.
- Exposes a single UI state object as `StateFlow`.
- The state carries what the screen renders: the arc's name, the day number and
  total, today's date, today's state from the `Resolution`, and the list of
  commitments scheduled today with their current entry values.
- Actions: complete a binary commitment, set a value on a counter or duration,
  record a log value, undo an entry.

Every write goes through the repository and the resolution re-emits. **Do not
recompute anything in the ViewModel** — if the screen needs a derived value, it
comes from `Resolution`, not from logic written here. Duplicated rules in the UI
layer is the specific failure this whole architecture exists to prevent.

### 3. `TodayScreen`

Top to bottom:

- Arc name, and "Day 12 of 40" derived from the resolution.
- Today's state, as plain text for now (`complete` / `partial` / `missed` /
  `pending`).
- The list of commitments scheduled today. Each card shows the name, whether it
  is satisfied, and its type-appropriate control:
    - **binary** — a checkbox or toggle
    - **counter** — current value against target, with plus/minus stepper
    - **duration** — same, in minutes
    - **log** — a text field for a number
    - Mark bonus commitments visibly as optional, in text.
- A commitment already satisfied should be visibly distinguishable from one that
  is not. Plain text or a checkmark is enough.

Stateless composable taking state and callbacks, with a stateful wrapper that
holds the ViewModel. Preview functions with fake state for each of the four
commitment types.

### 4. Navigation

A single destination is fine. Do not build a navigation graph for screens that
do not exist yet — add routes when there is a second screen.

## Constraints

- `:domain` is not modified. If the engine or a domain model appears to need a
  change, **stop and report it** rather than changing it. That report is a
  primary output of this task.
- No business logic in the ViewModel or composables. All derived values come
  from `Resolution`.
- `LocalDateTime.now()` is never called outside `SystemClock`. The ViewModel
  takes a `Clock`.
- No new dependencies. Compose, Material 3, Koin and what is already there.
- No animation, no custom theming, no design system work.

## Definition of done

- `./gradlew :domain:test` still 45/45 green.
- The app launches, shows the seeded arc, and lists today's commitments.
- Tapping a binary commitment marks it done, the entry persists, and the day
  state updates without a restart.
- Incrementing a counter past its target flips the day state when it is the last
  unsatisfied required commitment.
- Killing and relaunching the app shows the same state.
- Undo removes the entry and the day state reverts.

## Report back

- Files created.
- **Anything in the domain model that made the UI awkward.** Missing fields,
  wrong shapes, values the screen needed that `Resolution` does not expose,
  places where you were tempted to compute something in the ViewModel. This is
  the most valuable output of this task — the model is about to be translated to
  Swift, and shape problems are far cheaper to fix now than after the port.
- Anything you had to look up in `rules.md` to render correctly. That indicates
  a rule the engine should be surfacing rather than the UI re-deriving.
- Anything that felt like it belonged in `Resolution` but was not there.