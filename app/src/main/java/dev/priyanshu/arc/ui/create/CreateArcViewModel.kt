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
import dev.priyanshu.arc.ui.components.formatInputAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

enum class CreateArcStep { NAME_AND_DATES, COMMITMENTS, STRICTNESS, INTENTION, REVIEW }

// The steps the user counts through ("Step 2 of 4", the header bars).
// Review is not one of them: it shows no number.
const val NUMBERED_STEP_COUNT = 4

// This step's place in "Step N of 4", or null on Review.
val CreateArcStep.number: Int?
    get() = if (this == CreateArcStep.REVIEW) null else ordinal + 1

// A commitment as the creation flow edits it. Not NewCommitment: this needs
// a stable local id so the commitments step's list can edit/remove a row
// before it's ever sent to the repository, and scheduleDays as a real
// Set<Int> rather than the raw '1,3,5' wire string — that string-building
// is deferred to the one place that actually needs it, right before
// createArc() is called.
data class CommitmentDraft(
    val localId: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: CommitmentType = CommitmentType.BINARY,
    val targetText: String = "",
    val unit: String? = null,
    val isRequired: Boolean = true,
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val scheduleDays: Set<Int> = emptySet(),
) {
    // The device's own decimal mark is accepted, so "2,5" works on a phone
    // set to a language that writes it that way. See parseAmount.
    val targetValue: Double? get() = parseAmount(targetText, DecimalFormatSymbols.getInstance().decimalSeparator)

    val needsTarget: Boolean get() = type == CommitmentType.COUNTER || type == CommitmentType.DURATION

    // What stops this draft being saved, or null if it's ready.
    val error: String?
        get() = when {
            name.isBlank() -> "Give it a name."
            needsTarget && targetText.isNotBlank() && targetValue == null ->
                "Write the target as a number, like 2.5."
            // isFinite: toDoubleOrNull() also accepts "NaN" and "Infinity".
            needsTarget && targetValue.let { it == null || !it.isFinite() || it <= 0.0 } ->
                "Set a target above 0."
            needsTarget && unit.isNullOrBlank() -> "Add a unit, like ml or min."
            scheduleType == ScheduleType.WEEKDAYS && scheduleDays.isEmpty() -> "Pick at least one day."
            else -> null
        }

    // The tidy version that goes into the list: trimmed, and with fields that
    // don't apply to this type cleared.
    fun cleaned(): CommitmentDraft = copy(
        name = name.trim(),
        targetText = if (needsTarget) targetText.trim() else "",
        unit = if (needsTarget) unit?.trim() else null,
        scheduleDays = if (scheduleType == ScheduleType.WEEKDAYS) scheduleDays else emptySet(),
    )
}

// schema.sql defaults for a new arc. The strictness step opens Advanced when
// either differs, so a remix with its own values doesn't hide them.
const val DEFAULT_DAY_BOUNDARY_HOUR = 4
const val DEFAULT_BACKFILL_DAYS = 2

// Everything the create flow holds in memory across its five steps. Nothing
// here is written to the database until Begin (R15: "draft is not used in
// v0 — the creation flow holds state in memory and writes the arc only when
// the user confirms it").
data class CreateArcUiState(
    val step: CreateArcStep = CreateArcStep.NAME_AND_DATES,
    val today: LocalDate,
    val name: String = "",
    val startDate: LocalDate,
    val endDate: LocalDate,
    val commitments: List<CommitmentDraft> = emptyList(),
    val strictness: Strictness = Strictness.STANDARD,
    val restTokensPerWeek: Int = 1,
    val dayBoundaryHour: Int = DEFAULT_DAY_BOUNDARY_HOUR,
    val backfillDays: Int = DEFAULT_BACKFILL_DAYS,
    val intention: String = "",
    val activeArcExistsError: Boolean = false,
    val isSubmitting: Boolean = false,
    val showErrors: Boolean = false,
    val editing: CommitmentDraft? = null,
    val editorShowErrors: Boolean = false,
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
    // Changing backfill_days on the strictness step can make an already-chosen
    // start date too early (R16). Same isValidStartDate check step 1 uses.
    val strictnessError: String?
        get() = startDateError?.let { "$it Go back to step 1 to fix it." }

    val blockingError: String? get() = when (step) {
        CreateArcStep.NAME_AND_DATES -> step1Error
        CreateArcStep.COMMITMENTS -> step2Error
        CreateArcStep.STRICTNESS -> strictnessError
        CreateArcStep.REVIEW , CreateArcStep.INTENTION -> null
    }

    // Whether the open editor holds a commitment already in the list (Edit),
    // rather than a new one (Add).
    val isEditingExisting: Boolean
        get() = editing?.let { draft -> commitments.any { it.localId == draft.localId } } ?: false

    val lengthInDays: Int get() = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
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
        // The rule fields stay editable on the strictness step — this only
        // seeds them.
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
                        restTokensPerWeek = source.restTokensPerWeek.coerceIn(0, 7),
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

    fun removeCommitment(localId: String) =
        _state.update { it.copy(commitments = it.commitments.filterNot { c -> c.localId == localId }) }

    fun setStrictness(strictness: Strictness) = _state.update { it.copy(strictness = strictness) }

    fun setRestTokensPerWeek(count: Int) = _state.update { it.copy(restTokensPerWeek = count.coerceIn(0, 7)) }

    fun setDayBoundaryHour(hour: Int) = _state.update { it.copy(dayBoundaryHour = hour.coerceIn(0, 23)) }

    fun setBackfillDays(days: Int) = _state.update { it.copy(backfillDays = days.coerceAtLeast(0)) }

    fun setIntention(text: String) = _state.update { it.copy(intention = text) }

    fun back() = _state.update { s ->
        val previous = when (s.step) {
            CreateArcStep.NAME_AND_DATES -> CreateArcStep.NAME_AND_DATES
            CreateArcStep.COMMITMENTS -> CreateArcStep.NAME_AND_DATES
            CreateArcStep.STRICTNESS -> CreateArcStep.COMMITMENTS
            CreateArcStep.INTENTION -> CreateArcStep.STRICTNESS
            CreateArcStep.REVIEW -> CreateArcStep.INTENTION
        }
        // Changing step closes the commitment editor, so it can't reappear later.
        s.copy(step = previous, showErrors = false, editing = null, editorShowErrors = false)
    }

    // Next is always enabled. If the current step is invalid, next() stays put
    // and turns on showErrors so the screen draws that step's errors; moving
    // on (or back) turns them off again for the new step.
    fun next() = _state.update { s ->
        if (s.blockingError != null) return@update s.copy(showErrors = true)

        val nextStep = when (s.step) {
            CreateArcStep.NAME_AND_DATES -> CreateArcStep.COMMITMENTS
            CreateArcStep.COMMITMENTS -> CreateArcStep.STRICTNESS
            CreateArcStep.STRICTNESS -> CreateArcStep.INTENTION
            CreateArcStep.INTENTION -> CreateArcStep.REVIEW
            CreateArcStep.REVIEW -> CreateArcStep.REVIEW
        }
        s.copy(step = nextStep, showErrors = false, editing = null, editorShowErrors = false)
    }

    fun begin(onCreated: () -> Unit) {
        val s = _state.value
        if (s.isSubmitting) return
        if (s.step1Error != null || s.step2Error != null || s.strictnessError != null) return

        _state.update { it.copy(isSubmitting = true, activeArcExistsError = false) }

        viewModelScope.launch {
            val result = repository.createArc(
                name = s.name.trim(),
                startDate = s.startDate,
                endDate = s.endDate,
                strictness = s.strictness,
                restTokensPerWeek = if (s.strictness == Strictness.STANDARD) s.restTokensPerWeek else 0,
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

    fun startNewCommitment() = _state.update { it.copy(editing = CommitmentDraft(), editorShowErrors = false) }

    // An id that isn't in the list leaves the editor as it was.
    fun editCommitment(localId: String) = _state.update { s ->
        val draft = s.commitments.find { it.localId == localId } ?: return@update s
        s.copy(editing = draft, editorShowErrors = false)
    }

    // One function for every field in the editor: the screen says how to
    // change the draft, e.g. updateEditing { it.copy(name = "Water") }.
    fun updateEditing(change: (CommitmentDraft) -> CommitmentDraft) = _state.update { s ->
        val draft = s.editing ?: return@update s
        s.copy(editing = change(draft))
    }

    fun cancelEditing() = _state.update { it.copy(editing = null, editorShowErrors = false) }

    fun saveEditing() = _state.update { s ->
        val draft = s.editing ?: return@update s
        if (draft.error != null) return@update s.copy(editorShowErrors = true)
        s.copy(commitments = s.commitments.withSaved(draft.cleaned()), editing = null, editorShowErrors = false)
    }

    fun removeEditing() = _state.update { s ->
        val id = s.editing?.localId ?: return@update s
        s.copy(
            commitments = s.commitments.filterNot { it.localId == id },
            editing = null,
            editorShowErrors = false,
        )
    }

    // Saves a finished draft in one call, without opening the editor. Same
    // checks as saveEditing(): an incomplete draft is ignored.
    fun saveCommitment(draft: CommitmentDraft) = _state.update {
        if (draft.error != null) return@update it
        it.copy(commitments = it.commitments.withSaved(draft.cleaned()))
    }

    fun goToStep(step: CreateArcStep) = _state.update { it.copy(step = step, showErrors = false) }
}

// A typed amount as a number. "2.5" always works. A comma is read as the
// decimal mark only when the device uses one, so on an English phone "3,000"
// is rejected rather than quietly read as 3.
internal fun parseAmount(text: String, decimalSeparator: Char): Double? {
    val trimmed = text.trim()
    val withDot = if (decimalSeparator == ',') trimmed.replace(',', '.') else trimmed
    return withDot.toDoubleOrNull()
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
    targetText = targetValue?.let { formatInputAmount(it) }.orEmpty(),
    unit = unit,
    isRequired = isRequired,
    scheduleType = scheduleType,
    scheduleDays = scheduleDays.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
)

private fun List<CommitmentDraft>.withSaved(draft: CommitmentDraft): List<CommitmentDraft> {
    val index = indexOfFirst { it.localId == draft.localId }
    return if (index >= 0) toMutableList().also { it[index] = draft } else this + draft
}
