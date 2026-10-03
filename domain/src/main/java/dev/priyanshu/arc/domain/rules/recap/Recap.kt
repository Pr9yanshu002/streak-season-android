package dev.priyanshu.arc.domain.rules.recap

import dev.priyanshu.arc.domain.model.Arc
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.Day
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.Entry
import dev.priyanshu.arc.domain.rules.Resolution
import dev.priyanshu.arc.domain.rules.bestStreak
import dev.priyanshu.arc.domain.rules.completionPct
import dev.priyanshu.arc.domain.rules.countedDates
import dev.priyanshu.arc.domain.rules.dayNumber
import dev.priyanshu.arc.domain.rules.elapsedDates
import dev.priyanshu.arc.domain.rules.isScheduled
import dev.priyanshu.arc.domain.rules.isSatisfied
import dev.priyanshu.arc.domain.rules.isoWeekKey
import java.time.LocalDate

// R19 — recap statistics. A separate pure function over the same inputs as
// resolve() plus the Resolution it produced; kept in its own file (and package)
// rather than in RulesEngine.kt because it is not part of resolve() and the
// engine file was already large. It reaches back into the engine's internal
// R8/R11 helpers (same module, so `internal` is visible) — it never redefines
// "which days count".

data class Recap(
    val status: ArcStatus,          // COMPLETED or FAILED (recap exists only for these)
    val completeDays: Int,          // headline numerator — R8 complete days in the recap period
    val countedDays: Int,           // headline denominator — R8 counted set in the recap period
    val completionPct: Double,      // the SAME ratio, R8-rounded — "two views of one ratio"
    // recap-period length: totalDays if completed, dayNumber at failedOnDate if
    // failed ("made it to day 19").
    val daysReached: Int,
    val bestStreak: Int,            // R11 best streak, restricted to the recap period
    val hardestWeek: HardestWeek?,  // null if no week has >= 3 counted days
    val commitmentTotals: List<CommitmentTotal>,  // one per input commitment, input order
)

data class HardestWeek(
    val weekKey: String,            // R6 ISO week key, e.g. "2026-W03"
    val completeDays: Int,
    val countedDays: Int,
)

sealed interface CommitmentTotal {
    val commitmentId: String

    data class Binary(
        override val commitmentId: String,
        val daysDone: Int,
    ) : CommitmentTotal

    // counter + duration: identical stat, so one type. Unit/formatting is the UI's job.
    data class Measured(
        override val commitmentId: String,
        val total: Double,
        val daysTargetMet: Int,
    ) : CommitmentTotal

    data class Log(
        override val commitmentId: String,
        val first: Double?,         // by date; null if nothing recorded in the period
        val last: Double?,
        val daysRecorded: Int,
    ) : CommitmentTotal
}

/**
 * R19 — recap statistics for an arc that has ended. Returns null unless the arc
 * is `completed` or `failed`.
 *
 * Takes the [resolution] rather than recomputing day states: R19 is defined in
 * terms of R8 (the counted set) and R11 (best streak), so it consumes their
 * output instead of re-deriving "which days count" a second time — the drift
 * this project exists to avoid.
 *
 * Every statistic is over the recap period: `start_date` through `end_date` for
 * a completed arc, or through `failed_on_date` for a failed one. That
 * restriction is achieved purely by passing the period's last day as the cutoff
 * to the existing R8/R11 helpers — they cap at whatever as-of date they are
 * given (see [elapsedDates]) — so days logged after a failure are never
 * produced and so never counted.
 */
fun recap(
    arc: Arc,
    commitments: List<Commitment>,
    days: List<Day>,
    resolution: Resolution,
): Recap? {
    val periodEnd = when (resolution.arcStatus) {
        ArcStatus.COMPLETED -> arc.endDate
        ArcStatus.FAILED -> requireNotNull(resolution.failedOnDate) {
            "a failed arc must carry failedOnDate (R9)"
        }
        else -> return null
    }

    val dayStates = resolution.dayStates
    val periodDates = elapsedDates(arc, periodEnd)

    // Headline (R19) and completion percentage (R8) are two views of one ratio:
    // both read the same countedDates, so they cannot disagree.
    val counted = countedDates(arc, commitments, dayStates, periodEnd)
    val completeDays = counted.count { dayStates.getValue(it) == DayState.COMPLETE }
    val completionPct = completionPct(arc, commitments, dayStates, periodEnd)

    // daysReached: totalDays on a completed arc (dayNumber at endDate), or the
    // day the arc reached before failing (dayNumber at failedOnDate).
    val daysReached = dayNumber(arc, periodEnd)
    val bestStreak = bestStreak(arc, dayStates, periodEnd)

    return Recap(
        status = resolution.arcStatus,
        completeDays = completeDays,
        countedDays = counted.size,
        completionPct = completionPct,
        daysReached = daysReached,
        bestStreak = bestStreak,
        hardestWeek = hardestWeek(counted, dayStates),
        commitmentTotals = commitments.map { commitmentTotal(it, periodDates, days) },
    )
}

/**
 * R19 — the ISO week (same keys as R6) with the lowest ratio of complete days
 * to counted days, among weeks with at least 3 counted days. Ties go to the
 * earliest week; null if no week qualifies.
 *
 * [countedDates] is already R8's counted set for the recap period, so bucketing
 * it by ISO week keeps the headline, the completion percentage and the hardest
 * week all citing the same definition of a counted day.
 */
private fun hardestWeek(
    countedDates: List<LocalDate>,
    dayStates: Map<LocalDate, DayState>,
): HardestWeek? {
    val byWeek = countedDates.groupBy { isoWeekKey(it) }

    var hardest: HardestWeek? = null
    // Earliest week first (isoWeekKey's "YYYY-Www" sorts chronologically), and
    // only replace on a strictly lower ratio, so a tie keeps the earliest week.
    for (weekKey in byWeek.keys.sorted()) {
        val datesThisWeek = byWeek.getValue(weekKey)
        if (datesThisWeek.size < 3) continue

        val weekCounted = datesThisWeek.size
        val weekComplete = datesThisWeek.count { dayStates.getValue(it) == DayState.COMPLETE }

        val current = hardest
        // weekComplete/weekCounted < current.completeDays/current.countedDays,
        // cross-multiplied to avoid floating-point comparison (Swift parity).
        val isLower = current == null ||
            weekComplete.toLong() * current.countedDays < current.completeDays.toLong() * weekCounted
        if (isLower) {
            hardest = HardestWeek(weekKey, completeDays = weekComplete, countedDays = weekCounted)
        }
    }
    return hardest
}

/**
 * R19 — one commitment's totals over the recap period, across the days it was
 * scheduled and active. [isScheduled] already encodes activeFrom/activeUntil,
 * so a commitment removed mid-arc is counted only over its active days, and a
 * value logged on a day the commitment was not scheduled is never counted.
 * Soft-deleted entries never reach the engine, so there is nothing to exclude
 * here.
 */
private fun commitmentTotal(
    commitment: Commitment,
    periodDates: List<LocalDate>,
    days: List<Day>,
): CommitmentTotal {
    val daysByDate = days.associateBy { it.date }
    // periodDates is ascending, and filter preserves order, so scheduledDates —
    // and therefore the log's first/last below — are in date order.
    val scheduledDates = periodDates.filter { isScheduled(commitment, it) }
    fun entryOn(date: LocalDate): Entry? =
        daysByDate[date]?.entries?.firstOrNull { it.commitmentId == commitment.id }

    return when (commitment.type) {
        CommitmentType.BINARY -> CommitmentTotal.Binary(
            commitmentId = commitment.id,
            daysDone = scheduledDates.count { isSatisfied(commitment, entryOn(it)) },
        )
        CommitmentType.COUNTER, CommitmentType.DURATION -> CommitmentTotal.Measured(
            commitmentId = commitment.id,
            total = scheduledDates.sumOf { entryOn(it)?.value ?: 0.0 },
            daysTargetMet = scheduledDates.count { isSatisfied(commitment, entryOn(it)) },
        )
        CommitmentType.LOG -> {
            val recorded = scheduledDates.mapNotNull { entryOn(it)?.value }
            CommitmentTotal.Log(
                commitmentId = commitment.id,
                first = recorded.firstOrNull(),
                last = recorded.lastOrNull(),
                daysRecorded = recorded.size,
            )
        }
    }
}
