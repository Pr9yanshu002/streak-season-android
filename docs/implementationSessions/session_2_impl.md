# Session 2 — the Today screen, and moving rule logic out of the ViewModel

This is a walkthrough of everything built in this session, written for
someone who has never touched Kotlin or Android development before, in the
same spirit as `session_1_impl.md`. It covers two connected pieces of work
done back to back: building the first real screen, and then — once a
structural problem in that screen became obvious — fixing where some of its
logic actually lived.

## The starting point

After session 1, the project had:

1. **A finished, tested rules engine** (`:domain`) — 45 passing tests.
2. **A finished, tested persistence layer** (`:app/data`) — Room database,
   DAOs, mappers, `Clock`, `ArcRepository`, all wired together with Koin.
3. **Nothing on screen.** The app launched to a placeholder "Hello Android"
   text. There was no way to even create an arc, let alone look at one.

This session's brief was `docs/tasks/08-today-screen.md`: build the first
real screen — "Today" — and prove the *entire* chain works, end to end:
database → mappers → rules engine → screen → user taps something → it
writes back → the screen updates without a restart.

## Some vocabulary, up front

A few Android/Compose-specific terms come up a lot below (session 1's
glossary covered Kotlin/Room basics like `suspend`, `Flow`, data classes —
this one adds the UI-layer terms):

- **Jetpack Compose** is Android's current UI toolkit. Instead of describing
  a screen with a separate layout file, you describe it with ordinary Kotlin
  functions marked `@Composable`. Call the function, and it draws that piece
  of UI.
- **State** in Compose means "a value that, when it changes, automatically
  causes the screen to redraw itself." `collectAsState()` is the bridge
  between a `Flow` (session 1's "stream of values over time") and Compose's
  own state system — it turns "here's a live stream of values" into "here's
  the current value, and redraw me whenever it changes."
- A **ViewModel** is the conventional Android name for the class that sits
  between a screen and the data layer: it holds the screen's current state,
  and exposes the actions the screen's buttons can trigger. It's deliberately
  *not* supposed to contain business logic — just plumbing between "what the
  data layer says" and "what the screen needs to render."
- A **sealed interface** (or **sealed class**) is Kotlin's way of saying "a
  value of this type is *always* exactly one of this fixed, known list of
  shapes, and nothing else." `TodayUiState` is one: a screen is always
  exactly `Loading`, or `NoActiveArc`, or `Loaded` (with real data) — never
  some other unlisted possibility. The compiler will actually refuse to
  build the code if a `when` block handling a sealed type forgets one of the
  cases, which is exactly the safety net you want for "what does the screen
  show" logic.
- A **stateless composable** takes plain data and callbacks as parameters
  and draws them — it doesn't know or care where that data came from. A
  **stateful wrapper** is a thin composable around it that *does* know —
  it holds the ViewModel and feeds the stateless one. Splitting the two
  apart is what makes the stateless half directly previewable and testable
  with fake data, with no database or ViewModel required.
- A **Preview** (`@Preview`) is a composable Android Studio can render
  directly in the editor, fed by hand-built fake data, without running the
  app on a device at all.

## Part 1 — a debug-only seed

There was no arc-creation screen yet, so there was no way to get *any* data
onto the Today screen without one. The brief asked for a one-time,
debug-build-only seed: on first launch, if no arc exists yet, create one.

This became `DebugSeeder.kt`. It builds one arc — "September Reset,"
starting 10 days before whatever "now" the device reports and ending 30
days after — with four commitments, one of each type the rules engine
supports (`binary`, `counter`, `duration`, `log`), one of them marked
`bonus` and one scheduled weekdays-only, specifically so the screen would
have to handle both of those cases rather than only the easy path. It then
back-fills entries for the ten already-elapsed days in a deliberate mix —
some fully complete, some partial, some missed entirely, and one day
flagged as having used a rest token — so the very first launch already has
a real streak history behind it, not just an empty slate.

Two small, genuinely new questions came up in writing this:

- **How does the app know it's a debug build, without adding a new
  dependency?** The usual Android answer is a generated `BuildConfig.DEBUG`
  flag, but turning that on required a `build.gradle.kts` change
  (`buildFeatures.buildConfig = true`) that felt like more surface area than
  the task needed. Instead, `StreakSeasonApp` checks the app's own
  `ApplicationInfo.FLAG_DEBUGGABLE` flag at runtime — the same underlying
  fact, no new build feature required.
- **"Idempotent"** — the brief's word for "don't reseed on every launch" —
  became a single check: does any arc already exist? If yes, do nothing.

## Part 2 — TodayViewModel (first pass)

The plan was a `TodayViewModel` that collects `ArcRepository.observeResolution(arcId)`
and turns it into a `TodayUiState` the screen can render, plus a handful of
actions (complete a binary commitment, adjust a counter/duration, record a
log value, undo).

One deliberate departure from the textbook Android pattern: this is **not**
an `androidx.lifecycle.ViewModel`. The real one needs two libraries the
project doesn't have yet (`lifecycle-viewmodel-ktx`, for the base class and
`viewModelScope`; `koin-androidx-compose`, for getting one out of Koin
inside a composable) — and the brief explicitly said no new dependencies for
this task. So `TodayViewModel` is a plain Kotlin class, registered as a Koin
`single`, with its own manually-created `CoroutineScope`. For a
single-screen app this is a fine, honest shortcut; it's called out in a
comment as something to revisit if a second screen ever needs its own
lifecycle-scoped ViewModel.

## Part 3 — the first real domain-model wrinkle

This is where the first genuine problem showed up, and it's worth walking
through carefully because it's the thing the rest of the session ended up
being about.

`RulesEngine.resolve(...)` returns a `Resolution` object — but `Resolution`,
at the start of this session, only exposed *day-level* results: a state
(`complete`/`partial`/`missed`/…) per date, plus streaks, completion
percentage, and so on. It said nothing about *individual commitments* — no
"is this specific commitment done today," no "is it even scheduled today."
It also didn't expose the arc's day count ("Day 12 of 40") at all.

But the Today screen's whole job is a list of individual commitment cards,
each showing whether *that one* is done. To build that list at all, the
ViewModel needed answers `Resolution` simply didn't have.

Worse: the two rules engine functions that *do* compute those answers
internally — `isScheduled()` (is this commitment scheduled on this date,
per the R3 rule) and `isSatisfied()` (does this entry satisfy this
commitment, per R4) — are marked `internal` inside `:domain`, meaning
`:app` isn't even allowed to call them.

The project's own ground rules are explicit that the rules engine
(`:domain/rules/`) isn't something to just modify on the spot — "if the
engine or a domain model appears to need a change, stop and report it."
So rather than touch `:domain`, the first version of `TodayViewModel`
worked around the gap by writing small private copies of the same R3/R4
logic directly inside the ViewModel, clearly commented as exactly that — a
workaround, not a design choice — and the plan was always to report it
rather than quietly live with it. Similarly, `dayNumber`/`totalDays`
("Day 12 of 40") got computed by hand from the arc's raw start/end dates,
since nothing in `Resolution` provided them either.

This is precisely the failure mode the project's whole domain/UI split
exists to prevent — a rule re-implemented outside the engine, with no
guarantee the two copies stay in sync forever — just deliberately
contained to two small, clearly-labeled functions instead of scattered
everywhere, and flagged loudly rather than hidden.

## Part 4 — the screen itself

With that workaround in place, the rest was straightforward Compose: a
`TodayScreen` stateless composable (arc name, "Day N of M", today's state as
plain text, then one card per scheduled commitment) plus a `TodayRoute`
stateful wrapper that holds the ViewModel — matching the brief's explicit
instruction to keep those two separated.

Each commitment card shows its name, an "optional" label for bonus
commitments, whether it's satisfied, and a type-appropriate control:

- **binary** → a checkbox
- **counter** / **duration** → a stepper (−/+ buttons) showing "current /
  target"
- **log** → a plain text field for typing a number

Per the brief's explicit "deliberate ugliness" instruction, this uses stock
Material 3 components throughout — no custom colors, no animation, no
icons beyond what Material ships — because the domain model has never been
exercised by real UI before and is expected to need shape changes (which,
as it turned out, was exactly right).

Also added, matching the brief: an `ArcRepository` addition to fetch "the
active arc" (Phase 1 only ever has one), a `TodaySnapshot` bundle (arc +
commitments + today's entries + the `Resolution`) so the ViewModel had
everything it needed from one repository call, and a small
`setRestTokenUsed(...)` write method the seeder needed that didn't exist
yet.

**Worth flagging honestly, again:** the project's `CLAUDE.md` reserves the
repository layer (`ArcRepository`) for the project owner to write, with
review-only from here — the same note session 1 flagged. This session added
several more methods to it (`observeToday`, `hasAnyArc`, `getActiveArcId`,
`getCommitments`, `setRestTokenUsed`) to make the screen work, following the
task brief's instructions. Same situation as before: flagged here rather
than silently expanding scope.

## Part 5 — proving the first version worked

Rather than trust the code to compile and stop there, this got run for
real: installed onto a running Android emulator, launched, and driven by
hand (via `adb`, tapping real coordinates and typing real text) —

- checking a binary commitment → the day's state updated live, no restart
- incrementing a counter past its target → the day flipped to `complete`
  once every required commitment was satisfied
- force-killing the app and relaunching → everything was still there,
  read back correctly from the database
- tapping "Undo" → the entry disappeared and the day state reverted

One interesting accidental discovery along the way: the emulator's clock
happened to read just past midnight, before the arc's default 4am
day-boundary hour. Rule R1 says "before the boundary hour, it's still
yesterday as far as the app is concerned" — and the screen correctly showed
yesterday's data as "today," proving that day-boundary rule was actually
wired all the way through, not just tested in isolation inside `:domain`.

At the end of this pass, the honest report back was: the screen works, but
`Resolution` is missing real estate the UI needs, and two rule-shaped
functions got duplicated into `:app` to cover the gap. That report is what
kicked off the second half of this session.

## Part 6 — moving the duplicated logic into `:domain`

This time, permission to touch `:domain` was given explicitly, since fixing
this properly meant changing `Resolution`'s shape — normally exactly the
kind of change the project's rules say to stop and ask about first, which
is what had happened.

Three things moved from `:app` into the rules engine:

**1. A new domain enum, `CommitmentStatus`:**

```kotlin
enum class CommitmentStatus { NOT_SCHEDULED, PENDING, DONE }
```

One nuance worth spelling out: rest tokens (rule R6) never appear here.
Rest tokens are described in `rules.md` purely as something that changes a
*day's* overall state (turning what would've been `partial`/`missed` into
`rest`) — they never touch whether one specific commitment counts as
satisfied. So an unfinished required commitment on a "rest" day is simply
`PENDING`, same as any other day; there's no separate rested-commitment
state to invent.

**2. R2's missing values, `dayNumber` and `totalDays`, added to
`Resolution`** — computed properly in `:domain` this time, reusing a
function the engine already had (`elapsedDates`) rather than hand-rolling
new date math:

```kotlin
internal fun totalDays(arc: Arc): Int =
    ChronoUnit.DAYS.between(arc.startDate, arc.endDate).toInt() + 1

internal fun dayNumber(arc: Arc, today: LocalDate): Int =
    elapsedDates(arc, today).size
```

The `+ 1` in `totalDays` matters: `ChronoUnit.DAYS.between` counts the
*gap* between two dates, not an inclusive day count — a Jan 1 → Jan 5 arc
is a 4-day gap but a 5-day arc, exactly as `rules.md` already specified.
`dayNumber` comes out to `0` before the arc starts and gets pinned at
`totalDays` once the arc is over, for free, just by reusing the existing
`elapsedDates` function's own already-correct behavior at both edges.

**3. Per-commitment status for every date, `commitmentStatuses`,** added
to `Resolution` as `Map<LocalDate, Map<String, CommitmentStatus>>` — every
commitment gets an entry for every date the arc covers, including
`NOT_SCHEDULED`, so a missing map key never has to silently mean anything.
It's computed using the engine's *existing* `isScheduled()`/`isSatisfied()`
functions — which stayed exactly as `internal` as before; nothing about
their visibility changed, only that `Resolution` now carries their answer
outward instead of `:app` trying to guess it independently.

With all three in place, `TodayViewModel` shrank considerably: the two
hand-rolled R3/R4 lookalikes and the manual date-count math were deleted
outright, and the mapping function became exactly what a ViewModel is
supposed to be — reading fields off `Resolution` and reshaping them into
`TodayUiState`, with no decisions of its own.

**One extra case this surfaced:** what should the screen show if "today"
falls outside the arc's date range entirely — before it starts, or after
it's ended? `Resolution.commitmentStatuses` simply has no entry for a date
like that (it only covers the arc's own start-to-end range), which used to
silently render as an empty commitment list — indistinguishable from a bug.
Two new states were added to `TodayUiState`, `NotStarted` and `Ended`, and
the ViewModel tells them apart using `dayNumber` alone (`0` means not
started yet; anything else means it's pinned at the end) — without needing
to compare raw dates itself, keeping that decision anchored to values
`Resolution` already computed rather than a third hand-rolled comparison.

## Part 7 — proving it again

Every layer of this got its own test coverage:

- Seven new plain JUnit tests in `:domain` (not the shared cross-client
  fixture files — this shape isn't part of the iOS-shared contract yet, so
  it stayed as ordinary Kotlin tests): the three-way
  `NOT_SCHEDULED`/`PENDING`/`DONE` split on one date, an entry's removal
  turning `DONE` back into `PENDING`, `dayNumber`/`totalDays` on a 30-day
  arc, the day-boundary-hour edge case, a "no missing keys" structural
  check across every date in an arc's range, and both out-of-range edges
  (before start, after end).
- Two new tests in `:app` for `TodayViewModel`, one per new state
  (`NotStarted`, `Ended`), built against a real in-memory Room database the
  same way `ArcRepositoryTest` already does.

And once again, this got driven for real on the emulator rather than just
trusted from the tests: check-in, day-state flip, kill-and-relaunch
persistence, and undo were all re-verified after the refactor. The
day-boundary case got an extra, more convincing test this time — the
emulator was rooted and its clock was actually set to 00:30, past
midnight — and the screen correctly kept showing the previous day's data
and day count rather than jumping ahead, which is a stronger proof than
waiting for the clock to naturally land in that window.

One small unrelated snag surfaced only by running the new ViewModel tests
back to back: two tests in the same class both indirectly triggered Koin's
startup (via `ApplicationProvider.getApplicationContext()`, the same
Robolectric helper `ArcRepositoryTest` already relied on), and Koin refuses
to start a second time in the same process. Fixed by explicitly stopping
Koin after each test — a one-line addition confined to the new test file,
not a change to how the real app starts up.

## What exists now vs. before

**Before this session:** a finished rules engine and persistence layer,
with `Resolution` exposing only day-level state, and no screen anywhere in
the app.

**After this session:** a working Today screen, proven end to end on a
real emulator — including the trickiest part, the 4am day-boundary rule,
under an actually-manipulated clock — and a `Resolution` object that now
answers the two questions a commitment-level UI actually needs
(`dayNumber`/`totalDays`, and per-commitment `CommitmentStatus`) without
`:app` ever having to re-derive a rule the engine already knows how to
compute. `:domain:test` sits at 52 passing tests (up from 45), and a grep
across all of `:app` confirms no rule logic — no schedule-matching, no
satisfaction-checking, no day-count math — is left duplicated outside the
engine.
