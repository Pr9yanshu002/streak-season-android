package dev.priyanshu.arc.domain.rules

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import java.time.LocalDate

// R16–R18, R20, R21 — validation for arc creation, not something resolve()
// computes. Public (not internal): unlike isScheduled/isSatisfied, these are
// called from :app's create flow.

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

/** R17 — an arc's name must contain at least one non-whitespace character. */
fun isValidArcName(name: String): Boolean = name.isNotBlank()

/**
 * R18 — an arc must have at least one required commitment. An all-bonus arc
 * computes 0% completion for its whole life under R8 (no day ever has a
 * required commitment scheduled, so the counted set is always empty), which is
 * correct but meaningless, so it is blocked at creation.
 *
 * Takes the commitments' is-required flags rather than a commitment shape:
 * :domain has no notion of the app's creation-time draft, so the caller maps
 * its drafts to `List<Boolean>` and this checks the rule over them.
 */
fun hasAtLeastOneRequiredCommitment(commitmentIsRequiredFlags: List<Boolean>): Boolean =
    commitmentIsRequiredFlags.any { it }

/** R21 — the most rest tokens an arc may have per week. A week has seven days, so more could never be used (R6). */
const val MAX_REST_TOKENS_PER_WEEK = 7

/** R21 — the most commitments an arc may have when it is created. */
const val MAX_COMMITMENTS_PER_ARC = 10

/** R21 — whether a weekly rest-token allowance is within 0–[MAX_REST_TOKENS_PER_WEEK]. */
fun isValidRestTokensPerWeek(n: Int): Boolean = n in 0..MAX_REST_TOKENS_PER_WEEK

/**
 * R21 — the allowance to store for a new arc. Only standard arcs have rest
 * tokens; strict and chill store 0, matching how R6 already treats them.
 */
fun restTokensToStore(strictness: Strictness, restTokensPerWeek: Int): Int =
    if (strictness == Strictness.STANDARD) restTokensPerWeek else 0

/** R21 — whether another commitment may be added to an arc that has [currentCount] already. */
fun canAddCommitment(currentCount: Int): Boolean = currentCount < MAX_COMMITMENTS_PER_ARC

/** R20 — one way a commitment fails to be valid, in the order [commitmentProblems] reports them. */
enum class CommitmentProblem {
    /** R20.1 — the name has no non-whitespace character. */
    BLANK_NAME,

    /** R20.2 — a counter or duration whose target is missing, not above 0, or not a finite number. */
    TARGET_NOT_POSITIVE,

    /** R20.2 — a counter or duration with a blank unit. */
    MISSING_UNIT,

    /** R20.4 — a weekdays schedule with no days. */
    NO_DAYS,

    /** R20.4 — a weekdays schedule with a day outside 1–7. */
    DAY_OUT_OF_RANGE,
}

/**
 * R20 — every problem that stops a commitment being created, in the order of
 * [CommitmentProblem], or an empty list when it is valid.
 *
 * R20.3 (binary and log have no target) has no problem here: it isn't
 * something a person can get wrong. Callers clear the target, and the unit,
 * for those types when saving.
 *
 * Takes plain values rather than a commitment shape, like
 * [hasAtLeastOneRequiredCommitment]: :domain has no notion of the app's draft.
 */
fun commitmentProblems(
    name: String,
    type: CommitmentType,
    targetValue: Double?,
    unit: String?,
    scheduleType: ScheduleType,
    scheduleDays: Set<Int>,
): List<CommitmentProblem> {
    val problems = mutableListOf<CommitmentProblem>()
    val needsTarget = type == CommitmentType.COUNTER || type == CommitmentType.DURATION

    if (name.isBlank()) problems += CommitmentProblem.BLANK_NAME
    // isFinite: NaN and Infinity aren't targets anyone can reach (R4).
    if (needsTarget && (targetValue == null || !targetValue.isFinite() || targetValue <= 0.0)) {
        problems += CommitmentProblem.TARGET_NOT_POSITIVE
    }
    if (needsTarget && unit.isNullOrBlank()) problems += CommitmentProblem.MISSING_UNIT
    if (scheduleType == ScheduleType.WEEKDAYS) {
        if (scheduleDays.isEmpty()) problems += CommitmentProblem.NO_DAYS
        if (scheduleDays.any { it !in 1..7 }) problems += CommitmentProblem.DAY_OUT_OF_RANGE
    }
    return problems
}
