# Task: write the remaining fixtures

## Context

`contract/fixtures/` currently holds 5 worked examples out of the 45 cases
listed in `contract/fixtures/README.md`. This task writes the other 40.

A fixture is a JSON file describing an arc, some check-ins, a frozen `now`, and
the expected output of the rules engine. Both the Kotlin and the (future) Swift
engine load the same files and assert against them.

The rules engine is **not implemented yet**. Every fixture will fail with
`NotImplementedError`. That is expected and is not a reason to implement
anything.

Read these first — they are authoritative:

- `contract/rules.md` — R1–R14.
- `contract/fixtures/README.md` — the format, the conventions, and the numbered
  list of 45 cases.
- The 5 existing fixtures — match their style exactly.

## What to build

One JSON file per case in the README's list, excluding the 5 that already exist.
Use the exact `id` and filename given in that list.

For each fixture:

- `rules` lists the rule ids it exercises.
- `description` is one sentence saying what the fixture pins down. Write it as a
  claim about behaviour ("a counter under target does not satisfy"), not as a
  restatement of the setup.
- `expect` asserts **only what the fixture is about**. A scheduling fixture
  asserts `day_states`; it does not also assert `best_streak` unless streaks are
  the point. Over-asserting makes fixtures fail for unrelated reasons and turns
  one bug into twenty red tests.

## How to derive expected values

This is the part that matters. A wrong fixture is worse than a missing one: it
makes a correct engine look broken, and time gets spent "fixing" working code.

- Work every expected value out from `rules.md` by hand. Do not pattern-match
  off the existing fixtures' answers.
- Cite the rule in your reasoning as you go. If a value cannot be derived from a
  numbered rule, the fixture is testing something undefined — stop and flag it
  rather than inventing an answer.
- **Verify all date arithmetic.** Weekday-of-date, ISO week numbers, and day
  counts must be checked, not assumed. `2026-01-12` is a Monday in ISO week 03.
  Getting this wrong is the single most likely source of bad fixtures.
- `completion_pct` is 4 decimal places, round half up. Per R8, rest days are
  excluded from **both** numerator and denominator, as are pending days and days
  with no required commitment scheduled. Show the fraction you computed in your
  report.
- Keep each fixture minimal. One or two commitments, the shortest arc that makes
  the point. A 30-day arc where 5 days would do makes failures harder to read.

## Constraints

- **Do not implement any part of the rules engine.** `RulesEngine.resolve()`
  stays `TODO()`.
- Do not modify `rules.md`, `schema.sql`, the existing 5 fixtures, or anything
  under `domain/src/main/`.
- Do not change the fixture format. If a case cannot be expressed in it, flag it
  in the report instead of extending the format.
- Files go in `contract/fixtures/` only. Never write into
  `domain/src/test/resources/` — that path is generated output.

## Definition of done

- 45 files in `contract/fixtures/`.
- `./gradlew :domain:test` loads all 45 and fails all 45 with
  `NotImplementedError`, each named by its fixture id.

If a fixture fails to load or deserialize, that is a real failure — fix it. Only
`NotImplementedError` is the expected failure.

## Report back

- The list of fixtures created.
- For every fixture asserting `completion_pct`, the fraction computed
  (e.g. `3/13 = 0.2308`).
- Any case from the README's list that could not be derived from a numbered rule
  in `rules.md`, and what was ambiguous about it.
- Any case where the correct answer seemed to depend on a rule that is not
  written down.