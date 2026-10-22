package dev.priyanshu.arc.ui.today

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTextTest {

    @Test
    fun `all seven days is every day`() {
        assertEquals("Every day", formatSchedule(DayOfWeek.entries.toSet()))
    }

    @Test
    fun `monday to friday is weekdays`() {
        assertEquals(
            "Weekdays",
            formatSchedule(
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            ),
        )
    }

    @Test
    fun `saturday and sunday is the weekend sentence`() {
        assertEquals("Saturdays and Sundays", formatSchedule(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
    }

    @Test
    fun `any other set is short names, monday first`() {
        // Given out of order on purpose: the output is always Monday-first.
        assertEquals(
            "Mon, Wed, Fri",
            formatSchedule(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        )
    }
}
