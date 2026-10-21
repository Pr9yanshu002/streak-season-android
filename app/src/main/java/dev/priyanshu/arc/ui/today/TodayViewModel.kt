package dev.priyanshu.arc.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.Clock
import dev.priyanshu.arc.data.PendingMilestone
import dev.priyanshu.arc.data.TodaySnapshot
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.CommitmentStatus
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.rules.isoWeekKey
import dev.priyanshu.arc.ui.components.WeekStripDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

// A real androidx ViewModel now (docs/tasks/09-r15-r16-and-fixes.md): a Koin
// `single` lived for the whole process, so it would have collected the first
// arcId forever — broken the moment a second arc exists. `viewModelScope` is
// cancelled automatically when this ViewModel is cleared, same as the
// hand-made scope it replaces, but tied to the right lifecycle instead of the
// process's.
class TodayViewModel(
    private val repository: ArcRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    private val _pendingMilestone = MutableStateFlow<PendingMilestoneUiState?>(null)
    val pendingMilestone: StateFlow<PendingMilestoneUiState?> = _pendingMilestone.asStateFlow()

    private var arcId: String? = null
    private var observeJob: Job? = null

    init {
        refresh()
    }

    // Public and re-callable: this app has no navigation library, so
    // MainActivity's in-memory route switch reuses the same ViewModel
    // instance every time it swaps back to the Today route (Compose's
    // koinViewModel() cache keys by class, not by "which visit" — init only
    // ever runs once per process otherwise). TodayRoute re-invokes this via
    // DisposableEffect on every (re)composition into the route, so returning
    // to Today after creating/remixing an arc, or after a debug reseed,
    // re-reads the active arc instead of showing whatever was true the last
    // time this instance's init ran. Cancels any previous subscription first
    // so a stale arcId's Flow (pointing at a now-deleted arc, e.g. after a
    // debug reseed's clearAllTables()) can't emit into this state.
    fun refresh() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val id = repository.getActiveArcId()
            if (id == null) {
                arcId = null
                _state.value = TodayUiState.NoActiveArc
                _pendingMilestone.value = null
                return@launch
            }
            arcId = id
            repository.reconcile(id)

            // One combine, not two separate observeToday collectors — both
            // states are derived from the same resolution, and RulesEngine
            // isn't so cheap that running it twice per DB change is free.
            combine(
                repository.observeToday(id),
                repository.observePendingMilestones(id),
            ) { snapshot, pending -> snapshot to pending }
                .onEach { (snapshot, pending) ->
                    _state.value = snapshot.toUiState().withEndedStats(id)
                    _pendingMilestone.value = pending.toPendingUiState(
                        arcStartDate = snapshot.arc.startDate,
                        totalDays = snapshot.resolution.totalDays,
                        arcStatus = snapshot.resolution.arcStatus,
                        arcName = snapshot.arc.name,
                        todayDayNumber = snapshot.resolution.dayNumber,
                    )
                }
                .launchIn(this)
        }
    }

    // Called when TodayRoute leaves composition (DisposableEffect's
    // onDispose). refresh() only ever cancels the *previous* subscription
    // when re-entering Today — nothing otherwise stops this one while
    // another route is on screen. Without this, navigating away and letting
    // something else mutate the database (a debug reseed's
    // clearAllTables(), or in principle any other write) leaves this job
    // still collecting observeToday(arcId), which throws the moment the old
    // arc disappears out from under it — a crash on a screen that isn't
    // even visible.
    // Design 08's "You showed up X of Y days" is the recap's complete_days /
    // counted_days (R19). Those come from recap(), which needs every day's
    // entries — more than TodaySnapshot carries — so they're read once from
    // getRecapScreenData, the recap screen's own loader, and cached for this
    // arc. Only Ended needs them; every other state passes through untouched.
    private var endedStats: Pair<String, Pair<Int, Int>>? = null

    private suspend fun TodayUiState.withEndedStats(arcId: String): TodayUiState {
        if (this !is TodayUiState.Ended) return this
        val stats = endedStats?.takeIf { it.first == arcId }?.second
            ?: repository.getRecapScreenData(arcId)?.recap
                ?.let { it.completeDays to it.countedDays }
                ?.also { endedStats = arcId to it }
            ?: return this
        return copy(daysShowedUp = stats.first, daysCounted = stats.second)
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun acknowledgeMilestone(reflection: String?) {
        val pending = pendingMilestone.value ?: return
        viewModelScope.launch {
            repository.acknowledgeMilestones(
                ids = pending.ids,
                reflection = reflection,
                reflectionMilestoneId = pending.reflectionTargetId,
            )
        }
    }

    fun completeBinary(commitmentId: String, done: Boolean) {
        writeEntry(commitmentId, if (done) 1.0 else 0.0)
    }

    fun setCounterValue(commitmentId: String, value: Double) {
        writeEntry(commitmentId, value.coerceAtLeast(0.0))
    }

    fun setDurationValue(commitmentId: String, value: Double) {
        writeEntry(commitmentId, value.coerceAtLeast(0.0))
    }

    fun recordLog(commitmentId: String, value: Double) {
        writeEntry(commitmentId, value)
    }

    fun undo(commitmentId: String) {
        val id = arcId ?: return
        val today = (state.value as? TodayUiState.Loaded)?.today ?: return
        viewModelScope.launch { repository.undoEntry(id, today, commitmentId) }
    }

    // docs/tasks/17 — flags or unflags today as a rest day. Whether the flag
    // actually turns today into rest (R5/R6) is the engine's call; the new
    // state arrives back through observeToday.
    fun setRestDay(resting: Boolean) {
        val id = arcId ?: return
        val today = (state.value as? TodayUiState.Loaded)?.today ?: return
        viewModelScope.launch { repository.setRestTokenUsed(id, today, resting) }
    }

    private fun writeEntry(commitmentId: String, value: Double) {
        val id = arcId ?: return
        val today = (state.value as? TodayUiState.Loaded)?.today ?: return
        viewModelScope.launch { repository.recordEntry(id, today, commitmentId, value) }
    }
}

// Pure mapping from Resolution to UI state — no rule logic. R2/R3/R4 all
// live in RulesEngine now: dayNumber/totalDays and commitmentStatuses are
// read straight off Resolution, never recomputed here.
private fun TodaySnapshot.toUiState(): TodayUiState {
    // R9/R10 — checked before any date-range logic. A strict arc can fail
    // (and become terminal) well before its end date, so "today falls
    // outside the arc's range" is the wrong signal for Ended; arcStatus is
    // the direct one.
    if (resolution.arcStatus == ArcStatus.COMPLETED || resolution.arcStatus == ArcStatus.FAILED) {
        return TodayUiState.Ended(
            arcName = arc.name,
            totalDays = resolution.totalDays,
            completed = resolution.arcStatus == ArcStatus.COMPLETED,
            failedOnDate = resolution.failedOnDate,
            sourceArcId = arc.id,
            endedOn = resolution.failedOnDate ?: arc.endDate,
            // Start to end in date order; dayStates holds exactly the arc's range.
            season = resolution.dayStates.toSortedMap().values.toList(),
        )
    }

    val today = resolution.effectiveToday
    val statusesForToday = resolution.commitmentStatuses[today]
        // No entry for `today` means the arc hasn't started yet (R2) —
        // dayNumber is 0 until start_date.
        ?: return TodayUiState.NotStarted(
            arcName = arc.name,
            startDate = arc.startDate,
            totalDays = resolution.totalDays,
            // Calendar distance, not a rule: how many days until start_date.
            daysToGo = ChronoUnit.DAYS.between(today, arc.startDate).toInt(),
            intention = intention,
            // Required first, then bonus, each in the arc's own order.
            commitments = commitments
                .sortedBy { if (it.isRequired) 0 else 1 }
                .map { PlannedCommitmentUiState(it.id, it.name, it.isRequired, it.scheduleText()) },
        )

    val entriesByCommitmentId = todayEntries.associateBy { it.commitmentId }
    val commitmentStates = commitments.map { commitment ->
        val status = statusesForToday.getValue(commitment.id)
        CommitmentUiState(
            id = commitment.id,
            name = commitment.name,
            type = commitment.type,
            isRequired = commitment.isRequired,
            targetValue = commitment.targetValue,
            unit = commitment.unit,
            currentValue = entriesByCommitmentId[commitment.id]?.value,
            isSatisfied = status == CommitmentStatus.DONE,
            isScheduledToday = status != CommitmentStatus.NOT_SCHEDULED,
            scheduleText = commitment.scheduleText(),
        )
    }
    // Display order (docs/tasks/18): required, then bonus, then not today.
    // sortedBy is stable, so each group keeps the arc's own commitment order.
    val ordered = commitmentStates.sortedBy { c ->
        when {
            !c.isScheduledToday -> 2
            c.isRequired -> 0
            else -> 1
        }
    }
    val requiredToday = commitmentStates.filter { it.isRequired && it.isScheduledToday }

    // This ISO week's dates (Monday to Sunday) are plain calendar arithmetic;
    // every state is read from Resolution.dayStates, which only holds dates
    // inside the arc — anything else is an empty slot.
    val monday = today.with(DayOfWeek.MONDAY)
    val week = (0L..6L).map { offset ->
        val date = monday.plusDays(offset)
        WeekStripDay(
            date = date,
            state = resolution.dayStates[date] ?: DayState.OUT_OF_RANGE,
            isToday = date == today,
        )
    }

    return TodayUiState.Loaded(
        arcName = arc.name,
        dayNumber = resolution.dayNumber,
        totalDays = resolution.totalDays,
        today = today,
        todayState = resolution.dayStates[today] ?: DayState.PENDING,
        commitments = ordered,
        currentStreak = resolution.currentStreak,
        requiredDone = requiredToday.count { it.isSatisfied },
        requiredTotal = requiredToday.size,
        week = week,
        strictness = arc.strictness,
        // R6 — a lookup by the engine's own week key, not a count. Every week
        // the arc's range touches has an entry, so ?: 0 is only a fallback.
        restTokensLeftThisWeek = resolution.restTokensRemaining[isoWeekKey(today)] ?: 0,
    )
}

// The commitment's weekdays in words, for the "Not today · …" row. Daily is
// all seven days; WEEKDAYS reads the stored '1,3,5' list (ISO numbers,
// Mon = 1). This describes the schedule; it never decides whether a date is
// scheduled — that is Resolution's NOT_SCHEDULED (R3).
private fun Commitment.scheduleText(): String? {
    val days = when (scheduleType) {
        ScheduleType.DAILY -> DayOfWeek.entries.toSet()
        ScheduleType.WEEKDAYS -> scheduleDays.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .map { DayOfWeek.of(it) }
            .toSet()
    }
    return formatSchedule(days).ifBlank { null }
}

// Presentation choice, not a rule (docs/tasks/14): zero pending -> no card,
// exactly one -> the task-12 Single card unchanged, two or more -> one Summary
// listing them. The list arrives oldest-first from observePendingByArc and is
// kept in that order — not re-sorted here.
private fun List<PendingMilestone>.toPendingUiState(
    arcStartDate: LocalDate,
    totalDays: Int,
    arcStatus: ArcStatus,
    arcName: String,
    todayDayNumber: Int,
): PendingMilestoneUiState? = when {
    isEmpty() -> null
    size == 1 -> single().let { m ->
        PendingMilestoneUiState.Single(
            id = m.id,
            kind = m.kind,
            dayNumber = m.dayNumber(arcStartDate),
            totalDays = totalDays,
            triggeredOnDate = m.triggeredOnDate,
            arcCompleted = m.arcCompleted(arcStatus),
        )
    }
    else -> PendingMilestoneUiState.Summary(
        rows = map { m ->
            PendingMilestoneUiState.Summary.Row(
                id = m.id,
                kind = m.kind,
                dayNumber = m.dayNumber(arcStartDate),
                totalDays = totalDays,
                triggeredOnDate = m.triggeredOnDate,
                arcCompleted = m.arcCompleted(arcStatus),
            )
        },
        arcName = arcName,
        todayDayNumber = todayDayNumber,
    )
}

// dayNumber is plain calendar arithmetic (R2's definition: days since
// start_date, 1-indexed) applied to a date the engine already computed exactly
// (RulesEngine.requiredMilestones, R12 amended) — not a rule decision
// re-derived in :app, just counting from an engine-supplied date.
private fun PendingMilestone.dayNumber(arcStartDate: LocalDate): Int =
    ChronoUnit.DAYS.between(arcStartDate, triggeredOnDate).toInt() + 1

private fun PendingMilestone.arcCompleted(arcStatus: ArcStatus): Boolean? =
    if (kind == MilestoneKind.FINAL) arcStatus == ArcStatus.COMPLETED else null
