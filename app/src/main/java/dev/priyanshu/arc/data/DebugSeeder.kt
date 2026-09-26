package dev.priyanshu.arc.data

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Named debug scenarios, one per flow worth testing on-device without
// grinding through real days. Shown as a picker in the debug menu
// (MainActivity, debug builds only).
enum class DebugScenario {
    EMPTY,
    NOT_STARTED,
    ACTIVE_MID_ARC,
    MILESTONE_PENDING,
    ENDED_COMPLETED,
    ENDED_FAILED,
}

// Debug-only fixture data. Never runs in release builds — see
// StreakSeasonApp (auto-seed on first launch) and MainActivity (the debug
// menu's manual reseed).
class DebugSeeder(
    private val repository: ArcRepository,
    private val clock: Clock,
    private val database: AppDatabase,
) {
    // Auto-seed on first launch only (docs/tasks/08-today-screen.md).
    // Idempotent: bails if any arc already exists, so relaunching never
    // re-seeds and never clobbers an arc created through the UI.
    suspend fun seedIfNeeded() {
        if (repository.hasAnyArc()) return
        seedActiveMidArc()
    }

    // Manual reseed for the debug menu. Wipes every table first — unlike
    // seedIfNeeded(), this is explicitly allowed to clobber whatever's
    // there, since it only ever runs from a debug-only screen a developer
    // reached on purpose. Room's clearAllTables() respects foreign keys, so
    // table order here doesn't matter.
    //
    // Unlike every DAO suspend fun (Room's generated coroutine adapter
    // dispatches those off the caller's thread automatically),
    // clearAllTables() is a plain blocking call that asserts it's never
    // invoked on the main thread — callers (the debug menu's
    // rememberCoroutineScope(), which launches on Main) have to move it
    // themselves.
    suspend fun seed(scenario: DebugScenario) {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        when (scenario) {
            DebugScenario.EMPTY -> Unit
            DebugScenario.NOT_STARTED -> seedNotStarted()
            DebugScenario.ACTIVE_MID_ARC -> seedActiveMidArc()
            DebugScenario.MILESTONE_PENDING -> seedMilestonePending()
            DebugScenario.ENDED_COMPLETED -> seedEndedCompleted()
            DebugScenario.ENDED_FAILED -> seedEndedFailed()
        }
    }

    private suspend fun seedNotStarted() {
        val today = clock.now().toLocalDate()
        repository.createArc(
            name = "Not Started Yet",
            startDate = today.plusDays(5),
            endDate = today.plusDays(64),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(gymCommitment()),
        )
    }

    private suspend fun seedActiveMidArc() {
        val today = clock.now().toLocalDate()
        val startDate = today.minusDays(10)
        // Deliberately long (161 days total, day 11 of it) so this scenario
        // never crosses a milestone threshold on its own — that's what
        // MILESTONE_PENDING is for. Crossing p25 by accident here would pop
        // the milestone card in front of the plain checklist this scenario
        // exists to show.
        val endDate = today.plusDays(150)

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
        // seedIfNeeded()/seed() both guarantee no active arc exists before
        // this runs, so R15 can't trip here — anything else would be a
        // genuine invariant violation.
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

    // 20-day arc, 10 days in — progress is exactly 0.5, so p25 and p50 are
    // both required at once (R12) and neither is acknowledged yet.
    // reconcile() (TodayViewModel.init) inserts and surfaces them the moment
    // the Today screen loads.
    private suspend fun seedMilestonePending() {
        val today = clock.now().toLocalDate()
        val startDate = today.minusDays(9)
        repository.createArc(
            name = "Halfway There",
            startDate = startDate,
            endDate = startDate.plusDays(19),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(gymCommitment()),
        )
    }

    // No required commitments -> every day resolves complete regardless of
    // entries, so the arc completes cleanly once today is past end_date.
    private suspend fun seedEndedCompleted() {
        val today = clock.now().toLocalDate()
        repository.createArc(
            name = "Finished Strong",
            startDate = today.minusDays(20),
            endDate = today.minusDays(1),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )
    }

    // start_date is never logged, so day one resolves missed; today is 9
    // days past it, well outside the 2-day backfill window, so it's sealed
    // and R9 fails the arc from day one — terminal well before end_date.
    private suspend fun seedEndedFailed() {
        val today = clock.now().toLocalDate()
        val startDate = today.minusDays(9)
        repository.createArc(
            name = "Broke The Streak",
            startDate = startDate,
            endDate = startDate.plusDays(29),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(gymCommitment()),
        )
    }

    private fun gymCommitment() = NewCommitment(
        name = "Gym",
        type = CommitmentType.BINARY,
        isRequired = true,
        scheduleType = ScheduleType.DAILY,
    )

    private enum class DaySeed { COMPLETE, PARTIAL, MISSED, REST }
}
