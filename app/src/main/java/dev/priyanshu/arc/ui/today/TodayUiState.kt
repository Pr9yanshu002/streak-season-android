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

// What the Today screen shows for pending milestones (R12). One pending
// milestone is a Single card (the task-12 card, unchanged); two or more
// collapse into one Summary card listing them oldest-first, so a lapse
// doesn't make the user tap through a stack of full-screen cards before
// reaching check-in (docs/tasks/14). Choosing between the two is
// presentation, decided in TodayViewModel — it is not a rule and nothing in
// :domain knows about it.
//
// dayNumber on each is the crossing day computed from the engine's exact
// triggered_on_date, not today's — "Day 20 of 75" describes when the
// threshold was reached, not when the card happens to be shown. arcCompleted
// is only meaningful for kind == FINAL (R10 says final fires on completion or
// failure, and "Arc complete" is the wrong thing to say on the failure
// branch); null for every other kind.
sealed interface PendingMilestoneUiState {
    // The milestone ids this card is actually showing. Continue acknowledges
    // exactly these, never "everything pending for the arc" — a milestone can
    // go pending while the card is up (a day boundary passes, reconcile runs
    // on resume), and acknowledging by arc would silently bury one the user
    // never saw (docs/tasks/14).
    val ids: List<String>

    // Where an optional reflection is saved: the most recent milestone on the
    // card (latest triggered_on_date). A reflection typed on day 16 is not a
    // day-5 reflection, so it attaches to the latest and the others' stays
    // null (docs/tasks/14). For a Single that is simply its own id.
    val reflectionTargetId: String

    data class Single(
        val id: String,
        val kind: MilestoneKind,
        val dayNumber: Int,
        val totalDays: Int,
        val arcCompleted: Boolean? = null,
    ) : PendingMilestoneUiState {
        override val ids: List<String> get() = listOf(id)
        override val reflectionTargetId: String get() = id
    }

    data class Summary(val rows: List<Row>) : PendingMilestoneUiState {
        // rows arrive oldest-first (observePendingByArc's ORDER BY) and are
        // kept in that order — the view model does not re-sort (docs/tasks/14).
        override val ids: List<String> get() = rows.map { it.id }
        override val reflectionTargetId: String get() = rows.last().id

        data class Row(
            val id: String,
            val kind: MilestoneKind,
            val dayNumber: Int,
            val totalDays: Int,
            val triggeredOnDate: LocalDate,
            val arcCompleted: Boolean? = null,
        )
    }
}
