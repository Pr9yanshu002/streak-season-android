package dev.priyanshu.arc.data

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
    ENDED_RECAP,
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
            DebugScenario.ENDED_RECAP -> seedEndedRecap()
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

    // A clean 20-day completion: one required commitment, done every day, so
    // it's a real 100% arc (not the old commitment-less 0-of-0 that made the
    // recap headline read "0 of 0 days"). ENDED_RECAP is the richer showcase.
    private suspend fun seedEndedCompleted() {
        val today = clock.now().toLocalDate()
        val startDate = today.minusDays(20)
        val endDate = today.minusDays(1)
        val result = repository.createArc(
            name = "Finished Strong",
            startDate = startDate,
            endDate = endDate,
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(gymCommitment()),
        )
        val arcId = (result as? CreateArcResult.Created)?.arcId ?: error("debug seed failed: $result")
        val gymId = repository.getCommitments(arcId).first { it.name == "Gym" }.id
        var date = startDate
        while (!date.isAfter(endDate)) {
            repository.recordEntry(arcId, date, gymId, 1.0)
            date = date.plusDays(1)
        }
    }

    // A completed arc built to exercise every recap card (docs/tasks/15): all
    // four commitment types, a gym run interrupted by a few missed days so
    // there's a clear hardest week and a long closing streak, a day-1 intention,
    // and reflections saved on two milestones. reconcile() materialises the
    // milestones the way the app does on load; acknowledge() leaves the
    // reflections.
    private suspend fun seedEndedRecap() {
        val today = clock.now().toLocalDate()
        val startDate = today.minusDays(24)
        val endDate = startDate.plusDays(20) // 21-day arc, already ended (ends today-4)

        val result = repository.createArc(
            name = "Winter Arc",
            startDate = startDate,
            endDate = endDate,
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
                NewCommitment(name = "Push-ups", type = CommitmentType.COUNTER, targetValue = 30.0, unit = "reps", isRequired = false, scheduleType = ScheduleType.DAILY),
                NewCommitment(name = "Read", type = CommitmentType.DURATION, targetValue = 20.0, unit = "minutes", isRequired = false, scheduleType = ScheduleType.DAILY),
                NewCommitment(name = "Weight", type = CommitmentType.LOG, unit = "kg", isRequired = false, scheduleType = ScheduleType.DAILY),
            ),
            intention = "Stop starting over every Monday. Build something that sticks.",
        )
        val arcId = (result as? CreateArcResult.Created)?.arcId ?: error("debug seed failed: $result")

        val commitments = repository.getCommitments(arcId)
        val gymId = commitments.first { it.name == "Gym" }.id
        val pushupsId = commitments.first { it.name == "Push-ups" }.id
        val readId = commitments.first { it.name == "Read" }.id
        val weightId = commitments.first { it.name == "Weight" }.id

        for (offset in 0..20) {
            val date = startDate.plusDays(offset.toLong())
            // Gym missed days 7-9 (one rough week), done otherwise: ~86%
            // completion, a long closing streak, and one clearly worst week.
            if (offset !in 7..9) repository.recordEntry(arcId, date, gymId, 1.0)
            // Bonus commitments logged every day, including the gym-off ones.
            repository.recordEntry(arcId, date, pushupsId, (25 + (offset % 3) * 5).toDouble()) // 25/30/35, target 30
            repository.recordEntry(arcId, date, readId, (15 + (offset % 4) * 5).toDouble())     // 15/20/25/30, target 20
            repository.recordEntry(arcId, date, weightId, 80.0 - offset * 0.1)                  // 80.0 -> 78.0
        }

        // Materialise milestones (what Today load does) and leave a reflection
        // on two of them, so the Milestones card shows text, not just dates.
        repository.reconcile(arcId)
        val milestones = database.milestoneDao().observeByArc(arcId).first()
        milestones.firstOrNull { it.kind == "p25" }?.let {
            repository.acknowledgeMilestones(listOf(it.id), "First week felt impossible. Showed up anyway.", it.id)
        }
        milestones.firstOrNull { it.kind == "p50" }?.let {
            repository.acknowledgeMilestones(listOf(it.id), "Halfway. The missed days stung but didn't end it.", it.id)
        }
        // Acknowledge the remaining milestones (p75, final) with no reflection,
        // so nothing is left pending — Today lands straight on Ended, not on a
        // pending-milestone card. 'start' never appears as a card anyway.
        val remaining = milestones
            .filter { it.kind !in setOf("start", "p25", "p50") }
            .map { it.id }
        if (remaining.isNotEmpty()) {
            repository.acknowledgeMilestones(remaining, reflection = null, reflectionMilestoneId = remaining.first())
        }

        // reconcile() above flipped the stored status to 'completed', but Today
        // only loads the arc whose status is 'active' (getActiveArcId) and shows
        // Ended when the engine resolves that active arc as completed/failed —
        // the same shape the other ended scenarios rely on by never reconciling.
        // Put the stored status back to 'active'; Today's own reconcile on load
        // re-derives completed, and the milestones/reflections created above
        // already exist so they are kept, not recreated.
        database.arcDao().updateStatusIfChanged(
            arcId = arcId,
            status = "active",
            completedAt = null,
            failedOnDate = null,
            now = clock.millis(),
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
