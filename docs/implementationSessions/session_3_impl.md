# Session 3 — two new rules, closing the fixture-contract gap, a real ViewModel, and fixing a write race

A walkthrough of everything built in this session, in the same spirit as
sessions 1 and 2. This session worked through two written task briefs back
to back — `docs/tasks/09-r15-r16-and-fixes.md` and
`docs/tasks/10-fix-entry-write-race.md` — both aimed at cleaning up debt
flagged during session 2, before arc creation (the next real feature) adds
more surface area on top of it.

## The starting point

Session 2 left the Today screen fully working, with `Resolution` properly
exposing `dayNumber`, `totalDays`, and per-commitment status — but it also
left an honest list of loose ends:

1. Two rules the product needed (`R15`, `R16`) existed only as an idea, not
   as written rules or code.
2. Seven of `Resolution`'s new behaviors were tested only as plain Kotlin
   unit tests in `:domain`, not as entries in `contract/fixtures/` — the
   shared file format both this app and the iOS twin are supposed to load
   and agree on. A rule tested only in Kotlin is a rule the Swift port could
   silently get wrong.
3. `TodayViewModel` was a plain class registered as a Koin "build once,
   reuse forever" singleton — a shortcut taken because the project didn't
   yet depend on the libraries needed to make it a real Android ViewModel.
4. A real crash had been found (typing into the Weight field could throw
   `SQLiteConstraintException`) and was explicitly left unfixed, flagged for
   a dedicated pass.

This session closed out all four.

## Some vocabulary, up front

- A **sealed interface** used as a *result type* (rather than throwing) is a
  way of saying "calling this function always gives you back one of these
  specific, known outcomes — including the failure ones — as an ordinary
  value you can inspect," instead of "this either returns a value or blows
  up the call stack with an exception." It's the difference between "handle
  this case if you want to" (exceptions, easy to forget) and "the compiler
  won't let you ignore this case" (a sealed result, inspected with `when`).
- An **UPSERT** is a single SQL statement that means "insert this row — but
  if a row already conflicts with it, update the existing one instead of
  failing." The specific syntax used here,
  `INSERT ... ON CONFLICT(...) DO UPDATE SET ...`, is SQLite's built-in way
  of doing this in one atomic step, rather than the application code doing
  a separate "check if it exists" step followed by a separate "insert or
  update" step.
- A **check-then-act race** is a bug shape: code reads some state ("does
  this row exist?"), then decides what to do based on the answer ("if not,
  insert it"). If two callers run this at nearly the same time, both can
  read "no, it doesn't exist yet" before either one has written anything —
  and then both try to insert, and the second one fails. The fix generally
  isn't "make the check more careful" — it's "make the check and the act
  into one single, indivisible database operation," which is exactly what
  an UPSERT is.
- `viewModelScope` is a `CoroutineScope` androidx hands you for free once a
  class properly extends `androidx.lifecycle.ViewModel` — it's
  automatically cancelled when that ViewModel is cleared (screen closed for
  good), so background work started from it can't outlive the screen it
  belongs to. Session 2's `TodayViewModel` couldn't use this yet because the
  project didn't depend on the library that provides it.
- An **IME action** is the button an on-screen keyboard shows in place of a
  literal "return" key based on context — a search field gets a magnifying
  glass, a form's last field gets "Done." `ImeAction.Done` plus a
  `KeyboardActions(onDone = ...)` callback is how a text field says "when
  the user taps that specific button, do this."

## Part 1 — two new rules: R15 and R16

Both were written into `contract/rules.md` first, in the same numbered,
locked-decision style as the existing 14 rules.

**R15 — one active arc.** At most one arc can be `active` at a time;
creating a new one while another is still running is blocked until the
current one is completed, failed, or archived.

**R16 — a valid start date.** An arc's `start_date` can be in the past, but
not further back than `effectiveToday - backfill_days`. The reasoning is a
direct consequence of a rule that already existed (R7, sealing): if you
could backdate an arc's start further than its own backfill window allows,
you'd create an arc that already has sealed, unfixable missed days the
instant it's created — which would immediately fail a strict-mode arc
before the user ever got to use it.

**Where each rule actually lives in code** turned out to be the
interesting part, and it's a good illustration of the project's `:domain`
vs `:app` split:

- R16 is a pure question about dates and numbers — "given today and a
  backfill window, what's the earliest valid start date?" — answerable with
  no database at all. That makes it a `:domain` function,
  `earliestAllowedStartDate(today, backfillDays)`, plus a small
  `isValidStartDate(...)` wrapper. Both are public (not `internal`), unlike
  the engine's `isScheduled`/`isSatisfied` — arc creation in `:app` is going
  to need to actually call these, so they can't be hidden the way session
  2's report flagged those other two functions for being.
- R15 is a question only the database can answer — "does a row with
  `status = 'active'` exist right now?" That can't be a pure function over
  in-memory lists, so it belongs in `:app`, as a new
  `ArcRepository.hasActiveArc()` check.

## Part 2 — closing the fixture-contract gap

This was flagged as "the important part" of the first task brief, and it's
worth explaining why.

`contract/fixtures/` is a folder of JSON files that both this Android app
and its (separately written) iOS twin load and run the exact same
assertions against. It's the actual mechanism that keeps two independently
written codebases honest about agreeing on business rules — if a fixture
passes in Kotlin but the equivalent Swift code produces a different answer,
that's caught immediately, by design.

Session 2 added three new things to `Resolution` (`dayNumber`, `totalDays`,
and per-commitment status) but tested them with seven ordinary Kotlin
`@Test` methods instead of fixture files — reasonable in the moment, but it
meant these three behaviors existed completely outside the cross-client
safety net. The iOS side could implement any of them subtly wrong and
nothing would ever notice.

Fixing this meant:

1. **Extending the documented format.** `contract/fixtures/README.md` (the
   spec for the format itself) got two new simple `expect` fields
   (`day_number`, `total_days`) and one nested one,
   `commitment_statuses` — date, then commitment id, then one of
   `not_scheduled` / `pending` / `done`:

   ```jsonc
   "commitment_statuses": {
     "2026-01-06": { "c-gym": "done", "c-weekday-run": "not_scheduled" }
   }
   ```

   Every commitment gets a real status for every date in the arc's range —
   there's no "the key is just missing, and that silently means
   `not_scheduled`" shortcut, since a silently-absent key is exactly the
   kind of thing that's easy to implement wrong in a second language.

2. **Wiring the test harness to check the new fields.** The fixture-loading
   DTOs and the actual assertion code (`RulesEngineFixtureTest.kt`) both
   needed the three new fields added, following the exact pattern already
   used for the existing ones (day states, streaks, etc.) — every `expect`
   field stays optional, so a fixture only asserts what it's actually
   about.

3. **Writing eleven new fixture files**, one concept each, covering: a
   commitment's status at the very start and very end of an arc, before an
   arc has started and after it's ended, R1's day-boundary rule applied to
   the new fields (not just the old `effective_today` one), and — for every
   one of the four commitment types (binary, counter, duration, log) — a
   side-by-side "done" and "pending" example. One fixture also exists
   purely to prove there's no missing key anywhere: a tiny 3-day arc with
   two commitments, every single date/commitment combination listed
   explicitly.

   A rule worth calling out from the brief: **every expected value in these
   eleven fixtures was worked out by hand from `rules.md` directly**, not
   copied from what the old Kotlin tests already asserted. Copying forward
   would just make an existing mistake permanent if one existed — as it
   happens, none did; every value matched.

4. **Deleting the seven Kotlin tests**, once their fixture equivalents were
   green. The brief's reasoning: two copies of the same assertion will
   eventually drift out of sync, and the fixture is the one that actually
   ports to the other codebase, so it's the one that should survive.

`:domain:test` went from 45 fixture-driven tests to 56, plus a small,
separate `ValidationTest.kt` for R16 (5 tests, since that's genuinely
`:domain` logic not yet part of the shared JSON contract) — 61 total.

## Part 3 — `TodayViewModel` becomes a real ViewModel

The concrete problem with the Koin-singleton shortcut: a `single` is built
once and handed out forever, for the whole life of the app process. Since
`TodayViewModel`'s `init` block asks the repository "what's the current
active arc?" exactly once and then sticks with that answer, a singleton
`TodayViewModel` would keep showing the *first* arc it ever saw, even after
that arc finished and a second one began. Harmless today (there's only ever
been one arc in the whole app, via the debug seeder) — but a real bug
waiting to happen the moment arc creation exists.

The fix needed two libraries the project didn't have yet:
`androidx.lifecycle:lifecycle-viewmodel-compose` (the real `ViewModel` base
class, plus `viewModelScope`) and `io.insert-koin:koin-androidx-compose`
(the bridge that lets a `@Composable` function ask Koin for a ViewModel).
Both got added to the version catalog and resolved cleanly against
versions already pinned elsewhere in the project (the existing
`lifecycle-runtime-ktx` version, and the existing Koin BOM) — no new,
independently-tracked version numbers introduced.

With those in place: `TodayViewModel` now extends `androidx.lifecycle.
ViewModel`, uses `viewModelScope` instead of a hand-built
`CoroutineScope`, and gets registered in the Koin module with `viewModel { }`
instead of `single { }` — Koin's way of saying "build a fresh one, scoped
to whichever screen is asking, and clean it up when that screen goes away,"
the actual behavior a ViewModel is supposed to have. On the UI side,
`TodayRoute` now obtains its own ViewModel via `koinViewModel()` as a
default parameter, rather than `MainActivity` fetching one and passing it
down — the idiomatic shape for this pairing.

One small, genuinely interesting wrinkle: Koin 4.1 had, in the time since
this dependency was last touched, moved its `viewModel { }` module-builder
function to a new package (`org.koin.core.module.dsl`, from the old
`org.koin.androidx.viewmodel.dsl`). The old import still compiles — it's
just marked deprecated — so this got caught only because the build log's
warning was actually read, not because anything broke.

## Part 4 — the `startKoin` guard

A loose end from session 2's own testing: two `@Test` methods in the same
Robolectric test class both indirectly triggered `StreakSeasonApp.onCreate()`
(via `ApplicationProvider.getApplicationContext()`), and `onCreate()` calls
`startKoin()` unconditionally. Koin refuses to start a second time in the
same process, so the second test method would crash — worked around at the
time with a manual `stopKoin()` in each test's teardown.

That workaround lived in the test file, not in the actual app code, which
is backwards: the real fix belongs where the real risk is. `StreakSeasonApp`
now checks `GlobalContext.getOrNull() == null` before calling `startKoin()`
at all — a proper guard against being started twice, in the one place that
actually starts it. The `stopKoin()` workaround was deleted from the test
file entirely; it's no longer needed.

## Part 5 — proving the first four parts actually work

Rather than trust green tests alone, this got run for real on the emulator
again: fresh install, checked-in commitments, a day flipping to `complete`,
force-killing and relaunching to confirm persistence survived (proving the
new ViewModel's lifecycle change didn't quietly break anything), and
undo — plus one more deliberate check specific to this session: the
emulator's clock was rooted and set to 00:30, past midnight but before the
default 4am boundary, and the header correctly kept showing the *previous*
day's data and day count rather than jumping ahead. That's the same
day-boundary rule from `rules.md`'s R1, now proven again after the ViewModel
rewrite, not just trusted to still work.

## Part 6 — the write race, for real this time

This is where the second task brief (`10-fix-entry-write-race.md`) started,
and it turned out to be worse than the one-line summary suggested.

**The bug, precisely:** `ArcRepository.recordEntry()` used to read whether
an entry already existed for a given commitment and date, and then decide
whether to insert a new row or update the existing one — two separate
database calls, with a gap between them. Typing quickly into the Weight
field (a "log" commitment) triggered a write on every keystroke, and two of
those writes landing close enough together could both see "no existing row
yet," both try to insert, and the second one would crash with
`entries.UNIQUE (day_id, commitment_id)` constraint failure — the exact
crash session 2's report flagged as a known, unfixed issue.

**The fix** replaces the whole read-then-decide dance with one atomic SQL
statement per write — the UPSERT pattern described above. The specifics
mattered:

- **Not Room's built-in `@Upsert`.** That feature detects conflicts by
  matching on the row's *primary key* — but every entry gets a fresh random
  ID on every write, so Room's `@Upsert` would never actually see a
  conflict; it would just insert and crash on the real unique index exactly
  like before. The fix had to name the actual unique index,
  `(day_id, commitment_id)`, as the conflict target explicitly.
- On a conflict, the update clause revives a previously soft-deleted row
  (`deleted_at = NULL`) rather than leaving it dead — undoing an entry and
  then re-recording it should bring the same row back to life, not try
  (and fail) to create a second one.
- `created_at` is only ever set by the insert branch; the update branch
  never touches it, so a row's original creation time survives every
  subsequent edit.

**A discovery made while confirming the fix, not assumed:** the brief asked
for the concurrency test to be written and shown failing against the *old*
code before applying the fix — not just written and trusted. Doing that
surfaced something the brief's own description undersold: a second test
("does re-recording a previously undone entry revive it, instead of leaving
it soft-deleted?") didn't just fail occasionally under a race — it failed
**every single time**, with no concurrency involved at all. A soft-deleted
row still physically occupies the unique index; the old code's read
step filtered soft-deleted rows out, so it always concluded "nothing
exists yet" and always tried a plain insert into a slot that was already
taken. That's a plain bug, not a race — and it's exactly the kind of thing
that "write the test first, watch it fail, then fix it" is supposed to
catch, rather than assuming the bug is only as bad as it was described.

The exact same check-then-act shape existed one more place:
`ArcRepository`'s internal `getOrCreateDay` helper (used by `recordEntry`,
`setNote`, and the rest-token flag), against the `days` table's own unique
constraint, `(arc_id, date)`. Fixed the same way — one atomic "insert this
day, or do nothing if it already exists" statement — which, because it's a
single shared helper, fixed the same underlying risk for every caller at
once. `undoEntry` was checked too and turned out *not* to have this shape:
it looks up a row and then soft-deletes it by its own id, which is safe to
run twice regardless of timing (no insert involved, nothing to collide
with).

**One more genuinely surprising thing, found only by running the fixed
code:** the very first version of the day-upsert statement still crashed —
with a *different* error, `NOT NULL constraint failed: days.rest_token_used`.
The `days` table's schema documents that column as
`NOT NULL DEFAULT 0`, but it turns out Room doesn't carry a Kotlin
property's default value into the database's own generated `CREATE TABLE`
statement — Room's own generated insert code always lists every column
explicitly (resolving Kotlin defaults itself before ever touching SQL), so
it never needed the database to have a real `DEFAULT` clause to fall back
on. A raw, hand-written SQL statement doesn't get that safety net — it has
to list every `NOT NULL` column itself. Fixed by adding the missing column
to the statement; a small, easy-to-miss gap between "what the documented
schema says" and "what Room actually builds."

## Part 7 — the keystroke-per-write problem, and `createArc`'s error handling

Two smaller, independent fixes rounded out the second brief.

**The log field's text control** used to call back into the ViewModel (and
therefore trigger a database write) on every single keystroke — typing
`125` meant three separate writes, three re-resolutions, three screen
redraws, for one intended number. The fix holds the in-progress text as
plain local Compose state and only commits a write on two specific
triggers: the keyboard's "Done" action, or the field losing focus. Typing
still updates what's shown immediately — only the *persistence* was
deferred. Two extra decisions were needed and are worth stating plainly:
an unparseable value (stray letters, symbols) is discarded and the field
snaps back to whatever was last actually saved, rather than writing
garbage; and **submitting an empty field is treated as an undo** — it reuses
the exact same "remove this entry" action the card's own Undo button
already calls, rather than inventing a second way to delete something.
Rapid-tapping a counter or duration's `+`/`-` stepper was checked
separately and does still produce one write per tap — but each tap is a
genuinely distinct value the user asked for (unlike a log field's
meaningless in-progress keystrokes), and it no longer risks crashing now
that the underlying write is atomic. Per the brief, that's noted rather
than "fixed" with debounce logic that wasn't asked for.

**`createArc()`'s R15 check** used to be a `check()` call — the right tool
for "this should never happen, and if it does, something's deeply wrong,"
but the wrong one for "the user has an active arc already," which is a
completely ordinary state a real user will reach constantly. A `check()`
failure is an uncaught exception; a future arc-creation screen would just
crash instead of showing a message. The fix turns `createArc()`'s return
value into a small sealed result type (`CreateArcResult`, either
`Created(arcId)` or `ActiveArcExists`) that a caller inspects like any
other value — a future arc-creation screen can `when`-match on the result
and show "you already have an active arc" as an ordinary piece of UI, not
a crash handler. `check()`/`require()` stay exactly where they already
were for everything that's still a genuine programming error, not a
user-reachable one.

## What exists now vs. before

**Before this session:** the Today screen worked, but two planned rules
existed only as an idea; a real, previously-discovered crash sat unfixed in
the most-used part of the app (every check-in goes through it); the
cross-client fixture contract was quietly missing coverage for three fields
`Resolution` had just grown; and the ViewModel layer had a known,
deliberately-flagged lifecycle shortcut in it.

**After this session:** `rules.md` has 16 documented rules; the fixture
contract is caught up (`:domain:test` at 61, up from 45, with the
Kotlin-only tests it replaced actually deleted, not just duplicated
alongside); `TodayViewModel` has the lifecycle a ViewModel is supposed to
have; Koin can't be double-started even by an over-eager test; and the
write path every check-in depends on no longer has a check-then-act race
anywhere in it — proven by writing the failing test first, watching it
actually fail against the old code, and only then fixing it.
