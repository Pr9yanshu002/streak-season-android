package dev.priyanshu.arc.ui.recap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.priyanshu.arc.data.ArcRepository
import dev.priyanshu.arc.data.RecapMilestone
import dev.priyanshu.arc.data.RecapScreenData
import dev.priyanshu.arc.domain.model.ArcStatus
import dev.priyanshu.arc.domain.model.Commitment
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.rules.recap.CommitmentTotal
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Maps RecapScreenData (which already carries the engine's R19 numbers) into
// the ordered list of cards to show. Formatting and the skip rules live here;
// no recap statistic is computed or re-derived (docs/tasks/15). Follows
// TodayViewModel: a real ViewModel driven by RecapRoute's load(arcId), since
// there's no nav library to scope one per visit.
class RecapViewModel(
    private val repository: ArcRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<RecapUiState>(RecapUiState.Loading)
    val state: StateFlow<RecapUiState> = _state.asStateFlow()

    fun load(arcId: String) {
        _state.value = RecapUiState.Loading
        viewModelScope.launch {
            val data = repository.getRecapScreenData(arcId)
            _state.value = data?.toUiState() ?: RecapUiState.Unavailable
        }
    }
}

// internal, not private, so the skip rules can be unit-tested directly on a
// hand-built RecapScreenData without standing up Room (docs/tasks/15).
internal fun RecapScreenData.toUiState(): RecapUiState.Loaded {
    val commitmentsById = commitments.associateBy { it.id }
    val cards = buildList {
        add(
            RecapCard.Title(
                arcName = arcName,
                dateRange = "${startDate.format(DAY_MONTH_YEAR)} – ${endDate.format(DAY_MONTH_YEAR)}",
                headline = headline(),
            )
        )
        intention?.trim()?.takeIf { it.isNotEmpty() }?.let { add(RecapCard.Intention(it)) }
        add(RecapCard.BestStreak(recap.bestStreak))
        recap.hardestWeek?.let { week ->
            add(
                RecapCard.HardestWeek(
                    dateRange = "${week.weekStart.format(DAY_MONTH)} – ${week.weekEnd.format(DAY_MONTH)}",
                    completeDays = week.completeDays,
                    countedDays = week.countedDays,
                )
            )
        }
        add(RecapCard.WhatYouDid(recap.commitmentTotals.map { it.rowLabel(commitmentsById[it.commitmentId]) }))
        // start anchors the timeline; the card lists what the user crossed, so
        // skip it when nothing beyond start exists (docs/tasks/15).
        val milestoneRows = milestones
            .filter { it.kind != MilestoneKind.START }
            .sortedBy { it.date }
            .map { it.toRow(recap.status) }
        if (milestoneRows.isNotEmpty()) add(RecapCard.Milestones(milestoneRows))
        add(RecapCard.Completion(Math.round(recap.completionPct * 100).toInt()))
        add(RecapCard.WhatNext)
    }
    return RecapUiState.Loaded(cards)
}

private fun RecapScreenData.headline(): String =
    if (recap.status == ArcStatus.COMPLETED) {
        "You showed up ${recap.completeDays} of ${recap.countedDays} days."
    } else {
        "You made it to day ${recap.daysReached}."
    }

// Joins a CommitmentTotal's id back to the commitment for its name and unit —
// CommitmentTotal carries only the id (the numbers are the engine's point).
private fun CommitmentTotal.rowLabel(commitment: Commitment?): String {
    val name = commitment?.name ?: "(removed)"
    val unit = commitment?.unit?.takeIf { it.isNotBlank() }
    return when (this) {
        is CommitmentTotal.Binary -> "$name: $daysDone days"
        is CommitmentTotal.Measured ->
            "$name: ${formatValue(total)}${unit?.let { " $it" } ?: ""}"
        is CommitmentTotal.Log -> {
            // first/last are cross-module properties, so bind them before use.
            val firstValue = first
            val lastValue = last
            if (firstValue == null || lastValue == null) {
                "$name: no entries"
            } else {
                "$name: ${formatValue(firstValue)} → ${formatValue(lastValue)}${unit?.let { " $it" } ?: ""}"
            }
        }
    }
}

private fun RecapMilestone.toRow(status: ArcStatus): RecapCard.MilestoneRow =
    RecapCard.MilestoneRow(
        label = kindLabel(status),
        date = date.format(DAY_MONTH),
        reflection = reflection?.takeIf { it.isNotBlank() },
    )

// FINAL fires on completion or failure (R10); the arc's status picks the wording.
private fun RecapMilestone.kindLabel(status: ArcStatus): String = when (kind) {
    MilestoneKind.START -> "Underway"
    MilestoneKind.P25 -> "A quarter"
    MilestoneKind.P50 -> "Halfway"
    MilestoneKind.P75 -> "Three quarters"
    MilestoneKind.FINAL -> if (status == ArcStatus.COMPLETED) "Arc complete" else "Arc ended"
}

// Trim the ".0" off whole numbers so a counter reads "125 reps", not "125.0".
private fun formatValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val DAY_MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
