package dev.priyanshu.arc.ui.create

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

// Task 19 — the words the commitments step and its editor show for a draft.
class CreateArcTextTest {
    private lateinit var previousLocale: Locale

    // detailText formats amounts for the phone's language; pin it so
    // "3,000" is what the test expects.
    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.UK)
    }

    @After
    fun tearDown() = Locale.setDefault(previousLocale)

    @Test
    fun `schedule hint asks for days when none are picked`() {
        assertEquals("Pick the days it applies.", scheduleHint(emptySet()))
    }

    @Test
    fun `schedule hint for all seven days has no other days`() {
        assertEquals("Every day of the week.", scheduleHint((1..7).toSet()))
    }

    @Test
    fun `schedule hint names the days and what happens on the rest`() {
        assertEquals("Mon, Wed, Fri. On other days it won't show on Today.", scheduleHint(setOf(5, 1, 3)))
        assertEquals("Weekdays. On other days it won't show on Today.", scheduleHint((1..5).toSet()))
    }

    @Test
    fun `detail text for each kind`() {
        assertEquals("Tick off · Every day", CommitmentDraft(name = "Read").detailText())
        assertEquals(
            "Reach 3,000 ml · Every day",
            CommitmentDraft(name = "Water", type = CommitmentType.COUNTER, targetText = "3000", unit = "ml").detailText(),
        )
        assertEquals(
            "Spend 2.5 h · Every day",
            CommitmentDraft(name = "Study", type = CommitmentType.DURATION, targetText = "2.5", unit = "h").detailText(),
        )
        assertEquals("Record a number · Every day", CommitmentDraft(name = "Weight", type = CommitmentType.LOG).detailText())
    }

    @Test
    fun `detail text uses the schedule words from Today`() {
        val gym = CommitmentDraft(name = "Gym", scheduleType = ScheduleType.WEEKDAYS)
        assertEquals("Tick off · Mon, Wed, Fri", gym.copy(scheduleDays = setOf(1, 3, 5)).detailText())
        assertEquals("Tick off · Saturdays and Sundays", gym.copy(scheduleDays = setOf(6, 7)).detailText())
    }
}
