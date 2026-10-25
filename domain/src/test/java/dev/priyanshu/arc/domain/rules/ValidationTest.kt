package dev.priyanshu.arc.domain.rules

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// R16–R18, R20, R21 — plain unit tests, not fixtures: these are validation
// rules for arc creation, not something RulesEngine.resolve() computes.
class ValidationTest {

    @Test
    fun `earliest allowed start date is today minus backfill days`() {
        val today = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 1, 13), earliestAllowedStartDate(today, backfillDays = 2))
        assertEquals(today, earliestAllowedStartDate(today, backfillDays = 0))
    }

    @Test
    fun `start date exactly at the earliest allowed boundary is valid`() {
        val today = LocalDate.of(2026, 1, 15)
        assertTrue(isValidStartDate(LocalDate.of(2026, 1, 13), today, backfillDays = 2))
    }

    @Test
    fun `start date one day before the boundary is invalid`() {
        val today = LocalDate.of(2026, 1, 15)
        assertFalse(isValidStartDate(LocalDate.of(2026, 1, 12), today, backfillDays = 2))
    }

    @Test
    fun `start date equal to today is always valid`() {
        val today = LocalDate.of(2026, 1, 15)
        assertTrue(isValidStartDate(today, today, backfillDays = 0))
    }

    @Test
    fun `start date in the future is valid - no upper bound`() {
        val today = LocalDate.of(2026, 1, 15)
        assertTrue(isValidStartDate(LocalDate.of(2026, 6, 1), today, backfillDays = 2))
    }

    // R17 — arc name.

    @Test
    fun `a name with non-whitespace characters is valid`() {
        assertTrue(isValidArcName("September Reset"))
        assertTrue(isValidArcName("  trimmed later, but has content  "))
    }

    @Test
    fun `an empty or whitespace-only name is invalid`() {
        assertFalse(isValidArcName(""))
        assertFalse(isValidArcName("   "))
        assertFalse(isValidArcName("\t\n"))
    }

    // R18 — at least one required commitment.

    @Test
    fun `at least one required commitment is valid`() {
        assertTrue(hasAtLeastOneRequiredCommitment(listOf(true)))
        assertTrue(hasAtLeastOneRequiredCommitment(listOf(false, true, false)))
    }

    @Test
    fun `all-bonus or empty commitment sets are invalid`() {
        assertFalse(hasAtLeastOneRequiredCommitment(listOf(false, false)))
        assertFalse(hasAtLeastOneRequiredCommitment(emptyList()))
    }

    // R16 with R1 — the worked example in rules.md: before the boundary hour,
    // today is still yesterday, and the earliest start moves with it.

    @Test
    fun `R16 worked example - 02-30 with boundary 4 and backfill 2`() {
        val now = LocalDateTime.of(2026, 1, 15, 2, 30)
        val today = effectiveToday(now, boundaryHour = 4)
        assertEquals(LocalDate.of(2026, 1, 14), today)
        assertEquals(LocalDate.of(2026, 1, 12), earliestAllowedStartDate(today, backfillDays = 2))
    }

    @Test
    fun `the same moment with boundary 0 is already the calendar day`() {
        val now = LocalDateTime.of(2026, 1, 15, 2, 30)
        val today = effectiveToday(now, boundaryHour = 0)
        assertEquals(LocalDate.of(2026, 1, 15), today)
        assertEquals(LocalDate.of(2026, 1, 13), earliestAllowedStartDate(today, backfillDays = 2))
    }

    // R2 — the inclusive day count, from two dates.

    @Test
    fun `total days counts both the start and the end day`() {
        assertEquals(5, totalDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)))
        assertEquals(1, totalDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)))
    }

    // R20 — a valid commitment.

    private fun problems(
        name: String = "Water",
        type: CommitmentType = CommitmentType.COUNTER,
        targetValue: Double? = 3000.0,
        unit: String? = "ml",
        scheduleType: ScheduleType = ScheduleType.DAILY,
        scheduleDays: Set<Int> = emptySet(),
    ) = commitmentProblems(name, type, targetValue, unit, scheduleType, scheduleDays)

    @Test
    fun `a valid commitment of each type has no problems`() {
        assertEquals(emptyList<CommitmentProblem>(), problems())
        assertEquals(emptyList<CommitmentProblem>(), problems(type = CommitmentType.DURATION, targetValue = 2.5, unit = "h"))
        assertEquals(emptyList<CommitmentProblem>(), problems(type = CommitmentType.BINARY, targetValue = null, unit = null))
        assertEquals(emptyList<CommitmentProblem>(), problems(type = CommitmentType.LOG, targetValue = null, unit = null))
        assertEquals(
            emptyList<CommitmentProblem>(),
            problems(scheduleType = ScheduleType.WEEKDAYS, scheduleDays = setOf(1, 7)),
        )
    }

    @Test
    fun `a blank name is a problem`() {
        assertEquals(listOf(CommitmentProblem.BLANK_NAME), problems(name = ""))
        assertEquals(listOf(CommitmentProblem.BLANK_NAME), problems(name = "  \t"))
    }

    @Test
    fun `a counter or duration needs a target above 0`() {
        for (type in listOf(CommitmentType.COUNTER, CommitmentType.DURATION)) {
            assertEquals(listOf(CommitmentProblem.TARGET_NOT_POSITIVE), problems(type = type, targetValue = null))
            assertEquals(listOf(CommitmentProblem.TARGET_NOT_POSITIVE), problems(type = type, targetValue = 0.0))
            assertEquals(listOf(CommitmentProblem.TARGET_NOT_POSITIVE), problems(type = type, targetValue = -1.0))
        }
    }

    @Test
    fun `NaN and Infinity are not targets`() {
        assertEquals(listOf(CommitmentProblem.TARGET_NOT_POSITIVE), problems(targetValue = Double.NaN))
        assertEquals(listOf(CommitmentProblem.TARGET_NOT_POSITIVE), problems(targetValue = Double.POSITIVE_INFINITY))
    }

    @Test
    fun `a counter or duration needs a unit`() {
        assertEquals(listOf(CommitmentProblem.MISSING_UNIT), problems(unit = null))
        assertEquals(listOf(CommitmentProblem.MISSING_UNIT), problems(type = CommitmentType.DURATION, unit = " "))
    }

    @Test
    fun `binary and log need neither target nor unit`() {
        assertEquals(emptyList<CommitmentProblem>(), problems(type = CommitmentType.BINARY, targetValue = 0.0, unit = ""))
        assertEquals(emptyList<CommitmentProblem>(), problems(type = CommitmentType.LOG, targetValue = null, unit = null))
    }

    @Test
    fun `a weekdays schedule needs at least one day`() {
        assertEquals(listOf(CommitmentProblem.NO_DAYS), problems(scheduleType = ScheduleType.WEEKDAYS))
    }

    @Test
    fun `weekdays outside 1 to 7 are a problem`() {
        assertEquals(
            listOf(CommitmentProblem.DAY_OUT_OF_RANGE),
            problems(scheduleType = ScheduleType.WEEKDAYS, scheduleDays = setOf(0, 3)),
        )
        assertEquals(
            listOf(CommitmentProblem.DAY_OUT_OF_RANGE),
            problems(scheduleType = ScheduleType.WEEKDAYS, scheduleDays = setOf(8)),
        )
    }

    @Test
    fun `a daily schedule ignores its days`() {
        assertEquals(emptyList<CommitmentProblem>(), problems(scheduleType = ScheduleType.DAILY, scheduleDays = setOf(9)))
    }

    @Test
    fun `several problems are all reported, in order`() {
        assertEquals(
            listOf(
                CommitmentProblem.BLANK_NAME,
                CommitmentProblem.TARGET_NOT_POSITIVE,
                CommitmentProblem.MISSING_UNIT,
                CommitmentProblem.NO_DAYS,
            ),
            problems(name = " ", targetValue = null, unit = null, scheduleType = ScheduleType.WEEKDAYS),
        )
    }

    // R21 — creation limits.

    @Test
    fun `rest tokens per week are valid from 0 to 7`() {
        assertTrue(isValidRestTokensPerWeek(0))
        assertTrue(isValidRestTokensPerWeek(7))
        assertFalse(isValidRestTokensPerWeek(8))
        assertFalse(isValidRestTokensPerWeek(-1))
    }

    @Test
    fun `only standard arcs store rest tokens`() {
        assertEquals(2, restTokensToStore(Strictness.STANDARD, 2))
        assertEquals(0, restTokensToStore(Strictness.STRICT, 2))
        assertEquals(0, restTokensToStore(Strictness.CHILL, 2))
    }

    @Test
    fun `a tenth commitment can be added, an eleventh can't`() {
        assertTrue(canAddCommitment(0))
        assertTrue(canAddCommitment(9))
        assertFalse(canAddCommitment(10))
    }
}
