# Session 4 — arc creation and the empty state

A walkthrough of everything built in this session, in the same spirit as
sessions 1–3. This session worked through one task brief,
`docs/tasks/11-arc-creation.md`: the Today screen had worked since session 2,
but the only way to get an arc into the database was `DebugSeeder` — there
was no way for an actual user to make one. This closes that gap.

## The starting point

Session 3 left the Today screen and its write path solid: a real
`androidx.lifecycle.ViewModel`, R15 and R16 written into `rules.md` and
backed by code, and a check-then-act race fixed at the root. But both R15
(`ArcRepository.createArc()` returning `CreateArcResult.ActiveArcExists`) and
R16 (`earliestAllowedStartDate` / `isValidStartDate` in `:domain`) were built
with no caller — nothing in the UI invoked `createArc()` at all yet. This
session is what actually calls them.

## Scope, and what was deliberately left out

The product spec's four-step flow includes an identity step (cover image,
accent colour) and a library of roughly sixty preset commitments. Neither is
in scope — both are Phase 2. The brief's instruction was to build the
smallest flow that produces a valid arc, in stock Material 3, with no new
dependencies and no rule logic duplicated into the UI. Three steps plus a
review screen, all backed by calls into `:domain` and `ArcRepository` rather
than reimplementing anything they already decide.

## Part 1 — the empty state

`TodayUiState` already had to handle "no arc" as a case (`NoActiveArc`) once
`getActiveArcId()` could return null. What this session adds is a reason for
that case to ever resolve *forward*: a single line explaining what an arc is,
and a "Start an arc" button. The same state covers two situations that
`getActiveArcId()` can't tell apart on its own — no arc has ever existed, and
an arc exists but isn't `active` (completed, failed, archived) — because the
DAO query behind `getActiveArcId()` only ever matches `status = 'active'`.
Both collapse into one screen; neither is stranded without a path forward,
which is all the brief asked for. Building the archive or a recap screen for
a finished arc is explicitly out of scope here.

## Part 2 — navigation, kept deliberately small

Two routes now exist — Today and Create — which is one more than the app had
before. The brief's instruction ("nothing speculative beyond that") ruled out
pulling in `androidx.navigation` for a two-destination app. `AppRoute` is a
plain sealed interface (`Today`, `CreateArc`), and `MainActivity` switches on
it with a `remember { mutableStateOf<AppRoute>(...) }` — the whole graph is
about ten lines. The comment left on `AppRoute` says explicitly when to
reconsider this: the day a third route shows up and the in-memory `when`
stops being enough.

## Part 3 — the three-step flow

`CreateArcViewModel` holds `CreateArcUiState` — name, dates, a list of
`CommitmentDraft`, the rule fields, and the current step — entirely in
memory. Per R15's own text ("`draft` is not used in v0 — the creation flow
holds state in memory and writes the arc only when the user confirms it"),
nothing touches the database until the user presses Begin on the review
screen.

**Step 1 — name and dates.** Name is required and non-blank. Start date
defaults to today; the date picker's `SelectableDates` is wired directly to
`state.earliestAllowedStartDate`, which is `:domain`'s own
`earliestAllowedStartDate(today, backfillDays)` — the picker can't even
render a disallowed date as selectable, and the inline error message
(`startDateError`) is driven by the same `:domain` function
(`isValidStartDate`), not a re-derived comparison. End date must be on or
after start date, with 30/60/75/90-day quick-pick buttons alongside a manual
picker.

**Step 2 — commitments.** Add/edit/remove through a dialog editor, capped at
ten, at least one required. "At least one required" isn't cosmetic — R8 says
an all-bonus arc computes 0% completion forever, which is nonsense to show a
user, so `hasRequiredCommitment` blocks leaving the step rather than letting
that state ever reach the screen. Duplicate names are allowed by the schema
(warned about, not blocked) per the brief. Each commitment needs a type, and
for counter/duration a target and unit; schedule is either daily or a
weekday mask, matching `ScheduleType` already defined in `:domain`.

**Step 3 — rules and intention.** Strictness as three radio options, their
one-line descriptions lifted directly from `rules.md`'s own wording for R6
(standard's rest tokens) and R9 (strict's sealed-miss failure). Rest tokens
per week only shown under `STANDARD`. Day boundary hour and backfill days
sit behind an "Advanced" expander, defaulting to 4 and 2 — the same defaults
`DebugSeeder` already used.

**Review.** Everything the arc will be created with, a Begin button, and — if
`createArc()` comes back with `CreateArcResult.ActiveArcExists` — an inline
message rather than a crash. The brief calls this branch out explicitly as
one that should be unreachable if the entry point is gated correctly, but
still a real branch that has to degrade gracefully if it's ever hit.

## Part 4 — the interesting wrinkle: `backfill_days` changing mid-flow

Step 3 lets the user change `backfillDays` after already picking a start
date in step 1 — and changing it moves `earliestAllowedStartDate`, which can
make an already-valid start date invalid retroactively. The brief was
explicit that this has to be surfaced, not silently corrected: `step3Error`
re-runs the exact same `isValidStartDate` check step 1 uses, and if it now
fails, step 3 shows the same message step 1 would, plus "Go back to step 1
to fix it." Nothing auto-adjusts the user's chosen date behind their back.
Covered directly by a ViewModel test: set a valid start date at the default
`backfillDays = 2`, drop `backfillDays` to `0`, and confirm both
`startDateError` and `step3Error` become non-null.

## Part 5 — validation ownership

Every date-boundary decision routes through `:domain`'s
`earliestAllowedStartDate` / `isValidStartDate` — no rule logic was written
fresh in the ViewModel or composables for R16. Two validations exist that
have no corresponding entry in `rules.md`, and per the brief's instruction
("if a validation needs a rule that is not written down, stop and report it
rather than inventing one in the UI") they're called out here rather than
left silently embedded in `CreateArcUiState`:

- **Non-blank name.** Purely a form-usability constraint — there's no
  product rule that names must be non-empty, just that an empty name isn't
  useful to show as an arc's identity anywhere.
- **At least one required commitment.** This one *is* a direct consequence
  of an existing rule (R8's 0%-forever result for an all-bonus arc), just
  enforced at creation time instead of computed and then displayed as a
  permanently-broken arc. Worth a line in `rules.md` if a future arc-editing
  flow needs the same check outside creation.

Neither needed inventing new domain behaviour — both are UI-level guards in
front of rules that already exist or plain form hygiene.

## Part 6 — keeping `DebugSeeder` from fighting the real flow

`DebugSeeder.seedIfNeeded()` already bailed via `repository.hasAnyArc()` —
built that way back in session 2, before creation existed, specifically so
it would stop seeding the moment a real arc existed. What this session
needed was for that guard to actually matter: a real creation flow that can
put an arc in the database, and confidence that the seeder wouldn't step on
it either way (before or after that arc exists).

While verifying the empty state renders correctly on a genuinely fresh
install, the seeder's call site in `StreakSeasonApp.onCreate()` was
temporarily forced off (`isDebuggable() && false`, with a `TEMP` comment) so
the empty state could be observed without the seeder immediately filling it.
That flag was still off — the seeder fully disabled — at the point this
session picked back up, which would have quietly broken the "fresh debug
install shows a seeded arc" path the seeder exists for. Restored to a plain
`isDebuggable()` check; `seedIfNeeded()`'s own `hasAnyArc()` guard is what
actually keeps it from clobbering a user-created arc, and that guard was
never the thing in question.

## Part 7 — proving it end to end

Both `:domain:test` (61 tests, unchanged — this task didn't touch `:domain`)
and `:app:testDebugUnitTest` (18 tests) pass. The new coverage is
`CreateArcViewModelTest`, 12 tests: the R16 boundary from both sides (one day
before it, exactly on it, comfortably in the future), the `backfillDays`
interaction described in Part 4, blank-name and non-blank-name, the
empty-commitment-list and all-bonus blocks, a passing case with one required
commitment present, and `next()` correctly refusing to advance past an
invalid step versus advancing once it's valid.

## What exists now vs. before

**Before this session:** the Today screen worked, but only against a
debug-seeded arc — R15 and R16 existed in `:domain` and `ArcRepository` with
no caller, and there was no way for an actual user to get an arc into the
app or recover from having none.

**After this session:** a full create flow — empty state, three steps plus
review, in-memory until Begin, R15 and R16 both exercised through real UI
rather than sitting unused, and a two-route navigation switch sized to
exactly what the app has today. `DebugSeeder` runs again on debug builds
without being able to interfere with a user-created arc. The `backfill_days`
mid-flow interaction is surfaced to the user rather than silently
resolved, per the brief's explicit instruction not to auto-correct it.

Two validations exist in the UI with no line in `rules.md` behind them
(non-blank name, at least one required commitment) — both are noted in Part 5
rather than left implicit, since the second is a real candidate for
`rules.md` if arc-editing ever needs the same check outside creation.

On the three-step split itself: it held up. Step 1 and step 2 are each one
clear decision (when, and what); step 3 bundles strictness with the
advanced/rarely-touched fields, which worked because the advanced fields are
genuinely rare to change and collapsing them didn't hide anything step 3's
own validation depends on. The one seam worth watching if this gets revisited
is the `backfillDays`-in-step-3-affects-step-1 dependency — it works today
because there are only three steps to reason about, but it's the kind of
cross-step coupling that gets harder to keep straight if a fourth step is
ever added between them.
