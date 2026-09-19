package dev.priyanshu.arc.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// docs/tasks/10-fix-entry-write-race.md — proves the check-then-act race in
// ArcRepository.recordEntry() is real before fixing it, then guards against
// it regressing.
@RunWith(RobolectricTestRunner::class)
class ArcRepositoryWriteRaceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ArcRepository
    private lateinit var arcId: String
    private lateinit var commitmentId: String

    private val today = LocalDate.of(2026, 9, 12)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        repository = ArcRepository(database.arcDao(), database.commitmentDao(), database.dayDao(), database.entryDao(), clock)

        val result = repository.createArc(
            name = "Race Arc",
            startDate = today.minusDays(5),
            endDate = today.plusDays(20),
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(
                    name = "Weight",
                    type = CommitmentType.LOG,
                    isRequired = true,
                    scheduleType = ScheduleType.DAILY,
                ),
            ),
        )
        arcId = (result as CreateArcResult.Created).arcId
        commitmentId = repository.getCommitments(arcId).first().id
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `concurrent recordEntry calls for the same commitment and date do not crash and leave exactly one row`() = runTest {
        coroutineScope {
            val jobs = (1..40).map { i ->
                async(Dispatchers.Default) {
                    repository.recordEntry(arcId, today, commitmentId, i.toDouble())
                }
            }
            jobs.awaitAll()
        }

        val day = requireNotNull(database.dayDao().getByArcAndDate(arcId, today.toString()))
        assertEquals(1, database.entryDao().countByDayAndCommitment(day.id, commitmentId))
    }

    @Test
    fun `re-recording a previously undone entry revives it instead of leaving deleted_at set`() = runTest {
        repository.recordEntry(arcId, today, commitmentId, 70.0)
        repository.undoEntry(arcId, today, commitmentId)
        repository.recordEntry(arcId, today, commitmentId, 71.0)

        val day = requireNotNull(database.dayDao().getByArcAndDate(arcId, today.toString()))
        val entry = requireNotNull(database.entryDao().getByDayAndCommitmentIncludingDeleted(day.id, commitmentId))
        assertEquals(71.0, entry.value, 0.0001)
        assertNull(entry.deletedAt)
    }
}
