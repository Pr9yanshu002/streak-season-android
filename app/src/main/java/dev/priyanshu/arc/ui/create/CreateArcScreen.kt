package dev.priyanshu.arc.ui.create

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.ui.components.DayDisc
import dev.priyanshu.arc.ui.components.ReflectionField
import dev.priyanshu.arc.ui.components.StreakCard
import dev.priyanshu.arc.ui.components.formatAmount
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme
import dev.priyanshu.arc.ui.today.formatSchedule
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CreateArcRoute(
    remixFromArcId: String? = null,
    // Keyed by remixFromArcId, not the default class-only key: no navigation
    // library means koinViewModel() would otherwise cache one instance for
    // the whole process (MainActivity's in-memory route switch reuses it
    // every time it swaps back to CreateArc), so a second visit with a
    // different remix source — or a plain "start an arc" after a remix —
    // would silently reuse the first visit's pre-filled state instead of
    // re-running the parameterized factory.
    viewModel: CreateArcViewModel = koinViewModel(
        key = remixFromArcId ?: "new",
        parameters = { parametersOf(remixFromArcId) },
    ),
    onCreated: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    CreateArcScreen(
        state = state,
        actions = CreateArcActions(
            onNameChange = viewModel::setName,
            onStartDateChange = viewModel::setStartDate,
            onEndDateChange = viewModel::setEndDate,
            onQuickDuration = viewModel::pickQuickDuration,
            onAddCommitment = viewModel::startNewCommitment,
            onEditCommitment = viewModel::editCommitment,
            onEditingChange = viewModel::updateEditing,
            onSaveEditing = viewModel::saveEditing,
            onCancelEditing = viewModel::cancelEditing,
            onRemoveEditing = viewModel::removeEditing,
            onStrictnessChange = viewModel::setStrictness,
            onRestTokensChange = viewModel::setRestTokensPerWeek,
            onDayBoundaryHourChange = viewModel::setDayBoundaryHour,
            onBackfillDaysChange = viewModel::setBackfillDays,
            onIntentionChange = viewModel::setIntention,
            onBack = viewModel::back,
            onNext = viewModel::next,
            onBegin = { viewModel.begin(onCreated) },
            onCancel = onCancel,
            onGoToStep = viewModel::goToStep,
        ),
        modifier = modifier,
    )
}

class CreateArcActions(
    val onNameChange: (String) -> Unit,
    val onStartDateChange: (LocalDate) -> Unit,
    val onEndDateChange: (LocalDate) -> Unit,
    val onQuickDuration: (Int) -> Unit,
    val onAddCommitment: () -> Unit,
    val onEditCommitment: (String) -> Unit,
    val onEditingChange: ((CommitmentDraft) -> CommitmentDraft) -> Unit,
    val onSaveEditing: () -> Unit,
    val onCancelEditing: () -> Unit,
    val onRemoveEditing: () -> Unit,
    val onStrictnessChange: (Strictness) -> Unit,
    val onRestTokensChange: (Int) -> Unit,
    val onDayBoundaryHourChange: (Int) -> Unit,
    val onBackfillDaysChange: (Int) -> Unit,
    val onIntentionChange: (String) -> Unit,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onBegin: () -> Unit,
    val onCancel: () -> Unit,
    val onGoToStep: (CreateArcStep) -> Unit,
)

@Composable
fun CreateArcScreen(state: CreateArcUiState, actions: CreateArcActions, modifier: Modifier = Modifier) {
    val editing = state.editing
    if (editing != null) {
        CommitmentEditor(
            draft = editing,
            isExisting = state.isEditingExisting,
            error = if (state.editorShowErrors) editing.error else null,
            actions = actions,
            modifier = modifier,
        )
        return
    }
    // The phone's back gesture goes one step back, or leaves the flow from
    // step 1, instead of closing the app and losing the draft. While the
    // editor is open this isn't composed, so the editor's own handler wins.
    BackHandler(onBack = if (state.step == CreateArcStep.NAME_AND_DATES) actions.onCancel else actions.onBack)
    Column(modifier = modifier.fillMaxSize().imePadding()) {
        StepHeader(
            // Review fills every bar.
            currentStep = state.step.number ?: NUMBERED_STEP_COUNT,
            totalSteps = NUMBERED_STEP_COUNT,
            onClose = actions.onCancel,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s6, vertical = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s6),
        ) {
            val text = state.step.text()
            StepIntro(
                eyebrow = state.step.number?.let { "Step $it of $NUMBERED_STEP_COUNT" },
                title = text.title,
                subtitle = text.subtitle,
            )
            when (state.step) {
                CreateArcStep.NAME_AND_DATES -> NameAndDatesStep(state, actions)
                CreateArcStep.COMMITMENTS -> CommitmentsStep(state, actions)
                CreateArcStep.STRICTNESS -> StrictnessStep(state, actions)
                CreateArcStep.INTENTION -> IntentionStep(state, actions)
                CreateArcStep.REVIEW -> ReviewStep(state, actions)
            }
        }
        StepBottomBar(
            primaryText = when (state.step) {
                CreateArcStep.INTENTION -> "Review"
                CreateArcStep.REVIEW -> if (state.isSubmitting) "Starting…" else "Begin ${state.name.trim()}"
                else -> "Next"
            },
            onPrimary = if (state.step == CreateArcStep.REVIEW) actions.onBegin else actions.onNext,
            onBack = if (state.step == CreateArcStep.NAME_AND_DATES) null else actions.onBack,
        )
    }
}

// ---------------------------------------------------------------------------
// Step 1 — name and dates
// ---------------------------------------------------------------------------

@Composable
private fun NameAndDatesStep(state: CreateArcUiState, actions: CreateArcActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StreakTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = "Name",
            placeholder = "Winter Arc",
            error = state.shown(state.nameError),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            FieldLabel("Starts")
            val startError = state.shown(state.startDateError)
            DatePickerField(
                date = state.startDate,
                text = if (state.startDate == state.today) {
                    "Today · ${state.startDate.shortText()}"
                } else {
                    state.startDate.shortText()
                },
                minDate = state.earliestAllowedStartDate,
                isError = startError != null,
                onDateChange = actions.onStartDateChange,
            )
            // The error replaces the hint, so the field never has two lines of small text under it.
            if (startError != null) {
                FieldError(startError)
            } else {
                Text(
                    if (state.backfillDays == 0) {
                        "You can start today or any day ahead."
                    } else {
                        "You can start up to ${daysText(state.backfillDays)} back, or any day ahead."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = StreakTheme.colors.inkMuted,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            FieldLabel("How long")
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(Space.s2),
                verticalArrangement = Arrangement.spacedBy(Space.s2),
            ) {
                QUICK_LENGTHS.forEach { days ->
                    ChoiceChip(
                        text = "$days days",
                        selected = state.lengthInDays == days,
                        onClick = { actions.onQuickDuration(days) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            FieldLabel("Ends")
            val endError = state.shown(state.endDateError)
            DatePickerField(
                date = state.endDate,
                text = state.endDate.fullText(),
                minDate = state.startDate,
                isError = endError != null,
                onDateChange = actions.onEndDateChange,
            )
            endError?.let { FieldError(it) }
        }

        // Only when the dates make sense; a negative "-3 days" would just confuse.
        if (state.endDateError == null) {
            StreakCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                    Text(
                        "${state.lengthInDays} days",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        "${state.startDate.shortText()} – ${state.endDate.shortText()}, both days included",
                        style = MaterialTheme.typography.bodySmall,
                        color = StreakTheme.colors.inkMuted,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    date: LocalDate,
    minDate: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    text: String,
    isError: Boolean,
) {
    var showDialog by remember { mutableStateOf(false) }
    DateField(text = text, onClick = { showDialog = true }, isError = isError)

    if (showDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    minDate == null || !utcTimeMillis.toLocalDateFromUtcMillis().isBefore(minDate)
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(it.toLocalDateFromUtcMillis()) }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

// DatePicker works in UTC epoch millis internally, deliberately not the
// device's local zone — converting through local time would shift the
// selected calendar date near a day boundary.
private fun LocalDate.toUtcMillis(): Long = this.toEpochDay() * 86_400_000L

private fun Long.toLocalDateFromUtcMillis(): LocalDate = LocalDate.ofEpochDay(this / 86_400_000L)

// ---------------------------------------------------------------------------
// Step 2 — commitments
// ---------------------------------------------------------------------------

private const val MAX_COMMITMENTS = 10

// "Reach 3,000 ml · Every day", "Tick off · Saturdays and Sundays".
internal fun CommitmentDraft.detailText(): String {
    val what = when (type) {
        CommitmentType.BINARY -> "Tick off"
        CommitmentType.COUNTER -> "Reach ${formatAmount(targetValue ?: 0.0)} ${unit.orEmpty()}".trim()
        CommitmentType.DURATION -> "Spend ${formatAmount(targetValue ?: 0.0)} ${unit.orEmpty()}".trim()
        CommitmentType.LOG -> "Record a number"
    }
    val days = when (scheduleType) {
        ScheduleType.DAILY -> "Every day"
        ScheduleType.WEEKDAYS -> formatSchedule(scheduleDays.map { DayOfWeek.of(it) }.toSet())
    }
    return "$what · $days"
}

private fun Strictness.title(): String = when (this) {
    Strictness.STRICT -> "Strict"
    Strictness.STANDARD -> "Standard"
    Strictness.CHILL -> "Chill"
}

// From rules.md: a sealed partial or missed day fails a strict arc (R9);
// standard and chill never fail; rest days exist only in standard (R6).
internal fun Strictness.description(backfillDays: Int): String = when (this) {
    Strictness.STRICT ->
        if (backfillDays == 0) "A day you don't finish ends the arc. Days lock as soon as they end."
        else "A day you don't finish ends the arc. You get ${daysText(backfillDays)} to fill a day in before it locks."
    Strictness.STANDARD ->
        "A missed day breaks your streak, but the arc carries on. Rest days when you need them."
    Strictness.CHILL ->
        "The arc never ends early and there are no rest days to manage. Misses only lower your %."
}

// 4 → "4:00 AM", or "04:00" when the phone is set to 24-hour time.
@Composable
private fun hourText(hour: Int): String {
    val pattern = if (DateFormat.is24HourFormat(LocalContext.current)) "HH:mm" else "h:mm a"
    return LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

// The line under the day picker. All seven days has no "other days", so it
// just says so instead of "Every day. On other days it won't show on Today."
internal fun scheduleHint(days: Set<Int>): String = when (days.size) {
    0 -> "Pick the days it applies."
    7 -> "Every day of the week."
    else -> formatSchedule(days.map { DayOfWeek.of(it) }.toSet()) + ". On other days it won't show on Today."
}

@Composable
private fun CommitmentsStep(state: CreateArcUiState, actions: CreateArcActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Commitments", style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
            Text(
                "${state.commitments.size} of $MAX_COMMITMENTS",
                style = MaterialTheme.typography.labelLarge,
                color = StreakTheme.colors.inkMuted,
                modifier = Modifier.alignByBaseline(),
            )
        }

        if (state.commitments.isEmpty()) {
            Text(
                "Nothing yet. Add the first thing you'll do each day.",
                style = MaterialTheme.typography.bodyLarge,
                color = StreakTheme.colors.inkMuted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            state.commitments.forEach { commitment ->
                CommitmentListCard(
                    name = commitment.name,
                    detail = commitment.detailText(),
                    isRequired = commitment.isRequired,
                    onClick = { actions.onEditCommitment(commitment.localId) },
                )
            }
        }

        if (state.commitments.size < MAX_COMMITMENTS) {
            DashedAddButton("Add a commitment", onClick = actions.onAddCommitment)
        } else {
            Text(
                "That's the most an arc can hold.",
                style = MaterialTheme.typography.bodySmall,
                color = StreakTheme.colors.inkMuted,
            )
        }

        state.shown(state.step2Error)?.let { FieldError(it) }
        state.duplicateCommitmentNameWarning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = StreakTheme.colors.inkMuted)
        }
    }
}

// ---------------------------------------------------------------------------
// Commitment editor — full screen, shown while state.editing != null
// ---------------------------------------------------------------------------

// The words for each kind, shown on the editor's cards.
private class KindText(val type: CommitmentType, val title: String, val description: String)

private val KINDS = listOf(
    KindText(CommitmentType.BINARY, "Tick off", "Done or not done"),
    KindText(CommitmentType.COUNTER, "Reach a count", "Like 3,000 ml of water"),
    KindText(CommitmentType.DURATION, "Spend time", "Minutes towards a target"),
    KindText(CommitmentType.LOG, "Record a number", "Like your weight. Logging it is enough."),
)

@Composable
private fun CommitmentEditor(
    draft: CommitmentDraft,
    isExisting: Boolean,
    error: String?,
    actions: CreateArcActions,
    modifier: Modifier = Modifier,
) {
    // The phone's back gesture closes the editor, like ×, instead of leaving the whole flow.
    BackHandler(onBack = actions.onCancelEditing)
    val change = actions.onEditingChange

    Column(modifier.fillMaxSize().imePadding()) {
        EditorTopBar(
            title = if (isExisting) "Edit commitment" else "New commitment",
            onClose = actions.onCancelEditing,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.s6, vertical = Space.s4),
            verticalArrangement = Arrangement.spacedBy(Space.s6),
        ) {
            StreakTextField(
                value = draft.name,
                onValueChange = { text -> change { it.copy(name = text) } },
                label = "Name",
                placeholder = "Water",
            )

            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                FieldLabel("What kind")
                KindGrid(selected = draft.type, onSelect = { type -> change { it.copy(type = type) } })
            }

            if (draft.needsTarget) {
                val isTime = draft.type == CommitmentType.DURATION
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StreakTextField(
                        value = draft.targetText,
                        onValueChange = { text -> change { it.copy(targetText = text) } },
                        label = "Target each day",
                        placeholder = if (isTime) "30" else "3000",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                    StreakTextField(
                        value = draft.unit.orEmpty(),
                        onValueChange = { text -> change { it.copy(unit = text) } },
                        label = "Unit",
                        placeholder = if (isTime) "min" else "ml",
                        modifier = Modifier.width(110.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                FieldLabel("Does it decide the day?")
                SegmentedControl(
                    options = listOf(true to "Required", false to "Bonus"),
                    selected = draft.isRequired,
                    onSelect = { required -> change { it.copy(isRequired = required) } },
                )
                Text(
                    if (draft.isRequired) "The day only counts once this is done." else "Tracked, but never breaks a day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StreakTheme.colors.inkMuted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                FieldLabel("When")
                SegmentedControl(
                    options = listOf(ScheduleType.DAILY to "Every day", ScheduleType.WEEKDAYS to "Some days"),
                    selected = draft.scheduleType,
                    onSelect = { type -> change { it.copy(scheduleType = type) } },
                )
                if (draft.scheduleType == ScheduleType.WEEKDAYS) {
                    DayPicker(
                        selectedDays = draft.scheduleDays,
                        onToggle = { day ->
                            change { d -> d.copy(scheduleDays = if (day in d.scheduleDays) d.scheduleDays - day else d.scheduleDays + day) }
                        },
                    )
                    Text(
                        scheduleHint(draft.scheduleDays),
                        style = MaterialTheme.typography.bodySmall,
                        color = StreakTheme.colors.inkMuted,
                    )
                }
            }

            error?.let { FieldError(it) }
        }

        StepBottomBar(
            primaryText = if (isExisting) "Save changes" else "Add commitment",
            onPrimary = actions.onSaveEditing,
            onBack = if (isExisting) actions.onRemoveEditing else null,
            backText = "Remove",
        )
    }
}

@Composable
private fun KindGrid(selected: CommitmentType, onSelect: (CommitmentType) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Space.s2)) {
        KINDS.chunked(2).forEach { pair ->
            // IntrinsicSize.Min + fillMaxHeight: both cards in a row grow to the
            // taller one's height, so a two-line description doesn't make them uneven.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                pair.forEach { kind ->
                    OptionCard(
                        title = kind.title,
                        description = kind.description,
                        selected = kind.type == selected,
                        onClick = { onSelect(kind.type) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

// Errors stay hidden until the user taps Next on an invalid step.
private fun CreateArcUiState.shown(error: String?): String? = if (showErrors) error else null

private val QUICK_LENGTHS = listOf(30, 60, 75, 90)

// ---------------------------------------------------------------------------
// Step 3 — strictness (and the advanced day-boundary/backfill settings)
// ---------------------------------------------------------------------------

@Composable
private fun StrictnessStep(state: CreateArcUiState, actions: CreateArcActions) {
    var showAdvanced by rememberSaveable {
        mutableStateOf(
            state.dayBoundaryHour != DEFAULT_DAY_BOUNDARY_HOUR || state.backfillDays != DEFAULT_BACKFILL_DAYS,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
            Strictness.entries.forEach { level ->
                OptionCard(
                    title = level.title(),
                    description = level.description(state.backfillDays),
                    selected = level == state.strictness,
                    onClick = { actions.onStrictnessChange(level) },
                    showRadio = true,
                )
            }
        }

        if (state.strictness == Strictness.STANDARD) {
            SectionCard {
                StepperRow(
                    title = "Rest days",
                    subtitle = if (state.restTokensPerWeek == 0) {
                        "None · a missed day always breaks your streak"
                    } else {
                        "${state.restTokensPerWeek} a week · your streak carries on"
                    },
                    onDecrease = { actions.onRestTokensChange(state.restTokensPerWeek - 1) },
                    onIncrease = { actions.onRestTokensChange(state.restTokensPerWeek + 1) },
                    decreaseLabel = "One fewer rest day a week",
                    increaseLabel = "One more rest day a week",
                    leading = { DayDisc(state = DayState.REST, isToday = false) },
                )
            }
        }

        ExpandableSection(
            title = "Advanced",
            expanded = showAdvanced,
            onToggle = { showAdvanced = !showAdvanced },
        ) {
            StepperRow(
                title = "Day starts at ${hourText(state.dayBoundaryHour)}",
                subtitle = if (state.dayBoundaryHour == 0) {
                    "The day changes at midnight."
                } else {
                    "Late nights count as the day before."
                },
                onDecrease = { actions.onDayBoundaryHourChange(state.dayBoundaryHour - 1) },
                onIncrease = { actions.onDayBoundaryHourChange(state.dayBoundaryHour + 1) },
                decreaseLabel = "Start the day an hour earlier",
                increaseLabel = "Start the day an hour later",
            )
            HorizontalDivider(color = StreakTheme.colors.line)
            StepperRow(
                title = if (state.backfillDays == 0) {
                    "Only today can be filled in"
                } else {
                    "Fill in up to ${daysText(state.backfillDays)} back"
                },
                subtitle = "After that, a day locks.",
                onDecrease = { actions.onBackfillDaysChange(state.backfillDays - 1) },
                onIncrease = { actions.onBackfillDaysChange(state.backfillDays + 1) },
                decreaseLabel = "One fewer day to fill in",
                increaseLabel = "One more day to fill in",
            )
        }
        state.shown(state.strictnessError)?.let { FieldError(it) }
    }
}

// ---------------------------------------------------------------------------
// Step 4 — intention
// ---------------------------------------------------------------------------

@Composable
private fun IntentionStep(state: CreateArcUiState, actions: CreateArcActions) {
    ReflectionField(
        value = state.intention,
        onValueChange = actions.onIntentionChange,
        label = "You wrote",
        helper = "Skip it if nothing comes to mind.",
        placeholder = "I'm doing this because…",
    )
}

// ---------------------------------------------------------------------------
// Step 5 — review
// ---------------------------------------------------------------------------

@Composable
private fun ReviewStep(state: CreateArcUiState, actions: CreateArcActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s4)) {
        ReviewCard(title = state.name.trim(), onEdit = { actions.onGoToStep(CreateArcStep.NAME_AND_DATES) }) {
            Text("${state.lengthInDays} days · ${state.startsText()}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "${state.startDate.shortText()} – ${state.endDate.fullText()}",
                style = MaterialTheme.typography.bodySmall,
                color = StreakTheme.colors.inkMuted,
            )
        }

        val count = state.commitments.size
        ReviewCard(
            title = if (count == 1) "1 commitment" else "$count commitments",
            onEdit = { actions.onGoToStep(CreateArcStep.COMMITMENTS) },
        ) {
            Column(Modifier.padding(top = Space.s2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.commitments.forEach { commitment ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
                        Column(Modifier.weight(1f)) {
                            Text(commitment.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                commitment.detailText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = StreakTheme.colors.inkMuted,
                            )
                        }
                        RequirementChip(commitment.isRequired)
                    }
                }
            }
        }

        ReviewCard(title = state.strictness.title(), onEdit = { actions.onGoToStep(CreateArcStep.STRICTNESS) }) {
            if (state.strictness == Strictness.STANDARD) {
                Text(state.restDaysText(), style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Day starts at ${hourText(state.dayBoundaryHour)} · ${state.backfillText()}",
                style = MaterialTheme.typography.bodySmall,
                color = StreakTheme.colors.inkMuted,
            )
        }

        if (state.intention.isNotBlank()) {
            ReviewCard(
                title = "You wrote",
                onEdit = { actions.onGoToStep(CreateArcStep.INTENTION) },
                titleStyle = MaterialTheme.typography.labelLarge,
            ) {
                Text(state.intention.trim(), style = StreakTheme.type.voice)
            }
        }

        // R15: reachable only if an arc became active while this flow was open.
        if (state.activeArcExistsError) {
            Text(
                "You already have an active arc. You can start a new one once it ends.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(StreakTheme.colors.larchSoft)
                    .padding(Space.s4),
            )
        }
    }
}

// The words at the top of each step, in one place so the design copy is easy
// to check and change.
private class StepText(val title: String, val subtitle: String? = null)

private fun CreateArcStep.text(): StepText = when (this) {
    CreateArcStep.NAME_AND_DATES -> StepText("Name it and pick the dates")
    CreateArcStep.COMMITMENTS -> StepText(
        "What will you do?",
        "Required ones decide whether a day counts. Bonus ones are tracked, but never break a day.",
    )
    CreateArcStep.STRICTNESS -> StepText("How strict should it be?")
    CreateArcStep.INTENTION -> StepText(
        "Why are you doing this?",
        "One line, in your own words. You'll see it before day 1, and again in your recap.",
    )
    CreateArcStep.REVIEW -> StepText("Ready to begin?")
}

internal fun CreateArcUiState.startsText(): String = when {
    startDate == today -> "starts today"
    startDate.isAfter(today) -> "starts ${startDate.shortText()}"
    else -> "began ${startDate.shortText()}"
}

// Same words as the backfill row on the strictness step, in lower case.
internal fun CreateArcUiState.backfillText(): String =
    if (backfillDays == 0) "only today can be filled in" else "fill in up to ${daysText(backfillDays)} back"

internal fun CreateArcUiState.restDaysText(): String = when (restTokensPerWeek) {
    0 -> "No rest days"
    1 -> "1 rest day a week"
    else -> "$restTokensPerWeek rest days a week"
}

// Previews only: every action does nothing.
private val PreviewActions = CreateArcActions(
    onNameChange = {},
    onStartDateChange = {},
    onEndDateChange = {},
    onQuickDuration = {},
    onAddCommitment = {},
    onEditCommitment = {},
    onEditingChange = {},
    onSaveEditing = {},
    onCancelEditing = {},
    onRemoveEditing = {},
    onStrictnessChange = {},
    onRestTokensChange = {},
    onDayBoundaryHourChange = {},
    onBackfillDaysChange = {},
    onIntentionChange = {},
    onBack = {},
    onNext = {},
    onBegin = {},
    onCancel = {},
    onGoToStep = {},
)

@PreviewLightDark
@Composable
private fun ReviewStepPreview() {
    val today = LocalDate.of(2026, 10, 22)
    val state = CreateArcUiState(
        step = CreateArcStep.REVIEW,
        today = today,
        name = "Winter Arc",
        startDate = today,
        endDate = today.plusDays(59),
        commitments = listOf(
            CommitmentDraft(name = "Water", type = CommitmentType.COUNTER, targetText = "3000", unit = "ml"),
            CommitmentDraft(
                name = "Read",
                type = CommitmentType.DURATION,
                targetText = "20",
                unit = "min",
                isRequired = false,
            ),
        ),
        intention = "I want my evenings back.",
    )
    StreakTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CreateArcScreen(state = state, actions = PreviewActions)
        }
    }
}

