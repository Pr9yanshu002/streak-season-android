# Notes

Things learned while porting rules to code that aren't obvious from
`rules.md` alone, kept here for whoever ports this to Swift.

## Dates

- **ISO week keys (R6) need an explicit ISO-8601 calendar, not the platform
  default.** Kotlin: built from `java.time.temporal.IsoFields.WEEK_BASED_YEAR`
  / `IsoFields.WEEK_OF_WEEK_BASED_YEAR`, not `LocalDate.year` and not
  `WeekFields.of(Locale.getDefault())` — the week-based year isn't always the
  calendar year (e.g. `2026-12-31` falls in week 1 of week-based-year 2027),
  and a locale-dependent week field disagrees with itself across devices.
  Swift's `Calendar.current` is locale/region-dependent the same way — the
  port needs `Calendar(identifier: .iso8601)` with `minimumDaysInFirstWeek = 4`
  and `firstWeekday = 2` (Monday) set explicitly, not the default calendar, or
  the two engines will disagree on ISO week numbers near year boundaries.

## Numbers

- **R8's completion percentage is rounded half-up to 4 decimal places, not
  banker's rounding.** Kotlin: `BigDecimal(complete).divide(BigDecimal(counted),
  4, RoundingMode.HALF_UP)`. Swift's `Double.rounded()` and `(x *
  10000).rounded() / 10000` both use `.toNearestOrAwayFromZero` for positive
  values, which happens to agree with half-up here since `completionPct` is
  never negative — but that's incidental, not load-bearing. The Swift port
  should use `Decimal` with `NSDecimalRound(_:_:_:.plain)` (Foundation's
  half-up mode) rather than `Double` rounding, so a value that lands exactly on
  a rounding boundary is guaranteed to resolve the same way as Kotlin's
  `BigDecimal`, not just by coincidence of both being positive.

## Engine

`RulesEngine.resolve()` is complete (R1-R11) as of slice 4
(`docs/tasks/06-engine-r9-r10.md`). It runs in three stages, each consuming
only the previous stage's output:

1. **Day state.** Each date from `start_date` to `end_date` gets a
   provisional state ignoring rest tokens entirely (R1-R5: today, range,
   schedule, satisfaction, and the complete/pending/missed/partial ladder).
   A second pass over those same dates then spends rest tokens where flagged,
   valid, and needed, converting some `partial`/`missed` results to `rest`
   (R6). This has to be two passes because a token's validity depends on how
   many earlier dates in the same ISO week already spent one, which in turn
   depends on those dates' provisional states already being decided.
2. **Pure consumers of the final day states.** Which dates are sealed (R7),
   completion percentage (R8), and current/best streak (R11) are all computed
   independently from the finished `dayStates` map. None of them feed back
   into day state, and they don't depend on each other.
3. **Arc-level status.** Walking sealed dates ascending, a `strict` arc fails
   on the first `partial`/`missed` one found (R9); otherwise an `active` arc
   that has run past its end date is `completed` (R10). R9 is checked first,
   so a strict arc that both failed and outran its end date reports `failed`.

What the engine does **not** do, left for the app layer: R12 (milestone rows
— `start`/`p25`/`p50`/`p75`/`final` — are persisted objects, not derived
values), R13 (editing a live arc: `active_from`/`active_until` bookkeeping
when commitments are added, removed, or retargeted), and persisting the
consequences of R9/R10 (writing `completed_at`, creating the `final`
milestone row — the engine only reports the derived status, it never writes
to the database). R14 (clock manipulation) needs no app code beyond this: it
is satisfied by the engine simply not special-casing a backwards clock jump,
which `edge_clock_jumps_backwards` confirms.
