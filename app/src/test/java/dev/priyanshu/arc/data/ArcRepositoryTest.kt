package dev.priyanshu.arc.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.priyanshu.arc.domain.model.Arc
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.Day
import dev.priyanshu.arc.domain.model.Entry
import dev.priyanshu.arc.domain.model.MilestoneKind
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // docs/tasks/12-lifecycle-milestones.md: "a test that reconciles twice
    // and asserts one status write and one milestone row per kind." Calling
    // reconcile() a second time (with the clock advanced, so a spurious
    // second write would be visible) must leave completed_at and every
    // milestone row exactly as the first call left them.
    @Test
    fun `reconcile is idempotent — a second call does not rewrite status or duplicate milestones`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 11, 1), LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val createResult = repository.createArc(
            name = "October",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 10),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )
        val arcId = (createResult as CreateArcResult.Created).arcId

        repository.reconcile(arcId)
        val arcAfterFirst = requireNotNull(database.arcDao().observeById(arcId).first())
        val milestonesAfterFirst = database.milestoneDao().observeByArc(arcId).first()

        assertEquals("completed", arcAfterFirst.status)
        assertEquals(setOf("start", "p25", "p50", "p75", "final"), milestonesAfterFirst.map { it.kind }.toSet())

        clock.advanceTo(LocalDateTime.of(LocalDate.of(2026, 11, 2), LocalTime.of(10, 0)))
        repository.reconcile(arcId)
        val arcAfterSecond = requireNotNull(database.arcDao().observeById(arcId).first())
        val milestonesAfterSecond = database.milestoneDao().observeByArc(arcId).first()

        // Same completed_at, not re-stamped with the second call's clock —
        // updateStatusIfChanged's WHERE guard means no second write happened.
        assertEquals(arcAfterFirst.completedAt, arcAfterSecond.completedAt)
        // Same rows (same ids, same updated_at) — the diff-against-stored
        // check in reconcile() skipped every kind, not just avoided a crash
        // on the unique index.
        assertEquals(milestonesAfterFirst.map { it.id to it.updatedAt }.toSet(), milestonesAfterSecond.map { it.id to it.updatedAt }.toSet())
    }

    // Task 13, Part 1c — the two-weeks-closed case. A 20-day arc opened for
    // the first time on day 16: p25/p50/p75 were all crossed while the app
    // was closed. reconcile() must date each milestone row at its exact
    // crossing day (R12, amended), never at the open date — this is the
    // whole reason 12d amended R12. Also documents what the user sees: three
    // cards (start is excluded), oldest first.
    @Test
    fun `milestones crossed while the app was closed are dated at their crossing day, not the open date`() = runTest {
        val open = LocalDate.of(2026, 9, 16)
        val clock = FakeClock(LocalDateTime.of(open, LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val createResult = repository.createArc(
            name = "Closed For Two Weeks",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 20),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )
        val arcId = (createResult as CreateArcResult.Created).arcId

        repository.reconcile(arcId)

        val byKind = database.milestoneDao().observeByArc(arcId).first().associateBy { it.kind }
        // final is NOT required yet — the arc is still active on day 16.
        assertEquals(setOf("start", "p25", "p50", "p75"), byKind.keys)
        // Each row's triggered_on_date is its crossing day, not 2026-09-16.
        assertEquals("2026-09-01", byKind.getValue("start").triggeredOnDate)
        assertEquals("2026-09-05", byKind.getValue("p25").triggeredOnDate)
        assertEquals("2026-09-10", byKind.getValue("p50").triggeredOnDate)
        assertEquals("2026-09-15", byKind.getValue("p75").triggeredOnDate)

        // What the user sees: three cards (start excluded), oldest first.
        val pending = repository.observePendingMilestones(arcId).first()
        assertEquals(listOf(MilestoneKind.P25, MilestoneKind.P50, MilestoneKind.P75), pending.map { it.kind })
        assertEquals(
            listOf(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 15)),
            pending.map { it.triggeredOnDate },
        )
    }

    // Task 13, Part 1a — remix. Every copied commitment must get a FRESH
    // uuid (reusing the source's id would make the source's entries point at
    // the new arc's commitment), remixed_from_arc_id must be set, and nothing
    // else — days, entries, milestones — may be copied. The source arc must
    // be untouched. Checked against the database, not the code.
    @Test
    fun `remix creates a new arc with fresh commitment ids and remixed_from set, copying nothing else`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 16), LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        // Source: a STRICT arc that missed day one (sealed by day 16), so it
        // fails on reconcile and stops being active — remix is only offered on
        // a failed arc, and R15 would block createArc() if it were still active.
        val sourceResult = repository.createArc(
            name = "Original",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 30),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = listOf(
                NewCommitment(name = "Gym", type = CommitmentType.BINARY, isRequired = true, scheduleType = ScheduleType.DAILY),
            ),
        )
        val sourceId = (sourceResult as CreateArcResult.Created).arcId
        val sourceCommitmentId = database.commitmentDao().observeByArc(sourceId).first().single().id
        // Give the source real history so we can prove it is not copied.
        repository.recordEntry(sourceId, LocalDate.of(2026, 9, 10), sourceCommitmentId, 1.0)
        repository.reconcile(sourceId)
        assertEquals("failed", requireNotNull(database.arcDao().observeById(sourceId).first()).status)

        // Remix along the same path CreateArcViewModel uses: getRemixSource()
        // then createArc(remixedFromArcId = source).
        val remixSource = requireNotNull(repository.getRemixSource(sourceId))
        val remixResult = repository.createArc(
            name = remixSource.name,
            startDate = LocalDate.of(2026, 9, 16),
            endDate = LocalDate.of(2026, 10, 15),
            strictness = Strictness.STRICT,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = remixSource.commitments,
            remixedFromArcId = sourceId,
        )
        val remixId = (remixResult as CreateArcResult.Created).arcId

        val remixArc = requireNotNull(database.arcDao().observeById(remixId).first())
        assertEquals(sourceId, remixArc.remixedFromArcId)
        assertEquals("Original", remixArc.name)

        val remixCommitment = database.commitmentDao().observeByArc(remixId).first().single()
        assertEquals("Gym", remixCommitment.name)
        assertNotEquals(sourceCommitmentId, remixCommitment.id)

        // Nothing else copied onto the remix.
        assertEquals(emptyList<Any>(), database.dayDao().observeByArc(remixId).first())
        assertEquals(emptyList<Any>(), database.milestoneDao().observeByArc(remixId).first())

        // Source untouched: still failed, still carries its own day + milestones.
        assertEquals("failed", requireNotNull(database.arcDao().observeById(sourceId).first()).status)
        assertTrue(database.dayDao().observeByArc(sourceId).first().isNotEmpty())
        assertTrue(database.milestoneDao().observeByArc(sourceId).first().isNotEmpty())
    }

    // Task 14 — acknowledging a summary card must clear exactly the ids shown
    // and no others. A milestone not in the acknowledged set (here p75, as if
    // it had only just gone pending while the card was on screen) stays
    // pending, so the user still sees it next time.
    @Test
    fun `acknowledging a set of ids clears exactly those, leaving the rest pending`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 16), LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val arcId = createTwoWeeksClosedArc(repository)
        repository.reconcile(arcId)

        val pending = repository.observePendingMilestones(arcId).first()
        assertEquals(listOf(MilestoneKind.P25, MilestoneKind.P50, MilestoneKind.P75), pending.map { it.kind })
        val p25 = pending[0]
        val p50 = pending[1]
        val p75 = pending[2]

        // Acknowledge only p25 and p50 — reflection on the later of the two.
        repository.acknowledgeMilestones(
            ids = listOf(p25.id, p50.id),
            reflection = null,
            reflectionMilestoneId = p50.id,
        )

        val stillPending = repository.observePendingMilestones(arcId).first()
        assertEquals(listOf(MilestoneKind.P75), stillPending.map { it.kind })
        assertEquals(p75.id, stillPending.single().id)

        val byId = database.milestoneDao().observeByArc(arcId).first().associateBy { it.id }
        assertNotNull(byId.getValue(p25.id).acknowledgedAt)
        assertNotNull(byId.getValue(p50.id).acknowledgedAt)
        assertNull(byId.getValue(p75.id).acknowledgedAt)
    }

    // Task 14 — the one reflection on a summary card is saved on the latest
    // milestone only (latest triggered_on_date) and nowhere else: a reflection
    // typed on day 16 is not a day-5 reflection.
    @Test
    fun `reflection lands on the latest milestone and nowhere else`() = runTest {
        val clock = FakeClock(LocalDateTime.of(LocalDate.of(2026, 9, 16), LocalTime.of(10, 0)))
        val repository = ArcRepository(
            database.arcDao(),
            database.commitmentDao(),
            database.dayDao(),
            database.entryDao(),
            database.milestoneDao(),
            clock,
        )
        val arcId = createTwoWeeksClosedArc(repository)
        repository.reconcile(arcId)

        val pending = repository.observePendingMilestones(arcId).first()
        val latest = pending.last() // p75, latest triggered_on_date

        repository.acknowledgeMilestones(
            ids = pending.map { it.id },
            reflection = "  Kept going even after the slow week.  ",
            reflectionMilestoneId = latest.id,
        )

        val byId = database.milestoneDao().observeByArc(arcId).first().associateBy { it.id }
        // Trimmed, on the latest only.
        assertEquals("Kept going even after the slow week.", byId.getValue(latest.id).reflection)
        pending.filter { it.id != latest.id }.forEach { assertNull(byId.getValue(it.id).reflection) }
        // Every one shown was acknowledged.
        pending.forEach { assertNotNull(byId.getValue(it.id).acknowledgedAt) }
    }

    // start 2026-09-01 .. end 2026-09-20, opened on 2026-09-16: p25/p50/p75 all
    // crossed (days 5/10/15), final not yet. CHILL so the arc stays active and
    // never fails out from under the test. Same scenario as the two-weeks-closed
    // reconcile test above.
    private suspend fun createTwoWeeksClosedArc(repository: ArcRepository): String {
        val result = repository.createArc(
            name = "Closed For Two Weeks",
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 20),
            strictness = Strictness.CHILL,
            restTokensPerWeek = 0,
            backfillDays = 2,
            dayBoundaryHour = 4,
            commitments = emptyList(),
        )
        return (result as CreateArcResult.Created).arcId
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
