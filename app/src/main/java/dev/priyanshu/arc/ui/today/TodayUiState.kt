package dev.priyanshu.arc.ui.today

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import java.time.LocalDate

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoActiveArc : TodayUiState

    // Resolution.commitmentStatuses has no entry for a date outside the
    // arc's start/end range (R2) — that's the signal today hasn't arrived
    // yet or the arc is already over, not an empty commitment list.
    data class NotStarted(val arcName: String, val startDate: LocalDate) : TodayUiState
    data class Ended(val arcName: String, val totalDays: Int) : TodayUiState

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
