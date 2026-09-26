package dev.priyanshu.arc.data

import dev.priyanshu.arc.data.dao.ArcDao
import dev.priyanshu.arc.data.dao.CommitmentDao
import dev.priyanshu.arc.data.dao.DayDao
import dev.priyanshu.arc.data.dao.EntryDao
import dev.priyanshu.arc.data.dao.MilestoneDao
import dev.priyanshu.arc.data.entity.ArcEntity
import dev.priyanshu.arc.data.entity.CommitmentEntity
import dev.priyanshu.arc.data.entity.DayEntity
import dev.priyanshu.arc.data.mapper.toDomain
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
import dev.priyanshu.arc.domain.rules.Resolution
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// A commitment as specified at arc-creation time. Not the domain Commitment:
// that requires an id and an activeFrom, neither of which exists until this
// repository generates one and pins it to the arc's start date. icon has no
// domain-model equivalent at all — Commitment.kt trims it out as UI-only,
// so it only exists here, on the way into CommitmentEntity.
data class NewCommitment(
    val name: String,
    val icon: String? = null,
    val type: CommitmentType,
    val targetValue: Double? = null,
    val unit: String? = null,
    val isRequired: Boolean,
    val scheduleType: ScheduleType,
    val scheduleDays: String? = null,
)

// Everything RulesEngine.resolve() needs for one arc, loaded and mapped to
// domain models but not yet resolved. Shared by observeResolution and
// observeToday so the four-DAO combine only lives in one place.
private data class ArcSnapshot(
    val arc: Arc,
    val commitments: List<Commitment>,
    val days: List<Day>,
)

// What the Today screen needs: the resolved Resolution, plus the raw arc,
// commitments and today's entries that Resolution itself doesn't carry (see
// docs/tasks/08-today-screen.md's report — Resolution only exposes derived
// day states, not the per-commitment data a commitment list has to render).
data class TodaySnapshot(
    val arc: Arc,
    val commitments: List<Commitment>,
    val todayEntries: List<Entry>,
    val resolution: Resolution,
)

// R15 (docs/tasks/09-r15-r16-and-fixes.md) is a state a user can genuinely
// reach ("you already have an active arc"), not a programming error — so
// createArc() reports it as data the caller can branch on, rather than
// throwing. check()/require() stay reserved for actual invariant violations.
sealed interface CreateArcResult {
    data class Created(val arcId: String) : CreateArcResult
    data object ActiveArcExists : CreateArcResult
}

// A stored, unacknowledged milestone (R12) — the app's view of a milestones
// row, not RulesEngine's dated map. triggeredOnDate is always non-null here:
// every row this app writes comes from reconcile()'s upsert, which always
// supplies one.
data class PendingMilestone(
    val id: String,
    val kind: MilestoneKind,
    val triggeredOnDate: LocalDate,
)

// What the create flow needs to pre-fill a remix (docs/tasks/12: "pre-filled
// from the failed arc's name, commitments and rules, with fresh dates" —
// intention and dates are deliberately excluded, so they're not here).
data class RemixSource(
    val name: String,
    val commitments: List<NewCommitment>,
)

class ArcRepository(
    private val arcDao: ArcDao,
    private val commitmentDao: CommitmentDao,
    private val dayDao: DayDao,
    private val entryDao: EntryDao,
    private val milestoneDao: MilestoneDao,
    private val clock: Clock,
) {
    private fun observeSnapshot(arcId: String): Flow<ArcSnapshot> =
        combine(
            arcDao.observeById(arcId),
            commitmentDao.observeByArc(arcId),
            dayDao.observeByArc(arcId),
            entryDao.observeByArc(arcId),
        ) { arcEntity, commitmentEntities, dayEntities, entryEntities ->
            val arc = requireNotNull(arcEntity) { "arc $arcId not found" }.toDomain()
            val commitments = commitmentEntities.map { it.toDomain() }
            val entriesByDayId = entryEntities.groupBy { it.dayId }
            val days = dayEntities.map { day -> day.toDomain(entriesByDayId[day.id] ?: emptyList()) }
            ArcSnapshot(arc, commitments, days)
        }

    // Recomputes on every underlying change. RulesEngine.resolve() is a pure,
    // in-memory function over what's usually a few dozen rows — cheap enough
    // that caching the Resolution would be solving a problem that doesn't
    // exist yet.
    fun observeResolution(arcId: String): Flow<Resolution> =
        observeSnapshot(arcId).map { snapshot ->
            RulesEngine.resolve(snapshot.arc, snapshot.commitments, snapshot.days, clock.now())
        }

    fun observeToday(arcId: String): Flow<TodaySnapshot> =
        observeSnapshot(arcId).map { snapshot ->
            val resolution = RulesEngine.resolve(snapshot.arc, snapshot.commitments, snapshot.days, clock.now())
            val todayEntries = snapshot.days.find { it.date == resolution.effectiveToday }?.entries ?: emptyList()
            TodaySnapshot(snapshot.arc, snapshot.commitments, todayEntries, resolution)
        }

    suspend fun hasAnyArc(): Boolean = arcDao.count() > 0

    suspend fun getActiveArcId(): String? = arcDao.getActiveArcId()

    // R15 — a persistence question ("does a row with status = 'active'
    // exist?"), not something the pure rules engine can answer over
    // in-memory lists. createArc() below enforces it.
    suspend fun hasActiveArc(): Boolean = arcDao.getActiveArcId() != null

    suspend fun getCommitments(arcId: String): List<Commitment> =
        commitmentDao.observeByArc(arcId).first().map { it.toDomain() }

    // Remix (R9) pre-fill source. Reads CommitmentEntity directly rather than
    // through getCommitments()/Commitment — icon has no domain-model
    // equivalent (Commitment.kt trims it as UI-only), and NewCommitment is
    // exactly the shape createArc() needs, so building it here skips a
    // round-trip through the trimmed domain model and back.
    suspend fun getRemixSource(arcId: String): RemixSource? {
        val arc = arcDao.observeById(arcId).first() ?: return null
        val commitments = commitmentDao.observeByArc(arcId).first().map { c ->
            NewCommitment(
                name = c.name,
                icon = c.icon,
                type = CommitmentType.valueOf(c.type.uppercase()),
                targetValue = c.targetValue,
                unit = c.unit,
                isRequired = c.isRequired == 1,
                scheduleType = ScheduleType.valueOf(c.scheduleType.uppercase()),
                scheduleDays = c.scheduleDays,
            )
        }
        return RemixSource(name = arc.name, commitments = commitments)
    }

    // 'start' is excluded (docs/tasks/12) — it anchors the recap's timeline,
    // not a card the user dismisses.
    fun observePendingMilestones(arcId: String): Flow<List<PendingMilestone>> =
        milestoneDao.observePendingByArc(arcId).map { entities ->
            entities.map { entity ->
                PendingMilestone(
                    id = entity.id,
                    kind = MilestoneKind.valueOf(entity.kind.uppercase()),
                    triggeredOnDate = LocalDate.parse(
                        requireNotNull(entity.triggeredOnDate) {
                            "milestone ${entity.id} has no triggered_on_date"
                        }
                    ),
                )
            }
        }

    suspend fun acknowledgeMilestone(milestoneId: String, reflection: String?) {
        milestoneDao.acknowledge(milestoneId, reflection?.trim()?.ifBlank { null }, clock.millis())
    }

    suspend fun createArc(
        name: String,
        startDate: LocalDate,
        endDate: LocalDate,
        strictness: Strictness,
        restTokensPerWeek: Int,
        backfillDays: Int,
        dayBoundaryHour: Int,
        commitments: List<NewCommitment>,
        remixedFromArcId: String? = null,
    ): CreateArcResult {
        // R15 — at most one active arc at a time. A condition the user can
        // genuinely reach, so it's reported, not thrown.
        if (hasActiveArc()) return CreateArcResult.ActiveArcExists

        val now = clock.millis()
        val arcId = UUID.randomUUID().toString()

        arcDao.insert(
            ArcEntity(
                id = arcId,
                name = name,
                startDate = startDate.toString(),
                endDate = endDate.toString(),
                status = ArcStatus.ACTIVE.name.lowercase(),
                strictness = strictness.name.lowercase(),
                restTokensPerWeek = restTokensPerWeek,
                backfillDays = backfillDays,
                dayBoundaryHour = dayBoundaryHour,
                remixedFromArcId = remixedFromArcId,
                createdAt = now,
                updatedAt = now,
            )
        )
        commitmentDao.insertAll(
            commitments.mapIndexed { index, commitment ->
                CommitmentEntity(
                    id = UUID.randomUUID().toString(),
                    arcId = arcId,
                    name = commitment.name,
                    icon = commitment.icon,
                    type = commitment.type.name.lowercase(),
                    targetValue = commitment.targetValue,
                    unit = commitment.unit,
                    isRequired = if (commitment.isRequired) 1 else 0,
                    scheduleType = commitment.scheduleType.name.lowercase(),
                    scheduleDays = commitment.scheduleDays,
                    activeFrom = startDate.toString(),
                    sortOrder = index,
                    createdAt = now,
                    updatedAt = now,
                )
            }
        )
        return CreateArcResult.Created(arcId)
    }

    // docs/tasks/10-fix-entry-write-race.md — no check-then-act left: ensureDay
    // and entryDao.upsert are each a single atomic statement, so concurrent
    // callers for the same (arc, date, commitment) can no longer collide.
    suspend fun recordEntry(arcId: String, date: LocalDate, commitmentId: String, value: Double) {
        val now = clock.millis()
        val day = ensureDay(arcId, date, now)
        entryDao.upsert(
            id = UUID.randomUUID().toString(),
            dayId = day.id,
            commitmentId = commitmentId,
            value = value,
            now = now,
        )
    }

    suspend fun undoEntry(arcId: String, date: LocalDate, commitmentId: String) {
        val now = clock.millis()
        val day = dayDao.getByArcAndDate(arcId, date.toString()) ?: return
        val entry = entryDao.getByDayAndCommitment(day.id, commitmentId) ?: return
        entryDao.softDelete(entry.id, now)
    }

    suspend fun setNote(arcId: String, date: LocalDate, note: String?) {
        val now = clock.millis()
        val day = ensureDay(arcId, date, now)
        dayDao.update(day.copy(note = note, updatedAt = now))
    }

    suspend fun setRestTokenUsed(arcId: String, date: LocalDate, used: Boolean) {
        val now = clock.millis()
        val day = ensureDay(arcId, date, now)
        dayDao.update(day.copy(restTokenUsed = if (used) 1 else 0, updatedAt = now))
    }

    // The insert-or-leave-alone decision is one atomic statement (DayDao.upsert);
    // the row is guaranteed to exist by the time the follow-up SELECT runs,
    // regardless of which concurrent caller's insert actually won.
    private suspend fun ensureDay(arcId: String, date: LocalDate, now: Long): DayEntity {
        dayDao.upsert(UUID.randomUUID().toString(), arcId, date.toString(), now)
        return requireNotNull(dayDao.getByArcAndDate(arcId, date.toString())) {
            "day upsert for arc=$arcId date=$date did not produce a row"
        }
    }

    // R9/R10/R12 — persists the engine's derived arcStatus once it no longer
    // matches the stored row, and inserts whichever required milestone kinds
    // aren't stored yet. Idempotent by construction: updateStatusIfChanged's
    // WHERE clause only writes when status actually differs, and milestones
    // are only inserted for kinds missing from the observed set — so calling
    // this twice in a row (ViewModel init + onResume both call it) produces
    // at most one status write and one row per newly-required kind.
    suspend fun reconcile(arcId: String) {
        val resolution = observeResolution(arcId).first()
        val now = clock.millis()

        arcDao.updateStatusIfChanged(
            arcId = arcId,
            status = resolution.arcStatus.name.lowercase(),
            completedAt = if (resolution.arcStatus == ArcStatus.COMPLETED) now else null,
            failedOnDate = resolution.failedOnDate?.toString(),
            now = now,
        )

        val existingKinds = milestoneDao.observeByArc(arcId).first().map { it.kind }.toSet()
        resolution.requiredMilestones.forEach { (kind, triggeredOnDate) ->
            val wireKind = kind.name.lowercase()
            if (wireKind !in existingKinds) {
                milestoneDao.upsert(
                    id = UUID.randomUUID().toString(),
                    arcId = arcId,
                    kind = wireKind,
                    // The engine's exact crossing date (R12, amended) — never
                    // resolution.effectiveToday, which is when the app
                    // happened to notice, not when the threshold was crossed.
                    triggeredOnDate = triggeredOnDate.toString(),
                    now = now,
                )
            }
        }
    }
}
