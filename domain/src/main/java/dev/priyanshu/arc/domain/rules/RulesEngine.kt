package dev.priyanshu.arc.domain.rules

import dev.priyanshu.arc.domain.model.Arc
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentStatus
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.Day
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.Entry
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

// Slice 1 (R1-R5): docs/tasks/03-engine-r1-r5.md.
// Slice 2 (R6): docs/tasks/04-engine-r6.md.
// Slice 3 (R7, R8, R11): docs/tasks/05-engine-r7-r8-r11.md.
// Slice 4 (R9, R10): docs/tasks/06-engine-r9-r10.md. All rules implemented.
object RulesEngine {
    /**
     * Resolves an arc's full state at [now]. Pure function: no I/O, no
     * persistence, no clock reads — every date-dependent decision is derived
     * from [now] alone. Three stages, each built on the last:
     *
     * 1. Per-date state ignoring rest tokens (R1-R5), then rest tokens applied
     *    on top (R6) — see [provisionalDayState] and [applyRestTokens].
     * 2. Pure consumers of the final day states: which dates are sealed (R7),
     *    completion percentage (R8), and streaks (R11). None of these feed
     *    back into day state.
     * 3. Arc-level status: strict-mode failure on the first sealed
     *    partial/missed date (R9), then completion once the arc has run past
     *    its end date without failing (R10).
     */
    fun resolve(
        arc: Arc,
        commitments: List<Commitment>,
        days: List<Day>,
        now: LocalDateTime,
    ): Resolution {
        val today = effectiveToday(now, arc.dayBoundaryHour)
        val daysByDate = days.associateBy { it.date }

        // Stage 1 (R1-R5): each date's state as if rest tokens didn't exist.
        // Per-commitment status (R3/R4) rides along in the same pass since it
        // needs the same entriesForDate lookup and is never touched by rest
        // tokens (R6 only ever changes the aggregate DayState).
        val provisionalStates = mutableMapOf<LocalDate, DayState>()
        val commitmentStatuses = mutableMapOf<LocalDate, Map<String, CommitmentStatus>>()
        var date = arc.startDate
        while (!date.isAfter(arc.endDate)) {
            val entriesForDate = daysByDate[date]?.entries ?: emptyList()
            provisionalStates[date] = provisionalDayState(arc, commitments, entriesForDate, date, today)
            commitmentStatuses[date] = commitmentStatusesForDate(commitments, entriesForDate, date)
            date = date.plusDays(1)
        }

        // Stage 2 (R6): walk the same dates in order, spending a token wherever
        // one was flagged, valid, and needed. This has to be a second pass over
        // stage 1's results, not folded into it — whether Thursday's token is
        // valid depends on how many of the earlier days that same ISO week
        // already spent one, which in turn depends on stage 1 already having
        // decided whether each of those days would otherwise have failed.
        val (dayStates, restTokensRemaining) = applyRestTokens(arc, daysByDate, provisionalStates)
        val sealed = sealedDates(arc, today)

        // Stage 3 (R9/R10): with day states final, decide whether the arc has
        // failed (strict mode only, and only on sealed dates) or completed
        // (ran past its end date without failing). R9 is checked first — a
        // strict arc that both failed and outran its end date is failed, not
        // completed.
        val failedOnDate = if (arc.strictness == Strictness.STRICT) firstSealedFailure(arc, dayStates, sealed, today) else null
        val arcStatus = resolveArcStatus(arc, today, failedOnDate)

        return Resolution(
            effectiveToday = today,
            dayStates = dayStates,
            currentStreak = currentStreak(arc, dayStates, today),
            bestStreak = bestStreak(arc, dayStates, today),
            restTokensRemaining = restTokensRemaining,
            completionPct = completionPct(arc, commitments, dayStates, today),
            arcStatus = arcStatus,
            failedOnDate = failedOnDate,
            sealedDates = sealed,
            dayNumber = dayNumber(arc, today),
            totalDays = totalDays(arc),
            commitmentStatuses = commitmentStatuses,
        )
    }
}

/**
 * R1 — the current date ("today"). Derived from the local wall-clock time and
 * the arc's day-boundary hour: before the boundary hour, it's still yesterday.
 */
internal fun effectiveToday(now: LocalDateTime, boundaryHour: Int): LocalDate =
    if (now.hour < boundaryHour) now.toLocalDate().minusDays(1) else now.toLocalDate()

/** R2 — whether a date falls within the arc's start and end dates, inclusive at both ends. */
internal fun isInRange(arc: Arc, date: LocalDate): Boolean =
    !date.isBefore(arc.startDate) && !date.isAfter(arc.endDate)

/**
 * R2 — every date from the arc's start through today, capped at the arc's
 * end, inclusive at both ends. Empty if the arc hasn't started yet. Feeds
 * R8's completion percentage once that rule is implemented.
 */
internal fun elapsedDates(arc: Arc, today: LocalDate): List<LocalDate> {
    if (today.isBefore(arc.startDate)) return emptyList()
    val lastDate = if (arc.endDate.isBefore(today)) arc.endDate else today

    val dates = mutableListOf<LocalDate>()
    var date = arc.startDate
    while (!date.isAfter(lastDate)) {
        dates.add(date)
        date = date.plusDays(1)
    }
    return dates
}

/**
 * R3 — whether a commitment is scheduled on a date: active on that date, and
 * either daily or matching the weekday mask.
 */
internal fun isScheduled(commitment: Commitment, date: LocalDate): Boolean {
    if (date.isBefore(commitment.activeFrom)) return false
    val activeUntil = commitment.activeUntil
    if (activeUntil != null && date.isAfter(activeUntil)) return false

    return when (commitment.scheduleType) {
        ScheduleType.DAILY -> true
        ScheduleType.WEEKDAYS -> {
            val isoWeekday = date.dayOfWeek.value // Mon=1 .. Sun=7, matches rules.md
            isoWeekday in parseScheduleDays(commitment.scheduleDays)
        }
    }
}

// schedule_days is stored as a raw '1,3,5' string (schema.sql); parsing it is
// R3's job, not the domain model's.
private fun parseScheduleDays(scheduleDays: String?): Set<Int> {
    if (scheduleDays.isNullOrBlank()) return emptySet()
    return scheduleDays.split(",").map { it.trim().toInt() }.toSet()
}

/** R4 — whether an entry satisfies its commitment, per the commitment's type. */
internal fun isSatisfied(commitment: Commitment, entry: Entry?): Boolean {
    if (entry == null) return false
    return when (commitment.type) {
        CommitmentType.BINARY -> entry.value >= 1
        CommitmentType.COUNTER -> entry.value >= requireTargetValue(commitment)
        CommitmentType.DURATION -> entry.value >= requireTargetValue(commitment)
        CommitmentType.LOG -> true
    }
}

private fun requireTargetValue(commitment: Commitment): Double =
    requireNotNull(commitment.targetValue) { "commitment ${commitment.id} has no target_value" }

/**
 * R1-R5 — a single day's state, ignoring rest tokens entirely. This is stage 1
 * of R6's two-stage resolution: [applyRestTokens] (stage 2) converts some of
 * these `PARTIAL`/`MISSED` results into `REST`; everything else passes
 * through unchanged.
 */
internal fun provisionalDayState(
    arc: Arc,
    commitments: List<Commitment>,
    entries: List<Entry>,
    date: LocalDate,
    today: LocalDate,
): DayState {
    if (date.isAfter(today)) return DayState.FUTURE
    if (!isInRange(arc, date)) return DayState.OUT_OF_RANGE

    val entryByCommitmentId = entries.associateBy { it.commitmentId }
    val required = commitments.filter { it.isRequired && isScheduled(it, date) }
    val satisfied = required.filter { isSatisfied(it, entryByCommitmentId[it.id]) }

    if (required.isEmpty()) return DayState.COMPLETE
    if (satisfied.size == required.size) return DayState.COMPLETE
    if (date == today) return DayState.PENDING
    return if (satisfied.isEmpty()) DayState.MISSED else DayState.PARTIAL
}

/**
 * R6 — stage 2 of day-state resolution: applies rest tokens on top of stage
 * 1's provisional (rest-ignoring) states.
 *
 * Tokens exist only in standard-strictness arcs; in strict and chill the
 * allowance is 0 and a stored `rest_token_used` flag is ignored outright. A
 * token is spent only on a date flagged `rest_token_used` that would
 * otherwise resolve to `partial` or `missed` — a flag on an already-complete
 * date is a no-op. Validity is decided by walking each ISO week's dates in
 * ascending order and tracking how many tokens that week has already spent;
 * once the week's allowance is used up, later flagged dates in the same week
 * just keep their provisional state. Weeks never carry a token into the next
 * one — each week's running count starts over at 0.
 *
 * Returns the final per-date states plus `restTokensRemaining`
 * (allowance minus consumed, floored at 0) for every ISO week the arc's date
 * range touches.
 */
internal fun applyRestTokens(
    arc: Arc,
    daysByDate: Map<LocalDate, Day>,
    provisionalStates: Map<LocalDate, DayState>,
): Pair<Map<LocalDate, DayState>, Map<String, Int>> {
    val allowance = if (arc.strictness == Strictness.STANDARD) arc.restTokensPerWeek else 0

    val finalStates = mutableMapOf<LocalDate, DayState>()
    val consumedByWeek = mutableMapOf<String, Int>()

    var date = arc.startDate
    while (!date.isAfter(arc.endDate)) {
        val weekKey = isoWeekKey(date)
        consumedByWeek.putIfAbsent(weekKey, 0)

        val provisional = provisionalStates.getValue(date)
        val wouldOtherwiseFail = provisional == DayState.PARTIAL || provisional == DayState.MISSED
        val restTokenUsed = daysByDate[date]?.restTokenUsed ?: false
        val consumedSoFarThisWeek = consumedByWeek.getValue(weekKey)

        if (restTokenUsed && wouldOtherwiseFail && consumedSoFarThisWeek < allowance) {
            finalStates[date] = DayState.REST
            consumedByWeek[weekKey] = consumedSoFarThisWeek + 1
        } else {
            finalStates[date] = provisional
        }

        date = date.plusDays(1)
    }

    val restTokensRemaining = consumedByWeek.mapValues { (_, consumed) -> (allowance - consumed).coerceAtLeast(0) }
    return finalStates to restTokensRemaining
}

/**
 * R6 — the ISO week key a date belongs to, formatted `"<week-based year>-W<week>"`
 * (e.g. `"2026-W03"`), four-digit year and zero-padded two-digit week.
 *
 * Built from [IsoFields.WEEK_BASED_YEAR] / [IsoFields.WEEK_OF_WEEK_BASED_YEAR],
 * deliberately not [LocalDate.getYear] and not `WeekFields.of(Locale)`. The
 * week-based year isn't always the calendar year — e.g. 2026-12-31 falls in
 * week 1 of week-based-year 2027 — and a locale-dependent week field would
 * disagree with itself across devices, which would disagree with the Swift
 * port in turn. Swift's `Calendar` does not default to ISO-8601 week
 * semantics either, so the Swift port needs the equivalent explicit choice
 * (`Calendar(identifier: .iso8601)`), not whatever the current locale's
 * calendar happens to compute — see docs/notes.md.
 */
internal fun isoWeekKey(date: LocalDate): String {
    val weekBasedYear = date.get(IsoFields.WEEK_BASED_YEAR)
    val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    return "%04d-W%02d".format(weekBasedYear, week)
}

/**
 * R7 — every date in the arc that is sealed: no longer editable because more
 * than `backfillDays` days have passed since it. Today and any date still
 * inside the backfill window are left out. This only reports which dates are
 * sealed; enforcing that sealed dates reject edits is the app's job, not the
 * engine's.
 */
internal fun sealedDates(arc: Arc, today: LocalDate): Set<LocalDate> {
    val sealed = mutableSetOf<LocalDate>()
    var date = arc.startDate
    while (!date.isAfter(arc.endDate)) {
        if (today.isAfter(date.plusDays(arc.backfillDays.toLong()))) {
            sealed.add(date)
        }
        date = date.plusDays(1)
    }
    return sealed
}

/**
 * R9 — the first sealed date, walked ascending from the arc's start, that
 * resolved `partial` or `missed`. Only sealed dates are judged: the walk
 * `break`s (not `continue`s) at the first unsealed date, because a miss that
 * hasn't sealed yet is still fixable and everything after it is unjudgeable
 * until it does. Returns null if no sealed date has failed. Only meaningful
 * for `strict` arcs — callers must gate on strictness themselves, matching
 * `rules.md`'s "only applies when strictness = 'strict'".
 */
internal fun firstSealedFailure(
    arc: Arc,
    dayStates: Map<LocalDate, DayState>,
    sealedDates: Set<LocalDate>,
    today: LocalDate,
): LocalDate? {
    for (date in elapsedDates(arc, today)) {
        if (date !in sealedDates) break
        val state = dayStates.getValue(date)
        if (state == DayState.PARTIAL || state == DayState.MISSED) {
            return date
        }
    }
    return null
}

/**
 * R10 — the arc's derived status. A strict-mode failure (R9) always wins.
 * Otherwise, an `active` arc that has run past its end date is `completed`;
 * any other status passes through unchanged. The engine only reports this —
 * it does not mutate `arc` or persist `completed_at`/the `final` milestone,
 * both of which are the app's job.
 */
internal fun resolveArcStatus(arc: Arc, today: LocalDate, failedOnDate: LocalDate?): ArcStatus {
    if (failedOnDate != null) return ArcStatus.FAILED
    if (arc.status == ArcStatus.ACTIVE && today.isAfter(arc.endDate)) return ArcStatus.COMPLETED
    return arc.status
}

/**
 * R2 — the inclusive day count from the arc's start date to its end date. A
 * 2026-01-01 → 2026-01-05 arc is 5 days, not 4 — `ChronoUnit.DAYS.between` is
 * exclusive of the end date, hence the `+ 1`.
 */
internal fun totalDays(arc: Arc): Int = ChronoUnit.DAYS.between(arc.startDate, arc.endDate).toInt() + 1

/**
 * R2 — how far into the arc `today` is: the count of [elapsedDates]. `0`
 * before the arc starts (elapsedDates is empty), `1` on the start date, and
 * pinned at [totalDays] from the end date onward, since elapsedDates caps at
 * `endDate` and never grows past it.
 */
internal fun dayNumber(arc: Arc, today: LocalDate): Int = elapsedDates(arc, today).size

/**
 * R3/R4 — every commitment's status on a single date. Every commitment gets
 * an entry, including `NOT_SCHEDULED` — there is no "missing key" state.
 * Never affected by rest tokens (R6): those only ever change the aggregate
 * [DayState], not an individual commitment's satisfaction.
 */
internal fun commitmentStatusesForDate(
    commitments: List<Commitment>,
    entries: List<Entry>,
    date: LocalDate,
): Map<String, CommitmentStatus> {
    val entryByCommitmentId = entries.associateBy { it.commitmentId }
    return commitments.associate { commitment ->
        val status = when {
            !isScheduled(commitment, date) -> CommitmentStatus.NOT_SCHEDULED
            isSatisfied(commitment, entryByCommitmentId[commitment.id]) -> CommitmentStatus.DONE
            else -> CommitmentStatus.PENDING
        }
        commitment.id to status
    }
}

/** R3/R8 — whether at least one required commitment is scheduled on a date. */
private fun hasRequiredCommitmentScheduled(commitments: List<Commitment>, date: LocalDate): Boolean =
    commitments.any { it.isRequired && isScheduled(it, date) }

/**
 * R8 — the fraction of "countable" elapsed days that resolved `complete`.
 * A day is countable when it isn't `rest`, `pending` or `future`, and it had
 * at least one required commitment scheduled — so rest days, today while
 * still pending, and days with nothing required on them (a weekdays-only arc
 * on a Sunday) affect neither the numerator nor the denominator. `0.0` when
 * nothing is countable.
 */
internal fun completionPct(
    arc: Arc,
    commitments: List<Commitment>,
    dayStates: Map<LocalDate, DayState>,
    today: LocalDate,
): Double {
    val elapsed = elapsedDates(arc, today)
    val counted = elapsed.filter { date ->
        val state = dayStates.getValue(date)
        val excluded = state == DayState.REST || state == DayState.PENDING || state == DayState.FUTURE
        !excluded && hasRequiredCommitmentScheduled(commitments, date)
    }
    if (counted.isEmpty()) return 0.0

    val complete = counted.count { dayStates.getValue(it) == DayState.COMPLETE }
    return BigDecimal(complete).divide(BigDecimal(counted.size), 4, RoundingMode.HALF_UP).toDouble()
}

/**
 * R11 — the current streak: walk backwards from today, counting `complete`
 * and `rest` days, stopping at the first day that is neither (or at the
 * arc's start date). A `pending` today is skipped rather than counted or
 * treated as a break — the day isn't over yet.
 */
internal fun currentStreak(arc: Arc, dayStates: Map<LocalDate, DayState>, today: LocalDate): Int {
    var cursor = if (dayStates[today] == DayState.PENDING) today.minusDays(1) else today

    var streak = 0
    while (!cursor.isBefore(arc.startDate) && isStreakDay(dayStates[cursor])) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

/**
 * R11 — the best streak: the longest run of consecutive elapsed dates that
 * are `complete` or `rest`. A `pending` today is ignored entirely — it
 * neither extends nor breaks a run — everything else that isn't `complete`
 * or `rest` breaks it.
 */
internal fun bestStreak(arc: Arc, dayStates: Map<LocalDate, DayState>, today: LocalDate): Int {
    var best = 0
    var running = 0
    for (date in elapsedDates(arc, today)) {
        val state = dayStates.getValue(date)
        if (state == DayState.PENDING) continue

        running = if (isStreakDay(state)) running + 1 else 0
        if (running > best) best = running
    }
    return best
}

/** R11 — a day that keeps a streak alive: completed outright, or covered by a rest token. */
private fun isStreakDay(state: DayState?): Boolean = state == DayState.COMPLETE || state == DayState.REST

data class Resolution(
    val effectiveToday: LocalDate,
    val dayStates: Map<LocalDate, DayState>,
    val currentStreak: Int,
    val bestStreak: Int,
    val restTokensRemaining: Map<String, Int>,
    val completionPct: Double,
    val arcStatus: ArcStatus,
    val failedOnDate: LocalDate?,
    val sealedDates: Set<LocalDate>,
    val dayNumber: Int,
    val totalDays: Int,
    val commitmentStatuses: Map<LocalDate, Map<String, CommitmentStatus>>,
)
