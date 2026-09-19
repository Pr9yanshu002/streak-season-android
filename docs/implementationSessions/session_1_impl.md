# Session 1 — building the persistence layer

This is a walkthrough of everything built in this session, written for
someone who has never touched Kotlin or Android development before. It
explains not just *what* we did but *what each piece of jargon means* the
first time it shows up.

## The starting point

Before this session, the project had two pieces working:

1. **The rules engine** (`:domain` module) — the code that decides things
   like "is this day complete, missed, or a rest day?" and "what's the
   user's current streak?" This was already fully built and tested (45
   automated test cases, all passing).
2. **A blank Android app shell** — a launchable app with no real screens,
   just a placeholder "Hello Android" text.

What was completely missing: anywhere to actually **save data**. If you
closed the app, everything would vanish, because nothing was being written
to a database yet. That's what this session built: the layer that saves
arcs, commitments, and daily check-ins to a real on-device database, and
reads them back.

This corresponds to a written task brief in the project:
`docs/tasks/07-room-persistence.md`.

## Some Kotlin/Android vocabulary, up front

A few concepts come up constantly below, so here they are once:

- **Kotlin** is the programming language this whole app is written in
  (the iOS twin of this app is written in Swift instead — different
  language, same product).
- A **module** is a self-contained chunk of the project that compiles
  somewhat independently. This project has two: `:domain` (the rules
  engine, pure logic, nothing Android-specific) and `:app` (the actual
  Android app — screens, database, everything Android-specific).
- A **class** bundles related data and behavior together, like a blueprint.
  A **data class** is Kotlin's shorthand for a class that's *just* a bundle
  of values (like a struct) — Kotlin automatically writes the boring
  boilerplate for you (equality checks, printing, copying).
- An **interface** describes a *shape* of behavior without saying how it's
  done — "anything that claims to be a `Clock` must have a `now()` method,"
  full stop, no implementation. Different classes can then implement that
  interface in different ways (a real clock vs. a fake one for tests — more
  on this below).
- **Annotations** are the `@Something` labels you'll see above classes and
  functions everywhere in this code (`@Entity`, `@Dao`, `@Insert`, etc.).
  They don't do anything by themselves — they're instructions read by a
  code-generation tool at build time, which writes real working code based
  on them. This project leans on that heavily (see "Room" below).
- A **suspend function** is Kotlin's way of marking "this function might
  take a while (like a database read), so don't freeze the whole app while
  it runs." You can only call a suspend function from certain places that
  know how to run it in the background.
- A **`Flow`** is a stream of values over time, instead of just one value.
  Instead of asking the database "what's the current state right now?"
  once, a `Flow` says "tell me the current state now, *and* every time it
  changes after this." The UI can subscribe to a `Flow` and automatically
  redraw itself whenever the underlying data changes.
- A **nullable type**, written with a `?` (e.g. `String?`), means "this
  might legitimately have no value." `String` must always have a value;
  `String?` might be empty/absent. Kotlin forces you to handle the "what if
  it's absent" case everywhere a `?` shows up, which is how it catches a
  huge class of crashes before the app ever runs.

## Part 1 — Room: the database library

Android apps almost never talk to a database with raw SQL by hand. Instead,
most use **Room**, Google's official library for this. You describe your
tables as Kotlin classes with annotations, and Room generates all the
actual database code for you at build time.

### 1a. Entities — describing the tables

The project already had a file, `contract/schema.sql`, that is the
**source of truth** for what the database should look like (which is
shared with the iOS team too, so both apps agree on structure). It defines
five tables: `arcs`, `commitments`, `days`, `entries`, `milestones`.

For each table, we wrote a Kotlin **entity**: a data class annotated with
`@Entity` that tells Room "this class represents a database table." For
example (trimmed down), the arcs table became:

```kotlin
@Entity(tableName = "arcs", ...)
data class ArcEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    ...
)
```

A few things to notice, because they matter for beginners:

- `@PrimaryKey` marks the column that uniquely identifies each row (like a
  row's "ID card"). Every table here uses a randomly generated ID string
  (a UUID), not an auto-incrementing number — this is deliberate, so that
  two different phones can create records offline without ever colliding
  when they eventually sync.
- `@ColumnInfo(name = "start_date")` exists because Kotlin naming
  convention is `camelCase` (`startDate`) but SQL naming convention here is
  `snake_case` (`start_date`). This annotation tells Room "the Kotlin
  property is called `startDate`, but store/read it under the column name
  `start_date`," so the two conventions can coexist without a mismatch.
- Dates are stored as plain text (`"2026-09-11"`), not as some special date
  type — the schema comment explains why: if you store a date as a precise
  timestamp and then fly across time zones, the "day" it refers to can
  silently shift. Storing `'YYYY-MM-DD'` as text sidesteps that entirely.
- Nothing is ever truly deleted. Every table has a `deleted_at` column;
  "deleting" a row just means setting that column to the current time.
  Every table also has `updated_at`, bumped on every write. This is a
  project-wide rule (see the project's `CLAUDE.md`), and it matters because
  a future sync engine will need to know what changed and when — hard-
  deleting a row would erase that history.

We wrote five of these: `ArcEntity`, `CommitmentEntity`, `DayEntity`,
`EntryEntity`, `MilestoneEntity` — one per table, living in
`app/src/main/java/dev/priyanshu/arc/data/entity/`.

**One wrinkle worth explaining:** `schema.sql` declares a couple of
indexes (an index is a structure that makes a specific kind of database
lookup fast) with a `WHERE deleted_at IS NULL` condition attached — meaning
"only index the non-deleted rows." Room's annotation for indexes doesn't
support that conditional form. So those became plain, unconditional
indexes instead — functionally very slightly weaker (they also index
soft-deleted rows), but not incorrect, and it's called out in a comment in
the code so nobody's surprised by it later.

### 1b. DAOs — the actual queries

An entity just describes a table's *shape*. A **DAO** ("Data Access
Object") is where you write the actual questions you want to ask the
database — "give me this arc," "insert this entry," "give me every
commitment for this arc." Room takes a DAO — which is just an `interface`
with annotated methods — and generates the real, working SQL-executing code
for it automatically.

Example, `ArcDao`:

```kotlin
@Dao
interface ArcDao {
    @Insert
    suspend fun insert(arc: ArcEntity)

    @Query("SELECT * FROM arcs WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<ArcEntity?>
}
```

- `@Insert` is a built-in shortcut — Room already knows how to write an
  `INSERT` statement from the entity you hand it, no SQL needed.
  `insert` is `suspend` because writing to a database takes a moment and
  shouldn't block the app.
- `@Query` is for anything more specific — you write the actual SQL
  yourself, and Room checks it at compile time (misspell a column name and
  the *build itself* fails, not just the app at runtime).
- `observeById` returns a `Flow<ArcEntity?>` — a live stream that
  automatically emits a new value every time that row changes in the
  database. This is what eventually lets a screen "just update" when the
  data underneath it changes, with no manual refresh logic.
- **Every single read query filters `deleted_at IS NULL`.** This was called
  out as the highest-risk part of the whole task: if even one query forgets
  this filter, "deleted" rows start silently reappearing and every
  computed result built from them becomes wrong. So it's baked into the
  SQL text itself in every DAO, not bolted on afterward in Kotlin.
- There is deliberately **no delete method** anywhere. "Deleting" a row is
  just an `UPDATE` that sets `deleted_at` and `updated_at` — consistent
  with the "nothing is hard-deleted" rule above.

We wrote five DAOs, one per entity, in
`app/src/main/java/dev/priyanshu/arc/data/dao/`.

### 1c. AppDatabase — tying it together

One more small file, `AppDatabase.kt`, is the class that actually
represents "the database" as a whole — it lists all five entities and
exposes all five DAOs. This is the thing Room turns into a real,
openable `.db` file on the device.

### 1d. Mappers — translating between two worlds

Here's a subtlety that trips people up: the **entity** classes
(`ArcEntity`, etc.) are *not* the same classes the rules engine uses. The
rules engine (in `:domain`) works with its own, simpler classes — `Arc`,
`Commitment`, `Day`, `Entry` — that only carry what the *rules* need to
know, using proper Kotlin types like `LocalDate` (an actual date object)
instead of raw text, and enums (a fixed set of named options, like
`Strictness.STRICT` / `STANDARD` / `CHILL`) instead of raw strings.

So we need small translator functions — **mappers** — that convert a
database entity into the shape the rules engine expects. For example:

```kotlin
fun ArcEntity.toDomain(): Arc = Arc(
    id = id,
    name = name,
    startDate = LocalDate.parse(startDate),      // text "2026-09-11" -> a real date
    status = ArcStatus.valueOf(status.uppercase()), // text "active" -> ArcStatus.ACTIVE
    ...
)
```

(This `fun ArcEntity.toDomain()` syntax is called an **extension
function** — Kotlin lets you "attach" a new method onto an existing class
from outside it, without editing that class's original file. It reads
almost like a normal method call: `someArcEntity.toDomain()`.)

One genuine complication surfaced here: the rules engine's `Day` object
holds its list of check-ins (`entries`) *nested inside it*, but the
database splits those into two separate tables (`days` and `entries`).
So the day mapper can't just be a simple one-line conversion — it has to
be handed the matching list of entries from the caller:

```kotlin
fun DayEntity.toDomain(entries: List<EntryEntity>): Day = ...
```

This kind of mismatch — "the database shape and the domain-logic shape
don't line up 1-to-1" — is exactly the kind of thing worth writing down for
whoever eventually builds the iOS side, since they'll hit the same
translation problem.

## Part 2 — the Clock: making "today" testable

The rules engine has a strict rule: it is never allowed to check the
device's real clock itself (no "what time is it right now?" calls buried
inside it). Instead, the *caller* always hands it "what time is `now`,
as far as you're concerned." This sounds like a small detail but it's
what makes the whole engine testable — a test can say "pretend it's
3:59am" or "pretend it's exactly one second after the arc's day boundary"
without needing to actually wait until those times or change the device's
clock.

To support that from `:app`'s side, we defined:

```kotlin
interface Clock {
    fun now(): LocalDateTime
    fun millis(): Long
}

class SystemClock : Clock {
    override fun now(): LocalDateTime = LocalDateTime.now()
    override fun millis(): Long = System.currentTimeMillis()
}
```

`SystemClock` is the real one, used by the actual running app. Separately,
in the test-only part of the project, there's a `FakeClock` that starts at
whatever fixed moment a test hands it and can be manually moved forward —
so a test can pin "now" to any exact instant and check the engine's
behavior around tricky edge cases (like the 4am day-boundary rule) without
any real waiting.

This pattern — define an `interface`, write one real implementation and
one fake/test implementation — is extremely common in testable software
design generally, not just this project. It's usually called **dependency
injection**: instead of a class reaching out and grabbing "the real clock"
itself, the real or fake clock is handed to it ("injected") from outside.

## Part 3 — ArcRepository: the one door into the database

Nothing else in the app is supposed to talk to the DAOs directly. Instead,
there's a single `ArcRepository` class that:

1. Loads an arc, plus its commitments, days, and entries, from the five
   DAOs.
2. Converts all of it from entity shape to domain shape (via the mappers).
3. Hands it all to `RulesEngine.resolve(...)`, along with `clock.now()`.
4. Returns the result as a live `Flow`, so the UI can watch it and
   automatically update whenever anything underneath changes.

```kotlin
fun observeResolution(arcId: String): Flow<Resolution> =
    combine(
        arcDao.observeById(arcId),
        commitmentDao.observeByArc(arcId),
        dayDao.observeByArc(arcId),
        entryDao.observeByArc(arcId),
    ) { arc, commitments, days, entries ->
        RulesEngine.resolve(...)
    }
```

`combine` here is what lets four separate live streams (arc, commitments,
days, entries) get merged into one: any time *any one* of those four
changes, the whole thing recomputes and emits a fresh result. Nothing is
cached — the rules engine is cheap enough to just re-run on every change
rather than trying to cleverly patch a previous result.

The repository also exposes the handful of "write" actions the app
actually needs right now: `createArc(...)`, `recordEntry(...)`,
`undoEntry(...)`, `setNote(...)`. Deliberately, it does *not* expose a
full generic CRUD surface (create/read/update/delete for everything) —
only exactly what's needed today, per the project's general "don't build
things speculatively" style.

**Worth flagging honestly:** the project's own ground rules
(`CLAUDE.md`) reserve the repository layer specifically for the project
owner to write, with Claude reviewing only — this was missed while
following the task brief (which itself asked for the repository as a
deliverable), and got caught and flagged only after the fact. It's staying
in place for now, by the owner's choice, pending his review — but it's
worth being upfront that this shouldn't have been written without checking
first.

## Part 4 — Koin: wiring everything together

All these pieces (`AppDatabase`, five DAOs, `Clock`, `ArcRepository`) need
to be constructed once and shared across the whole app, rather than each
screen creating its own separate database connection. This is another
classic **dependency injection** problem, solved here with a library
called **Koin**.

A Koin "module" is basically a recipe list — "here's how to build each
thing, and here's what it needs":

```kotlin
val databaseModule = module {
    single { Room.databaseBuilder(...).build() }
    single { get<AppDatabase>().arcDao() }
    ...
}
```

`single { ... }` means "build exactly one instance of this and reuse it
everywhere" (as opposed to building a fresh one every time something asks
for it). `get<AppDatabase>()` means "ask Koin for the `AppDatabase`
instance it already built, and use it here."

Koin gets started once, when the app first launches, in a small new
`StreakSeasonApp` class (an `Application` subclass — the object Android
creates once per app process, before any screen exists) which is now
registered in `AndroidManifest.xml`.

## Part 5 — proving it actually works

Per the task brief, one automated test was written:
`ArcRepositoryTest.kt`. It:

1. Creates an in-memory database (a real, working database that just lives
   in RAM and disappears when the test ends — perfect for tests, since
   nothing needs cleaning up afterward).
2. Creates an arc with two commitments through the repository's public
   `createArc(...)` method — exactly the way real app code would.
3. Records check-in entries across several different days (some complete,
   some partial, one day left untouched entirely, and "today" left
   half-finished).
4. Reads the result back out through `observeResolution(...)`.
5. Separately, builds the exact same arc/commitments/days as plain
   in-memory objects and asks `RulesEngine.resolve(...)` directly what
   *it* thinks the answer should be.
6. Asserts the two answers match exactly.

The point of step 5 isn't to re-test the rules engine's logic (that's
already covered by 45 separate tests in `:domain`) — it's to prove that
the database, the mappers, and the repository don't lose or corrupt
anything on the way from "user taps a button" to "rules engine sees the
data." This test runs using **Robolectric**, a tool that simulates just
enough of an Android device that Android-flavored code (like a Room
database) can run directly on a regular computer during a test, with no
emulator or physical phone required.

This test passed. Separately, the original 45 rules-engine tests were
re-run and still all pass — confirming the persistence work didn't
accidentally change any engine behavior.

## Part 6 — the build headaches (and why they happened)

A couple of things broke the build that had nothing to do with the actual
code written above — worth explaining since they can look alarming out of
context:

1. **A version conflict between two build tools.** Room needs a
   code-generation tool called **KSP** to turn all those `@Entity`/`@Dao`
   annotations into real working code at build time. The very newest
   version of Android's build system (AGP) changed how it manages
   generated code internally, and the current version of KSP hasn't fully
   caught up yet — it's a known, open, unresolved bug in KSP itself
   ([tracked here](https://github.com/google/ksp/issues/2729)). The
   documented, official-workaround fix is one line in `gradle.properties`;
   that's what was applied, with a comment linking the bug so nobody's
   confused by it later if it resurfaces.
2. **An unrelated dependency version mismatch.** A couple of the app's
   pre-existing libraries had quietly been updated to versions that
   require targeting a newer Android API level (37) than the project was
   configured for (36). This had nothing to do with Room or this task — it
   would have blocked *any* build of the app module — but it needed fixing
   to get anything running at all, so `compileSdk` was bumped from 36 to
   37.

## What exists now vs. before

**Before this session:** a rules engine with no way to save or load
anything, and an empty app shell.

**After this session:** a full, tested path from "user does something" all
the way down to "a real SQLite database file on the device" and back up
through "the rules engine computes the current state" — with nothing yet
built to *display* that state. The only piece left for the vertical slice
is the actual screens (Compose UI) that call into `ArcRepository` and show
something on screen.
