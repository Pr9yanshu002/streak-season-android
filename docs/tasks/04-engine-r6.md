# Task: rules engine, slice 2 — R6, rest tokens

## Context

R1–R5 are implemented. 21 of 45 fixtures are green. This task adds R6, the rest
token rule.

Read `contract/rules.md` R6 first. It is authoritative and it is unusually
precise — every clause in it matters.

## Why this rule is different

R1–R5 resolve each date independently. R6 does not.

A token's validity depends on how many tokens were already consumed **earlier in
the same ISO week**. So you cannot decide Thursday's state without first having
decided Tuesday's. And you cannot know whether Tuesday consumed a token without
first knowing whether Tuesday would otherwise have been `partial` or `missed` —
which is R5's job.

That ordering constraint is the whole difficulty of this rule. Solve it
deliberately rather than discovering it halfway through.

## Scope

- **R6** only.
- The `rest` branch of R5's ladder, which was skipped in slice 1, is now live.
- `restTokensRemaining` in `Resolution` now returns real values instead of
  `emptyMap()`.

Still out of scope, keep the placeholders: `sealedDates`, `currentStreak`,
`bestStreak`, `completionPct`, `arcStatus`, `failedOnDate`.

## The rules, restated

Every one of these has a fixture. Do not skip any.

- Tokens exist **only** when `strictness = 'standard'`. In `strict` and `chill`,
  the allowance is 0 and any stored `rest_token_used` flag is ignored entirely.
- Allowance is `arc.rest_tokens_per_week` per **ISO week**, Monday to Sunday.
- A token is consumed only on a day that has `rest_token_used = 1` **and** would
  otherwise resolve to `partial` or `missed`. A token flagged on a day that is
  already `complete` is ignored and not consumed.
- Validity is decided **by date ascending** within the ISO week. If the
  allowance is 1 and both Tuesday and Thursday are flagged, Tuesday takes it and
  Thursday resolves to `partial`/`missed`.
- **No carryover.** Each ISO week starts fresh; unused tokens are lost.
- **Partial weeks get the full allowance**, not a prorated one. A 3-day arc
  starting on a Saturday still gets 1 token.
- `restTokensRemaining` is `allowance - consumed`, floored at 0, keyed by ISO
  week.

## ISO week keys

The map key format is `"2026-W03"` — four-digit **ISO week-based year**, `-W`,
then a two-digit zero-padded ISO week number.

The week-based year is not always the calendar year. Dates in early January can
belong to the last ISO week of the previous year, and dates in late December can
belong to week 1 of the next. Use `IsoFields.WEEK_BASED_YEAR` and
`IsoFields.WEEK_OF_WEEK_BASED_YEAR`, not `date.year` and not
`WeekFields.of(Locale.getDefault())`. A locale-dependent week field will produce
different answers on different devices and will not match the Swift port.

Add a KDoc note on the key-building function recording this, since Swift's
`Calendar` defaults differently and this will need care when porting.

## Constraints

- **Never read the clock.** `now` is a parameter.
- No new dependencies. `java.time` only.
- Explicit over clever. This translates to Swift by hand — prefer a readable
  loop over a dense `groupingBy`/`fold` chain.
- Keep the two-stage structure visible in the code. Someone reading it should be
  able to see that provisional states are computed first and tokens applied
  after, without tracing control flow to work it out.
- Do not restructure or rewrite R1–R5. If a change there is genuinely needed,
  stop and report it rather than doing it.

## Definition of done

`./gradlew :domain:test` passes everything from slice 1 plus the `rest_*` group:

- `rest_token_converts_missed`
- `rest_token_no_carryover`
- `rest_token_on_complete_day_ignored`
- `rest_token_partial_first_week_full_allowance`
- `rest_token_ignored_in_strict_mode`
- `rest_token_second_in_week_invalid`

No previously green fixture may go red. If one does, the fix is in the new code,
not in slice 1 and not in the fixture.

**Do not make a fixture pass by special-casing it.** If a fixture in scope fails
and R6 as written says it should pass, the implementation is wrong. If the
fixture's expected value looks wrong, stop and report it.

## Report back

- Green count and which fixtures moved.
- A plain-language description of the two-stage structure: what is computed
  first, what is computed second, and why it cannot be done in one pass.
- Anything in R6 that was ambiguous, and the interpretation chosen.
- Anything about the ISO week handling that you expect to behave differently in
  Swift. This goes in the report, and add a line for it to `docs/notes.md` under
  a "Dates" heading.