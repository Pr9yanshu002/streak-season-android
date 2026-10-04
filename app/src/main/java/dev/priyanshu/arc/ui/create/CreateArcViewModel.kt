package dev.priyanshu.arc.ui.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.Clock
import dev.priyanshu.arc.data.CreateArcResult
import dev.priyanshu.arc.data.NewCommitment
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.domain.rules.earliestAllowedStartDate
import dev.priyanshu.arc.domain.rules.hasAtLeastOneRequiredCommitment
import dev.priyanshu.arc.domain.rules.isValidArcName
import dev.priyanshu.arc.domain.rules.isValidStartDate
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CreateArcStep { NAME_AND_DATES, COMMITMENTS, RULES_AND_INTENTION, REVIEW }

// A commitment as the creation flow edits it. Not NewCommitment: this needs
// a stable local id so the step-2 list can edit/remove a row before it's
// ever sent to the repository, and scheduleDays as a real Set<Int> rather
// than the raw '1,3,5' wire string — that string-building is deferred to
// the one place that actually needs it, right before createArc() is called.
data class CommitmentDraft(
    val localId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: CommitmentType = CommitmentType.BINARY,
    val targetValue: Double? = null,
    val unit: String? = null,
    val isRequired: Boolean = true,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val scheduleDays: Set<Int> = emptySet(),
)

// Everything the three-step flow holds in memory. Nothing here is written to
// the database until Begin (R15: "draft is not used in v0 — the creation
// flow holds state in memory and writes the arc only when the user confirms
// it").
data class CreateArcUiState(
    val step: CreateArcStep = CreateArcStep.NAME_AND_DATES,
    val today: LocalDate,
    val name: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val commitments: List<CommitmentDraft> = emptyList(),
    val strictness: Strictness = Strictness.STANDARD,
    val restTokensPerWeek: Int = 1,
    val dayBoundaryHour: Int = 4,
    val backfillDays: Int = 2,
    val intention: String = "",
    val activeArcExistsError: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    // R16, called, not re-derived — see earliestAllowedStartDate/isValidStartDate.
    val earliestAllowedStartDate: LocalDate get() = earliestAllowedStartDate(today, backfillDays)

    // R17 — enforced from :domain (isValidArcName), not re-derived here, so the
    // Swift app validates names identically.
    val nameError: String?
        get() = if (!isValidArcName(name)) "Name can't be empty." else null

    val startDateError: String?
        get() = if (!isValidStartDate(startDate, today, backfillDays)) {
            "Start date can't be before $earliestAllowedStartDate — that's as far back as the " +
                "$backfillDays-day backfill window reaches."
        } else {
            null
        }

    val endDateError: String?
        get() = if (endDate.isBefore(startDate)) "End date can't be before the start date." else null

    val step1Error: String? get() = nameError ?: startDateError ?: endDateError

    // R18 — enforced from :domain (hasAtLeastOneRequiredCommitment), not
    // re-derived here, so the Swift app blocks all-bonus arcs identically.
    val hasRequiredCommitment: Boolean
        get() = hasAtLeastOneRequiredCommitment(commitments.map { it.isRequired })

    val step2Error: String?
        get() = when {
            commitments.isEmpty() -> "Add at least one commitment."
            !hasRequiredCommitment ->
                "At least one commitment must be required — an all-bonus arc can never show progress (R8)."
            else -> null
        }

    val duplicateCommitmentNameWarning: String?
        get() {
            val duplicated = commitments
                .groupingBy { it.name.trim().lowercase() }
                .eachCount()
                .any { it.value > 1 }
            return if (duplicated) "Two or more commitments share a name. That's allowed, but worth checking." else null
        }

    // Changing backfill_days (step 3) can invalidate an already-chosen start
    // date. Re-checked with the same isValidStartDate call step 1 uses —
    // surfaced here rather than silently moving the user's start date.
    val step3Error: String?
        get() = startDateError?.let { "$it Go back to step 1 to fix it." }
}

class CreateArcViewModel(
    private val repository: ArcRepository,
    private val clock: Clock,
    private val remixFromArcId: String? = null,
) : ViewModel() {
    private val _state: MutableStateFlow<CreateArcUiState>
    val state: StateFlow<CreateArcUiState>

    init {
        // R1's effectiveToday (the day-boundary-hour transform) is internal
        // to :domain and not reachable here — see this task's report. Using
        // the plain calendar date is off by at most one day, only in the
        // few hours before the boundary hour.
        val today = clock.now().toLocalDate()
        _state = MutableStateFlow(
            CreateArcUiState(today = today, startDate = today, endDate = today.plusDays(59))
        )
        state = _state.asStateFlow()

        // Remix (R9): pre-fill name, commitments and the rule set from the
        // failed arc, so the retry starts from the same rules it ran under.
        // The rule fields stay editable on step 3 — this only seeds them.
        // Dates always start fresh (today..+59, set above) and intention is
        // deliberately left blank — the user writes a new one, not the old
        // one (docs/tasks/12).
        remixFromArcId?.let { sourceArcId ->
            viewModelScope.launch {
                val source = repository.getRemixSource(sourceArcId) ?: return@launch
                _state.update {
                    it.copy(
                        name = source.name,
                        commitments = source.commitments.map { c -> c.toDraft() },
                        strictness = source.strictness,
                        restTokensPerWeek = source.restTokensPerWeek,
                        backfillDays = source.backfillDays,
                        dayBoundaryHour = source.dayBoundaryHour,
                    )
                }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun setStartDate(date: LocalDate) = _state.update { it.copy(startDate = date) }

    fun setEndDate(date: LocalDate) = _state.update { it.copy(endDate = date) }

    fun pickQuickDuration(days: Int) = _state.update { it.copy(endDate = it.startDate.plusDays((days - 1).toLong())) }

    fun saveCommitment(draft: CommitmentDraft) = _state.update { s ->
        val index = s.commitments.indexOfFirst { it.localId == draft.localId }
        val commitments = if (index >= 0) {
            s.commitments.toMutableList().also { it[index] = draft }
        } else {
            s.commitments + draft
        }
        s.copy(commitments = commitments)
    }

    fun removeCommitment(localId: String) =
        _state.update { it.copy(commitments = it.commitments.filterNot { c -> c.localId == localId }) }

    fun setStrictness(strictness: Strictness) = _state.update { it.copy(strictness = strictness) }

    fun setRestTokensPerWeek(count: Int) = _state.update { it.copy(restTokensPerWeek = count.coerceAtLeast(0)) }

    fun setDayBoundaryHour(hour: Int) = _state.update { it.copy(dayBoundaryHour = hour.coerceIn(0, 23)) }

    fun setBackfillDays(days: Int) = _state.update { it.copy(backfillDays = days.coerceAtLeast(0)) }

    fun setIntention(text: String) = _state.update { it.copy(intention = text) }

    fun back() = _state.update { s ->
        val previous = when (s.step) {
            CreateArcStep.NAME_AND_DATES -> CreateArcStep.NAME_AND_DATES
            CreateArcStep.COMMITMENTS -> CreateArcStep.NAME_AND_DATES
            CreateArcStep.RULES_AND_INTENTION -> CreateArcStep.COMMITMENTS
            CreateArcStep.REVIEW -> CreateArcStep.RULES_AND_INTENTION
        }
        s.copy(step = previous)
    }

    // No-ops (rather than crashing or forcing a jump) if the current step is
    // invalid — the inline error is already visible, and the Next/Begin
    // button driving this is disabled while it's non-null anyway.
    fun next() = _state.update { s ->
        val blockingError = when (s.step) {
            CreateArcStep.NAME_AND_DATES -> s.step1Error
            CreateArcStep.COMMITMENTS -> s.step2Error
            CreateArcStep.RULES_AND_INTENTION -> s.step3Error
            CreateArcStep.REVIEW -> null
        }
        if (blockingError != null) return@update s

        val nextStep = when (s.step) {
            CreateArcStep.NAME_AND_DATES -> CreateArcStep.COMMITMENTS
            CreateArcStep.COMMITMENTS -> CreateArcStep.RULES_AND_INTENTION
            CreateArcStep.RULES_AND_INTENTION -> CreateArcStep.REVIEW
            CreateArcStep.REVIEW -> CreateArcStep.REVIEW
        }
        s.copy(step = nextStep)
    }

    fun begin(onCreated: () -> Unit) {
        val s = _state.value
        if (s.step1Error != null || s.step2Error != null || s.step3Error != null) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, activeArcExistsError = false) }
            val result = repository.createArc(
                name = s.name.trim(),
                startDate = s.startDate,
                endDate = s.endDate,
                strictness = s.strictness,
                restTokensPerWeek = s.restTokensPerWeek,
                backfillDays = s.backfillDays,
                dayBoundaryHour = s.dayBoundaryHour,
                commitments = s.commitments.map { it.toNewCommitment() },
                remixedFromArcId = remixFromArcId,
                intention = s.intention,
            )
            when (result) {
                is CreateArcResult.Created -> onCreated()
                // R15 — unreachable if the empty-state entry point is gated
                // correctly, but a real branch, not a crash.
                is CreateArcResult.ActiveArcExists ->
                    _state.update { it.copy(isSubmitting = false, activeArcExistsError = true) }
            }
        }
    }
}

private fun CommitmentDraft.toNewCommitment(): NewCommitment = NewCommitment(
    name = name.trim(),
    type = type,
    targetValue = targetValue,
    unit = unit?.trim()?.ifBlank { null },
    isRequired = isRequired,
    scheduleType = scheduleType,
    scheduleDays = if (scheduleType == ScheduleType.WEEKDAYS) scheduleDays.sorted().joinToString(",") else null,
)

// Remix pre-fill's reverse of toNewCommitment() above. icon is dropped — the
// creation flow has no icon picker to show it in, same as every other
// commitment created through this flow.
private fun NewCommitment.toDraft(): CommitmentDraft = CommitmentDraft(
    name = name,
    type = type,
    targetValue = targetValue,
    unit = unit,
    isRequired = isRequired,
    scheduleType = scheduleType,
    scheduleDays = scheduleDays.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
)
