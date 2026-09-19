package dev.priyanshu.arc.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.domain.model.Arc
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.Day
import dev.priyanshu.arc.domain.model.Entry
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.domain.rules.RulesEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Proves the wiring end to end: create an arc with commitments, record
// entries across several days through the repository's public write API,
// read the result back, and check it against RulesEngine.resolve() called
// directly on the equivalent in-memory domain objects. This is not
// re-testing the engine (that's RulesEngineFixtureTest in :domain) — it's
// checking that Room + the mappers + the repository round-trip everything
// the engine needs without losing or corrupting it.
@RunWith(RobolectricTestRunner::class)
class ArcRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ArcRepository
    private lateinit var clock: FakeClock

    private val startDate = LocalDate.of(2026, 9, 1)
    private val endDate = LocalDate.of(2026, 9, 10)
    private val today = LocalDate.of(2026, 9, 5)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = FakeClock(LocalDateTime.of(today, LocalTime.of(10, 0)))
        repository = ArcRepository(database.arcDao(), database.commitmentDao(), database.dayDao(), database.entryDao(), clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `resolution read back through the repository matches the engine`() = runTest {
        val createResult = repository.createArc(
            name = "September",
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
                NewCommitment(
                    name = "Water",
                    type = CommitmentType.COUNTER,
                    targetValue = 8.0,
                    unit = "glasses",
                    isRequired = true,
                    scheduleType = ScheduleType.DAILY,
                ),
            ),
        )
        val arcId = (createResult as CreateArcResult.Created).arcId

        val persistedCommitments = database.commitmentDao().observeByArc(arcId).first()
        val meditateId = persistedCommitments.first { it.name == "Meditate" }.id
        val waterId = persistedCommitments.first { it.name == "Water" }.id

        // Sep 1-2: complete. Sep 3: missed (nothing logged). Sep 4: partial
        // (meditated, water short). Sep 5 (today): partial entries so far,
        // which R5 resolves to pending, not partial, since today isn't over.
        recordDay(repository, arcId, LocalDate.of(2026, 9, 1), meditateId, 1.0, waterId, 8.0)
        recordDay(repository, arcId, LocalDate.of(2026, 9, 2), meditateId, 1.0, waterId, 8.0)
        recordDay(repository, arcId, LocalDate.of(2026, 9, 4), meditateId, 1.0, waterId, 3.0)
        repository.recordEntry(arcId, today, meditateId, 1.0)

        repository.setNote(arcId, LocalDate.of(2026, 9, 2), "felt good")

        val actual = repository.observeResolution(arcId).first()

        val expectedArc = Arc(
            id = arcId,
            name = "September",
            startDate = startDate,
            endDate = endDate,
            status = ArcStatus.ACTIVE,
            strictness = Strictness.STANDARD,
            restTokensPerWeek = 1,
            backfillDays = 2,
            dayBoundaryHour = 4,
        )
        val expectedCommitments = listOf(
            Commitment(
                id = meditateId,
                name = "Meditate",
                type = CommitmentType.BINARY,
                isRequired = true,
                scheduleType = ScheduleType.DAILY,
                activeFrom = startDate,
            ),
            Commitment(
                id = waterId,
                name = "Water",
                type = CommitmentType.COUNTER,
                targetValue = 8.0,
                unit = "glasses",
                isRequired = true,
                scheduleType = ScheduleType.DAILY,
                activeFrom = startDate,
            ),
        )
        val expectedDays = listOf(
            Day(
                date = LocalDate.of(2026, 9, 1),
                entries = listOf(Entry(meditateId, 1.0), Entry(waterId, 8.0)),
            ),
            Day(
                date = LocalDate.of(2026, 9, 2),
                entries = listOf(Entry(meditateId, 1.0), Entry(waterId, 8.0)),
            ),
            Day(
                date = LocalDate.of(2026, 9, 4),
                entries = listOf(Entry(meditateId, 1.0), Entry(waterId, 3.0)),
            ),
            Day(
                date = today,
                entries = listOf(Entry(meditateId, 1.0)),
            ),
        )
        val expected = RulesEngine.resolve(expectedArc, expectedCommitments, expectedDays, clock.now())

        assertEquals(expected, actual)
    }

    private suspend fun recordDay(
        repository: ArcRepository,
        arcId: String,
        date: LocalDate,
        meditateId: String,
        meditateValue: Double,
        waterId: String,
        waterValue: Double,
    ) {
        repository.recordEntry(arcId, date, meditateId, meditateValue)
        repository.recordEntry(arcId, date, waterId, waterValue)
    }
}
