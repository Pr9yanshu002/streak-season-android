package dev.priyanshu.arc.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.domain.rules.recap.CommitmentTotal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Guards the repository->recap glue that no other test covers: loading the arc,
// commitments, days and milestones from Room, resolving, calling recap(), and
// pulling intention + milestone reflections. recap() itself is pinned by the
// :domain fixtures; RecapViewModelTest pins the mapping. This is the Room
// round-trip in between (docs/tasks/15).
@RunWith(RobolectricTestRunner::class)
class ArcRepositoryRecapTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ArcRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Well past the arc's end date, so the engine resolves it completed.
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 1, 20), LocalTime.of(10, 0)))
        repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getRecapScreenData returns the recap, intention, commitments and milestone reflections`() = runTest {
        // 5-day arc, Jan 1-5. Gym (required) done on 1,2,3,5 (missed 4). Weight
        // (bonus log) recorded on 1 and 5. Completed by the time clock says Jan 20.
        val created = repository.createArc(
            name = "January",
            startDate = LocalDate.of(2026, 1, 1),
            endDate = LocalDate.of(2026, 1, 5),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
                NewCommitment(name = "Weight", type = CommitmentType.LOG, unit = "kg", isRequired = false, scheduleType = ScheduleType.DAILY),
            ),
            intention = "Prove the recap pipeline works.",
        )
        val arcId = (created as CreateArcResult.Created).arcId
        val commitments = repository.getCommitments(arcId)
        val gymId = commitments.first { it.name == "Gym" }.id
        val weightId = commitments.first { it.name == "Weight" }.id

        for (day in listOf(1, 2, 3, 5)) {
            repository.recordEntry(arcId, LocalDate.of(2026, 1, day), gymId, 1.0)
        }
        repository.recordEntry(arcId, LocalDate.of(2026, 1, 1), weightId, 70.0)
        repository.recordEntry(arcId, LocalDate.of(2026, 1, 5), weightId, 68.0)

        // Materialise milestones and leave a reflection on p50.
        repository.reconcile(arcId)
        val p50Id = database.milestoneDao().observeByArc(arcId).first().first { it.kind == "p50" }.id
        repository.acknowledgeMilestones(listOf(p50Id), reflection = "Halfway felt good.", reflectionMilestoneId = p50Id)

        val data = repository.getRecapScreenData(arcId)
        assertNotNull(data)
        data!!

        // Recap numbers (R19) survived the Room round-trip.
        assertEquals(ArcStatus.COMPLETED, data.recap.status)
        assertEquals(4, data.recap.completeDays)
        assertEquals(5, data.recap.countedDays)
        assertEquals(0.8, data.recap.completionPct, 0.0001)
        assertEquals(5, data.recap.daysReached)
        assertEquals(3, data.recap.bestStreak)

        val hardest = requireNotNull(data.recap.hardestWeek)
        assertEquals("2026-W01", hardest.weekKey)
        assertEquals(LocalDate.of(2025, 12, 29), hardest.weekStart)
        assertEquals(LocalDate.of(2026, 1, 4), hardest.weekEnd)
        assertEquals(3, hardest.completeDays)
        assertEquals(4, hardest.countedDays)

        // The bits recap() does not carry: intention, the commitments, the totals.
        assertEquals("Prove the recap pipeline works.", data.intention)
        assertEquals(setOf("Gym", "Weight"), data.commitments.map { it.name }.toSet())

        val totalsById = data.recap.commitmentTotals.associateBy { it.commitmentId }
        assertEquals(CommitmentTotal.Binary(gymId, daysDone = 4), totalsById[gymId])
        assertEquals(CommitmentTotal.Log(weightId, first = 70.0, last = 68.0, daysRecorded = 2), totalsById[weightId])

        // Milestones come from the table with their reflections.
        val milestones = data.milestones.associateBy { it.kind }
        assertEquals("Halfway felt good.", milestones[MilestoneKind.P50]?.reflection)
        assertNull(milestones[MilestoneKind.P75]?.reflection)
        assertNotNull(milestones[MilestoneKind.START])
    }

    @Test
    fun `getRecapScreenData returns null for an arc that has not ended`() = runTest {
        val created = repository.createArc(
            name = "Active",
            startDate = LocalDate.of(2026, 1, 15),
            endDate = LocalDate.of(2026, 2, 15),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
            ),
        )
        val arcId = (created as CreateArcResult.Created).arcId

        // Clock is Jan 20, inside Jan 15 - Feb 15, so the arc is still active.
        assertNull(repository.getRecapScreenData(arcId))
    }
}
