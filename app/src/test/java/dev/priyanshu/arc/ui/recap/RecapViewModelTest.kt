package dev.priyanshu.arc.ui.recap

import dev.priyanshu.arc.data.RecapMilestone
import dev.priyanshu.arc.data.RecapScreenData
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.rules.recap.CommitmentTotal
import dev.priyanshu.arc.domain.rules.recap.HardestWeek
import dev.priyanshu.arc.domain.rules.recap.Recap
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The recap's card list is built by toUiState(), which applies the skip rules
// (docs/tasks/15). These exercise that mapping directly — no Room, no engine —
// since the rules are pure presentation over a RecapScreenData.
class RecapViewModelTest {

    @Test
    fun `blank intention skips the Intention card`() {
        val blank = screenData(intention = "   ").toUiState().cards
        val present = screenData(intention = "Get back in the habit.").toUiState().cards

        assertFalse("blank intention must not produce an Intention card", blank.any { it is RecapCard.Intention })
        assertTrue("a real intention must produce an Intention card", present.any { it is RecapCard.Intention })
    }

    @Test
    fun `null hardest week skips the Hardest Week card`() {
        val none = screenData(hardestWeek = null).toUiState().cards
        val some = screenData(
            hardestWeek = HardestWeek(
                weekKey = "2026-W02",
                weekStart = LocalDate.of(2026, 1, 5),
                weekEnd = LocalDate.of(2026, 1, 11),
                completeDays = 3,
                countedDays = 4,
            ),
        ).toUiState().cards

        assertFalse(none.any { it is RecapCard.HardestWeek })
        assertTrue(some.any { it is RecapCard.HardestWeek })
    }

    @Test
    fun `milestones with only start skip the Milestones card`() {
        val onlyStart = screenData(
            milestones = listOf(RecapMilestone(MilestoneKind.START, LocalDate.of(2026, 1, 1), reflection = null)),
        ).toUiState().cards
        val withP50 = screenData(
            milestones = listOf(
                RecapMilestone(MilestoneKind.START, LocalDate.of(2026, 1, 1), reflection = null),
                RecapMilestone(MilestoneKind.P50, LocalDate.of(2026, 1, 10), reflection = "halfway"),
            ),
        ).toUiState().cards

        assertFalse(onlyStart.any { it is RecapCard.Milestones })
        assertTrue(withP50.any { it is RecapCard.Milestones })
    }

    @Test
    fun `a full completed arc produces every card in order`() {
        val cards = screenData(
            intention = "Get back in the habit.",
            hardestWeek = HardestWeek("2026-W02", LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 11), 3, 4),
            milestones = listOf(
                RecapMilestone(MilestoneKind.START, LocalDate.of(2026, 1, 1), null),
                RecapMilestone(MilestoneKind.P50, LocalDate.of(2026, 1, 10), "halfway"),
            ),
        ).toUiState().cards

        assertEquals(
            listOf(
                RecapCard.Title::class,
                RecapCard.Intention::class,
                RecapCard.BestStreak::class,
                RecapCard.HardestWeek::class,
                RecapCard.WhatYouDid::class,
                RecapCard.Milestones::class,
                RecapCard.Completion::class,
                RecapCard.WhatNext::class,
            ),
            cards.map { it::class },
        )
    }

    private fun screenData(
        status: ArcStatus = ArcStatus.COMPLETED,
        intention: String? = null,
        hardestWeek: HardestWeek? = null,
        milestones: List<RecapMilestone> = emptyList(),
    ): RecapScreenData {
        val gym = Commitment(
            id = "c-gym",
            name = "Gym",
            type = CommitmentType.BINARY,
            isRequired = true,
            scheduleType = ScheduleType.DAILY,
            activeFrom = LocalDate.of(2026, 1, 1),
        )
        return RecapScreenData(
            recap = Recap(
                status = status,
                completeDays = 8,
                countedDays = 10,
                completionPct = 0.8,
                daysReached = 10,
                bestStreak = 5,
                hardestWeek = hardestWeek,
                commitmentTotals = listOf(CommitmentTotal.Binary("c-gym", daysDone = 8)),
            ),
            arcName = "September Reset",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 10),
            intention = intention,
            commitments = listOf(gym),
            milestones = milestones,
        )
    }
}
