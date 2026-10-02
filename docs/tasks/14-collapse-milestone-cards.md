# Task 14: collapse catch-up milestone cards

## Context

Task 13's verification (`docs/implementationSessions/task-13-report.md`, item
1c) found that after a lapse, the user sits through one full-screen milestone
card per crossed threshold — three, sometimes four — before reaching the
check-in screen. Each demands a Continue tap and an implicit reflect-or-skip
decision. The likely result is tapping through all of them without reading,
which defeats the point of milestones.

This task replaces that sequence with a single summary card when more than one
milestone is pending.

This is a **presentation change only**. No rule changes, no `rules.md` edit, no
fixture changes, no change to `resolve()` or to which milestones exist or what
dates they carry.

---

## Part 1 — the summary card

### Behaviour

- **Exactly one pending milestone** — the existing card, unchanged.
- **Two or more pending** — one card listing all of them, oldest first. Each row
  shows the milestone's label and where it fell, e.g. "Halfway — day 10 of 20,
  10 Sep". Dates come from `triggered_on_date`, the crossing date, as now.
- One **optional** reflection field on the summary card.
- **Continue** acknowledges every milestone shown on the card.

### Copy

Do not write "while you were away." Two pending milestones can also happen
without any absence — the user opens the app, sees one card, closes it without
dismissing, and a second threshold is crossed the next day. Use neutral wording
that is true in both cases, e.g. "You've passed 3 milestones."

### Where the reflection goes

Save the reflection on the **most recent** milestone on the card — latest
`triggered_on_date`. Leave the others' `reflection` null.

A reflection written on day 16 is not a day-5 reflection, so attaching it to the
latest milestone is the honest place for it. The recap will read it from there.

### `final` in the summary

If the arc ended while milestones were pending, `final` appears in the list like
any other kind. Do not special-case it. Report how the summary card followed by
the `Ended` screen reads in that case.

### Acknowledging — two requirements

**Acknowledge by the ids shown, not "everything pending for this arc."** A
milestone can become pending while the card is on screen — the day boundary
passes, a threshold is crossed, reconcile runs on resume. Acknowledging by arc
would silently dismiss a milestone the user never saw. Pass the exact ids the
card displayed.

**Acknowledge atomically.** All ids, plus the reflection on the latest one, in
one transaction. If it is three separate writes and the app is killed partway,
the next launch shows a summary of the leftovers, which is confusing. Either a
single `UPDATE … WHERE id IN (…)` plus the reflection write inside
`@Transaction`, or `withTransaction` — whichever fits the existing DAO style.

Set `updated_at` on every row written, per the hard rules.

### Where the logic lives

Choosing between single card and summary is presentation, so it belongs in
`TodayViewModel`'s mapping into UI state — for example a sealed
`PendingMilestoneUiState` with `Single` and `Summary` cases. It is not a rule and
does not go in `:domain`.

The pending list already arrives ordered from `observePendingByArc`. Do not
re-sort it in the view model.

---

## Part 2 — confirm the remix test goes through the real path

Task 13 also fixed remix so it carries the source arc's rule set (strictness,
rest tokens, backfill, day boundary). The original remix test passed `STRICT`
into `createArc()` directly, so it never exercised the pre-fill and could not
have caught the bug.

1. Check whether the remix test was updated to go through the path the user
   takes: `getRemixSource()` into `CreateArcViewModel`'s pre-fill, then
   `createArc()` with whatever the view model produced.
2. If it was not, rewrite it so it does. Assert all four rule values come
   through, using source values that **differ from the create flow's defaults**
   (`STANDARD`, `1`, `2`, `4`) — otherwise a broken pre-fill that falls back to
   defaults would still pass.
3. Prove it: temporarily drop `strictness` from `RemixSource`, confirm the test
   fails, then restore. Same method as task 13's item 1d.

Also confirm that pre-filling a source `backfill_days` different from 2 moves
the R16 earliest start date, and that the start-date re-validation still flags
a pre-filled start date that falls outside the new window.

---

## Constraints

- No change to `:domain`, `rules.md`, `schema.sql` or any fixture.
- No new dependencies.
- No schema migration — every column needed already exists.
- Stock Material 3, consistent with the existing card. No animation.

## Definition of done

- One pending milestone shows the existing card, unchanged.
- Two or more show one summary card, oldest first, with crossing dates.
- Continue acknowledges exactly the ids shown, in one transaction.
- The reflection is saved on the latest milestone only.
- After kill and relaunch, nothing acknowledged reappears.
- The remix test goes through the pre-fill path and fails when a rule field is
  dropped from `RemixSource`.
- `./gradlew :domain:test` and `:app:testDebugUnitTest` green.

### Tests to add

- View model: one pending maps to `Single`; three map to `Summary` in the same
  order.
- Repository: acknowledging a set of ids clears exactly those; a milestone not in
  the set stays pending.
- Repository: the reflection lands on the latest milestone and nowhere else.

## Report back

Write to `docs/implementationSessions/task-15-report.md` and commit it with the
task, per `CLAUDE.md`.

- Screens: what the summary card shows for three pending milestones, and for the
  case where `final` is among them, followed by the `Ended` screen.
- How atomicity was achieved — the specific mechanism.
- Part 2: was the remix test already going through the pre-fill path, or did it
  need rewriting? Did it fail with `strictness` removed from `RemixSource`?
- Whether the backfill / R16 re-validation behaved correctly on a pre-filled
  remix.