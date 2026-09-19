package dev.priyanshu.arc.ui.today

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.data.AppDatabase
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.FakeClock
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
        ArcRepository(database.arcDao(), database.commitmentDao(), database.dayDao(), database.entryDao(), clock)

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
    fun `arc that is past its end date shows Ended`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0)))
        val repository = repository(clock)
        repository.createArc(
            name = "Past Arc",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )

        val viewModel = TodayViewModel(repository, clock)
        val state = viewModel.state.first { it !is TodayUiState.Loading }

        assertEquals(TodayUiState.Ended(arcName = "Past Arc", totalDays = 10), state)
    }
}
