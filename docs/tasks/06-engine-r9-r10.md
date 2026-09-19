# Task: rules engine, slice 4 — R9, R10 (final slice)

## Context

R1–R8 and R11 are implemented. 38 of 45 fixtures are green. This task adds the
last two rules the engine computes, after which `RulesEngine.resolve()` is
complete and all 45 fixtures should pass.

Read `contract/rules.md` R9 and R10 first. Both are short, and R9 is given as
pseudocode — follow it literally.

## Scope

- **R9 — strict-mode failure.** `arcStatus` and `failedOnDate` in `Resolution`
  return real values instead of the placeholders.
- **R10 — arc completion.** `arcStatus` becomes `completed` when the arc has run
  past its end date without failing.

Not in scope: R12 (milestones), R13 (editing a live arc), and the app-level half
of R14. Those are app concerns, not engine concerns — see the note at the end.

## The rules, restated

**R9** applies only when `strictness = 'strict'`. Two things make it easy to get
wrong:

- It evaluates **sealed dates only** (R7). The loop walks elapsed dates
  ascending and **breaks** at the first unsealed date — it does not `continue`
  past it. An unsealed miss is still fixable, so nothing beyond it can be judged
  yet.
- **The first sealed failing date wins.** Once a failing date is found, stop.
  Later failing dates do not overwrite it.

`standard` and `chill` arcs never fail, regardless of how many days are missed.

**R10.** When `today > end_date`, status is `active`, and R9 did not fail the
arc, the status becomes `completed`.

Order matters: R9 runs first. A strict arc that both failed on day 19 and has
run past its end date is `failed`, not `completed`.

`completed_at` and the `final` milestone are mentioned in R10 but are **not**
engine outputs — the engine is a pure function and does not write to the
database or create rows. It reports the derived status; the app acts on it.

## Constraints

- **Never read the clock.** `now` is a parameter.
- The engine returns a derived status. It does not mutate the `Arc` it was
  given, and it does not persist anything. `arc.status` is an input;
  `Resolution.arcStatus` is an output.
- Follow R9's pseudocode structurally, including the `break`. Someone reading
  the Kotlin next to `rules.md` should see the same algorithm.
- Explicit over clever. This translates to Swift by hand.
- Do not restructure earlier slices. If a change is genuinely needed, stop and
  report it.

## Definition of done

`./gradlew :domain:test` — **45 of 45 green.**

The remaining red fixtures are:

- `strict_fails_on_sealed_miss`
- `strict_first_failing_date_wins`
- `strict_complete_run_survives`
- `strict_does_not_fail_unsealed_miss`
- `edge_arc_completes_on_end_date`
- `edge_clock_jumps_backwards`
- `edge_single_day_arc`

No previously green fixture may go red.

**Do not make a fixture pass by special-casing it.** If a fixture in scope fails
and the rule as written says it should pass, the implementation is wrong. If the
fixture's expected value looks wrong, stop and report it.

## After the tests pass

Two cleanup items, only once all 45 are green:

1. Read back through `RulesEngine.kt` and confirm every function carries a KDoc
   naming the rule it implements (`/** R9 — ... */`). This is what makes the
   file readable next to `rules.md` during the Swift port.
2. Confirm no function in `:domain` reads the clock, and that `:domain` still
   has no Android dependency. `import android.*` must not compile.

## Report back

- Final green count.
- A plain-language summary, in one paragraph, of the whole `resolve()` pipeline
  now that it is complete — the stages in order, and what each contributes.
  This goes in `docs/notes.md` under an "Engine" heading as well as in the
  report.
- Anything in R9 or R10 that was ambiguous, and the interpretation chosen.
- Any remaining item in `rules.md` (R12, R13, R14) that the engine does **not**
  handle and that the app layer will therefore have to, listed explicitly. This
  is the handover list for the Room and UI work that comes next.