package dev.priyanshu.arc.ui.create

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import java.time.LocalDate
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

    // Piece 7 — the strict card explains R7 sealing with the arc's own backfill.
    @Test
    fun `strict description with no backfill says days lock as soon as they end`() {
        assertEquals(
            "A day you don't finish ends the arc. Days lock as soon as they end.",
            Strictness.STRICT.description(backfillDays = 0),
        )
    }

    @Test
    fun `strict description counts the backfill days`() {
        assertEquals(
            "A day you don't finish ends the arc. You get 1 day to fill a day in before it locks.",
            Strictness.STRICT.description(backfillDays = 1),
        )
        assertEquals(
            "A day you don't finish ends the arc. You get 2 days to fill a day in before it locks.",
            Strictness.STRICT.description(backfillDays = 2),
        )
    }

    @Test
    fun `standard and chill descriptions don't depend on backfill`() {
        assertEquals(Strictness.STANDARD.description(0), Strictness.STANDARD.description(5))
        assertEquals(Strictness.CHILL.description(0), Strictness.CHILL.description(5))
    }

    // Piece 8 — the review cards.
    private val today = LocalDate.of(2026, 10, 22) // a Thursday
    private val base = CreateArcUiState(now = today.atTime(10, 0), startDate = today, endDate = today.plusDays(59))

    @Test
    fun `starts text says starts today when the arc starts today`() {
        assertEquals("starts today", base.startsText())
    }

    @Test
    fun `starts text for a future and a past start`() {
        assertEquals("starts Sat 24 Oct", base.copy(startDate = today.plusDays(2)).startsText())
        assertEquals("began Tue 20 Oct", base.copy(startDate = today.minusDays(2)).startsText())
    }

    @Test
    fun `backfill text on the review card`() {
        assertEquals("only today can be filled in", base.copy(backfillDays = 0).backfillText())
        assertEquals("fill in up to 1 day back", base.copy(backfillDays = 1).backfillText())
        assertEquals("fill in up to 2 days back", base.backfillText())
    }

    @Test
    fun `rest days text`() {
        assertEquals("No rest days", base.copy(restTokensPerWeek = 0).restDaysText())
        assertEquals("1 rest day a week", base.copy(restTokensPerWeek = 1).restDaysText())
        assertEquals("3 rest days a week", base.copy(restTokensPerWeek = 3).restDaysText())
    }
}
