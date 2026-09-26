# Session 5 — arc lifecycle, milestones, and remix

A walkthrough of everything built in this session, in the same spirit as
sessions 1–4. This session worked through `docs/tasks/12-lifecycle-milestones.md`
end to end, plus an unplanned follow-up brief it produced along the way,
`docs/tasks/12d-r12-amend-milestone-dates.md`.

## The starting point

Session 4 left arc creation working: a real create flow, a two-route
`AppRoute` switch, and a Today screen that could show an active arc or an
empty state. But the product's premise — that arcs *end* — still wasn't true.
An arc that ran past its end date just kept behaving as if active forever,
and R12 (milestones) had never been implemented at all; it had been explicitly
out of scope in every earlier engine slice. Task 12's brief set the goal:
persist the engine's derived `arcStatus` (R9/R10) into the stored row, and
compute + persist milestones (R12) as the raw material a future recap will
need.

The brief drew a clear architectural line up front: `:domain` derives *what
should be true* (status, which milestone kinds should exist); `:app` makes it
true (writes the status, inserts the rows). That line held for the whole
session, though which side of the CLAUDE.md ownership split did the writing
moved more than once.

## Part 0 — getting the repo ready to push

Before touching task 12, this session did a git-readiness check: the project
had never been `git init`'d, and `.gitignore` had just been hand-edited.
Checked for secrets (`local.properties`, `gradle.properties`, no `.jks`/
`keystore`/`.env`/`google-services.json` anywhere), confirmed build outputs
were properly excluded, and confirmed `settings.gradle.kts` doesn't include
`contract/` as a Gradle module. Two gaps found and fixed: `.kotlin/` (the
Kotlin incremental-compile cache, same category as `.gradle/`) wasn't
ignored, and `.idea/` was only partially ignored via a handful of named files
— replaced with a blanket `/.idea/`. `git init` itself was left for later;
this was purely an "is it safe to commit" pass.

## Part 1 — dividing task 12, and the split that didn't hold

CLAUDE.md reserves the rules engine and the repository layer for Priyanshu,
Claude reviewing only. Task 12 touches both — `Resolution` needed a new
milestone field, and `ArcRepository` needed the reconciliation logic. Rather
than assume the standing engine-slice override applied (task 12 isn't part of
the numbered `NN-engine-*.md` series it was granted for), the split was
negotiated up front: Priyanshu would write the `Resolution` field and the
reconciliation/upsert logic; Claude would write the Room entities/DAOs, DI
wiring, fixtures, and the Compose UI.

That split produced real work in both directions before it eventually gave
way (Part 4) to a full handover.

## Part 2 — reviewing handwritten changes, and three real bugs

Partway through, Priyanshu wrote a first pass at the engine and repository
pieces himself and asked for a review-and-fix pass. `./gradlew
:app:compileDebugKotlin` caught two of the three problems immediately:

1. **`MainActivity.kt`** used `org.koin.java.KoinJavaComponent.inject` — the
   Java-interop version of Koin's `inject()`, which needs an explicit `Class`
   argument — instead of the reified, Android-idiomatic
   `org.koin.android.ext.android.inject()`. Only usage of `by inject()` in
   the whole codebase, so no existing precedent to have caught this earlier.
2. **`ArcRepository.reconcile()`** referenced `resolution.completedAt`, a
   field that doesn't exist on `Resolution` (the engine never decides a
   wall-clock timestamp — that's `:app`'s job per R10), and called
   `arcDao.updateStatusIfChanged(...)` with two of its five required
   arguments.

The third was a design problem the compiler couldn't catch:
`RulesEngine.requiredMilestones` used `.toString()` on the `ArcStatus` enum
inside `reconcile()`, which would have written `"COMPLETED"` (the enum's
`.name`) into a column every other write already treats as lowercase
(`"completed"`, matching `createArc()`'s `ArcStatus.ACTIVE.name.lowercase()`
and `getActiveArcId()`'s `status = 'active'` query).

Fixing the engine side took a wrong turn. R12 says `triggered_on_date` is
"the `today` at which it was created" — read literally, that meant every
milestone kind required in a single resolution should share one date
(`Resolution.effectiveToday`), not the per-kind historical dates the original
code back-computed (`arc.startDate.plusDays(crossingDay - 1)`). Acting on
that reading, `requiredMilestones` was changed from
`Map<MilestoneKind, LocalDate>` to `Set<MilestoneKind>`, on the theory that
`:app` should stamp `effectiveToday` onto whatever kinds it found newly
required. This compiled, passed the (still fixture-less) domain tests, and
was wrong — Part 3 is why.

## Part 3 — task 12d: the rule was wrong, not the code

A follow-up brief, `docs/tasks/12d-r12-amend-milestone-dates.md`, made the
case directly: R12 as written conflates *when the app noticed* a threshold
with *when the user crossed it*, and those differ every time the app isn't
opened daily — which is most of the time over a 40- to 90-day arc. Worked
example from the brief: a 40-day arc starting 2026-01-12 crosses 50% on
2026-01-31 (day 20), but if the user doesn't open the app until 2026-02-05,
the `Set<MilestoneKind>` design would have stamped `p50` with 2026-02-05 —
the day the app happened to check, not the day the user actually got there.
The recap's milestone sequence and photo timeline both order by this date;
recording the wrong one makes both wrong for anyone who skips days, which is
close to everyone.

The crossing date isn't an estimate — it's exact arithmetic already available
from `start_date` and `totalDays`:
`crossingDay = ceil(totalDays × numerator / denominator)`. Per CLAUDE.md's
"if code and `rules.md` disagree, the code is wrong," fixing this meant
amending the rule *first*, then the code:

- **`contract/rules.md`** — R12's `triggered_on_date` sentence rewritten:
  it's the date the threshold was crossed, computed by the engine from
  `start_date`/`totalDays`, never `effectiveToday`. Several thresholds
  crossed in one resolution now explicitly carry *different* dates — their
  own crossing dates, not a shared observation date.
- **`RulesEngine.requiredMilestones`** reverted to
  `Map<MilestoneKind, LocalDate>`, restoring (and re-commenting) the original
  per-kind crossing-date computation: `start` → `arc.startDate`, `p25`/`p50`/
  `p75` → `arc.startDate + (crossingDay - 1)` via integer `ceilDiv` (no
  `Double`, per the brief — a 75-day arc's p50 crosses at day 38, and a float
  comparison at a `>=` boundary is exactly where that would land wrong),
  `final` → `arc.endDate` (completed) or `failedOnDate` (failed).
- Crossing days verified by hand against the brief's table — 40-day arc:
  10/20/30; 75-day arc: 19/38/57; 1-day arc: 1/1/1 — all matched.
- **Fixtures**: `Expect.kt`'s `required_milestones` field reverted to
  `Map<String, String>`, `RulesEngineFixtureTest.kt` asserts it as a *whole
  map* (not key-by-key — a kind the engine returns that a fixture omits fails
  the test, same reasoning as `commit_status_no_missing_keys`, so a threshold
  firing too early can't pass silently), and a new fixture,
  `milestone_date_is_crossing_not_observation`, locks in the exact scenario
  the task exists to prevent: a 40-day arc where `effective_today` is two
  weeks past `p50`'s actual crossing date.
- An audit for the same mistake elsewhere in the codebase (the brief asked
  for this explicitly, since it was the second time the substitution had been
  made) turned up nothing else — the only other two `effectiveToday` usages
  (`TodayViewModel.kt`, `ArcRepository.kt`) are both legitimately about
  finding *today's* entries/checklist, not standing in for a computable date.

Task 12d's own definition of done required a `milestones` row to be
verifiably written with the engine's date, which meant building the `:app`
plumbing that didn't exist yet: `MilestoneDao.upsert` (conflict-safe,
`ON CONFLICT(arc_id, kind) DO UPDATE`, matching `EntryDao`'s established
pattern — `reflection`/`photo_path`/`acknowledged_at` deliberately excluded
from the update clause since they're user-written after the row exists), a
`MilestoneDao` dependency threaded into `ArcRepository`, and `reconcile()`
extended to diff `requiredMilestones` against what's already stored and
insert only the missing kinds, using the engine's date — never
`effectiveToday`.

## Part 4 — the handover

Building the milestone card next meant `ArcRepository` needed two more
methods (`observePendingMilestones`, `acknowledgeMilestone`). Checking in
about that — per the standing precedent of asking each time repository-layer
work comes up — is what prompted Priyanshu to stop splitting the task
mid-flight: "I hand over everything to you for now, so you can go ahead and
implement everything." Everything from here on — remix, the
reconcile-idempotency test, the milestone-card repository methods — was
implemented directly, scoped to finishing task 12 rather than as a standing
rule change.

## Part 5 — finishing task 12

**Milestone card.** `MilestoneDao` gained `observePendingByArc` (excludes
`start` — it anchors the recap timeline rather than being a card the user
dismisses, per the brief — ordered oldest-first) and `acknowledge` (sets
`reflection`/`acknowledged_at`). `TodayViewModel` combines `observeToday` and
`observePendingMilestones` into a single subscription (one `RulesEngine.resolve()`
per emission, not two), exposing `pendingMilestone: StateFlow<PendingMilestoneUiState?>`.
`TodayScreen` renders the card — full-screen, stock Material 3, "Day X of Y",
one optional reflection field, dismiss — on top of whatever else the screen
would otherwise show, whenever a pending milestone exists. `dayNumber` for
the card is computed from the engine's exact crossing date via
`ChronoUnit.DAYS.between`, deliberately not re-deriving anything the engine
already decided — just counting from a date it already supplied.

**`Ended` state, and a real bug found along the way.** `TodayUiState.Ended`
previously inferred "the arc is over" purely from date math
(`commitmentStatuses[today] == null` and `dayNumber != 0`) — it couldn't
distinguish completed from failed, carried no `failedOnDate`, and had no
remix entry point. Rewriting it to read `resolution.arcStatus`/`failedOnDate`
directly surfaced a genuine bug: a **strict arc that fails mid-arc** (well
before its end date) has `today` still comfortably inside the arc's date
range, so `commitmentStatuses[today]` is never null — the old check would
never have fired, and a terminal, failed arc (R9: "no further check-ins")
would have kept rendering as if still active. The new arcStatus-based check
fixes this directly. A regression test
(`strict arc with a sealed miss shows Ended as failed, before its end date`)
locks it in: a strict arc missing day one, sealed by day ten of thirty, now
correctly shows `Ended(completed = false, failedOnDate = 2026-09-01)` instead
of the normal checklist.

**Remix, end to end.** `ArcRepository.getRemixSource(arcId)` reads a source
arc's name and commitments directly off the Room entities (not through
`getCommitments()`, which maps to the domain `Commitment` and drops `icon` as
UI-only) and returns them as `RemixSource(name, commitments)` — deliberately
nothing else, since remix copies "name, commitments and rules, with fresh
dates" and explicitly not entries, days, milestones, or the intention.
`createArc()` gained a `remixedFromArcId: String? = null` parameter.
`AppRoute.CreateArc` became `data class CreateArc(val remixFromArcId: String? = null)`
so the in-memory nav switch could carry a payload without pulling in
`androidx.navigation`. `MainActivity` wires the Today screen's new
"Remix this arc" button (shown only when the ended arc failed — a completed
arc has nothing to retry) through to it. `CreateArcViewModel` takes
`remixFromArcId` via Koin's `parametersOf` (`koinViewModel(parameters = { parametersOf(remixFromArcId) })`)
and, when non-null, loads the source and pre-fills `name`/`commitments` in
its `init` block — dates and intention are left at their normal fresh
defaults.

**Idempotency, proven, not just asserted in a comment.** The brief explicitly
asked for "a test that reconciles twice and asserts one status write and one
milestone row per kind." Added to `ArcRepositoryTest`: reconcile once,
capture `completed_at` and every milestone row's `updated_at`; advance the
`FakeClock`; reconcile again; assert both are byte-for-byte unchanged. If a
second write had actually happened — not just been safely absorbed by the
unique index — this would catch it, since a real second write would move
`updated_at` even when the stored value doesn't change.

## Part 6 — proving it end to end

`./gradlew :domain:test` — 62 fixtures (56 at session start, plus the six
added this session: the crossing-vs-observation regression from 12d, and the
five originally-scoped cases — exact p25 boundary, several thresholds
crossed at once, a single-day arc crossing everything on day one, a failed
arc's `final` dated at the failure date rather than today or `end_date`, and
an arc not yet started requiring only `start`). `./gradlew :app:assembleDebug
:app:testDebugUnitTest` — clean, including the new idempotency test, the
mid-arc-failure regression, and a remix pre-fill test
(`CreateArcViewModelTest`) confirming name/commitments transfer but
intention and dates don't.

`CLAUDE.md` updated to match: R1–R12 (was R1–R11), 62 fixtures (was 45 — that
figure was already stale before this session found it), and the Compose UI
paragraph rewritten to describe the ended-arc state, the milestone card, and
remix, replacing the "whatever comes after arc creation" placeholder from
session 4.

## What exists now vs. before

**Before this session:** an arc that ran past its end date kept behaving as
active forever. Milestones didn't exist in any form — no engine field, no
table writes, no UI. `Resolution` had no opinion on arc lifecycle beyond
`arcStatus`/`failedOnDate`, which nothing downstream read.

**After this session:** `ArcRepository.reconcile()` — called from
`TodayViewModel.init` and `MainActivity.onResume()`, idempotent by
construction — persists arc status/`completed_at`/`failed_on_date` and
inserts newly-required milestone rows dated at their exact crossing day,
never at the day the app happened to check. The Today screen has a third
state for an ended arc, correctly distinguishing completed from failed
(including the mid-arc-failure case the old logic missed entirely), a
full-screen milestone card that appears at each threshold and dismisses
correctly, and a remix path that pre-fills a new arc from a failed one
without resurrecting the old arc or copying its history.

The one wrinkle worth remembering if R12 or its sibling rules get touched
again: a rule can be the thing that's wrong, not just the code implementing
it. The engine's shape changed twice in this session for the same field —
once based on a literal (but flawed) reading of R12, once after amending R12
itself — and the second change is the one that should stick. `rules.md` is
the source of truth precisely so a future review has one place to check
instead of re-litigating the question from the code.
