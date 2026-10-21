package dev.priyanshu.arc.ui.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.ui.components.BinaryCommitmentCard
import dev.priyanshu.arc.ui.components.CounterCommitmentCard
import dev.priyanshu.arc.ui.components.NumberCommitmentCard
import dev.priyanshu.arc.ui.components.RestDayCard
import dev.priyanshu.arc.ui.components.StreakPill
import dev.priyanshu.arc.ui.components.StrokeGlyph
import dev.priyanshu.arc.ui.components.StrokeIcon
import dev.priyanshu.arc.ui.components.UnscheduledCommitmentRow
import dev.priyanshu.arc.ui.components.WeekStrip
import dev.priyanshu.arc.ui.components.WeekStripDay
import dev.priyanshu.arc.ui.theme.Space
import dev.priyanshu.arc.ui.theme.StreakTheme
import java.time.DayOfWeek
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

// docs/tasks/18 Part 2 — the active day, built from ui/components. One
// Loaded state, three layouts, chosen only from what the engine already
// decided:
//  - in progress (design 01): "Day N of M", week strip, "Today · X of Y
//    required done", the commitments, the rest-day offer at the bottom
//  - day complete (02): an evergreen "Day N done" card holding the reversed
//    week strip; no rest card (restDayRow is null on a complete day)
//  - rest day (03): the resting card above the strip, and the commitments
//    under "Still here if you want them" — they keep working
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
    val resting = state.restDayRow == RestDayRow.RESTING
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Space.s6),
        verticalArrangement = Arrangement.spacedBy(Space.s4 + Space.s1),
    ) {
        item { TodayHeader(state) }
        if (state.isDayComplete) {
            item { DayDoneCard(state) }
        } else {
            item { DayCount(dayNumber = state.dayNumber, totalDays = state.totalDays) }
            if (resting) {
                item {
                    RestDayCard(resting = true, tokensLeft = state.restTokensLeftThisWeek, onSetRestDay = onSetRestDay)
                }
            }
            item { WeekStrip(days = state.week) }
        }
        item {
            if (resting) StillHereHeading() else TodayHeading(state)
        }
        // One item so the cards sit 12 dp apart (the design's list gap) while
        // sections stay 20 dp apart. key() keeps each card's own state (the
        // number field's typed text) attached to its commitment.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
                state.commitments.forEach { commitment ->
                    key(commitment.id) {
                        CommitmentItem(
                            commitment = commitment,
                            isDayComplete = state.isDayComplete,
                            onToggleBinary = onToggleBinary,
                            onCounterChange = onCounterChange,
                            onDurationChange = onDurationChange,
                            onLogChange = onLogChange,
                            onUndo = onUndo,
                        )
                    }
                }
            }
        }
        if (state.restDayRow == RestDayRow.OFFER) {
            item {
                RestDayCard(resting = false, tokensLeft = state.restTokensLeftThisWeek, onSetRestDay = onSetRestDay)
            }
        }
    }
}

// "Wednesday 21 January"
private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
private fun TodayHeader(state: TodayUiState.Loaded) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(state.arcName, style = MaterialTheme.typography.labelLarge)
            Text(
                state.today.format(HEADER_DATE_FORMAT),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Hidden at 0 (docs/tasks/18): "0-day streak" reads as a reproach.
        if (state.currentStreak > 0) StreakPill(days = state.currentStreak)
    }
}

// "Day 52" large, "of 90" smaller and muted, sharing a baseline.
@Composable
private fun DayCount(dayNumber: Int, totalDays: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s2)) {
        Text("Day $dayNumber", style = MaterialTheme.typography.displayMedium, modifier = Modifier.alignByBaseline())
        Text(
            "of $totalDays",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

// Design 02: replaces "Day N of M" and the plain strip once today is complete.
// The streak in the sentence is Resolution.currentStreak, which counts today.
@Composable
private fun DayDoneCard(state: TodayUiState.Loaded) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Column(
            modifier = Modifier.padding(Space.s4 + Space.s1),
            verticalArrangement = Arrangement.spacedBy(Space.s4),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.s4),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StrokeIcon(StrokeGlyph.TICK, MaterialTheme.colorScheme.primary, size = 24.dp, strokeWidth = 2.5f)
                }
                Column {
                    Text("Day ${state.dayNumber} done", style = MaterialTheme.typography.headlineMedium)
                    val days = if (state.currentStreak == 1) "1 day" else "${state.currentStreak} days"
                    Text(
                        "Everything required, $days in a row.",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                    )
                }
            }
            WeekStrip(days = state.week, reversed = true)
        }
    }
}

// "Today" with "X of Y required done" on the right. The count turns evergreen
// once the day is complete (design 02). Left out when nothing required is
// scheduled today — "0 of 0" says nothing useful.
@Composable
private fun TodayHeading(state: TodayUiState.Loaded) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Today", style = MaterialTheme.typography.titleLarge, modifier = Modifier.alignByBaseline())
        if (state.requiredTotal > 0) {
            Text(
                "${state.requiredDone} of ${state.requiredTotal} required done",
                style = MaterialTheme.typography.labelLarge,
                color = if (state.isDayComplete) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

// Design 03: on a rest day the commitments sit under this instead of "Today".
@Composable
private fun StillHereHeading() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s1)) {
        Text("Still here if you want them", style = MaterialTheme.typography.titleLarge)
        Text(
            "Anything you log today still counts.",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// Picks the component for one commitment. Every value comes straight from
// CommitmentUiState; the +/- arithmetic is the same the old stepper did
// (one per tap, never below 0).
@Composable
private fun CommitmentItem(
    commitment: CommitmentUiState,
    isDayComplete: Boolean,
    onToggleBinary: (String, Boolean) -> Unit,
    onCounterChange: (String, Double) -> Unit,
    onDurationChange: (String, Double) -> Unit,
    onLogChange: (String, Double) -> Unit,
    onUndo: (String) -> Unit,
) {
    val isBonus = !commitment.isRequired
    if (!commitment.isScheduledToday) {
        UnscheduledCommitmentRow(name = commitment.name, scheduleText = commitment.scheduleText)
        return
    }
    when (commitment.type) {
        CommitmentType.BINARY -> BinaryCommitmentCard(
            name = commitment.name,
            isDone = commitment.isSatisfied,
            isBonus = isBonus,
            onToggle = { done -> onToggleBinary(commitment.id, done) },
        )
        CommitmentType.COUNTER, CommitmentType.DURATION -> {
            val current = commitment.currentValue ?: 0.0
            val onChange = if (commitment.type == CommitmentType.DURATION) onDurationChange else onCounterChange
            CounterCommitmentCard(
                name = commitment.name,
                current = current,
                target = commitment.targetValue,
                unit = if (commitment.type == CommitmentType.DURATION) "min" else commitment.unit.orEmpty(),
                isBonus = isBonus,
                isDone = commitment.isSatisfied,
                onDecrement = { onChange(commitment.id, (current - 1).coerceAtLeast(0.0)) },
                onIncrement = { onChange(commitment.id, current + 1) },
                // Design 02: an unfinished bonus on a finished day says so.
                captionSuffix = if (isDayComplete && isBonus && !commitment.isSatisfied) {
                    "doesn't affect your day"
                } else {
                    null
                },
            )
        }
        CommitmentType.LOG -> NumberCommitmentCard(
            name = commitment.name,
            value = commitment.currentValue,
            unit = commitment.unit,
            isBonus = isBonus,
            onCommit = { value -> onLogChange(commitment.id, value) },
            // A blank field clears the entry — the same undo the old card's
            // "Undo" button and LogControl used (docs/tasks/10).
            onClear = { onUndo(commitment.id) },
        )
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

// Design 01's Wednesday: Gym done, Water part way, two bonus items, a long
// walk not scheduled today, one rest token left.
private val previewToday: LocalDate = LocalDate.of(2026, 1, 21)

private fun fakeCommitment(
    id: String,
    name: String,
    type: CommitmentType,
    isRequired: Boolean = true,
    targetValue: Double? = null,
    unit: String? = null,
    currentValue: Double? = null,
    isSatisfied: Boolean = false,
    isScheduledToday: Boolean = true,
    scheduleText: String? = "Every day",
) = CommitmentUiState(
    id = id,
    name = name,
    type = type,
    isRequired = isRequired,
    targetValue = targetValue,
    unit = unit,
    currentValue = currentValue,
    isSatisfied = isSatisfied,
    isScheduledToday = isScheduledToday,
    scheduleText = scheduleText,
)

private fun fakeWeek(today: DayState): List<WeekStripDay> {
    val monday = previewToday.with(DayOfWeek.MONDAY)
    val states = listOf(
        DayState.COMPLETE, DayState.COMPLETE, today,
        DayState.FUTURE, DayState.FUTURE, DayState.FUTURE, DayState.FUTURE,
    )
    return states.mapIndexed { i, state ->
        WeekStripDay(date = monday.plusDays(i.toLong()), state = state, isToday = i == 2)
    }
}

private fun fakeLoadedState(todayState: DayState = DayState.PENDING): TodayUiState.Loaded {
    val complete = todayState == DayState.COMPLETE
    return TodayUiState.Loaded(
        arcName = "Winter Arc",
        dayNumber = 52,
        totalDays = 90,
        today = previewToday,
        todayState = todayState,
        commitments = listOf(
            fakeCommitment("1", "Gym", CommitmentType.BINARY, currentValue = 1.0, isSatisfied = true),
            fakeCommitment(
                "2", "Water", CommitmentType.COUNTER, targetValue = 3000.0, unit = "ml",
                currentValue = if (complete) 3000.0 else 2100.0, isSatisfied = complete,
            ),
            fakeCommitment(
                "3", "Reading", CommitmentType.DURATION, isRequired = false, targetValue = 30.0,
                currentValue = 10.0,
            ),
            fakeCommitment("4", "Weight", CommitmentType.LOG, isRequired = false, unit = "kg"),
            fakeCommitment(
                "5", "Long walk", CommitmentType.BINARY, isScheduledToday = false,
                scheduleText = "Saturdays and Sundays",
            ),
        ),
        currentStreak = if (complete) 8 else 7,
        requiredDone = if (complete) 2 else 1,
        requiredTotal = 2,
        week = fakeWeek(todayState),
        strictness = Strictness.STANDARD,
        restTokensLeftThisWeek = if (todayState == DayState.REST) 0 else 1,
    )
}

@Composable
private fun TodayPreview(state: TodayUiState.Loaded) {
    StreakTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                state = state,
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
}

@PreviewLightDark
@Composable
private fun TodayInProgressPreview() {
    TodayPreview(fakeLoadedState(DayState.PENDING))
}

@PreviewLightDark
@Composable
private fun TodayCompletePreview() {
    TodayPreview(fakeLoadedState(DayState.COMPLETE))
}

@PreviewLightDark
@Composable
private fun TodayRestingPreview() {
    TodayPreview(fakeLoadedState(DayState.REST))
}
