package dev.priyanshu.arc.domain.rules

import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// R16 — plain unit tests, not fixtures: this is a validation rule for arc
// creation, not something RulesEngine.resolve() computes.
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
}
