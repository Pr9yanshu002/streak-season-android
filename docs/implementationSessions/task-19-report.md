# Task 19 — Create-arc redesign: report

Pieces 1–8 on `task-19-create-arc-redesign` (merged as PR #9). Priyanshu wrote each piece by
hand; Claude reviewed it, fixed what was agreed and wrote the tests. There is
no brief in `docs/tasks/` and no design folder for the create flow
(`docs/design/` has only `today/`), so the pieces were checked against the
behaviour Priyanshu described, not against a design.

Every step of the flow is rebuilt. The follow-up fixes from the whole-branch
review are on `task-19b-create-arc-review-fixes`.

## Pieces 1–2 — five steps, errors shown on Next

- `CreateArcStep` is `NAME_AND_DATES, COMMITMENTS, STRICTNESS, INTENTION,
  REVIEW`. Intention has its own `IntentionStep`.
- `CreateArcUiState.showErrors`; `step3Error` renamed `strictnessError`;
  `blockingError` is the single "what stops Next on this step" getter.
- Next is always enabled. A blocked `next()` stays on the step and sets
  `showErrors`; a successful `next()` and `back()` clear it. The screen draws
  the name, start, end, step-2 and strictness errors only when `showErrors`
  is set (private `shown()` helper). The duplicate-name warning is not gated.
- Review finding fixed by Priyanshu: `back()` did not clear `showErrors`.
- Stale comments about the three-step flow and the disabled Next button
  updated by Claude.

## Piece 3 — step header and intro

- `ui/create/CreateArcParts.kt`: `StepHeader` (four bars, read by TalkBack as
  "Step N of 4", and a Close button) and `StepIntro` (eyebrow, title,
  optional subtitle). Each step's title and subtitle are in
  `CreateArcStep.text()`.
- `CreateArcStep.number` (1–4, null on Review) and `NUMBERED_STEP_COUNT` feed
  both the bars and the eyebrow.
- Review findings fixed by Priyanshu: `ordinal+1.coerceAtMost(4)` coerced the
  `1`, so Review read "Step 5 of 4"; the Close button was inside the bars'
  `clearAndSetSemantics`, so TalkBack couldn't reach it.

## Piece 4 — bottom bar and Begin

- `StepBottomBar`: divider, a 112 dp outline Back (hidden on step 1) and a
  filled primary button: "Next", "Review" on Intention, "Begin <name>" on
  Review, "Starting…" while submitting.
- `begin()` sets `isSubmitting` before launching and returns if it is
  already set. Before, two quick taps could both pass `createArc`'s R15 check
  before either insert landed and create two active arcs. The new test fails
  against the old `begin()` (2 arcs).
- Review finding fixed by Priyanshu: `LargeButton` fills the width, so an
  unweighted Back squeezed the primary button to zero.

## Piece 5 — field parts

- `FieldLabel`, `FieldError` (larch info icon, polite live region),
  `StreakTextField`, `streakFieldColors()`, `DateField`, `ChoiceChip`.
- Drawables `ic_close`, `ic_info`, `ic_calendar` (Material icons).
- A pinned `material3 = "1.4.0"` catalog entry was removed: the Compose BOM
  already supplies Material3 at 1.4.0, and the pin would have overridden it
  on a later BOM bump.

## Piece 5B — name and dates step

- Name uses `StreakTextField`. Starts/Ends use `DateField` ("Today · Thu 22
  Oct" when the start is today). The start shows the R16 backfill hint, which
  the error replaces. "How long" is a `ChoiceChip` row (30/60/75/90). A card
  shows the length and the range, hidden while the end is before the start.
- `CreateArcUiState.lengthInDays` counts both the start and the end day.
- Review finding fixed by Priyanshu: `DatePickerField` still drew the old
  ISO-date button above the new field.

## Piece 6-a — commitment editor state

- `CommitmentDraft` holds `targetText` instead of a Double, with
  `targetValue`, `needsTarget`, `error` and `cleaned()`. This fixes the old
  truncation: a 2.5 target came back as 2 in the dialog and the summary.
  Remix converts the stored number back with `formatInputAmount`.
- Editor state in the ViewModel (`editing`, `editorShowErrors`,
  `isEditingExisting`; `startNewCommitment`, `editCommitment`,
  `updateEditing`, `cancelEditing`, `saveEditing`, `removeEditing`). Not used
  by the screen yet.
- Review fixes by Claude, as agreed:
  - "NaN" and "Infinity" were accepted as targets (`toDoubleOrNull()` parses
    them); `error` now requires a finite target.
  - `saveCommitment` bypassed the checks; it now ignores an incomplete draft
    and saves the cleaned one.
  - `editCommitment` with an unknown id closed the open editor; it now leaves
    it alone.
  - Changing step now closes the editor.
  - The screen was updated just enough to compile (the dialog and summary
    use `targetText`; the dialog rejects a non-finite target).

## Pieces 6b-2 and 6c — commitment editor and list

- The old dialog is gone. The editor is full screen, driven by the
  ViewModel's `editing` state: kind cards, target and unit, required/bonus,
  every day or some days with a day picker. The phone's back gesture closes
  it. New parts: `EditorTopBar`, `SegmentedControl`, `OptionCard`,
  `DayPicker`.
- The commitments step lists each commitment as a card ("Reach 3,000 ml ·
  Every day", Required/Bonus chip) that opens the editor, with an "N of 10"
  count and a dashed add button. New parts: `RequirementChip`,
  `CommitmentListCard`, `DashedAddButton`.
- Review fixes by Claude: a target typed with the phone's decimal comma is
  accepted (`parseAmount`), and text that isn't a number gets its own
  message; the day-picker hint for all seven days no longer says "On other
  days".

## Piece 7 — strictness step

- Strict, standard and chill are `OptionCard`s. Standard shows a rest-days
  stepper; day start and backfill sit in an Advanced section that opens on
  its own when a remix brings non-default values. New parts: `SectionCard`,
  `StepperRow`, `ExpandableSection`.
- `begin()` stores 0 rest tokens for strict and chill arcs (R6).
- Review fixes by Claude: hints stay true at 0, the day-start time follows
  the 12/24-hour setting, rest days capped at 7 (also on remix), stepper
  changes announced by TalkBack, `DEFAULT_DAY_BOUNDARY_HOUR` /
  `DEFAULT_BACKFILL_DAYS`.

## Piece 8 — intention and review steps

- Intention uses `ReflectionField`. Review is a column of `ReviewCard`s
  (name and dates, commitments, strictness, intention); each Edit jumps to
  its step (`goToStep`).
- Review fixes by Claude: space between the cards, "only today can be
  filled in" for a backfill of 0, "starts today".

## Not in `rules.md` (create-flow checks in `:app` only)

Only R15–R18 come from `:domain` or `rules.md`. The app-only limits are
listed in `task-19-notes.md`: at most 10 commitments, at most 7 rest days a
week, end date not before the start date, counter/duration need a target
above 0 and a unit, a weekdays schedule needs a day.

## Open items

- Filled Material icons instead of the design system's stroke glyphs
  (`StrokeGlyph`); colours read from `StreakTheme.colors` where the
  components use `MaterialTheme.colorScheme`; raw dp values.
- From the whole-branch review, not fixed yet: rule logic re-derived in
  `:app` (R1 `effectiveToday` and R2 `totalDays` are `internal` in
  `:domain`), text-style overrides, and the accessibility gaps. The known
  issues (`isSubmitting` after an exception, remix pre-fill overwriting
  early edits, the draft lost on process death) are in `task-19-notes.md`.
- Not checked on the emulator by Claude.

## Tests

`./gradlew :app:testDebugUnitTest`: 115/115. `./gradlew :domain:test`: 161/161.

New or changed:
- `CreateArcViewModelTest`: `step3Error` → `strictnessError`; blocked Next
  shows errors; recovery after a blocked Next; the full five-step walk and
  back; back clears errors; double `begin()` creates one arc; `lengthInDays`
  (default 60, each quick length, one-day arc, moved start); the editor
  (open, incomplete save, complete save, edit in place, cancel, remove,
  unknown id, step change closes it); `saveCommitment` checks; remix keeps a
  fractional target.
- `CreateArcStepTest` (new): step numbering.
- `CommitmentDraftTest` (new): `error`, NaN/Infinity, decimals, `cleaned()`,
  comma decimals (`parseAmount`).
- `CreateArcViewModelTest` (pieces 6b-2 to 8): editor flows through
  `updateEditing`, rest-day clamp and strictness switching, rest tokens
  stored per strictness, `goToStep`.
- `CreateArcTextTest` (new): `scheduleHint`, `detailText`, the strictness
  descriptions, `startsText`, `backfillText`, `restDaysText`.
