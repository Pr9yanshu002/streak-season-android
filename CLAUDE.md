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
- Rules are numbered R1–R14. Reference them in comments and commit messages.
- `contract/` is a plain folder, not a Gradle module. Do not add it to
  `settings.gradle.kts`.

## Hard rules

- **Never hard-delete.** Set `deleted_at`. This applies to every table.
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
is fully implemented (R1–R11) and all 45 fixtures in `contract/fixtures/`
pass. The rules engine itself is done.

The persistence layer (`docs/tasks/07-room-persistence.md`) is also done:
Room entities/DAOs, mappers, `Clock`/`SystemClock`/`FakeClock`, `ArcRepository`,
and Koin DI are all in `:app/data` and `:app/di`, schema-verified against
`contract/schema.sql` with no differences, `ArcRepositoryTest` passing.

Compose UI: the Today screen (`docs/tasks/08-today-screen.md`) and the arc
creation flow with empty state (`docs/tasks/11-arc-creation.md`) are both
done — `ui/today`, `ui/create`, and a two-route `AppRoute` switch in
`MainActivity` (no `androidx.navigation` dependency; add one when a third
route makes the in-memory switch stop being enough). `DebugSeeder` only runs
in debug builds and only when no arc exists yet, so it never clobbers an arc
created through the UI. Remaining Compose work is whatever comes after arc
creation (see `docs/tasks/` for the next brief).