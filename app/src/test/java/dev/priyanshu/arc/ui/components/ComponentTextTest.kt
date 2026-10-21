package dev.priyanshu.arc.ui.components

import dev.priyanshu.arc.domain.model.DayState
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// The text the Today components produce from plain data: TalkBack labels for
// the week strip, and how amounts are written. Pinned to an English locale,
// since day names and number grouping both follow the device locale.
class ComponentTextTest {
    private lateinit var savedLocale: Locale

    @Before
    fun setUp() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun tearDown() {
        Locale.setDefault(savedLocale)
    }

    // 2026-01-19 is a Monday.
    private val monday = LocalDate.of(2026, 1, 19)

    @Test
    fun `every disc reads its day and state`() {
        assertEquals("Monday, complete", dayDiscDescription(WeekStripDay(monday, DayState.COMPLETE, isToday = false)))
        assertEquals("Tuesday, partial", dayDiscDescription(WeekStripDay(monday.plusDays(1), DayState.PARTIAL, isToday = false)))
        assertEquals("Thursday, missed", dayDiscDescription(WeekStripDay(monday.plusDays(3), DayState.MISSED, isToday = false)))
        assertEquals("Friday, rest day", dayDiscDescription(WeekStripDay(monday.plusDays(4), DayState.REST, isToday = false)))
        assertEquals("Sunday, still to come", dayDiscDescription(WeekStripDay(monday.plusDays(6), DayState.FUTURE, isToday = false)))
        assertEquals(
            "Monday, not part of this arc",
            dayDiscDescription(WeekStripDay(monday, DayState.OUT_OF_RANGE, isToday = false)),
        )
    }

    @Test
    fun `today is called out in its description`() {
        assertEquals(
            "Wednesday, today, in progress",
            dayDiscDescription(WeekStripDay(monday.plusDays(2), DayState.PENDING, isToday = true)),
        )
        assertEquals(
            "Wednesday, today, rest day",
            dayDiscDescription(WeekStripDay(monday.plusDays(2), DayState.REST, isToday = true)),
        )
    }

    @Test
    fun `amounts are grouped with no trailing zero`() {
        assertEquals("2,100", formatAmount(2100.0))
        assertEquals("3,000", formatAmount(3000.0))
        assertEquals("72.5", formatAmount(72.5))
        assertEquals("0", formatAmount(0.0))
    }

    @Test
    fun `input amounts parse straight back`() {
        assertEquals("70", formatInputAmount(70.0))
        assertEquals("72.5", formatInputAmount(72.5))
        assertEquals("2100", formatInputAmount(2100.0))
        assertEquals(2100.0, formatInputAmount(2100.0).toDouble(), 0.0)
    }
}
