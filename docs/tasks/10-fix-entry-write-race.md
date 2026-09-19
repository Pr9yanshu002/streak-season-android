# Task: fix the entry write race, keystroke writes, and `createArc` error handling

## Context

Three defects found during the last pass. All sit in the write path that every
check-in goes through, so they should be fixed before arc creation adds more
writers.

Parts 1 and 2 are related but independent — fix both. Part 1 makes concurrent
writes safe wherever they come from; part 2 stops this particular flood.

---

## Part 1 — `recordEntry()` check-then-act race

### The defect

`ArcRepository.recordEntry()` reads to see whether an entry already exists for
`(commitmentId, date)`, then inserts or updates based on the answer. The read
and the write are separate suspend calls with no transaction, so two concurrent
callers can both read "no existing row" before either writes. The first insert
succeeds; the second violates `UNIQUE (commitment_id, date)` and throws
`SQLiteConstraintException`.

Reproducible today by typing into a log-type commitment's text field.

### The fix

Remove the check-then-act entirely. Replace it with a single atomic statement
that lets SQLite resolve the conflict:

```sql
INSERT INTO entries (id, arc_id, commitment_id, date, value, recorded_at,
                     created_at, updated_at)
VALUES (:id, :arcId, :commitmentId, :date, :value, :now, :now, :now)
ON CONFLICT(commitment_id, date) DO UPDATE SET
    value       = excluded.value,
    recorded_at = excluded.recorded_at,
    updated_at  = excluded.updated_at,
    deleted_at  = NULL
```

Points that matter:

- **Do not use Room's `@Upsert`.** It matches on the primary key, which is a
  fresh UUID on every call, so Room would never detect the conflict — it would
  insert and collide on the unique index, producing the same crash.
- The conflict target must be `(commitment_id, date)` explicitly.
- `deleted_at = NULL` on update is deliberate: re-recording an entry that was
  previously undone should revive it rather than leave a dead row.
- `created_at` is only set on insert. The `DO UPDATE` clause must not touch it.
- The generated `id` is only used when the insert path wins. On update the
  existing row keeps its id — which is correct, since sync identifies rows by
  id and changing it would look like a delete plus an insert.

Check whether `undoEntry()` and any note or photo write have the same
check-then-act shape and fix them the same way if so. `days` has
`UNIQUE (arc_id, date)` and is likely to have the identical problem.

---

## Part 2 — log fields write on every keystroke

### The defect

`LogControl`'s text field calls `onChange` per character, and the ViewModel
launches a coroutine per call. Typing `125` persists `1`, then `12`, then `125`
— three DB writes, three engine resolutions, three recompositions, for one
number. This is wrong independently of the race, and it is what makes the race
trivially reproducible.

### The fix

Hold the in-progress text in local Compose state. Call `recordEntry()` once, on
either:

- the keyboard's done/IME action, or
- focus leaving the field

Requirements:

- The field must still show what the user is typing as they type. Only the
  **persistence** is deferred, not the display.
- When the underlying `Resolution` changes for reasons other than this field,
  the displayed text should follow it — but not while the field has focus, or
  the user's typing will be overwritten mid-entry.
- Non-numeric or empty input must not be written. Decide what empty means
  (most likely: no write, or an undo) and say which you chose in the report.

Counter and duration controls use steppers, which fire per discrete tap, so they
are lower risk. Check whether rapid tapping produces the same flood; if it does,
note it rather than adding debounce logic in this task.

---

## Part 3 — `createArc()` throws on a user-reachable condition

### The defect

R15 (one active arc) is enforced with `check()`, which throws
`IllegalStateException`. That is the right tool for a programming error, but
"you already have an active arc" is a state the user can genuinely reach. The
create-arc screen needs to show a message, not crash.

### The fix

Return a result the caller can handle rather than throwing. A sealed result type
or a `Result<T>` with a typed failure — pick whichever fits the existing style
and keep it consistent.

Do not swallow the condition silently. The caller must be able to distinguish
"blocked by R15" from any other failure, so the UI can say something specific.

Keep `check()`/`require()` for genuine programming errors elsewhere. This change
is only for conditions a user can cause.

---

## Constraints

- `:domain` is not modified. These are all `:app` concerns.
- No new dependencies.
- No debounce or throttle utilities. Part 2 is solved by committing at the right
  moment, not by rate-limiting a stream of wrong writes.
- Do not relax or remove the `UNIQUE (commitment_id, date)` constraint. It is
  correct; the code violating it is what is wrong.

## Definition of done

- `./gradlew :domain:test` still green, unchanged count.
- A test that calls `recordEntry()` concurrently for the same
  `(commitmentId, date)` — several parallel coroutines — and asserts no
  exception and exactly one surviving row. This test must fail against the old
  implementation; confirm that it does before fixing.
- A test that re-recording a previously undone entry revives it rather than
  leaving `deleted_at` set.
- Manual check on the emulator: type a multi-digit number into the weight field.
  No crash, and exactly one write lands when the field is committed.
- Check-in, day-flip, undo and persistence still work.

## Report back

- Whether the concurrency test failed against the old implementation before the
  fix. If it did not, the test is not reproducing the race and needs
  strengthening.
- Which other repository methods had the same check-then-act shape, and whether
  they were fixed.
- What empty input in a log field now does.
- Whether rapid stepper tapping on counter or duration commitments produces the
  same write flood.
- The shape chosen for `createArc()`'s result, and how the caller distinguishes
  the R15 failure from other failures.Read CLAUDE.md, then read docs/tasks/10-fix-entry-write-race.md and do it.