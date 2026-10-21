package dev.priyanshu.arc.ui.today

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.data.AppDatabase
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.CreateArcResult
import dev.priyanshu.arc.data.FakeClock
import dev.priyanshu.arc.data.NewCommitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Covers the two "today is outside the arc's range" states that
// TodayViewModel derives from Resolution.dayNumber/commitmentStatuses
// (docs/tasks/08-today-screen.md follow-up: NotStarted / Ended).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun repository(clock: FakeClock) =
        ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )

    @Test
    fun `arc that has not started yet shows NotStarted`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 1), LocalTime.of(10, 0)))
        val repository = repository(clock)
        val startDate = LocalDate.of(2026, 9, 10)
        repository.createArc(
            name = "Future Arc",
            startDate = startDate,
            endDate = LocalDate.of(2026, 10, 10),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )

        val viewModel = TodayViewModel(repository, clock)
        val state = viewModel.state.first { it !is TodayUiState.Loading }

        assertEquals(
            TodayUiState.NotStarted(
                arcName = "Future Arc",
                startDate = startDate,
                // 2026-09-10 .. 2026-10-10 inclusive (R2).
                totalDays = 31,
                // 2026-09-01 -> 2026-09-10.
                daysToGo = 9,
                intention = null,
                commitments = emptyList(),
            ),
            state,
        )
    }

    // Task 18, design 06 — the intention comes through from arcs.intention
    // (trimmed by createArc), and the planned commitments list required first,
    // then bonus, each with its schedule in words.
    @Test
    fun `not started carries the intention and the planned commitments`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 1), LocalTime.of(10, 0)))
        val repository = repository(clock)
        repository.createArc(
            name = "Spring Sprint",
            startDate = LocalDate.of(2026, 9, 4),
            endDate = LocalDate.of(2026, 10, 3),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            intention = "  Get my mornings back.  ",
            commitments = listOf(
                NewCommitment(name = "Journal", type = CommitmentType.BINARY, isRequired = false, scheduleType = ScheduleType.DAILY),
                NewCommitment(
                    name = "Run 5 km", type = CommitmentType.BINARY, isRequired = true,
                    scheduleType = ScheduleType.WEEKDAYS, scheduleDays = "1,3,5",
                ),
            ),
        )

        val viewModel = TodayViewModel(repository, clock)
        val state = viewModel.state.first { it !is TodayUiState.Loading } as TodayUiState.NotStarted

        assertEquals("Get my mornings back.", state.intention)
        assertEquals(3, state.daysToGo)
        assertEquals(30, state.totalDays)
        assertEquals(listOf("Run 5 km", "Journal"), state.commitments.map { it.name })
        assertEquals(listOf("Mon, Wed, Fri", "Every day"), state.commitments.map { it.scheduleText })
        assertEquals(listOf(true, false), state.commitments.map { it.isRequired })
    }

    @Test
    fun `arc that is past its end date shows Ended as completed`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0)))
        val repository = repository(clock)
        val createResult = repository.createArc(
            name = "Past Arc",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )
        val arcId = (createResult as CreateArcResult.Created).arcId

        val viewModel = TodayViewModel(repository, clock)
        val state = viewModel.state.first { it !is TodayUiState.Loading }
        // "You showed up X of Y" is the recap's own complete/counted days (R19),
        // read through the same loader the recap screen uses.
        val recap = requireNotNull(repository.getRecapScreenData(arcId)).recap

        assertEquals(
            TodayUiState.Ended(
                arcName = "Past Arc",
                totalDays = 10,
                completed = true,
                failedOnDate = null,
                sourceArcId = arcId,
                // Completed: ended on the arc's own end date.
                endedOn = LocalDate.of(2026, 9, 10),
                // No commitments at all, so every day is complete (R5's empty case).
                season = List(10) { DayState.COMPLETE },
                daysShowedUp = recap.completeDays,
                daysCounted = recap.countedDays,
            ),
            state,
        )
    }

    // R9/R10: a strict arc's sealed miss makes it terminal immediately, not
    // only once today runs past end_date — the date-math signal the old
    // Ended detection used couldn't tell this apart from a plain in-progress
    // day, since today (day 10 of 30) still falls well inside the arc's range.
    @Test
    fun `strict arc with a sealed miss shows Ended as failed, before its end date`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 10), LocalTime.of(10, 0)))
        val repository = repository(clock)
        val createResult = repository.createArc(
            name = "Strict Arc",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(
                    name = "Gym",
                    type = CommitmentType.BINARY,
                    isRequired = true,
                    scheduleType = ScheduleType.DAILY,
                ),
            ),
        )
        val arcId = (createResult as CreateArcResult.Created).arcId
        // 2026-09-01 never logged -> missed, sealed by 2026-09-10 (backfillDays = 2).

        val viewModel = TodayViewModel(repository, clock)
        val state = viewModel.state.first { it !is TodayUiState.Loading }
        val recap = requireNotNull(repository.getRecapScreenData(arcId)).recap

        assertEquals(
            TodayUiState.Ended(
                arcName = "Strict Arc",
                totalDays = 30,
                completed = false,
                failedOnDate = LocalDate.of(2026, 9, 1),
                sourceArcId = arcId,
                // Failed: ended on the failure date, not the end date.
                endedOn = LocalDate.of(2026, 9, 1),
                // Sep 1-9 missed, Sep 10 (today) pending, Sep 11-30 still to come.
                season = List(9) { DayState.MISSED } + DayState.PENDING + List(20) { DayState.FUTURE },
                daysShowedUp = recap.completeDays,
                daysCounted = recap.countedDays,
            ),
            state,
        )
    }

    // Task 14 — one pending milestone stays the single card. Opened on day 7
    // of a 20-day arc, only p25 (day 5) has been crossed; p50 (day 10) has not.
    @Test
    fun `one pending milestone maps to Single`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 7), LocalTime.of(10, 0)))
        val repository = repository(clock)
        repository.createArc(
            name = "Quarter In",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 20),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )

        val viewModel = TodayViewModel(repository, clock)
        val pending = viewModel.pendingMilestone.first { it != null }

        assertTrue(pending is PendingMilestoneUiState.Single)
        pending as PendingMilestoneUiState.Single
        assertEquals(MilestoneKind.P25, pending.kind)
        // Crossing day 5 of 20 (2026-09-05), not today (day 7) — R12 amended.
        assertEquals(5, pending.dayNumber)
        assertEquals(20, pending.totalDays)
        // Design 04's "Day 5 of 20 · 5 September" reads the crossing date.
        assertEquals(LocalDate.of(2026, 9, 5), pending.triggeredOnDate)
    }

    // Task 14 — two or more pending collapse into one Summary, oldest-first and
    // not re-sorted. Opened on day 16, p25/p50/p75 (days 5/10/15) are all
    // crossed; final is not (the arc is still active). The reflection target is
    // the latest (p75).
    @Test
    fun `three pending milestones map to Summary oldest-first`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 16), LocalTime.of(10, 0)))
        val repository = repository(clock)
        repository.createArc(
            name = "Closed For Two Weeks",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 20),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )

        val viewModel = TodayViewModel(repository, clock)
        val pending = viewModel.pendingMilestone.first { it != null }

        assertTrue(pending is PendingMilestoneUiState.Summary)
        pending as PendingMilestoneUiState.Summary
        assertEquals(
            listOf(MilestoneKind.P25, MilestoneKind.P50, MilestoneKind.P75),
            pending.rows.map { it.kind },
        )
        assertEquals(listOf(5, 10, 15), pending.rows.map { it.dayNumber })
        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 5),
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 15),
            ),
            pending.rows.map { it.triggeredOnDate },
        )
        // Reflection attaches to the latest milestone on the card (p75).
        assertEquals(pending.rows.last().id, pending.reflectionTargetId)
        // Design 05's header: the arc's name and today's day number (16), not
        // the latest milestone's.
        assertEquals("Closed For Two Weeks", pending.arcName)
        assertEquals(16, pending.todayDayNumber)
    }

    // Task 17 — the rest-day row. Arc runs Mon 2026-09-07 .. 2026-10-06, one
    // required daily commitment, opened Thu 2026-09-10 (ISO week 2026-W37).
    private val restWeekToday = LocalDate.of(2026, 9, 10)

    private suspend fun createRestArc(
        repository: ArcRepository,
        strictness: Strictness,
        startDate: LocalDate = LocalDate.of(2026, 9, 7),
    ): Pair<String, String> {
        val result = repository.createArc(
            name = "Rest Arc",
            startDate = startDate,
            endDate = LocalDate.of(2026, 10, 6),
            strictness = strictness,
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
            ),
        )
        val arcId = (result as CreateArcResult.Created).arcId
        return arcId to repository.getCommitments(arcId).single().id
    }

    private suspend fun TodayViewModel.loaded(): TodayUiState.Loaded =
        state.first { it is TodayUiState.Loaded } as TodayUiState.Loaded

    @Test
    fun `standard arc offers a rest day, and resting then not resting flips the row`() = runTest {
        val clock = FakeClock(LocalDateTime.of(restWeekToday, LocalTime.of(10, 0)))
        val repository = repository(clock)
        createRestArc(repository, Strictness.STANDARD)
        val viewModel = TodayViewModel(repository, clock)

        val offered = viewModel.loaded()
        assertEquals(RestDayRow.OFFER, offered.restDayRow)
        assertEquals(1, offered.restTokensLeftThisWeek)

        viewModel.setRestDay(true)
        val resting = viewModel.state.first { (it as? TodayUiState.Loaded)?.todayState == DayState.REST }
            as TodayUiState.Loaded
        assertEquals(RestDayRow.RESTING, resting.restDayRow)
        assertEquals(0, resting.restTokensLeftThisWeek)

        viewModel.setRestDay(false)
        val backToPending = viewModel.state.first { (it as? TodayUiState.Loaded)?.todayState == DayState.PENDING }
            as TodayUiState.Loaded
        assertEquals(RestDayRow.OFFER, backToPending.restDayRow)
        assertEquals(1, backToPending.restTokensLeftThisWeek)
    }

    @Test
    fun `no rest-day row once today is complete`() = runTest {
        val clock = FakeClock(LocalDateTime.of(restWeekToday, LocalTime.of(10, 0)))
        val repository = repository(clock)
        val (arcId, commitmentId) = createRestArc(repository, Strictness.STANDARD)
        repository.recordEntry(arcId, restWeekToday, commitmentId, 1.0)
        val viewModel = TodayViewModel(repository, clock)

        val state = viewModel.loaded()
        assertEquals(DayState.COMPLETE, state.todayState)
        assertNull(state.restDayRow)
    }

    // Starts today: started on the Monday, its missed Monday would already be
    // sealed by Thursday and fail the arc (R9), so Today would show Ended.
    @Test
    fun `no rest-day row on a strict arc`() = runTest {
        val clock = FakeClock(LocalDateTime.of(restWeekToday, LocalTime.of(10, 0)))
        val repository = repository(clock)
        createRestArc(repository, Strictness.STRICT, startDate = restWeekToday)
        val viewModel = TodayViewModel(repository, clock)

        assertNull(viewModel.loaded().restDayRow)
    }

    // Tue 2026-09-08 is flagged and missed, so it takes this week's only
    // token (R6) — nothing left to offer today.
    @Test
    fun `no rest-day row when this week's token is already used`() = runTest {
        val clock = FakeClock(LocalDateTime.of(restWeekToday, LocalTime.of(10, 0)))
        val repository = repository(clock)
        val (arcId, _) = createRestArc(repository, Strictness.STANDARD)
        repository.setRestTokenUsed(arcId, LocalDate.of(2026, 9, 8), used = true)
        val viewModel = TodayViewModel(repository, clock)

        val state = viewModel.loaded()
        assertEquals(DayState.PENDING, state.todayState)
        assertEquals(0, state.restTokensLeftThisWeek)
        assertNull(state.restDayRow)
    }

    // Task 18 — what the redesigned Loaded state carries, all mapped from
    // Resolution. Arc starts Wed 2026-09-09, opened Fri 2026-09-11. Wed and Thu
    // are complete; today has Meditate done and Water at 1 of 3. Long walk is
    // required but weekends only, so it's NOT_SCHEDULED today.
    @Test
    fun `loaded state maps order, required counts, week and schedule text from Resolution`() = runTest {
        val today = LocalDate.of(2026, 9, 11)
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val repository = repository(clock)
        val result = repository.createArc(
            name = "Week Arc",
            startDate = LocalDate.of(2026, 9, 9),
            endDate = LocalDate.of(2026, 10, 9),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            // Deliberately not in display order: the mapper reorders.
            commitments = listOf(
                NewCommitment(name = "Meditate", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
                NewCommitment(
                    name = "Long walk", type = CommitmentType.BINARY, isRequired = true,
                    scheduleType = ScheduleType.WEEKDAYS, scheduleDays = "6,7",
                ),
                NewCommitment(
                    name = "Read", type = CommitmentType.DURATION, targetValue = 20.0, isRequired = false,
                    scheduleType = ScheduleType.DAILY,
                ),
                NewCommitment(
                    name = "Water", type = CommitmentType.COUNTER, targetValue = 3.0, unit = "glasses",
                    isRequired = true, scheduleType = ScheduleType.DAILY,
                ),
            ),
        )
        val arcId = (result as CreateArcResult.Created).arcId
        val ids = repository.getCommitments(arcId).associate { it.name to it.id }
        for (date in listOf(LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10))) {
            repository.recordEntry(arcId, date, ids.getValue("Meditate"), 1.0)
            repository.recordEntry(arcId, date, ids.getValue("Water"), 3.0)
        }
        repository.recordEntry(arcId, today, ids.getValue("Meditate"), 1.0)
        repository.recordEntry(arcId, today, ids.getValue("Water"), 1.0)

        val viewModel = TodayViewModel(repository, clock)
        val state = viewModel.state.first { it is TodayUiState.Loaded } as TodayUiState.Loaded

        // Required, then bonus, then not today.
        assertEquals(listOf("Meditate", "Water", "Read", "Long walk"), state.commitments.map { it.name })
        val longWalk = state.commitments.last()
        assertEquals(false, longWalk.isScheduledToday)
        assertEquals("Saturdays and Sundays", longWalk.scheduleText)
        assertEquals("Every day", state.commitments.first().scheduleText)

        // Long walk is required but not scheduled today, so it isn't counted.
        assertEquals(1, state.requiredDone)
        assertEquals(2, state.requiredTotal)

        // Pending today is skipped by R11; Wed and Thu count.
        assertEquals(2, state.currentStreak)
        assertEquals(false, state.isDayComplete)

        // Mon 7 .. Sun 13. The arc starts Wednesday, so Mon/Tue are empty slots.
        assertEquals(
            listOf(
                DayState.OUT_OF_RANGE, DayState.OUT_OF_RANGE, DayState.COMPLETE, DayState.COMPLETE,
                DayState.PENDING, DayState.FUTURE, DayState.FUTURE,
            ),
            state.week.map { it.state },
        )
        assertEquals(LocalDate.of(2026, 9, 7), state.week.first().date)
        assertEquals(listOf(false, false, false, false, true, false, false), state.week.map { it.isToday })
    }
}
