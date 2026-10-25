# Task 19b — Create-arc review fixes: report

Follow-up to task 19 on `task-19b-create-arc-review-fixes`. There is no brief
in `docs/tasks/`: after task 19 merged (PR #9), Claude reviewed the whole
branch against `main`, and Priyanshu chose which findings to fix. The finding
IDs below (B, X) come from that review. Claude made the changes; nothing in
`:domain`, `contract/` or the repository layer was touched.

## Fixes (commit `bdfc2b6`)

- **B1 — back gesture.** The create flow had no back handling outside the
  commitment editor, so the phone's back gesture likely closed the app and
  lost the in-memory draft. `CreateArcScreen` now has a `BackHandler`: back
  goes one step back, or leaves the flow from step 1. It isn't composed while
  the editor is open, so the editor's own handler still wins.
- **B2 — strict wording (R9).** The strict card said "One missed day ends the
  arc", but a sealed partial day fails a strict arc too. It now reads "A day
  you don't finish ends the arc." The code comment says the same.
- **B3 — intention placeholder.** `ReflectionField` (`ui/components/Forms.kt`)
  takes a `placeholder`, defaulting to "How is it going?" so Today is
  unchanged. The intention step passes "I'm doing this because…".
- **B4 — keyboard.** `imePadding()` on the outer column of the flow and of
  the commitment editor. The app is edge-to-edge on SDK 36, where
  `adjustResize` no longer resizes the window.
- **B7 — Edit during submit.** `goToStep()` is ignored while `isSubmitting`.
- **B8 — start hint (R16).** "You can start up to 1 day back…", and "You can
  start today or any day ahead." for a backfill of 0.
- **B9 — start-date error (R16).** "The earliest start is Sun 20 Sep, 2 days
  back. Pick that or a later day.", or "The earliest start is today." Before,
  it showed an ISO date and said "backfill window".
- **B12 — date formatting.** `shortText()`, `fullText()` and `daysText()`
  moved to a new `ui/create/CreateArcText.kt` so the ViewModel and the screen
  share them. The formatters are built when used, so a language change while
  the app runs is picked up.
- **B10 — all-bonus error.** No "(R8)" in user-facing text: "Make at least one
  commitment required. Required ones decide whether a day counts."
- **B11 — active-arc message (R15).** "You already have an active arc. You can
  start a new one once it ends."

## Cleanup (commit `2776a32`)

- **X1–X3 — dead code.** `CommitmentDraft.summary()` and `removeCommitment()`
  deleted. `saveCommitment()` stays as the tests' shortcut; its comment says
  so.
- **X4 — unused imports.** `Spacer`, `OutlinedTextField`.
- **X5 — stale comment.** `KINDS` no longer claims the list rows use it.
- **X6–X7 — docs.** `CLAUDE.md` marks task 19 done; `task-19-report.md` covers
  pieces 1–8 and keeps only the open items.
- **X8.** `DayOfWeek.entries` instead of `values()`.
- **X9 — previews.** `@PreviewLightDark` for the create parts, plus previews
  for `StepBottomBar`, `StepperRow`, `ExpandableSection`, `ReviewCard` and the
  whole screen on Review.
- **Formatting.** Spacing, trailing commas, one parameter per line where a
  signature was crammed, blank lines. Done by hand: Android Studio's
  command-line formatter won't run while Studio is open. Nine long lines (over
  120 characters) were left as they were.

## Not fixed (recorded in `task-19-notes.md`)

- B5: `isSubmitting` stays true if `createArc` throws (from before task 19).
- B6: the remix pre-fill overwrites anything typed before it loads (from
  before task 19).
- B13: the draft is lost on process death; keeping it needs a
  `SavedStateHandle`.

Also still open from the review, not attempted here:
- D1–D4, rule logic in `:app`: R1 `effectiveToday` and R2 `totalDays` are
  `internal` in `:domain`, so the flow uses the calendar date and its own day
  count. Making them public is Priyanshu's code. The draft checks (target
  above 0, unit, at least one weekday) are app-only.
- T1–T3, text styles: `bodyLarge` with a `SemiBold` override, a divider in
  Material's default colour, a repeated corner radius.
- A1–A9, accessibility: day-picker circles under 44 dp wide, headings not
  marked, strictness cards not in a selectable group, the active-arc message
  not announced, generic Edit labels.

## Tests

`./gradlew :domain:test`: 161/161. `./gradlew :app:testDebugUnitTest`: 115/115.

Changed: `CreateArcTextTest`'s three strict-description tests expect the new
wording. No other test compared the changed strings. The back gesture,
`imePadding()` and the previews are Compose-only and have no unit tests.

Not checked on the emulator by Claude.
