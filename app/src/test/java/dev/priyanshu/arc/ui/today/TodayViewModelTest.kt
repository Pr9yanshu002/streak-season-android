package dev.priyanshu.arc.ui.today

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.data.AppDatabase
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.CreateArcResult
import dev.priyanshu.arc.data.FakeClock
import dev.priyanshu.arc.data.NewCommitment
import dev.priyanshu.arc.domain.model.CommitmentType
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

        assertEquals(TodayUiState.NotStarted(arcName = "Future Arc", startDate = startDate), state)
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

        assertEquals(
            TodayUiState.Ended(
                arcName = "Past Arc",
                totalDays = 10,
                completed = true,
                failedOnDate = null,
                sourceArcId = arcId,
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

        assertEquals(
            TodayUiState.Ended(
                arcName = "Strict Arc",
                totalDays = 30,
                completed = false,
                failedOnDate = LocalDate.of(2026, 9, 1),
                sourceArcId = arcId,
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
    }
}
