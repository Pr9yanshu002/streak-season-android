package dev.priyanshu.arc.ui.today

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import java.time.LocalDate

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoActiveArc : TodayUiState

    // Resolution.commitmentStatuses has no entry for a date outside the
    // arc's start/end range (R2) — that's the signal today hasn't arrived yet.
    data class NotStarted(val arcName: String, val startDate: LocalDate) : TodayUiState

    // Read straight off resolution.arcStatus/failedOnDate (R9/R10), not
    // date math — a strict arc can fail (and become terminal) well before
    // its end date, so "today is past the arc's range" is the wrong signal
    // for this state. sourceArcId is only meaningful for remix, which only
    // makes sense when !completed.
    data class Ended(
        val arcName: String,
        val totalDays: Int,
        val completed: Boolean,
        val failedOnDate: LocalDate?,
        val sourceArcId: String,
    ) : TodayUiState

    data class Loaded(
        val arcName: String,
        val dayNumber: Int,
        val totalDays: Int,
        val today: LocalDate,
        val todayState: DayState,
        val commitments: List<CommitmentUiState>,
    ) : TodayUiState
}

data class CommitmentUiState(
    val id: String,
    val name: String,
    val type: CommitmentType,
    val isRequired: Boolean,
    val targetValue: Double?,
    val unit: String?,
    // null means no entry exists yet today.
    val currentValue: Double?,
    val isSatisfied: Boolean,
)

// A milestone card waiting to be shown (R12). dayNumber is the crossing
// day computed from the engine's exact triggered_on_date, not today's —
// "Day 20 of 75" describes when the threshold was reached, not when the
// card happens to be shown. arcCompleted is only meaningful for kind ==
// FINAL — R10 says final fires on completion or failure, and "Arc complete"
// is the wrong thing to say on the failure branch.
data class PendingMilestoneUiState(
    val id: String,
    val kind: MilestoneKind,
    val dayNumber: Int,
    val totalDays: Int,
    val arcCompleted: Boolean? = null,
)
