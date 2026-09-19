package dev.priyanshu.arc.domain.rules

import java.time.LocalDate

// R16 — validation for arc creation, not something resolve() computes. Public
// (not internal): unlike isScheduled/isSatisfied, this is meant to be called
// from :app when arc creation is built.

/**
 * R16 — the earliest date an arc may start on. Any earlier and the arc would
 * be created with sealed missed days (R7) already behind it, which fails a
 * strict arc immediately (R9). There is no latest bound — a start date may be
 * in the future; before the arc starts, `dayNumber` is 0 (R2).
 */
fun earliestAllowedStartDate(today: LocalDate, backfillDays: Int): LocalDate =
    today.minusDays(backfillDays.toLong())

/** R16 — whether a proposed start date is valid, given today and the arc's backfill window. */
fun isValidStartDate(startDate: LocalDate, today: LocalDate, backfillDays: Int): Boolean =
    !startDate.isBefore(earliestAllowedStartDate(today, backfillDays))
