package dev.priyanshu.arc.ui.create

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// Task 19 — what stops a commitment draft being saved, and how it's tidied
// before it goes into the list. Plain data, no ViewModel.
class CommitmentDraftTest {
    private val water = CommitmentDraft(
        name = "Water",
        type = CommitmentType.COUNTER,
        targetText = "2000",
        unit = "ml",
    )

    @Test
    fun `a complete counter has no error`() {
        assertNull(water.error)
    }

    @Test
    fun `a binary commitment needs only a name`() {
        assertNull(CommitmentDraft(name = "Read").error)
    }

    @Test
    fun `a blank name is an error`() {
        assertNotNull(water.copy(name = "   ").error)
    }

    @Test
    fun `a counter or duration needs a target above 0`() {
        assertNotNull(water.copy(targetText = "").error)
        assertNotNull(water.copy(targetText = "0").error)
        assertNotNull(water.copy(targetText = "-5").error)
        assertNotNull(water.copy(targetText = "lots").error)
        assertNotNull(water.copy(type = CommitmentType.DURATION, targetText = "").error)
    }

    @Test
    fun `NaN and Infinity are not targets`() {
        assertNotNull(water.copy(targetText = "NaN").error)
        assertNotNull(water.copy(targetText = "Infinity").error)
        assertNotNull(water.copy(targetText = "-Infinity").error)
    }

    @Test
    fun `a counter or duration needs a unit`() {
        assertNotNull(water.copy(unit = null).error)
        assertNotNull(water.copy(unit = "  ").error)
    }

    @Test
    fun `a weekdays schedule needs at least one day`() {
        val weekdays = water.copy(scheduleType = ScheduleType.WEEKDAYS)
        assertNotNull(weekdays.error)
        assertNull(weekdays.copy(scheduleDays = setOf(1, 3, 5)).error)
    }

    @Test
    fun `the target keeps its decimals`() {
        assertEquals(72.5, water.copy(targetText = " 72.5 ").targetValue)
    }

    @Test
    fun `cleaned trims the name, target and unit`() {
        val cleaned = water.copy(name = "  Water ", targetText = " 2000 ", unit = " ml ").cleaned()
        assertEquals("Water", cleaned.name)
        assertEquals("2000", cleaned.targetText)
        assertEquals("ml", cleaned.unit)
    }

    @Test
    fun `cleaned drops a target and unit that don't apply to the type`() {
        val cleaned = water.copy(type = CommitmentType.BINARY).cleaned()
        assertEquals("", cleaned.targetText)
        assertNull(cleaned.unit)
        assertNull(cleaned.targetValue)
    }

    @Test
    fun `cleaned drops weekdays from a daily schedule`() {
        val cleaned = water.copy(scheduleType = ScheduleType.DAILY, scheduleDays = setOf(1, 2)).cleaned()
        assertEquals(emptySet<Int>(), cleaned.scheduleDays)
    }
}
