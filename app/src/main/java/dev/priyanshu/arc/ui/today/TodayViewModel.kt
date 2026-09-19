package dev.priyanshu.arc.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.Clock
import dev.priyanshu.arc.data.TodaySnapshot
import dev.priyanshu.arc.domain.model.CommitmentStatus
import dev.priyanshu.arc.domain.model.DayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private var arcId: String? = null

    init {
        viewModelScope.launch {
            val id = repository.getActiveArcId()
            if (id == null) {
                _state.value = TodayUiState.NoActiveArc
                return@launch
            }
            arcId = id
            repository.observeToday(id)
                .onEach { snapshot -> _state.value = snapshot.toUiState() }
                .launchIn(viewModelScope)
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
    val today = resolution.effectiveToday
    val statusesForToday = resolution.commitmentStatuses[today]
        // No entry for `today` means it's outside the arc's start/end range
        // (R2) — dayNumber pins at 0 before the arc starts and at totalDays
        // once it's over, so it tells the two cases apart without redoing
        // any date comparison here.
        ?: return if (resolution.dayNumber == 0) {
            TodayUiState.NotStarted(arcName = arc.name, startDate = arc.startDate)
        } else {
            TodayUiState.Ended(arcName = arc.name, totalDays = resolution.totalDays)
        }

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
