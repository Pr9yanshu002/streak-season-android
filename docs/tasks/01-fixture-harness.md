# Task: fixture test harness for the Arc domain module

## Context

Arc is being built twice — once in Kotlin/Compose (Android), once in
Swift/SwiftUI (iOS). The two codebases share no code. What keeps them in
agreement is a set of JSON fixtures that both apps load and assert against.

This task builds the Kotlin side of that harness. **It does not implement the
rules engine.** The engine is written by hand afterwards, against these tests.

Read these first — they are authoritative:

- `contract/rules.md` — rules R1–R14. If code and this document disagree, the
  code is wrong.
- `contract/schema.sql` — table and column names.
- `contract/fixtures/README.md` — the fixture JSON format and the list of 45
  cases.
- `contract/fixtures/*.json` — five worked examples.

## What to build

### 1. The `:domain` Gradle module

The module shell already exists (`domain/` with `build.gradle.kts`, included in
`settings.gradle.kts`). Configure it:

- `java-library` + `org.jetbrains.kotlin.jvm` plugins. **Not**
  `com.android.library`.
- No Android dependencies of any kind. `import android.*` must not compile.
- Dependencies: `kotlinx-serialization-json`, and JUnit 5 for tests. Nothing else.
- This project uses a version catalog (`gradle/libs.versions.toml`). Add all
  versions and libraries there, not inline in the build file. The
  kotlin-serialization plugin version must use `version.ref = "kotlin"` so it
  matches the project's Kotlin version exactly.
- Add `implementation(project(":domain"))` to `:app`. Never the reverse.

### 2. Domain models

Kotlin data classes mirroring `schema.sql`, in package `com.arc.domain.model`.

- `Arc`, `Commitment`, `Day`, `Entry`
- Enums: `ArcStatus`, `Strictness`, `CommitmentType`, `ScheduleType`, `DayState`
- Dates are `java.time.LocalDate`. Instants are `Long` epoch millis.
- Column names in the DB are snake_case; Kotlin properties are camelCase. Map
  explicitly with `@SerialName` — do not rely on a global naming strategy.

These are domain models, not persistence models. No Room annotations here.

### 3. Fixture loading

- A `Fixture` data class matching the JSON in `fixtures/README.md`.
- `FixtureLoader` that reads every `*.json` from test resources and
  deserializes it. `README.md` is not a fixture — skip non-JSON files.
- Every key inside `expect` is optional. Model them as nullable and assert
  only what is present.
- `now` in a fixture is a local datetime with no offset — parse as
  `LocalDateTime`, never `Instant`.

### 4. Copy task

A Gradle task that copies `contract/fixtures/*.json` into
`domain/src/test/resources/fixtures/`, wired to run before `test`. The contract
folder is the source of truth; the copy is generated and should be gitignored.

### 5. The engine's public surface (signatures only)

In `com.arc.domain.rules`:

```kotlin
object RulesEngine {
    fun resolve(
        arc: Arc,
        commitments: List<Commitment>,
        days: List<Day>,
        now: LocalDateTime,
    ): Resolution = TODO("not implemented")
}

data class Resolution(
    val effectiveToday: LocalDate,
    val dayStates: Map<LocalDate, DayState>,
    val currentStreak: Int,
    val bestStreak: Int,
    val restTokensRemaining: Map<String, Int>,  // ISO week key, e.g. "2026-W03"
    val completionPct: Double,
    val arcStatus: ArcStatus,
    val failedOnDate: LocalDate?,
    val sealedDates: Set<LocalDate>,
)
```

Stub only. `TODO()` in the body. Do not implement any rule.

### 6. The test factory

A JUnit 5 `@TestFactory` that produces one dynamic test per fixture, named
after the fixture's `id` so failures are readable in the test panel.

Each test calls `RulesEngine.resolve(...)` and asserts every non-null field in
`expect`. Assertion messages must name the fixture and the field — a failure
should be diagnosable without opening the JSON.

`completionPct` compares to 4 decimal places, round half up. Use `BigDecimal`
with `RoundingMode.HALF_UP`, not `String.format` and not `Math.round`.

## Constraints

- **Do not implement the rules engine.** Signatures and `TODO()` only.
- **The engine must never read the clock.** No `LocalDate.now()`,
  `System.currentTimeMillis()`, or `Clock.systemDefaultZone()` anywhere in
  `:domain`. `now` is always a parameter.
- No abstraction that isn't used right now. No repository interfaces, no
  use-case classes, no DI. This module is data classes and functions.
- Prefer explicit over clever. This code gets rewritten in Swift by hand, so
  it should be readable by someone translating it line by line.

## Definition of done

`./gradlew :domain:test` runs and **every fixture fails** with
`NotImplementedError`, each listed by name in the test output.

Only 5 fixtures exist right now (the worked examples). The remaining 40 are
written in a later task. 5 red tests is the correct outcome here — the harness
is what is being verified, not coverage.

## Report back

When finished, list:
- the files created
- how many fixtures were loaded
- anything in the fixture format that was ambiguous or that the format could
  not express