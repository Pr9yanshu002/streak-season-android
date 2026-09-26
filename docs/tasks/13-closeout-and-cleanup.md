# Task 13: close out task 12, then a cleanup pass

## Context

Task 12 (arc lifecycle and milestones) is largely built: `reconcile()` exists
and runs from `TodayViewModel.refresh()` and `MainActivity.onResume()`,
`requiredMilestones` returns exact crossing dates per amended R12, and Today has
`Ended` and pending-milestone states.

This task has two parts. **Part 1 is verification** — confirm task 12 actually
works end to end, and report what does not. **Part 2 is four small fixes** that
have accumulated. Do Part 1 first and report on it before starting Part 2; if
Part 1 turns up something broken, stop and report rather than fixing it inside
this task.

---

## Part 1 — verify task 12

Check each of these on the emulator and in code. For each, report **works**,
**partly works** (say what is missing), or **not built**.

### 1a. Remix

- A failed arc's ended screen offers a remix option.
- Remix opens the create flow pre-filled with the source arc's name,
  commitments and rules, and **fresh** dates subject to R16.
- The created arc has `remixed_from_arc_id` set to the source arc.
- **Every copied commitment has a new UUID.** Reusing the source's commitment
  ids would make the old arc's entries point at the new arc's commitments.
  Check this in the database, not by reading the code.
- Entries, days, milestones and the intention are **not** copied.
- The source arc is untouched.

### 1b. Milestone card

- A milestone card appears when an unacknowledged milestone exists.
- Dismissing sets `acknowledged_at`.
- **After killing and relaunching the app, a dismissed milestone does not
  reappear.**
- Several pending milestones present in order, oldest first.
- `start` does not appear as a card.
- A reflection typed into the card is saved and survives relaunch.

### 1c. The two-weeks-closed case

Using `FakeClock` in a test, or `DebugSeeder`, set up an arc where three
thresholds were crossed while the app was closed, then open Today.

- Report exactly what the user sees: how many cards, in what order, and what
  each says.
- Confirm each milestone row's `triggered_on_date` is its **crossing date**, not
  the date the app was opened. This is the behaviour amended R12 exists for.
- Say whether sitting through that many cards before reaching the check-in
  screen is reasonable. An honest opinion is wanted here, not a yes.

### 1d. Reconcile idempotency

Confirm `ArcRepositoryTest`'s "reconcile is idempotent" test exists, and that it
**fails** if either guard is removed — the `status != :status` clause in
`updateStatusIfChanged`, or the stored-kinds diff before the milestone upsert.
Remove each temporarily, run the test, restore it. A test that passes with the
guard removed is not testing idempotency.

---

## Part 2 — cleanup

### 2a. `minSdk` and SQLite version

**This is the urgent one.** `INSERT … ON CONFLICT … DO UPDATE` requires SQLite
3.24+. Android bundles SQLite with the OS, and API levels below 29 ship older
versions. Every write in this app — entries, days, milestones — uses that
syntax.

1. Report the current `minSdk`.
2. Run `SELECT sqlite_version()` on the emulator and report the result.
3. If `minSdk` is below 29, bump it to 29. This app is not distributed through
   a store and runs on personal devices only; Android 10 is a reasonable floor.
4. Check whether any code was written to work around a lower `minSdk` that can
   now be removed. Report it; do not remove it without saying so.

### 2b. R17 and R18 into `rules.md`

Two validations exist only in `CreateArcViewModel`. They are product rules the
Swift app will need, so they belong in the contract. Append after R16, in the
existing style:

```markdown
## R17 — Arc name

An arc's name must contain at least one non-whitespace character.

## R18 — At least one required commitment

An arc must have at least one commitment with `is_required = 1`.

An arc where every commitment is a bonus computes 0% completion for its entire
life under R8, because no day has a required commitment scheduled and the
counted set is always empty. That is correct by the rules and meaningless on
screen, so it is blocked at creation rather than rendered.
```

Then:

- Move both checks into `:domain/rules/Validation.kt` as public functions,
  alongside the R16 ones, with a KDoc naming the rule.
- Have `CreateArcViewModel` call them instead of its own logic. Delete the
  duplicated logic — do not leave both.
- Add unit tests in `ValidationTest`.

The reason to move them: a validation that lives only in a Kotlin ViewModel is
one the Swift app will either miss or reimplement differently.

### 2c. Fixture key check

`FixtureLoader` decodes with `ignoreUnknownKeys = true`, so fixtures can carry a
human-readable `note` field. The cost is that a misspelled `expect` key —
`current_streek` — is silently ignored, asserts nothing, and passes.

Add a test that fails if any fixture's `expect` block contains a key the harness
does not recognise. Build the allowed set from `ExpectDto`'s `@SerialName`
values rather than a hand-maintained list, so adding a new `expect` field does
not require updating the check separately.

Top-level keys outside `expect` (such as `note`) are allowed. Only `expect` is
checked.

Run it against all 62 fixtures and report any that fail.

### 2d. Soft-delete rule in `CLAUDE.md`

Add to the Hard rules section:

```markdown
- **Soft-deleted rows still occupy unique indexes.** Any table with both
  `deleted_at` and a UNIQUE constraint must write via
  `INSERT … ON CONFLICT(<unique cols>) DO UPDATE … SET deleted_at = NULL`,
  never a filtered read followed by a plain insert. Room's `@Upsert` cannot be
  used for these — it matches on the primary key, which is a fresh UUID.
  Affects: entries, days, milestones.
```

This has caused the same class of bug twice. It should be in the file Claude
Code reads at the start of every session.

---

## Constraints

- Part 1 is verification. Report problems; do not fix them in this task.
- `resolve()` behaviour does not change. R17 and R18 are validation, not
  resolution.
- Do not change any fixture's expected values.
- No new dependencies.

## Definition of done

- Part 1 reported, item by item.
- `minSdk` is 29 or higher, and the SQLite version is recorded.
- R17 and R18 are in `rules.md` and enforced from `:domain`, with no duplicate
  logic left in the ViewModel.
- The fixture key check exists and passes.
- `CLAUDE.md` carries the soft-delete rule.
- `./gradlew :domain:test` and `:app` tests green.

## Report back

- Part 1, item by item: works / partly works / not built, with specifics.
- The honest opinion on 1c — is the multi-card sequence reasonable?
- Whether the idempotency test failed with each guard removed.
- Original `minSdk`, SQLite version, and any workaround code found.
- Any fixture the key check caught.