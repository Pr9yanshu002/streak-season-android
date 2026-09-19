# Task: persistence layer — Room, mappers, repository

## Context

The rules engine is complete: 45/45 fixtures green. `RulesEngine.resolve()` is a
pure function taking an `Arc`, its commitments, days and entries, plus a `now`,
and returning a `Resolution`.

Nothing loads or saves anything yet. This task builds the layer that does.

All of it lives in `:app`. `:domain` is not touched.

Read `contract/schema.sql` first — it is authoritative for table names, column
names and types. Room must produce a database that matches it exactly.

## What to build

### 1. Entities

One `@Entity` per table in `schema.sql`: `arcs`, `commitments`, `entries`,
`days`, `milestones`.

- `@Entity(tableName = "arcs")` etc. — snake_case table names, matching the SQL.
- `@ColumnInfo(name = "start_date")` on every property whose Kotlin name differs.
  Do not rely on a global naming convention.
- Types match the SQL: dates are `String` here, not `LocalDate`. Timestamps are
  `Long`. Booleans are `Int` (0/1).
- Indices as declared in `schema.sql`, including the `UNIQUE` constraints on
  `(commitment_id, date)`, `(arc_id, date)` and `(arc_id, kind)`.
- These are persistence types. No business logic, no `LocalDate`, no enums.

Enable Room schema export and commit the generated JSON. It is the artifact that
proves the Room schema matches `schema.sql`.

### 2. DAOs

One DAO per entity. Only the queries needed right now — do not write a full CRUD
surface speculatively.

**Every read query must filter `deleted_at IS NULL`.** This is the highest-risk
part of this task: a missing filter means soft-deleted commitments come back and
every day state the engine computes is silently wrong. Put the filter in the
query itself, not in Kotlin after the fact.

Writes:
- No `@Delete`. Deletion is `UPDATE ... SET deleted_at = :now, updated_at = :now`.
- Every update sets `updated_at`. There is no code path that writes a row without
  bumping it.

### 3. Type converters and mappers

Two distinct things, keep them separate:

- **Room `@TypeConverter`s** — only if a property type genuinely needs one. Since
  entities use `String`/`Long`/`Int` throughout, you may need none. Prefer none.
- **Mappers** — `ArcEntity.toDomain(): Arc` and friends, in
  `app/data/mapper/`. This is where `"2026-01-12"` becomes `LocalDate`,
  `"strict"` becomes `Strictness.STRICT`, and `1` becomes `true`.

Mapping is one-directional for now: entity → domain for reads, and domain values
→ entity fields at the point of writing. Do not build a symmetric
`Arc.toEntity()` until something needs it.

### 4. Clock

The engine never reads the clock, so `:app` supplies `now`.

```kotlin
interface Clock {
    fun now(): LocalDateTime
}
```

A real implementation using the system zone, and a `FakeClock` in test source
that returns a fixed value and can be advanced. This is the only way to test
behaviour around the 4am boundary without changing the device date.

Everything that needs the current time takes a `Clock`. Nothing calls
`LocalDateTime.now()` directly outside the real implementation.

### 5. Repository

One `ArcRepository` in `app/data/`. Its job is to assemble engine inputs and
expose the result.

```kotlin
fun observeResolution(arcId: String): Flow<Resolution>
```

Loads the arc, its live commitments, days and entries; maps them to domain
types; calls `RulesEngine.resolve(...)` with `clock.now()`; emits.

Recompute on every data change. Do not cache the `Resolution` — the engine
operates on in-memory lists and is fast. Add caching only if something is
measurably slow, which it will not be at this scale.

Plus the writes the vertical slice needs: create an arc with its commitments,
record an entry, undo an entry, set a note.

### 6. DI

Koin, not Hilt. Less ceremony for a project this size and no annotation
processing. Modules for the database, DAOs, repository and clock.

## Constraints

- **No sync code.** The schema carries `synced_at` and soft deletes so sync is
  possible later. No sync engine, no `SyncService`, no upload queue, no network
  dependency. Not a stub, not an interface.
- **No Supabase, no network layer at all.**
- `:domain` is not modified. If the engine appears to need a change, stop and
  report it.
- Dependencies point one way: `:app` → `:domain`.
- Room queries live in DAOs. No SQL anywhere else.
- No repository interface with a single implementation. Concrete class.
- Suspend functions and `Flow` for DAO access. Room provides both natively.

## Definition of done

- `./gradlew :domain:test` still 45/45 green. This task cannot affect it, and if
  it does, something was changed that should not have been.
- The app compiles and the database is created on first run.
- An instrumented or Robolectric test that: inserts an arc with two commitments,
  records entries across several days, reads back through the repository, and
  asserts the `Resolution` matches what the rules predict. One test is enough —
  this is proving the wiring, not re-testing the engine.
- The exported Room schema JSON is committed.

## Report back

- Files created, grouped by layer.
- **A diff of the exported Room schema against `contract/schema.sql`** — every
  table, column, type, index and constraint. Any difference is a bug in the
  entities, not in the SQL. List them explicitly even if the answer is "none".
- Confirmation that every read query filters `deleted_at IS NULL`, listed query
  by query.
- Anything in `schema.sql` that Room could not express directly, and how it was
  handled. `CHECK` constraints are the likely case — note what was done about
  them.
- Any place where the domain model shape made the mapping awkward. That is a
  signal the model may need adjusting before the iOS port, and it is worth
  hearing early.