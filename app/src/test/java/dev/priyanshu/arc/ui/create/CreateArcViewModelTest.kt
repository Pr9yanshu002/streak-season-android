package dev.priyanshu.arc.ui.create

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.data.AppDatabase
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.CreateArcResult
import dev.priyanshu.arc.data.FakeClock
import dev.priyanshu.arc.data.NewCommitment
import dev.priyanshu.arc.domain.model.CommitmentType
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// docs/tasks/11-arc-creation.md — the validation rules the creation flow
// enforces: R16's start-date boundary (called, not reimplemented) and the
// UI-level "at least one required commitment" / "non-blank name" blocks.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateArcViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var viewModel: CreateArcViewModel

    private val today = LocalDate.of(2026, 9, 12)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        viewModel = CreateArcViewModel(repository, clock)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `default start date (today) is valid`() {
        assertNull(viewModel.state.value.startDateError)
    }

    @Test
    fun `start date one day before the R16 boundary is invalid`() {
        // default backfillDays = 2, today = 2026-09-12 -> earliest allowed is 2026-09-10
        viewModel.setStartDate(LocalDate.of(2026, 9, 9))
        assertNotNull(viewModel.state.value.startDateError)
    }

    @Test
    fun `start date exactly at the R16 boundary is valid`() {
        viewModel.setStartDate(LocalDate.of(2026, 9, 10))
        assertNull(viewModel.state.value.startDateError)
    }

    @Test
    fun `a future start date is valid`() {
        viewModel.setStartDate(today.plusDays(10))
        assertNull(viewModel.state.value.startDateError)
    }

    @Test
    fun `lowering backfillDays can invalidate an already-chosen start date, surfaced as step3Error`() {
        viewModel.setStartDate(LocalDate.of(2026, 9, 10)) // valid while backfillDays = 2
        assertNull(viewModel.state.value.startDateError)

        viewModel.setBackfillDays(0) // earliest allowed becomes today itself
        assertNotNull(viewModel.state.value.startDateError)
        assertNotNull(viewModel.state.value.step3Error)
    }

    @Test
    fun `blank name is invalid`() {
        viewModel.setName("   ")
        assertNotNull(viewModel.state.value.nameError)
    }

    @Test
    fun `non-blank name is valid`() {
        viewModel.setName("My Arc")
        assertNull(viewModel.state.value.nameError)
    }

    @Test
    fun `empty commitment list is blocked`() {
        assertNotNull(viewModel.state.value.step2Error)
    }

    @Test
    fun `an all-bonus commitment list is blocked`() {
        viewModel.saveCommitment(CommitmentDraft(name = "Read", isRequired = false))
        assertNotNull(viewModel.state.value.step2Error)
    }

    @Test
    fun `at least one required commitment clears the block`() {
        viewModel.saveCommitment(CommitmentDraft(name = "Read", isRequired = false))
        viewModel.saveCommitment(CommitmentDraft(name = "Meditate", isRequired = true))
        assertNull(viewModel.state.value.step2Error)
    }

    @Test
    fun `next does not advance past an invalid step`() {
        viewModel.setName("") // blank -> invalid
        viewModel.next()
        assertEquals(CreateArcStep.NAME_AND_DATES, viewModel.state.value.step)
    }

    @Test
    fun `next advances once the current step is valid`() {
        viewModel.setName("My Arc")
        viewModel.next()
        assertEquals(CreateArcStep.COMMITMENTS, viewModel.state.value.step)
    }

    // docs/tasks/12-lifecycle-milestones.md: remix pre-fills name and
    // commitments from the source arc, but never intention or dates — those
    // start fresh, same as a plain "start an arc".
    @Test
    fun `remix pre-fills name and commitments but not intention or dates`() = runTest {
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val createResult = repository.createArc(
            name = "Failed Arc",
            startDate = today.minusDays(30),
            endDate = today.minusDays(1),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
            ),
        )
        val sourceArcId = (createResult as CreateArcResult.Created).arcId

        val remixViewModel = CreateArcViewModel(repository, clock, remixFromArcId = sourceArcId)
        val state = remixViewModel.state.first { it.name == "Failed Arc" }

        assertEquals("Failed Arc", state.name)
        assertEquals(listOf("Gym"), state.commitments.map { it.name })
        assertTrue(state.intention.isBlank())
        assertEquals(today, state.startDate)
        assertEquals(today.plusDays(59), state.endDate)
    }
}
