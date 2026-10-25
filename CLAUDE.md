# Streak Season — Android

A time-boxed self-improvement app. The user defines an "arc": a chapter with a
start date, an end date, a set of daily commitments, and a rule set. When the
arc ends they get a recap.

This app is being built twice — this repo (Kotlin/Compose) and `streak-season-ios`
(Swift/SwiftUI). The two codebases share **no code**. They are kept in agreement
by a set of JSON fixtures that both apps load and assert against.

Note: the product is called "Streak Season", but the domain noun in code is
**arc**. Do not rename it.

## Authority

`contract/rules.md` and `contract/schema.sql` are the source of truth.

- Never invent a rule. If behaviour is unspecified, stop and ask.
- If code and `rules.md` disagree, the code is wrong.
- Rules are numbered R1–R18. Reference them in comments and commit messages.
- `contract/` is a plain folder, not a Gradle module. Do not add it to
  `settings.gradle.kts`.

## Hard rules

- **Never hard-delete.** Set `deleted_at`. This applies to every table.
- **Soft-deleted rows still occupy unique indexes.** Any table with both
  `deleted_at` and a UNIQUE constraint must write via
  `INSERT … ON CONFLICT(<unique cols>) DO UPDATE … SET deleted_at = NULL`,
  never a filtered read followed by a plain insert. Room's `@Upsert` cannot be
  used for these — it matches on the primary key, which is a fresh UUID.
  Affects: entries, days, milestones.
- **Every write bumps `updated_at`.** No exceptions.
- **The rules engine never reads the clock.** No `LocalDate.now()`,
  `System.currentTimeMillis()`, or `Clock.systemDefaultZone()` anywhere in
  `:domain`. `now` is always a parameter. The build will not catch this — it is
  the one thing that has to be watched by hand.
- **`:domain` has no Android dependencies.** It is a `java-library` +
  `kotlin-jvm` module. If something needs `Context`, it belongs in `:app`.
- **Dependencies point one way:** `:app` → `:domain`. Never the reverse.
- **Derived data is never stored.** Day state, streaks, rest-token consumption
  and completion percentage are computed by the engine, not persisted.
- **Dates are `LocalDate` in the domain, `TEXT` ('YYYY-MM-DD') in the DB.**
  Convert at the mapper in `:app`. Never store or pass calendar dates as
  timestamps.

## Module layout

```
app/        Compose UI, Room, DI, notifications, widgets. Full Android.
domain/     Rules engine and models. Pure Kotlin. ~10% of the codebase.
contract/   schema.sql, rules.md, fixtures/. Shared with the iOS repo.
docs/       Task briefs and learning notes.
```

`:domain` should stay small. If it exceeds roughly a dozen files, something has
leaked in that belongs in `:app`.

`:domain`'s package root is `dev.priyanshu.arc.domain` (matches the app's
`dev.priyanshu.arc` namespace) — not `com.arc.domain`.

Not in `:domain`: repository interfaces, use-case classes, DI, coroutines.
The engine is a synchronous function over in-memory lists.

## Who writes what

- **Priyanshu writes, Claude reviews only:** the rules engine (`domain/rules/`),
  the repository layer, anything where the Kotlin/Swift comparison is the point.
  Do not implement these even if asked to "finish" something — ask first.
- **Claude writes, Priyanshu reads:** Gradle config, Room DAOs and entities,
  mappers, DI setup, navigation scaffolding, test harness.
- **Claude writes freely:** fixtures, and the Postgres translation later.

## Git workflow

- Never commit to `main`. Every change happens on a branch and lands via PR.
- Branch name: `task-<nn>-<short-slug>`, matching the brief in `docs/tasks/`
  (e.g. `task-13-closeout-cleanup`).
- Before starting a task: `git checkout main && git pull`, then create the branch.
- Commit messages start with the task number: `Task 13: ...`.
- At the end of every task, write the "Report back" section to
  `docs/implementationSessions/task-<nn>-report.md` and commit it with the
  task's other changes, before opening the PR.
- Open the PR with `gh pr create --base main`. Do not merge it — Priyanshu
  reviews and merges.

## Style

- Pragmatic over pure. No abstraction without a current use.
- No interface with one implementation unless it is being mocked today.
- Explicit over clever. This code gets translated to Swift by hand, so it should
  be readable by someone working line by line.
- Explain expected behaviour before writing code for an unfamiliar system.
- Plain language in comments and explanations.

## Current phase

Phase 1 — Android vertical slice. Local Room DB only. No Supabase, no sync code,
no network. The schema is sync-ready (UUIDs, `updated_at`/`deleted_at`/
`synced_at`, soft deletes) but there is no sync engine and none should be added.

Deferred, do not build: blueprints, multiple progress metaphors (tree only),
stats tab, arc pause, "N times per week" scheduling, video recap, web.

The `:domain` fixture test harness is built (`RulesEngineFixtureTest`, one
JUnit 5 dynamic test per fixture in `contract/fixtures/`, copied into test
resources by the `:domain:copyFixtures` Gradle task). `RulesEngine.resolve()`
is fully implemented (R1–R12) and all 62 fixtures in `contract/fixtures/`
pass. The rules engine itself is done.

The persistence layer (`docs/tasks/07-room-persistence.md`) is also done:
Room entities/DAOs, mappers, `Clock`/`SystemClock`/`FakeClock`, `ArcRepository`,
and Koin DI are all in `:app/data` and `:app/di`, schema-verified against
`contract/schema.sql` with no differences, `ArcRepositoryTest` passing.

Compose UI: the Today screen (`docs/tasks/08-today-screen.md`), the arc
creation flow with empty state (`docs/tasks/11-arc-creation.md`), and arc
lifecycle + milestones (`docs/tasks/12-lifecycle-milestones.md`, amended by
`12d-r12-amend-milestone-dates.md`) are all done. Arc status/completion
(R9/R10) and milestones (R12) reconcile from the engine into Room on Today
load and app resume (`ArcRepository.reconcile`, idempotent). Milestones are
computed by the engine as an exact `Map<MilestoneKind, LocalDate>` (dated at
the threshold's crossing day, never at the day the app happened to check) and
persisted with a conflict-safe upsert. The Today screen has a third case for
an ended arc (completed or failed, with a Remix option pre-filling the create
flow on a failed one) and a full-screen milestone acknowledgment card.
`ui/today`, `ui/create`, and an `AppRoute` switch in `MainActivity` (no
`androidx.navigation` dependency; `CreateArc` carries an optional remix source
arc id as a payload). There is a third route, `AppRoute.DebugMenu`, but it is
debug-only — reached via a 🐞 FAB shown only when the manifest's `debuggable`
flag is set — so it deliberately does not count as the "real nav graph" trigger:
it never ships. `TodayRoute` drives `TodayViewModel.refresh()`/`stopObserving()`
from a `DisposableEffect` (there's no nav library, so `koinViewModel()` caches
one instance per process — entering re-reads the active arc, leaving cancels the
subscription so a debug reseed's `clearAllTables()` can't crash a backgrounded
Today). `DebugSeeder` has two paths, both debug-only: `seedIfNeeded()`
auto-seeds on first launch, idempotent, bails if any arc exists so it never
clobbers a UI-created arc; `seed(scenario)` is the debug menu's manual reseed,
which explicitly wipes every table and reseeds exactly one of six named
`DebugScenario`s (empty, not-started, active-mid-arc, milestone-pending,
ended-completed, ended-failed). `docs/architecture.md` is a from-scratch
architecture walkthrough written for the eventual Swift port.

The design system (`docs/tasks/16-design-system-theme.md`) is now in the theme.
Its source of truth is `contract/design/tokens.json` (+ `README.md`). `ui/theme/`
holds `StreakTheme` (replacing the template `StreakSeasonTheme`): Material 3
`ColorScheme`s mapped from the tokens, a `Bricolage`/`Literata` `Typography`,
`StreakShapes`, a `Space` object, and the non-Material tokens (`missed`, `rest`,
the day-state colours, `ss-voice`) exposed via `StreakTheme.colors` /
`StreakTheme.type` through static `CompositionLocal`s. Dynamic colour is gone.
Static font cuts live in `res/font/` (`ss-title` uses 600, not tokens' 650).
`ThemePreview.kt` renders every token in both themes. The task changed only the
theme — no screen layout — so screens built against the template now show the
new palette/fonts but still need their own redesign tasks.

Rest days (`docs/tasks/17-rest-day.md`) are done. R5/R6/R11 were amended: rest
is checked before pending, so flagging an unfinished today takes the week's
token immediately and finishing everything anyway returns it. Tokens are never
applied automatically. In the engine, `applyRestTokens` treats a provisional
`PENDING` day as token-eligible, and `isoWeekKey` is public so `:app` can read
this week's `restTokensRemaining`. Today shows a rest-day row
(`TodayUiState.Loaded.restDayRow`: OFFER / RESTING / none) driving
`TodayViewModel.setRestDay` → `ArcRepository.setRestTokenUsed`. `DayDao.upsert`
now revives a soft-deleted day row instead of `DO NOTHING`. Open finding (R7,
not fixed): repository writes don't check sealing, and with `backfill_days = 0`
a Today screen left open across the day boundary can write to a sealed date.

The Today redesign (`docs/tasks/18-today-redesign.md`, designs in
`docs/design/today/`) is done. Reusable, stateless pieces live in
`ui/components/` (`DayDisc`, `WeekStrip`, `StreakPill`, `BonusChip`, the
commitment cards, `RestDayCard`, `ProgressPie`, `ReflectionField`,
`LargeButton`).

The create-arc redesign (task 19) is done. The flow has five steps (name
and dates, commitments, strictness, intention, review); Next is always
enabled and a blocked Next shows that step's errors (`showErrors`). The
commitment editor is a full-screen view driven by the ViewModel's `editing`
state, and Review's Edit buttons jump back with `goToStep`. The create
flow's own parts (`StepHeader`, `StepBottomBar`, the field parts,
`OptionCard`, `DayPicker`, `StepperRow`, `ReviewCard`…) are in
`ui/create/CreateArcParts.kt`; shared date/day words are in
`ui/create/CreateArcText.kt`. App-only limits (10 commitments, 7 rest days)
and known issues are in `docs/implementationSessions/task-19-notes.md`; see
also `task-19-report.md`.

Remaining Compose work is whatever comes next (see `docs/tasks/` for the next
brief).