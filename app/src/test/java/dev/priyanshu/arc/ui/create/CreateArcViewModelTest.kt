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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `lowering backfillDays can invalidate an already-chosen start date, surfaced as strictnessError`() {
        viewModel.setStartDate(LocalDate.of(2026, 9, 10)) // valid while backfillDays = 2
        assertNull(viewModel.state.value.startDateError)

        viewModel.setBackfillDays(0) // earliest allowed becomes today itself
        assertNotNull(viewModel.state.value.startDateError)
        assertNotNull(viewModel.state.value.strictnessError)
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

    // Task 19 — lengthInDays counts both the start and the end day, so it
    // drives the "N days" summary and which "How long" chip is selected.
    @Test
    fun `default arc is 60 days long`() {
        assertEquals(60, viewModel.state.value.lengthInDays)
    }

    @Test
    fun `each quick length gives exactly that many days`() {
        listOf(30, 60, 75, 90).forEach { days ->
            viewModel.pickQuickDuration(days)
            assertEquals(days, viewModel.state.value.lengthInDays)
        }
    }

    @Test
    fun `an arc that ends on its start day is 1 day long`() {
        viewModel.setEndDate(viewModel.state.value.startDate)
        assertEquals(1, viewModel.state.value.lengthInDays)
    }

    @Test
    fun `moving the start date changes the length when the end date stays`() {
        viewModel.pickQuickDuration(30)
        viewModel.setStartDate(today.plusDays(10))
        assertEquals(20, viewModel.state.value.lengthInDays)
    }

    // Task 19 — Next is always enabled. A blocked next() doesn't move; it
    // turns on showErrors so the screen draws the step's errors.
    @Test
    fun `a blocked next sets showErrors and stays on NAME_AND_DATES`() {
        viewModel.setName("")
        viewModel.next()
        val state = viewModel.state.value
        assertEquals(CreateArcStep.NAME_AND_DATES, state.step)
        assertTrue(state.showErrors)
    }

    @Test
    fun `after a blocked next, fixing the name and calling next moves to COMMITMENTS with showErrors off`() {
        viewModel.setName("")
        viewModel.next()
        assertTrue(viewModel.state.value.showErrors)

        viewModel.setName("My Arc")
        viewModel.next()
        val state = viewModel.state.value
        assertEquals(CreateArcStep.COMMITMENTS, state.step)
        assertFalse(state.showErrors)
    }

    @Test
    fun `next walks every step to REVIEW and back from REVIEW returns to INTENTION`() {
        viewModel.setName("My Arc")
        viewModel.saveCommitment(CommitmentDraft(name = "Meditate", isRequired = true))

        assertEquals(CreateArcStep.NAME_AND_DATES, viewModel.state.value.step)
        viewModel.next()
        assertEquals(CreateArcStep.COMMITMENTS, viewModel.state.value.step)
        viewModel.next()
        assertEquals(CreateArcStep.STRICTNESS, viewModel.state.value.step)
        viewModel.next()
        assertEquals(CreateArcStep.INTENTION, viewModel.state.value.step)
        viewModel.next()
        assertEquals(CreateArcStep.REVIEW, viewModel.state.value.step)

        viewModel.back()
        assertEquals(CreateArcStep.INTENTION, viewModel.state.value.step)
    }

    @Test
    fun `back resets showErrors`() {
        viewModel.setName("My Arc")
        viewModel.next() // -> COMMITMENTS
        viewModel.next() // blocked: no commitments yet
        val blocked = viewModel.state.value
        assertEquals(CreateArcStep.COMMITMENTS, blocked.step)
        assertTrue(blocked.showErrors)

        viewModel.back()
        val state = viewModel.state.value
        assertEquals(CreateArcStep.NAME_AND_DATES, state.step)
        assertFalse(state.showErrors)
    }

    // Task 19 — isSubmitting is set before begin() launches anything, and a
    // second begin() while it's set does nothing. Without the guard, two quick
    // taps both pass createArc's R15 check before either insert lands, and two
    // active arcs get created.
    @Test
    fun `begin sets isSubmitting straight away and a second begin is ignored`() = runTest {
        viewModel.setName("My Arc")
        viewModel.saveCommitment(CommitmentDraft(name = "Meditate", isRequired = true))

        var createdCalls = 0
        val created = CompletableDeferred<Unit>()
        val onCreated = {
            createdCalls++
            created.complete(Unit)
            Unit
        }
        viewModel.begin(onCreated)
        assertTrue(viewModel.state.value.isSubmitting)
        viewModel.begin(onCreated)
        created.await()

        assertEquals(1, database.arcDao().count())
        assertEquals(1, createdCalls)
    }

    // Task 19 — the commitment editor lives in the ViewModel: open a draft,
    // change it, then save (blocked until it's complete), cancel or remove.
    @Test
    fun `starting a new commitment opens an empty draft that isn't in the list`() {
        viewModel.startNewCommitment()
        val state = viewModel.state.value
        assertNotNull(state.editing)
        assertFalse(state.isEditingExisting)
        assertFalse(state.editorShowErrors)
    }

    @Test
    fun `saving an incomplete draft keeps the editor open and shows errors`() {
        viewModel.startNewCommitment()
        viewModel.saveEditing() // no name yet
        val state = viewModel.state.value
        assertNotNull(state.editing)
        assertTrue(state.editorShowErrors)
        assertTrue(state.commitments.isEmpty())
    }

    @Test
    fun `saving a complete draft adds it cleaned and closes the editor`() {
        viewModel.startNewCommitment()
        viewModel.updateEditing { it.copy(name = "  Meditate ") }
        viewModel.saveEditing()
        val state = viewModel.state.value
        assertNull(state.editing)
        assertFalse(state.editorShowErrors)
        assertEquals(listOf("Meditate"), state.commitments.map { it.name })
    }

    @Test
    fun `editing an existing commitment replaces it in place`() {
        viewModel.saveCommitment(CommitmentDraft(name = "Read"))
        viewModel.saveCommitment(CommitmentDraft(name = "Gym"))
        val readId = viewModel.state.value.commitments.first().localId

        viewModel.editCommitment(readId)
        assertTrue(viewModel.state.value.isEditingExisting)
        viewModel.updateEditing { it.copy(name = "Read 20 pages") }
        viewModel.saveEditing()

        assertEquals(listOf("Read 20 pages", "Gym"), viewModel.state.value.commitments.map { it.name })
    }

    @Test
    fun `cancelling an edit leaves the list unchanged`() {
        viewModel.saveCommitment(CommitmentDraft(name = "Read"))
        viewModel.editCommitment(viewModel.state.value.commitments.single().localId)
        viewModel.updateEditing { it.copy(name = "Something else") }
        viewModel.cancelEditing()

        val state = viewModel.state.value
        assertNull(state.editing)
        assertEquals(listOf("Read"), state.commitments.map { it.name })
    }

    @Test
    fun `removing from the editor deletes the commitment and closes the editor`() {
        viewModel.saveCommitment(CommitmentDraft(name = "Read"))
        viewModel.saveCommitment(CommitmentDraft(name = "Gym"))
        viewModel.editCommitment(viewModel.state.value.commitments.first().localId)
        viewModel.removeEditing()

        val state = viewModel.state.value
        assertNull(state.editing)
        assertEquals(listOf("Gym"), state.commitments.map { it.name })
    }

    @Test
    fun `editing an id that isn't in the list leaves the open editor alone`() {
        viewModel.startNewCommitment()
        viewModel.updateEditing { it.copy(name = "Water") }
        viewModel.editCommitment("not-a-real-id")
        assertEquals("Water", viewModel.state.value.editing?.name)
    }

    @Test
    fun `changing step closes the editor`() {
        viewModel.setName("My Arc")
        viewModel.next() // -> COMMITMENTS
        viewModel.startNewCommitment()
        viewModel.back()
        assertNull(viewModel.state.value.editing)

        viewModel.next() // -> COMMITMENTS again
        viewModel.saveCommitment(CommitmentDraft(name = "Meditate"))
        viewModel.startNewCommitment()
        viewModel.next() // -> STRICTNESS
        assertEquals(CreateArcStep.STRICTNESS, viewModel.state.value.step)
        assertNull(viewModel.state.value.editing)
    }

    // Piece 6b-2 — the full-screen editor drives these through updateEditing,
    // the same way the screen does.
    @Test
    fun `the shown error follows the draft field by field once errors are on`() {
        viewModel.startNewCommitment()
        viewModel.updateEditing { it.copy(type = CommitmentType.COUNTER) }
        viewModel.saveEditing()
        assertTrue(viewModel.state.value.editorShowErrors)
        assertEquals("Give it a name.", viewModel.state.value.editing?.error)

        viewModel.updateEditing { it.copy(name = "Water") }
        assertEquals("Set a target above 0.", viewModel.state.value.editing?.error)
        viewModel.updateEditing { it.copy(targetText = "3000") }
        assertEquals("Add a unit, like ml or min.", viewModel.state.value.editing?.error)
        viewModel.updateEditing { it.copy(unit = "ml") }
        assertNull(viewModel.state.value.editing?.error)

        viewModel.saveEditing()
        val state = viewModel.state.value
        assertNull(state.editing)
        assertFalse(state.editorShowErrors)
        assertEquals("3000", state.commitments.single().targetText)
    }

    @Test
    fun `errors from one editor don't carry into the next`() {
        viewModel.startNewCommitment()
        viewModel.saveEditing() // blocked, errors on
        viewModel.cancelEditing()
        viewModel.startNewCommitment()
        assertFalse(viewModel.state.value.editorShowErrors)
    }

    @Test
    fun `switching kind keeps the typed target until save, and saving as tick-off drops it`() {
        viewModel.startNewCommitment()
        viewModel.updateEditing { it.copy(name = "Water", type = CommitmentType.COUNTER, targetText = "3000", unit = "ml") }
        viewModel.updateEditing { it.copy(type = CommitmentType.BINARY) }
        viewModel.updateEditing { it.copy(type = CommitmentType.COUNTER) }
        assertEquals("3000", viewModel.state.value.editing?.targetText)
        assertEquals("ml", viewModel.state.value.editing?.unit)

        viewModel.updateEditing { it.copy(type = CommitmentType.BINARY) }
        viewModel.saveEditing()
        val saved = viewModel.state.value.commitments.single()
        assertEquals("", saved.targetText)
        assertNull(saved.unit)
    }

    @Test
    fun `day toggles add and remove ISO days, and saving keeps them`() {
        // Same toggle as the screen's DayPicker onToggle.
        fun toggle(day: Int) = viewModel.updateEditing { d ->
            d.copy(scheduleDays = if (day in d.scheduleDays) d.scheduleDays - day else d.scheduleDays + day)
        }
        viewModel.startNewCommitment()
        viewModel.updateEditing { it.copy(name = "Long run", scheduleType = ScheduleType.WEEKDAYS) }
        assertEquals("Pick at least one day.", viewModel.state.value.editing?.error)

        toggle(6)
        toggle(7)
        toggle(6)
        assertEquals(setOf(7), viewModel.state.value.editing?.scheduleDays)

        viewModel.saveEditing()
        assertEquals(setOf(7), viewModel.state.value.commitments.single().scheduleDays)
    }

    @Test
    fun `days picked then switched back to every day are dropped on save`() {
        viewModel.startNewCommitment()
        viewModel.updateEditing { it.copy(name = "Gym", scheduleType = ScheduleType.WEEKDAYS, scheduleDays = setOf(1, 3, 5)) }
        viewModel.updateEditing { it.copy(scheduleType = ScheduleType.DAILY) }
        viewModel.saveEditing()

        val saved = viewModel.state.value.commitments.single()
        assertEquals(ScheduleType.DAILY, saved.scheduleType)
        assertTrue(saved.scheduleDays.isEmpty())
    }

    @Test
    fun `saveCommitment ignores an incomplete draft and cleans a complete one`() {
        viewModel.saveCommitment(CommitmentDraft(name = "  "))
        assertTrue(viewModel.state.value.commitments.isEmpty())

        viewModel.saveCommitment(CommitmentDraft(name = " Read ", targetText = "5", unit = "pages"))
        val saved = viewModel.state.value.commitments.single()
        assertEquals("Read", saved.name)
        assertEquals("", saved.targetText) // binary: no target
        assertNull(saved.unit)
    }

    // The old dialog read the target back with toInt(), so a 2.5 target
    // came back as 2 and was saved that way. The draft now holds the text.
    @Test
    fun `remix keeps a fractional target`() = runTest {
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
                NewCommitment(
                    name = "Run",
                    type = CommitmentType.DURATION,
                    targetValue = 2.5,
                    unit = "km",
                    isRequired = true,
                    scheduleType = ScheduleType.DAILY,
                ),
            ),
        )
        val sourceArcId = (createResult as CreateArcResult.Created).arcId

        val remixViewModel = CreateArcViewModel(repository, clock, remixFromArcId = sourceArcId)
        val run = remixViewModel.state.first { it.name == "Failed Arc" }.commitments.single()

        assertEquals("2.5", run.targetText)
        assertEquals(2.5, run.targetValue)
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

    // Task 13, Part 1a gap — remix must also carry the source arc's whole rule
    // set (strictness + the three numeric knobs), not just name + commitments.
    // This drives the exact path the user takes: open the remix
    // (CreateArcViewModel(remixFromArcId = ...)) -> pre-fill -> Begin
    // (begin()/createArc) -> read the persisted arc back. Every source value is
    // deliberately NON-default (create-flow defaults are STANDARD/1/2/4), so if
    // the pre-fill ever stops copying a field the created arc falls back to the
    // default and the matching assertion fails.
    @Test
    fun `remix carries the whole rule set through begin into the created arc`() = runTest {
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        // STRICT source that misses day one (sealed long ago, backfill 5), so
        // it fails on reconcile and stops being active — remix is only offered
        // on a failed arc, and R15 would block begin() if it were still active.
        val sourceResult = repository.createArc(
            name = "Original",
            startDate = today.minusDays(30),
            endDate = today.plusDays(20),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 3,
            backfillDays = 5,
            dayBoundaryHour = 6,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
            ),
        )
        val sourceId = (sourceResult as CreateArcResult.Created).arcId
        repository.reconcile(sourceId)
        assertEquals("failed", requireNotNull(database.arcDao().observeById(sourceId).first()).status)

        val remixViewModel = CreateArcViewModel(repository, clock, remixFromArcId = sourceId)
        val prefilled = remixViewModel.state.first { it.name == "Original" }

        // Pre-fill carries every rule field (not just name + commitments).
        assertEquals(Strictness.STRICT, prefilled.strictness)
        assertEquals(3, prefilled.restTokensPerWeek)
        assertEquals(5, prefilled.backfillDays)
        assertEquals(6, prefilled.dayBoundaryHour)

        // Begin along the real path and read the persisted arc back.
        val created = CompletableDeferred<Unit>()
        remixViewModel.begin(onCreated = { created.complete(Unit) })
        created.await()

        val newArcId = requireNotNull(repository.getActiveArcId())
        val newArc = requireNotNull(database.arcDao().observeById(newArcId).first())
        assertEquals(sourceId, newArc.remixedFromArcId)
        assertEquals("strict", newArc.strictness)
        assertEquals(3, newArc.restTokensPerWeek)
        assertEquals(5, newArc.backfillDays)
        assertEquals(6, newArc.dayBoundaryHour)
    }

    // "Keep them editable on the strictness step": pre-fill seeds the rule fields but must
    // not lock them — the user can still change any of them before Begin.
    @Test
    fun `remixed rule fields stay editable after pre-fill`() = runTest {
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val sourceResult = repository.createArc(
            name = "Original",
            startDate = today.minusDays(30),
            endDate = today.plusDays(20),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 3,
            backfillDays = 5,
            dayBoundaryHour = 6,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
            ),
        )
        val sourceId = (sourceResult as CreateArcResult.Created).arcId
        repository.reconcile(sourceId)

        val remixViewModel = CreateArcViewModel(repository, clock, remixFromArcId = sourceId)
        // Await pre-fill on name only — the pre-fill is one atomic state update,
        // so editability must hold regardless of what the rule fields were
        // seeded to.
        remixViewModel.state.first { it.name == "Original" }

        remixViewModel.setStrictness(Strictness.CHILL)
        remixViewModel.setRestTokensPerWeek(1)
        remixViewModel.setBackfillDays(0)
        remixViewModel.setDayBoundaryHour(9)

        val edited = remixViewModel.state.value
        assertEquals(Strictness.CHILL, edited.strictness)
        assertEquals(1, edited.restTokensPerWeek)
        assertEquals(0, edited.backfillDays)
        assertEquals(9, edited.dayBoundaryHour)
    }

    // Task 14, Part 2 — the pre-filled backfill_days must flow into R16's
    // earliest-start boundary, not the create-flow default of 2. A source with
    // backfill 5 moves the earliest start to today - 5, and the step-1 /
    // strictness-step start-date re-validation must use that window, so a start date
    // before it is still flagged.
    @Test
    fun `remixed backfill_days moves the R16 earliest start date and re-validation uses it`() = runTest {
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val sourceResult = repository.createArc(
            name = "Original",
            startDate = today.minusDays(30),
            endDate = today.plusDays(20),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 3,
            backfillDays = 5,
            dayBoundaryHour = 6,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
            ),
        )
        val sourceId = (sourceResult as CreateArcResult.Created).arcId
        repository.reconcile(sourceId)

        val remixViewModel = CreateArcViewModel(repository, clock, remixFromArcId = sourceId)
        val prefilled = remixViewModel.state.first { it.name == "Original" }

        // Pre-filled backfill 5 -> earliest start is today - 5, not today - 2.
        assertEquals(5, prefilled.backfillDays)
        assertEquals(today.minusDays(5), prefilled.earliestAllowedStartDate)

        // A start one day before the (wider) window is still flagged on both
        // the step-1 and strictness-step checks.
        remixViewModel.setStartDate(today.minusDays(6))
        val outside = remixViewModel.state.value
        assertNotNull(outside.startDateError)
        assertNotNull(outside.strictnessError)

        // The edge of the window is accepted; a date valid only under the
        // default backfill-2 window (today - 5 .. today - 3) is accepted too,
        // confirming the wider source window is the one in force.
        remixViewModel.setStartDate(today.minusDays(5))
        assertNull(remixViewModel.state.value.startDateError)
        remixViewModel.setStartDate(today.minusDays(4))
        assertNull(remixViewModel.state.value.startDateError)
    }
}
