package dev.priyanshu.arc.data

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness

// Debug-only fixture data so the Today screen has something to show before
// arc creation exists (docs/tasks/08-today-screen.md). Idempotent: bails out
// if any arc already exists, so relaunching never re-seeds. Never runs in
// release builds — see StreakSeasonApp.
class DebugSeeder(
    private val repository: ArcRepository,
    private val clock: Clock,
) {
    suspend fun seedIfNeeded() {
        if (repository.hasAnyArc()) return

        val today = clock.now().toLocalDate()
        val startDate = today.minusDays(10)
        val endDate = today.plusDays(30)

        val result = repository.createArc(
            name = "September Reset",
            startDate = startDate,
            endDate = endDate,
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(
                    name = "Meditate",
                    type = CommitmentType.BINARY,
                    isRequired = true,
                    scheduleType = ScheduleType.DAILY,
                ),
                // Weekdays-only, so the Today screen exercises R3.
                NewCommitment(
                    name = "Push-ups",
                    type = CommitmentType.COUNTER,
                    targetValue = 30.0,
                    unit = "reps",
                    isRequired = true,
                    scheduleType = ScheduleType.WEEKDAYS,
                    scheduleDays = "1,2,3,4,5",
                ),
                // Bonus, so the Today screen exercises R5's "bonus never
                // affects day state" and the "optional" label.
                NewCommitment(
                    name = "Read",
                    type = CommitmentType.DURATION,
                    targetValue = 20.0,
                    unit = "minutes",
                    isRequired = false,
                    scheduleType = ScheduleType.DAILY,
                ),
                NewCommitment(
                    name = "Weight",
                    type = CommitmentType.LOG,
                    unit = "kg",
                    isRequired = true,
                    scheduleType = ScheduleType.DAILY,
                ),
            ),
        )
        // seedIfNeeded() already checked hasAnyArc() above, so R15 can't
        // trip here — anything else would be a genuine invariant violation.
        val arcId = (result as? CreateArcResult.Created)?.arcId
            ?: error("debug seed failed: $result")

        val commitments = repository.getCommitments(arcId)
        val meditateId = commitments.first { it.name == "Meditate" }.id
        val pushupsId = commitments.first { it.name == "Push-ups" }.id
        val weightId = commitments.first { it.name == "Weight" }.id

        // Meditate and Weight are required every day regardless of weekday,
        // so writing/withholding just those two deterministically produces
        // complete/partial/missed without needing to know which days
        // Push-ups (weekdays-only) actually applies to.
        val pastDays = listOf(
            -10 to DaySeed.COMPLETE,
            -9 to DaySeed.COMPLETE,
            -8 to DaySeed.MISSED,
            -7 to DaySeed.PARTIAL,
            -6 to DaySeed.COMPLETE,
            -5 to DaySeed.REST,
            -4 to DaySeed.MISSED,
            -3 to DaySeed.PARTIAL,
            -2 to DaySeed.COMPLETE,
            -1 to DaySeed.PARTIAL,
        )

        for ((offset, seed) in pastDays) {
            val date = today.plusDays(offset.toLong())
            when (seed) {
                DaySeed.COMPLETE -> {
                    repository.recordEntry(arcId, date, meditateId, 1.0)
                    repository.recordEntry(arcId, date, pushupsId, 30.0)
                    repository.recordEntry(arcId, date, weightId, 70.0)
                }
                DaySeed.PARTIAL -> repository.recordEntry(arcId, date, meditateId, 1.0)
                DaySeed.MISSED -> Unit
                DaySeed.REST -> repository.setRestTokenUsed(arcId, date, used = true)
            }
        }
        // Today is left untouched: pending, ready for the user to check in.
    }

    private enum class DaySeed { COMPLETE, PARTIAL, MISSED, REST }
}
