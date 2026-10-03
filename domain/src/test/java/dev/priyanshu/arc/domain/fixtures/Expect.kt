package dev.priyanshu.arc.domain.fixtures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire-shaped, like FixtureDto — dates and enum-like fields stay raw strings,
// parsed in the test assertions. Every field is optional — a fixture only
// asserts what it's about (fixtures/README.md). failed_on_date is the one
// field that's conditionally required: whenever arc_status is present,
// failed_on_date must be too (null meaning "did not fail"). That convention
// is what lets this stay a plain nullable-fields data class — there's no
// field where "key omitted" and "key present but null" need to mean
// different things.
@Serializable
data class ExpectDto(
    @SerialName("effective_today") val effectiveToday: String? = null,
    @SerialName("day_states") val dayStates: Map<String, String>? = null,
    @SerialName("current_streak") val currentStreak: Int? = null,
    @SerialName("best_streak") val bestStreak: Int? = null,
    @SerialName("rest_tokens_remaining") val restTokensRemaining: Map<String, Int>? = null,
    @SerialName("completion_pct") val completionPct: Double? = null,
    @SerialName("arc_status") val arcStatus: String? = null,
    @SerialName("failed_on_date") val failedOnDate: String? = null,
    @SerialName("sealed_dates") val sealedDates: List<String>? = null,
    @SerialName("day_number") val dayNumber: Int? = null,
    @SerialName("total_days") val totalDays: Int? = null,
    // date -> commitment id -> status string ("not_scheduled" / "pending" / "done").
    @SerialName("commitment_statuses") val commitmentStatuses: Map<String, Map<String, String>>? = null,
    // kind -> the exact date its threshold was crossed (R12, amended). Assert
    // the whole map, not key by key — a kind the engine returns that the
    // fixture omits must fail, same reasoning as commit_status_no_missing_keys.
    @SerialName("required_milestones")
    val requiredMilestones: Map<String, String>? = null,
    // R19 recap. Present -> the harness calls recap() and asserts. Unlike most
    // expect blocks this is asserted whole (see RecapExpectDto) so no field the
    // engine computes slips by unasserted.
    val recap: RecapExpectDto? = null,
)

// R19 — the expected Recap. When present and not is_null, EVERY field is
// asserted: the scalars, hardest_week (omitted means "assert null — no hardest
// week"), and the full commitment_totals set. This whole-object stance mirrors
// required_milestones: a partial assertion would let a wrong value the author
// didn't think to list pass silently.
@Serializable
data class RecapExpectDto(
    // true -> recap() must return null (an arc that has not ended). All other
    // fields are ignored in that case.
    @SerialName("is_null") val isNull: Boolean = false,
    val status: String? = null,
    @SerialName("complete_days") val completeDays: Int? = null,
    @SerialName("counted_days") val countedDays: Int? = null,
    @SerialName("completion_pct") val completionPct: Double? = null,
    @SerialName("days_reached") val daysReached: Int? = null,
    @SerialName("best_streak") val bestStreak: Int? = null,
    // Omitted (or JSON null) means "assert there is no hardest week".
    @SerialName("hardest_week") val hardestWeek: HardestWeekExpectDto? = null,
    // commitment id -> that commitment's expected total. Asserted as a whole
    // set: a commitment the engine returns that the fixture omits fails.
    @SerialName("commitment_totals") val commitmentTotals: Map<String, CommitmentTotalExpectDto>? = null,
)

@Serializable
data class HardestWeekExpectDto(
    @SerialName("week_key") val weekKey: String,
    @SerialName("complete_days") val completeDays: Int,
    @SerialName("counted_days") val countedDays: Int,
)

// One of three shapes, discriminated by `kind`: "binary" (days_done),
// "measured" for counter/duration (total + days_target_met), or "log"
// (first/last/days_recorded; first and last are null when nothing was recorded).
@Serializable
data class CommitmentTotalExpectDto(
    val kind: String,
    @SerialName("days_done") val daysDone: Int? = null,
    val total: Double? = null,
    @SerialName("days_target_met") val daysTargetMet: Int? = null,
    val first: Double? = null,
    val last: Double? = null,
    @SerialName("days_recorded") val daysRecorded: Int? = null,
)
