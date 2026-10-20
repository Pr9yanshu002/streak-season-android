package dev.priyanshu.arc.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.ui.theme.StreakTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.koin.androidx.compose.koinViewModel

@Composable
fun TodayRoute(
    viewModel: TodayViewModel = koinViewModel(),
    onCreateArc: () -> Unit = {},
    onRemix: (sourceArcId: String) -> Unit = {},
    onViewRecap: (sourceArcId: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // No navigation library — MainActivity's in-memory route switch reuses
    // this same ViewModel instance every time it swaps back to Today
    // (koinViewModel() caches by class, not by "which visit"), so init alone
    // would only ever run once per process. Entering this route re-reads the
    // active arc (TodayViewModel.refresh()); leaving it cancels that
    // subscription (stopObserving()) so it can't crash on a database change
    // made while this screen isn't even on screen (e.g. a debug reseed).
    DisposableEffect(Unit) {
        viewModel.refresh()
        onDispose { viewModel.stopObserving() }
    }

    val state by viewModel.state.collectAsState()
    val pendingMilestone by viewModel.pendingMilestone.collectAsState()
    TodayScreen(
        state = state,
        pendingMilestone = pendingMilestone,
        onAcknowledgeMilestone = viewModel::acknowledgeMilestone,
        onToggleBinary = viewModel::completeBinary,
        onCounterChange = viewModel::setCounterValue,
        onDurationChange = viewModel::setDurationValue,
        onLogChange = viewModel::recordLog,
        onUndo = viewModel::undo,
        onSetRestDay = viewModel::setRestDay,
        onCreateArc = onCreateArc,
        onRemix = onRemix,
        onViewRecap = onViewRecap,
        modifier = modifier,
    )
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    pendingMilestone: PendingMilestoneUiState?,
    onAcknowledgeMilestone: (reflection: String?) -> Unit,
    onToggleBinary: (commitmentId: String, done: Boolean) -> Unit,
    onCounterChange: (commitmentId: String, value: Double) -> Unit,
    onDurationChange: (commitmentId: String, value: Double) -> Unit,
    onLogChange: (commitmentId: String, value: Double) -> Unit,
    onUndo: (commitmentId: String) -> Unit,
    onSetRestDay: (resting: Boolean) -> Unit,
    onCreateArc: () -> Unit,
    onRemix: (sourceArcId: String) -> Unit,
    onViewRecap: (sourceArcId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A pending milestone takes over the whole screen (docs/tasks/12) — shown
    // instead of whatever `state` would otherwise render, checked first
    // regardless of which TodayUiState we're in. One pending shows the
    // original card; two or more collapse into a single summary (docs/tasks/14).
    when (pendingMilestone) {
        is PendingMilestoneUiState.Single -> {
            MilestoneCard(milestone = pendingMilestone, onAcknowledge = onAcknowledgeMilestone, modifier = modifier)
            return
        }
        is PendingMilestoneUiState.Summary -> {
            MilestoneSummaryCard(summary = pendingMilestone, onAcknowledge = onAcknowledgeMilestone, modifier = modifier)
            return
        }
        null -> Unit
    }

    when (state) {
        is TodayUiState.Loading ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        // Covers both "no arc has ever existed" and "an arc exists but none
        // is active" (completed/failed/archived) — getActiveArcId() only
        // ever matches status = 'active', so both cases already collapse
        // into this one state; no separate state needed for the second.
        is TodayUiState.NoActiveArc ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "An arc is a time-boxed chapter with daily commitments and a rule set. " +
                            "Start one to see it here.",
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onCreateArc) { Text("Start an arc") }
                }
            }

        is TodayUiState.NotStarted ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("${state.arcName} starts ${state.startDate}.")
            }

        is TodayUiState.Ended ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        if (state.completed) {
                            "${state.arcName} is complete — ${state.totalDays} days."
                        } else {
                            "${state.arcName} ended on ${state.failedOnDate}."
                        },
                        textAlign = TextAlign.Center,
                    )
                    // Recap is the main thing to do from here (docs/tasks/15);
                    // the run-again / start-new actions live on its last card.
                    Button(onClick = { onViewRecap(state.sourceArcId) }) { Text("View recap") }
                    OutlinedButton(onClick = onCreateArc) { Text("Start a new arc") }
                    // Remix only makes sense for a failed arc — a completed
                    // one has nothing to retry.
                    if (!state.completed) {
                        OutlinedButton(onClick = { onRemix(state.sourceArcId) }) { Text("Remix this arc") }
                    }
                }
            }

        is TodayUiState.Loaded ->
            TodayContent(
                state = state,
                onToggleBinary = onToggleBinary,
                onCounterChange = onCounterChange,
                onDurationChange = onDurationChange,
                onLogChange = onLogChange,
                onUndo = onUndo,
                onSetRestDay = onSetRestDay,
                modifier = modifier,
            )
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState.Loaded,
    onToggleBinary: (String, Boolean) -> Unit,
    onCounterChange: (String, Double) -> Unit,
    onDurationChange: (String, Double) -> Unit,
    onLogChange: (String, Double) -> Unit,
    onUndo: (String) -> Unit,
    onSetRestDay: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(state.arcName, style = MaterialTheme.typography.headlineSmall)
                Text("Day ${state.dayNumber} of ${state.totalDays}", style = MaterialTheme.typography.bodyMedium)
                Text("Today: ${state.todayState.label()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        // Commitments below stay usable on a rest day — finishing them all
        // makes today complete and hands the token back (R5/R6).
        state.restDayRow?.let { row ->
            item {
                RestDayCard(
                    row = row,
                    tokensLeft = state.restTokensLeftThisWeek,
                    onSetRestDay = onSetRestDay,
                )
            }
        }
        items(state.commitments, key = { it.id }) { commitment ->
            CommitmentCard(
                commitment = commitment,
                onToggleBinary = { done -> onToggleBinary(commitment.id, done) },
                onCounterChange = { value -> onCounterChange(commitment.id, value) },
                onDurationChange = { value -> onDurationChange(commitment.id, value) },
                onLogChange = { value -> onLogChange(commitment.id, value) },
                onUndo = { onUndo(commitment.id) },
            )
        }
    }
}

// docs/tasks/17 — stock Material 3, same Card as the commitment rows. Whether
// it shows at all, and which form, is decided by TodayUiState.Loaded.restDayRow.
@Composable
private fun RestDayCard(
    row: RestDayRow,
    tokensLeft: Int,
    onSetRestDay: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (row) {
                RestDayRow.OFFER -> {
                    Text("Need a rest day?", style = MaterialTheme.typography.titleMedium)
                    Text("$tokensLeft left this week", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { onSetRestDay(true) }) { Text("Rest today") }
                    Text(
                        "It's only used if today ends unfinished. Finish everything anyway and you keep it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                RestDayRow.RESTING -> {
                    Text("Today is a rest day", style = MaterialTheme.typography.titleMedium)
                    Text("Your rest day for this week", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "Your streak carries on. If you end up finishing everything anyway, " +
                            "the rest day isn't used and you keep it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = { onSetRestDay(false) }) { Text("Not resting after all") }
                }
            }
        }
    }
}

// Full-screen, stock Material 3 (docs/tasks/12) — no animation, no photo
// picker. The reflection field is optional: an empty/blank value is passed
// through as null (ArcRepository.acknowledgeMilestones trims it anyway, but
// keeping that decision visible here matches LogControl's commit()).
@Composable
private fun MilestoneCard(
    milestone: PendingMilestoneUiState.Single,
    onAcknowledge: (reflection: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reflection by remember(milestone.id) { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(milestone.label(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Day ${milestone.dayNumber} of ${milestone.totalDays}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = reflection,
            onValueChange = { reflection = it },
            label = { Text("Reflection (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onAcknowledge(reflection.trim().ifBlank { null }) }) { Text("Continue") }
    }
}

// Shown when two or more milestones are pending at once (docs/tasks/14) — one
// card lists them oldest-first instead of a stack of full-screen cards. Copy
// is neutral: "You've passed N milestones." stays true whether or not there
// was an absence (two can go pending while the app was only briefly closed, or
// never closed at all). The single optional reflection is saved on the latest
// milestone (handled by acknowledge via reflectionTargetId). Scrolls because
// four rows plus the field plus the keyboard can overflow a short screen.
@Composable
private fun MilestoneSummaryCard(
    summary: PendingMilestoneUiState.Summary,
    onAcknowledge: (reflection: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reflection by remember(summary.ids) { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "You've passed ${summary.rows.size} milestones.",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        summary.rows.forEach { row ->
            Text(
                "${row.shortLabel()} — day ${row.dayNumber} of ${row.totalDays}, " +
                    row.triggeredOnDate.format(MILESTONE_DATE_FORMAT),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = reflection,
            onValueChange = { reflection = it },
            label = { Text("Reflection (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onAcknowledge(reflection.trim().ifBlank { null }) }) { Text("Continue") }
    }
}

// FINAL fires on either completion or failure (R10) — arcCompleted picks
// which of the two this actually is; it's only ever null for the other
// kinds, which don't need the distinction.
private fun PendingMilestoneUiState.Single.label(): String = when (kind) {
    MilestoneKind.START -> "You're underway"
    MilestoneKind.P25 -> "A quarter of the way there"
    MilestoneKind.P50 -> "Halfway there"
    MilestoneKind.P75 -> "Three quarters there"
    MilestoneKind.FINAL -> if (arcCompleted == true) "Arc complete" else "Arc ended"
}

// Short form for a summary row — the long sentences read as a list of one-liners
// here. START never reaches a card (observePendingByArc excludes it); FINAL
// splits the same way label() does.
private fun PendingMilestoneUiState.Summary.Row.shortLabel(): String = when (kind) {
    MilestoneKind.START -> "Underway"
    MilestoneKind.P25 -> "A quarter"
    MilestoneKind.P50 -> "Halfway"
    MilestoneKind.P75 -> "Three quarters"
    MilestoneKind.FINAL -> if (arcCompleted == true) "Arc complete" else "Arc ended"
}

// "10 Sep" — the crossing date, read from triggered_on_date (docs/tasks/14).
private val MILESTONE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun DayState.label(): String = when (this) {
    DayState.COMPLETE -> "complete"
    DayState.PENDING -> "pending"
    DayState.REST -> "rest"
    DayState.MISSED -> "missed"
    DayState.PARTIAL -> "partial"
    DayState.FUTURE -> "future"
    DayState.OUT_OF_RANGE -> "out of range"
}

@Composable
private fun CommitmentCard(
    commitment: CommitmentUiState,
    onToggleBinary: (Boolean) -> Unit,
    onCounterChange: (Double) -> Unit,
    onDurationChange: (Double) -> Unit,
    onLogChange: (Double) -> Unit,
    onUndo: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(commitment.name, style = MaterialTheme.typography.titleMedium)
                    if (!commitment.isRequired) {
                        Text("optional", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Text(if (commitment.isSatisfied) "✓ done" else "not done")
            }
            Spacer(Modifier.height(8.dp))
            when (commitment.type) {
                CommitmentType.BINARY -> BinaryControl(commitment, onToggleBinary)
                CommitmentType.COUNTER -> StepperControl(commitment, unitLabel = commitment.unit ?: "", onChange = onCounterChange)
                CommitmentType.DURATION -> StepperControl(commitment, unitLabel = "min", onChange = onDurationChange)
                CommitmentType.LOG -> LogControl(commitment, onChange = onLogChange, onClear = onUndo)
            }
            if (commitment.currentValue != null) {
                TextButton(onClick = onUndo) { Text("Undo") }
            }
        }
    }
}

@Composable
private fun BinaryControl(commitment: CommitmentUiState, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = (commitment.currentValue ?: 0.0) >= 1.0,
            onCheckedChange = onToggle,
        )
        Text("Done")
    }
}

@Composable
private fun StepperControl(commitment: CommitmentUiState, unitLabel: String, onChange: (Double) -> Unit) {
    val current = commitment.currentValue ?: 0.0
    val target = commitment.targetValue
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange((current - 1).coerceAtLeast(0.0)) }) { Text("−") }
        val targetText = if (target != null) " / ${target.toInt()}" else ""
        Text(
            "${current.toInt()}$targetText $unitLabel",
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        OutlinedButton(onClick = { onChange(current + 1) }) { Text("+") }
    }
}

// docs/tasks/10-fix-entry-write-race.md — writes only commit on the IME
// "done" action or on losing focus, not per keystroke. Empty input commits
// as a clear (onClear, the same undo the card's "Undo" button uses); an
// unparseable value is discarded and the field reverts to the last known
// value rather than writing garbage.
@Composable
private fun LogControl(commitment: CommitmentUiState, onChange: (Double) -> Unit, onClear: () -> Unit) {
    var text by remember(commitment.id) {
        mutableStateOf(commitment.currentValue?.toString().orEmpty())
    }
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Follows Resolution's value when it changes for some other reason (e.g.
    // undo elsewhere) — but never while this field is focused, or the user's
    // in-progress typing would be overwritten mid-entry.
    LaunchedEffect(commitment.currentValue) {
        if (!isFocused) {
            text = commitment.currentValue?.toString().orEmpty()
        }
    }

    fun commit() {
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> onClear()
            else -> trimmed.toDoubleOrNull()?.let(onChange) ?: run {
                text = commitment.currentValue?.toString().orEmpty()
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(commitment.unit ?: "value") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            commit()
            focusManager.clearFocus()
        }),
        modifier = Modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            if (wasFocused && !isFocused) {
                commit()
            }
        },
    )
}

private val previewDate: LocalDate = LocalDate.of(2026, 9, 11)

private fun fakeCommitment(
    id: String,
    name: String,
    type: CommitmentType,
    isRequired: Boolean = true,
    targetValue: Double? = null,
    unit: String? = null,
    currentValue: Double? = null,
    isSatisfied: Boolean = false,
) = CommitmentUiState(
    id = id,
    name = name,
    type = type,
    isRequired = isRequired,
    targetValue = targetValue,
    unit = unit,
    currentValue = currentValue,
    isSatisfied = isSatisfied,
)

private fun fakeLoadedState() = TodayUiState.Loaded(
    arcName = "September Reset",
    dayNumber = 12,
    totalDays = 40,
    today = previewDate,
    todayState = DayState.PENDING,
    commitments = listOf(
        fakeCommitment("1", "Meditate", CommitmentType.BINARY, currentValue = 1.0, isSatisfied = true),
        fakeCommitment("2", "Push-ups", CommitmentType.COUNTER, targetValue = 30.0, unit = "reps", currentValue = 12.0),
        fakeCommitment("3", "Read", CommitmentType.DURATION, isRequired = false, targetValue = 20.0, unit = "minutes"),
        fakeCommitment("4", "Weight", CommitmentType.LOG, unit = "kg"),
    ),
    strictness = Strictness.STANDARD,
    restTokensLeftThisWeek = 1,
)

@Preview(showBackground = true, name = "Today screen")
@Composable
private fun TodayScreenPreview() {
    StreakTheme {
        TodayScreen(
            state = fakeLoadedState(),
            pendingMilestone = null,
            onAcknowledgeMilestone = {},
            onToggleBinary = { _, _ -> },
            onCounterChange = { _, _ -> },
            onDurationChange = { _, _ -> },
            onLogChange = { _, _ -> },
            onUndo = {},
            onSetRestDay = {},
            onCreateArc = {},
            onRemix = {},
            onViewRecap = {},
        )
    }
}

@Preview(showBackground = true, name = "Rest day — resting")
@Composable
private fun RestDayRestingPreview() {
    StreakTheme {
        RestDayCard(row = RestDayRow.RESTING, tokensLeft = 0, onSetRestDay = {})
    }
}

@Preview(showBackground = true, name = "Binary")
@Composable
private fun BinaryCardPreview() {
    StreakTheme {
        CommitmentCard(
            commitment = fakeCommitment("1", "Meditate", CommitmentType.BINARY),
            onToggleBinary = {}, onCounterChange = {}, onDurationChange = {}, onLogChange = {}, onUndo = {},
        )
    }
}

@Preview(showBackground = true, name = "Counter")
@Composable
private fun CounterCardPreview() {
    StreakTheme {
        CommitmentCard(
            commitment = fakeCommitment("2", "Push-ups", CommitmentType.COUNTER, targetValue = 30.0, unit = "reps", currentValue = 12.0),
            onToggleBinary = {}, onCounterChange = {}, onDurationChange = {}, onLogChange = {}, onUndo = {},
        )
    }
}

@Preview(showBackground = true, name = "Duration")
@Composable
private fun DurationCardPreview() {
    StreakTheme {
        CommitmentCard(
            commitment = fakeCommitment(
                "3", "Read", CommitmentType.DURATION,
                isRequired = false, targetValue = 20.0, unit = "minutes", currentValue = 20.0, isSatisfied = true,
            ),
            onToggleBinary = {}, onCounterChange = {}, onDurationChange = {}, onLogChange = {}, onUndo = {},
        )
    }
}

@Preview(showBackground = true, name = "Log")
@Composable
private fun LogCardPreview() {
    StreakTheme {
        CommitmentCard(
            commitment = fakeCommitment("4", "Weight", CommitmentType.LOG, unit = "kg", currentValue = 71.2, isSatisfied = true),
            onToggleBinary = {}, onCounterChange = {}, onDurationChange = {}, onLogChange = {}, onUndo = {},
        )
    }
}
