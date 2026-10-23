package dev.priyanshu.arc.ui.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.ScheduleType
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.ui.theme.Space
import java.time.LocalDate
import java.util.UUID
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
            onSaveCommitment = viewModel::saveCommitment,
            onRemoveCommitment = viewModel::removeCommitment,
            onStrictnessChange = viewModel::setStrictness,
            onRestTokensChange = viewModel::setRestTokensPerWeek,
            onDayBoundaryHourChange = viewModel::setDayBoundaryHour,
            onBackfillDaysChange = viewModel::setBackfillDays,
            onIntentionChange = viewModel::setIntention,
            onBack = viewModel::back,
            onNext = viewModel::next,
            onBegin = { viewModel.begin(onCreated) },
            onCancel = onCancel,
        ),
        modifier = modifier,
    )
}

class CreateArcActions(
    val onNameChange: (String) -> Unit,
    val onStartDateChange: (LocalDate) -> Unit,
    val onEndDateChange: (LocalDate) -> Unit,
    val onQuickDuration: (Int) -> Unit,
    val onSaveCommitment: (CommitmentDraft) -> Unit,
    val onRemoveCommitment: (String) -> Unit,
    val onStrictnessChange: (Strictness) -> Unit,
    val onRestTokensChange: (Int) -> Unit,
    val onDayBoundaryHourChange: (Int) -> Unit,
    val onBackfillDaysChange: (Int) -> Unit,
    val onIntentionChange: (String) -> Unit,
    val onBack: () -> Unit,
    val onNext: () -> Unit,
    val onBegin: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
fun CreateArcScreen(state: CreateArcUiState, actions: CreateArcActions, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
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
        ){
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
                CreateArcStep.REVIEW -> ReviewStep(state)
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
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = { Text("Name") },
            singleLine = true,
            isError = state.shown(state.nameError)!=null,
            supportingText = { state.shown(state.nameError)?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            Text("Start date", style = MaterialTheme.typography.labelLarge)
            DatePickerField(
                date = state.startDate,
                minDate = state.earliestAllowedStartDate,
                onDateChange = actions.onStartDateChange,
            )
            state.shown(state.startDateError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        Column {
            Text("End date", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 75, 90).forEach { days ->
                    OutlinedButton(onClick = { actions.onQuickDuration(days) }) { Text("$days d") }
                }
            }
            Spacer(Modifier.height(8.dp))
            DatePickerField(
                date = state.endDate,
                minDate = state.startDate,
                onDateChange = actions.onEndDateChange,
            )
            state.shown(state.endDateError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(date: LocalDate, minDate: LocalDate?, onDateChange: (LocalDate) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { showDialog = true }) { Text(date.toString()) }

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

@Composable
private fun CommitmentsStep(state: CreateArcUiState, actions: CreateArcActions) {
    var editingDraft by rememberSaveable { mutableStateOf<String?>(null) } // localId, or "new"
    var showEditor by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.commitments.forEach { commitment ->
            CommitmentRow(
                commitment = commitment,
                onEdit = {
                    editingDraft = commitment.localId
                    showEditor = true
                },
                onRemove = { actions.onRemoveCommitment(commitment.localId) },
            )
        }

        if (state.commitments.size < 10) {
            OutlinedButton(onClick = {
                editingDraft = null
                showEditor = true
            }) { Text("Add commitment") }
        } else {
            Text("Up to 10 commitments per arc.", style = MaterialTheme.typography.bodySmall)
        }

        state.shown(state.step2Error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        state.duplicateCommitmentNameWarning?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showEditor) {
        val initial = editingDraft?.let { id -> state.commitments.find { it.localId == id } }
        CommitmentEditorDialog(
            initial = initial,
            onSave = { draft ->
                actions.onSaveCommitment(draft)
                showEditor = false
            },
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
private fun CommitmentRow(commitment: CommitmentDraft, onEdit: () -> Unit, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(commitment.name, style = MaterialTheme.typography.titleMedium)
                Text(commitment.summary(), style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

private fun CommitmentDraft.summary(): String {
    val kind = type.name.lowercase()
    val requiredness = if (isRequired) "required" else "bonus"
    val schedule = when (scheduleType) {
        ScheduleType.DAILY -> "daily"
        ScheduleType.WEEKDAYS -> "weekdays " + scheduleDays.sorted().joinToString(",")
    }
    val target = if (targetValue != null) " · ${targetValue.toInt()} ${unit.orEmpty()}" else ""
    return "$kind · $requiredness · $schedule$target"
}

@Composable
private fun CommitmentEditorDialog(
    initial: CommitmentDraft?,
    onSave: (CommitmentDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: CommitmentType.BINARY) }
    var targetText by remember { mutableStateOf(initial?.targetValue?.toInt()?.toString() ?: "") }
    var unit by remember { mutableStateOf(initial?.unit ?: "") }
    var isRequired by remember { mutableStateOf(initial?.isRequired ?: true) }
    var scheduleType by remember { mutableStateOf(initial?.scheduleType ?: ScheduleType.DAILY) }
    var scheduleDays by remember { mutableStateOf(initial?.scheduleDays ?: emptySet()) }

    val needsTarget = type == CommitmentType.COUNTER || type == CommitmentType.DURATION
    val targetValue = targetText.toDoubleOrNull()
    val isValid = name.isNotBlank() &&
        (!needsTarget || (targetValue != null && targetValue > 0 && unit.isNotBlank())) &&
        (scheduleType == ScheduleType.DAILY || scheduleDays.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add commitment" else "Edit commitment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)

                Text("Type", style = MaterialTheme.typography.labelLarge)
                CommitmentType.entries.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = type == option, onClick = { type = option })
                        Text(option.name.lowercase())
                    }
                }

                if (needsTarget) {
                    OutlinedTextField(value = targetText, onValueChange = { targetText = it }, label = { Text("Target") }, singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Unit") }, singleLine = true)
                }

                Text("Required or bonus", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = isRequired, onClick = { isRequired = true })
                    Text("Required")
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = !isRequired, onClick = { isRequired = false })
                    Text("Bonus")
                }

                Text("Schedule", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = scheduleType == ScheduleType.DAILY, onClick = { scheduleType = ScheduleType.DAILY })
                    Text("Every day")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = scheduleType == ScheduleType.WEEKDAYS, onClick = { scheduleType = ScheduleType.WEEKDAYS })
                    Text("Specific weekdays")
                }
                if (scheduleType == ScheduleType.WEEKDAYS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEKDAY_LABELS.forEach { (iso, label) ->
                            FilterChip(
                                selected = iso in scheduleDays,
                                onClick = { scheduleDays = if (iso in scheduleDays) scheduleDays - iso else scheduleDays + iso },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    onSave(
                        CommitmentDraft(
                            localId = initial?.localId ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            type = type,
                            targetValue = if (needsTarget) targetValue else null,
                            unit = if (needsTarget) unit.trim() else null,
                            isRequired = isRequired,
                            scheduleType = scheduleType,
                            scheduleDays = if (scheduleType == ScheduleType.WEEKDAYS) scheduleDays else emptySet(),
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val WEEKDAY_LABELS = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

// Errors stay hidden until the user taps Next on an invalid step.
private fun CreateArcUiState.shown(error:String?) : String? = if(showErrors)  error else null

// ---------------------------------------------------------------------------
// Step 3 — strictness (and the advanced day-boundary/backfill settings)
// ---------------------------------------------------------------------------

@Composable
private fun StrictnessStep(state: CreateArcUiState, actions: CreateArcActions) {
    var showAdvanced by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Strictness", style = MaterialTheme.typography.labelLarge)
        StrictnessOption(
            strictness = Strictness.STRICT,
            description = "A sealed miss archives the arc (R9). No safety net.",
            selected = state.strictness == Strictness.STRICT,
            onSelect = actions.onStrictnessChange,
        )
        StrictnessOption(
            strictness = Strictness.STANDARD,
            description = "Rest tokens let you skip some days without breaking your streak (R6).",
            selected = state.strictness == Strictness.STANDARD,
            onSelect = actions.onStrictnessChange,
        )
        StrictnessOption(
            strictness = Strictness.CHILL,
            description = "Never fails. Finishes with whatever percentage you earn.",
            selected = state.strictness == Strictness.CHILL,
            onSelect = actions.onStrictnessChange,
        )

        if (state.strictness == Strictness.STANDARD) {
            Column {
                Text("Rest tokens per week", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { actions.onRestTokensChange(state.restTokensPerWeek - 1) }) { Text("−") }
                    Text("${state.restTokensPerWeek}", modifier = Modifier.padding(horizontal = 12.dp))
                    OutlinedButton(onClick = { actions.onRestTokensChange(state.restTokensPerWeek + 1) }) { Text("+") }
                }
            }
        }

        HorizontalDivider()

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced" else "Advanced")
        }
        if (showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column {
                    Text("Day boundary hour (0–23, default 4)", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { actions.onDayBoundaryHourChange(state.dayBoundaryHour - 1) }) { Text("−") }
                        Text("${state.dayBoundaryHour}", modifier = Modifier.padding(horizontal = 12.dp))
                        OutlinedButton(onClick = { actions.onDayBoundaryHourChange(state.dayBoundaryHour + 1) }) { Text("+") }
                    }
                }
                Column {
                    Text("Backfill days (default 2)", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { actions.onBackfillDaysChange(state.backfillDays - 1) }) { Text("−") }
                        Text("${state.backfillDays}", modifier = Modifier.padding(horizontal = 12.dp))
                        OutlinedButton(onClick = { actions.onBackfillDaysChange(state.backfillDays + 1) }) { Text("+") }
                    }
                }
            }
        }

        state.shown(state.strictnessError)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

    }
}

// ---------------------------------------------------------------------------
// Step 4 — intention
// ---------------------------------------------------------------------------

@Composable
private fun IntentionStep(state: CreateArcUiState, actions: CreateArcActions){
    OutlinedTextField(
        value = state.intention,
        onValueChange = actions.onIntentionChange,
        label = { Text("Intention (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StrictnessOption(
    strictness: Strictness,
    description: String,
    selected: Boolean,
    onSelect: (Strictness) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        RadioButton(selected = selected, onClick = { onSelect(strictness) })
        Column {
            Text(strictness.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------
// Step 5 — review
// ---------------------------------------------------------------------------

@Composable
private fun ReviewStep(state: CreateArcUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(state.name, style = MaterialTheme.typography.headlineSmall)
        Text("${state.startDate} → ${state.endDate}")
        Text(state.strictness.name.lowercase().replaceFirstChar { it.uppercase() })
        if (state.strictness == Strictness.STANDARD) {
            Text("${state.restTokensPerWeek} rest token(s) per week")
        }
        Text("Day boundary hour: ${state.dayBoundaryHour}, backfill: ${state.backfillDays} day(s)")
        if (state.intention.isNotBlank()) {
            Text("\"${state.intention}\"", style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(8.dp))
        Text("Commitments", style = MaterialTheme.typography.labelLarge)
        state.commitments.forEach { commitment ->
            Text("• ${commitment.name} — ${commitment.summary()}")
        }

        if (state.activeArcExistsError) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You already have an active arc. Finish, fail, or archive it before starting a new one.",
                color = MaterialTheme.colorScheme.error,
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
