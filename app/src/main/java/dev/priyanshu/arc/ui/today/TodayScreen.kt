package dev.priyanshu.arc.ui.today

import android.icu.text.MessageFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.ui.components.BinaryCommitmentCard
import dev.priyanshu.arc.ui.components.BonusChip
import dev.priyanshu.arc.ui.components.CounterCommitmentCard
import dev.priyanshu.arc.ui.components.DayDisc
import dev.priyanshu.arc.ui.components.LargeButton
import dev.priyanshu.arc.ui.components.NumberCommitmentCard
import dev.priyanshu.arc.ui.components.ProgressPie
import dev.priyanshu.arc.ui.components.ReflectionField
import dev.priyanshu.arc.ui.components.RestDayCard
import dev.priyanshu.arc.ui.components.StreakCard
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
import java.util.Locale
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
        is TodayUiState.NoActiveArc -> NoArcContent(onCreateArc = onCreateArc, modifier = modifier)

        is TodayUiState.NotStarted -> NotStartedContent(state = state, modifier = modifier)

        is TodayUiState.Ended -> EndedContent(
            state = state,
            onViewRecap = onViewRecap,
            onCreateArc = onCreateArc,
            onRemix = onRemix,
            modifier = modifier,
        )

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

// A full-height page whose bottom part sits at the bottom of the screen when
// there's room, and scrolls with the rest when there isn't (designs 04, 05,
// 07 and 08 all anchor their actions to the bottom). heightIn(min) +
// SpaceBetween does what a weight() spacer can't inside verticalScroll.
@Composable
private fun AnchoredPage(
    modifier: Modifier,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(Space.s6),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column { top() }
            Column(modifier = Modifier.padding(top = Space.s6)) { bottom() }
        }
    }
}

// How far round each milestone's pie is filled (designs 04, 05).
private fun MilestoneKind.fraction(): Float = when (this) {
    MilestoneKind.START -> 0f
    MilestoneKind.P25 -> 0.25f
    MilestoneKind.P50 -> 0.5f
    MilestoneKind.P75 -> 0.75f
    MilestoneKind.FINAL -> 1f
}

private fun dayCount(n: Int): String = if (n == 1) "1 day" else "$n days"

// Design 04 — one pending milestone takes over the screen (docs/tasks/12).
// The reflection is optional: blank is passed through as null
// (ArcRepository.acknowledgeMilestones trims it anyway).
@Composable
private fun MilestoneCard(
    milestone: PendingMilestoneUiState.Single,
    onAcknowledge: (reflection: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reflection by remember(milestone.id) { mutableStateOf("") }
    AnchoredPage(
        modifier = modifier,
        top = {
            Spacer(Modifier.height(Space.s6 + Space.s2))
            ProgressPie(
                fraction = milestone.kind.fraction(),
                color = MaterialTheme.colorScheme.tertiary,
                track = StreakTheme.colors.larchSoft,
                size = 112.dp,
            )
            Spacer(Modifier.height(Space.s6 + Space.s2))
            Text(milestone.label(), style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(Space.s2 + Space.s1))
            Text(
                "Day ${milestone.dayNumber} of ${milestone.totalDays} · " +
                    milestone.triggeredOnDate.format(MILESTONE_DATE_FORMAT),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            // "45 days behind you, 45 ahead." — left out for FINAL, where
            // "0 ahead" says nothing (and a failed arc's days weren't all run).
            if (milestone.kind != MilestoneKind.FINAL) {
                Spacer(Modifier.height(Space.s4))
                Text(
                    "${dayCount(milestone.dayNumber)} behind you, " +
                        "${milestone.totalDays - milestone.dayNumber} ahead.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        bottom = {
            ReflectionField(
                value = reflection,
                onValueChange = { reflection = it },
                label = "A line for later",
                helper = "You'll see it again in your recap.",
            )
            Spacer(Modifier.height(Space.s6))
            LargeButton(
                text = "Continue",
                onClick = { onAcknowledge(reflection.trim().ifBlank { null }) },
                filled = true,
            )
        },
    )
}

// Design 05 — two or more pending milestones collapse into one page
// (docs/tasks/14), listed oldest-first. The one reflection is saved on the
// latest milestone (reflectionTargetId), which the helper line names.
@Composable
private fun MilestoneSummaryCard(
    summary: PendingMilestoneUiState.Summary,
    onAcknowledge: (reflection: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var reflection by remember(summary.ids) { mutableStateOf("") }
    val latest = summary.rows.last()
    AnchoredPage(
        modifier = modifier,
        top = {
            Spacer(Modifier.height(Space.s6 + Space.s2))
            Text(
                "${summary.arcName} · Day ${summary.todayDayNumber} of ${latest.totalDays}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(Space.s2 + Space.s1))
            Text("You've passed ${summary.rows.size} milestones", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(Space.s6 + Space.s2))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                summary.rows.forEach { row ->
                    val isLatest = row.id == latest.id
                    StreakCardRow(
                        border = if (isLatest) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary)
                        } else {
                            BorderStroke(1.dp, StreakTheme.colors.line)
                        },
                    ) {
                        ProgressPie(
                            fraction = row.kind.fraction(),
                            color = MaterialTheme.colorScheme.tertiary,
                            track = StreakTheme.colors.larchSoft,
                            size = 32.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                row.label(),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                "Day ${row.dayNumber} · ${row.triggeredOnDate.format(MILESTONE_DATE_FORMAT)}" +
                                    if (isLatest) " · latest" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        bottom = {
            ReflectionField(
                value = reflection,
                onValueChange = { reflection = it },
                label = "One line about how it's going",
                helper = "Saved with the latest one, ${latest.label()}.",
            )
            Spacer(Modifier.height(Space.s6))
            LargeButton(
                text = "Continue",
                onClick = { onAcknowledge(reflection.trim().ifBlank { null }) },
                filled = true,
            )
        },
    )
}

// A surface card laid out as one row — the milestone rows (05) and the "What
// you'll do" rows (06).
@Composable
private fun StreakCardRow(
    border: BorderStroke = BorderStroke(1.dp, StreakTheme.colors.line),
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.s4, vertical = Space.s2 + Space.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s4),
            content = content,
        )
    }
}

// FINAL fires on either completion or failure (R10) — arcCompleted picks
// which of the two this actually is; it's only ever null for the other
// kinds, which don't need the distinction.
private fun milestoneLabel(kind: MilestoneKind, arcCompleted: Boolean?): String = when (kind) {
    MilestoneKind.START -> "You're underway"
    MilestoneKind.P25 -> "A quarter of the way there"
    MilestoneKind.P50 -> "Halfway there"
    MilestoneKind.P75 -> "Three quarters there"
    MilestoneKind.FINAL -> if (arcCompleted == true) "Arc complete" else "Arc ended"
}

private fun PendingMilestoneUiState.Single.label(): String = milestoneLabel(kind, arcCompleted)

// Design 05 lists rows with the same full sentences as the single card.
private fun PendingMilestoneUiState.Summary.Row.label(): String = milestoneLabel(kind, arcCompleted)

// "14 January" — the crossing date, read from triggered_on_date (docs/tasks/14).
private val MILESTONE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")

// Design 07 — no active arc. The row of pies previews what an arc fills in;
// the four steps are the create flow's order.
@Composable
private fun NoArcContent(onCreateArc: () -> Unit, modifier: Modifier = Modifier) {
    AnchoredPage(
        modifier = modifier,
        top = {
            Spacer(Modifier.height(Space.s6 + Space.s2))
            Row(
                modifier = Modifier.clearAndSetSemantics {},
                horizontalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1),
            ) {
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    ProgressPie(fraction = fraction, color = MaterialTheme.colorScheme.primary, track = null, size = 36.dp)
                }
            }
        },
        bottom = {
            Text("Start an arc", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(Space.s4))
            Text(
                "An arc is a stretch of days with a start, an end, and a few things you'll do each day. " +
                    "When it ends, you'll see how it went.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s6 + Space.s2))
            StreakCard {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
                    listOf(
                        "Name it and pick the dates.",
                        "Choose what you'll do, and on which days.",
                        "Set how strict it is.",
                        "Write down why you're doing it.",
                    ).forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.width(Space.s4).alignByBaseline(),
                            )
                            Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alignByBaseline())
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.s6 + Space.s2))
            LargeButton(text = "Start an arc", onClick = onCreateArc, filled = true)
        },
    )
}

// "Monday 6 April" / "Saturday 28 February"
private val LONG_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")

// Design 06 — the arc exists but hasn't started. Countdown, the user's own
// "why" in their voice, and what they signed up for.
@Composable
private fun NotStartedContent(state: TodayUiState.NotStarted, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.s6),
        verticalArrangement = Arrangement.spacedBy(Space.s4 + Space.s1),
    ) {
        Column {
            Text(state.arcName, style = MaterialTheme.typography.labelLarge)
            Text(
                "Starts ${state.startDate.format(LONG_DATE_FORMAT)} · ${dayCount(state.totalDays)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(top = Space.s6)) {
            Text(
                "${state.daysToGo}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.height(Space.s2))
            Text(
                if (state.daysToGo == 1) "day to go" else "days to go",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(Space.s2))
            Text(
                "Nothing to check in yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Only when the user wrote one — the create flow allows skipping it.
        if (state.intention != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, StreakTheme.colors.line),
            ) {
                Column(
                    modifier = Modifier.padding(Space.s4 + Space.s1),
                    verticalArrangement = Arrangement.spacedBy(Space.s2),
                ) {
                    Text(
                        "You wrote",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(state.intention, style = StreakTheme.type.voice)
                }
            }
        }
        if (state.commitments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2)) {
                Text("What you'll do", style = MaterialTheme.typography.titleLarge)
                state.commitments.forEach { commitment ->
                    StreakCardRow {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.s2),
                        ) {
                            Text(
                                commitment.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            )
                            if (!commitment.isRequired) BonusChip()
                        }
                        if (commitment.scheduleText != null) {
                            Text(
                                commitment.scheduleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// "Ninety" — the arc's length spelled out for design 08's "Ninety days,
// done." ICU's English spell-out (MessageFormat's "spellout" argument type,
// part of the platform since API 24 — no new dependency), first letter
// capitalised.
internal fun spellOut(n: Int): String =
    MessageFormat("{0,spellout}", Locale.ENGLISH)
        .format(arrayOf<Any>(n))
        .replaceFirstChar { it.uppercase() }

// Design 08 — the arc is over (completed or failed). The recap is the main
// thing to do from here (docs/tasks/15). Remix only makes sense for a failed
// arc, so it only shows there.
@Composable
private fun EndedContent(
    state: TodayUiState.Ended,
    onViewRecap: (sourceArcId: String) -> Unit,
    onCreateArc: () -> Unit,
    onRemix: (sourceArcId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnchoredPage(
        modifier = modifier,
        top = {
            Text(state.arcName, style = MaterialTheme.typography.labelLarge)
            Text(
                "Ended ${state.endedOn.format(LONG_DATE_FORMAT)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s10 + Space.s2))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (state.completed) {
                        Text("${spellOut(state.totalDays)} days, done.", style = MaterialTheme.typography.headlineMedium)
                    }
                    // Shown once the recap's counts have loaded.
                    if (state.daysShowedUp != null && state.daysCounted != null) {
                        Text("You showed up", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(Space.s2 + Space.s1))
                        Text(
                            "${state.daysShowedUp}",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(Space.s2))
                        Text("of ${dayCount(state.daysCounted)}", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(Modifier.width(Space.s4))
                SeasonGrid(arcName = state.arcName, season = state.season)
            }
        },
        bottom = {
            Text(
                "Your recap is ready — the whole season, and what you wrote along the way.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s4 + Space.s1))
            Column(verticalArrangement = Arrangement.spacedBy(Space.s2 + Space.s1)) {
                LargeButton(text = "See your recap", onClick = { onViewRecap(state.sourceArcId) }, filled = true)
                LargeButton(text = "Start something new", onClick = onCreateArc, filled = false)
                if (!state.completed) {
                    LargeButton(text = "Remix this arc", onClick = { onRemix(state.sourceArcId) }, filled = false)
                }
            }
        },
    )
}

// Every day of the arc as a 12 dp disc, seven to a row from the start date
// (design 08). One TalkBack item for the whole grid.
@Composable
private fun SeasonGrid(arcName: String, season: List<DayState>) {
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "All ${dayCount(season.size)} of $arcName, one row per week."
        },
        verticalArrangement = Arrangement.spacedBy(Space.s1),
    ) {
        season.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s1)) {
                week.forEach { state -> DayDisc(state = state, isToday = false, size = 12.dp) }
            }
        }
    }
}

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

@Composable
private fun StatePreview(state: TodayUiState, pendingMilestone: PendingMilestoneUiState? = null) {
    StreakTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            TodayScreen(
                state = state,
                pendingMilestone = pendingMilestone,
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

// Design 04.
@PreviewLightDark
@Composable
private fun MilestoneSinglePreview() {
    StatePreview(
        state = fakeLoadedState(),
        pendingMilestone = PendingMilestoneUiState.Single(
            id = "m50", kind = MilestoneKind.P50, dayNumber = 45, totalDays = 90,
            triggeredOnDate = LocalDate.of(2026, 1, 14),
        ),
    )
}

// Design 05.
@PreviewLightDark
@Composable
private fun MilestoneSummaryPreview() {
    StatePreview(
        state = fakeLoadedState(),
        pendingMilestone = PendingMilestoneUiState.Summary(
            rows = listOf(
                PendingMilestoneUiState.Summary.Row("m25", MilestoneKind.P25, 23, 90, LocalDate.of(2025, 12, 23)),
                PendingMilestoneUiState.Summary.Row("m50", MilestoneKind.P50, 45, 90, LocalDate.of(2026, 1, 14)),
                PendingMilestoneUiState.Summary.Row("m75", MilestoneKind.P75, 68, 90, LocalDate.of(2026, 2, 6)),
            ),
            arcName = "Winter Arc",
            todayDayNumber = 70,
        ),
    )
}

// Design 06.
@PreviewLightDark
@Composable
private fun NotStartedPreview() {
    StatePreview(
        TodayUiState.NotStarted(
            arcName = "Spring Sprint",
            startDate = LocalDate.of(2026, 4, 6),
            totalDays = 30,
            daysToGo = 3,
            intention = "Get my mornings back.",
            commitments = listOf(
                PlannedCommitmentUiState("1", "Run 5 km", isRequired = true, scheduleText = "Mon, Wed, Fri"),
                PlannedCommitmentUiState("2", "Up by 6:30", isRequired = true, scheduleText = "Every day"),
                PlannedCommitmentUiState("3", "Journal", isRequired = false, scheduleText = "Every day"),
            ),
        ),
    )
}

// Design 07.
@PreviewLightDark
@Composable
private fun NoArcPreview() {
    StatePreview(TodayUiState.NoActiveArc)
}

// Design 08: a 90-day arc, mostly complete, with some partial, missed and rest days.
@PreviewLightDark
@Composable
private fun EndedPreview() {
    val season = "CCCCCCRCCPCCCRCCCMCCCCMCRMCPCCCMCCRCCCPCCCCMCCCCCCCCRCCCCCCCCCCCCCCPCCCMCCCCRCCCMCCCCCPCCC"
        .map {
            when (it) {
                'P' -> DayState.PARTIAL
                'M' -> DayState.MISSED
                'R' -> DayState.REST
                else -> DayState.COMPLETE
            }
        }
    StatePreview(
        TodayUiState.Ended(
            arcName = "Winter Arc",
            totalDays = 90,
            completed = true,
            failedOnDate = null,
            sourceArcId = "arc",
            endedOn = LocalDate.of(2026, 2, 28),
            season = season,
            daysShowedUp = 72,
            daysCounted = 84,
        ),
    )
}
