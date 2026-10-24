package dev.priyanshu.arc.ui.create

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

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

    @Test
    fun `a target that isn't a number says so`() {
        assertEquals("Write the target as a number, like 2.5.", water.copy(targetText = "lots").error)
        assertEquals("Set a target above 0.", water.copy(targetText = "").error)
        assertEquals("Set a target above 0.", water.copy(targetText = "0").error)
    }

    @Test
    fun `a dot is always the decimal mark`() {
        assertEquals(2.5, parseAmount("2.5", '.')!!, 0.0)
        assertEquals(2.5, parseAmount(" 2.5 ", ',')!!, 0.0)
    }

    @Test
    fun `a comma is the decimal mark only where the device uses one`() {
        assertEquals(2.5, parseAmount("2,5", ',')!!, 0.0)
        // On an English phone "3,000" is not quietly read as 3.
        assertNull(parseAmount("3,000", '.'))
        assertNull(parseAmount("2,5", '.'))
    }

    @Test
    fun `a comma decimal on a comma phone is a valid target`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val draft = water.copy(targetText = "2,5")
            assertEquals(2.5, draft.targetValue!!, 0.0)
            assertNull(draft.error)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
