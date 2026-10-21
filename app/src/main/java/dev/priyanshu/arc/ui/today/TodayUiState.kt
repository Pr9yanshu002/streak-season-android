package dev.priyanshu.arc.ui.today

import dev.priyanshu.arc.domain.model.CommitmentType
import dev.priyanshu.arc.domain.model.DayState
import dev.priyanshu.arc.domain.model.MilestoneKind
import dev.priyanshu.arc.domain.model.Strictness
import dev.priyanshu.arc.ui.components.WeekStripDay
import java.time.LocalDate

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoActiveArc : TodayUiState

    // Resolution.commitmentStatuses has no entry for a date outside the
    // arc's start/end range (R2) — that's the signal today hasn't arrived yet.
    // Design 06: totalDays is Resolution.totalDays (R2); daysToGo is plain
    // calendar distance from today to startDate; intention is arcs.intention
    // (null when the user didn't write one).
    data class NotStarted(
        val arcName: String,
        val startDate: LocalDate,
        val totalDays: Int,
        val daysToGo: Int,
        val intention: String?,
        val commitments: List<PlannedCommitmentUiState>,
    ) : TodayUiState

    // Read straight off resolution.arcStatus/failedOnDate (R9/R10), not
    // date math — a strict arc can fail (and become terminal) well before
    // its end date, so "today is past the arc's range" is the wrong signal
    // for this state. sourceArcId is only meaningful for remix, which only
    // makes sense when !completed.
    //
    // Design 08: endedOn is failedOnDate for a failed arc, the arc's end date
    // for a completed one. season is every date's Resolution.dayStates entry,
    // start to end, for the small grid. daysShowedUp / daysCounted are the
    // recap's complete_days / counted_days (R19), read from
    // ArcRepository.getRecapScreenData — null until that has loaded.
    data class Ended(
        val arcName: String,
        val totalDays: Int,
        val completed: Boolean,
        val failedOnDate: LocalDate?,
        val sourceArcId: String,
        val endedOn: LocalDate,
        val season: List<DayState>,
        val daysShowedUp: Int? = null,
        val daysCounted: Int? = null,
    ) : TodayUiState

    data class Loaded(
        val arcName: String,
        val dayNumber: Int,
        val totalDays: Int,
        val today: LocalDate,
        val todayState: DayState,
        // Ordered for display (docs/tasks/18): required scheduled today, then
        // bonus scheduled today, then everything not scheduled today.
        val commitments: List<CommitmentUiState>,
        // Resolution.currentStreak (R11).
        val currentStreak: Int,
        // "X of Y required done": required commitments scheduled today (Y)
        // and how many of those the engine marked DONE (X). Both are tallies
        // of Resolution.commitmentStatuses[today] — no R3/R4 decision here.
        val requiredDone: Int,
        val requiredTotal: Int,
        // This ISO week, Monday to Sunday. Dates are calendar arithmetic; each
        // state is Resolution.dayStates[date], OUT_OF_RANGE outside the arc.
        val week: List<WeekStripDay>,
        val strictness: Strictness,
        // Resolution.restTokensRemaining for today's ISO week (R6), read off
        // the engine by its own week key — never counted here.
        val restTokensLeftThisWeek: Int,
    ) : TodayUiState {
        // The engine already decided today's state; this only names the case
        // the "Day N done" layout keys off (design 02).
        val isDayComplete: Boolean get() = todayState == DayState.COMPLETE

        // docs/tasks/17 — which rest-day row to show, or null for none. Shown
        // only on a standard arc (R6: strict and chill have no tokens), never
        // on a complete day (the flag would be ignored), and only when today
        // is already rest or there is a token left to take.
        val restDayRow: RestDayRow?
            get() = when {
                strictness != Strictness.STANDARD -> null
                todayState == DayState.COMPLETE -> null
                todayState == DayState.REST -> RestDayRow.RESTING
                restTokensLeftThisWeek > 0 -> RestDayRow.OFFER
                else -> null
            }
    }
}

enum class RestDayRow { OFFER, RESTING }

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
    // false when Resolution says NOT_SCHEDULED for today (R3) — shown as a
    // muted "Not today" row instead of a card.
    val isScheduledToday: Boolean,
    // formatSchedule() of the commitment's weekdays, e.g. "Saturdays and
    // Sundays". null when there's nothing to describe.
    val scheduleText: String?,
)

// One commitment in design 06's "What you'll do" list, before the arc starts.
data class PlannedCommitmentUiState(
    val id: String,
    val name: String,
    val isRequired: Boolean,
    // formatSchedule() of its weekdays; null when there's nothing to describe.
    val scheduleText: String?,
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
        // triggered_on_date, for design 04's "Day 45 of 90 · 14 January".
        val triggeredOnDate: LocalDate,
        val arcCompleted: Boolean? = null,
    ) : PendingMilestoneUiState {
        override val ids: List<String> get() = listOf(id)
        override val reflectionTargetId: String get() = id
    }

    // arcName and todayDayNumber feed design 05's "Winter Arc · Day 70 of 90"
    // line: the arc's name and Resolution.dayNumber for today.
    data class Summary(
        val rows: List<Row>,
        val arcName: String,
        val todayDayNumber: Int,
    ) : PendingMilestoneUiState {
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
