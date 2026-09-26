package dev.priyanshu.arc.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.Clock
import dev.priyanshu.arc.data.PendingMilestone
import dev.priyanshu.arc.data.TodaySnapshot
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.CommitmentStatus
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
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
                    _state.value = snapshot.toUiState()
                    _pendingMilestone.value = pending.firstOrNull()?.toUiState(
                        arcStartDate = snapshot.arc.startDate,
                        totalDays = snapshot.resolution.totalDays,
                        arcStatus = snapshot.resolution.arcStatus,
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
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun acknowledgeMilestone(reflection: String?) {
        val milestone = pendingMilestone.value ?: return
        viewModelScope.launch { repository.acknowledgeMilestone(milestone.id, reflection) }
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
        )
    }

    val today = resolution.effectiveToday
    val statusesForToday = resolution.commitmentStatuses[today]
        // No entry for `today` means the arc hasn't started yet (R2) —
        // dayNumber is 0 until start_date.
        ?: return TodayUiState.NotStarted(arcName = arc.name, startDate = arc.startDate)

    val entriesByCommitmentId = todayEntries.associateBy { it.commitmentId }
    val commitmentStates = commitments.mapNotNull { commitment ->
        val status = statusesForToday.getValue(commitment.id)
        if (status == CommitmentStatus.NOT_SCHEDULED) return@mapNotNull null
        CommitmentUiState(
            id = commitment.id,
            name = commitment.name,
            type = commitment.type,
            isRequired = commitment.isRequired,
            targetValue = commitment.targetValue,
            unit = commitment.unit,
            currentValue = entriesByCommitmentId[commitment.id]?.value,
            isSatisfied = status == CommitmentStatus.DONE,
        )
    }

    return TodayUiState.Loaded(
        arcName = arc.name,
        dayNumber = resolution.dayNumber,
        totalDays = resolution.totalDays,
        today = today,
        todayState = resolution.dayStates[today] ?: DayState.PENDING,
        commitments = commitmentStates,
    )
}

// dayNumber here is plain calendar arithmetic (R2's definition: days since
// start_date, 1-indexed) applied to a date the engine already computed
// exactly (RulesEngine.requiredMilestones, R12 amended) — not a rule
// decision re-derived in :app, just counting from an engine-supplied date.
private fun PendingMilestone.toUiState(
    arcStartDate: LocalDate,
    totalDays: Int,
    arcStatus: ArcStatus,
): PendingMilestoneUiState =
    PendingMilestoneUiState(
        id = id,
        kind = kind,
        dayNumber = ChronoUnit.DAYS.between(arcStartDate, triggeredOnDate).toInt() + 1,
        totalDays = totalDays,
        arcCompleted = if (kind == MilestoneKind.FINAL) arcStatus == ArcStatus.COMPLETED else null,
    )
